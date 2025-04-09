package com.am24.am24

import ChatRequest
import ChatResponse
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.navigation.NavController
import coil.imageLoader
import coil.request.ImageRequest
import com.google.gson.JsonSyntaxException
import java.io.File
import android.os.Environment
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import java.io.IOException
import androidx.compose.runtime.setValue
import android.app.DownloadManager

// Data classes
data class MoodLevels(val trust: Int = 0, val jealousy: Int = 0, val romantic_passion: Int = 0, val satisfaction: Int = 0)
data class RelationshipHistory(val attachment: Int = 50, val confidence: Int = 50, val emotionalDepth: Int = 50)
data class ModelingState(val relationshipHistory: RelationshipHistory = RelationshipHistory(), val moodLevels: MoodLevels = MoodLevels(), val relationshipStage: String = "Friend")
data class EmotionDeltas(val trustDelta: Int = 0, val jealousyDelta: Int = 0, val romanticPassionDelta: Int = 0, val satisfactionDelta: Int = 0)
data class MessageImpact(val impactScore: Int, val emotionDeltas: EmotionDeltas, val snippetToStore: String = "", val explanation: String = "")

// Updated Message data class to remove 'viewed' field
data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false,
    val mediaType: String? = null, // Supports "photo" or "voice" only
    val mediaUrl: String? = null,
    val processed: Boolean = false,
)

// ChatAIViewModel
class ChatAIViewModel : ViewModel() {
    private val database = FirebaseDatabase.getInstance()
    private val gson = Gson()
    private val aiStates = mutableMapOf<String, ModelingState>()
    val memoryLogs = mutableMapOf<String, MutableList<String>>()
    private val messageCounts = mutableMapOf<String, Int>()

    private val statesRef = database.getReference("aiStates")
    val memoryRef = database.getReference("aiMemoryLogs")
    private val messageCountRef = database.getReference("aiMessageCounts")

    init {
        listOf("zaraAi", "kabirAi").forEach { aiId ->
            statesRef.child(aiId).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val stateJson = snapshot.getValue(String::class.java)
                    aiStates[aiId] = stateJson?.let { gson.fromJson(it, ModelingState::class.java) } ?: ModelingState()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
            memoryRef.child(aiId).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val logs = snapshot.children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
                    memoryLogs[aiId] = logs
                }
                override fun onCancelled(error: DatabaseError) {}
            })
            messageCountRef.child(aiId).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    messageCounts[aiId] = snapshot.getValue(Int::class.java) ?: 0
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    fun getModelingState(aiId: String): ModelingState = aiStates[aiId] ?: ModelingState()
    fun getMemoryLog(aiId: String): List<String> = memoryLogs[aiId] ?: mutableListOf()
    fun getMessageCount(aiId: String): Int = messageCounts[aiId] ?: 0

    private fun convertMessages(msgs: List<Message>): List<ChatMessage> {
        return msgs.map { ChatMessage(it.senderId, it.text, it.timestamp) }
    }

    suspend fun classifyMessageImpact(aiId: String, userText: String, recentMessages: List<Message>): MessageImpact {
        val state = getModelingState(aiId)
        val conv = convertMessages(recentMessages).takeLast(7)
        val mem = getMemoryLog(aiId).joinToString(" | ")
        val systemPrompt = """
You are an "Impact Classifier" for interactive conversations.

Given the AI character's current emotional state:
  - Trust: ${state.moodLevels.trust}
  - Jealousy: ${state.moodLevels.jealousy}
  - Romantic Passion: ${state.moodLevels.romantic_passion}
  - Satisfaction: ${state.moodLevels.satisfaction}

Memory log: [$mem]
LAST 7 MSGS: ${conv.joinToString(" | ") { "${it.role}: ${it.content}" }}
NEW MSG: $userText

Assign an "impactScore" (0–100) and distribute this impact across emotional deltas.
Only store messages with impactScore ≥ 50.
Return exactly this JSON:
{
  "impactScore": Int,
  "emotionDeltas": {
    "trustDelta": Int,
    "jealousyDelta": Int,
    "romanticPassionDelta": Int,
    "satisfactionDelta": Int
  },
  "snippetToStore": "<exact user message>",
  "explanation": "<brief explanation>"
}
""".trimIndent()

        val raw = callClassifierApi(listOf(ChatMessage("system", systemPrompt)))
        return raw?.let {
            try {
                val jsonPart = it.substring(it.indexOf('{'), it.lastIndexOf('}') + 1)
                gson.fromJson(jsonPart, MessageImpact::class.java)
            } catch (e: Exception) {
                MessageImpact(0, EmotionDeltas())
            }
        } ?: MessageImpact(0, EmotionDeltas())
    }

    private fun computeRelationshipStage(points: Int): String {
        return when {
            points < 50 -> "Friend"
            points < 100 -> "Casual Flirt"
            points < 200 -> "Romantic Partner"
            points < 400 -> "Spouse"
            else -> "Soulmate+"
        }
    }

    private fun applyMessageImpact(aiId: String, impact: MessageImpact) {
        val oldState = getModelingState(aiId)
        if (impact.impactScore >= 50) {
            memoryLogs.getOrPut(aiId) { mutableListOf() }.add(impact.snippetToStore)
            memoryRef.child(aiId).setValue(memoryLogs[aiId])
        }
        val updatedMood = oldState.moodLevels.copy(
            trust = (oldState.moodLevels.trust + impact.emotionDeltas.trustDelta).coerceIn(0, 100),
            jealousy = (oldState.moodLevels.jealousy + impact.emotionDeltas.jealousyDelta).coerceIn(0, 100),
            romantic_passion = (oldState.moodLevels.romantic_passion + impact.emotionDeltas.romanticPassionDelta).coerceIn(0, 100),
            satisfaction = (oldState.moodLevels.satisfaction + impact.emotionDeltas.satisfactionDelta).coerceIn(0, 100)
        )
        val attachmentDelta = (impact.emotionDeltas.trustDelta - impact.emotionDeltas.jealousyDelta) / 2
        val confidenceDelta = (impact.emotionDeltas.trustDelta + impact.emotionDeltas.satisfactionDelta) / 2
        val emotionalDepthDelta = (impact.emotionDeltas.romanticPassionDelta + impact.emotionDeltas.satisfactionDelta) / 2

        val updatedHistory = oldState.relationshipHistory.copy(
            attachment = (oldState.relationshipHistory.attachment + attachmentDelta).coerceIn(0, 100),
            confidence = (oldState.relationshipHistory.confidence + confidenceDelta).coerceIn(0, 100),
            emotionalDepth = (oldState.relationshipHistory.emotionalDepth + emotionalDepthDelta).coerceIn(0, 100)
        )
        val currentPoints = (updatedHistory.attachment + updatedHistory.confidence + updatedHistory.emotionalDepth) / 3
        val pointsEarned = impact.impactScore / 10
        val newPoints = currentPoints + pointsEarned
        val newStage = computeRelationshipStage(newPoints)
        val newState = oldState.copy(moodLevels = updatedMood, relationshipHistory = updatedHistory, relationshipStage = newStage)
        aiStates[aiId] = newState
        statesRef.child(aiId).setValue(gson.toJson(newState))
    }

    private fun buildMasterPrompt(aiId: String, state: ModelingState, userName: String): ChatMessage {
        val mem = getMemoryLog(aiId).joinToString(" | ")
        val currentPoints = (state.relationshipHistory.attachment + state.relationshipHistory.confidence + state.relationshipHistory.emotionalDepth) / 3
        val nickname = getDynamicNickname(userName, currentPoints)
        val biography = getPersonaBiography(aiId)
        val promptText = """
You are ${getPersonaName(aiId)}.
User Profile: $userName
Your Biography: $biography

Memory Log: [$mem]

Your current state:
$state

Use the nickname "$nickname" when addressing the user.
Respond with a message that reflects your personality, current mood, and relationship stage.
""".trimIndent()
        return ChatMessage("system", promptText)
    }

    private fun getPersonaName(aiId: String): String = when (aiId) { "zaraAi" -> "Zara"; "kabirAi" -> "Kabir"; else -> "AI" }
    private fun getDynamicNickname(userName: String, points: Int): String = when {
        points < 50 -> userName.split(" ").firstOrNull() ?: userName
        points < 100 -> "babe"
        points < 200 -> "darling"
        points < 400 -> "love"
        else -> "my love"
    }
    private fun getPersonaBiography(aiId: String): String = when (aiId) { "zaraAi" -> "Cricket Diva with passion and grace."; "kabirAi" -> "Bad Boy Cop with a rebellious charm."; else -> "A unique personality." }

    private suspend fun generateAIResponse(aiId: String, userInput: String, recentMessages: List<Message>, userName: String): String {
        val state = getModelingState(aiId)
        val masterPrompt = buildMasterPrompt(aiId, state, userName)
        val messagesForResponse = listOf(masterPrompt, ChatMessage("user", userInput))
        val raw = callKupidXApi(messagesForResponse)
        return raw?.trim()?.removePrefix("```json")?.removeSuffix("```")?.trim() ?: "Hello! How can I assist you today?"
    }

    fun sendMessageToAI(
        aiId: String,
        userInput: String,
        currentUserId: String,
        messagesRef: DatabaseReference,
        context: android.content.Context,
        recentMessages: List<Message>,
        userName: String
    ) {
        if (userInput.isBlank()) return

        val userMsg = Message(id = messagesRef.push().key ?: return, senderId = currentUserId, receiverId = aiId, text = userInput, timestamp = System.currentTimeMillis(), read = false, processed = false)
        messagesRef.child(userMsg.id).setValue(userMsg)
        incrementMessageCount(aiId)

        viewModelScope.launch {
            val impact = classifyMessageImpact(aiId, userInput, recentMessages)
            applyMessageImpact(aiId, impact)
            val responseText = generateAIResponse(aiId, userInput, recentMessages, userName)
            val aiMsg = Message(id = messagesRef.push().key ?: return@launch, senderId = aiId, receiverId = currentUserId, text = responseText, timestamp = System.currentTimeMillis(), read = false, processed = false)
            messagesRef.child(aiMsg.id).setValue(aiMsg)
        }
    }

    private fun incrementMessageCount(aiId: String) {
        val currentCount = messageCounts[aiId] ?: 0
        messageCounts[aiId] = currentCount + 1
        messageCountRef.child(aiId).setValue(currentCount + 1)
    }

    suspend fun callKupidXApi(messages: List<ChatMessage>): String? = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().connectTimeout(120, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()
        val railwayUrl = "https://am24.org/openai/chat"
        val chatRequest = ChatRequest(model = "llama-3.3-70b-versatile", messages = messages, max_tokens = 8000)
        val jsonBody = gson.toJson(chatRequest)
        Log.d("FinalRequest", "Sending final request: $jsonBody")
        val mediaType = "application/json".toMediaType()
        val reqBody = jsonBody.toRequestBody(mediaType)
        val req = Request.Builder().url(railwayUrl).post(reqBody).build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e("FinalResponse", "Request failed with code: ${resp.code}")
                    return@withContext "Error: ${resp.code}"
                }
                val rBody = resp.body?.string() ?: return@withContext null
                Log.d("FinalResponse", rBody)
                val chatResp = gson.fromJson(rBody, ChatResponse::class.java)
                chatResp.choices.firstOrNull()?.message?.content
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error: ${e.message}"
        }
    }

    private suspend fun callClassifierApi(messages: List<ChatMessage>): String? = callKupidXApi(messages)

    fun clearMemoryForAI(aiId: String) {
        memoryLogs[aiId]?.clear()
        memoryRef.child(aiId).setValue(null)
    }
}

@Composable
fun ChatScreen(navController: NavController, otherUserId: String) {
    val profileViewModel: ProfileViewModel = viewModel()
    val chatAIViewModel: ChatAIViewModel = viewModel()
    ChatScreenContent(navController, otherUserId, profileViewModel, chatAIViewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
    navController: NavController,
    otherUserId: String,
    profileViewModel: ProfileViewModel,
    chatAIViewModel: ChatAIViewModel
) {
    var isSendingMessage by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var fullScreenTarget by remember { mutableStateOf<Message?>(null) }
    val database = FirebaseDatabase.getInstance()
    val usersRef = database.getReference("users")
    val chatId = getChatId(currentUserId, otherUserId)
    val messagesRef = database.getReference("messages/$chatId")
    val notificationsRef = database.getReference("notifications")
    val ratingsRef = database.getReference("ratings")
    val reportsRef = database.getReference("reports")
    val storageRef = FirebaseStorage.getInstance().reference

    val isAiConversation = otherUserId.endsWith("Ai")

    var averageRating by remember { mutableStateOf(0.0) }
    var yourRating by rememberSaveable(otherUserId) { mutableStateOf(-1.0) }
    var currentUserProfile by remember { mutableStateOf<Profile?>(null) }
    var otherUserProfile by remember { mutableStateOf<Profile?>(null) }
    val messages = remember { mutableStateListOf<Message>() }
    var messageText by remember { mutableStateOf("") }
    var showRating by remember { mutableStateOf(false) }
    var moreOptionsMenuExpanded by remember { mutableStateOf(false) }
    var showClearChatMenu by remember { mutableStateOf(false) }
    var showDeleteTimerMenu by remember { mutableStateOf(false) }
    /** default = 30 days */
    val ONE_MONTH_MS = 30L * 24 * 60 * 60 * 1000   // 30 × 24 h
    var deleteTimer by remember { mutableStateOf<Long?>(ONE_MONTH_MS) }
    var suggestions by remember { mutableStateOf<ChatSuggestions?>(null) }
    var suggestionsExpanded by remember { mutableStateOf(false) }
    var placeSuggestions by remember { mutableStateOf<List<PlaceDetails>?>(null) }
    var placeSuggestionsExpanded by remember { mutableStateOf(false) }
    var isLoadingSuggestions by remember { mutableStateOf(false) }
    var isLoadingPlaces by remember { mutableStateOf(false) }
    var isLoadingMessages by remember { mutableStateOf(true) } // New loading state for messages
    var isLoadingProfiles by remember { mutableStateOf(true) } // New loading state for profiles
    var isUploadingMedia by remember { mutableStateOf(false) } // New loading state for media upload

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

    // Media-related state (photo only now)
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedMediaType by remember { mutableStateOf<String?>(null) }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && selectedMediaUri != null) {
            isUploadingMedia = true
            sendMediaMessage(
                currentUserId = currentUserId,
                otherUserId = otherUserId,
                chatId = chatId,
                uri = selectedMediaUri!!,
                mediaType = "photo",
                messagesRef = messagesRef,
                context = context
            ) {
                postNotification(notificationsRef, otherUserId, currentUserId, "[Photo Message]")
                selectedMediaUri = null
                selectedMediaType = null
                isUploadingMedia = false
            }
        } else {
            Toast.makeText(context, "Photo capture failed", Toast.LENGTH_SHORT).show()
            isUploadingMedia = false
        }
    }

    // Permission launchers
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && !isAiConversation) {
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
            Toast.makeText(context, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            selectedMediaType = "photo"          // guarantee it’s set
            selectedMediaUri = freshPhotoUri(context)
            takePhotoLauncher.launch(selectedMediaUri!!)
        } else {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedMediaUri = it
            selectedMediaType = "photo"
        }
    }

    // Existing LaunchedEffect blocks with loading indicators
    LaunchedEffect(Unit) {
        isLoadingProfiles = true
        usersRef.child(currentUserId).get().addOnSuccessListener { snapshot ->
            currentUserProfile = snapshot.getValue(Profile::class.java)
            isLoadingProfiles = false
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to load your profile", Toast.LENGTH_SHORT).show()
            isLoadingProfiles = false
        }
        if (!isAiConversation) {
            usersRef.child(otherUserId).get().addOnSuccessListener { snapshot ->
                val profile = snapshot.getValue(Profile::class.java)
                if (profile != null) {
                    otherUserProfile = profile
                    averageRating = profile.averageRating
                }
                isLoadingProfiles = false
            }.addOnFailureListener {
                Toast.makeText(context, "Failed to load user", Toast.LENGTH_SHORT).show()
                isLoadingProfiles = false
            }
            fetchUserRating(ratingsRef, otherUserId) { rating -> yourRating = rating }
            fetchAverageRating(ratingsRef, otherUserId) { avg -> averageRating = avg }
        }
    }

    DisposableEffect(messagesRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isLoadingMessages = true
                val newMessages = snapshot.children.mapNotNull { it.getValue(Message::class.java) }
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
            postNotification(notificationsRef, otherUserId, message.senderId, message.text ?: "[Media]")
            messagesRef.child(message.id).child("processed").setValue(true)
        }
    }

    // Delete timer logic
    LaunchedEffect(deleteTimer, messages) {
        while (true) {
            deleteTimer?.let { timer ->
                val oldestMessage = messages.minByOrNull { it.timestamp }
                if (oldestMessage != null) {
                    val elapsedTime = System.currentTimeMillis() - oldestMessage.timestamp
                    if (elapsedTime >= timer) {
                        // Clear RTDB
                        messagesRef.removeValue()
                        messages.clear()

                        // Delete media from Storage
                        messages.forEach { msg ->
                            if (msg.mediaUrl != null && (msg.mediaType == "photo" || msg.mediaType == "voice")) {
                                try {
                                    val storage = FirebaseStorage.getInstance()
                                    val mediaRef = storage.getReferenceFromUrl(msg.mediaUrl)
                                    mediaRef.delete().addOnSuccessListener {
                                        Log.d("Storage", "Media deleted successfully for message ${msg.id}")
                                    }.addOnFailureListener { e ->
                                        Log.e("Storage", "Failed to delete media for message ${msg.id}: ${e.message}")
                                    }
                                } catch (e: Exception) {
                                    Log.e("Storage", "Invalid URL or failed to get reference for message ${msg.id}: ${e.message}")
                                }
                            }
                        }
                        deleteTimer = null // Reset timer after deletion
                    }
                }
                delay(60000) // Check every minute
            } ?: break
        }
    }

    LaunchedEffect(isRecording) {
        if (!isAiConversation && isRecording) {
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
                return getChatSuggestions(messages, currentUserProfile ?: Profile(), otherUserProfile, context)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            otherUserProfile?.let {
                                if (!isAiConversation) navController.navigate("matchedUserProfile/$otherUserId")
                            }
                        }
                    ) {
                        if (isLoadingProfiles) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(40.dp).clip(CircleShape))
                        } else if (otherUserProfile?.profilepicUrl?.isNotBlank() == true) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(otherUserProfile!!.profilepicUrl)
                                    .diskCacheKey(otherUserProfile!!.profilepicUrl)
                                    .memoryCacheKey(otherUserProfile!!.profilepicUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile",
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Person, "Default Avatar", tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(otherUserProfile?.name ?: "Chat", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } },
                actions = {
                    if (!isAiConversation) {
                        IconButton(
                            onClick = {
                                suggestionsExpanded = true
                                if (suggestions == null || messages.size > 10) {
                                    scope.launch {
                                        isLoadingSuggestions = true
                                        suggestions = fetchSuggestionsWithRetry()
                                        isLoadingSuggestions = false
                                    }
                                }
                            }
                        ) { Icon(Icons.Default.Lightbulb, "Suggestions", tint = Color(0xFFFFA500)) }
                        IconButton(
                            onClick = {
                                placeSuggestionsExpanded = true
                                if (placeSuggestions == null || messages.size > 10) {
                                    scope.launch {
                                        isLoadingPlaces = true
                                        val sugg = fetchSuggestionsWithRetry()
                                        placeSuggestions = sugg?.topics?.let { getPlaceSuggestions(it, otherUserProfile, context) }
                                        isLoadingPlaces = false
                                    }
                                }
                            }
                        ) { Icon(Icons.Default.Place, "Places Suggestions", tint = Color(0xFFFF6F00)) }
                    }
                    IconButton(onClick = { moreOptionsMenuExpanded = true }) { Icon(Icons.Default.MoreVert, "More Options", tint = Color.White) }
                    DropdownMenu(expanded = moreOptionsMenuExpanded, onDismissRequest = { moreOptionsMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Delete, "Clear Chat", tint = Color.Red); Spacer(Modifier.width(4.dp)); Text("Clear Chat...") } },
                            onClick = { moreOptionsMenuExpanded = false; showClearChatMenu = true }
                        )
                        DropdownMenuItem(
                            text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Timer, "Set Delete Timer", tint = Color.Yellow); Spacer(Modifier.width(4.dp)); Text("Set Delete Timer...") } },
                            onClick = { moreOptionsMenuExpanded = false; showDeleteTimerMenu = true }
                        )
                    }
                    DropdownMenu(expanded = showClearChatMenu, onDismissRequest = { showClearChatMenu = false }) {
                        DropdownMenuItem(text = { Text("Clear Chat Only") }, onClick = { showClearChatMenu = false; messagesRef.setValue(null); messages.clear() })
                    }
                    DropdownMenu(expanded = showDeleteTimerMenu,
                        onDismissRequest = { showDeleteTimerMenu = false }) {

                        @Composable
                        fun item(label:String, value:Long?) = DropdownMenuItem(
                            text = {
                                Text(
                                    label,
                                    // orange when currently selected
                                    color = if (deleteTimer == value) Color(0xFFFFA500) else Color.White
                                )
                            },
                            onClick = { deleteTimer = value ; showDeleteTimerMenu = false }
                        )

                        item("1 Day"   , 24L * 60 * 60 * 1000)
                        item("1 Week"  , 7L  * 24 * 60 * 60 * 1000)
                        item("1 Month" , 30L * 24 * 60 * 60 * 1000)
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues).background(Color.Black)) {
            Column(Modifier.fillMaxSize()) {
                if (!isAiConversation && otherUserProfile != null && showRating) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        RatingBar(
                            rating = averageRating,
                            ratingCount = otherUserProfile!!.numberOfRatings
                        )
                        Text(
                            "Your Rating: ${
                                if (yourRating >= 0) String.format(
                                    "%.1f",
                                    yourRating
                                ) else "N/A"
                            }", color = Color.Gray, fontSize = 14.sp
                        )
                        Slider(
                            value = if (yourRating >= 0) yourRating.toFloat() else 0f,
                            onValueChange = { yourRating = it.toDouble() },
                            onValueChangeFinished = {
                                if (yourRating >= 0) updateUserRating(
                                    ratingsRef,
                                    usersRef,
                                    otherUserId,
                                    yourRating,
                                    context
                                )
                            },
                            valueRange = 0f..5f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFF4500),
                                activeTrackColor = Color(0xFFFF4500)
                            )
                        )
                    }
                }

                if (isLoadingMessages) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFFA500))
                    }
                } else {
                    LazyColumn(
                        Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        reverseLayout = true,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        items(messages.reversed()) { message ->
                            when (message.mediaType) {
                                "voice" -> VoiceMessageBubble(message, currentUserId)
                                "photo" -> MediaMessageBubble(
                                    message = message,
                                    currentUserId = currentUserId,
                                    onFullscreen = { fullScreenTarget = it }
                                )
                                else -> MessageBubble(message, currentUserId)
                            }
                        }
                    }
                }

                if (!isAiConversation && isRecording) {
                    Text(
                        "Recording... Time left: ${recordingTimeLeft / 1000}s",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (!isAiConversation && recordedVoiceUri != null) {
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
                        IconButton(onClick = { recordedVoiceUri = null ; recordFile = null }) {
                            Icon(Icons.Default.Close, null, tint = Color.Red)
                        }
                    }
                }

                /* Media preview (photo only, no checkbox) */
                if (!isAiConversation &&
                    selectedMediaUri != null &&
                    selectedMediaType == "photo"
                ) {
                    Box(Modifier.padding(8.dp)) {
                        AsyncImage(
                            model = selectedMediaUri,
                            contentDescription = "Preview",
                            modifier = Modifier
                                .height(200.dp)
                                .fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )

                        /* small red ✕ in the top‑right corner */
                        IconButton(
                            onClick = { selectedMediaUri = null; selectedMediaType = null },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.Red)
                        }
                    }
                }


                // Media Buttons Row (with rating toggle icon)
                if (!isAiConversation) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.Start),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mic/Stop button
                        IconButton(onClick = {
                            if (isRecording) {
                                recorder?.stop(); recorder?.release(); recorder = null
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
                                } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }) {
                            Icon(
                                if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                null,
                                tint = Color(0xFFFFA500)
                            )
                        }

                        // Photo picker
                        IconButton(onClick = { pickPhotoLauncher.launch("image/*") }) {
                            Icon(Icons.Default.Photo, null, tint = Color(0xFFFFA500))
                        }

                        // Camera for photo
                        IconButton(onClick = {
                            selectedMediaType = "photo"
                            if (context.checkSelfPermission(Manifest.permission.CAMERA)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                selectedMediaUri = freshPhotoUri(context)
                                takePhotoLauncher.launch(selectedMediaUri!!)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }) { Icon(Icons.Default.Camera, null, tint = Color(0xFFFFA500)) }


                        // Rating toggle icon
                        IconButton(onClick = { showRating = !showRating }) {
                            Icon(Icons.Default.Star, "Toggle Rating", tint = Color(0xFFFFA500))
                        }
                    }
                }

                /* ─── Input bar ───────────────────────────────────────────────────────── */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    /* text field -------------------------------------------------------- */
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Type a message…", color = Color.Gray) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .background(Color.DarkGray, RoundedCornerShape(24.dp)),
                        colors = TextFieldDefaults.textFieldColors(
                            containerColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            focusedPlaceholderColor = Color.Gray,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { /* let the orange button handle it */ })
                    )

                    Spacer(Modifier.width(8.dp))

                    /* single orange SEND button ---------------------------------------- */
                    IconButton(
                        onClick = {
                            if (isSendingMessage) return@IconButton   // ignore double‑taps
                            isSendingMessage = true

                            /* 1) PHOTO ---------------------------------------------------- */
                            if (selectedMediaUri != null && selectedMediaType == "photo") {
                                sendMediaMessage(
                                    currentUserId, otherUserId, chatId,
                                    selectedMediaUri!!, "photo",
                                    messagesRef, context
                                ) {
                                    postNotification(
                                        notificationsRef, otherUserId, currentUserId,
                                        "[Photo Message]"
                                    )
                                    selectedMediaUri = null
                                    selectedMediaType = null
                                    isSendingMessage = false          // done
                                }
                                return@IconButton
                            }

                            /* 2) VOICE ---------------------------------------------------- */
                            if (recordedVoiceUri != null) {
                                sendVoiceMessage(
                                    currentUserId, otherUserId, chatId,
                                    recordedVoiceUri!!, messagesRef, context
                                )
                                postNotification(
                                    notificationsRef, otherUserId, currentUserId,
                                    "[Voice Message]"
                                )
                                recordedVoiceUri = null
                                recordFile = null
                                isSendingMessage = false
                                return@IconButton
                            }

                            /* 3) TEXT ----------------------------------------------------- */
                            if (messageText.isNotBlank()) {
                                if (isAiConversation) {
                                    chatAIViewModel.sendMessageToAI(
                                        otherUserId, messageText, currentUserId,
                                        messagesRef, context, messages,
                                        currentUserProfile?.name ?: "User"
                                    )
                                } else {
                                    sendMessage(currentUserId, otherUserId, chatId,
                                        messageText, messagesRef)
                                    postNotification(
                                        notificationsRef, otherUserId, currentUserId,
                                        messageText
                                    )
                                }
                                messageText = ""
                            }
                            isSendingMessage = false
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFFF4500), CircleShape)
                    ) {
                        if (isSendingMessage) {
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

            // Suggestions Dropdown (preserved from original)
            if (!isAiConversation && suggestionsExpanded) {
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
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                        CircularProgressIndicator(color = Color(0xFFFFA500))
                                    }
                                }
                            } else {
                                suggestions?.let { sugg ->
                                    if (sugg.topics.isNotEmpty()) {
                                        item {
                                            Text("Topics", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        items(sugg.topics.toMutableList()) { topic ->
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 8.dp, top = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(topic, color = Color.White)
                                                IconButton(onClick = {
                                                    suggestions = sugg.copy(topics = sugg.topics.filter { it != topic })
                                                }) {
                                                    Icon(Icons.Default.Delete, "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                    if (sugg.activities.isNotEmpty()) {
                                        item {
                                            Spacer(Modifier.height(8.dp))
                                            Text("Activities", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        items(sugg.activities.toMutableList()) { activity ->
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 8.dp, top = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "${activity.placeName} - ${activity.integration}",
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable {
                                                            messageText = activity.integration
                                                            suggestionsExpanded = false
                                                        }
                                                )
                                                IconButton(onClick = {
                                                    suggestions = sugg.copy(activities = sugg.activities.filter { it != activity })
                                                }) {
                                                    Icon(Icons.Default.Delete, "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                    if (sugg.integrationTips.isNotEmpty()) {
                                        item {
                                            Spacer(Modifier.height(8.dp))
                                            Text("Tips", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        items(sugg.integrationTips.toMutableList()) { tip ->
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 8.dp, top = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(tip, color = Color.White)
                                                IconButton(onClick = {
                                                    suggestions = sugg.copy(integrationTips = sugg.integrationTips.filter { it != tip })
                                                }) {
                                                    Icon(Icons.Default.Delete, "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                    if (sugg.topics.isEmpty() && sugg.activities.isEmpty() && sugg.integrationTips.isEmpty()) {
                                        item {
                                            Text("No suggestions", color = Color.Gray)
                                            Spacer(Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        isLoadingSuggestions = true
                                                        suggestions = fetchSuggestionsWithRetry()
                                                        isLoadingSuggestions = false
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500))
                                            ) { Text("Get Suggestions", color = Color.White) }
                                        }
                                    }
                                } ?: item {
                                    Text("No suggestions", color = Color.Gray)
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isLoadingSuggestions = true
                                                suggestions = fetchSuggestionsWithRetry()
                                                isLoadingSuggestions = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500))
                                    ) { Text("Get Suggestions", color = Color.White) }
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
                                    ) { Text("Close", color = Color.White) }
                                }
                            }
                        }
                    }
                }
            }

            // Places Suggestions Dropdown (preserved from original)
            if (!isAiConversation && placeSuggestionsExpanded) {
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
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
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
                                                        postNotification(notificationsRef, otherUserId, currentUserId, messageText)
                                                        placeSuggestionsExpanded = false
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(onClick = {
                                                    placeSuggestions = places.filter { it != place }
                                                }) {
                                                    Icon(Icons.Default.Delete, "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    } else {
                                        item {
                                            Text("No places found", color = Color.Gray)
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
                                            ) { Text("Get Suggestions", color = Color.White) }
                                        }
                                    }
                                } ?: item {
                                    Text("No places found", color = Color.Gray)
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
                                    ) { Text("Get Suggestions", color = Color.White) }
                                }
                            }
                            item {
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Button(
                                        onClick = { placeSuggestions = emptyList() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                    ) { Text("Clear All", color = Color.White) }
                                    Button(
                                        onClick = { placeSuggestionsExpanded = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4500))
                                    ) { Text("Close", color = Color.White) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    /* Full-screen viewer overlay (photo only) */
    FullscreenMediaViewer(
        target = fullScreenTarget,
        onDismiss = { fullScreenTarget = null },
        messagesRef = messagesRef,
        reportsRef = reportsRef
    )
}

fun freshPhotoUri(context: Context): Uri {
    val photoFile = createTempFile(context, ".jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
}

// Helper function to create temporary files
fun createTempFile(context: Context, extension: String): File {
    val dir = File(context.cacheDir, "media")
    if (!dir.exists()) dir.mkdirs()
    return File.createTempFile("media_${System.currentTimeMillis()}", extension, dir)
}

/* Full-screen viewer overlay (photo only) */
@Composable
fun FullscreenMediaViewer(
    target: Message?,
    onDismiss: () -> Unit,
    messagesRef: DatabaseReference,
    reportsRef: DatabaseReference
) {
    val context = LocalContext.current

    if (target == null) return

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (target.mediaType == "photo") {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(target.mediaUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Full Screen Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onError = { Toast.makeText(context, "Failed to load photo", Toast.LENGTH_SHORT).show() }
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

/* MediaMessageBubble (photo only) */
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
        horizontalArrangement = if (isCurrentUser) Arrangement.End
        else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Box(Modifier.clickable { onFullscreen(message) }) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(message.mediaUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Photo",
                    modifier = Modifier.size(150.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatRelativeTime(message.timestamp),
                    color = Color.DarkGray,
                    fontSize = 12.sp
                )
                if (ticks.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(ticks, color = Color(0xFFFF4500), fontSize = 12.sp)
                }
            }
        }
    }
}


fun sendMediaMessage(
    currentUserId: String,
    otherUserId: String,
    chatId: String,
    uri: Uri,
    mediaType: String,
    messagesRef: DatabaseReference,
    context: android.content.Context,
    onSuccess: () -> Unit = {}
) {
    val timestamp = System.currentTimeMillis()
    val storageRef = FirebaseStorage.getInstance().reference
    val fileName = "${mediaType}_${timestamp}.${if (mediaType == "photo") "jpg" else "aac"}"
    val mediaRef = storageRef.child("$mediaType/$chatId/$fileName")

    mediaRef.putFile(uri).addOnSuccessListener {
        mediaRef.downloadUrl.addOnSuccessListener { downloadUrl ->
            val messageId = messagesRef.push().key ?: return@addOnSuccessListener
            val message = Message(
                id = messageId,
                senderId = currentUserId,
                receiverId = otherUserId,
                text = "",
                timestamp = timestamp,
                read = false,
                mediaType = mediaType,
                mediaUrl = downloadUrl.toString(),
                processed = false,
            )
            messagesRef.child(messageId).setValue(message)
            onSuccess()
        }.addOnFailureListener { Toast.makeText(context, "Failed to get $mediaType URL", Toast.LENGTH_SHORT).show() }
    }.addOnFailureListener { Toast.makeText(context, "Failed to upload $mediaType", Toast.LENGTH_SHORT).show() }
}

// Updated PlaceDetailsCard (unchanged from previous, included for completeness)
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
                }) { Text("Directions", color = Color(0xFFFFA500)) }
                Text("Tap to send", color = Color(0xFFFF4500), fontSize = 12.sp)
            }
        }
    }
}

// Helper functions
fun postNotification(notificationsRef: DatabaseReference, toUserId: String, fromUserId: String, message: String) {
    val notificationId = notificationsRef.child(toUserId).push().key ?: return
    val noti = Notification(id = notificationId, type = "chat_message", senderId = fromUserId, senderUsername = "", message = message, timestamp = System.currentTimeMillis(), isRead = "false")
    notificationsRef.child(toUserId).child(notificationId).setValue(noti)
        .addOnSuccessListener { Log.d("Notifications", "Notification posted: $message") }
        .addOnFailureListener { Log.e("Notifications", "Failed to post notification: ${it.message}") }
}

fun getChatId(userId1: String, userId2: String): String = if (userId1 < userId2) "${userId1}_$userId2" else "${userId2}_$userId1"

fun sendMessage(currentUserId: String, otherUserId: String, chatId: String, messageText: String, messagesRef: DatabaseReference) {
    val timestamp = System.currentTimeMillis()
    val messageId = messagesRef.push().key ?: return
    val message = Message(id = messageId, senderId = currentUserId, receiverId = otherUserId, text = messageText, timestamp = timestamp, read = false, processed = false)
    messagesRef.child(messageId).setValue(message)
}

fun sendVoiceMessage(currentUserId: String, otherUserId: String, chatId: String, uri: Uri, messagesRef: DatabaseReference, context: android.content.Context) {
    val timestamp = System.currentTimeMillis()
    val storageRef = FirebaseStorage.getInstance().reference
    val fileName = "voice_message_$timestamp.aac"
    val voiceRef = storageRef.child("voice_messages/$chatId/$fileName")

    voiceRef.putFile(uri).addOnSuccessListener {
        voiceRef.downloadUrl.addOnSuccessListener { downloadUrl ->
            val messageId = messagesRef.push().key ?: return@addOnSuccessListener
            val message = Message(id = messageId, senderId = currentUserId, receiverId = otherUserId, text = "", timestamp = timestamp, read = false, mediaType = "voice", mediaUrl = downloadUrl.toString(), processed = false)
            messagesRef.child(messageId).setValue(message)
        }.addOnFailureListener { Toast.makeText(context, "Failed to get voice URL.", Toast.LENGTH_SHORT).show() }
    }.addOnFailureListener { Toast.makeText(context, "Failed to upload voice.", Toast.LENGTH_SHORT).show() }
}

@Composable
fun MessageBubble(message: Message, currentUserId: String) {
    val isCurrentUser = message.senderId == currentUserId
    // Set bubble colors: Yellow for current user, Orange for other user
    val bubbleColor = if (isCurrentUser) Color(0xFFFFDB00) else Color(0xFFFF6F00)
    // Use a contrasting text color based on bubble color
    val textColor = if (isCurrentUser) Color.Black else Color.White

    val context = LocalContext.current
    // Create an annotated string where links are detected and styled
    val annotatedText = remember(message.text) { createAnnotatedString(message.text) }

    // Display check ticks if needed
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
                .background(bubbleColor, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            // Use ClickableText to allow clickable links within the message text
            ClickableText(
                text = annotatedText,
                style = TextStyle(color = textColor, fontSize = 16.sp),
                onClick = { offset ->
                    annotatedText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            // Launch the browser with the URL
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
                    Text(text = ticks, color = Color(0xFFFF4500), fontSize = 12.sp)
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
                            setOnErrorListener { _, what, extra -> Toast.makeText(context, "Playback error: $what, $extra", Toast.LENGTH_SHORT).show(); false }
                        }
                    }
                }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color(0xFFFF4500)) }
                LinearProgressIndicator(progress = progress, Modifier.weight(1f).padding(horizontal = 8.dp), color = Color(0xFFFFA500), trackColor = Color.Gray)
                Text(formatDuration(player?.duration?.toLong() ?: 0L), color = Color.DarkGray, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatRelativeTime(message.timestamp), color = Color.DarkGray, fontSize = 12.sp)
                if (ticks.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Text(ticks, color = Color(0xFFFF4500), fontSize = 12.sp) }
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
        // Style the link text (e.g., blue and underlined)
        builder.addStyle(
            style = SpanStyle(
                color = Color.Blue,
                textDecoration = TextDecoration.Underline
            ),
            start = start,
            end = end
        )
        // Add an annotation to detect clicks on the URL
        builder.addStringAnnotation(
            tag = "URL",
            annotation = result.value,
            start = start,
            end = end
        )
    }
    return builder.toAnnotatedString()
}

@Composable
fun VoiceMessagePlayer(mediaUrl: String, isPlaying: Boolean, onPlayToggle: () -> Unit, progress: Float, duration: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPlayToggle) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color(0xFFFF4500)) }
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

fun updateUserRating(ratingsRef: DatabaseReference, usersRef: DatabaseReference, userId: String, rating: Double, context: android.content.Context) {
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
                Toast.makeText(context, "Rating updated!", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { Toast.makeText(context, "Failed to update average rating.", Toast.LENGTH_SHORT).show() }
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
        }.addOnFailureListener { Toast.makeText(context, "Failed to update ratings.", Toast.LENGTH_SHORT).show() }
    }.addOnFailureListener { Toast.makeText(context, "Failed to fetch current ratings.", Toast.LENGTH_SHORT).show() }
}