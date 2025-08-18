package com.am24.am24

import kotlinx.coroutines.tasks.await

/**
 * Utility function to collect user IDs that should be hidden from swiping UIs.
 */
suspend fun fetchExcludedUsers(me: String): Set<String> {
    val db = FirebaseRefs.db
    val oneWeekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1_000L
    val twoWeeksAgo = System.currentTimeMillis() - 14 * 24 * 60 * 60 * 1_000L
    val excludedIds = mutableSetOf<String>()

    // ① matches — every matched UID is excluded
    val matchSnap = db.getReference("matches/$me").get().await()
    matchSnap.children.forEach { excludedIds += it.key!! }

    // ② likes you gave in the last 7 days
    val likeSnap = db.getReference("likesGiven/$me").get().await()
    likeSnap.children.forEach { child ->
        val ts = child.getValue(Long::class.java) ?: 0L
        if (ts >= oneWeekAgo) excludedIds += child.key!!
    }

    // ③ dislikes you gave in the last 14 days
    val dislikeSnap = db.getReference("dislikesGiven/$me").get().await()
    dislikeSnap.children.forEach { child ->
        val ts = child.getValue(Long::class.java) ?: 0L
        if (ts >= twoWeeksAgo) excludedIds += child.key!!
    }

    // ④ permanent excludes based on swipe counts
    val permSnap = db.getReference("users/$me/permanentExcludes").get().await()
    permSnap.children.forEach { excludedIds += it.key!! }

    val countsSnap = db.getReference("users/$me/swipeCounts").get().await()
    countsSnap.children.forEach { child ->
        val cnt = child.getValue(Int::class.java) ?: 0
        if (cnt >= 3) excludedIds += child.key!!
    }

    return excludedIds
}