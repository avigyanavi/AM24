@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ChatScreen(navController: NavController, otherUserId: String) {
    val profileViewModel: ProfileViewModel = viewModel()
    ChatScreenContent(navController = navController, otherUserId = otherUserId, profileViewModel = profileViewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(navController: NavController, otherUserId: String, profileViewModel: ProfileViewModel) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    val database = FirebaseDatabase.getInstance()
    val usersRef = database.getReference("users")
    val chatId = getChatId(currentUserId, otherUserId)
    val messagesRef = database.getReference("messages/$chatId")
    val ratingsRef = database.getReference("ratings")

    var averageRating by remember { mutableStateOf(0.0) }
    var yourRating by rememberSaveable(otherUserId) { mutableStateOf(-1.0) }
    var otherUserProfile by remember { mutableStateOf<Profile?>(null) }
    val messages = remember { mutableStateListOf<Message>() }
    var messageText by remember { mutableStateOf("") }

    var showRating by remember { mutableStateOf(true) }
    var moreOptionsMenuExpanded by remember { mutableStateOf(false) }

    // Voice recording states
    var isRecording by remember { mutableStateOf(false) }
    var recorder: MediaRecorder? by remember { mutableStateOf(null) }
    var recordFile: File? by remember { mutableStateOf(null) }
    val maxDurationMs = 60 * 1000
    var recordingTimeLeft by remember { mutableStateOf(maxDurationMs) }

    var recordedVoiceUri by remember { mutableStateOf<Uri?>(null) }
    var isVoicePlaying by remember { mutableStateOf(false) }
    var voiceProgress by remember { mutableStateOf(0f) }
    var voicePlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val chatPrefsRef = usersRef.child(currentUserId).child("chatPreferences").child(otherUserId)

    // Permission for RECORD_AUDIO
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isRecording = true
        } else {
            Toast.makeText(context, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    // Load user profile and rating
    LaunchedEffect(otherUserId) {
        usersRef.child(otherUserId).get().addOnSuccessListener { snapshot ->
            val profile = snapshot.getValue(Profile::class.java)
            if (profile != null) {
                otherUserProfile = profile
                averageRating = profile.averageRating
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to load user", Toast.LENGTH_SHORT).show()
        }

        fetchUserRating(ratingsRef, otherUserId) { rating ->
            yourRating = rating
        }

        fetchAverageRating(ratingsRef, otherUserId) { avg ->
            averageRating = avg
        }

        // Load showRating preference
        chatPrefsRef.child("showRating").get().addOnSuccessListener { snap ->
            if (snap.exists()) {
                showRating = snap.getValue(Boolean::class.java) ?: true
            } else {
                showRating = true // default if not set
            }
        }
    }

    // Listen for messages
    LaunchedEffect(chatId) {
        messagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newMessages = mutableListOf<Message>()
                for (msgSnap in snapshot.children) {
                    val message = msgSnap.getValue(Message::class.java)
                    if (message != null) newMessages.add(message)
                }
                newMessages.sortBy { it.timestamp }
                messages.clear()
                messages.addAll(newMessages)
                markMessagesAsRead(messagesRef, messages, currentUserId)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Handle recording logic
    LaunchedEffect(isRecording) {
        if (isRecording) {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                try {
                    withContext(Dispatchers.IO) {
                        recorder?.release()
                        recorder = null
                        recordFile = File(context.cacheDir, "voice_message_${System.currentTimeMillis()}.aac")
                        recorder = MediaRecorder().apply {
                            setAudioSource(MediaRecorder.AudioSource.MIC)
                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            setOutputFile(recordFile?.absolutePath)
                            setMaxDuration(maxDurationMs)
                            prepare()
                            start()
                        }
                    }

                    withContext(Dispatchers.Main) { recordingTimeLeft = maxDurationMs }

                    while (isRecording && recordingTimeLeft > 0) {
                        delay(1000)
                        withContext(Dispatchers.Main) {
                            recordingTimeLeft -= 1000
                        }
                    }

                    if (isRecording && recordingTimeLeft <= 0) {
                        withContext(Dispatchers.Main) {
                            isRecording = false
                        }
                    }

                } catch (e: Exception) {
                    Log.e("VoiceMsg", "Recording error: ${e.message}")
                    withContext(Dispatchers.Main) {
                        isRecording = false
                        Toast.makeText(context, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    withContext(Dispatchers.IO) {
                        recorder?.release()
                        recorder = null
                    }
                }
            } else {
                isRecording = false
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            // Stopped recording
            try {
                withContext(Dispatchers.IO) {
                    recorder?.apply {
                        stop()
                        release()
                    }
                    recorder = null
                }
                val fileUri = recordFile?.let { Uri.fromFile(it) }
                withContext(Dispatchers.Main) {
                    if (fileUri != null) recordedVoiceUri = fileUri
                    recordingTimeLeft = maxDurationMs
                }
            } catch (e: Exception) {
                Log.e("VoiceMsg", "Stop recording error: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                withContext(Dispatchers.IO) {
                    recorder?.release()
                    recorder = null
                }
            }
        }
    }

    // Handle voice playback progress
    LaunchedEffect(isVoicePlaying) {
        if (isVoicePlaying && voicePlayer != null) {
            while (isVoicePlaying && voicePlayer?.isPlaying == true) {
                delay(500L)
                val current = voicePlayer?.currentPosition ?: 0
                val duration = voicePlayer?.duration ?: 1
                voiceProgress = current.toFloat() / duration.toFloat()
            }
        } else {
            voiceProgress = 0f
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (otherUserProfile?.profilepicUrl?.isNotBlank() == true) {
                            AsyncImage(
                                model = otherUserProfile!!.profilepicUrl,
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = otherUserProfile!!.name,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Hide rating icon next to the username (just toggles showRating)
                            IconButton(onClick = {
                                showRating = !showRating
                                // Save preference
                                chatPrefsRef.child("showRating").setValue(showRating)
                            }) {
                                Icon(
                                    imageVector = if (showRating) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Hide/Show Rating",
                                    tint = Color.Gray
                                )
                            }
                        } else {
                            Text("Chat", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { moreOptionsMenuExpanded = !moreOptionsMenuExpanded }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = moreOptionsMenuExpanded,
                        onDismissRequest = { moreOptionsMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Report", color = Color.White) },
                            onClick = {
                                moreOptionsMenuExpanded = false
                                val targetId = otherUserId
                                profileViewModel.reportProfile(
                                    profileId = targetId,
                                    reporterId = currentUserId,
                                    onSuccess = { Toast.makeText(context, "Reported!", Toast.LENGTH_SHORT).show() },
                                    onFailure = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Block/Unblock", color = Color.White) },
                            onClick = {
                                moreOptionsMenuExpanded = false
                                val targetId = otherUserId
                                profileViewModel.blockProfile(
                                    currentUserId = currentUserId,
                                    targetUserId = targetId,
                                    onSuccess = { Toast.makeText(context, "Done!", Toast.LENGTH_SHORT).show() },
                                    onFailure = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Unmatch", color = Color.White) },
                            onClick = {
                                moreOptionsMenuExpanded = false
                                unmatchUser(otherUserId, context)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            if (otherUserProfile != null && showRating) {
                // Show rating info and slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Avg Rating: ${String.format("%.1f", averageRating)}",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Your Rating: ${if (yourRating >= 0) String.format("%.1f", yourRating) else "N/A"}",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Slider(
                        value = if (yourRating >= 0) yourRating.toFloat() else 0f,
                        onValueChange = { yourRating = it.toDouble() },
                        onValueChangeFinished = {
                            if (yourRating >= 0) {
                                updateUserRating(ratingsRef, usersRef, otherUserId, yourRating, context)
                            }
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

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.Bottom
            ) {
                items(messages.reversed()) { message ->
                    if (message.mediaType == "voice" && !message.mediaUrl.isNullOrEmpty()) {
                        VoiceMessageBubble(message, currentUserId)
                    } else {
                        MessageBubble(message, currentUserId)
                    }
                }
            }

            if (isRecording) {
                Text(
                    text = "Recording... Time left: ${recordingTimeLeft / 1000}s",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else if (recordedVoiceUri != null) {
                // Voice message preview
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
                                voicePlayer?.setOnCompletionListener {
                                    isVoicePlaying = false
                                    voiceProgress = 0f
                                }
                            }
                        }
                    },
                    progress = voiceProgress,
                    duration = voicePlayer?.duration?.toLong() ?: 0L
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            recordedVoiceUri = null
                            recordFile = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Delete", color = Color.White)
                    }
                    Button(
                        onClick = {
                            sendVoiceMessage(currentUserId, otherUserId, chatId, recordedVoiceUri!!, messagesRef, context)
                            recordedVoiceUri = null
                            recordFile = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4500))
                    ) {
                        Text("Send Voice", color = Color.White)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (isRecording) {
                        isRecording = false
                    } else {
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            isRecording = true
                            messageText = ""
                            recordedVoiceUri = null
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Record",
                        tint = Color(0xFFFFA500)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Type a message...", color = Color.Gray) },
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.DarkGray, shape = RoundedCornerShape(24.dp)),
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        focusedPlaceholderColor = Color.Gray,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (messageText.isNotBlank()) {
                                sendMessage(currentUserId, otherUserId, chatId, messageText, messagesRef)
                                messageText = ""
                            }
                        }
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            sendMessage(currentUserId, otherUserId, chatId, messageText, messagesRef)
                            messageText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFFF4500), shape = CircleShape)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}


fun getChatId(userId1: String, userId2: String): String {
    return if (userId1 < userId2) "${userId1}_$userId2" else "${userId2}_$userId1"
}

fun sendMessage(
    currentUserId: String,
    otherUserId: String,
    chatId: String,
    messageText: String,
    messagesRef: DatabaseReference
) {
    val timestamp = System.currentTimeMillis()
    val messageId = messagesRef.push().key ?: return
    val message = Message(
        id = messageId,
        senderId = currentUserId,
        receiverId = otherUserId,
        text = messageText,
        timestamp = timestamp,
        read = false
    )
    messagesRef.child(messageId).setValue(message)
}

fun sendVoiceMessage(
    currentUserId: String,
    otherUserId: String,
    chatId: String,
    uri: Uri,
    messagesRef: DatabaseReference,
    context: android.content.Context
) {
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
                mediaUrl = downloadUrl.toString()
            )
            messagesRef.child(messageId).setValue(message)
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to get voice URL.", Toast.LENGTH_SHORT).show()
        }
    }.addOnFailureListener {
        Toast.makeText(context, "Failed to upload voice.", Toast.LENGTH_SHORT).show()
    }
}

fun markMessagesAsRead(messagesRef: DatabaseReference, messages: List<Message>, currentUserId: String) {
    val unreadMessages = messages.filter { it.receiverId == currentUserId && !it.read }
    for (msg in unreadMessages) {
        messagesRef.child(msg.id).child("read").setValue(true)
    }
}

@Composable
fun MessageBubble(message: Message, currentUserId: String) {
    val isCurrentUser = message.senderId == currentUserId
    val ticks = if (isCurrentUser) if (message.read) "✔✔" else "✔" else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black, shape = RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(message.text, color = Color.White, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatRelativeTime(message.timestamp), color = Color.LightGray, fontSize = 12.sp)
                if (ticks.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
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
        } else {
            progress = 0f
        }
    }

    val ticks = if (isCurrentUser) if (message.read) "✔✔" else "✔" else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black, shape = RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (isPlaying) {
                        player?.pause()
                        isPlaying = false
                    } else {
                        val mp = MediaPlayer()
                        mp.setDataSource(message.mediaUrl)
                        mp.prepareAsync()
                        mp.setOnPreparedListener {
                            mp.start()
                            player = mp
                            isPlaying = true
                            mp.setOnCompletionListener {
                                isPlaying = false
                                progress = 0f
                            }
                        }
                        mp.setOnErrorListener { _, what, extra ->
                            Toast.makeText(context, "Playback error: $what, $extra", Toast.LENGTH_SHORT).show()
                            false
                        }
                    }
                }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color(0xFFFF4500)
                    )
                }
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    color = Color(0xFFFFA500),
                    trackColor = Color.Gray
                )
                Text(
                    text = formatDuration(player?.duration?.toLong() ?: 0L),
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatRelativeTime(message.timestamp), color = Color.LightGray, fontSize = 12.sp)
                if (ticks.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(ticks, color = Color(0xFFFF4500), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun VoiceMessagePlayer(
    mediaUrl: String,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    progress: Float,
    duration: Long
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPlayToggle() }) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color(0xFFFF4500)
            )
        }
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            color = Color(0xFFFFA500),
            trackColor = Color.Gray
        )
        Text(
            text = formatRelativeTime(duration),
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

fun unmatchUser(userId: String, context: android.content.Context) {
    // Implement unmatch logic in ProfileViewModel if needed
    Toast.makeText(context, "User $userId unmatched.", Toast.LENGTH_SHORT).show()
}

fun fetchAverageRating(
    ratingsRef: DatabaseReference,
    userId: String,
    onAverageFetched: (Double) -> Unit
) {
    ratingsRef.child(userId).child("averageRating").get().addOnSuccessListener { snapshot ->
        val average = snapshot.getValue(Double::class.java) ?: 0.0
        onAverageFetched(average)
    }.addOnFailureListener {
        onAverageFetched(0.0)
    }
}

fun fetchUserRating(
    ratingsRef: DatabaseReference,
    userId: String,
    onRatingFetched: (Double) -> Unit
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    ratingsRef.child(userId).child("ratings").child(currentUserId).get()
        .addOnSuccessListener { snapshot ->
            val yourRating = snapshot.getValue(Double::class.java) ?: 0.0
            onRatingFetched(yourRating)
        }
        .addOnFailureListener {
            onRatingFetched(0.0)
        }
}


fun updateUserRating(
    ratingsRef: DatabaseReference,
    usersRef: DatabaseReference,
    userId: String,
    rating: Double,
    context: android.content.Context
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRatingRef = ratingsRef.child(userId)
    userRatingRef.get().addOnSuccessListener { snapshot ->
        val ratingsMap = if (snapshot.exists() && snapshot.child("ratings").value is Map<*, *>) {
            snapshot.child("ratings")
                .getValue(object : GenericTypeIndicator<MutableMap<String, Double>>() {}) ?: mutableMapOf()
        } else {
            mutableMapOf()
        }

        ratingsMap[currentUserId] = rating
        val averageRating = if (ratingsMap.isNotEmpty()) ratingsMap.values.average() else 0.0

        val updates = mapOf(
            "ratings" to ratingsMap,
            "averageRating" to averageRating
        )
        userRatingRef.updateChildren(updates).addOnSuccessListener {
            usersRef.child(userId).child("averageRating").setValue(averageRating)
                .addOnSuccessListener {
                    Toast.makeText(context, "Rating updated!", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    Toast.makeText(context, "Failed to update average rating in profile.", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to update ratings.", Toast.LENGTH_SHORT).show()
        }
    }.addOnFailureListener {
        Toast.makeText(context, "Failed to fetch current ratings.", Toast.LENGTH_SHORT).show()
    }
}

