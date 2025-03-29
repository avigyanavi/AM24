package com.am24.am24

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import com.google.firebase.storage.StorageReference

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ProfileViewModel"
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")
    private val database = FirebaseDatabase.getInstance()
    private val notificationsRef = database.getReference("notifications")
    private val chatRef = database.getReference("chats") // New chat reference for DM creation

    // Match Pop-Up State
    private val _matchPopUpState = MutableStateFlow<Pair<Profile, Profile>?>(null)
    val matchPopUpState: StateFlow<Pair<Profile, Profile>?> get() = _matchPopUpState

    // Add this MutableStateFlow at the top of ProfileViewModel
    private val _currentUserProfile = MutableStateFlow<Profile?>(null)
    val currentUserProfile: StateFlow<Profile?> get() = _currentUserProfile

    // NEW: Voice recording properties
    private var voiceRecorder: MediaRecorder? = null
    var voiceNoteUrl: String? = null
    var voiceNoteFilePath: String? = null

    // Add this function to fetch and store the current user's profile
    fun fetchCurrentUserProfile() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId.isNullOrEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = usersRef.child(currentUserId).get().await()
                val profile = snapshot.getValue(Profile::class.java)
                if (profile != null) {
                    _currentUserProfile.value = profile
                } else {
                    Log.e(TAG, "Failed to fetch current user's profile.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching current user profile: ${e.message}")
            }
        }
    }

    fun fetchProfilesByCity(cityName: String, onResult: (List<Profile>) -> Unit) {
        val dbRef = FirebaseDatabase.getInstance().getReference("profiles")
        dbRef.orderByChild("city").equalTo(cityName)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val profiles = mutableListOf<Profile>()
                    for (child in dataSnapshot.children) {
                        val profile = child.getValue(Profile::class.java)
                        if (profile != null) {
                            profiles.add(profile)
                        }
                    }
                    onResult(profiles)
                }
                override fun onCancelled(error: DatabaseError) { /* handle error */ }
            })
    }

    fun fetchProfilesByHometown(hometown: String, onResult: (List<Profile>) -> Unit) {
        val dbRef = FirebaseDatabase.getInstance().getReference("profiles")
        dbRef.orderByChild("hometown").equalTo(hometown)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val results = mutableListOf<Profile>()
                    for (child in snapshot.children) {
                        val prof = child.getValue(Profile::class.java)
                        prof?.let { results.add(it) }
                    }
                    onResult(results)
                }
                override fun onCancelled(error: DatabaseError) {
                    onResult(emptyList())
                }
            })
    }

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

    // Function to trigger the match pop-up
    fun triggerMatchPopUp(currentUserId: String, matchedUserId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentUserProfile = getProfileById(currentUserId)
                val matchedUserProfile = getProfileById(matchedUserId)

                if (currentUserProfile != null && matchedUserProfile != null) {
                    _matchPopUpState.value = currentUserProfile to matchedUserProfile
                } else {
                    Log.e(TAG, "Failed to retrieve profiles for match pop-up.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering match pop-up: ${e.message}")
            }
        }
    }

    fun saveProfileUpdated(
        updatedProfile: Profile,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = updatedProfile.userId
                    ?: FirebaseAuth.getInstance().currentUser?.uid
                    ?: throw Exception("No userId found for updatedProfile")

                // Convert updatedProfile to a map
                val updates = mapOf(
                    "profilepicUrl" to updatedProfile.profilepicUrl,
                    "optionalPhotoUrls" to updatedProfile.optionalPhotoUrls,
                    "voiceNoteUrl" to updatedProfile.voiceNoteUrl
                    // plus other fields you want to update
                )
                usersRef.child(userId).updateChildren(updates).await()
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to save updated profile")
            }
        }
    }


    // Function to clear the match pop-up state
    fun clearMatchPopUp() {
        _matchPopUpState.value = null
    }

    // Helper function to fetch a profile by user ID
    private suspend fun getProfileById(userId: String): Profile? {
        return try {
            val snapshot = usersRef.child(userId).get().await()
            snapshot.getValue(Profile::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch profile for userId $userId: ${e.message}")
            null
        }
    }

    // ================= New Functions for Voice Recording =================

    fun startVoiceRecording(context: Context, filePath: String) {
        try {
            voiceRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000) // 128 kbps for better quality
                setAudioSamplingRate(44100) // 44.1 kHz sampling rate
                setOutputFile(filePath)
                prepare()
                start()
            }
            voiceNoteFilePath = filePath
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice recording: ${e.message}")
        }
    }

    fun stopVoiceRecording() {
        try {
            voiceRecorder?.apply {
                stop()
                reset()
                release()
            }
            voiceRecorder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping voice recording: ${e.message}")
        }
    }

    fun uploadVoiceToRealtime(storageRef: StorageReference, uri: Uri) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val voiceNoteRef = storageRef.child("users/$userId/voice_note.mp3") // Adjust file extension if needed
        voiceNoteRef.putFile(uri)
            .addOnSuccessListener {
                voiceNoteRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    voiceNoteUrl = downloadUri.toString()
                    Log.d(TAG, "Voice note uploaded successfully: $downloadUri")
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to upload voice note: ${exception.message}")
            }
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
                    notifications.sortByDescending { it.timestamp }
                    onSuccess(notifications)
                }.addOnFailureListener { error ->
                    onFailure(error.message ?: "Failed to fetch notifications.")
                }
            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to fetch notifications.")
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

    // Send a like notification
    fun sendLikeNotification(
        senderId: String,
        receiverId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val notificationId = notificationsRef.child(receiverId).push().key
                    ?: throw Exception("Failed to generate notification ID")

                val notification = Notification(
                    id = notificationId,
                    type = "new_like",
                    senderId = senderId,
                    senderUsername = "", // Do not include username
                    message = "You have a new like!",
                    timestamp = timestamp,
                    isRead = "false"
                )
                notificationsRef.child(receiverId).child(notificationId).setValue(notification).await()
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to send like notification.")
            }
        }
    }

    fun fetchUserProfile(
        userId: String,
        onSuccess: (Profile) -> Unit,
        onFailure: (String) -> Unit
    ) {
        usersRef.child(userId).get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.getValue(Profile::class.java)
                if (profile != null) {
                    onSuccess(profile)
                } else {
                    onFailure("Profile not found")
                }
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to fetch profile")
            }
    }


    // Send a match notification
    fun sendMatchNotification(
        senderId: String,
        receiverId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val notificationId = notificationsRef.child(receiverId).push().key
                    ?: throw Exception("Failed to generate notification ID")

                val notification = Notification(
                    id = notificationId,
                    type = "new_match",
                    senderId = senderId,
                    senderUsername = "", // Do not include username
                    message = "You have matched with a user!",
                    timestamp = timestamp,
                    isRead = "false"
                )
                notificationsRef.child(receiverId).child(notificationId).setValue(notification).await()
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.message ?: "Failed to send match notification.")
            }
        }
    }
}