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

    fun fetchUsernameById(userId: String, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
        userRef.child("username").get().addOnSuccessListener { snapshot ->
            val username = snapshot.getValue(String::class.java)
            if (username != null) {
                onSuccess(username)
            } else {
                onFailure("Username not found")
            }
        }.addOnFailureListener {
            onFailure(it.message ?: "Failed to fetch username")
        }
    }

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


    // Send a friend request
    fun sendFriendRequest(
        currentUserId: String,
        targetUserId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        fetchUsernameById(currentUserId, { currentUserName ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val timestamp = System.currentTimeMillis()
                    val request = FriendRequest(
                        senderId = currentUserId,
                        senderUsername = currentUserName,
                        status = "requested",
                        timestamp = timestamp
                    )

                    // Store the friend request details in the "friends" node
                    friendsRef.child(targetUserId).child(currentUserId).setValue(request).await()

                    // Create and store a notification in the "notifications" node
                    val notificationId = notificationsRef.child(targetUserId).push().key
                        ?: throw Exception("Failed to generate notification ID")
                    val notification = Notification(
                        id = notificationId,
                        type = "friend_request",
                        senderId = currentUserId,
                        senderUsername = currentUserName,
                        message = "You received a friend request from $currentUserName",
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
            onFailure("Failed to fetch sender's username.")
        })
    }


    // Accept a friend request
    fun acceptFriendRequest(
        currentUserId: String,
        requesterId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        fetchUsernameById(currentUserId, { currentUserName ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val timestamp = System.currentTimeMillis()
                    val currentUserRequestRef = friendsRef.child(currentUserId).child(requesterId)
                    val requesterUserRequestRef = friendsRef.child(requesterId).child(currentUserId)

                    // Update the friend request status to "accepted"
                    currentUserRequestRef.child("status").setValue("accepted").await()
                    requesterUserRequestRef.child("status").setValue("accepted").await()

                    // Create and store a notification for the requester
                    val notificationId = notificationsRef.child(requesterId).push().key
                        ?: throw Exception("Failed to generate notification ID")
                    val notification = Notification(
                        id = notificationId,
                        type = "accept_request",
                        senderId = currentUserId,
                        senderUsername = currentUserName,
                        message = "$currentUserName accepted your friend request",
                        timestamp = timestamp,
                        isRead = "false"
                    )
                    notificationsRef.child(requesterId).child(notificationId).setValue(notification).await()

                    // Optionally, create a new chat between friends
                    createFriendChat(currentUserId, requesterId)
                    onSuccess()
                } catch (e: Exception) {
                    onFailure(e.message ?: "Failed to accept friend request.")
                }
            }
        }, {
            onFailure("Failed to fetch current user's username.")
        })
    }

    // Reject a friend request
    fun rejectFriendRequest(
        currentUserId: String,
        requesterId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        fetchUsernameById(currentUserId, { currentUserName ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val currentUserRequestRef = friendsRef.child(currentUserId).child(requesterId)
                    val requesterUserRequestRef = friendsRef.child(requesterId).child(currentUserId)

                    // Remove the friend request from both users' records
                    currentUserRequestRef.removeValue().await()
                    requesterUserRequestRef.removeValue().await()

                    // Create and store a rejection notification for the requester
                    val notificationId = notificationsRef.child(requesterId).push().key
                        ?: throw Exception("Failed to generate notification ID")
                    val notification = Notification(
                        id = notificationId,
                        type = "reject_request",
                        senderId = currentUserId,
                        senderUsername = currentUserName,
                        message = "$currentUserName rejected your friend request",
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
            onFailure("Failed to fetch current user's username.")
        })
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



    // Get the status of the friend request or friendship between two users
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


    // Create a new chat entry for friends in the DM screen
    private fun createFriendChat(currentUserId: String, friendId: String) {
        val chatId = chatRef.push().key ?: return

        // Add the chat entry for both users
        val userChatRef = chatRef.child(currentUserId).child(chatId)
        val friendChatRef = chatRef.child(friendId).child(chatId)

        val chatData = mapOf(
            "chatId" to chatId,
            "participants" to listOf(currentUserId, friendId)
        )

        userChatRef.setValue(chatData)
        friendChatRef.setValue(chatData)
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

    /**
     * Report a user profile.
     */
    fun reportProfile(
        profileId: String,
        reporterId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val reportRef = FirebaseDatabase.getInstance().getReference("reportedProfiles").child(profileId)
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
                Log.e(TAG, "Failed to report profile: ${e.message}")
                onFailure(e.message ?: "Failed to report profile.")
            }
        }
    }

    /**
     * Block a user profile.
     */
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
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to block profile: ${e.message}")
                onFailure(e.message ?: "Failed to block profile.")
            }
        }
    }
}