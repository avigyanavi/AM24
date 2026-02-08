package com.am24.am24

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * In-memory cache for lightweight user metadata stored under userSummaries/.
 */
object UserSummaryCache {

    private const val TAG = "UserSummaryCache"

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, UserSummary>()

    suspend fun getSummaries(userIds: Collection<String>): Map<String, UserSummary> {
        if (userIds.isEmpty()) return emptyMap()
        val resolved = mutableMapOf<String, UserSummary>()
        val missing = mutableListOf<String>()

        mutex.withLock {
            userIds.forEach { id ->
                val cached = cache[id]
                if (cached != null) {
                    resolved[id] = cached
                } else {
                    missing += id
                }
            }
        }

        if (missing.isNotEmpty()) {
            val fetched = fetchFromNetwork(missing)
            mutex.withLock {
                fetched.forEach { (id, summary) -> cache[id] = summary }
            }
            resolved.putAll(fetched)
        }

        return resolved
    }

    fun clear() {
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock { cache.clear() }
        }
    }

    private suspend fun fetchFromNetwork(ids: Collection<String>): Map<String, UserSummary> = coroutineScope {
        val summariesRef = FirebaseRefs.db.getReference("userSummaries")
        val usersRef = FirebaseRefs.userProfiles
        val result = mutableMapOf<String, UserSummary>()
        ids.map { id ->
            async(Dispatchers.IO) {
                try {
                    val snapshot = summariesRef.child(id).get().await()
                    val summary = snapshot.getValue(UserSummary::class.java)
                    if (summary != null) {
                        val normalized = if (summary.userId.isBlank()) summary.copy(userId = id) else summary
                        result[id] = normalized
                        return@async
                    }
                    val profileSnapshot = usersRef.document(id).get().await()
                    val profile = profileSnapshot.safeGetProfile("userSummary/$id") ?: return@async
                    result[id] = profile.toUserSummary(id)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch summary for $id", e)
                    try {
                        val profileSnapshot = usersRef.document(id).get().await()
                        val profile = profileSnapshot.safeGetProfile("userSummaryFallback/$id")
                        if (profile != null) {
                            result[id] = profile.toUserSummary(id)
                        }
                    } catch (fallbackError: Exception) {
                        Log.e(TAG, "Failed to fetch profile fallback for $id", fallbackError)
                    }
                }
            }
        }.awaitAll()
        result
    }
}
private fun Profile.toUserSummary(userIdFallback: String): UserSummary {
    return UserSummary(
        userId = userId.ifBlank { userIdFallback },
        username = username,
        name = name,
        dob = dob,
        roles = roles,
        jobRole = jobRole,
        profilepicUrl = profilepicUrl,
        profilepicThumbnailUrl = profilepicThumbnailUrl,
        lastActive = lastActive,
        likesReceivedCount = totalDatingLikes,
        latitude = latitude.takeIf { it != 0.0 },
        longitude = longitude.takeIf { it != 0.0 }
    )
}