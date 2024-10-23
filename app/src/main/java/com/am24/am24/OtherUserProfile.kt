package com.am24.am24

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.rememberSwipeableState
import coil.compose.AsyncImage
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun OtherUserProfileScreen(
    navController: NavController,
    otherUserId: String,
    profileViewModel: ProfileViewModel = viewModel(), // Create ProfileViewModel instance here
    modifier: Modifier = Modifier
) {
    OtherUserProfileContent(
        navController = navController,
        userId = otherUserId,
        profileViewModel = profileViewModel, // Pass to content
        modifier = modifier
    )
}

@OptIn(ExperimentalWearMaterialApi::class)
@Composable
fun OtherUserProfileContent(
    navController: NavController,
    userId: String,
    profileViewModel: ProfileViewModel, // Add ProfileViewModel parameter
    modifier: Modifier = Modifier
) {
    val profile = remember { mutableStateOf(Profile()) }
    var currentPhotoIndex by remember { mutableStateOf(0) }
    var showFullScreenMedia by remember { mutableStateOf(false) }

    val firebaseDatabase = FirebaseDatabase.getInstance()
    val userRef = firebaseDatabase.getReference("users").child(userId)

    // Load the other user's profile data
    LaunchedEffect(userId) {
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                profile.value = snapshot.getValue(Profile::class.java) ?: Profile()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to fetch user profile: ${error.message}")
            }
        })
    }

    // Swipeable logic for the entire profile
    val swipeableState = rememberSwipeableState(initialValue = 0)
    val anchors = mapOf(
        -300f to -1, // Swiped left
        0f to 0,     // Neutral position
        300f to 1    // Swiped right
    )

    val coroutineScope = rememberCoroutineScope()

    // Action to handle swipe completion
    LaunchedEffect(swipeableState.currentValue) {
        when (swipeableState.currentValue) {
            -1 -> {
                // Handle swipe left action
                profileViewModel.swipeLeft(userId, profile.value.userId)
                navController.navigate("datingScreen") // Navigate after swipe
                swipeableState.snapTo(0) // Reset state
            }
            1 -> {
                // Handle swipe right action
                profileViewModel.swipeRight(userId, profile.value.userId)
                navController.navigate("datingScreen") // Navigate after swipe
                swipeableState.snapTo(0) // Reset state
            }
        }
    }

    // Scrollable content for other user's profile
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 1. Profile Media Section (Profile photo, video, username, age, etc.)
        ProfileMediaSection(
            profile = profile.value,
            currentPhotoIndex = currentPhotoIndex,
            onPhotoIndexChanged = { index -> currentPhotoIndex = index },
            navController = navController,
            showFullScreenMedia = showFullScreenMedia,
            onFullscreenClick = { showFullScreenMedia = true },
            isOtherUserProfile = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Profile Actions Section (Follow, Report, Upvote/Downvote, etc.)
        ProfileActionsSection(
            navController = navController,
            userId = userId,
            profile = profile.value,
            profileViewModel = profileViewModel,
            onSwipeLeft = {
                coroutineScope.launch { swipeableState.animateTo(-1) }
            },
            onSwipeRight = {
                coroutineScope.launch { swipeableState.animateTo(1) }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Details Section (Gender, username, community, religion, zodiac, etc.)
        ProfileDetailsSection(profile = profile.value)

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Bio and Voice Bio Section (Bio and Voice Note)
        ProfileBioVoiceSection(profile = profile.value)

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Location Section (City, hometown, country)
        ProfileLocationSection(profile = profile.value)

        Spacer(modifier = Modifier.height(16.dp))

        // 6. Lifestyle Section (Lifestyle details and what the user is looking for)
        ProfileLifestyleSection(profile = profile.value)

        Spacer(modifier = Modifier.height(16.dp))

        // 7. Interests Section (User's interests)
        ProfileInterestsSection(profile = profile.value)

        Spacer(modifier = Modifier.height(16.dp))

        // 8. Metrics Section (Rankings, swipe ratios, etc.)
        ProfileMetricsSection(profile = profile.value)

        // Fullscreen media dialog
        if (showFullScreenMedia) {
            FullscreenMediaDialog(
                profile = profile.value,
                currentPhotoIndex = currentPhotoIndex,
                onDismiss = { showFullScreenMedia = false }
            )
        }
    }
}

@Composable
fun ProfileMediaSection(
    profile: Profile,
    currentPhotoIndex: Int,
    onPhotoIndexChanged: (Int) -> Unit,
    navController: NavController,
    showFullScreenMedia: Boolean,
    onFullscreenClick: () -> Unit,
    isOtherUserProfile: Boolean
) {
    val context = LocalContext.current

    // Media Section logic with photos, video, and fullscreen icons
    val photoUrls = listOfNotNull(profile.profilepicUrl) + profile.optionalPhotoUrls
    val videoUrl = profile.videoUrl

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        if (photoUrls.isNotEmpty()) {
            AsyncImage(
                model = photoUrls[currentPhotoIndex],
                contentDescription = "Profile Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            // Fullscreen Icon for images
            FullscreenIcon(onClick = onFullscreenClick)
        }

        // Video Bio logic
        videoUrl?.let {
            FullscreenIcon(onClick = onFullscreenClick)
        }

        if (!isOtherUserProfile) {
            // Edit profile icon for the user's own profile
            IconButton(onClick = { navController.navigate("editProfile") }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Profile",
                    tint = Color.White
                )
            }
        }
    }

    // Name, age, rating, and composite score
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "${profile.username}, ${calculateAge(profile.dob)}",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Color.White
        )
        RatingBar(rating = profile.rating)
        Text(
            text = "Composite Score: ${profile.am24RankingCompositeScore}",
            fontSize = 16.sp,
            color = Color.White
        )
    }
}

@Composable
fun FullscreenMediaDialog(
    profile: Profile,
    currentPhotoIndex: Int,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val photoUrls = listOfNotNull(profile.profilepicUrl) + profile.optionalPhotoUrls
            if (photoUrls.isNotEmpty()) {
                AsyncImage(
                    model = photoUrls[currentPhotoIndex],
                    contentDescription = "Fullscreen Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Fullscreen",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun RatingBar(rating: Double) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = String.format("%.1f", rating),
            color = Color.Yellow,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        repeat(5) { index ->
            Icon(
                imageVector = if (index < rating.toInt()) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = Color.Yellow
            )
        }
    }
}


// 3. Details Section (Gender, username, community, religion, zodiac, etc.)
@Composable
fun ProfileDetailsSection(profile: Profile) {
    FeedItemCard {
        Column {
            ProfileText(label = "Gender", value = profile.gender)
            ProfileText(label = "Username", value = profile.username)
            ProfileText(label = "Community", value = profile.community)
            ProfileText(label = "Religion", value = profile.religion)
            ProfileText(label = "Zodiac", value = deriveZodiac(profile.dob))
            ProfileText(label = "High School", value = profile.highSchool.takeIf { it.isNotBlank() } ?: "N/A")
            ProfileText(label = "College", value = profile.college.takeIf { it.isNotBlank() } ?: "N/A")
            ProfileText(label = "Post-Graduation", value = profile.postGraduation.takeIf { it!!.isNotBlank() } ?: "N/A")
            ProfileText(label = "Job Role", value = profile.jobRole.takeIf { it.isNotBlank() } ?: "N/A")
            ProfileText(label = "KupidX Score", value = String.format("%.1f", profile.rating))
        }
    }
}


// 4. Bio and Voice Bio Section (Bio and Voice Note)
@Composable
fun ProfileBioVoiceSection(profile: Profile) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var downloading by remember { mutableStateOf(false) } // Track if the file is being downloaded

    FeedItemCard {
        Column {
            ProfileText(label = "Bio", value = profile.bio)
            Spacer(modifier = Modifier.height(8.dp))

            // Voice Note Playback Section
            profile.voiceNoteUrl?.let { voiceUrl ->
                Text("Voice Bio:", color = Color(0xFF00bf63))

                // Playback button and downloading indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                mediaPlayer?.pause()
                                isPlaying = false
                            } else {
                                if (mediaPlayer == null) {
                                    // Start downloading and play the audio
                                    downloading = true
                                    downloadAndPlayVoice(context, voiceUrl) { mediaPlayerInstance ->
                                        mediaPlayer = mediaPlayerInstance
                                        isPlaying = true
                                        downloading = false
                                        // Set the completion listener to stop the player when the voice note ends
                                        mediaPlayer?.setOnCompletionListener {
                                            isPlaying = false
                                            playbackProgress = 0f
                                        }
                                    }
                                } else {
                                    mediaPlayer?.start()
                                    isPlaying = true
                                }
                            }
                        }
                    ) {
                        if (downloading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color(0xFF00bf63),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = playbackProgress,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        color = Color(0xFF00bf63),
                        trackColor = Color.Gray
                    )
                }
            }
        }
    }

    // Manage voice playback progress
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

    // Clean up the MediaPlayer when the Composable leaves the screen
    DisposableEffect(profile.voiceNoteUrl) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
}

fun downloadAndPlayVoice(context: Context, voiceUrl: String, onDownloadComplete: (MediaPlayer) -> Unit) {
    val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(voiceUrl)

    val localFile = File.createTempFile("tempVoice", ".aac", context.cacheDir) // Ensure the file is created in the app's cache directory

    storageRef.getFile(localFile).addOnSuccessListener {
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(localFile.absolutePath) // Play the downloaded local file
            prepare()
            start()
        }
        onDownloadComplete(mediaPlayer)
    }.addOnFailureListener {
        Log.e("DownloadError", "Failed to download voice file: ${it.message}")
        Toast.makeText(context, "Failed to play the voice note.", Toast.LENGTH_SHORT).show()
    }
}


// 5. Location Section (City, hometown, country)
@Composable
fun ProfileLocationSection(profile: Profile) {
    FeedItemCard {
        Column {
            ProfileText(label = "Current City", value = profile.city.takeIf { it.isNotBlank() } ?: profile.customCity ?: "N/A")
            ProfileText(label = "Hometown", value = profile.hometown.takeIf { it.isNotBlank() } ?: profile.customHometown ?: "N/A")
            ProfileText(label = "Country", value = profile.country.takeIf { it.isNotBlank() } ?: "N/A")
        }
    }
}

// 6. Lifestyle Section (Lifestyle details and what the user is looking for)
@Composable
fun ProfileLifestyleSection(profile: Profile) {
    FeedItemCard {
        Column {
            profile.lifestyle?.let { lifestyle ->
                ProfileText(label = "Smoking", value = lifestyle.smoking)
                ProfileText(label = "Drinking", value = lifestyle.drinking)
                ProfileText(label = "Alcohol Type", value = lifestyle.alcoholType)
                ProfileText(label = "Cannabis Friendly", value = if (lifestyle.cannabisFriendly) "Yes" else "No")
                ProfileText(label = "Laid Back", value = if (lifestyle.laidBack) "Yes" else "No")
                ProfileText(label = "Social Butterfly", value = if (lifestyle.socialButterfly) "Yes" else "No")
                ProfileText(label = "Diet", value = lifestyle.diet)
                ProfileText(label = "Sleep Cycle", value = lifestyle.sleepCycle)
                ProfileText(label = "Work-Life Balance", value = lifestyle.workLifeBalance)
                ProfileText(label = "Exercise Frequency", value = lifestyle.exerciseFrequency)
                ProfileText(label = "Adventurous", value = if (lifestyle.adventurous) "Yes" else "No")
                ProfileText(label = "Pet Friendly", value = if (lifestyle.petFriendly) "Yes" else "No")
            }

            ProfileText(label = "Looking For", value = profile.lookingFor.takeIf { it.isNotBlank() } ?: "N/A")
        }
    }
}


// 7. Interests Section (User's interests)
@Composable
fun ProfileInterestsSection(profile: Profile) {
    FeedItemCard {
        Column {
            Text(text = "Interests", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            if (profile.interests.isNotEmpty()) {
                profile.interests.forEach { interest ->
                    Text(
                        text = "${interest.emoji ?: ""} ${interest.name}",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                Text(text = "No interests specified", color = Color.Gray)
            }
        }
    }
}


// 8. Metrics Section (Rankings, swipe ratios, etc.)
@Composable
fun ProfileMetricsSection(profile: Profile) {
    FeedItemCard {
        Column {
            ProfileText(label = "Global Ranking", value = profile.am24RankingGlobal.toString())
            ProfileText(label = "Age Ranking", value = profile.am24RankingAge.toString())
            ProfileText(label = "High School Ranking", value = profile.am24RankingHighSchool.toString())
            ProfileText(label = "College Ranking", value = profile.am24RankingCollege.toString())
            ProfileText(label = "Gender Ranking", value = profile.am24RankingGender.toString())
            ProfileText(label = "Hometown Ranking", value = profile.am24RankingHometown.toString())
            ProfileText(label = "Country Ranking", value = profile.am24RankingCountry.toString())
            ProfileText(label = "City Ranking", value = profile.am24RankingCity.toString())

            Spacer(modifier = Modifier.height(8.dp))

            ProfileText(label = "Swipe Right to Left Ratio", value = String.format("%.2f", profile.getCalculatedSwipeRightToLeftRatio()))
            ProfileText(label = "Match Count per Swipe Right", value = String.format("%.2f", profile.getCalculatedMatchCountPerSwipeRight()))

            Spacer(modifier = Modifier.height(8.dp))

            ProfileText(label = "Cumulative Upvotes", value = profile.cumulativeUpvotes.toString())
            ProfileText(label = "Cumulative Downvotes", value = profile.cumulativeDownvotes.toString())
            ProfileText(label = "Average Upvotes per Post", value = String.format("%.2f", profile.averageUpvoteCount))
            ProfileText(label = "Average Downvotes per Post", value = String.format("%.2f", profile.averageDownvoteCount))

            Spacer(modifier = Modifier.height(8.dp))

            ProfileText(label = "Date Joined", value = formatDate(profile.dateOfJoin))
        }
    }
}

@Composable
fun FullscreenIcon(onClick: () -> Unit) {
    Box {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Fullscreen",
                tint = Color.White
            )
        }
    }
}

@Composable
fun FeedItemCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(Color.Black),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(2.dp, Color(0xFF00bf63)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun ProfileActionsSection(
    navController: NavController,
    userId: String,
    profile: Profile,
    profileViewModel: ProfileViewModel,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit// Inject the ViewModel
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Swipe Left (Cross)
        IconButton(
            onClick = onSwipeLeft,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color.Red)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Swipe Left",
                tint = Color.White
            )
        }

        // Swipe Right (Tick)
        IconButton(
            onClick = onSwipeRight,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color.Green)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Swipe Right",
                tint = Color.White
            )
        }

        // Upvote logic
        IconButton(
            onClick = {
                profileViewModel.upvoteProfile(
                    profileId = profile.userId,
                    userId = userId,
                    onSuccess = { /* Handle success */ },
                    onFailure = { /* Handle error */ }
                )
            }
        ) {
            Icon(
                imageVector = Icons.Default.ThumbUp,
                contentDescription = "Upvote",
                tint = if (profile.profileUpvotes.contains(userId)) Color.Green else Color.White
            )
        }

        // Downvote logic
        IconButton(
            onClick = {
                profileViewModel.downvoteProfile(
                    profileId = profile.userId,
                    userId = userId,
                    onSuccess = { /* Handle success */ },
                    onFailure = { /* Handle error */ }
                )
            }
        ) {
            Icon(
                imageVector = Icons.Default.ThumbDown,
                contentDescription = "Downvote",
                tint = if (profile.profileDownvotes.contains(userId)) Color.Red else Color.White
            )
        }

        // Report logic
        IconButton(
            onClick = {
                profileViewModel.reportProfile(
                    profileId = profile.userId,
                    reporterId = userId,
                    onSuccess = { /* Handle success */ },
                    onFailure = { /* Handle error */ }
                )
            }
        ) {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = "Report",
                tint = Color.Yellow
            )
        }

        // Block logic
        IconButton(
            onClick = {
                profileViewModel.blockProfile(
                    currentUserId = userId,
                    targetUserId = profile.userId,
                    onSuccess = { /* Handle success */ },
                    onFailure = { /* Handle error */ }
                )
            }
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = "Block",
                tint = Color.Red
            )
        }
    }
}
