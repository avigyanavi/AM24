import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.am24.am24.DatingFilterSettings
import com.am24.am24.FirebaseRefs
import com.am24.am24.Notification
import com.am24.am24.Profile
import com.am24.am24.ProfileViewModel
import com.am24.am24.R
import com.am24.am24.calculateAge
import com.am24.am24.calculateDistance
import com.am24.am24.canonicalGenderRes
import com.am24.am24.handleSwipeRight
import com.firebase.geofire.GeoFire
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private val gson = com.google.gson.Gson()

private fun Map<*, *>.toProfile(): Profile =
    gson.fromJson(gson.toJson(this), Profile::class.java)

// ─── NEW: data class for holding incoming compliment ───
data class ComplimentData(
    val text: String = "",
    val voiceUrl: String? = null,
    val timestamp: Long = 0L
) // ← NEW

class DatingViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val BOOST_DURATION_MS = 1 * 60 * 60 * 1000L

        /* NEW ── sentinel to mean “don’t filter by distance / Worldwide” */
        const val WORLDWIDE_DISTANCE = 101
        const val INDIA_MAX_DISTANCE = 65
    }

    private val TAG = "DatingViewModel"
    private val database = FirebaseRefs.db
    private val usersRef = database.getReference("users")
    private val geoFire = GeoFire(database.getReference("geoFireLocations"))
    private var lastNonEmptyProfiles: List<Profile> = emptyList()


    // StateFlows
    private val _allProfiles = MutableStateFlow<List<Profile>>(emptyList())
    val allProfiles: StateFlow<List<Profile>> get() = _allProfiles

    /* ─────────── Live inventory counts ─────────── */
    private val _complimentsLeft = MutableStateFlow(0)
    private val _boostsLeft      = MutableStateFlow(0)

    val complimentsLeft: StateFlow<Int> = _complimentsLeft.asStateFlow()
    val boostsLeft     : StateFlow<Int> = _boostsLeft     .asStateFlow()

    private val _datingFilters = MutableStateFlow(DatingFilterSettings().copy(distance = WORLDWIDE_DISTANCE))
    val datingFilters: StateFlow<DatingFilterSettings> get() = _datingFilters

    private val _blockedUsers = MutableStateFlow<List<String>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> get() = _isLoading

    // progress percentage for profile refresh/loading
    private val _loadingProgress = MutableStateFlow(0)
    val loadingProgress: StateFlow<Int> get() = _loadingProgress

    private var profilesListener: ValueEventListener? = null
    private var complimentsRef: DatabaseReference? = null
    private var complimentsListener: ValueEventListener? = null
    private var boostsRef: DatabaseReference? = null
    private var boostsListener: ValueEventListener? = null

    private val auth = FirebaseAuth.getInstance()
    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        firebaseAuth.currentUser?.uid?.let { me ->
            loadFilters()
            viewModelScope.launch {
                _blockedUsers.value = fetchBlockedUsers(me)
                _complimentsLeft.value = fetchComplimentsBalance(me)
            }
            updateBoostedUsers(me)
            loadCompliments(me)
            refreshFilteredProfiles()
        }
    }

    // ─── NEW: track compliments sent *to* me ─────────────
    private val _complimentsReceived = MutableStateFlow<Map<String, ComplimentData>>(emptyMap()) // ← NEW
    val complimentsReceived: StateFlow<Map<String, ComplimentData>> get() = _complimentsReceived // ← NEW

    // ── NEW: track which user is currently on top of the swipe‐deck ──
    private val _currentSwipeUserId = MutableStateFlow<String?>(null)
    /** The userId of the profile card currently “on deck” */
    val currentSwipeUserId: StateFlow<String?> = _currentSwipeUserId

    /** update the ID of the profile currently shown on top of the deck  */
    fun setCurrentSwipeUserId(userId: String?) {
        Log.d(TAG, "VM – setCurrentSwipeUserId → $userId")
        _currentSwipeUserId.value = userId
    }


    private val _boostedUsers      = MutableStateFlow<List<Profile>>(emptyList())
    val boostedUsers: StateFlow<List<Profile>>      get() = _boostedUsers

    private val _userDistanceMap   = MutableStateFlow<Map<String, Float>>(emptyMap())
    val userDistanceMap: StateFlow<Map<String, Float>> get() = _userDistanceMap

    // 1) the un-wrapped filter logic exactly as before
    private val baseFiltered = combine(
        _allProfiles,
        _datingFilters,
        _blockedUsers
    ) { profiles, filters, blocked ->
        applyDatingFilters(profiles, filters, blocked)
    }

    // 2) the “freeze while loading” wrapper
    val displayingProfiles: StateFlow<List<Profile>> =
        combine(baseFiltered, _isLoading) { newList, loading ->
            when {
                loading && newList.isEmpty() -> emptyList()
                loading -> lastNonEmptyProfiles
                else -> {
                    if (newList.isNotEmpty()) lastNonEmptyProfiles = newList
                    newList
                }
            }
        }
            .stateIn(
                scope   = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )


    // ── init() is unchanged except we no longer call startRealtimeProfilesListener() ──
    init {
        auth.addAuthStateListener(authListener)
    }

    private fun loadFilters() {
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            try {
                /* grab profile + filters in one round-trip */
                val userSnap  = usersRef.child(uid).get().await()
                val profile   = userSnap.getValue(Profile::class.java)
                val filtersDb = userSnap.child("datingFilters")
                    .getValue(DatingFilterSettings::class.java)
                    ?: DatingFilterSettings().copy(distance = WORLDWIDE_DISTANCE)

                /* ▶︎ if gender not set yet, seed it from profile.interestedIn */
                val seededFilters = if (filtersDb.gender.isBlank()) {
                    val list = profile?.interestedIn.orEmpty()
                        .filter { it.isNotBlank() }

                    if (list.isNotEmpty()) {
                        filtersDb.copy(gender = list.joinToString(",")).also { updated ->
                            // persist the new default so we don’t do this again
                            usersRef.child(uid)
                                .child("datingFilters")
                                .setValue(updated)
                        }
                    } else filtersDb
                } else filtersDb

                _datingFilters.value = seededFilters          // flow update
            } catch (e: Exception) {
                Log.e(TAG, "Error loading filters: ${e.message}", e)
            }
        }
    }
    fun sendCompliment(
        receiverId: String,
        textMessage: String?,
        voiceUri: Uri?,
        profileViewModel: ProfileViewModel
    ) {
        viewModelScope.launch {
            val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            val timestamp = System.currentTimeMillis()

            val complimentRef =
                database.getReference("compliments/$senderId/$receiverId")
            val complimentReceivedRef =
                database.getReference("complimentsReceived/$receiverId/$senderId")

            val complimentData = hashMapOf<String, Any>(
                "timestamp" to timestamp,
                "text" to textMessage.orEmpty()
            )

            // Upload voice if present
            if (voiceUri != null) {
                val storageRef = FirebaseStorage.getInstance()
                    .getReference("complimentsVoices/$senderId/${UUID.randomUUID()}.aac")
                val uploadResult = storageRef.putFile(voiceUri).await()
                val voiceUrl = uploadResult.storage.downloadUrl.await().toString()
                complimentData["voiceUrl"] = voiceUrl
            }

// write both trees ----------------------------------------------------------
            complimentRef.setValue(complimentData)
            complimentReceivedRef.setValue(complimentData)

            profileViewModel.sendComplimentNotification(senderId, receiverId)

            /* ▼▼▼ 2-d: burn one compliment quota & update the StateFlow ▼▼▼ */
            val leftNow = (_complimentsLeft.value - 1).coerceAtLeast(0)
            database.getReference("users/$senderId")
                .child("availableCompliments")
                .setValue(leftNow)
            _complimentsLeft.value = leftNow
            /* ▲▲▲ --------------------------------------------------------------------- */

            // refresh the map of compliments received (optional but nice)
            loadCompliments(senderId)

// continue with your existing swipe-right logic
            handleSwipeRight(senderId, receiverId, profileViewModel)
        }
    }

    /** Call this once (e.g. from DatingScreen’s LaunchedEffect) */
    fun startInventoryWatcher(uid: String) {
        val root = FirebaseDatabase.getInstance().reference.child("users/$uid")

        val compliments = root.child("availableCompliments")
        val complimentsL = simpleIntListener { _complimentsLeft.value = it }
        complimentsRef = compliments
        complimentsListener = complimentsL
        compliments.addValueEventListener(complimentsL)

        val boosts = root.child("availableBoosts")
        val boostsL = simpleIntListener { _boostsLeft.value = it }
        boostsRef = boosts
        boostsListener = boostsL
        boosts.addValueEventListener(boostsL)
    }

    private fun stopInventoryWatcher() {
        complimentsListener?.let { l -> complimentsRef?.removeEventListener(l) }
        complimentsListener = null
        complimentsRef = null
        boostsListener?.let { l -> boostsRef?.removeEventListener(l) }
        boostsListener = null
        boostsRef = null
    }
    /** helper that turns a ValueEventListener into a one-liner */
    private fun simpleIntListener(setter: (Int) -> Unit) =
        object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                setter(s.getValue(Int::class.java) ?: 0)
            }
            override fun onCancelled(e: DatabaseError) { /* ignore */ }
        }


    suspend fun fetchComplimentsBalance(userId: String): Int {
        val ref  = database.getReference("users/$userId/availableCompliments")
        return ref.get().await().getValue(Int::class.java) ?: 0
    }

    // ─── NEW: load complimentsReceived/$me into _complimentsReceived ───────────
    private fun loadCompliments(receiverId: String) { // ← NEW
        viewModelScope.launch {
            try {
                val snap = database
                    .getReference("complimentsReceived/$receiverId")
                    .get()
                    .await()

                val map = snap.children.associate { child ->
                    val fromId = child.key!!
                    val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val text = child.child("text").getValue(String::class.java).orEmpty()
                    val voice = child.child("voiceUrl").getValue(String::class.java)
                    fromId to ComplimentData(text = text, voiceUrl = voice, timestamp = ts)
                }
                _complimentsReceived.value = map
            } catch (e: Exception) {
                Log.e(TAG, "Error loading compliments: ${e.message}")
            }
        }
    } // ← NEW

    /**
     * Update and save filters in Firebase
     */
    fun updateDatingFilters(updatedFilters: DatingFilterSettings) {
        viewModelScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            val previous = _datingFilters.value
            try {
                usersRef.child(userId).child("datingFilters").setValue(updatedFilters).await()
                _datingFilters.value = updatedFilters
                refreshFilteredProfiles()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating filters: ${e.message}", e)
                _datingFilters.value = previous
            }
        }
    }
    /**
     * Refresh profiles manually
     */
    fun refreshFilteredProfiles() {
        viewModelScope.launch {
            _isLoading.value = true
            _loadingProgress.value = 0
            val me = FirebaseAuth.getInstance().currentUser?.uid

            try {
                if (me == null) {
                    _allProfiles.value = emptyList()
                    return@launch
                }

                val maxDist = _datingFilters.value.distance
                coroutineScope {
                    val blockedDeferred = async { fetchBlockedUsers(me) }
                    val complimentsDeferred = async { fetchGlobalComplimenters(me) }
                    val boostedDeferred = async { fetchGlobalBoostedUsers() }
                    val premiumDeferred = async { fetchGlobalPremiumUsers() }
                    val nearbyDeferred = async { fetchNearbyProfilesCloud(me, maxDist) }

                    _blockedUsers.value = blockedDeferred.await()
                    _loadingProgress.value = 25
                    val globalCompliments = complimentsDeferred.await()
                    _loadingProgress.value = 40
                    val globalBoosted = boostedDeferred.await()
                    _loadingProgress.value = 55
                    val globalPremium = premiumDeferred.await()
                    _loadingProgress.value = 70
                    val list = nearbyDeferred.await()
                    _loadingProgress.value = 85
                    val merged = (globalCompliments + globalBoosted + globalPremium + list)
                        .distinctBy { it.userId }
                    _allProfiles.value = merged.filterNot { it.userId == me }
                }

                updateBoostedUsers(me)
                loadCompliments(me)
            } catch (e: Exception) {
                Log.e(TAG, "refreshFilteredProfiles() failed: ${e.message}", e)
            } finally {
                _isLoading.value = false
                _loadingProgress.value = 100
            }
        }
    }

    private val functions = FirebaseFunctions.getInstance("asia-south1")

    private suspend fun fetchNearbyProfilesCloud(
        me: String,
        maxDistanceKm: Int
    ): List<Profile> = withContext(Dispatchers.IO) {


        val payload = hashMapOf(
            "uid"         to me,
            "maxDistance" to maxDistanceKm
        )
        Log.d("VM", "➡️  Calling getNearbyProfiles with $payload")

        // 1️⃣  get the callable reference …
        val callable: HttpsCallableReference =
            functions.getHttpsCallable("getNearbyProfiles")

        // … 2️⃣  and adjust its timeout (default is 60 s)
        callable.setTimeout(60, TimeUnit.SECONDS)     // 2 minutes

        // 3️⃣  invoke the function
        @Suppress("UNCHECKED_CAST")
        val data = callable.call(payload).await().data as? Map<*, *> ?: return@withContext emptyList()

        val list = data["profiles"] as? List<*> ?: return@withContext emptyList()

        list.mapNotNull { (it as? Map<*, *>)?.toProfile() }
    }

    private suspend fun fetchGlobalBoostedUsers(): List<Profile> = withContext(Dispatchers.IO) {
        val callable = functions.getHttpsCallable("getGlobalBoostedUsers")
        callable.setTimeout(60, TimeUnit.SECONDS)
        @Suppress("UNCHECKED_CAST")
        val data = callable.call().await().data as? Map<*, *> ?: return@withContext emptyList()
        val list = data["profiles"] as? List<*> ?: return@withContext emptyList()
        list.mapNotNull { (it as? Map<*, *>)?.toProfile() }
    }

    private suspend fun fetchGlobalPremiumUsers(): List<Profile> = withContext(Dispatchers.IO) {
        val callable = functions.getHttpsCallable("getGlobalPremiumUsers")
        callable.setTimeout(60, TimeUnit.SECONDS)
        @Suppress("UNCHECKED_CAST")
        val data = callable.call().await().data as? Map<*, *> ?: return@withContext emptyList()
        val list = data["profiles"] as? List<*> ?: return@withContext emptyList()
        list.mapNotNull { (it as? Map<*, *>)?.toProfile() }
    }

    private suspend fun fetchGlobalComplimenters(uid: String): List<Profile> = withContext(Dispatchers.IO) {
        val callable = functions.getHttpsCallable("getGlobalComplimenters")
        callable.setTimeout(60, TimeUnit.SECONDS)
        val payload = hashMapOf("uid" to uid)

        @Suppress("UNCHECKED_CAST")
        val data =
            callable.call(payload).await().data as? Map<*, *> ?: return@withContext emptyList()
        val list = data["profiles"] as? List<*> ?: return@withContext emptyList()
        list.mapNotNull { (it as? Map<*, *>)?.toProfile() }
    }

    /** call this when the user presses “Boost” */
    fun boostUser(
        targetUserId: String,
        onFinished: () -> Unit = {}
    ) {
        val me = FirebaseAuth.getInstance().uid ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            // 1) decrement my boost-count and record my lastBoostTimestamp
            usersRef.child(me).apply {
                child("availableBoosts").get().await().getValue(Long::class.java)?.let { current ->
                    child("availableBoosts").setValue((current - 1).coerceAtLeast(0L)).await()
                }
                child("lastBoostTimestamp").setValue(now).await()
            }

            // 2) set the other user’s isBoosted + boostedAt
            usersRef.child(targetUserId).apply {
                child("isBoosted").setValue(true).await()
                child("boostedAt").setValue(now).await()
            }

            // 3) refresh your boosted list
            updateBoostedUsers(me)
            refreshFilteredProfiles()               // <-- add this
            onFinished()
        }
    }

    /** rebuilds `_boostedUsers`, dropping any >6 h old and notifying owners */
    private fun updateBoostedUsers(currentUserId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            /* 1️⃣  Find boosts that just expired */
            val expiredUids = _allProfiles.value
                .filter { it.isBoosted && (it.boostedAt == null || now - it.boostedAt!! > BOOST_DURATION_MS) }
                .map { it.userId }

            /* 2️⃣  Clear their isBoosted flag and notify them */
            expiredUids.forEach { uid ->
                usersRef.child(uid).child("isBoosted").setValue(false)
                pushBoostOverNotification(uid)              // 🔔
            }

            /* 3️⃣  Keep only still-valid boosts for the deck UI */
            val boosted = _allProfiles.value.filter {
                it.isBoosted && it.boostedAt != null && now - it.boostedAt!! <= BOOST_DURATION_MS
            }

            _boostedUsers.value = boosted // preserve arrival/boostedAt order
        }
    }

    private suspend fun pushBoostOverNotification(receiverId: String) {
        val notifRef = FirebaseRefs.db.getReference("notifications")
        val id       = notifRef.child(receiverId).push().key ?: return
        val payload  = Notification(
            id             = id,
            type           = "boost_over",
            senderId       = FirebaseAuth.getInstance().uid ?: "",
            senderUsername = "Kupidx",
            message        = "Your Boost has ended. Ready for another? ⚡",
            timestamp      = System.currentTimeMillis(),
            isRead         = "false"
        )
        notifRef.child(receiverId).child(id).setValue(payload).await()
    }


    private suspend fun applyDatingFilters(profiles: List<Profile>, filters: DatingFilterSettings, blocked:  List<String> // ← NEW
    ): List<Profile> = coroutineScope {
        Log.d(TAG, "⚙️ FILTER DUMP  ->  $filters")   // ← add

        // Fetch blocked users
        var result = profiles.filterNot { blocked.contains(it.userId) }

        // Apply localities filter
        if (filters.localities.isNotEmpty()) {
            val normalizedLocs = filters.localities.map { it.replace("\\s".toRegex(), "").lowercase() }
            result = result.filter { p ->
                val raw = p.hometown.ifBlank { p.customHometown.orEmpty() }
                val profLoc = raw.replace("\\s".toRegex(), "").lowercase()
                normalizedLocs.contains(profLoc)
            }
        }


        if (filters.city.isNotBlank() && filters.city != "All") {
            val target = filters.city.replace("\\s".toRegex(), "").lowercase()
            result = result.filter {
                it.city.replace("\\s".toRegex(), "").lowercase() == target
            }
        }

        // Apply high school filter
        if (filters.highSchool.isNotBlank()) {
            result = result.filter { profile ->
                profile.highSchool?.equals(filters.highSchool, ignoreCase = true) == true
            }
        }

        // Apply college filter
        if (filters.college.isNotBlank()) {
            result = result.filter { profile ->
                profile.college?.equals(filters.college, ignoreCase = true) == true
            }
        }

        // Apply post-grad filter
        if (filters.postGrad.isNotBlank()) {
            result = result.filter { profile ->
                profile.postGraduation?.equals(filters.postGrad, ignoreCase = true) == true
            }
        }

        // Apply community filter
        if (filters.community.isNotBlank()) {
            result = result.filter { profile ->
                profile.community.equals(filters.community, ignoreCase = true)
            }
        }

        // Apply religion filter
        if (filters.religion.isNotBlank()) {
            result = result.filter { profile ->
                profile.religion.equals(filters.religion, ignoreCase = true)
            }
        }

        // Apply caste filter
        if (filters.caste.isNotBlank()) {
            result = result.filter { profile ->
                profile.caste.equals(filters.caste, ignoreCase = true)
            }
        }

        // Apply work filter
        if (filters.work.isNotBlank()) {
            result = result.filter { profile ->
                profile.work?.equals(filters.work, ignoreCase = true) == true
            }
        }

        // Apply ethnicity filter
        if (filters.ethnicity.isNotBlank()) {
            result = result.filter { profile ->
                profile.ethnicity.equals(filters.ethnicity, ignoreCase = true)
            }
        }

        // Apply income level filter
        if (filters.incomeLevel.isNotBlank()) {
            result = result.filter { profile ->
                profile.incomeLevel.equals(filters.incomeLevel, ignoreCase = true)
            }
        }

        // Apply age range filter
        result = result.filter { profile ->
// keep if age unknown  OR  within range
            val age = profile.dob.takeIf { it.isNotBlank() }?.let { calculateAge(it) }
            if (age == null || age in filters.ageStart..filters.ageEnd)
                true  else false
        }

        // ── NEW: Minimum ⭐ Rating  (Plus & Premium)
        if (filters.minRating > 0f) {
            result = result.filter { it.averageRating >= filters.minRating }
        }

        // ── NEW: Top-N 🏆 Ranking  (Premium only)
        if (filters.maxRanking > 0) {
            result = result.filter { it.am24Ranking == 0 || it.am24Ranking <= filters.maxRanking }
        }
        // Apply gender filter
        if (filters.gender.isNotBlank()) {
            val genderIds = filters.gender.split(",")
                .mapNotNull { canonicalGenderRes(it) }
            result = result.filter { profile ->
                val gId = canonicalGenderRes(profile.gender)
                when (gId) {
                    null -> true
                    R.string.male_option -> genderIds.contains(R.string.male_option)
                    R.string.female_option -> genderIds.contains(R.string.female_option)
                    else -> genderIds.any {
                        it == R.string.male_option || it == R.string.female_option
                    }
                }
            }
        }

        // Apply sexual orientation filter
        if (filters.sexualOrientation.isNotBlank()) {
            result = result.filter { profile ->
                profile.sexualOrientation.equals(filters.sexualOrientation, ignoreCase = true)
            }
        }

        // Apply distance filter and populate distance map
        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null) {
            val distanceMap = mutableMapOf<String, Float>()
            val filteredByDistance = mutableListOf<Profile>()

            for (profile in result) {
                val dist = distanceBetween(currentUserId, profile.userId, geoFire)
                if (dist != null) {
                    distanceMap[profile.userId] = dist
                }
                val withinRange = filters.distance == WORLDWIDE_DISTANCE ||
                        dist == null || dist <= filters.distance.toFloat()
                if (withinRange) {
                    filteredByDistance.add(profile)
                }
            }

            _userDistanceMap.value = distanceMap
            result = filteredByDistance
        }

        return@coroutineScope result
    }

    /** in‑process LRU for distance look‑ups (key is the *sorted* pair) */
    private val distanceCache = object : LinkedHashMap<Pair<String,String>, Float>(150, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String,String>, Float>?): Boolean =
            size > 150        // keep the last ~150 pairs (~10 kB)
    }

    /** one‑shot helper that returns a cached value or runs the expensive call once */
    suspend fun distanceBetween(uidA: String, uidB: String, geoFire: GeoFire): Float? {
        val key = if (uidA < uidB) uidA to uidB else uidB to uidA   // a⇄b and b⇄a are the same lookup
        distanceCache[key]?.let { return it }

        // not cached → hit the network once
        val d = calculateDistance(uidA, uidB, geoFire)
        if (d != null) distanceCache[key] = d
        return d
    }

    private val _verificationStatuses =
        MutableStateFlow<Map<String,String>>(emptyMap())
    val verificationStatuses: StateFlow<Map<String,String>>
            = _verificationStatuses
    private val db = FirebaseDatabase.getInstance().reference

    /** one-off load for a single uid */
    fun loadVerification(uid: String) = viewModelScope.launch {
        val status = try {
            db.child("verifications")
                .child(uid)
                .child("status")
                .get()
                .await()
                .getValue(String::class.java)
                ?: "pending"
        } catch(e: Exception) {
            "pending"
        }
        _verificationStatuses.update { it + (uid to status) }
    }


    // Fetch the list of blocked user IDs
    private suspend fun fetchBlockedUsers(userId: String): List<String> {
        return try {
            val snapshot = database.getReference("blocks/$userId").get().await()
            snapshot.children.mapNotNull { it.key }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching blocked users: ${e.message}")
            emptyList()
        }
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        super.onCleared()
        profilesListener?.let { usersRef.removeEventListener(it) }
        stopInventoryWatcher()
    }


    /** Increment compliments quota locally when rewarded. */
    fun incrementComplimentsLocal() {
        _complimentsLeft.value = _complimentsLeft.value + 1
    }

    /** Increment boosts quota locally when rewarded. */
    fun incrementBoostsLocal() {
        _boostsLeft.value = _boostsLeft.value + 1
    }


    /** Called when the user hits “Submit” in the report dialog */
    fun reportUser(
        reporterId: String,
        reporteeId: String,
        reason: String
    ) {
        viewModelScope.launch {
            Log.d(TAG, "VM – reporting $reporteeId for reason “$reason”")
            val now = System.currentTimeMillis()
            // 1) save report
            database.getReference("reports/$reporteeId/$reporterId")
                .setValue(mapOf("reason" to reason, "timestamp" to now))
                .await()

            // 2) block them
            database.getReference("blocks/$reporterId/$reporteeId")
                .setValue(true)
                .await()

            // 3) update your local blocked list & refresh
            _blockedUsers.value = _blockedUsers.value + reporteeId
            refreshFilteredProfiles()
            Log.d(TAG, "VM – reportUser complete")
        }
    }
}

