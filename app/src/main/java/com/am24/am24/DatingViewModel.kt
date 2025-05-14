import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.am24.am24.DatingFilterSettings
import com.am24.am24.FirebaseRefs
import com.am24.am24.Profile
import com.am24.am24.ProfileViewModel
import com.am24.am24.calculateAge
import com.am24.am24.calculateDistance
import com.am24.am24.handleSwipeRight
import com.firebase.geofire.GeoFire
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
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
import com.firebase.geofire.GeoFireUtils          // 🔥 NEW
import com.firebase.geofire.GeoLocation          // 🔥 NEW
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.GenericTypeIndicator

// ─── NEW: data class for holding incoming compliment ───
data class ComplimentData(
    val text: String = "",
    val voiceUrl: String? = null,
    val timestamp: Long = 0L
) // ← NEW

class DatingViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val BOOST_DURATION_MS = 6 * 60 * 60 * 1000L
        internal const val COMPLIMENT_DAILY_QUOTA = 10
        /* NEW ── sentinel to mean “don’t filter by distance / Worldwide” */
        const val WORLDWIDE_DISTANCE = 100
        private val RADIUS_STEPS_KM              = listOf(20.0, 50.0, 70.0, 100.0) // 🔥 NEW
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

    private val _complimentsLeft = MutableStateFlow(COMPLIMENT_DAILY_QUOTA)
    val complimentsLeft: StateFlow<Int> get() = _complimentsLeft

    private val _datingFilters = MutableStateFlow(DatingFilterSettings().copy(distance = WORLDWIDE_DISTANCE))
    val datingFilters: StateFlow<DatingFilterSettings> get() = _datingFilters

    private val _blockedUsers = MutableStateFlow<List<String>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> get() = _isLoading

    private var profilesListener: ValueEventListener? = null

    // ─── NEW: track compliments sent *to* me ─────────────
    private val _complimentsReceived = MutableStateFlow<Map<String, ComplimentData>>(emptyMap()) // ← NEW
    val complimentsReceived: StateFlow<Map<String, ComplimentData>> get() = _complimentsReceived // ← NEW


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
                _complimentsLeft.value = loadAndResetComplimentsDaily(me)
            }
            updateBoostedUsers(me)
            loadCompliments(me)
        }
        refreshFilteredProfiles()    // keep this AFTER the launch block        }
    }

    /**
     * Load dating filters from Firebase
     */
    private fun loadFilters() {
        viewModelScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            try {
                val snapshot = usersRef.child(userId)
                    .child("datingFilters")
                    .get()
                    .await()
                snapshot.getValue(DatingFilterSettings::class.java)?.let {
                    _datingFilters.value = it
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading filters: ${e.message}")
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
    suspend fun loadAndResetComplimentsDaily(userId: String): Int {
        val ref   = database.getReference("users/$userId")
        val snap  = ref.get().await()

        var left  = snap.child("availableCompliments")
            .getValue(Int::class.java) ?: COMPLIMENT_DAILY_QUOTA
        var day   = snap.child("lastComplimentResetDayOfYear")
            .getValue(Int::class.java) ?: -1

        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (today != day) {
            left = COMPLIMENT_DAILY_QUOTA
            day  = today
            ref.child("availableCompliments").setValue(left)
            ref.child("lastComplimentResetDayOfYear").setValue(day)
        }
        return left
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

    private suspend fun fetchNearbyProfiles(
        me: String,
        maxDistanceKm: Int
    ): List<Profile> = coroutineScope {
        try {
        // 1️⃣ get *my* lat/lng from RTDB --------------------------------------
        val myLocSnap = database.getReference("geoFireLocations/$me/l").get().await()
        val latLng = myLocSnap.getValue(object : GenericTypeIndicator<List<Double>>() {})
        if (latLng == null || latLng.size < 2) {
            Log.w(TAG, "⚠️  user has no lat/lng – falling back to whole /users tree")
            return@coroutineScope usersRef.get().await().children
                .mapNotNull { it.getValue(Profile::class.java) }
                .filter { it.userId != me }
        }
        val myLocation = GeoLocation(latLng[0], latLng[1])

        // 2️⃣ progressive radius search until we have ~DESIRED_MIN_ROWS -------
        val steps = if (maxDistanceKm >= WORLDWIDE_DISTANCE)
            listOf(Double.MAX_VALUE)        // "world-wide"
        else
            RADIUS_STEPS_KM.filter { it <= maxDistanceKm.toDouble() }

        val collectedUids = linkedSetOf<String>()

        outer@ for (radius in steps) {
            val bounds = GeoFireUtils.getGeoHashQueryBounds(myLocation, radius * 1000)
            val uidTasks = bounds.map { b ->
                database.getReference("geoFireLocations")
                    .orderByChild("g")
                    .startAt(b.startHash)
                    .endAt(b.endHash)
                    .get()
            }

            // run all bounds queries in parallel and wait
            val snapshots = uidTasks.map { it.await() }
            for (snap in snapshots) {
                for (child in snap.children) {
                    val uid = child.key ?: continue
                    val hash = child.child("g").getValue(String::class.java) ?: continue
                    val locArr = child.child("l")
                        .getValue(object : GenericTypeIndicator<List<Double>>() {}) ?: continue

                    val candidate = GeoLocation(locArr[0], locArr[1])
                    val dist = GeoFireUtils.getDistanceBetween(myLocation, candidate) / 1000.0
                    if (dist <= radius) collectedUids += uid
                }
            }

            if (collectedUids.size >= DESIRED_MIN_ROWS || radius == steps.last())
                break@outer
        }

        // 3️⃣ batch-download the actual profile docs --------------------------
        if (collectedUids.isEmpty()) return@coroutineScope emptyList()

        val profileTasks = collectedUids.map { uid ->
            usersRef.child(uid).get()     // one Task<DataSnapshot> per UID
        }

        val snaps = profileTasks.map { it.await() }

        return@coroutineScope snaps.mapNotNull { snap ->
            snap.getValue(Profile::class.java)
        }.filter { it.userId != me }
    } catch (e: Exception) {
            Log.e(TAG, "fetchNearbyProfiles() failed: ${e.message}", e)
            emptyList()
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

                    // ✅ always refresh blocks first to prevent race conditions
                    _blockedUsers.value = fetchBlockedUsers(me)

                    val maxDist  = _datingFilters.value.distance
                    _allProfiles.value = fetchNearbyProfiles(me, maxDist)

                    updateBoostedUsers(me)
                    loadCompliments(me)
                } catch (e: Exception) {
                    Log.e(TAG, "refreshFilteredProfiles() failed: ${e.message}", e)
                } finally {
                    _isLoading.value = false
                }
            }
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

    /** rebuilds `_boostedUsers`, dropping any >6h old */
    private fun updateBoostedUsers(currentUserId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            // clean out expired boosts in Firebase too (optional)
            _allProfiles.value
                .filter { it.isBoosted && (it.boostedAt == null || now - it.boostedAt!! > BOOST_DURATION_MS) }
                .forEach {
                    usersRef.child(it.userId).child("isBoosted").setValue(false)
                }

            // only keep the fresh ones
            val boosted = _allProfiles.value.filter {
                it.isBoosted && it.boostedAt != null && now - it.boostedAt!! <= BOOST_DURATION_MS
            }

            // compute distances…
            val distMap = boosted.mapNotNull { p ->
                calculateDistance(currentUserId, p.userId, geoFire)
                    ?.let { p.userId to it }
            }.toMap()

            _userDistanceMap.value = distMap
            _boostedUsers.value    = boosted.sortedBy { distMap[it.userId] ?: Float.MAX_VALUE }
        }
    }

    private suspend fun applyDatingFilters(profiles: List<Profile>, filters: DatingFilterSettings, blocked:  List<String> // ← NEW
    ): List<Profile> = coroutineScope {
        Log.d(TAG, "⚙️ FILTER DUMP  ->  $filters")   // ← add

        // Fetch blocked users
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@coroutineScope emptyList()
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
        // 101 km or more ⇒ no distance filter at all
        if (maxDistance >= WORLDWIDE_DISTANCE) return@coroutineScope profiles

        val geoFire = GeoFire(FirebaseRefs.db.getReference("geoFireLocations"))
        val kept    = mutableListOf<Profile>()

        profiles.forEach { other ->
            launch {
                val d = calculateDistance(me, other.userId, geoFire)
                /* keep if  ➜ distance unknown  OR  distance ≤ slider value */
                if (d == null || d <= maxDistance) {
                    synchronized(kept) { kept.add(other) }
                }
            }
        }
        kept
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
    }
}

