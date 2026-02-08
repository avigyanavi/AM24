package com.am24.am24

import android.util.Log
import com.am24.am24.FirebaseRefs.db
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
/**
 * Centralised session cache that keeps long-lived Firebase data warm while the
 * user is signed in. Screens can observe the exposed [StateFlow]s instead of
 * executing their own `get().await()` calls on every composition.
 */
object SessionDataRepository {

    private val TAG = "SessionDataRepo"
    private const val MAX_BLOCKS = 1000
    private const val MAX_MATCHES = 1000
    private const val MAX_LIKES_RECEIVED = 1000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile

    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds

    private val _matchIds = MutableStateFlow<Set<String>>(emptySet())
    val matchIds: StateFlow<Set<String>> = _matchIds

    private val _likesReceived = MutableStateFlow<Map<String, Long>>(emptyMap())
    val likesReceived: StateFlow<Map<String, Long>> = _likesReceived

    private val _unmatchedLikeIds = MutableStateFlow<Set<String>>(emptySet())
    val unmatchedLikeIds: StateFlow<Set<String>> = _unmatchedLikeIds

    private val _likedCount = MutableStateFlow(0)
    val likedCount: StateFlow<Int> = _likedCount

    private val _sessionReady = MutableStateFlow(false)
    val sessionReady: StateFlow<Boolean> = _sessionReady

    private val started = AtomicBoolean(false)
    private val listeners = mutableListOf<Pair<Query, ValueEventListener>>()
    private var profileListener: ListenerRegistration? = null
    private val migratingProfiles = mutableSetOf<String>()
    fun ensureStarted() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            start(uid)
        }
    }

    @Synchronized
    fun start(userId: String) {
        if (started.get() && _currentUserId.value == userId) {
            return
        }
        stop()
        started.set(true)
        _sessionReady.value = false
        _currentUserId.value = userId
        attachUserListener(userId)
        attachBlocksListener(userId)
        attachMatchesListener(userId)
        attachLikesListener(userId)
    }

    @Synchronized
    fun stop() {
        started.set(false)
        listeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        listeners.clear()
        profileListener?.remove()
        profileListener = null
        _currentUserId.value = null
        _profile.value = null
        _blockedUserIds.value = emptySet()
        _matchIds.value = emptySet()
        _likesReceived.value = emptyMap()
        _unmatchedLikeIds.value = emptySet()
        _likedCount.value = 0
        _sessionReady.value = false
        ProfileCache.clear()
        UserSummaryCache.clear()
    }

    private fun attachUserListener(userId: String) {
        profileListener?.remove()
        val ref = FirebaseRefs.userProfiles.document(userId)
        profileListener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Profile listener cancelled: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                scope.launch { migrateProfileFromRealtime(userId) }
                return@addSnapshotListener
            }
            scope.launch {
                val profile = snapshot?.safeGetProfile("sessionProfile/$userId")?.let { base ->
                    if (base.userId.isBlank()) base.copy(userId = userId) else base
                }
                if (profile != null) {
                    _profile.value = profile
                    ProfileCache.put(profile)
                    _sessionReady.value = true
                } else {
                    Log.w(TAG, "No profile snapshot for $userId")
                }
            }
        }
        }
    private suspend fun migrateProfileFromRealtime(userId: String) {
        synchronized(migratingProfiles) {
            if (!migratingProfiles.add(userId)) return
        }
        try {
            val existing = FirebaseRefs.userProfiles.document(userId).get().await()
            if (existing.exists()) {
                existing.safeGetProfile("migrateProfileExisting/$userId")?.let { profile ->
                    val resolved = if (profile.userId.isBlank()) profile.copy(userId = userId) else profile
                    _profile.value = resolved
                    ProfileCache.put(resolved)
                    _sessionReady.value = true
                }
                return
            }
            val snapshot = db.getReference("users/$userId").get().await()
            val profile = snapshot.safeGetProfile("migrateProfile/$userId")
                ?.let { base -> if (base.userId.isBlank()) base.copy(userId = userId) else base }
                ?: return
            FirebaseRefs.userProfiles.document(userId)
                .set(profile, SetOptions.merge())
                .await()
            _profile.value = profile
            ProfileCache.put(profile)
            _sessionReady.value = true
            Log.d(TAG, "Migrated profile for $userId from RTDB to Firestore.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to migrate profile for $userId: ${e.message}", e)
        } finally {
            synchronized(migratingProfiles) {
                migratingProfiles.remove(userId)
            }
        }
    }

    private fun attachBlocksListener(userId: String) {
        val ref = db.getReference("blocks/$userId").orderByKey().limitToLast(MAX_BLOCKS)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.childrenCount >= MAX_BLOCKS) {
                    Log.w(TAG, "Blocks listener limited to last $MAX_BLOCKS entries for $userId.")
                }
                val ids = snapshot.children.mapNotNull { it.key }.toSet()
                _blockedUserIds.value = ids
                recomputeLikeDerivedState()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Blocks listener cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        listeners += ref to listener
    }

    private fun attachMatchesListener(userId: String) {
        val ref = db.getReference("matches/$userId").orderByKey().limitToLast(MAX_MATCHES)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.childrenCount >= MAX_MATCHES) {
                    Log.w(TAG, "Matches listener limited to last $MAX_MATCHES entries for $userId.")
                }
                val ids = snapshot.children.mapNotNull { it.key }.toSet()
                _matchIds.value = ids
                recomputeLikeDerivedState()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Matches listener cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        listeners += ref to listener
    }

    private fun attachLikesListener(userId: String) {
        val ref = db.getReference("likesReceived/$userId").orderByKey().limitToLast(MAX_LIKES_RECEIVED)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (snapshot.childrenCount >= MAX_LIKES_RECEIVED) {
                        Log.w(TAG, "likesReceived listener limited to last $MAX_LIKES_RECEIVED entries for $userId.")
                    }
                    // Debug: log raw child values (trim in prod)
                    Log.d(TAG, "likesReceived snapshot for $userId children: ${snapshot.children.map { it.key to it.value }}")

                    val map = snapshot.children.mapNotNull { child ->
                        val key = child.key ?: return@mapNotNull null

                        // 1) nested object: { timestamp: 123456789 }
                        val nestedTs = child.child("timestamp").getValue(Number::class.java)?.toLong()
                        if (nestedTs != null && nestedTs > 0L) return@mapNotNull key to nestedTs

                        // 2) direct numeric value stored as Number (covers Int/Long/Double)
                        val directNum = child.getValue(Number::class.java)?.toLong()
                        if (directNum != null && directNum > 0L) return@mapNotNull key to directNum

                        // 3) direct numeric stored as String
                        val asString = child.getValue(String::class.java)
                        val parsed = asString?.toLongOrNull()
                        if (parsed != null && parsed > 0L) return@mapNotNull key to parsed

                        // 4) boolean presence (true) => keep but mark unknown timestamp (0L)
                        val asBool = child.getValue(Boolean::class.java)
                        if (asBool == true) {
                            Log.w(TAG, "Like value for $key is boolean true — keeping key with unknown timestamp (0L)")
                            return@mapNotNull key to 0L
                        }

                        // 5) Unknown/unexpected format: keep the key with unknown timestamp (0L) and log
                        Log.w(TAG, "Unrecognized likesReceived/$userId/$key value: ${child.value} (${child.value?.javaClass?.name}). Keeping key and requesting server verification.")
                        // Optional: request server-side verification (if not already requested)
                        try {
                            val cleanupRef = db.getReference("likesReceivedCleanupRequests/$userId/$key")
                            // Only set if absent — avoid overwriting repeated requests (ServerValue.TIMESTAMP)
                            cleanupRef.runTransaction(object : Transaction.Handler {
                                override fun doTransaction(currentData: MutableData): Transaction.Result {
                                    if (currentData.value == null) {
                                        currentData.value = ServerValue.TIMESTAMP
                                    }
                                    return Transaction.success(currentData)
                                }
                                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                                    if (error != null) Log.w(TAG, "Failed to set cleanup request for $userId/$key: ${error.message}")
                                }
                            })
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to write cleanup request for $key: ${e.message}")
                        }
                        return@mapNotNull key to 0L
                    }.toMap()

                    // Update state — keep keys even with fallback 0L
                    _likesReceived.value = map
                    recomputeLikeDerivedState()
                } catch (e: Exception) {
                    Log.w(TAG, "likes listener parse error: ${e.message}", e)
                    // IMPORTANT: do not remove keys on parse error; keep existing map (no change)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Likes listener cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        listeners += ref to listener
    }

    private fun recomputeLikeDerivedState() {
        val likes = _likesReceived.value.keys
        if (likes.isEmpty()) {
            _unmatchedLikeIds.value = emptySet()
            _likedCount.value = 0
            return
        }
        val blocked = _blockedUserIds.value
        val matches = _matchIds.value
        val filtered = likes.filterNot { id ->
            blocked.contains(id) || matches.contains(id)
        }.toSet()
        _unmatchedLikeIds.value = filtered
        _likedCount.value = filtered.size
    }
}