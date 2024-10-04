package com.am24.am24

import com.google.firebase.database.Exclude
import com.google.firebase.database.ServerValue

data class Post(
    val postId: String = "",
    val userId: String = "",  // ID of the user who created the post
    val username: String = "",  // Username of the person who posted
    val contentText: String = "",  // The text content of the post (could be transcription from voice)
    val voiceUrl: String? = null,  // URL for the voice recording in Firebase Storage
    val transcriptedText: String? = null,  // Transcription of the voice note
    val timestamp: Any = ServerValue.TIMESTAMP,  // Timestamp of post creation
    val profilepicUrl: String? = null,

    // Engagement Metrics
    var upvotes: Int = 0,  // Number of upvotes for this post
    var downvotes: Int = 0,  // Number of downvotes for this post
    val totalComments: Int = 0,  // Number of comments (in case commenting is added later)
    var upvotedUsers: MutableMap<String, Boolean> = mutableMapOf(),
    var downvotedUsers: MutableMap<String, Boolean> = mutableMapOf(),

    // Tags and Location
    val userTags: List<String> = emptyList(),  // Tags related to the content or user
    val locationTags: List<String> = emptyList(),  // Location-based tags for discoverability

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
    val userId: String = "",
    val username: String = "",
    val commentText: String = "",
    val timestamp: Any = ServerValue.TIMESTAMP
) {
    fun getCommentTimestamp(): Long {
        return if (timestamp is Long) {
            timestamp
        } else {
            System.currentTimeMillis() // Fallback to current time if timestamp is not available as Long
        }
    }
}

