package com.am24.am24

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ProfileViewModel"
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")
    private val database = FirebaseDatabase.getInstance()
    private val friendsRef = database.getReference("friends")
    private val notificationsRef = database.getReference("notifications")
    private val chatRef = database.getReference("chats") // New chat reference for DM creation
    private val reportsRef = FirebaseDatabase.getInstance().getReference("reportedProfiles")

    // Update the notification message and read status in the database
    fun updateNotificationMessage(
        userId: String,
        notificationId: String,
        newMessage: String,
        newIsRead: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val notificationRef = notificationsRef.child(userId).child(notificationId)
                // Update message and isRead fields in the notification
                val updates = mapOf<String, Any>(
                    "message" to newMessage,
                    "isRead" to newIsRead
                )
                notificationRef.updateChildren(updates).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onSuccess()
                    } else {
                        onFailure("Failed to update notification.")
                    }
                }
            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to update notification.")
            }
        }
    }

    // Fetch full name (firstName + lastName)
    fun fetchUsernameById(userId: String, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        val userRef = usersRef.child(userId)
        userRef.get().addOnSuccessListener { snapshot ->
            val firstName = snapshot.child("firstName").getValue(String::class.java) ?: "Unknown"
            val lastName = snapshot.child("lastName").getValue(String::class.java) ?: "Unknown"
            val fullName = "$firstName $lastName"
            onSuccess(fullName)
        }.addOnFailureListener {
            onFailure(it.message ?: "Failed to fetch full name")
        }
    }

    // Send a friend request
    fun sendFriendRequest(
        currentUserId: String,
        targetUserId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        fetchUsernameById(currentUserId, { currentFullName ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val timestamp = System.currentTimeMillis()
                    val request = FriendRequest(
                        senderId = currentUserId,
                        senderUsername = currentFullName,
                        status = "requested",
                        timestamp = timestamp
                    )

                    // Store the friend request details in "friends > targetUserId > currentUserId"
                    friendsRef.child(targetUserId).child(currentUserId).setValue(request).await()

                    // Create and store a notification in "notifications > targetUserId"
                    val notificationId = notificationsRef.child(targetUserId).push().key
                        ?: throw Exception("Failed to generate notification ID")
                    val notification = Notification(
                        id = notificationId,
                        type = "friend_request",
                        senderId = currentUserId,
                        senderUsername = currentFullName, // Ensure this is the sender's name
                        message = "You received a friend request from $currentFullName",
                        timestamp = timestamp,
                        isRead = "false"
                    )
                    notificationsRef.child(targetUserId).child(notificationId).setValue(notification).await()

                    onSuccess()
                } catch (e: Exception) {
                    onFailure(e.message ?: "Failed to send friend request.")
                }
            }
        }, {
            onFailure("Failed to fetch sender's full name.")
        })
    }

    // Accept a friend request
    fun acceptFriendRequest(
        currentUserId: String,
        requesterId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        fetchUsernameById(currentUserId, { currentFullName ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val timestamp = System.currentTimeMillis()

                    // Update the friend request status to "accepted" on both sides
                    friendsRef.child(currentUserId).child(requesterId).setValue(
                        FriendRequest(
                            senderId = requesterId,
                            senderUsername = currentFullName,
                            status = "accepted",
                            timestamp = timestamp
                        )
                    ).await()
                    friendsRef.child(requesterId).child(currentUserId).child("status").setValue("accepted").await()

                    // Create a notification for the requester
                    val notificationId = notificationsRef.child(requesterId).push().key
                        ?: throw Exception("Failed to generate notification ID")
                    val notification = Notification(
                        id = notificationId,
                        type = "accept_request",
                        senderId = currentUserId,
                        senderUsername = currentFullName, // Ensure this is the acceptor's name
                        message = "$currentFullName accepted your friend request",
                        timestamp = timestamp,
                        isRead = "false"
                    )
                    notificationsRef.child(requesterId).child(notificationId).setValue(notification).await()

                    createFriendChat(currentUserId, requesterId)
                    onSuccess()
                } catch (e: Exception) {
                    onFailure(e.message ?: "Failed to accept friend request.")
                }
            }
        }, {
            onFailure("Failed to fetch current user's full name.")
        })
    }

    // Reject a friend request
    fun rejectFriendRequest(
        currentUserId: String,
        requesterId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        fetchUsernameById(currentUserId, { currentFullName ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Remove the friend request from both users' records
                    friendsRef.child(currentUserId).child(requesterId).removeValue().await()
                    friendsRef.child(requesterId).child(currentUserId).removeValue().await()

                    // Create a rejection notification for the requester
                    val notificationId = notificationsRef.child(requesterId).push().key
                        ?: throw Exception("Failed to generate notification ID")
                    val notification = Notification(
                        id = notificationId,
                        type = "reject_request",
                        senderId = currentUserId,
                        senderUsername = currentFullName,
                        message = "$currentFullName rejected your friend request",
                        timestamp = System.currentTimeMillis(),
                        isRead = "false"
                    )
                    notificationsRef.child(requesterId).child(notificationId).setValue(notification).await()

                    onSuccess()
                } catch (e: Exception) {
                    onFailure(e.message ?: "Failed to reject friend request.")
                }
            }
        }, {
            onFailure("Failed to fetch current user's full name.")
        })
    }

    private fun createFriendChat(currentUserId: String, friendId: String) {
        val chatId = chatRef.push().key ?: return
        val chatData = mapOf(
            "chatId" to chatId,
            "participants" to listOf(currentUserId, friendId)
        )
        chatRef.child(currentUserId).child(chatId).setValue(chatData)
        chatRef.child(friendId).child(chatId).setValue(chatData)
    }

    // Mark a notification as read (String-based)
    fun markNotificationAsRead(
        userId: String,
        notificationId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val notificationRef = notificationsRef.child(userId).child(notificationId)
                notificationRef.child("isRead").setValue("true").addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onSuccess()
                    } else {
                        onFailure("Failed to mark notification as read.")
                    }
                }
            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to mark notification as read.")
            }
        }
    }

    // Send notification for profile interactions
    private fun sendProfileNotification(
        userId: String,
        senderId: String,
        type: String,
        message: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val notificationId = notificationsRef.child(userId).push().key ?: throw Exception("Failed to generate notification ID")
                val notification = Notification(
                    id = notificationId,
                    type = type,
                    senderId = senderId,
                    message = message,
                    timestamp = System.currentTimeMillis(),
                    isRead = "false"
                )
                notificationsRef.child(userId).child(notificationId).setValue(notification).await()
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to send notification.")
            }
        }
    }

    // Block a profile with notification
    fun blockProfile(
        currentUserId: String,
        targetUserId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val blockedRef = usersRef.child(currentUserId).child("blockedUsers").child(targetUserId)
                blockedRef.setValue(true).await()
                sendProfileNotification(
                    userId = targetUserId,
                    senderId = currentUserId,
                    type = "block",
                    message = "Your profile has been blocked by another user.",
                    onSuccess = onSuccess,
                    onFailure = onFailure
                )
            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to block profile.")
            }
        }
    }


    // Fetch all notifications
    fun getNotifications(
        userId: String,
        onSuccess: (List<Notification>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                notificationsRef.child(userId).get().addOnSuccessListener { snapshot ->
                    val notifications = mutableListOf<Notification>()
                    snapshot.children.forEach { child ->
                        val notification = child.getValue(Notification::class.java)
                        if (notification != null) {
                            notifications.add(notification)
                        }
                    }
                    onSuccess(notifications)
                }.addOnFailureListener { error ->
                    onFailure(error.message ?: "Failed to fetch notifications.")
                }
            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to fetch notifications.")
            }
        }
    }


    // Fetch friend request status
    fun getFriendRequestStatus(
        currentUserId: String,
        targetUserId: String,
        onStatusRetrieved: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val friendRequestRef = friendsRef.child(currentUserId).child(targetUserId)

                friendRequestRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val request = snapshot.getValue(FriendRequest::class.java)
                        onStatusRetrieved(request?.status ?: "not_requested")
                    }

                    override fun onCancelled(error: DatabaseError) {
                        onFailure(error.message)
                    }
                })
            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to retrieve status.")
            }
        }
    }

    // Remove a friend relationship
    fun removeFriend(
        currentUserId: String,
        targetUserId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                friendsRef.child(currentUserId).child(targetUserId).removeValue().await()
                friendsRef.child(targetUserId).child(currentUserId).removeValue().await()
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove friend: ${e.message}")
                onFailure(e.message ?: "Failed to remove friend.")
            }
        }
    }


// Count unread notifications for a user
    fun countUnreadNotifications(userId: String, onCountRetrieved: (Int) -> Unit) {
        val userNotificationsRef = notificationsRef.child(userId)
        userNotificationsRef.orderByChild("isRead").equalTo(false)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onCountRetrieved(snapshot.childrenCount.toInt())
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to count unread notifications: ${error.message}")
                }
            })
    }

    // Report a user profile
    fun reportProfile(
        profileId: String,
        reporterId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val reportRef = database.getReference("reportedProfiles").child(profileId)
                val reportId = reportRef.push().key ?: throw Exception("Unable to generate report ID.")

                val reportData = mapOf(
                    "reportId" to reportId,
                    "profileId" to profileId,
                    "reporterId" to reporterId,
                    "timestamp" to ServerValue.TIMESTAMP
                )
                reportRef.child(reportId).setValue(reportData).await()
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to report profile.")
            }
        }
    }


    fun getUserReview(
        currentUserId: String,
        targetUserId: String,
        onSuccess: (Double) -> Unit,
        onFailure: () -> Unit
    ) {
        val reviewRef = FirebaseDatabase.getInstance()
            .getReference("userReviews")
            .child(targetUserId)
            .child(currentUserId)

        reviewRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rating = snapshot.getValue(Double::class.java)
                if (rating != null) {
                    onSuccess(rating)
                } else {
                    onSuccess(0.0)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onFailure()
            }
        })
    }

    fun submitUserReview(
        currentUserId: String,
        targetUserId: String,
        rating: Double,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val reviewRef = FirebaseDatabase.getInstance()
            .getReference("userReviews")
            .child(targetUserId)
            .child(currentUserId)

        reviewRef.setValue(rating)
            .addOnSuccessListener {
                // Update the target user's average rating
                updateAverageRating(targetUserId)

                // Send notification to the target user about the new or updated review
                sendProfileNotification(
                    userId = targetUserId,
                    senderId = currentUserId,
                    type = "review",
                    message = "You received a new review with a rating of $rating!",
                    onSuccess = onSuccess,
                    onFailure = { onFailure() }
                )
            }
            .addOnFailureListener {
                onFailure()
            }
    }


    private fun updateAverageRating(targetUserId: String) {
        val reviewsRef = FirebaseDatabase.getInstance()
            .getReference("userReviews")
            .child(targetUserId)

        reviewsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var totalRating = 0.0
                var reviewCount = 0
                for (child in snapshot.children) {
                    val rating = child.getValue(Double::class.java)
                    if (rating != null) {
                        totalRating += rating
                        reviewCount++
                    }
                }
                val averageRating = if (reviewCount > 0) totalRating / reviewCount else 0.0
                // Update the target user's profile with the new average rating
                val userRef = FirebaseDatabase.getInstance().getReference("users").child(targetUserId)
                userRef.child("rating").setValue(averageRating)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error if needed
            }
        })
    }
}
