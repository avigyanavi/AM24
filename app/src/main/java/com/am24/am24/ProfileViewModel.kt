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
        if (currentUserId.isNullOrEmpty()) {
            Log.e(TAG, "No current user ID found")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = usersRef.child(currentUserId).get().await()
                val profile = snapshot.getValue(Profile::class.java)?.copy(
                    isMatrimonyMode = snapshot.child("isMatrimonyMode").getValue(Boolean::class.java) ?: false
                )
                if (profile != null) {
                    Log.d(TAG, "Fetched profile with isMatrimonyMode: ${profile.isMatrimonyMode}")
                    _currentUserProfile.value = profile
                } else {
                    Log.e(TAG, "Failed to fetch current user's profile: Profile is null")
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
                    .takeIf { it.isNotBlank() }
                    ?: FirebaseAuth.getInstance().currentUser?.uid
                    ?: throw Exception("No userId found for updatedProfile")

                // 1) Recompute local composite
                val newComposite = calculateCompositeScore(updatedProfile)
                val profileWithScore = updatedProfile.copy(
                    am24RankingCompositeScore = newComposite
                )

                // 2) Build updates for all changed fields
                val updates = mapOf<String, Any?>(
                    // from your partial code + the "score" fields
                    "profilepicUrl"               to profileWithScore.profilepicUrl,
                    "optionalPhotoUrls"           to profileWithScore.optionalPhotoUrls,
                    "voiceNoteUrl"                to profileWithScore.voiceNoteUrl,

                    "email"                       to profileWithScore.email,
                    "name"                        to profileWithScore.name,
                    "bio"                         to profileWithScore.bio,
                    "gender"                      to profileWithScore.gender,
                    "hometown"                    to profileWithScore.hometown,
                    "highSchool"                  to profileWithScore.highSchool,
                    "highSchoolGraduationYear"    to profileWithScore.highSchoolGraduationYear,
                    "college"                     to profileWithScore.college,
                    "collegeGraduationYear"       to profileWithScore.collegeGraduationYear,
                    "collegeDegree"               to profileWithScore.collegeDegree,
                    "postGraduation"              to profileWithScore.postGraduation,
                    "postGraduationYear"          to profileWithScore.postGraduationYear,
                    "postGraduationDegree"        to profileWithScore.postGraduationDegree,
                    "community"                   to profileWithScore.community,
                    "religion"                    to profileWithScore.religion,
                    "lookingFor"                  to profileWithScore.lookingFor,
                    "interests"                   to profileWithScore.interests.map {
                        mapOf("name" to it.name, "emoji" to it.emoji)
                    },
                    "lifestyle"                   to profileWithScore.lifestyle,

                    // job & work
                    "jobRole"                     to profileWithScore.jobRole,
                    "customJobRole"               to profileWithScore.customJobRole,
                    "work"                        to profileWithScore.work,
                    "customWork"                  to profileWithScore.customWork,

                    // matrimony toggles & fields
                    "isMatrimonyMode"             to profileWithScore.isMatrimonyMode,
                    "marriageTimeline"            to profileWithScore.marriageTimeline,
                    "relocationPreference"        to profileWithScore.relocationPreference,
                    "postMarriageCareerPlan"      to profileWithScore.postMarriageCareerPlan,
                    "traditionalVsLiberal"        to profileWithScore.traditionalVsLiberal,
                    "fatherOccupation"            to profileWithScore.fatherOccupation,
                    "motherOccupation"            to profileWithScore.motherOccupation,
                    "isConsultantVerified"        to profileWithScore.isConsultantVerified,

                    // data that influences the composite
                    "averageRating"               to profileWithScore.averageRating,
                    "averageSwipeRightsOnUser"    to profileWithScore.averageSwipeRightsOnUser,
                    "averageUpvoteCount"          to profileWithScore.averageUpvoteCount,
                    "averageDownvoteCount"        to profileWithScore.averageDownvoteCount,
                    "matchCount"                  to profileWithScore.matchCount,
                    "matchCountPerSwipeRight"     to profileWithScore.matchCountPerSwipeRight,

                    // final composite
                    "am24RankingCompositeScore"   to newComposite
                )

                // 3) Push to Firebase
                usersRef.child(userId).updateChildren(updates).await()

                // 4) If you keep local state
                _currentUserProfile.value = profileWithScore
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
                try {
                    val profile = snapshot.getValue(Profile::class.java)?.copy(
                        // Ensure isMatrimonyMode has a default of false if not set in Firebase
                        isMatrimonyMode = snapshot.child("isMatrimonyMode").getValue(Boolean::class.java) ?: false
                    )
                    if (profile != null) {
                        onSuccess(profile)
                    } else {
                        onFailure("Profile not found")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing profile for userId $userId: ${e.message}")
                    onFailure("Failed to parse profile data")
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to fetch profile for userId $userId: ${error.message}")
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
/** ------------------------------------------------------------------
 *  Composite‑score formula for *this* profile only
 *  ------------------------------------------------------------------
 *  We normalise each component to 0‒1, multiply by a weight,
 *  then sum.  Feel free to tweak the weights.
 */
private fun calculateCompositeScore(p: Profile): Double {
    /* weights must sum to 1 */
    val ratingWeight     = 0.30   // averageRating (0‑5)
    val swipeWeight      = 0.20   // averageSwipeRightsOnUser (0‑1)
    val upvoteWeight     = 0.15   // up‑vote ratio (0‑1)
    val matchProbWeight  = 0.15   // matchCountPerSwipeRight (0‑1)
    val matchesWeight    = 0.10   // total matches (normalised 0‑1)
    val completionWeight = 0.10   // profileCompletionPercentage (0‑1)

    /* 0‒1 normalised sub‑scores */
    val ratingPart  = (p.averageRating / 5.0).coerceIn(0.0, 1.0)
    val swipePart   = p.averageSwipeRightsOnUser.coerceIn(0.0, 1.0)

    val votesTotal  = p.averageUpvoteCount + p.averageDownvoteCount
    val upvotePart  = if (votesTotal > 0)
        (p.averageUpvoteCount / votesTotal).coerceIn(0.0, 1.0) else 0.0

    val matchProb   = p.getCalculatedMatchCountPerSwipeRight().coerceIn(0.0, 1.0)

    /* total matches – normalise with a soft cap of 100 */
    val matchesPart = (p.matchCount / 100.0).coerceIn(0.0, 1.0)

    val completionPart = (p.profileCompletionPercentage / 100.0).coerceIn(0.0, 1.0)

    return ratingWeight     * ratingPart +
            swipeWeight      * swipePart  +
            upvoteWeight     * upvotePart +
            matchProbWeight  * matchProb  +
            matchesWeight    * matchesPart+
            completionWeight * completionPart
}
