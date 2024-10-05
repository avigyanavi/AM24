package com.am24.am24

import com.google.firebase.database.Exclude
import com.google.firebase.database.ServerValue

data class Post(
    val postId: String = "",
    val userId: String = "",  // ID of the user who created the post
    val username: String = "",  // Username of the person who posted
    val contentText: String = "",  // The text content of the post
    val timestamp: Any = ServerValue.TIMESTAMP,  // Timestamp of post creation
    val profilepicUrl: String? = null,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isStrikethrough: Boolean = false,
    val fontFamily: String = "Default",
    val fontSize: Int = 14,

    // Engagement Metrics
    var upvotes: Int = 0,  // Number of upvotes for this post
    var downvotes: Int = 0,  // Number of downvotes for this post
    val totalComments: Int = 0,  // Number of comments (in case commenting is added later)
    var upvotedUsers: MutableMap<String, Boolean> = mutableMapOf(),
    var downvotedUsers: MutableMap<String, Boolean> = mutableMapOf(),

    // Tags and Location
    val userTags: List<String> = emptyList(),  // Tags related to the content or user

    // Comments Section
    val comments: Map<String, Comment> = emptyMap(),

    // Calculated Metrics for Leaderboard
    val upvoteToDownvoteRatio: Double = 0.0  // Ratio of upvotes to downvotes for leaderboard rankings
) {
    @Exclude
    fun getPostTimestamp(): Long {
        return if (timestamp is Long) timestamp as Long else System.currentTimeMillis()
    }
}

data class Comment(
    val commentId: String = "", // Add this field
    val userId: String = "",
    val username: String = "",
    val commentText: String = "",
    val timestamp: Any = ServerValue.TIMESTAMP,
    var upvotes: Int = 0,
    var downvotes: Int = 0,
    var upvotedUsers: MutableMap<String, Boolean> = mutableMapOf(),
    var downvotedUsers: MutableMap<String, Boolean> = mutableMapOf()
) {
    fun getCommentTimestamp(): Long {
        return if (timestamp is Long) timestamp else (timestamp as? Map<*, *>)?.get("timestamp") as? Long ?: 0L
    }
}

