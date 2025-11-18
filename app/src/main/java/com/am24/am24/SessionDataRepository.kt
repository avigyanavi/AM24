package com.am24.am24

import android.util.Log
import com.am24.am24.FirebaseRefs.db
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralised session cache that keeps long-lived Firebase data warm while the
 * user is signed in. Screens can observe the exposed [StateFlow]s instead of
 * executing their own `get().await()` calls on every composition.
 */
object SessionDataRepository {

    private val TAG = "SessionDataRepo"

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
    private val listeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()

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
        val ref = db.getReference("users/$userId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    val profile = snapshot.getValue(Profile::class.java)?.let { base ->
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

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Profile listener cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        listeners += ref to listener
    }

    private fun attachBlocksListener(userId: String) {
        val ref = db.getReference("blocks/$userId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
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
        val ref = db.getReference("matches/$userId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
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
        val ref = db.getReference("likesReceived/$userId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val likes = snapshot.children.associate { child ->
                    val nestedTimestamp = child.child("timestamp").getValue(Long::class.java)
                    val directTimestamp = child.getValue(Long::class.java)
                    val timestamp = nestedTimestamp ?: directTimestamp ?: 0L
                    child.key!! to timestamp
                }
                _likesReceived.value = likes
                recomputeLikeDerivedState()
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