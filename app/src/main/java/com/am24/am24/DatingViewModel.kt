import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.am24.am24.ComplimentWithProfile
import com.am24.am24.DmBootstrap
import com.am24.am24.ExclusionEventBus
import com.am24.am24.FirebaseRefs
import com.am24.am24.MatchSummary
import com.am24.am24.Message
import com.am24.am24.Notification
import com.am24.am24.Profile
import com.am24.am24.ProfileViewModel
import com.am24.am24.UserDeletionCache
import com.am24.am24.calculateDistance
import com.am24.am24.handleSwipeRight
import com.firebase.geofire.GeoFire
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val gson = com.google.gson.Gson()

private fun Map<*, *>.toProfile(): Profile =
    gson.fromJson(gson.toJson(this), Profile::class.java)

private fun normalizeLastActive(raw: Any?): Long? {
    val numeric = when (raw) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull()
        else -> null
    } ?: return null
    if (numeric <= 0) return null

    // Firebase Realtime Database writes timestamps in milliseconds (e.g., 1752518783622).
    // If a seconds-based value slips in, upscale it to keep the one-week cutoff accurate.
    return if (numeric < 10_000_000_000L) numeric * 1000 else numeric
}


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
        const val WORLDWIDE_DISTANCE = 3000
        const val INDIA_MAX_DISTANCE = 5000
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
    private var complimentsReceivedRef: DatabaseReference? = null
    private var complimentsReceivedListener: ValueEventListener? = null

    private val auth = FirebaseAuth.getInstance()
    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        firebaseAuth.currentUser?.uid?.let { me ->
            viewModelScope.launch {
                _blockedUsers.value = fetchBlockedUsers(me)
                _complimentsLeft.value = fetchComplimentsBalance(me)
            }
            updateBoostedUsers(me)
            startComplimentsListener(me)
            viewModelScope.launch { refreshDmBootstrap(me, force = true) }
            refreshFilteredProfiles()
        }
    }

    // ─── Compliments / DM bootstrap ─────────────
    private val _complimentsReceived = MutableStateFlow<Map<String, ComplimentData>>(emptyMap())
    val complimentsReceived: StateFlow<Map<String, ComplimentData>> get() = _complimentsReceived

    private val _dmBootstrap = MutableStateFlow<DmBootstrap?>(null)
    val dmBootstrap: StateFlow<DmBootstrap?> = _dmBootstrap.asStateFlow()

    private var lastDmBootstrapFetch = 0L

    // ── NEW: track which user is currently on top of the swipe‐deck ──
    private val _currentSwipeUserId = MutableStateFlow<String?>(null)

    /** update the ID of the profile currently shown on top of the deck  */
    fun setCurrentSwipeUserId(userId: String?) {
        Log.d(TAG, "VM – setCurrentSwipeUserId → $userId")
        _currentSwipeUserId.value = userId
    }


    private val _boostedUsers      = MutableStateFlow<List<Profile>>(emptyList())
    val boostedUsers: StateFlow<List<Profile>>      get() = _boostedUsers


    private val baseFiltered = combine(
        _allProfiles,
        _blockedUsers
    ) { profiles, blocked ->
        profiles.filterNot { it.userId in blocked }
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

    fun sendCompliment(
        receiverId: String,
        textMessage: String?,
        profileViewModel: ProfileViewModel
    ) {
        viewModelScope.launch {
            try {
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

// continue with your existing swipe-right logic
                handleSwipeRight(senderId, receiverId, profileViewModel)
                ExclusionEventBus.emit(receiverId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send compliment: ${e.message}", e)
            }
        }
    }

    /** Call this once from whichever surface first needs compliment/boost inventory (chat, DMs, etc.). */
    fun startInventoryWatcher(uid: String) {
        val root = FirebaseRefs.db.reference.child("users/$uid")

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
    private fun startComplimentsListener(uid: String) {
        stopComplimentsListener()
        val ref = database.getReference("complimentsReceived/$uid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = snapshot.children.associate { child ->
                    val fromId = child.key!!
                    val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val text = child.child("text").getValue(String::class.java).orEmpty()
                    fromId to ComplimentData(text = text, timestamp = ts)
                }
                _complimentsReceived.value = map
                refreshDmBootstrap(uid)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "compliments listener cancelled: ${error.message}")
            }
        } // ← NEW
        complimentsReceivedRef = ref
        complimentsReceivedListener = listener
        ref.addValueEventListener(listener)
    }

    private fun stopComplimentsListener() {
        complimentsReceivedListener?.let { l ->
            complimentsReceivedRef?.removeEventListener(l)
        }
        complimentsReceivedListener = null
        complimentsReceivedRef = null
    }

    private fun refreshDmBootstrap(uid: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastDmBootstrapFetch < 2_000) return
        lastDmBootstrapFetch = now
        viewModelScope.launch(Dispatchers.IO) {
            fetchDmBootstrap(uid)
        }
    }

    private suspend fun fetchDmBootstrap(uid: String) {
        runCatching {
            val payload = hashMapOf(
                "limitMatches" to 30,
                "limitCompliments" to 15
            )
            val callable = functions.getHttpsCallable("fetchDmBootstrap").apply {
                setTimeout(60, TimeUnit.SECONDS)
            }
            val data = callable.call(payload).await().data as? Map<*, *> ?: return
            val compliments = (data["compliments"] as? List<*>)
                ?.mapNotNull { it.toComplimentWithProfile() }
                ?: emptyList()
            val matches = (data["matches"] as? List<*>)
                ?.mapNotNull { it.toMatchSummary() }
                ?: emptyList()
            val likedCount = (data["likedCount"] as? Number)?.toInt() ?: 0
            _dmBootstrap.value = DmBootstrap(compliments, matches, likedCount)
        }.onFailure { err ->
            Log.w(TAG, "fetchDmBootstrap failed: ${err.message}")
            val fallbackCompliments = fetchComplimentsFallback(uid)
            _dmBootstrap.value = DmBootstrap(compliments = fallbackCompliments)
        }
    }

    private suspend fun fetchComplimentsFallback(uid: String): List<ComplimentWithProfile> = withContext(Dispatchers.IO) {
        val compliments = _complimentsReceived.value
        if (compliments.isEmpty()) return@withContext emptyList<ComplimentWithProfile>()
        compliments.mapNotNull { (senderId, compliment) ->
            runCatching {
                val snap = usersRef.child(senderId).get().await()
                if (UserDeletionCache.isDeleted(FirebaseRefs.db, senderId, snap)) return@mapNotNull null
                val profile = snap.getValue(Profile::class.java) ?: return@mapNotNull null
                if (profile.username.isBlank()) {
                    UserDeletionCache.markDeleted(senderId)
                    return@mapNotNull null
                }
                UserDeletionCache.markActive(senderId)
                ComplimentWithProfile(profile, compliment)
            }.getOrNull()
        }.sortedByDescending { it.compliment.timestamp }
    }

    private fun Any?.toComplimentWithProfile(): ComplimentWithProfile? {
        val map = this as? Map<*, *> ?: return null
        val profileMap = map["profile"] as? Map<*, *> ?: return null
        val complimentMap = map["compliment"] as? Map<*, *> ?: return null
        val profile = profileMap.toProfile()
        val compliment = ComplimentData(
            text = complimentMap["text"] as? String ?: "",
            voiceUrl = complimentMap["voiceUrl"] as? String,
            timestamp = (complimentMap["timestamp"] as? Number)?.toLong() ?: 0L
        )
        return ComplimentWithProfile(profile, compliment)
    }

    private fun Any?.toMatchSummary(): MatchSummary? {
        val map = this as? Map<*, *> ?: return null
        val profileMap = map["profile"] as? Map<*, *> ?: return null
        val profile = profileMap.toProfile()
        val hasUnread = map["hasUnread"] as? Boolean ?: false
        val messageMap = map["lastMessage"] as? Map<*, *>
        val message = messageMap?.toMessage()
        return MatchSummary(profile, message, hasUnread)
    }

    private fun Map<*, *>.toMessage(): Message = Message(
        id = this["id"] as? String ?: "",
        senderId = this["senderId"] as? String ?: "",
        receiverId = this["receiverId"] as? String ?: "",
        text = this["text"] as? String ?: "",
        timestamp = (this["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        read = this["read"] as? Boolean ?: false,
        mediaType = this["mediaType"] as? String,
        mediaUrl = this["mediaUrl"] as? String,
        processed = this["processed"] as? Boolean ?: false,
        isPost = this["isPost"] as? Boolean ?: false
    )

    // ── COUNTRY-ONLY REFRESH ────────────────────────────────────────────────
    private val refreshMutex = Mutex()
    fun refreshFilteredProfiles() {
        viewModelScope.launch {
            if (!refreshMutex.tryLock()) return@launch
            _isLoading.value = true
            _loadingProgress.value = 0
            val me = FirebaseAuth.getInstance().currentUser?.uid

            try {
                if (me == null) {
                    _allProfiles.value = emptyList()
                    return@launch
                }

                coroutineScope {
                    // load user country + tier
                    val blockedDeferred = async { fetchBlockedUsers(me) }
                    val limit = Int.MAX_VALUE

                    _blockedUsers.value = blockedDeferred.await()
                    _loadingProgress.value = 30

                    val paidProfiles = fetchGlobalPremiumUsers(me, limit)
                    _loadingProgress.value = 80

                    // order: Premium → Plus → Rest; remove me; dedupe by userId; cap to limit
                    val ordered = paidProfiles
                        .filter { it.userId != me && it.userId.isNotBlank() }
                        .distinctBy { it.userId }
                        .let { list ->
                            val prem = list.filter { it.isPremium }
                            val plus = list.filter { !it.isPremium && it.isPlus }
                            prem + plus
                        }
                    _allProfiles.value = ordered
                }

                updateBoostedUsers(me)
            } catch (e: Exception) {
                Log.e(TAG, "refreshFilteredProfiles(paid) failed: ${e.message}", e)
            } finally {
                _isLoading.value = false
                _loadingProgress.value = 100
                refreshMutex.unlock() // IMPORTANT: release
            }
        }
    }

    // ── Cloud + fallback for country fetch ─────────────────────────────────
    private val functions = FirebaseFunctions.getInstance("asia-south1")

    private suspend fun fetchGlobalPremiumUsers(
        me: String,
        limit: Int
    ): List<Profile> = withContext(Dispatchers.IO) {
        val snap = usersRef
            .orderByChild("priority")
            .equalTo(true)
            .limitToFirst(limit)
            .get()
            .await()

        snap.children.mapNotNull { child ->
            val profile = child.getValue(Profile::class.java) ?: return@mapNotNull null
            profile.userId = profile.userId.ifBlank { child.key.orEmpty() }
            if (profile.priority) profile else null
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

    private fun sanitizeText(input: String?, max: Int = 64): String {
        if (input == null) return ""
        val cleaned = input.replace(Regex("[\\r\\n\\t]"), " ").trim()
        return cleaned.take(max)
    }


    /** in‑process LRU for distance look‑ups (key is the *sorted* pair) */
    private val distanceCache = object : LinkedHashMap<Pair<String,String>, Float>(150, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String,String>, Float>?): Boolean =
            size > 150        // keep the last ~150 pairs (~10 kB)
    }


    // thread-safe local cache
    private val distanceCacheMap = ConcurrentHashMap<Pair<String, String>, Float>()

    /** Read-only accessor for UI code */
    fun getCachedDistance(me: String, other: String): Float? =
        distanceCacheMap[me to other]

    /** Public prefetch that DOES NOT touch Compose state */
    suspend fun prefetchDistance(me: String, other: String, geoFire: GeoFire) {
        val key = me to other
        if (distanceCacheMap.containsKey(key)) return
        val d = calculateDistance(me, other, geoFire) ?: return
        distanceCacheMap[key] = d
    }

    /** Optional: make your existing distanceBetween also populate the cache */
    suspend fun distanceBetween(me: String, other: String, geoFire: GeoFire): Float {
        val key = me to other
        distanceCacheMap[key]?.let { return it }
        val d = calculateDistance(me, other, geoFire) ?: Float.NaN
        if (!d.isNaN()) distanceCacheMap[key] = d
        return d
    }

    private val _verificationStatuses =
        MutableStateFlow<Map<String,String>>(emptyMap())
    val verificationStatuses: StateFlow<Map<String,String>>
            = _verificationStatuses
    private val db = FirebaseRefs.db.reference

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
        stopComplimentsListener()
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

