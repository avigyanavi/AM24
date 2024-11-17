package com.am24.am24

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.firebase.geofire.GeoFire
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun OtherUserProfileScreen(
    navController: NavController,
    otherUserId: String,
    currentUserId: String,
    currentUserName: String,
    geoFire: GeoFire,
    profileViewModel: ProfileViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    OtherUserProfileContent(
        navController = navController,
        userId = otherUserId,
        currentUserId = currentUserId,
        currentUserName = currentUserName,
        geoFire = geoFire,
        profileViewModel = profileViewModel,
        modifier = modifier
    )
}

@Composable
fun OtherUserProfileContent(
    navController: NavController,
    userId: String,            // Target user's ID
    currentUserId: String,     // Current user's ID
    currentUserName: String,
    profileViewModel: ProfileViewModel,
    geoFire: GeoFire,
    modifier: Modifier = Modifier
) {
    val profile = remember { mutableStateOf(Profile()) }
    var currentPhotoIndex by remember { mutableStateOf(0) }
    var showFullScreenMedia by remember { mutableStateOf(false) }
    var userDistance by remember { mutableStateOf<Float?>(null) }

    // Load the other user's profile data
    LaunchedEffect(userId) {
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                profile.value = snapshot.getValue(Profile::class.java) ?: Profile()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to fetch user profile: ${error.message}")
            }
        })
    }

    // Calculate distance between users
    LaunchedEffect(userId) {
        if (currentUserId.isNotEmpty() && userId.isNotEmpty()) {
            val distance = calculateDistance(currentUserId, userId, geoFire)
            userDistance = distance
        }
    }

    // Scrollable content for other user's profile
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ProfileMediaSection(
            profile = profile.value,
            currentPhotoIndex = currentPhotoIndex,
            onPhotoIndexChanged = { index -> currentPhotoIndex = index },
            onFullscreenClick = { showFullScreenMedia = true },
            navController = navController
        )

        // Display user distance and rating bar at the top
        userDistance?.let { distance ->
            Text(
                text = "${distance.roundToInt()} km away",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Name, Age, Rating, Composite Score
        Text(
            text = "${profile.value.name}, ${calculateAge(profile.value.dob)}",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Color.White
        )
        RatingBar(rating = profile.value.averageRating)
        Text(
            text = "Vibe Score: ${profile.value.vibepoints}",
            fontSize = 16.sp,
            color = Color.White
        )

        // Other sections (ProfileActionsSection, ProfileDetailsSection, etc.)
        Spacer(modifier = Modifier.height(16.dp))

        // Updated call to ProfileActionsSection with correct IDs
        ProfileActionsSection(
            currentUserId = currentUserId,   // Corrected
            targetUserId = userId,           // Corrected
            profile = profile.value,
            profileViewModel = profileViewModel
        )

        Spacer(modifier = Modifier.height(16.dp))
        ProfileDetailsSection(profile.value)
        Spacer(modifier = Modifier.height(16.dp))
        ProfileBioVoiceSection(profile.value)
        Spacer(modifier = Modifier.height(16.dp))
        ProfileLocationSection(profile.value)
        Spacer(modifier = Modifier.height(16.dp))
        ProfileLifestyleSection(profile.value)
        Spacer(modifier = Modifier.height(16.dp))
        ProfileInterestsSection(profile.value)
        Spacer(modifier = Modifier.height(16.dp))
        ProfileMetricsSection(profile.value)

        // Handle full-screen media dialog
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
fun ProfileActionsSection(
    currentUserId: String,     // Corrected
    targetUserId: String,      // Corrected
    profile: Profile,
    profileViewModel: ProfileViewModel
) {
    val friendStatus = remember { mutableStateOf("not_requested") }
    val dynamicUsername = remember { mutableStateOf("") }

    // Fetch the friend request status
    LaunchedEffect(targetUserId) {
        profileViewModel.getFriendRequestStatus(
            currentUserId = currentUserId,   // Corrected
            targetUserId = targetUserId,     // Corrected
            onStatusRetrieved = { status ->
                friendStatus.value = status
            },
            onFailure = {
                friendStatus.value = "not_requested"
            }
        )
    }

    // Fetch the current user's username
    LaunchedEffect(currentUserId) {
        profileViewModel.fetchUsernameById(
            currentUserId,
            onSuccess = { username -> dynamicUsername.value = username },
            onFailure = { dynamicUsername.value = "Unknown" }
        )
    }

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Friend Request Button
        IconButton(
            onClick = {
                when (friendStatus.value) {
                    "not_requested" -> {
                        profileViewModel.sendFriendRequest(
                            currentUserId = currentUserId,   // Corrected
                            targetUserId = targetUserId,     // Corrected
                            onSuccess = {
                                friendStatus.value = "requested"
                            },
                            onFailure = { /* Handle error */ }
                        )
                    }

                    "requested" -> {
                        profileViewModel.rejectFriendRequest(
                            currentUserId = currentUserId,   // Corrected
                            requesterId = targetUserId,      // Corrected
                            onSuccess = {
                                friendStatus.value = "not_requested"
                            },
                            onFailure = { /* Handle error */ }
                        )
                    }

                    "accepted" -> {
                        profileViewModel.removeFriend(
                            currentUserId = currentUserId,   // Corrected
                            targetUserId = targetUserId,     // Corrected
                            onSuccess = {
                                friendStatus.value = "not_requested"
                            },
                            onFailure = { /* Handle error */ }
                        )
                    }
                }
            },
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    when (friendStatus.value) {
                        "accepted" -> Color.Red
                        "requested" -> Color.Gray
                        else -> Color(0xFF00bf63)
                    }
                )
        ) {
            Icon(
                imageVector = when (friendStatus.value) {
                    "accepted" -> Icons.Default.PersonRemove
                    "requested" -> Icons.Default.Pending
                    else -> Icons.Default.PersonAdd
                },
                contentDescription = when (friendStatus.value) {
                    "accepted" -> "Remove Friend"
                    "requested" -> "Withdraw Request"
                    else -> "Add Friend"
                },
                tint = Color.White
            )
        }

        // Report Profile Button
        IconButton(
            onClick = {
                profileViewModel.reportProfile(
                    profileId = targetUserId,      // Corrected
                    reporterId = currentUserId,    // Corrected
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

        // Block Profile Button
        IconButton(
            onClick = {
                profileViewModel.blockProfile(
                    currentUserId = currentUserId, // Corrected
                    targetUserId = targetUserId,   // Corrected
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

@Composable
fun ProfileMediaSection(
    profile: Profile,
    currentPhotoIndex: Int,
    onPhotoIndexChanged: (Int) -> Unit,
    onFullscreenClick: () -> Unit,
    navController: NavController
) {
    val photoUrls = listOfNotNull(profile.profilepicUrl) + profile.optionalPhotoUrls

    // Handle tap to change photos
    val totalMediaCount = photoUrls.size

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable {
                // Move to the next photo or video when tapped
                val nextIndex = (currentPhotoIndex + 1) % totalMediaCount
                onPhotoIndexChanged(nextIndex)
            }
    ) {
        when {
            currentPhotoIndex < photoUrls.size -> {
                AsyncImage(
                    model = photoUrls[currentPhotoIndex],
                    contentDescription = "Profile Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Navigation bars above photos
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            photoUrls.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(20.dp, 4.dp)
                        .padding(horizontal = 2.dp)
                        .clip(CircleShape)
                        .background(if (index == currentPhotoIndex) Color.White else Color.Gray)
                )
            }
        }

        // Fullscreen Icon
        FullscreenIcon(onClick = onFullscreenClick)
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
        repeat(5) { index ->
            Icon(
                imageVector = if (index < rating.toInt()) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = Color(0xFFFF4500)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = String.format("%.1f", rating),
            color = Color(0xFFFF4500),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
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
            ProfileText(label = "Total Rating", value = String.format("%.1f", profile.averageRating))
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
                Text("Voice Bio:", color = Color.White)

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
                                tint = Color(0xFFFF4500),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = playbackProgress,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        color =Color(0xFFFF4500),
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
        }
    }
}

// 6. Lifestyle Section (Lifestyle details and what the user is looking for)
@Composable
fun ProfileLifestyleSection(profile: Profile) {
    FeedItemCard {
        Column {
            profile.lifestyle?.let { lifestyle ->

                ProfileText(label = "Smoking", value = when (lifestyle.smoking) {
                    in 0..2 -> "Non-Smoker"
                    in 3..6 -> "Social Smoker"
                    else -> "Regular Smoker"
                })

                ProfileText(label = "Drinking", value = when (lifestyle.drinking) {
                    in 0..2 -> "Non-Drinker"
                    in 3..6 -> "Occasional Drinker"
                    else -> "Heavy Drinker"
                })

                ProfileText(label = "Alcohol Type", value = lifestyle.alcoholType.ifBlank { "None" })

                ProfileText(label = "Cannabis Friendly", value = if (lifestyle.cannabisFriendly) "Yes" else "No")

                ProfileText(label = "Indoorsy to Outdoorsy", value = when (lifestyle.indoorsyToOutdoorsy) {
                    in 0..2 -> "Homebody"
                    in 3..6 -> "Balanced"
                    else -> "Outdoorsy"
                })

                ProfileText(label = "Social Butterfly", value = when (lifestyle.socialButterfly) {
                    in 0..2 -> "Introverted"
                    in 3..6 -> "Ambivert"
                    else -> "Extroverted"
                })

                ProfileText(label = "Diet", value = lifestyle.diet)

                ProfileText(label = "Sleep Cycle", value = when (lifestyle.sleepCycle) {
                    in 0..2 -> "Early Riser"
                    in 3..6 -> "Balanced"
                    else -> "Night Owl"
                })

                ProfileText(label = "Work-Life Balance", value = when (lifestyle.workLifeBalance) {
                    in 0..2 -> "Workaholic"
                    in 3..6 -> "Balanced"
                    else -> "Relaxed"
                })

                ProfileText(label = "Exercise Frequency", value = when (lifestyle.exerciseFrequency) {
                    in 0..2 -> "Never Exercises"
                    in 3..6 -> "Occasionally Exercises"
                    else -> "Exercises Daily"
                })

                ProfileText(label = "Adventurous", value = when (lifestyle.adventurous) {
                    in 0..2 -> "Cautious"
                    in 3..6 -> "Moderate"
                    else -> "Adventurous"
                })

                ProfileText(label = "Pet Friendly", value = if (lifestyle.petFriendly) "Yes" else "No")

                ProfileText(label = "Family Oriented", value = when (lifestyle.familyOriented) {
                    in 0..2 -> "Independent"
                    in 3..6 -> "Balanced"
                    else -> "Family-Oriented"
                })

                ProfileText(label = "Intellectual", value = when (lifestyle.intellectual) {
                    in 0..2 -> "Casual"
                    in 3..6 -> "Inquisitive"
                    else -> "Intellectual"
                })

                ProfileText(label = "Creative/Artistic", value = when (lifestyle.creativeArtistic) {
                    in 0..2 -> "Practical"
                    in 3..6 -> "Occasionally Creative"
                    else -> "Artistic"
                })

                ProfileText(label = "Health/Fitness Enthusiast", value = when (lifestyle.healthFitnessEnthusiast) {
                    in 0..2 -> "Occasional"
                    in 3..6 -> "Moderate"
                    else -> "Dedicated"
                })

                ProfileText(label = "Spiritual/Mindful", value = when (lifestyle.spiritualMindful) {
                    in 0..2 -> "Non-Spiritual"
                    in 3..6 -> "Occasionally Mindful"
                    else -> "Deeply Mindful"
                })

                ProfileText(label = "Humorous/Easy-Going", value = when (lifestyle.humorousEasyGoing) {
                    in 0..2 -> "Serious"
                    in 3..6 -> "Balanced"
                    else -> "Humorous"
                })

                ProfileText(label = "Professional/Ambitious", value = when (lifestyle.professionalAmbitious) {
                    in 0..2 -> "Relaxed"
                    in 3..6 -> "Balanced"
                    else -> "Ambitious"
                })

                ProfileText(label = "Environmentally Conscious", value = when (lifestyle.environmentallyConscious) {
                    in 0..2 -> "Not Conscious"
                    in 3..6 -> "Occasionally Conscious"
                    else -> "Eco-Conscious"
                })

                ProfileText(label = "Cultural Heritage-Oriented", value = when (lifestyle.culturalHeritageOriented) {
                    in 0..2 -> "Open-Minded"
                    in 3..6 -> "Balanced"
                    else -> "Culturally Rooted"
                })

                ProfileText(label = "Foodie/Culinary Enthusiast", value = when (lifestyle.foodieCulinaryEnthusiast) {
                    in 0..2 -> "Basic"
                    in 3..6 -> "Moderate"
                    else -> "Food Enthusiast"
                })

                ProfileText(label = "Urban Wanderer", value = when (lifestyle.urbanWanderer) {
                    in 0..2 -> "Homebody"
                    in 3..6 -> "Balanced"
                    else -> "City Explorer"
                })

                ProfileText(label = "Politically Aware", value = when (lifestyle.politicallyAware) {
                    in 0..2 -> "Not Interested"
                    in 3..6 -> "Aware"
                    else -> "Engaged"
                })

                ProfileText(label = "Community-Oriented", value = when (lifestyle.communityOriented) {
                    in 0..2 -> "Individualist"
                    in 3..6 -> "Balanced"
                    else -> "Community-Oriented"
                })
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
            ProfileText(label = "Age Ranking", value = profile.am24RankingAge.toString())
            ProfileText(label = "High School Ranking", value = profile.am24RankingHighSchool.toString())
            ProfileText(label = "College Ranking", value = profile.am24RankingCollege.toString())
            ProfileText(label = "Gender Ranking", value = profile.am24RankingGender.toString())
            ProfileText(label = "Hometown Ranking", value = profile.am24RankingHometown.toString())
            ProfileText(label = "Country Ranking", value = profile.am24Ranking.toString())
            ProfileText(label = "City Ranking", value = profile.am24RankingCity.toString())

            Spacer(modifier = Modifier.height(8.dp))

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
        border = BorderStroke(2.dp, Color(0xFFFF4500)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}












