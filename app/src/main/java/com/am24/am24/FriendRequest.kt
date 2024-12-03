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

// friendrequest.kt

data class FilterSettings(
    var filterOption: String = "everyone",
    var datingFilters: DatingFilterSettings = DatingFilterSettings(),
    var feedFilters: FeedFilterSettings = FeedFilterSettings(),
    var sortOption: String = "No Sort",
    var searchQuery: String = "",
    val additionalFilters: Map<String, Any?> = emptyMap(),
    var isVoiceOnly: Boolean = false
)

data class DatingFilterSettings(
    val localities: List<String> = emptyList(),
    val city: String = "All",       // Changed from capitalRegion to city
    val highSchool: String = "",
    val college: String = "",
    val postGrad: String = "",
    val work: String = "",
    val ageStart: Int = 18,
    val ageEnd: Int = 100,
    val distance: Int = 10,
    val gender: String = "",
    val rating: String = ""
)

data class FeedFilterSettings(
    val localities: List<String> = emptyList(),
    val city: String = "All",       // Changed from capitalRegion to city
    val highSchool: String = "",
    val college: String = "",
    val postGrad: String = "",
    val work: String = "",
    val ageStart: Int = 18,
    val ageEnd: Int = 100,
    val gender: String = "",
    val rating: String = ""
    // Removed distance field
)
