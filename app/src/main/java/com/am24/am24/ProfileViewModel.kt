package com.am24.am24

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlinx.coroutines.Job
import java.util.concurrent.TimeUnit

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ProfileViewModel"
    private val sessionRepository = SessionDataRepository
    private val database = FirebaseRefs.db
    private val notificationsRef = database.getReference("notifications")
    private val profileCollection = FirebaseRefs.userProfiles
    private val _verificationStatus = MutableStateFlow<String?>(null)
    val verificationStatus: StateFlow<String?> = _verificationStatus

    // Match Pop-Up State
    private val _matchPopUpState = MutableStateFlow<Pair<Profile, Profile>?>(null)
    val matchPopUpState: StateFlow<Pair<Profile, Profile>?> get() = _matchPopUpState

    // Add this MutableStateFlow at the top of ProfileViewModel
    private val _currentUserProfile = MutableStateFlow<Profile?>(null)
    val currentUserProfile: StateFlow<Profile?> get() = _currentUserProfile

    // Sexual orientation of current user
    private val _sexualOrientation = MutableStateFlow<String?>(null)
    val sexualOrientation: StateFlow<String?> get() = _sexualOrientation

    private val _complimentsLeft = MutableStateFlow(0)
    val complimentsLeft: StateFlow<Int> get() = _complimentsLeft

    private var profileMetadataListener: ListenerRegistration? = null
    private var complimentsWatcherStarted = false

    private var profileCollectionJob: Job? = null
    // NEW: Voice recording properties
    private var voiceRecorder: MediaRecorder? = null
    var voiceNoteUrl: String? = null
    var voiceNoteFilePath: String? = null
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _isPlus = MutableStateFlow(false)
    val isPlus: StateFlow<Boolean> = _isPlus

    private val _loginPlusExpiry = MutableStateFlow(0L)
    val loginPlusExpiry: StateFlow<Long> = _loginPlusExpiry

    private val _entryFeePaidAt = MutableStateFlow(0L)
    val entryFeePaidAt: StateFlow<Long> = _entryFeePaidAt

    private val _isEntryFeePaid = MutableStateFlow(false)
    val isEntryFeePaid: StateFlow<Boolean> = _isEntryFeePaid

    private val _premiumExpiryDate = MutableStateFlow<Long?>(null)
    val premiumExpiryDate: StateFlow<Long?> = _premiumExpiryDate

    private val _subscriptionStatus = MutableStateFlow<String?>(null)
    val subscriptionStatus: StateFlow<String?> = _subscriptionStatus

    private val _nextRenewal = MutableStateFlow<Long?>(null)
    val nextRenewal: StateFlow<Long?> = _nextRenewal

    private val _entryFeePlusIntroSeen = MutableStateFlow(true)
    val entryFeePlusIntroSeen: StateFlow<Boolean> = _entryFeePlusIntroSeen

    private val _entryFeeOfferExpiry = MutableStateFlow(0L)
    val entryFeeOfferExpiry: StateFlow<Long> = _entryFeeOfferExpiry

    private val _entryFeeOfferSeen = MutableStateFlow(false)
    val entryFeeOfferSeen: StateFlow<Boolean> = _entryFeeOfferSeen

    private val _subscriptionId = MutableStateFlow<String?>(null)
    val subscriptionId: StateFlow<String?> = _subscriptionId

    private val _remainingSwipes = MutableStateFlow(0)
    val remainingSwipes: StateFlow<Int> = _remainingSwipes

    private val _availableAiMessages = MutableStateFlow(0)
    val availableAiMessages: StateFlow<Int> = _availableAiMessages

    private var didSetAppOpenScreen = false
    private var lastTrackedScreen: String? = null

    // Add this function to fetch and store the current user's profile
    fun fetchCurrentUserProfile() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId.isNullOrEmpty()) {
            Log.e(TAG, "No current user ID found")
            return
        }

        sessionRepository.start(currentUserId)
        if (profileCollectionJob != null) return

        profileCollectionJob = viewModelScope.launch {
            sessionRepository.profile.collect { profile ->
                if (profile != null) {
                    Log.d(TAG, "Session profile updated for $currentUserId")
                    _currentUserProfile.value = profile
                    _complimentsLeft.value = profile.availableCompliments
                    _sexualOrientation.value = profile.sexualOrientation
                    if (!complimentsWatcherStarted) {
                        watchProfileMetadata()
                        complimentsWatcherStarted = true
                    }
                }
            }
        }
    }

    private fun watchProfileMetadata() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (profileMetadataListener != null) return
        val docRef = profileCollection.document(uid)
        profileMetadataListener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "watchProfileMetadata cancelled: ${error.message}")
                return@addSnapshotListener
            }
            val data = snapshot?.data ?: return@addSnapshotListener
            val loginPlus = (data["loginPlusExpiry"] as? Number)?.toLong() ?: 0L
            val entryFeePaid = data["isEntryFeePaid"] as? Boolean ?: false
            val entryFeePaidAtValue = (data["entryFeePaidAt"] as? Number)?.toLong() ?: 0L
            val premiumExpiry = (data["premiumExpiryDate"] as? Number)?.toLong()
            val subscriptionStatusValue = data["subscriptionStatus"] as? String
            val nextRenewalValue = (data["nextRenewal"] as? Number)?.toLong()
            val subscriptionIdValue = (data["subscription"] as? Map<*, *>)?.get("id") as? String
            val entryFeePlusIntroSeenValue = data["entryFeePlusIntroSeen"] as? Boolean ?: true
            val entryFeeOfferExpiryValue = (data["entryFeeOfferExpiry"] as? Number)?.toLong() ?: 0L
            val entryFeeOfferSeenValue = data["entryFeeOfferSeen"] as? Boolean ?: false
            val swipesRemaining = ((data["swipesInfo"] as? Map<*, *>)?.get("remainingSwipes") as? Number)?.toInt() ?: 0
            val availableAiMessagesValue = (data["availableAiMessages"] as? Number)?.toInt() ?: 0
            val plusFlag = data["isPlus"] as? Boolean ?: false
            val premiumFlag = data["isPremium"] as? Boolean ?: false
            val adminFlag = data["isAdmin"] as? Boolean ?: false
            val availableComplimentsValue = (data["availableCompliments"] as? Number)?.toInt() ?: 0

            val now = System.currentTimeMillis()
            val sanitizedOfferExpiry =
                if (entryFeeOfferExpiryValue > 0L && entryFeeOfferExpiryValue < now) 0L
                else entryFeeOfferExpiryValue
            val entryFeePaidExpiry = entryFeePaidAtValue
                .takeIf { it > 0L }
                ?.let { it + TimeUnit.DAYS.toMillis(30) }
            val nextRenewalActive = nextRenewalValue?.takeIf { it > now }
            val loginPlusActive = loginPlus.takeIf { it > now }
            val entryFeeActive = entryFeePaid && (
                    (entryFeePaidExpiry?.let { it > now } == true) ||
                            nextRenewalActive != null ||
                            loginPlusActive != null
                    )

            _loginPlusExpiry.value = loginPlus
            _isEntryFeePaid.value = entryFeePaid
            _entryFeePaidAt.value = entryFeePaidAtValue
            _premiumExpiryDate.value = premiumExpiry
            _subscriptionStatus.value = subscriptionStatusValue
            _nextRenewal.value = nextRenewalValue
            _subscriptionId.value = subscriptionIdValue
            _entryFeePlusIntroSeen.value = entryFeePlusIntroSeenValue
            _entryFeeOfferExpiry.value = sanitizedOfferExpiry
            _entryFeeOfferSeen.value = entryFeeOfferSeenValue
            _remainingSwipes.value = swipesRemaining
            _availableAiMessages.value = availableAiMessagesValue
            _isPlus.value = plusFlag
            _isPremium.value = premiumFlag
            _isAdmin.value = adminFlag
            _complimentsLeft.value = availableComplimentsValue

            if (entryFeeOfferExpiryValue > 0L && entryFeeOfferExpiryValue < now) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { docRef.update("entryFeeOfferExpiry", FieldValue.delete()).await() }
                }
            }

            if (plusFlag && entryFeePaid && !entryFeeActive && !premiumFlag) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        docRef.update(
                            mapOf(
                                "isPlus" to false,
                                "isEntryFeePaid" to false
                            )
                        ).await()
                    }
                }
            }
        }
    }
    // ────────────────────────────────────────────────────────────────────────

    fun prepareForAppOpenTracking() {
        didSetAppOpenScreen = false
    }

    fun trackCurrentScreen(route: String?) {
        if (route.isNullOrBlank() || route == lastTrackedScreen) return
        lastTrackedScreen = route
        _currentUserProfile.update { profile ->
            profile?.copy(
                currentScreen = route,
                lastScreenOnAppOpen = if (!didSetAppOpenScreen) route else profile.lastScreenOnAppOpen
            )
        }

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val updates = mutableMapOf<String, Any>("currentScreen" to route)
        if (!didSetAppOpenScreen) {
            updates["lastScreenOnAppOpen"] = route
            didSetAppOpenScreen = true
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                profileCollection.document(currentUserId)
                    .set(updates, SetOptions.merge())
                    .await()
            }
                .onFailure { Log.e(TAG, "Failed to update screen tracking", it) }
        }
    }

    fun trackLastScreenBeforeClose() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val screen = lastTrackedScreen ?: _currentUserProfile.value?.currentScreen ?: return
        _currentUserProfile.update { profile ->
            profile?.copy(lastScreenBeforeClose = screen)
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                profileCollection.document(currentUserId)
                    .set(mapOf("lastScreenBeforeClose" to screen), SetOptions.merge())
                    .await()
            }.onFailure { Log.e(TAG, "Failed to update last screen before close", it) }
        }
    }

    init {
        watchProfileMetadata()
    }

    fun fetchUsernameById(userId: String, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        profileCollection.document(userId).get().addOnSuccessListener { snapshot ->
            val username = snapshot.getString("username")
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
                    "ethnicity"                  to profileWithScore.ethnicity,
                    "incomeLevel"               to profileWithScore.incomeLevel,
                    "lookingFor"                  to profileWithScore.lookingFor,
                    "loveLanguage"                  to profileWithScore.loveLanguage,
                    "sexualOrientation"          to profileWithScore.sexualOrientation,
                    "currentScreen"             to profileWithScore.currentScreen,
                    "lastScreenBeforeClose"     to profileWithScore.lastScreenBeforeClose,
                    "lastScreenOnAppOpen"        to profileWithScore.lastScreenOnAppOpen,
                    "interests"                   to profileWithScore.interests.map {
                        mapOf("name" to it.name, "emoji" to it.emoji)
                    },
                    "lifestyle"                   to profileWithScore.lifestyle,

                    // identity chips & visibility flags
                    "roles"                       to profileWithScore.roles,
                    "showRolesOnProfile"         to profileWithScore.showRolesOnProfile,
                    "tribes"                      to profileWithScore.tribes,
                    "showTribesOnProfile"        to profileWithScore.showTribesOnProfile,
                    "bodyType"                   to profileWithScore.bodyType,
                    "showBodyTypeOnProfile"      to profileWithScore.showBodyTypeOnProfile,
                    "kinks"                       to profileWithScore.kinks,
                    "showKinksOnProfile"         to profileWithScore.showKinksOnProfile,

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

                // 3) Push to Firebase (Firestore)
                profileCollection.document(userId).set(updates, SetOptions.merge()).await()

                // 4) If you keep local state
                _currentUserProfile.value = profileWithScore
                _sexualOrientation.value = profileWithScore.sexualOrientation
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
            val snapshot = profileCollection.document(userId).get().await()
            snapshot.safeGetProfile("getProfileById/$userId")
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

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

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
            .continueWithTask { uploadTask ->
                val result = uploadTask.result
                if (!uploadTask.isSuccessful || result == null) {
                    val exception = uploadTask.exception ?: Exception("Upload failed")
                    return@continueWithTask Tasks.forException<Uri>(exception)
                }
                result.storage.downloadUrl
            }
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

    // Send a like notification
    fun sendLikeNotification(
        senderId: String,
        receiverId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (UserDeletionCache.isDeleted(database, senderId)) {
                    Log.w(TAG, "Skipping like notification: sender $senderId is deleted.")
                    return@launch
                }
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
        profileCollection.document(userId).get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.safeGetProfile("fetchUserProfile/$userId")?.copy(
                    isMatrimonyMode = snapshot.getBoolean("isMatrimonyMode") ?: false
                )
                if (profile != null) {
                    onSuccess(profile)
                } else {
                    onFailure("Profile not found")
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to fetch profile for userId $userId: ${error.message}")
                onFailure(error.message ?: "Failed to fetch profile")
            }
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

    /** Call this when the user earns an extra boost (e.g. via a rewarded ad). */
    fun incrementBoostsLocal() {
        _currentUserProfile.update { prof ->
            prof?.copy(
                availableBoosts = prof.availableBoosts + 1
            )
        }
    }
    /** Deduct one boost locally after using a boost. */
    fun decrementBoostsLocal() {
        _currentUserProfile.update { prof ->
            prof?.copy(
                availableBoosts = (prof.availableBoosts - 1).coerceAtLeast(0)
            )
        }
    }


    /** Increment compliments balance locally after a rewarded ad. */
    fun incrementComplimentsLocal() {
        _currentUserProfile.update { prof ->
            prof?.copy(
                availableCompliments = prof.availableCompliments + 1
            )
        }
    }
    fun joinGroupChat(groupId: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (groupId.isBlank()) return
        val currentProfile = _currentUserProfile.value ?: return
        val updatedIds = currentProfile.leftGroupChatIds.filterNot { it == groupId }

        _currentUserProfile.update { prof ->
            prof?.copy(leftGroupChatIds = updatedIds)
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                profileCollection.document(currentUserId)
                    .set(mapOf("leftGroupChatIds" to updatedIds), SetOptions.merge())
                    .await()
            }.onFailure { Log.e(TAG, "Failed to update left group chats", it) }
        }
    }
    fun leaveGroupChat(groupId: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (groupId.isBlank()) return
        val currentProfile = _currentUserProfile.value ?: return
        val updatedIds = (currentProfile.leftGroupChatIds + groupId).distinct()

        _currentUserProfile.update { prof ->
            prof?.copy(leftGroupChatIds = updatedIds)
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                profileCollection.document(currentUserId)
                    .set(mapOf("leftGroupChatIds" to updatedIds), SetOptions.merge())
                    .await()
            }.onFailure { Log.e(TAG, "Failed to update left group chats", it) }
        }
    }
    override fun onCleared() {
        super.onCleared()

        profileMetadataListener?.remove()
        profileMetadataListener = null
        complimentsWatcherStarted = false

        profileCollectionJob?.cancel()
        profileCollectionJob = null
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
