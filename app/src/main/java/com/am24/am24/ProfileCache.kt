package com.am24.am24

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Lightweight in-memory cache for user profiles.
 */
object ProfileCache {

    private val TAG = "ProfileCache"

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Profile>()

    private val _knownProfiles = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val knownProfiles: StateFlow<Map<String, Profile>> = _knownProfiles

    suspend fun getProfiles(userIds: Collection<String>): Map<String, Profile> {
        if (userIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Profile>()
        val missing = mutableListOf<String>()

        mutex.withLock {
            userIds.forEach { id ->
                val cached = cache[id]
                if (cached != null) {
                    result[id] = cached.copy()
                } else {
                    missing += id
                }
            }
        }

        if (missing.isNotEmpty()) {
            val fetched = fetchFromNetwork(missing)
            mutex.withLock {
                fetched.forEach { (id, profile) -> cache[id] = profile }
                _knownProfiles.value = cache.mapValues { it.value.copy() }
            }
            result.putAll(fetched.mapValues { it.value.copy() })
        }

        return result
    }

    fun put(profile: Profile) {
        val resolvedId = profile.userId.takeIf { it.isNotBlank() }
            ?: return
        val normalized = profile.copy(userId = resolvedId)
        coroutineScopeLaunch {
            mutex.withLock {
                cache[normalized.userId] = normalized
                _knownProfiles.value = cache.mapValues { it.value.copy() }
            }
        }
    }

    fun clear() {
        coroutineScopeLaunch {
            mutex.withLock {
                cache.clear()
                _knownProfiles.value = emptyMap()
            }
        }
    }

    private fun coroutineScopeLaunch(block: suspend () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            block()
        }
    }

    private suspend fun fetchFromNetwork(ids: Collection<String>): Map<String, Profile> = coroutineScope {
        val usersRef = FirebaseRefs.userProfiles
        val result = mutableMapOf<String, Profile>()

        ids.map { id ->
            async(Dispatchers.IO) {
                try {
                    val snapshot = usersRef.document(id).get().await()
                    val profile = snapshot.safeGetProfile("profileCache/$id")
                    if (profile != null) {
                        val normalized = if (profile.userId.isBlank()) profile.copy(userId = id) else profile
                        result[id] = normalized
                    } else {
                        Log.w(TAG, "Profile for $id was null")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch profile $id", e)
                }
            }
        }.awaitAll()

        result
    }
}