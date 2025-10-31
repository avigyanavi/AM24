package com.am24.am24

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object UserDeletionCache {
    private data class Entry(val value: Boolean, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    // Tuneable knobs
    private val ttl: Duration = 10.minutes
    private const val NETWORK_TIMEOUT_MS = 1500L  // keep UI snappy

    suspend fun isDeleted(
        database: FirebaseDatabase,
        uid: String,
        userSnapshot: DataSnapshot? = null,
    ): Boolean {
        if (uid.isBlank()) return true

        // 1) Fresh cache?
        cache[uid]?.let { e ->
            if (System.currentTimeMillis() < e.expiresAtMs) return e.value
            // expired → drop-through
        }

        // 2) Coalesce concurrent requests for same uid
        val waiter = CompletableDeferred<Boolean>()
        val existing = inFlight.putIfAbsent(uid, waiter)
        if (existing != null) {
            // Someone else is doing the work; await their result
            return existing.await()
        }

        // 3) Compute and complete all waiters
        val result = try {
            fetchDeletedFlag(database, uid, userSnapshot)
        } finally {
            // ensure we clean up even on exceptions
        }

        // cache with TTL
        cache[uid] = Entry(result, System.currentTimeMillis() + ttl.inWholeMilliseconds)

        // complete any waiters
        inFlight.remove(uid)?.complete(result)

        return result
    }

    private suspend fun fetchDeletedFlag(
        database: FirebaseDatabase,
        uid: String,
        userSnapshot: DataSnapshot?
    ): Boolean {
        return withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val userSnap = userSnapshot ?: database.getReference("users").child(uid).get().await()
            if (!userSnap.exists()) {
                // Hard signal: user node missing → deleted
                return@withTimeoutOrNull true
            }

            val usernameRaw = userSnap.child("username").getValue(String::class.java)?.trim().orEmpty()
            if (usernameRaw.isBlank()) {
                // Soft/unknown state (mid-signup). BE CONSERVATIVE:
                // Treat as active to avoid hiding posts / spinner loops.
                return@withTimeoutOrNull false
            }

            val normalized = usernameRaw.lowercase(Locale.ROOT)
            val mappedUid = database.getReference("usernames").child(normalized).get().await()
                .getValue(String::class.java)
                ?.trim()

            // Require exact match; Firebase UIDs are case-sensitive
            val deleted = mappedUid == null || mappedUid != uid
            return@withTimeoutOrNull deleted
        } ?: false // timeout/error → assume active (NOT deleted)
    }

    fun markDeleted(uid: String) {
        if (uid.isNotBlank()) {
            cache[uid] = Entry(true, System.currentTimeMillis() + ttl.inWholeMilliseconds)
        }
    }

    fun markActive(uid: String) {
        if (uid.isNotBlank()) {
            cache[uid] = Entry(false, System.currentTimeMillis() + ttl.inWholeMilliseconds)
        }
    }

    /** Optional: purge all cache (e.g., on logout or major sync) */
    fun clear() {
        cache.clear()
        inFlight.clear()
    }
}
