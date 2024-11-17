// VoicePostComposable.kt
package com.am24.am24

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicePostComposable(
    navController: NavController,
    postViewModel: PostViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State variables
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var recordedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var tagsInput by remember { mutableStateOf("") }
    var recordingFilePath by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }


    val userId = FirebaseAuth.getInstance().currentUser?.uid
    // Fetch username from the database
    LaunchedEffect(userId) {
        username = userId?.let { fetchUsernameById(it) } ?: "Anonymous"
    }

    // MediaRecorder and MediaPlayer instances
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Permissions
    val recordAudioPermissionState = remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            recordAudioPermissionState.value = isGranted
            if (!isGranted) {
                Toast.makeText(
                    context,
                    "Audio recording permission is required.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )

    var playbackProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying && mediaPlayer != null) {
            while (isPlaying && mediaPlayer?.isPlaying == true) {
                delay(500L)
                val current = mediaPlayer?.currentPosition ?: 0
                val duration = mediaPlayer?.duration ?: 1
                playbackProgress = current.toFloat() / duration.toFloat()
            }
        } else {
            playbackProgress = 0f
        }
    }

    // Helper functions defined within the composable
    fun startRecording() {
        mediaRecorder = MediaRecorder().apply {
            val fileName = "VOICE_${System.currentTimeMillis()}.3gp"
            val file = File(context.cacheDir, fileName)
            recordingFilePath = file.absolutePath

            try {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(recordingFilePath)
                prepare()
                start()
                isRecording = true
                Toast.makeText(context, "Recording started...", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(context, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun stopRecording() {
        mediaRecorder?.apply {
            try {
                stop()
                Toast.makeText(context, "Recording stopped.", Toast.LENGTH_SHORT).show()
            } catch (e: RuntimeException) {
                e.printStackTrace()
                Toast.makeText(context, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            release()
        }
        mediaRecorder = null

        // Convert the recorded file path to Uri
        val file = File(recordingFilePath)
        if (file.exists()) {
            recordedAudioUri = Uri.fromFile(file)
        } else {
            Toast.makeText(context, "Recording file not found.", Toast.LENGTH_SHORT).show()
            recordedAudioUri = null
        }
        isRecording = false
    }

    fun playAudio(uri: Uri) {
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(context, uri)
                prepare()
                start()
                isPlaying = true
                Toast.makeText(context, "Playback started...", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(context, "Playback failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }

            setOnCompletionListener {
                release()
                mediaPlayer = null
                isPlaying = false
                Toast.makeText(context, "Playback completed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun pauseAudio() {
        mediaPlayer?.pause()
        isPlaying = false
        Toast.makeText(context, "Playback paused.", Toast.LENGTH_SHORT).show()
    }

    fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Voice Post", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        // Validate input
                        if (recordedAudioUri == null) {
                            Toast.makeText(
                                context,
                                "Please record a voice message.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@TextButton
                        }

                        if (userId == null) {
                            Toast.makeText(context, "User not authenticated.", Toast.LENGTH_SHORT)
                                .show()
                            return@TextButton
                        }

                        // Convert tags string to list
                        val tagsList =
                            tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                        postViewModel.createVoicePost(
                            userId = userId,
                            username = username,
                            voiceUri = recordedAudioUri!!,
                            userTags = tagsList,
                            onSuccess = {
                                coroutineScope.launch {
                                    Toast.makeText(
                                        context,
                                        "Voice post created successfully.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    navController.popBackStack()
                                }
                            },
                            onFailure = { error ->
                                coroutineScope.launch {
                                    Toast.makeText(
                                        context,
                                        "Failed to create post: $error",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    },
                        enabled = recordedAudioUri != null,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (recordedAudioUri != null) Color(0xFFFFA500) else Color.Gray
                        )
                        ) {
                        Text("Post", color = Color(0xFFFF4500))
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top
            ) {
                // Record/Stop Button
                Button(
                    onClick = {
                        if (isRecording) {
                            stopRecording()
                        } else {
                            // Check permission
                            if (!recordAudioPermissionState.value) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                startRecording()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color(0xFFFF4500) else Color(0xFFFFA500)
                    )
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (isRecording) "Stop Recording" else "Start Recording",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Recording Duration Display
                var recordingDuration by remember { mutableStateOf(0) }

                if (isRecording) {
                    LaunchedEffect(isRecording) {
                        while (isRecording) {
                            delay(1000L)
                            recordingDuration += 1
                        }
                    }

                    Text(
                        text = "Recording: ${formatDuration(recordingDuration)}",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Playback Controls
                if (recordedAudioUri != null) {
                    PlaybackControls(
                        isPlaying = isPlaying,
                        onPlayPause = {
                            if (isPlaying) {
                                pauseAudio()
                            } else {
                                playAudio(recordedAudioUri!!)
                            }
                        },
                        onReRecord = {
                            // Reset state to allow re-recording
                            recordedAudioUri = null
                            tagsInput = ""
                        },
                        progress = playbackProgress
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Tags Input
                OutlinedTextField(
                    value = tagsInput,
                    onValueChange = { tagsInput = it },
                    label = { Text("Add Tags (comma separated)", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF4500),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFFFF4500)
                    )
                )
            }
        }
    )
    /**
     * Clean up MediaPlayer and MediaRecorder when composable is disposed
     */
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }
}

/**
 * Playback Controls Composable
 */
@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onReRecord: () -> Unit,
    progress: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1C1C))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Play/Pause Button
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color(0xFFFF4500),
                    modifier = Modifier.size(48.dp)
                )
            }

            // Re-record Button
            IconButton(onClick = onReRecord) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Re-record",
                    tint = Color(0xFFFF4500),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Progress Bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = Color(0xFFFFA500),
            trackColor = Color.Gray,
        )
    }
}




