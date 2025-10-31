package com.am24.am24

import DatingViewModel
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import android.widget.VideoView
import com.am24.am24.ui.theme.DarkGrayBackground
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageView
import androidx.media3.transformer.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.am24.am24.util.LocaleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.media3.common.MimeTypes
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.transformer.*
import com.am24.am24.util.CachedFullscreenVideoPlayer
import java.io.IOException
import kotlin.compareTo
import kotlin.dec
import com.am24.am24.ui.CompatibilityMeter

// Updated Message data class (without viewed field)
data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false,
    val mediaType: String? = null, // Supports "photo", "video" or "voice"
    val mediaUrl: String? = null,
    val processed: Boolean = false,
    @get:PropertyName("isPost")
    @set:PropertyName("isPost")
    var isPost: Boolean = false // Add this field to flag shared posts
)

private object RatingPromptSession {
    val shownForChat = mutableSetOf<String>()   // chatId → prompt already shown
}

@Composable
fun ChatScreen(navController: NavController, otherUserId: String) {
    val profileViewModel: ProfileViewModel = viewModel()
    ChatScreenContent(navController, otherUserId, profileViewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
    navController: NavController,
    otherUserId: String,
    profileViewModel: ProfileViewModel,
) {
    var previewRefresh by remember { mutableStateOf(0) }
    var pendingEditUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val datingViewModel: DatingViewModel = viewModel()
    var isSendingMessage by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = LocalContext.current as Activity
    DisposableEffect(Unit) {
        // Prevent screenshots on chat view
        activity.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val compliments by datingViewModel.complimentsReceived.collectAsState()
    val compliment = compliments[otherUserId]
    var fullScreenTarget by remember { mutableStateOf<Message?>(null) }
    val database = FirebaseRefs.db
    val usersRef = database.getReference("users")
    val chatId = getChatId(currentUserId, otherUserId)
    val messagesRef = database.getReference("messages/$chatId")
    val notificationsRef = database.getReference("notifications")
    val ratingsRef = database.getReference("ratings")
    val reportsRef = database.getReference("reports")
    var isOtherUserTyping by remember { mutableStateOf(false) }
    val typingRef = database.getReference("typing/$chatId/$otherUserId")
    var averageRating by remember { mutableStateOf(0.0) }
    var yourRating by rememberSaveable(otherUserId) { mutableStateOf(-1.0) }
    var currentUserProfile by remember { mutableStateOf<Profile?>(null) }
    var otherUserProfile by remember { mutableStateOf<Profile?>(null) }

    val privateAlbumSharedMe       = currentUserProfile?.allowExplicitPics  == true
    val privateAlbumSharedPartner  = otherUserProfile ?.allowExplicitPics  == true
    var deleteForever           = currentUserProfile?.deleteTimerOverride == true
    val messages = remember { mutableStateListOf<Message>() }
    var messageText by remember { mutableStateOf("") }
    var showRating by remember { mutableStateOf(true) }
    var aiMatchResult by remember { mutableStateOf<AiMatchCheckResult?>(null) }

    // decide *once* per session
    LaunchedEffect(Unit) {
        if (yourRating < 0 && chatId !in RatingPromptSession.shownForChat) {
            showRating = true
            RatingPromptSession.shownForChat += chatId     // remember for the session
        }
    }
    LaunchedEffect(messages.size, yourRating) {
        if (yourRating < 0       // not yet rated
            && messages.size >= 5
            && showRating) {
            showRating = false   // auto-hide after 5 chat messages
        }
    }
    var moreOptionsMenuExpanded by remember { mutableStateOf(false) }
    var showClearChatMenu by remember { mutableStateOf(false) }
    var showDeleteTimerMenu by remember { mutableStateOf(false) }
    var showUnmatchDialog by remember { mutableStateOf(false) } // Added for unmatch confirmation
    val ONE_MONTH_MS = 30L * 24 * 60 * 60 * 1000
    var deleteTimer by remember { mutableStateOf<Long?>(ONE_MONTH_MS) }
    var suggestions by remember { mutableStateOf<ChatSuggestions?>(null) }
    var suggestionsExpanded by remember { mutableStateOf(false) }
    var placeSuggestions by remember { mutableStateOf<List<PlaceDetails>?>(null) }
    var placeSuggestionsExpanded by remember { mutableStateOf(false) }
    var isLoadingSuggestions by remember { mutableStateOf(false) }
    var isLoadingPlaces by remember { mutableStateOf(false) }
    var isLoadingMessages by remember { mutableStateOf(true) }
    var isLoadingProfiles by remember { mutableStateOf(true) }
    var isUploadingMedia by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var recorder: MediaRecorder? by remember { mutableStateOf(null) }
    var recordFile: File? by remember { mutableStateOf(null) }
    val maxDurationMs = 30 * 1000
    var recordingTimeLeft by remember { mutableStateOf(maxDurationMs) }
    var recordedVoiceUri by remember { mutableStateOf<Uri?>(null) }
    var isVoicePlaying by remember { mutableStateOf(false) }
    var voiceProgress by remember { mutableStateOf(0f) }
    var voicePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val ctx = LocalContext.current
    var chatLang by rememberSaveable { mutableStateOf(LocaleUtils.getSavedLang(ctx)) }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedMediaType by remember { mutableStateOf<String?>(null) }
    var showReportDialog by remember { mutableStateOf(false) } // Added for report dialog
    val userRef = FirebaseRefs.db.getReference("users").child(currentUserId)
    // add next to the other top-level state vars
    var aiMessagesLeft by remember { mutableStateOf(0) }          // ★

    /* ───────────────────  LOAD THE INITIAL BALANCE  ────────────────── */
// place this *once* near your other LaunchedEffect(Unit) blocks
    LaunchedEffect(Unit) {                                                       // ★ BEGIN AI-LOAD ★
        try {
            val snap = userRef.get().await()
            aiMessagesLeft = snap.child("availableAiMessages")
                .getValue(Int::class.java) ?: 0
        } catch (e: Exception) {
            Log.e("ChatScreen", "Failed loading AiMessages", e)
        }
    } // ★ END AI-LOAD ★

    /* ─────────────────────  CREDIT-CONSUME HELPER  ─────────────────── */
    fun consumeAiMessage(doWork: suspend () -> Unit) = scope.launch {
        if (aiMessagesLeft <= 0) {
            navController.navigate("buyAiMessages")      // bounce to top-up screen
            return@launch
        }
        aiMessagesLeft--
        userRef.child("availableAiMessages").setValue(aiMessagesLeft)            // atomic RTDB update
        doWork()
    }                                                                            // ★ END AI-FUN ★

    // Determine if the current user is premium
    val isPremiumUser = currentUserProfile?.isPremium == true

    val editLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Editor returned correctly
        if (it.resultCode == Activity.RESULT_OK && pendingEditUri != null) {
            selectedMediaUri = pendingEditUri
            previewRefresh++
        }
        pendingEditUri = null
    }

    // Track lifecycle explicitly:
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (pendingEditUri != null) {
                    // Assume edit finished, even if no proper result was returned
                    selectedMediaUri = pendingEditUri
                    previewRefresh++
                    pendingEditUri = null
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(selectedMediaUri, selectedMediaType) {
        if (selectedMediaUri != null && selectedMediaType != null) {
            Log.d("ChatScreen", "Media preview shown: uri=$selectedMediaUri, type=$selectedMediaType")
        } else {
            Log.d("ChatScreen", "Media preview cleared")
        }
    }

    DisposableEffect(typingRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isOtherUserTyping = snapshot.getValue(Boolean::class.java) == true
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatScreen", "Error reading typing status: ${error.message}")
            }
        }
        typingRef.addValueEventListener(listener)
        onDispose { typingRef.removeEventListener(listener) }
    }

    DisposableEffect(Unit) {
        onDispose {
            currentUserProfile?.userId?.let { uid ->
                currentUserProfile = currentUserProfile?.copy(allowExplicitPics = false)
                FirebaseRefs.db.getReference("users/$uid")
                    .child("allowExplicitPics")
                    .setValue(false)
            }
        }
    }

    val takePhotoLauncher: ManagedActivityResultLauncher<Uri, Boolean> =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && pendingPhotoUri != null) {
                selectedMediaUri = pendingPhotoUri
                selectedMediaType = "photo"
            }
            pendingPhotoUri = null
            isUploadingMedia = false
        }

    // ─── NEW single‐intent launcher for video capture ───
    val videoCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isUploadingMedia = false
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedMediaUri  = uri
                selectedMediaType = "video"
            }
        }
    }
    val msg1 = stringResource(R.string.perm_mic_required)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isRecording = true
            recordFile = File(context.filesDir, "voice_message.aac")
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recordFile?.absolutePath)
                prepare()
                start()
            }
            recordingTimeLeft = maxDurationMs
        } else if (!granted) {
            Toast.makeText(context, msg1, Toast.LENGTH_SHORT).show()
        }
    }
    val msg2 = stringResource(R.string.perm_camera_required)

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when (selectedMediaType) {
                "photo" -> {
                    selectedMediaUri = freshPhotoUri(context)
                    takePhotoLauncher.launch(selectedMediaUri!!)
                }
                "video" -> {
                    // 🎥 now use your new Intent + launcher
                    isUploadingMedia = true
                    val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30)  // 30 sec max
                        putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)    // high quality
                    }
                    videoCaptureLauncher.launch(intent)
                }
            }
        } else {
            Toast.makeText(context, msg2, Toast.LENGTH_SHORT).show()
        }
    }


    val onToggleRecord: () -> Unit = {
        if (isRecording) {
            recorder?.stop()
            recorder?.release()
            recorder = null
            isRecording = false
            recordedVoiceUri = Uri.fromFile(recordFile)
        } else {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                isRecording = true
                messageText = ""
                recordedVoiceUri = null
                recordFile = File(context.filesDir, "voice_message.aac")
                recorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(recordFile?.absolutePath)
                    prepare(); start()
                }
                recordingTimeLeft = maxDurationMs
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedMediaUri = it
            selectedMediaType = "photo"
            Log.d("ChatScreen", "Photo picked: $uri")
        }
    }

    val msg3 = stringResource(R.string.video_duration_limit_30s)

    val pickVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // check duration
            val retriever = MediaMetadataRetriever().apply { setDataSource(context, it) }
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            retriever.release()
            if (dur > 30_000L) {
                Toast.makeText(context, msg3, Toast.LENGTH_SHORT).show()
                return@let
            }
            selectedMediaUri  = it
            selectedMediaType = "video"
            Log.d("ChatScreen", "Video picked: $uri")
        }
    }
    val mensaj = stringResource(R.string.message) + "]"
    val vm = stringResource(R.string.voice_message)

    val sendHandler: () -> Unit = mySend@{

        if (isSendingMessage || isUploadingMedia) return@mySend
        isSendingMessage = true

        /* ──────────────────────────────────────────────────
           1.  PHOTO / VIDEO  (selectedMediaUri != null)
           ────────────────────────────────────────────────── */
        if (selectedMediaUri != null && selectedMediaType != null) {
            scope.launch {
                isUploadingMedia = true
                try {
                    sendMediaMessage(
                        currentUserId, otherUserId, chatId,
                        selectedMediaUri!!, selectedMediaType!!,
                        messagesRef, context
                    )
                    postNotification(
                        notificationsRef = notificationsRef,
                        toUserId = otherUserId,
                        fromUserId = currentUserId,
                        fromUsername = currentUserProfile?.username ?: "",
                        message = "[${selectedMediaType!!.replaceFirstChar { it.uppercase() }}" +mensaj,
                    )
                } finally {
                    selectedMediaUri  = null
                    selectedMediaType = null
                    isUploadingMedia  = false
                    isSendingMessage  = false
                }
            }
            return@mySend
        }

        /* ──────────────────────────────────────────────────
           2.  VOICE  (no moderation for now)
           ────────────────────────────────────────────────── */
        if (recordedVoiceUri != null) {
            sendVoiceMessage(
                currentUserId, otherUserId, chatId,
                recordedVoiceUri!!, messagesRef, context
            )
            postNotification(
                notificationsRef = notificationsRef,
                toUserId = otherUserId,
                fromUserId = currentUserId,
                fromUsername = currentUserProfile?.username ?: "",
                message = vm
            )
            recordedVoiceUri = null
            recordFile       = null
            isSendingMessage = false
            return@mySend
        }

        /* ──────────────────────────────────────────────────
           3.  TEXT
           ────────────────────────────────────────────────── */
        if (messageText.isNotBlank()) {
            scope.launch {
                /* 3-C  Push to Firebase (your old code) */
                val newId = messagesRef.push().key ?: return@launch
                val msg = Message(
                    id          = newId,
                    senderId    = currentUserId,
                    receiverId  = otherUserId,
                    text        = messageText,
                    timestamp   = System.currentTimeMillis()
                )
                messagesRef.child(newId).setValue(msg).addOnCompleteListener {
                    isSendingMessage = false
                }
                postNotification(
                    notificationsRef = notificationsRef,
                    toUserId = otherUserId,
                    fromUserId = currentUserId,
                    fromUsername = currentUserProfile?.username ?: "",
                    message = messageText
                )
                database.getReference("typing/$chatId/$currentUserId").setValue(false)
                messageText = ""
            }
            return@mySend
        }

        /* ──────────────────────────────────────────────────
           4.  Nothing to send
           ────────────────────────────────────────────────── */
        isSendingMessage = false
    }

    val plsf = stringResource(R.string.profile_load_self_failed)
    val plof = stringResource(R.string.profile_load_other_failed)

    LaunchedEffect(Unit) {
        isLoadingProfiles = true
        usersRef.child(currentUserId).get().addOnSuccessListener { snapshot ->
            currentUserProfile = snapshot.getValue(Profile::class.java)
            isLoadingProfiles = false
        }.addOnFailureListener {
            Toast.makeText(context, plsf, Toast.LENGTH_SHORT).show()
            isLoadingProfiles = false
        }
        usersRef.child(otherUserId).get().addOnSuccessListener { snapshot ->
            val profile = snapshot.getValue(Profile::class.java)
            if (profile != null) {
                otherUserProfile = profile
                averageRating = profile.averageRating
            }
            isLoadingProfiles = false
        }.addOnFailureListener {
            Toast.makeText(context, plof, Toast.LENGTH_SHORT).show()
            isLoadingProfiles = false
        }
        fetchUserRating(ratingsRef, otherUserId) { rating -> yourRating = rating }
        fetchAverageRating(ratingsRef, otherUserId) { avg -> averageRating = avg }
        val aiRef = FirebaseRefs.db
            .getReference("aiMatchCheck/$currentUserId/$otherUserId")
        try {
            val snap = aiRef.get().await()
            val existing = snap.getValue(AiMatchCheckResult::class.java)
            if (existing != null) {
                aiMatchResult = existing
            } else if (currentUserProfile != null && otherUserProfile != null) {
                runAiMatchCheck(
                    context = context,
                    coroutineScope = scope,
                    currentUserId = currentUserId,
                    currentUserProfile = currentUserProfile!!,
                    otherProfile = otherUserProfile!!
                ) { result -> aiMatchResult = result }
            }
        } catch (e: Exception) {
            Log.e("ChatScreen", "Error fetching AI result: ${e.message}")
        }
    }

    DisposableEffect(messagesRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isLoadingMessages = true
                val newMessages = snapshot.children.mapNotNull { msgSnapshot ->
                    // Skip the "participants" node
                    if (msgSnapshot.key == "participants") return@mapNotNull null

                    try {
                        val map = msgSnapshot.value as? Map<String, Any> ?: return@mapNotNull null
                        Message(
                            id = map["id"] as? String ?: "",
                            senderId = map["senderId"] as? String ?: "",
                            receiverId = map["receiverId"] as? String ?: "",
                            text = map["text"] as? String ?: "",
                            timestamp = (map["timestamp"] as? Long) ?: System.currentTimeMillis(),
                            read = map["read"] as? Boolean ?: false,
                            mediaType = map["mediaType"] as? String,
                            mediaUrl = map["mediaUrl"] as? String,
                            processed = map["processed"] as? Boolean ?: false,
                            isPost = map["isPost"] as? Boolean ?: false
                        ).also { msg ->
                            Log.d("ChatScreen", "Received message: id=${msg.id}, isPost=${msg.isPost}, mediaType=${msg.mediaType}, text=${msg.text}, mediaUrl=${msg.mediaUrl}")
                            if (msg.text.isEmpty() && msg.mediaUrl == null) {
                                Log.w("ChatScreen", "Blank message detected: id=${msg.id}, isPost=${msg.isPost}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatScreen", "Error deserializing message ${msgSnapshot.key}: ${e.message}")
                        null
                    }
                }
                Log.d("ChatScreen", "Fetched ${newMessages.size} messages for chatId=$chatId")
                messages.clear()
                messages.addAll(newMessages)
                isLoadingMessages = false
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatScreen", "Error reading messages: ${error.message}")
                isLoadingMessages = false
            }
        }
        messagesRef.addValueEventListener(listener)
        onDispose { messagesRef.removeEventListener(listener) }
    }

    LaunchedEffect(messages) {
        messages.filter { !it.processed && it.senderId != currentUserId }.forEach { message ->
            postNotification(
                notificationsRef = notificationsRef,
                toUserId = otherUserId,
                fromUserId = message.senderId,
                fromUsername = otherUserProfile?.username ?: "",
                message = message.text ?: "[Media]"
            )
            messagesRef.child(message.id).child("processed").setValue(true)
        }
    }

    LaunchedEffect(deleteTimer, messages) {
        while (true) {
            deleteTimer?.let { timer ->
                val oldestMessage = messages.minByOrNull { it.timestamp }
                if (oldestMessage != null) {
                    val elapsedTime = System.currentTimeMillis() - oldestMessage.timestamp
                    if (elapsedTime >= timer) {
                        messagesRef.removeValue()
                        messages.clear()
                        deleteTimer = null
                    }
                }
                delay(60000)
            } ?: break
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (recordingTimeLeft > 0) {
                delay(1000L)
                recordingTimeLeft -= 1000
            }
            if (isRecording) {
                recorder?.stop()
                recorder?.release()
                recorder = null
                isRecording = false
                recordedVoiceUri = Uri.fromFile(recordFile)
            }
        }
    }


    suspend fun fetchSuggestionsWithRetry(): ChatSuggestions? {
        var attempts = 0
        val maxAttempts = 3
        while (attempts < maxAttempts) {
            try {
                return getChatSuggestions(messages, chatLang)
            } catch (e: JsonSyntaxException) {
                attempts++
                Log.w("ChatScreen", "JSON parse error on attempt $attempts: ${e.message}")
                if (attempts == maxAttempts) {
                    Toast.makeText(context, "Failed to fetch suggestions after $maxAttempts attempts.", Toast.LENGTH_SHORT).show()
                    return null
                }
                delay(1000L)
            }
        }
        return null
    }

    val unmatchmsg = stringResource(R.string.unmatch_success)
    val unmatchfailed = stringResource(R.string.unmatch_failed)
    // Unmatch handler function
    fun unmatchUser() {
        scope.launch {
            try {
                // ❶ Build a single multi-path update that wipes the match entirely
                val updates = mapOf<String, Any?>(
                    // remove the match rows for both users ⬇
                    "matches/$currentUserId/$otherUserId" to null,
                    "matches/$otherUserId/$currentUserId" to null,

                    // delete the whole conversation + typing status ⬇
                    "messages/$chatId" to null,
                    "typing/$chatId"  to null
                )

                // ❷ Execute atomically
                database.reference.updateChildren(updates).await()

                // ❸ Clear any unread-badge notifications for this chat
                notificationsRef.child(currentUserId)
                    .orderByChild("senderId").equalTo(otherUserId)
                    .get().await().children.forEach { it.ref.removeValue() }

                notificationsRef.child(otherUserId)
                    .orderByChild("senderId").equalTo(currentUserId)
                    .get().await().children.forEach { it.ref.removeValue() }

                // ❹ Local UI tidy-up
                messages.clear()
                navController.popBackStack()
                Toast.makeText(context, unmatchmsg, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("ChatScreen", "Unmatch failed: ${e.message}")
                Toast.makeText(context, unmatchfailed, Toast.LENGTH_SHORT).show()
            }
        }
    }


    Scaffold(
        topBar = {
            var showPrivateMenu by remember { mutableStateOf(false) }
            var chattitle = stringResource(R.string.chat_title_fallback)
            var nav_back = stringResource(R.string.nav_back)
            var chatrate = stringResource(R.string.chat_menu_rate)
            var chatclear = stringResource(R.string.chat_menu_clear)
            var chatdeletetimer = stringResource(R.string.chat_menu_delete_timer)
            var chatunmatch = stringResource(R.string.chat_menu_unmatch)
            var chatkeepforever = stringResource(R.string.chat_menu_keep_forever)
            var chatreport = stringResource(R.string.chat_menu_report)
            var chatclearonly = stringResource(R.string.chat_clear_only)
            var duration1 = stringResource(R.string.duration_1d)
            var duration2 = stringResource(R.string.duration_1w)
            var duration3 = stringResource(R.string.duration_1m)
            TopAppBar(
                title = {
                    val scrollState = rememberScrollState()
                    LaunchedEffect(otherUserProfile?.name ?: chattitle) {
                        delay(500)
                        val hasOverflow = snapshotFlow { scrollState.maxValue }
                            .filter { it > 0 }
                            .firstOrNull()
                        if (hasOverflow == null) {
                            scrollState.scrollTo(0)
                            return@LaunchedEffect
                        }
                        while (isActive) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                            delay(1500)
                            scrollState.animateScrollTo(0)
                            delay(1500)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            otherUserProfile?.let {
                                navController.navigate("matchedUserProfile/$otherUserId")
                            }
                        }
                    ) {
                        if (isLoadingProfiles) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            val url = otherUserProfile?.profilepicThumbnailUrl ?: otherUserProfile?.profilepicUrl
                            if (url?.isNotBlank() == true) {
                                val placeholder = painterResource(R.drawable.local_placeholder)
                                val pathKey = Uri.parse(url).path
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(url)
                                        .diskCacheKey(pathKey)
                                        .memoryCacheKey(pathKey)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Profile",
                                    placeholder = placeholder,
                                    error = placeholder,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.Gray),
                                    contentScale = ContentScale.Crop
                                )
                                if (!privateAlbumSharedPartner) {  // overlay but DON'T eat clicks
                                    Icon(
                                        Icons.Default.Block,
                                        contentDescription = null,
                                        tint = Color.Red,
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically)      // RowScope.align – vertical only
                                            .offset(x = (-6).dp, y = (-10).dp)
                                            .size(14.dp)
                                            .pointerInput(Unit) { /* consume nothing */ }
                                    )
                                }
                            } else {
                                otherUserProfile ?: Profile(userId = "", username = "", name = chattitle)
                                AIOrProfileImage(
                                    profile = otherUserProfile ?: Profile(userId = "", username = "", name = ""),
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.Gray)
                                )
                                if (!privateAlbumSharedPartner) {  // overlay but DON'T eat clicks
                                    Icon(
                                        Icons.Default.Block,
                                        contentDescription = null,
                                        tint = Color.Red,
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically)      // RowScope.align – vertical only
                                            .offset(x = (-6).dp, y = (-10).dp)
                                            .size(14.dp)
                                            .pointerInput(Unit) { /* consume nothing */ }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(scrollState, true)
                        ) {
                            Text(
                                text = otherUserProfile?.name ?: chattitle,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            nav_back,
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    val outOfCredits = aiMessagesLeft <= 0        // helper
// ----------------  explicit-pics button  ----------------
                    // ----------------  private-album share button  ----------------
                    IconButton(onClick = { showPrivateMenu = true }) {
                        Icon(
                            if (privateAlbumSharedMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Private album settings",
                            tint = if (privateAlbumSharedMe) Color(0xFFFF5252) else LocalContentColor.current
                        )
                    }
                    DropdownMenu(
                        expanded = showPrivateMenu,
                        onDismissRequest = { showPrivateMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.share_private_album))
                                    Spacer(Modifier.weight(1f))
                                    val fallbackName = stringResource(R.string.your_match)
                                    val name = currentUserProfile?.name ?: fallbackName
                                    val sharedAlbumMsg = stringResource(R.string.user_shared_private_album, name)
                                    val hiddenAlbumMsg = stringResource(R.string.user_hidden_private_album, name)
                                    Switch(
                                        checked = privateAlbumSharedMe,
                                        onCheckedChange = { allowed ->
                                            // ① Update local state + Firebase
                                            currentUserProfile = currentUserProfile?.copy(allowExplicitPics = allowed)
                                            currentUserProfile?.userId?.let { uid ->
                                                FirebaseRefs.db.getReference("users/$uid")
                                                    .child("allowExplicitPics")
                                                    .setValue(allowed)
                                            }

                                            val notifMsg = if (allowed) sharedAlbumMsg else hiddenAlbumMsg

                                            postNotification(
                                                notificationsRef = notificationsRef,
                                                toUserId = otherUserId,
                                                fromUserId = currentUserId,
                                                fromUsername = currentUserProfile?.username ?: "",
                                                message = notifMsg
                                            )
                                        }
                                    )
                                }
                            },
                            onClick = { /* nothing – switch above handles it */ }
                        )
                    }
                    IconButton(
                        onClick = {
                            consumeAiMessage {                    // WILL navigate if credits == 0
                                suggestionsExpanded = true
                                if (suggestions == null || messages.size > 10) {
                                    scope.launch {
                                        isLoadingSuggestions = true
                                        suggestions = fetchSuggestionsWithRetry()
                                        isLoadingSuggestions = false
                                    }
                                }
                            }
                        },
                        enabled = true,                           // ← always clickable
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if (outOfCredits)
                                Color.Gray                        // grey look when exhausted
                            else
                                Color(0xFFFFA500)                 // orange when you still have credits
                        )
                    ) {
                        Icon(Icons.Default.SmartToy, null, modifier = Modifier.size(24.dp))
                    }

                    IconButton(
                        onClick = {
                            consumeAiMessage {
                                placeSuggestionsExpanded = true
                                if (placeSuggestions == null || messages.size > 10) {
                                    scope.launch {
                                        isLoadingPlaces = true
                                        val sugg = fetchSuggestionsWithRetry()
                                        placeSuggestions = sugg?.topics
                                            ?.let { getPlaceSuggestions(it, otherUserProfile, context) }
                                        isLoadingPlaces = false
                                    }
                                }
                            }
                        },
                        enabled = true,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if (outOfCredits)
                                Color.Gray
                            else
                                Color(0xFFFF6F00)
                        )
                    ) {
                        Icon(Icons.Default.LocationCity, null, modifier = Modifier.size(24.dp))
                    }

                    IconButton(onClick = { moreOptionsMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "More Options", tint = Color.White)
                    }
                    DropdownMenu(expanded = moreOptionsMenuExpanded, onDismissRequest = { moreOptionsMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = "Toggle Rating", tint = Color(0xFFFF4500))
                                    Spacer(Modifier.width(4.dp))
                                    Text(chatrate)
                                }
                            },
                            onClick = {
                                moreOptionsMenuExpanded = false
                                showRating = !showRating
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Delete, chatclear, tint = Color.Red)
                                    Spacer(Modifier.width(4.dp))
                                    Text(chatclear)
                                }
                            },
                            onClick = { moreOptionsMenuExpanded = false; showClearChatMenu = true }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, chatdeletetimer, tint = Color.Yellow)
                                    Spacer(Modifier.width(4.dp))
                                    Text(chatdeletetimer)
                                }
                            },
                            onClick = { moreOptionsMenuExpanded = false; showDeleteTimerMenu = true }
                        )
                        DropdownMenuItem(
                            enabled     = isPremiumUser,              // still greyed-out for non-premium
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Timer,              // same timer glyph either way
                                    contentDescription = null,
                                    tint = if (isPremiumUser) Color(0xFFFFC107) else Color.Gray
                                )
                            },
                            text = {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(chatkeepforever)
                                    Spacer(Modifier.weight(1f))
                                    when {
                                        !isPremiumUser -> Icon(        // 🔒 for locked users
                                            Icons.Default.Lock, null, tint = Color.Gray
                                        )
                                        deleteForever  -> Icon(        // ✔ when ON
                                            Icons.Default.Check, null, tint = Color(0xFFFFC107)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                moreOptionsMenuExpanded = false          // close the menu

                                if (!isPremiumUser) return@DropdownMenuItem  // locked → ignore tap

                                /* ⟵ TOGGLE both ways */
                                deleteForever = !deleteForever
                                currentUserProfile?.userId?.let { uid ->
                                    FirebaseRefs.db.getReference("users/$uid")
                                        .child("deleteTimerOverride")
                                        .setValue(deleteForever)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Close, chatunmatch, tint = Color.Red)
                                    Spacer(Modifier.width(4.dp))
                                    Text(chatunmatch)
                                }
                            },
                            onClick = {
                                moreOptionsMenuExpanded = false
                                showUnmatchDialog = true
                            }
                        )
                        // New Report Option
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, chatreport, tint = Color.Red)
                                    Spacer(Modifier.width(4.dp))
                                    Text(chatreport)
                                }
                            },
                            onClick = {
                                moreOptionsMenuExpanded = false
                                showReportDialog = true // Trigger report dialog
                            }
                        )
                    }
                    DropdownMenu(expanded = showClearChatMenu, onDismissRequest = { showClearChatMenu = false }) {
                        DropdownMenuItem(text = { Text(chatclearonly) }, onClick = { showClearChatMenu = false; messagesRef.setValue(null); messages.clear() })
                    }
                    DropdownMenu(expanded = showDeleteTimerMenu, onDismissRequest = { showDeleteTimerMenu = false }) {
                        @Composable
                        fun item(label: String, value: Long?) = DropdownMenuItem(
                            text = { Text(label, color = if (deleteTimer == value) Color(0xFFFFA500) else Color.White) },
                            onClick = { deleteTimer = value; showDeleteTimerMenu = false }
                        )
                        item(duration1, 24L * 60 * 60 * 1000)
                        item(duration2, 7L * 24 * 60 * 60 * 1000)
                        item(duration3, 30L * 24 * 60 * 60 * 1000)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = DarkGrayBackground
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues).background(DarkGrayBackground)) {
            Column(Modifier.fillMaxSize()) {
                if (otherUserProfile != null && showRating) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        CompatibilityMeter(percent = aiMatchResult?.totalMatchPercentage?.toDouble() ?: 0.0)
                        var yourrating = stringResource(R.string.chat_your_rating)
                        val text = yourrating + "${if (yourRating >= 0) String.format("%.1f", yourRating) else "N/A"}"
                        Text(
                            text,
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        StarSelector(
                            rating = if (yourRating >= 0) yourRating.toInt() else 0,
                            onSelect = { selected ->
                                yourRating = selected.toDouble()
                                updateUserRating(ratingsRef, usersRef, otherUserId, yourRating, context)
                                if (currentUserProfile != null && otherUserProfile != null) {
                                    runAiMatchCheck(
                                        context = context,
                                        coroutineScope = scope,
                                        currentUserId = currentUserId,
                                        currentUserProfile = currentUserProfile!!,
                                        otherProfile = otherUserProfile!!
                                    ) { result -> aiMatchResult = result }
                                }
                                showRating = false          // hide once a rating is given
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                if (!deleteForever) {
                    AssistChip(
                        onClick = { showDeleteTimerMenu = true },
                        label = {
                            val txt = deleteTimer?.let { millis ->
                                when (millis) {
                                    24L * 60 * 60 * 1000 -> R.string.chat_delete_after_1d
                                    7L  * 24 * 60 * 60 * 1000 -> R.string.chat_delete_after_1w
                                    30L * 24 * 60 * 60 * 1000 -> R.string.chat_delete_after_1m
                                    else -> R.string.chat_delete_soon
                                }
                            } ?: R.string.chat_delete_disabled
                            Text(
                                text = stringResource(id = R.string.chat_messages_delete, stringResource(id = txt))
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Timer, null) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.DarkGray, labelColor = Color.White
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 4.dp)
                    )
                }

                if (privateAlbumSharedPartner) {
                    Button(
                        onClick = { navController.navigate("privateAlbum/$otherUserId") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                    ) {
                        val name = otherUserProfile?.username ?: "user"
                        Text(stringResource(R.string.view_users_private_album, name), color = Color.White)
                    }
                }

                if (isLoadingMessages) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFFA500))
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .visibleScrollbar(listState),
                        reverseLayout = true,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (isOtherUserTyping) {
                            item { TypingIndicator() }
                        }
                        items(messages.reversed()) { message ->
                            if (message.isPost) {
                                // Map Message to Post for PostBubble
                                val post = Post(
                                    postId = message.id,
                                    userId = message.senderId,
                                    contentText = message.text,
                                    mediaUrl = message.mediaUrl,
                                    mediaType = message.mediaType,
                                    timestamp = message.timestamp
                                )
                                PostBubble(
                                    post = post,
                                    currentUserId = currentUserId,
                                    onFullscreen = { p ->
                                        // Map Post back to Message for FullscreenMediaViewer
                                        fullScreenTarget = Message(
                                            id = p.postId,
                                            senderId = p.userId,
                                            receiverId = if (p.userId == currentUserId) otherUserId else currentUserId,
                                            text = p.contentText ?: "",
                                            timestamp = p.timestamp as Long,
                                            mediaType = p.mediaType,
                                            mediaUrl = p.mediaUrl,
                                            isPost = true
                                        )
                                    }
                                )
                            } else {
                                when (message.mediaType) {
                                    "voice" -> VoiceMessageBubble(message, currentUserId)
                                    "photo" -> MediaMessageBubble(
                                        message,
                                        currentUserId,
                                        onFullscreen = { fullScreenTarget = it }
                                    )
                                    "video" -> MediaMessageBubble(
                                        message,
                                        currentUserId,
                                        onFullscreen = { fullScreenTarget = it }
                                    )
                                    else -> MessageBubble(message, currentUserId)
                                }
                            }
                        }
                        compliment?.let { c ->
                            item {
                                val m = Message(
                                    id = "superswipe_${c.timestamp}",
                                    senderId = otherUserId,
                                    receiverId = currentUserId,
                                    text = c.text,
                                    timestamp = c.timestamp,
                                    mediaType = if (c.voiceUrl != null) "\uD83C\uDFA4" else null,
                                    mediaUrl = c.voiceUrl,
                                    read = true
                                )
                                MessageBubble(
                                    message = m,
                                    currentUserId = currentUserId,
                                    isSuperswipe = true
                                )
                            }
                        }
                    }
                }
                if (isRecording) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.recording_left, recordingTimeLeft / 1000),
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onToggleRecord,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFB71C1C), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop recording",
                                tint = Color.White
                            )
                        }
                    }
                }
                if (recordedVoiceUri != null) {
                    VoiceMessagePlayer(
                        mediaUrl = recordedVoiceUri.toString(),
                        isPlaying = isVoicePlaying,
                        onPlayToggle = {
                            if (isVoicePlaying) {
                                voicePlayer?.pause()
                                isVoicePlaying = false
                            } else {
                                playLocalVoice(context, recordedVoiceUri!!) { mp ->
                                    voicePlayer = mp
                                    isVoicePlaying = true
                                    mp.setOnCompletionListener {
                                        isVoicePlaying = false; voiceProgress = 0f
                                    }
                                }
                            }
                        },
                        progress = voiceProgress,
                        duration = voicePlayer?.duration?.toLong() ?: 0L
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { recordedVoiceUri = null; recordFile = null }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red)
                        }
                    }
                }
                var fullScreenLocal by remember { mutableStateOf(false) }
                var nosuitableeditor = stringResource(R.string.editor_not_found)
                var editornoapp = stringResource(R.string.editor_no_app)

                selectedMediaUri?.let { localUri ->
                    MediaPreviewBox(
                        uri = localUri,
                        mediaType = selectedMediaType,
                        onCancel = {
                            selectedMediaUri = null
                            selectedMediaType = null
                            fullScreenLocal = false
                        },
                        onFull = { fullScreenLocal = true },
                        // Edit Handler
                        onEdit = {
                            scope.launch {
                                try {
                                    val extension = if (selectedMediaType == "photo") "jpg" else "mp4"
                                    val localCopyUri = copyUriToLocalFile(context, localUri, extension).getOrElse { throw it }
                                    pendingEditUri = localCopyUri

                                    val editIntent = Intent(Intent.ACTION_EDIT).apply {
                                        setDataAndType(localCopyUri, if (selectedMediaType == "photo") "image/*" else "video/*")
                                        putExtra(MediaStore.EXTRA_OUTPUT, localCopyUri)
                                        addFlags(
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                        )
                                    }

                                    if (editIntent.resolveActivity(context.packageManager) != null) {
                                        editLauncher.launch(editIntent)
                                    } else {
                                        Toast.makeText(context, nosuitableeditor, Toast.LENGTH_SHORT).show()
                                        pendingEditUri = null
                                    }
                                } catch (e: Exception) {
                                    Log.e("ChatScreen", "Error preparing edit: ${e.message}")
                                    Toast.makeText(context, editornoapp, Toast.LENGTH_SHORT).show()
                                    pendingEditUri = null
                                }
                            }
                        },
                        refreshKey = previewRefresh
                    )
                }
                if (fullScreenLocal && selectedMediaUri != null && selectedMediaType != null) {
                    SelectedMediaFullScreen(
                        uri = selectedMediaUri!!,
                        mediaType = selectedMediaType,
                        onDismiss = { fullScreenLocal = false }
                    )
                }
                ChatInputBar(
                    messageText = messageText,
                    onTextChange = { newText ->
                        messageText = newText
                        database.getReference("typing/$chatId/$currentUserId")
                            .setValue(newText.isNotEmpty())
                    },
                    onSend = sendHandler,
                    sendEnabled = !isSendingMessage && !isUploadingMedia,
                    sending = isSendingMessage,
                    isRecording = isRecording,
                    onToggleRecord = onToggleRecord,
                    onPickPhoto = { pickPhotoLauncher.launch("image/*") },
                    onPickVideo = { pickVideoLauncher.launch("video/*") },
                    onCapturePhoto = {
                        selectedMediaType = "photo"
                        captureWithPermission(
                            context,
                            cameraPermissionLauncher,
                            ::freshPhotoUri,
                            takePhotoLauncher
                        ) { uri ->
                            pendingPhotoUri = uri
                            isUploadingMedia = true
                        }
                    },
                    onCaptureVideo = {
                        isUploadingMedia = true
                        selectedMediaType = "video"
                        // ask for CAMERA first; your launcher will then do the actual Intent
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }
            if (suggestionsExpanded) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            onClick = { suggestionsExpanded = false },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .width(400.dp)
                            .heightIn(max = 500.dp)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(2.dp, Color(0xFFFF6F00))
                    ) {
                        LazyColumn(Modifier.padding(16.dp)) {
                            if (isLoadingSuggestions) {
                                item {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFFFFA500))
                                    }
                                }
                            } else {
                                suggestions?.let { sugg ->
                                    if (sugg.topics.isNotEmpty()) {
                                        item { Text(stringResource(R.string.lbl_topics), color = Color.White, fontWeight = FontWeight.Bold) }
                                        items(sugg.topics.toMutableList()) { topic ->
                                            Row(
                                                Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(topic, color = Color.White)
                                                IconButton(onClick = {
                                                    suggestions = sugg.copy(topics = sugg.topics.filter { it != topic })
                                                }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                    if (sugg.activities.isNotEmpty()) {
                                        item { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.lbl_activities), color = Color.White, fontWeight = FontWeight.Bold) }
                                        items(sugg.activities.toMutableList()) { activity ->
                                            Row(
                                                Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "${activity.placeName} - ${activity.integration}",
                                                    color = Color.White,
                                                    modifier = Modifier.weight(1f).clickable {
                                                        messageText = activity.integration
                                                        suggestionsExpanded = false
                                                    }
                                                )
                                                IconButton(onClick = {
                                                    suggestions = sugg.copy(activities = sugg.activities.filter { it != activity })
                                                }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                    if (sugg.integrationTips.isNotEmpty()) {
                                        item { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.lbl_tips), color = Color.White, fontWeight = FontWeight.Bold) }
                                        items(sugg.integrationTips.toMutableList()) { tip ->
                                            Row(
                                                Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(tip, color = Color.White)
                                                IconButton(onClick = {
                                                    suggestions = sugg.copy(integrationTips = sugg.integrationTips.filter { it != tip })
                                                }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                    if (sugg.topics.isEmpty() && sugg.activities.isEmpty() && sugg.integrationTips.isEmpty()) {
                                        item {
                                            Text(stringResource(R.string.no_suggestions), color = Color.Gray)
                                            Spacer(Modifier.height(8.dp))
                                            Button(onClick = {
                                                scope.launch {
                                                    isLoadingSuggestions = true
                                                    suggestions = fetchSuggestionsWithRetry()
                                                    isLoadingSuggestions = false
                                                }
                                            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500))) {
                                                Text(stringResource(R.string.get_suggestions), color = Color.White)
                                            }
                                        }
                                    }
                                } ?: item {
                                    Text(stringResource(R.string.no_suggestions), color = Color.Gray)
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = {
                                        scope.launch {
                                            isLoadingSuggestions = true
                                            suggestions = fetchSuggestionsWithRetry()
                                            isLoadingSuggestions = false
                                        }
                                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500))) {
                                        Text(stringResource(R.string.get_suggestions), color = Color.White)
                                    }
                                }
                            }
                            item {
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Button(
                                        onClick = { suggestions = ChatSuggestions(emptyList(), emptyList(), emptyList()) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                    ) { Text("Clear All", color = Color.White) }
                                    Button(
                                        onClick = { suggestionsExpanded = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4500))
                                    ) { Text(stringResource(R.string.close), color = Color.White) }
                                }
                            }
                        }
                    }
                }
            }
            if (placeSuggestionsExpanded) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            onClick = { placeSuggestionsExpanded = false },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .width(400.dp)
                            .heightIn(max = 500.dp)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(2.dp, Color(0xFFFF6F00))
                    ) {
                        LazyColumn(Modifier.padding(16.dp)) {
                            if (isLoadingPlaces) {
                                item {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFFFF6F00))
                                    }
                                }
                            } else {
                                placeSuggestions?.let { places ->
                                    if (places.isNotEmpty()) {
                                        items(places.toMutableList()) { place ->
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                PlaceDetailsCard(
                                                    place,
                                                    onSend = {
                                                        val messageText = "Check out this place: ${place.placeName}. Directions: https://maps.google.com/?q=${place.latLng.latitude},${place.latLng.longitude}"
                                                        sendMessage(currentUserId, otherUserId, chatId, messageText, messagesRef)
                                                        postNotification(
                                                            notificationsRef = notificationsRef,
                                                            toUserId = otherUserId,
                                                            fromUserId = currentUserId,
                                                            fromUsername = currentUserProfile?.username ?: "",
                                                            message = messageText
                                                        )
                                                        placeSuggestionsExpanded = false
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(onClick = {
                                                    placeSuggestions = places.filter { it != place }
                                                }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    } else {
                                        item {
                                            Text(stringResource(R.string.places_none), color = Color.Gray)
                                            Spacer(Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        isLoadingPlaces = true
                                                        val sugg = fetchSuggestionsWithRetry()
                                                        placeSuggestions = sugg?.topics?.let { getPlaceSuggestions(it, otherUserProfile, context) }
                                                        isLoadingPlaces = false
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                                            ) { Text(stringResource(R.string.get_suggestions), color = Color.White) }
                                        }
                                    }
                                } ?: item {
                                    Text(stringResource(R.string.places_none), color = Color.Gray)
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isLoadingPlaces = true
                                                val sugg = fetchSuggestionsWithRetry()
                                                placeSuggestions = sugg?.topics?.let { getPlaceSuggestions(it, otherUserProfile, context) }
                                                isLoadingPlaces = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                                    ) { Text(stringResource(R.string.get_suggestions), color = Color.White) }
                                }
                            }
                            item {
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Button(
                                        onClick = { placeSuggestions = emptyList() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                    ) { Text(stringResource(R.string.clear_all), color = Color.White) }
                                    Button(
                                        onClick = { placeSuggestionsExpanded = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4500))
                                    ) { Text(stringResource(R.string.close), color = Color.White) }
                                }
                            }
                        }
                    }
                }
            }
            // Unmatch Confirmation Dialog
            if (showUnmatchDialog) {
                AlertDialog(
                    onDismissRequest = { showUnmatchDialog = false },
                    title = { Text(stringResource(R.string.chat_unmatch_title)) },
                    text = { Text(stringResource(R.string.chat_unmatch_text)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showUnmatchDialog = false
                                unmatchUser()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text(stringResource(R.string.chat_unmatch_confirm), color = Color.White)
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = { showUnmatchDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) {
                            Text(stringResource(R.string.cancel), color = Color.White)
                        }
                    }
                )
            }

            // Report Dialog
            if (showReportDialog) {
                var reportReason by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showReportDialog = false },
                    title = { Text(stringResource(R.string.chat_report_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.chat_report_prompt))
                            Spacer(Modifier.height(8.dp))
                            TextField(
                                value = reportReason,
                                onValueChange = { reportReason = it },
                                placeholder = { Text(stringResource(R.string.chat_report_placeholder)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.DarkGray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        }
                    },
                    confirmButton = {
                        val prompt = stringResource(R.string.chat_report_prompt)
                        val userreported = stringResource(R.string.chat_report_success)
                        val userreportfailed = stringResource(R.string.chat_report_failed)
                        Button(
                            // Replace your existing onClick in the Report dialog with this:
                            onClick = {
                                if (reportReason.isBlank()) {
                                    Toast.makeText(context, prompt, Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch {
                                    try {
                                        // 1️⃣ Submit the report
                                        submitReport(reportsRef, currentUserId, otherUserId, reportReason, context)
                                        // 2️⃣ Block the user
                                        blockUser(database, currentUserId, otherUserId, context)

                                        // 3️⃣ Now *inline* your unmatch logic, so it runs in this same coroutine:
                                        val chatId = getChatId(currentUserId, otherUserId)
                                        val updates = mapOf<String, Any?>(
                                            "matches/$currentUserId/$otherUserId" to null,
                                            "matches/$otherUserId/$currentUserId" to null,
                                            "messages/$chatId" to null,
                                            "typing/$chatId" to null
                                        )
                                        database.reference.updateChildren(updates).await()

                                        // 4️⃣ Clear any leftover notifications for this chat:
                                        notificationsRef.child(currentUserId)
                                            .orderByChild("senderId").equalTo(otherUserId)
                                            .get().await().children.forEach { it.ref.removeValue() }

                                        notificationsRef.child(otherUserId)
                                            .orderByChild("senderId").equalTo(currentUserId)
                                            .get().await().children.forEach { it.ref.removeValue() }

                                        // 5️⃣ Finally, update UI on the main thread:
                                        withContext(Dispatchers.Main) {
                                            showReportDialog = false
                                            messages.clear()
                                            navController.popBackStack()
                                            Toast.makeText(context, userreported, Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("ChatScreen", "Report/Unmatch failed: ${e.message}")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, userreportfailed, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text(stringResource(R.string.chat_report_submit), color = Color.White)
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = { showReportDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) {
                            Text(stringResource(R.string.cancel), color = Color.White)
                        }
                    }
                )
            }
        }
    }
    FullscreenMediaViewer(
        target = fullScreenTarget,
        onDismiss = { fullScreenTarget = null },
        messagesRef = messagesRef,
        reportsRef = reportsRef
    )
}

@Composable
fun StarSelector(
    rating: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    starColor: Color = Color(0xFFFF4500)
) {
    Row(modifier) {
        for (index in 1..5) {
            IconButton(
                onClick = { onSelect(index) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (index <= rating)
                        Icons.Filled.Star       // filled
                    else
                        Icons.Outlined.StarBorder, // empty
                    contentDescription = "$index star",
                    tint = if (index <= rating) starColor else Color.Gray
                )
            }
        }
    }
}


suspend fun askProceed(ctx: Context, msg: String): Boolean =
    suspendCancellableCoroutine { cont ->
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setMessage(msg)
            .setPositiveButton("Proceed") { _, _ -> cont.resume(true) }
            .setNegativeButton(R.string.cancel)  { _, _ -> cont.resume(false) }
            .setOnCancelListener          {        cont.resume(false) }
            .show()
    }

suspend fun copyUriToLocalFile(context: Context, uri: Uri, extension: String): Result<Uri> {
    val inputStream = try {
        context.contentResolver.openInputStream(uri)
            ?: return Result.failure(IOException("Failed to open input stream"))
    } catch (e: Exception) {
        return Result.failure(e)
    }

    val outputFile = File(context.cacheDir, "edited_${System.currentTimeMillis()}.$extension")
    val outputStream = outputFile.outputStream()

    inputStream.use { input ->
        outputStream.use { output ->
            input.copyTo(output)
        }
    }

    return Result.success(
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outputFile
        )
    )
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.typing), color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        AnimatedDots()
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
suspend fun compressVideo(
    context: Context,
    uri: Uri,
    targetBitrate: Int = 1_000_000
): File {
    val outFile = File.createTempFile("compressed_", ".mp4", context.cacheDir)

    // 2) Configure encoder settings
    val videoSettings = VideoEncoderSettings.Builder()
        .setBitrate(targetBitrate)
        .build()
    val encoderFactory = DefaultEncoderFactory.Builder(context)
        .setRequestedVideoEncoderSettings(videoSettings)
        .build()

    // 3) Build the transformer
    val transformer = Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
        .setEncoderFactory(encoderFactory)
        .build()

    try {
        suspendCancellableCoroutine<Unit> { cont ->
            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    cont.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    cont.resumeWithException(exportException)
                }
            })

            cont.invokeOnCancellation {
                transformer.cancel()
                outFile.delete()
            }
            Handler(Looper.getMainLooper()).post {
                transformer.start(
                    MediaItem.fromUri(uri),
                    outFile.absolutePath
                )
            }
        }
    } catch (throwable: Throwable) {
        outFile.delete()
        throw throwable
    }

    return outFile
}

@Composable
fun AnimatedDots() {
    var dotCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            dotCount = (dotCount + 1) % 4
            delay(300L)
        }
    }
    Text(
        ".".repeat(dotCount),
        color = Color.Gray,
        fontSize = 12.sp
    )
}

@Composable
fun CachedPhotoThumbnail(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderResId: Int? = null,
    errorResId: Int? = null
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(url)
            .diskCacheKey(url)
            .memoryCacheKey(url)
            .crossfade(true)
            .apply {
                placeholderResId?.let { placeholder(it) }
                errorResId?.let { error(it) }
            }
            .build()
    )
    Box(
        modifier = modifier
            .border(BorderStroke(2.dp, Color(0xFFFF6F00)), RoundedCornerShape(4.dp))
    ) {
        Image(
            painter = painter,
            contentDescription = "Photo Thumbnail",
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize()
        )
        if (painter.state is AsyncImagePainter.State.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
fun CachedVideoThumbnail(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderResId: Int? = null,
    errorResId: Int? = null,
    frameMillis: Long = 1_000L
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(url)
            .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
            .videoFrameMillis(frameMillis)
            .crossfade(true)
            .apply {
                placeholderResId?.let(::placeholder)
                errorResId?.let(::error)
            }
            .build()
    )
    Box(
        modifier = modifier
            .border(BorderStroke(2.dp, Color(0xFFFF6F00)), RoundedCornerShape(4.dp))
    ) {
        Image(
            painter,
            contentDescription = "Video thumbnail",
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize()
        )
        if (painter.state is AsyncImagePainter.State.Loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

fun freshPhotoUri(context: Context): Uri {
    val photoFile = createTempFile(context, ".jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
}

fun freshVideoUri(context: Context): Uri {
    val videoFile = createTempFile(context, ".mp4")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)
}

fun createTempFile(context: Context, extension: String): File {
    val dir = File(context.cacheDir, "media")
    if (!dir.exists()) dir.mkdirs()
    return File.createTempFile("media_${System.currentTimeMillis()}", extension, dir)
}

@androidx.annotation.OptIn(UnstableApi::class)
suspend fun sendMediaMessage(
    currentUserId: String,
    otherUserId: String,
    chatId: String,
    uri: Uri,
    mediaType: String,
    messagesRef: DatabaseReference,
    context: Context
) {
    val ts = System.currentTimeMillis()
    val storageRef = FirebaseStorage.getInstance().reference
    val ext = if (mediaType == "photo") "jpg" else "mp4"
    val remoteName = "${mediaType}_${ts}.$ext"
    val mediaRef = storageRef.child("$mediaType/$chatId/$remoteName")
    var tempFile: File? = null
    val bytes = try {
        when (mediaType) {
            "photo" -> compressImage(context, uri)
            "video" -> {
                tempFile = compressVideo(context, uri)
                null
            }
            else -> null
        }
    } catch (e: Exception) {
        Log.e("ChatScreen", "Compression failed: ${e.message}")
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to compress $mediaType", Toast.LENGTH_SHORT).show()
        }
        return
    }
    try {
        when {
            bytes != null -> mediaRef.putBytes(bytes).await()
            tempFile != null -> mediaRef.putFile(Uri.fromFile(tempFile)).await()
            else -> mediaRef.putFile(uri).await()
        }
        val downloadUrl = mediaRef.downloadUrl.await().toString()
        val id = messagesRef.push().key ?: return
        val msg = Message(
            id = id,
            senderId = currentUserId,
            receiverId = otherUserId,
            text = "",
            timestamp = ts,
            read = false,
            mediaType = mediaType,
            mediaUrl = downloadUrl,
            processed = false
        )
        messagesRef.child(id).setValue(msg)
    } catch (e: Exception) {
        Log.e("ChatScreen", "Upload failed: ${e.message}")
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to upload $mediaType", Toast.LENGTH_SHORT).show()
        }
    } finally {
        tempFile?.delete()
    }
}

@Composable
fun FullscreenMediaViewer(
    target: Message?,
    onDismiss: () -> Unit,
    messagesRef: DatabaseReference,
    reportsRef: DatabaseReference
) {
    val failedtoloadphoto = stringResource(R.string.photo_load_failed)
    val context = LocalContext.current
    if (target == null) return
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkGrayBackground)
        ) {
            if (target.mediaType == "photo") {
                val photoRequest = ImageRequest.Builder(context)
                    .data(target.mediaUrl)
                    .diskCacheKey(target.mediaUrl)
                    .memoryCacheKey(target.mediaUrl)
                    .crossfade(true)
                    .build()
                AsyncImage(
                    model = photoRequest,
                    contentDescription = "Full Screen Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onError = {
                        Toast.makeText(context, failedtoloadphoto, Toast.LENGTH_SHORT).show()
                    }
                )
            } else if (target.mediaType == "video") {
                FullscreenVideoPlayer(uri = Uri.parse(target.mediaUrl), onDismiss = onDismiss)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun FullscreenVideoPlayer(uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var videoLoading by remember { mutableStateOf(true) }
    val simpleCache = remember { VideoCacheProvider.getInstance(context) }
    val cacheDataSourceFactory = remember {
        val upstreamFactory = DefaultDataSource.Factory(context)
        CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
    }
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
    }
    val playerView = remember {
        PlayerView(context).apply {
            useController = true
        }
    }
    LaunchedEffect(uri) {
        videoLoading = true
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> exoPlayer.play()
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                else -> Unit
            }
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    videoLoading = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerView.player = null
            exoPlayer.release()
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkGrayBackground)
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { playerView },
                update = { it.player = exoPlayer },
                modifier = Modifier.fillMaxSize()
            )
            if (videoLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
fun MediaMessageBubble(
    message: Message,
    currentUserId: String,
    onFullscreen: (Message) -> Unit
) {
    val isCurrentUser = message.senderId == currentUserId
    val ticks = if (isCurrentUser) {
        if (message.read) "✔✔" else "✔"
    } else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Box(modifier = Modifier.clickable { onFullscreen(message) }) {
                when (message.mediaType) {
                    "photo" -> CachedPhotoThumbnail(
                        url = message.mediaUrl ?: "",
                        modifier = Modifier.size(150.dp),
                        contentScale = ContentScale.Crop,
                        placeholderResId = R.drawable.local_placeholder,
                        errorResId = R.drawable.local_placeholder
                    )
                    "video" -> CachedVideoThumbnail(
                        url = message.mediaUrl ?: "",
                        modifier = Modifier.size(150.dp),
                        contentScale = ContentScale.Crop,
                        placeholderResId = R.drawable.local_placeholder,
                        errorResId = R.drawable.local_placeholder
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatRelativeTime(message.timestamp), color = Color.DarkGray, fontSize = 12.sp)
                if (ticks.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(ticks, color = Color(0xFFFF4500), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, currentUserId: String, isSuperswipe: Boolean = false) {
    val isCurrentUser = message.senderId == currentUserId
    val bubbleColor = when {
        isSuperswipe -> Color(0xFFE91E63)
        message.senderId == currentUserId -> Color(0xFFFFDB00)
        else -> Color(0xFFFF6F00)
    }
    val textColor = if (isCurrentUser) Color.Black else Color.White
    val context = LocalContext.current
    val annotatedText = remember(message.text) { createAnnotatedString(message.text) }
    val ticks = if (isCurrentUser) {
        if (message.read) "✔✔" else "✔"
    } else ""
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.background(bubbleColor, RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            if (isSuperswipe) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEmotions, contentDescription = "SuperSwipe", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Super-Swipe!", color = Color.White, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
            }
            ClickableText(
                text = annotatedText,
                style = TextStyle(color = textColor, fontSize = 16.sp),
                onClick = { offset ->
                    annotatedText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                            context.startActivity(intent)
                        }
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text(text = formatRelativeTime(message.timestamp), color = Color.DarkGray, fontSize = 12.sp)
                if (ticks.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(ticks, color = Color(0xFFFF4500), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun VoiceMessageBubble(message: Message, currentUserId: String) {
    val context = LocalContext.current
    val isCurrentUser = message.senderId == currentUserId
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var player: MediaPlayer? by remember { mutableStateOf(null) }
    LaunchedEffect(isPlaying) {
        if (isPlaying && player != null) {
            while (isPlaying && player?.isPlaying == true) {
                delay(500L)
                val current = player?.currentPosition ?: 0
                val duration = player?.duration ?: 1
                progress = current.toFloat() / duration.toFloat()
            }
        } else progress = 0f
    }
    val ticks = if (isCurrentUser) if (message.read) "✔✔" else "✔" else ""
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start) {
        Column(Modifier.background(Color.Black, RoundedCornerShape(12.dp)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (isPlaying) {
                        player?.pause()
                        isPlaying = false
                    } else {
                        val mp = MediaPlayer().apply {
                            setDataSource(message.mediaUrl)
                            prepareAsync()
                            setOnPreparedListener { start(); player = this; isPlaying = true }
                            setOnCompletionListener { isPlaying = false; progress = 0f }
                            setOnErrorListener { _, what, extra ->
                                Toast.makeText(context, "Playback error: $what, $extra", Toast.LENGTH_SHORT).show(); false
                            }
                        }
                    }
                }) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color(0xFFFF4500))
                }
                LinearProgressIndicator(progress = progress, Modifier.weight(1f).padding(horizontal = 8.dp), color = Color(0xFFFFA500), trackColor = Color.Gray)
                Text(formatDuration(player?.duration?.toLong() ?: 0L), color = Color.DarkGray, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatRelativeTime(message.timestamp), color = Color.DarkGray, fontSize = 12.sp)
                if (ticks.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(ticks, color = Color(0xFFFF4500), fontSize = 12.sp)
                }
            }
        }
    }
}

fun createAnnotatedString(text: String): AnnotatedString {
    val regex = Regex("((http|https)://[\\w-]+(\\.[\\w-]+)+([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])?)")
    val builder = AnnotatedString.Builder(text)
    regex.findAll(text).forEach { result ->
        val start = result.range.first
        val end = result.range.last + 1
        builder.addStyle(style = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline), start = start, end = end)
        builder.addStringAnnotation(tag = "URL", annotation = result.value, start = start, end = end)
    }
    return builder.toAnnotatedString()
}

@Composable
fun VoiceMessagePlayer(mediaUrl: String, isPlaying: Boolean, onPlayToggle: () -> Unit, progress: Float, duration: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPlayToggle) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color(0xFFFF4500))
        }
        LinearProgressIndicator(progress = progress, Modifier.weight(1f).padding(horizontal = 8.dp), color = Color(0xFFFFA500), trackColor = Color.Gray)
        Text(formatDuration(duration), color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

fun fetchUserRating(ratingsRef: DatabaseReference, userId: String, onRatingFetched: (Double) -> Unit) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    ratingsRef.child(userId).child("ratings").child(currentUserId).get()
        .addOnSuccessListener { snapshot -> onRatingFetched(snapshot.getValue(Double::class.java) ?: 0.0) }
        .addOnFailureListener { onRatingFetched(0.0) }
}

fun fetchAverageRating(ratingsRef: DatabaseReference, userId: String, onAverageFetched: (Double) -> Unit) {
    ratingsRef.child(userId).child("averageRating").get()
        .addOnSuccessListener { snapshot -> onAverageFetched(snapshot.getValue(Double::class.java) ?: 0.0) }
        .addOnFailureListener { onAverageFetched(0.0) }
}

fun updateUserRating(ratingsRef: DatabaseReference, usersRef: DatabaseReference, userId: String, rating: Double, context: Context) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRatingRef = ratingsRef.child(userId)
    userRatingRef.get().addOnSuccessListener { snapshot ->
        val ratingsMap = if (snapshot.exists() && snapshot.child("ratings").value is Map<*, *>) {
            snapshot.child("ratings").getValue(object : GenericTypeIndicator<MutableMap<String, Double>>() {}) ?: mutableMapOf()
        } else mutableMapOf()
        val isNewRating = !ratingsMap.containsKey(currentUserId)
        ratingsMap[currentUserId] = rating
        val averageRating = if (ratingsMap.isNotEmpty()) ratingsMap.values.average() else 0.0
        val updates = mapOf("ratings" to ratingsMap, "averageRating" to averageRating)
        userRatingRef.updateChildren(updates).addOnSuccessListener {
            usersRef.child(userId).child("averageRating").setValue(averageRating).addOnSuccessListener {
                Toast.makeText(context, R.string.rating_updated, Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { Toast.makeText(context, R.string.rating_update_failed, Toast.LENGTH_SHORT).show() }
            if (isNewRating) {
                usersRef.child(userId).child("numberOfRatings").runTransaction(object : Transaction.Handler {
                    override fun doTransaction(mutableData: MutableData): Transaction.Result {
                        val currentCount = mutableData.getValue(Int::class.java) ?: 0
                        mutableData.value = currentCount + 1
                        return Transaction.success(mutableData)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                        if (!committed) Log.e("Rating", "Failed to increment numberOfRatings: ${error?.message}")
                    }
                })
            }
        }.addOnFailureListener { Toast.makeText(context,  R.string.rating_avg_update_failed, Toast.LENGTH_SHORT).show() }
    }.addOnFailureListener { Toast.makeText(context,  R.string.rating_fetch_failed, Toast.LENGTH_SHORT).show() }
}

fun getChatId(userId1: String, userId2: String): String = if (userId1 < userId2) "${userId1}_$userId2" else "${userId2}_$userId1"

fun postNotification(
    notificationsRef: DatabaseReference,
    toUserId: String,
    fromUserId: String,
    fromUsername: String,
    message: String
) {
    val notificationId = notificationsRef.child(toUserId).push().key ?: return
    val noti = Notification(
        id = notificationId,
        type = "chat_message",
        senderId = fromUserId,
        senderUsername = fromUsername,
        message = message,
        timestamp = System.currentTimeMillis(),
        isRead = "false"
    )
    notificationsRef.child(toUserId).child(notificationId).setValue(noti)
        .addOnSuccessListener { Log.d("Notifications", "Notification posted: $message") }
        .addOnFailureListener { Log.e("Notifications", "Failed to post notification: ${it.message}") }
}

fun sendMessage(currentUserId: String, otherUserId: String, chatId: String, messageText: String, messagesRef: DatabaseReference) {
    val timestamp = System.currentTimeMillis()
    val messageId = messagesRef.push().key ?: return
    val message = Message(
        id = messageId,
        senderId = currentUserId,
        receiverId = otherUserId,
        text = messageText,
        timestamp = timestamp,
        read = false,
        processed = false
    )
    messagesRef.child(messageId).setValue(message)
}

fun sendVoiceMessage(currentUserId: String, otherUserId: String, chatId: String, uri: Uri, messagesRef: DatabaseReference, context: Context) {
    val timestamp = System.currentTimeMillis()
    val storageRef = FirebaseStorage.getInstance().reference
    val fileName = "voice_message_$timestamp.aac"
    val voiceRef = storageRef.child("voice_messages/$chatId/$fileName")
    voiceRef.putFile(uri).addOnSuccessListener {
        voiceRef.downloadUrl.addOnSuccessListener { downloadUrl ->
            val messageId = messagesRef.push().key ?: return@addOnSuccessListener
            val message = Message(
                id = messageId,
                senderId = currentUserId,
                receiverId = otherUserId,
                text = "",
                timestamp = timestamp,
                read = false,
                mediaType = "voice",
                mediaUrl = downloadUrl.toString(),
                processed = false
            )
            messagesRef.child(messageId).setValue(message)
        }.addOnFailureListener { Toast.makeText(context, "Failed to get voice URL.", Toast.LENGTH_SHORT).show() }
    }.addOnFailureListener { Toast.makeText(context, "Failed to upload voice.", Toast.LENGTH_SHORT).show() }
}

@Composable
fun PlaceDetailsCard(place: PlaceDetails, onSend: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onSend() },
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(place.placeName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            place.address?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = Color.Gray, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    val gmmIntentUri = Uri.parse("google.navigation:q=${place.latLng.latitude},${place.latLng.longitude}")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply { setPackage("com.google.android.apps.maps") }
                    context.startActivity(mapIntent)
                }) { Text(stringResource(R.string.directions), color = Color(0xFFFFA500)) }
                Text(stringResource(R.string.tap_to_send), color = Color(0xFFFF4500), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AIOrProfileImage(profile: Profile, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val placeholder = painterResource(R.drawable.local_placeholder)
    val url = profile.profilepicThumbnailUrl ?: profile.profilepicUrl
    url?.takeIf { it.isNotBlank() }?.let {
        val pathKey = Uri.parse(it).path
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(it)
                .diskCacheKey(pathKey)
                .memoryCacheKey(pathKey)
                .crossfade(true)
                .build(),
            contentDescription = profile.username,
            placeholder = placeholder,
            error = placeholder,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } ?: Image(
        painter = placeholder,
        contentDescription = profile.username,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

@Composable
fun MediaPreviewBox(
    uri: Uri,
    mediaType: String?,
    onCancel: () -> Unit,
    onFull: () -> Unit,
    onEdit: () -> Unit,
    refreshKey: Int
) {
    Column(
        Modifier
            .padding(8.dp)
            .border(BorderStroke(1.dp, Color(0xFFFF6F00)), RoundedCornerShape(8.dp))
    ) {
        Box(
            Modifier
                .height(200.dp)
                .fillMaxWidth()
                .clickable { onFull() }
        ) {
            key(refreshKey) {
                if (mediaType == "video") {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(uri)
                                setOnPreparedListener { it.isLooping = false; seekTo(1) }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            AppCompatImageView(ctx).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            view.context.contentResolver.openInputStream(uri)?.use { stream ->
                                val bmp = BitmapFactory.decodeStream(stream)
                                view.setImageBitmap(bmp)
                            }
                        }
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onEdit) { Text("✏️ " + stringResource(R.string.edit), color = Color.White, fontSize = 12.sp) }
            TextButton(onClick = onCancel) {
                Text("❌ " + stringResource(R.string.cancel), color = Color.Red, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun MediaToolsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isRecording: Boolean,
    onToggleRecord: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit,
    onCapturePhoto: () -> Unit,
    onCaptureVideo: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null
                )
            },
            text = { Text(if (isRecording) stringResource(R.string.btn_stop_recording) else stringResource(R.string.btn_record_voice)) },
            onClick = { onDismiss(); onToggleRecord() }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Photo, null) },
            text = { Text(stringResource(R.string.btn_pick_photo)) },
            onClick = { onDismiss(); onPickPhoto() }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.VideoLibrary, null) },
            text = { Text(stringResource(R.string.btn_pick_video)) },
            onClick = { onDismiss(); onPickVideo() }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.CameraAlt, null) },
            text = { Text(stringResource(R.string.btn_capture_photo)) },
            onClick = { onDismiss(); onCapturePhoto() }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Videocam, null) },
            text = { Text(stringResource(R.string.btn_capture_video)) },
            onClick = { onDismiss(); onCaptureVideo() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    messageText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    sendEnabled: Boolean,
    sending: Boolean,
    isRecording: Boolean,
    onToggleRecord: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit,
    onCapturePhoto: () -> Unit,
    onCaptureVideo: () -> Unit
) {
    var mediaMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        TextField(
            value = messageText,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(R.string.hint_type_message), color = Color.White) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .background(Color.DarkGray, RoundedCornerShape(24.dp)),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.DarkGray,
                focusedTextColor = Color.White,
                focusedPlaceholderColor = Color.Gray,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor             = KupidxOrange
            ),
//            singleLine = true,

            keyboardActions = KeyboardActions(
                onSend = {
                    if (sendEnabled) onSend()
                }
            )
        )

        Spacer(Modifier.width(4.dp))

        IconButton(onClick = { mediaMenu = true }) {
            Icon(Icons.Default.MoreVert, null, tint = Color(0xFFFFA500))
        }
        MediaToolsMenu(
            expanded = mediaMenu,
            onDismiss = { mediaMenu = false },
            isRecording = isRecording,
            onToggleRecord = onToggleRecord,
            onPickPhoto = onPickPhoto,
            onPickVideo = onPickVideo,
            onCapturePhoto = onCapturePhoto,
            onCaptureVideo = onCaptureVideo
        )

        Spacer(Modifier.width(4.dp))

        IconButton(
            enabled = sendEnabled,
            onClick = onSend,
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFFFF4500), CircleShape)
        ) {
            if (sending) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Icon(Icons.Default.Send, null, tint = Color.White)
            }
        }
    }
}

fun captureWithPermission(
    context: Context,
    permissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    uriProvider: (Context) -> Uri,
    captureLauncher: ManagedActivityResultLauncher<Uri, Boolean>,
    onUriReady: (Uri) -> Unit
) {
    if (context.checkSelfPermission(Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED) {
        val uri = uriProvider(context)
        onUriReady(uri)
        captureLauncher.launch(uri)
    } else {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun SelectedMediaFullScreen(
    uri: Uri,
    mediaType: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var videoReady by remember { mutableStateOf(mediaType != "video") }   // photo → ready instantly

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxSize().background(DarkGrayBackground)) {

            when (mediaType) {
                "photo" ->                               /* show local photo quickly with ImageView */
                    AndroidView(
                        factory = { ctx ->
                            androidx.appcompat.widget.AppCompatImageView(ctx).apply {
                                setImageURI(uri)
                                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                "video" -> {                             /* use ExoPlayer so it *always* plays */
                    CachedFullscreenVideoPlayer(
                        uri       = uri,
                        onDismiss = onDismiss
                    )
                }
            }

            if (!videoReady) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) { Icon(Icons.Default.Close, null, tint = Color.White) }
        }
    }
}

@Composable
fun PostBubble(
    post: Post,
    currentUserId: String,
    onFullscreen: (Post) -> Unit
) {
    val isCurrentUser = post.userId == currentUserId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black, RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFFFFA500), RoundedCornerShape(12.dp)) // Orange border for shared posts
                .padding(12.dp)
        ) {
            // Indicate shared post
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = "Shared Post", tint = Color(0xFFFFA500), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Shared Post", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            // Render content based on mediaType
            when (post.mediaType) {
                "voice" -> {
                    // Map Post to Message for VoiceMessageBubble
                    val message = Message(
                        id = post.postId,
                        senderId = post.userId,
                        receiverId = if (isCurrentUser) "" else currentUserId,
                        text = "",
                        timestamp = post.timestamp as Long,
                        mediaType = "voice",
                        mediaUrl = post.mediaUrl
                    )
                    VoiceMessageBubble(message, currentUserId)
                }
                "photo", "video" -> {
                    // Map Post to Message for MediaMessageBubble
                    val message = Message(
                        id = post.postId,
                        senderId = post.userId,
                        receiverId = if (isCurrentUser) "" else currentUserId,
                        text = post.contentText ?: "",
                        timestamp = post.timestamp as Long,
                        mediaType = post.mediaType,
                        mediaUrl = post.mediaUrl
                    )
                    MediaMessageBubble(
                        message = message,
                        currentUserId = currentUserId,
                        onFullscreen = { onFullscreen(post) }
                    )
                }
                else -> {
                    // Map Post to Message for MessageBubble
                    val message = Message(
                        id = post.postId,
                        senderId = post.userId,
                        receiverId = if (isCurrentUser) "" else currentUserId,
                        text = post.contentText ?: "",
                        timestamp = post.timestamp as Long
                    )
                    MessageBubble(message, currentUserId)
                }
            }
        }
    }
}


// Submit a report to Firebase
private suspend fun submitReport(
    reportsRef: DatabaseReference,
    reporterId: String,
    reportedId: String,
    reason: String,
    context: Context
) {
    val reportId = reportsRef.push().key ?: return
    val report = mapOf(
        "reporterId" to reporterId,
        "reportedId" to reportedId,
        "reason" to reason,
        "timestamp" to System.currentTimeMillis(),
        "status" to "pending"
    )
    reportsRef.child(reportId).setValue(report).await()
}

// Block a user by adding them to the blocker's block list
private suspend fun blockUser(
    database: FirebaseDatabase,
    blockerId: String,
    blockedId: String,
    context: Context
) {
    val blockRef = database.getReference("blocks/$blockerId/$blockedId")
    blockRef.setValue(true).await()
}

/* ------------ open the best editor the device offers (photo & video) ------------- */
fun launchMediaEditor(context: Context, uri: Uri, mediaType: String?) {

    /* we’ll try several well-known intents until one works */
    val candidates = buildList {
        // Google / AOSP photo-crop-edit
        if (mediaType == "photo") add(Intent(Intent.ACTION_EDIT)
            .setDataAndType(uri, "image/*")
            .putExtra(Intent.EXTRA_TITLE, "Edit photo"))

        // generic ACTION_EDIT (many gallery apps register for it)
        add(Intent(Intent.ACTION_EDIT)
            .setDataAndType(uri, if (mediaType == "photo") "image/*" else "video/*"))

        // Samsung / Google Photos video-trim
        if (mediaType == "video") add(Intent("com.android.gallery3d.action.TRIM")
            .setDataAndType(uri, "video/*"))
    }

    /* grant temporary read + write access so the external editor can touch the file */
    candidates.forEach {
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    /* pick the first intent that has a resolver */
    val target = candidates.firstOrNull { it.resolveActivity(context.packageManager) != null }

    if (target != null) {
        context.startActivity(target)
    } else {
        Toast.makeText(context,  R.string.editor_not_found, Toast.LENGTH_SHORT).show()
    }
}

fun buildEditIntent(uri: Uri, mediaType: String): Intent {
    return Intent(Intent.ACTION_EDIT).apply {
        setDataAndType(uri, if (mediaType == "photo") "image/*" else "video/*")
        putExtra(Intent.EXTRA_TITLE, "Edit media")
        putExtra(MediaStore.EXTRA_OUTPUT, uri)                       // ← tell it “write here”
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }
}
