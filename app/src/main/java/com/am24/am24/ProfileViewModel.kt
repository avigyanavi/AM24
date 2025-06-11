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
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.flow.update

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ProfileViewModel"
    private val usersRef = FirebaseRefs.db.getReference("users")
    private val database = FirebaseRefs.db
    private val notificationsRef = database.getReference("notifications")
    private val chatRef = database.getReference("chats") // New chat reference for DM creation

    private val _verificationStatus = MutableStateFlow<String?>(null)
    val verificationStatus: StateFlow<String?> = _verificationStatus

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

    init {
        watchAdminFlag()    // ← start listening immediately
    }

    fun fetchProfilesByCity(cityName: String, onResult: (List<Profile>) -> Unit) {
        val dbRef = FirebaseRefs.db.getReference("profiles")
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
        val dbRef = FirebaseRefs.db.getReference("profiles")
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
        val userRef = FirebaseRefs.db.getReference("users").child(userId)
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
                    "voiceNoteUrl"             to profileWithScore.voiceNoteUrl,
                    "bio"                      to profileWithScore.bio,
                    "email"                       to profileWithScore.email,
                    "name"                        to profileWithScore.name,
                    "gender"                      to profileWithScore.gender,
                    "hometown"                    to profileWithScore.hometown,
                    "customHometown"              to profileWithScore.customHometown,
                    "highSchool"                  to profileWithScore.highSchool,
                    "highSchoolGraduationYear"    to profileWithScore.highSchoolGraduationYear,
                    "height"                      to profileWithScore.height,
                    "height2"                     to profileWithScore.height2,
                    "college"                     to profileWithScore.college,
                    "collegeGraduationYear"       to profileWithScore.collegeGraduationYear,
                    "collegeDegree"               to profileWithScore.collegeDegree,
                    "postGraduation"              to profileWithScore.postGraduation,
                    "postGraduationYear"          to profileWithScore.postGraduationYear,
                    "postGraduationDegree"        to profileWithScore.postGraduationDegree,
                    "politics"                    to profileWithScore.politics,
                    "community"                   to profileWithScore.community,
                    "caste"                       to profileWithScore.caste,
                    "city"                        to profileWithScore.city,
                    "customCity"                  to profileWithScore.customCity,
                    "religion"                    to profileWithScore.religion,
                    "lookingFor"                  to profileWithScore.lookingFor,
                    "loveLanguage"                  to profileWithScore.loveLanguage,
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

    /** Call this right after you know a boost has succeeded. */
    fun decrementBoostsLocal() {
        _currentUserProfile.update { prof ->
            prof?.copy(
                availableBoosts = (prof.availableBoosts - 1).coerceAtLeast(0)
            )
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

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    fun watchAdminFlag() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseRefs.db
            .getReference("users")
            .child(uid)
            .child("isAdmin")
            .addValueEventListener(object: ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    _isAdmin.value = snap.getValue(Boolean::class.java) == true
                }
                override fun onCancelled(e: DatabaseError) { /* log error */ }
            })
    }

    fun uploadGovtSelfie(
        uid: String,
        uri: Uri,
        onComplete: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1) upload selfie image
                val storageRef = FirebaseStorage.getInstance()
                    .reference
                    .child("verifications/$uid/selfie.jpg")
                storageRef.putFile(uri).await()

                // 2) get download URL
                val downloadUrl = storageRef.downloadUrl.await().toString()

                // 3) write selfieUrl into the same verifications/{uid} node
                val verifRef = FirebaseRefs.db
                    .getReference("verifications")
                    .child(uid)
                verifRef.child("selfieUrl").setValue(downloadUrl).await()

                // 4) callback
                onComplete(true, "Selfie submitted, review in 48 h")
            } catch (e: Exception) {
                onComplete(false, "Failed to submit selfie: ${e.message}")
            }
        }
    }

    fun uploadVoiceToRealtime(storageRef: StorageReference, uri: Uri, onUploaded: (downloadUrl: String) -> Unit
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val voiceNoteRef = storageRef.child("users/$userId/voice_note.mp3") // Adjust file extension if needed
        voiceNoteRef.putFile(uri)
            .continueWithTask { it.result!!.storage.downloadUrl }
            .addOnSuccessListener { downloadUri ->
                voiceNoteUrl = downloadUri.toString()
                onUploaded(voiceNoteUrl!!)
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

    /** LOW-LEVEL helper – writes one row under /notifications */
    private suspend fun pushNotification(
        receiverId: String,
        type: String,
        senderId: String,
        message: String,
        extra: Map<String, Any?> = emptyMap()
    ) {
        val ts = System.currentTimeMillis()
        val id = notificationsRef.child(receiverId).push().key ?: return

        val payload = Notification(
            id             = id,
            type           = type,
            senderId       = senderId,
            senderUsername = "",           // we resolve lazily when displaying
            message        = message,
            timestamp      = ts,
            isRead         = "false"
        )

        // save the main object
        notificationsRef.child(receiverId).child(id).setValue(payload).await()

        // (optional) save extras in a side node
        extra.forEach { (k, v) ->
            database
                .getReference("notificationsExtra")
                .child(receiverId)
                .child(id)
                .child(k)
                .setValue(v)
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

    /**
     * Uploads a government ID image to:
     *   • Storage at "verifications/{uid}/id.jpg"
     *   • Realtime DB at "verifications/{uid}" → { status: "pending", timestamp: ... }
     *
     * Calls onComplete(true, message) on success or onComplete(false, errorMsg) on failure.
     */
    fun uploadGovtId(
        uid: String,
        uri: Uri,
        onComplete: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1) upload image
                val storageRef = FirebaseStorage.getInstance()
                    .reference
                    .child("verifications/$uid/id.jpg")
                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                // 2) write status
                val verifRef = FirebaseRefs.db
                    .getReference("verifications")
                    .child(uid)
                val data = mapOf(
                    "photoUrl"  to downloadUrl,
                    "status" to "pending",
                    "timestamp" to System.currentTimeMillis()
                )
                verifRef.setValue(data).await()

                // 3) callback
                onComplete(true, "ID submitted, review in 48 h")
            } catch (e: Exception) {
                onComplete(false, "Failed to submit ID: ${e.message}")
            }
        }
    }

    /**
     * Marks the user’s profile as verified by setting
     * `users/{uid}/isConsultantVerified = true`, and updates local state.
     */

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

    fun observeVerificationStatus(uid: String) {
        val verRef = FirebaseRefs.db
            .getReference("verifications")
            .child(uid)
            .child("status")

        // detach any previous listener if you like…
        verRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                _verificationStatus.value = snap.getValue(String::class.java)
            }
            override fun onCancelled(err: DatabaseError) {
                Log.e(TAG, "Verification listener failed: ${err.message}")
            }
        })
    }

    /** Notify receiver that someone sent a compliment */
    fun sendComplimentNotification(
        senderId: String,
        receiverId: String,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) = viewModelScope.launch {
        try {
            pushNotification(
                receiverId = receiverId,
                type       = "new_compliment",
                senderId   = senderId,
                message    = "senderUsername sent you a compliment 💌"
            )
            onSuccess()
        } catch (e: Exception) {
            onFailure(e.message ?: "Failed to send compliment notification")
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
