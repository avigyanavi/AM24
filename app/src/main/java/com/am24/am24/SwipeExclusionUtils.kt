package com.am24.am24

import kotlinx.coroutines.tasks.await
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
/**
 * Utility function to collect user IDs that should be hidden from swiping UIs.
 */
suspend fun fetchExcludedUsers(me: String): Set<String> = coroutineScope {
    val db = FirebaseRefs.db
    val tag = "SwipeExclusionUtils"


    val matchesDeferred = async {
        runCatching { db.getReference("matches/$me").get().await() }
            .onFailure { Log.w(tag, "Failed to load matches for $me", it) }
            .getOrNull()
    }
    val likesDeferred = async {
        runCatching { db.getReference("likesGiven/$me").get().await() }
            .onFailure { Log.w(tag, "Failed to load likes for $me", it) }
            .getOrNull()
    }
    val dislikesDeferred = async {
        runCatching { db.getReference("dislikesGiven/$me").get().await() }
            .onFailure { Log.w(tag, "Failed to load dislikes for $me", it) }
            .getOrNull()
    }
    val permanentDeferred = async {
        runCatching { db.getReference("users/$me/permanentExcludes").get().await() }
            .onFailure { Log.w(tag, "Failed to load permanent excludes for $me", it) }
            .getOrNull()
    }
    val swipeCountsDeferred = async {
        runCatching { db.getReference("users/$me/swipeCounts").get().await() }
            .onFailure { Log.w(tag, "Failed to load swipe counts for $me", it) }
            .getOrNull()
    }

    val excludedIds = mutableSetOf<String>()

    matchesDeferred.await()?.children?.forEach { child -> child.key?.let(excludedIds::add) }
    likesDeferred.await()?.children?.forEach { child -> child.key?.let(excludedIds::add) }
    dislikesDeferred.await()?.children?.forEach { child -> child.key?.let(excludedIds::add) }
    permanentDeferred.await()?.children?.forEach { child -> child.key?.let(excludedIds::add) }

    swipeCountsDeferred.await()?.children?.forEach { child ->
        val cnt = child.getValue(Int::class.java) ?: 0
        if (cnt >= 3) {
            child.key?.let(excludedIds::add)
        }
    }
    excludedIds
}

/**
 * Remove all swipe related exclusions for the given user.
 *
 * Deletes matches, likes, dislikes and any permanent exclusion or
 * swipe count entries for [userId] so they can start fresh.
 */
suspend fun clearExcludedUsers(userId: String) {
    val db = FirebaseRefs.db
    db.getReference("matches/$userId").removeValue().await()
    db.getReference("likesGiven/$userId").removeValue().await()
    db.getReference("dislikesGiven/$userId").removeValue().await()
    db.getReference("users/$userId/permanentExcludes").removeValue().await()
    db.getReference("users/$userId/swipeCounts").removeValue().await()
}