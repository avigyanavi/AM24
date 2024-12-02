package com.am24.am24

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.database.PropertyName

class Notification(
    var id: String = "",
    var type: String = "", // "friend_request", "accept_request", etc.
    var senderId: String = "",
    var senderUsername: String = "",
    message: String = "",
    var timestamp: Long = 0,
    isRead: String = "false"
) {
    var message by mutableStateOf(message)

    @get:PropertyName("isRead") @set:PropertyName("isRead")
    var isRead by mutableStateOf(isRead)
}

data class FilterSettings(
    val filterOption: String = "everyone",               // Filter scope: "everyone", "my posts", etc.
    val datingFilters: DatingFilterSettings = DatingFilterSettings(),
    val feedFilters: FeedFilterSettings = FeedFilterSettings(),
    val sortOption: String = "timestamp",               // Sorting option: "timestamp", "upvotes", "comments"
    val searchQuery: String = "",                       // Search query for posts or usernames
    val additionalFilters: Map<String, Any?> = emptyMap(), // Extensibility for future filters
    val isVoiceOnly: Boolean = false // New field to track toggle state
    )

data class DatingFilterSettings(
    val localities: List<String> = emptyList(),          // List of selected localities for dating
    val highSchool: String = "",                        // Selected high school for dating
    val college: String = "",                           // Selected college for dating
    val ageStart: Int = 0,                             // Minimum age for dating filter
    val ageEnd: Int = 100,                               // Maximum age for dating filter
    val distance: Int = 10,                             // Distance preference for dating filter
    val gender: String = "",                             // Gender preference for dating filter
    val rating: String = ""
    )

data class FeedFilterSettings(
    val localities: List<String> = emptyList(),          // List of selected localities for feed
    val highSchool: String = "",                        // Selected high school for feed
    val college: String = "",                           // Selected college for feed
    val ageStart: Int = 0,                             // Minimum age for feed filter
    val ageEnd: Int = 100,                               // Maximum age for feed filter
    val distance: Int = 10,                             // Distance preference for feed filter
    val gender: String = "",                             // Gender preference for feed filter
    val rating: String = ""
)




