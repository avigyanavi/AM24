package com.am24.am24

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.database.PropertyName

data class FriendRequest(
    val senderId: String = "",
    val senderUsername: String = "",
    val status: String = "requested", // Possible values: "requested", "accepted", "rejected"
    val timestamp: Long = 0
    )

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