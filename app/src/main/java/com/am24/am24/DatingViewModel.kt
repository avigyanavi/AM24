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
import com.am24.am24.calculateAge
import com.am24.am24.calculateDistance
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
        private const val BOOST_DURATION_MS = 6 * 60 * 60 * 1000L
        /* NEW ── sentinel to mean “don’t filter by distance / Worldwide” */
        const val WORLDWIDE_DISTANCE = 101
        private const val DESIRED_MIN_ROWS       = 50
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

    private var profilesListener: ValueEventListener? = null
    private var complimentsRef: DatabaseReference? = null
    private var complimentsListener: ValueEventListener? = null
    private var boostsRef: DatabaseReference? = null
    private var boostsListener: ValueEventListener? = null

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
            if (loading) {
                // still waiting for the network → keep showing whatever was last non-empty
                lastNonEmptyProfiles
            } else {
                // network done → if we got something non-empty, cache it
                if (newList.isNotEmpty()) lastNonEmptyProfiles = newList
                newList
            }
        }
            .stateIn(
                scope   = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )


    // ── init() is unchanged except we no longer call startRealtimeProfilesListener() ──
    init {
        loadFilters()
        FirebaseAuth.getInstance().currentUser?.uid?.let { me ->
            viewModelScope.launch {
                _blockedUsers.value    = fetchBlockedUsers(me)    // ← NEW (must precede refresh)
                _complimentsLeft.value = fetchComplimentsBalance(me)
            }
            updateBoostedUsers(me)
            loadCompliments(me)
        }
        refreshFilteredProfiles()    // keep this AFTER the launch block        }
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
            _datingFilters.value = updatedFilters
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            try {
                usersRef.child(userId).child("datingFilters").setValue(updatedFilters).await()
                refreshFilteredProfiles()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating filters: ${e.message}")
            }
        }
    }
        /**
     * Refresh profiles manually
     */
        fun refreshFilteredProfiles() {
            viewModelScope.launch {
                _isLoading.value = true
                val me = FirebaseAuth.getInstance().currentUser?.uid

                try {
                    if (me == null) {
                        _allProfiles.value = emptyList()
                        return@launch
                    }

                    // refresh blocks first
                    _blockedUsers.value = fetchBlockedUsers(me)

                    // 👉 call the Cloud Function instead of the local helper
                    val maxDist = _datingFilters.value.distance
                    _allProfiles.value = fetchNearbyProfilesCloud(me, maxDist)

                    updateBoostedUsers(me)
                    loadCompliments(me)
                } catch (e: Exception) {
                    Log.e(TAG, "refreshFilteredProfiles() failed: ${e.message}", e)
                } finally {
                    _isLoading.value = false
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
            "maxDistance" to maxDistanceKm,
            "minRows"     to DESIRED_MIN_ROWS
        )
        Log.d("VM", "➡️  Calling getNearbyProfiles with $payload")

        // 1️⃣  get the callable reference …
        val callable: HttpsCallableReference =
            functions.getHttpsCallable("getNearbyProfiles")

        // … 2️⃣  and adjust its timeout (default is 60 s)
        callable.setTimeout(120, TimeUnit.SECONDS)     // 2 minutes

        // 3️⃣  invoke the function
        @Suppress("UNCHECKED_CAST")
        val data = callable.call(payload).await().data as? Map<*, *> ?: return@withContext emptyList()

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

            /* 4️⃣  Recompute distance map & StateFlows */
            val distMap = boosted.mapNotNull { p ->
                calculateDistance(currentUserId, p.userId, geoFire)?.let { p.userId to it }
            }.toMap()

            _userDistanceMap.value = distMap
            _boostedUsers.value    = boosted.sortedBy { distMap[it.userId] ?: Float.MAX_VALUE }
        }
    }

    private suspend fun pushBoostOverNotification(receiverId: String) {
        val notifRef = FirebaseRefs.db.getReference("notifications")
        val id       = notifRef.child(receiverId).push().key ?: return
        val payload  = Notification(
            id             = id,
            type           = "boost_over",
            senderId       = FirebaseAuth.getInstance().uid ?: "",
            senderUsername = "",
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
            result = result.filter { profile ->
                filters.localities.contains(profile.hometown)
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

        // Apply work filter
        if (filters.work.isNotBlank()) {
            result = result.filter { profile ->
                profile.work?.equals(filters.work, ignoreCase = true) == true
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
            val genders = filters.gender.split(",")   // ",Female,Male" → ["", "Female", "Male"]
            result = result.filter { profile ->
                profile.gender?.let { genders.contains(it) } == true
            }
        }

        // Apply distance filter
        if (filters.distance in 0 until WORLDWIDE_DISTANCE) {   // ✅ BEFORE it was 0..100
            result = filterByDistance(result, filters.distance)
        }

        return@coroutineScope result
    }

    private suspend fun filterByDistance(
        profiles: List<Profile>,
        maxDistance: Int
    ): List<Profile> = coroutineScope {

        val me = FirebaseAuth.getInstance().uid ?: return@coroutineScope profiles
        if (maxDistance >= WORLDWIDE_DISTANCE) return@coroutineScope profiles   // keep all

        val geoFire = GeoFire(FirebaseRefs.db.getReference("geoFireLocations"))
        val kept    = mutableListOf<Profile>()

        profiles.forEach { other ->
            launch {
                val d = calculateDistance(me, other.userId, geoFire)  // <-- may be null
                /* keep if distance is unknown **OR** ≤ slider value  */
                if (d == null || d <= maxDistance) {                  // <-- THIS LINE
                    synchronized(kept) { kept.add(other) }
                }
            }
        }
        kept
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
        super.onCleared()
        profilesListener?.let { usersRef.removeEventListener(it) }
        stopInventoryWatcher()
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

