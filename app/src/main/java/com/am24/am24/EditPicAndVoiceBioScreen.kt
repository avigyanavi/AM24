import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.am24.am24.Profile
import com.am24.am24.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage

import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPicAndVoiceBioScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    // A local "Profile?" from which we will load initial data.
    var profile by remember { mutableStateOf<Profile?>(null) }

    // Up to 5 images in memory. Each item is either a string (Firebase Storage URL) or empty "".
    val photoItems = remember { mutableStateListOf<String>() }

    // Voice note URL if present, else null.
    var voiceNoteUrl by remember { mutableStateOf<String?>(null) }

    // 1) Fetch the user’s profile once.
    LaunchedEffect(currentUserId) {
        profileViewModel.fetchUserProfile(
            userId = currentUserId,
            onSuccess = { fetchedProfile ->
                profile = fetchedProfile

                // Build up to 5 slots from profilepicUrl + optionalPhotoUrls
                val combined = mutableListOf<String>()
                fetchedProfile.profilepicUrl?.let { combined.add(it) }
                combined.addAll(fetchedProfile.optionalPhotoUrls)
                // ensure exactly 5
                while (combined.size < 5) combined.add("")
                if (combined.size > 5) {
                    combined.dropLast(combined.size - 5)
                }
                photoItems.clear()
                photoItems.addAll(combined.take(5))

                voiceNoteUrl = fetchedProfile.voiceNoteUrl
            },
            onFailure = { error ->
                Log.e("EditPic", "Failed to load profile: $error")
            }
        )
    }

    // If the profile is not loaded yet, show a loading spinner.
    if (profile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFF6F00))
        }
        return
    }

    // Minimal audio playback
    val mediaPlayer = remember { MediaPlayer() }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    fun togglePlayVoice() {
        // If voiceNoteUrl is blank, do nothing
        if (voiceNoteUrl.isNullOrEmpty()) return

        if (isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
        } else {
            mediaPlayer.reset()
            try {
                mediaPlayer.setDataSource(voiceNoteUrl)
                mediaPlayer.prepare()
                mediaPlayer.start()
                isPlaying = true
                // On completion, reset isPlaying
                mediaPlayer.setOnCompletionListener {
                    isPlaying = false
                }
            } catch (e: IOException) {
                Log.e("VoicePlay", "Error playing voice note: ${e.message}")
            }
        }
    }

    // 2) Image picking. We store which "slot index" the user clicked.
    var slotIndexToReplace by remember { mutableStateOf<Int?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Upload to Firebase => get the resulting URL => store in photoItems[slotIndex]
            val slotIdx = slotIndexToReplace
            if (slotIdx == null) return@let // no valid slot
            uploadImageToFirebase(
                userId = currentUserId,
                imageUri = it,
                onSuccess = { downloadUrl ->
                    photoItems[slotIdx] = downloadUrl
                },
                onFailure = { err ->
                    Log.e("PhotoUpload", "Failed: $err")
                }
            )
        }
    }

    // 3) Voice picking
    val pickVoiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            uploadVoiceToFirebase(
                userId = currentUserId,
                voiceUri = it,
                onSuccess = { dlUrl ->
                    voiceNoteUrl = dlUrl
                },
                onFailure = { err ->
                    Log.e("VoiceUpload", "Failed: $err")
                }
            )
        }
    }

    // 4) Save function
    fun onSave() {
        // Gather non-empty photo URLs
        val nonEmpty = photoItems.filter { it.isNotBlank() }
        val mainPic = nonEmpty.firstOrNull()
        val others = if (nonEmpty.size > 1) nonEmpty.drop(1) else emptyList()

        // Build updated profile
        val updatedProfile = profile!!.copy(
            profilepicUrl = mainPic,
            optionalPhotoUrls = others,
            voiceNoteUrl = voiceNoteUrl
        )

        // Save to Firebase (Realtime DB)
        profileViewModel.saveProfileUpdated(
            updatedProfile = updatedProfile,
            onSuccess = {
                // Navigate back
                navController.navigateUp()
            },
            onFailure = { e ->
                Log.e("EditPic", "Failed to update: $e")
            }
        )
    }

    // UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Pictures & Voice Bio", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Tap on empty slot to add photo. Use Up/Down to reorder. First photo is your main pic.",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Show the 5 slots in a row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                photoItems.forEachIndexed { index, url ->
                    // Each slot is 100x100
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                    ) {
                        if (url.isBlank()) {
                            // Empty
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, Color.Gray, RectangleShape)
                                    .clickable {
                                        slotIndexToReplace = index
                                        pickImageLauncher.launch("image/*")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", color = Color.Gray, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Non-empty. Show the image plus reorder + remove icons
                            AsyncImage(
                                model = url,
                                contentDescription = "Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Remove icon in top-right corner
                            IconButton(
                                onClick = {
                                    // If this is the only photo => do not remove
                                    val countNonEmpty = photoItems.count { it.isNotBlank() }
                                    if (countNonEmpty == 1 && index == photoItems.indexOfFirst { it.isNotBlank() }) {
                                        // can't remove the only photo
                                        Log.w("EditPic", "Cannot remove the only photo.")
                                    } else {
                                        photoItems[index] = ""
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, "Remove", tint = Color.Red)
                            }

                            // Reorder icons (Up = move left, Down = move right for a row)
                            // We'll place them along the bottom left
                            if (index > 0) {
                                IconButton(
                                    onClick = {
                                        // swap items at index & index-1
                                        val temp = photoItems[index - 1]
                                        photoItems[index - 1] = photoItems[index]
                                        photoItems[index] = temp
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(24.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Left", tint = Color.White)
                                }
                            }
                            if (index < photoItems.lastIndex) {
                                IconButton(
                                    onClick = {
                                        // swap items at index & index+1
                                        val temp = photoItems[index + 1]
                                        photoItems[index + 1] = photoItems[index]
                                        photoItems[index] = temp
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .offset(x = 32.dp, y = (-4).dp)
                                        .size(24.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Right", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Voice Bio
            Text(
                text = "Voice Bio",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (voiceNoteUrl.isNullOrEmpty()) {
                Text(
                    text = "You have no recorded voice bio yet. Tap below to upload/record one.",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Button(
                        onClick = {
                            pickVoiceLauncher.launch("audio/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                    ) {
                        Text("Upload Voice", color = Color.White)
                    }
                }
            } else {
                // Show a mini player with play/pause
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { togglePlayVoice() }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Toggle Playback",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    // Remove voice icon
                    IconButton(onClick = { voiceNoteUrl = null }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Remove Voice", tint = Color.Red)
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Bottom row: Cancel / Save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { navController.navigateUp() },
                    colors = ButtonDefaults.buttonColors(Color.Gray)
                ) {
                    Text("Cancel", color = Color.White)
                }
                Button(
                    onClick = { onSave() },
                    colors = ButtonDefaults.buttonColors(Color(0xFF00bf63))
                ) {
                    Text("Save", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** Upload image to Firebase Storage, returning the final Storage download URL via onSuccess. */
private fun uploadImageToFirebase(
    userId: String,
    imageUri: Uri,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit
) {
    val fileName = "${System.currentTimeMillis()}.jpg"
    val userImageRef = FirebaseStorage.getInstance().reference.child("users/$userId/$fileName")

    userImageRef.putFile(imageUri)
        .addOnSuccessListener { snapshot ->
            snapshot.storage.downloadUrl
                .addOnSuccessListener { downloadUrl ->
                    onSuccess(downloadUrl.toString())
                }
                .addOnFailureListener { e ->
                    onFailure("Failed to get download URL: ${e.message}")
                }
        }
        .addOnFailureListener { e ->
            onFailure("Upload failed: ${e.message}")
        }
}

/** Upload voice file (audio) to Firebase Storage. */
private fun uploadVoiceToFirebase(
    userId: String,
    voiceUri: Uri,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit
) {
    val fileName = "voice_${System.currentTimeMillis()}.mp3"
    val voiceRef = FirebaseStorage.getInstance().reference.child("users/$userId/$fileName")

    voiceRef.putFile(voiceUri)
        .addOnSuccessListener { task ->
            task.storage.downloadUrl.addOnSuccessListener { dl ->
                onSuccess(dl.toString())
            }.addOnFailureListener { e ->
                onFailure("Failed to get voice download URL: ${e.message}")
            }
        }
        .addOnFailureListener { e ->
            onFailure("Failed to upload voice: ${e.message}")
        }
}
