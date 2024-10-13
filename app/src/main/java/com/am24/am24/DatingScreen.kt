// DatingScreen.kt

@file:OptIn(ExperimentalMaterialApi::class)

package com.am24.am24

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.runtime.snapshots.SnapshotStateList
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.getValue
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DatingScreen(navController: NavController, startUserId: String? = null, modifier: Modifier = Modifier) {
    DatingScreenContent(navController = navController, startUserId = startUserId, modifier = modifier)
}

@Composable
fun DatingScreenContent(navController: NavController, startUserId: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val profiles = remember { mutableStateListOf<Profile>() }
    var currentProfileIndex by remember { mutableStateOf(0) }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    val firebaseDatabase = FirebaseDatabase.getInstance()
    val usersRef = firebaseDatabase.getReference("users")
    val swipesRef = firebaseDatabase.getReference("swipes/$currentUserId")

    // Fetch profiles
    LaunchedEffect(Unit) {
        val currentTime = System.currentTimeMillis()
        val oneWeekAgo = currentTime - (7 * 24 * 60 * 60 * 1000) // 7 days in milliseconds
        val oneMonthAgo = currentTime - (30L * 24 * 60 * 60 * 1000) // 30 days in milliseconds

        // Fetch swipes to filter profiles
        swipesRef.get().addOnSuccessListener { swipesSnapshot ->
            val swipedUserIds = mutableSetOf<String>()
            for (swipe in swipesSnapshot.children) {
                val swipeData = swipe.getValue<Map<String, Any>>()
                val liked = swipeData?.get("liked") as? Boolean
                val timestamp = swipeData?.get("timestamp") as? Long ?: 0L
                val userId = swipe.key ?: continue

                if (liked == false && timestamp > oneMonthAgo) {
                    // Swiped left within last month, exclude
                    swipedUserIds.add(userId)
                } else if (liked == true && timestamp > oneWeekAgo) {
                    // Swiped right within last week, exclude
                    swipedUserIds.add(userId)
                }
                // Else, include in profiles to show
            }

            // Fetch profiles after getting swipedUserIds
            usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val fetchedProfiles = mutableListOf<Profile>()
                    for (userSnapshot in snapshot.children) {
                        val profile = userSnapshot.getValue(Profile::class.java)
                        if (profile != null && profile.userId != currentUserId && !swipedUserIds.contains(profile.userId)) {
                            fetchedProfiles.add(profile)
                        }
                    }
                    // Rearrange profiles to start with startUserId if provided
                    if (startUserId != null) {
                        val startProfileIndex =
                            fetchedProfiles.indexOfFirst { it.userId == startUserId }
                        if (startProfileIndex != -1) {
                            val startProfile = fetchedProfiles.removeAt(startProfileIndex)
                            fetchedProfiles.add(0, startProfile)
                        }
                    }
                    profiles.clear()
                    profiles.addAll(fetchedProfiles)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseError", "DatabaseError: ${error.message}")
                    Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        } .addOnFailureListener { exception ->
            Log.e("FirebaseError", "Failed to fetch swipes: ${exception.message}")
            // Proceed to fetch profiles without swipes data
//            fetchProfilesWithoutSwipes(
//                usersRef = usersRef,
//                currentUserId = currentUserId,
//                startUserId = startUserId,
//                profiles = profiles,
//                context = context
//            )
        }
    }

    // Ensure current profile index is valid
    if (profiles.isNotEmpty() && currentProfileIndex >= profiles.size) {
        currentProfileIndex = 0 // Reset to first profile
    }

    // Swipe logic and display current profile
    if (profiles.isNotEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val currentProfile = profiles[currentProfileIndex]

            DatingProfileCard(
                profile = currentProfile,
                onSwipeRight = {
                    handleSwipeRight(currentUserId, currentProfile.userId)
                    currentProfileIndex = (currentProfileIndex + 1) % profiles.size
                },
                onSwipeLeft = {
                    handleSwipeLeft(currentUserId, currentProfile.userId)
                    currentProfileIndex = (currentProfileIndex + 1) % profiles.size
                },
                navController = navController
            )
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No more profiles around you",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Re-adjust your filters or wait a while.",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        // Navigate to Filters Screen (to be implemented)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                ) {
                    Text(text = "Adjust Filters", color = Color.White)
                }
            }
        }
    }
}

fun fetchProfilesWithoutSwipes(
    usersRef: DatabaseReference,
    currentUserId: String,
    startUserId: String?,
    profiles: SnapshotStateList<Profile>,
    context: Context
) {
    usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val fetchedProfiles = mutableListOf<Profile>()
            for (userSnapshot in snapshot.children) {
                val profile = userSnapshot.getValue(Profile::class.java)
                if (profile != null && profile.userId != currentUserId) {
                    fetchedProfiles.add(profile)
                }
            }
            // Rearrange profiles to start with startUserId if provided
            if (startUserId != null) {
                val startProfileIndex = fetchedProfiles.indexOfFirst { it.userId == startUserId }
                if (startProfileIndex != -1) {
                    val startProfile = fetchedProfiles.removeAt(startProfileIndex)
                    fetchedProfiles.add(0, startProfile)
                }
            }
            profiles.clear()
            profiles.addAll(fetchedProfiles)
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e("FirebaseError", "DatabaseError: ${error.message}")
            Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    })
}


@Composable
fun DatingProfileCard(
    profile: Profile,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    navController: NavController
) {
    var currentPhotoIndex by remember(profile) { mutableStateOf(0) }
    var showDetailsAndMetrics by remember { mutableStateOf(false) }
    var showFullScreenImage by remember { mutableStateOf(false) } // For fullscreen image
    val photoUrls = listOfNotNull(profile.profilepicUrl) + profile.optionalPhotoUrls

    LaunchedEffect(profile) {
        currentPhotoIndex = 0
    }

    // Create swipeable state
    val swipeableState = rememberSwipeableState(initialValue = 0)
    val anchors = mapOf(
        -300f to -1, // Swiped left
        0f to 0,     // Neutral position
        300f to 1    // Swiped right
    )

    val coroutineScope = rememberCoroutineScope()

    // Check swipe direction after swipe completes
    LaunchedEffect(swipeableState.currentValue) {
        if (swipeableState.currentValue == -1) {
            onSwipeLeft()
            swipeableState.snapTo(0) // Reset swipeable state
        } else if (swipeableState.currentValue == 1) {
            onSwipeRight()
            swipeableState.snapTo(0) // Reset swipeable state
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .swipeable(
                state = swipeableState,
                anchors = anchors,
                thresholds = { _, _ -> FractionalThreshold(0.3f) },
                orientation = Orientation.Horizontal
            )
            .offset { IntOffset(swipeableState.offset.value.roundToInt(), 0) }
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = BorderStroke(3.dp, getLevelBorderColor(profile.level))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Picture Carousel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val tapX = offset.x
                                    val photoCount = photoUrls.size
                                    if (photoCount > 1) {
                                        currentPhotoIndex = if (tapX > size.width / 2) {
                                            (currentPhotoIndex + 1) % photoCount
                                        } else {
                                            (currentPhotoIndex - 1 + photoCount) % photoCount
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    if (photoUrls.isNotEmpty()) {
                        AsyncImage(
                            model = photoUrls[currentPhotoIndex],
                            contentDescription = "Profile Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Navigation Bars above the photos
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
                    IconButton(
                        onClick = { showFullScreenImage = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Name, Age, Rating, Composite Score
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Top
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

                Spacer(modifier = Modifier.height(12.dp))

                // Toggle Details and Metrics Section
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showDetailsAndMetrics = !showDetailsAndMetrics }) {
                        Icon(
                            imageVector = if (showDetailsAndMetrics) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle Details and Metrics",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = if (showDetailsAndMetrics) "Hide Details and Metrics" else "Show Details and Metrics",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                if (showDetailsAndMetrics) {
                    // Display Details and Metrics side by side
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        // Details Column
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            // Details Section
                            ProfileText(label = "Name", value = profile.name)
                            ProfileText(label = "Gender", value = profile.gender)
                            ProfileText(label = "Bio", value = profile.bio)
                            ProfileText(label = "Hometown", value = profile.hometown)
                            ProfileText(label = "High School", value = profile.highSchool)
                            ProfileText(label = "College", value = profile.college)
                        }

                        // Metrics Column
                        UserInfoSectionDetailed(
                            profile = profile,
                            onLeaderboardClick = {
                                navController.navigate("leaderboard")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        )
                    }
                }

                // Button Row (Swipe Left/Right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Swipe Left Button
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                swipeableState.animateTo(-1)
                            }
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Swipe Left", tint = Color.White)
                    }

                    // Swipe Right Button
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                swipeableState.animateTo(1)
                            }
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.Green)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Swipe Right", tint = Color.White)
                    }
                }
            }
        }
    }

    // Fullscreen Image Dialog
    if (showFullScreenImage) {
        Dialog(onDismissRequest = { showFullScreenImage = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = photoUrls[currentPhotoIndex],
                    contentDescription = "Full Screen Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { showFullScreenImage = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

fun handleSwipeRight(currentUserId: String, otherUserId: String) {
    val database = FirebaseDatabase.getInstance()
    val timestamp = System.currentTimeMillis()

    val currentUserSwipesRef = database.getReference("swipes/$currentUserId/$otherUserId")
    val otherUserSwipesRef = database.getReference("swipes/$otherUserId/$currentUserId")

    val currentUserLikesGivenRef = database.getReference("likesGiven/$currentUserId/$otherUserId")
    val otherUserLikesReceivedRef = database.getReference("likesReceived/$otherUserId/$currentUserId")

    // Record the swipe right with timestamp
    val swipeData = mapOf(
        "liked" to true,
        "timestamp" to timestamp
    )
    currentUserSwipesRef.setValue(swipeData)

    // Update likesGiven and likesReceived
    currentUserLikesGivenRef.setValue(timestamp)
    otherUserLikesReceivedRef.setValue(timestamp)

    // Check if the other user has swiped right on current user
    otherUserSwipesRef.get().addOnSuccessListener { snapshot ->
        val otherUserSwipeData = snapshot.getValue<Map<String, Any>>()
        val otherUserSwipedRight = otherUserSwipeData?.get("liked") == true

        if (otherUserSwipedRight) {
            // It's a match!
            val currentUserMatchesRef = database.getReference("matches/$currentUserId/$otherUserId")
            val otherUserMatchesRef = database.getReference("matches/$otherUserId/$currentUserId")
            currentUserMatchesRef.setValue(timestamp)
            otherUserMatchesRef.setValue(timestamp)

            // Remove from likesReceived and likesGiven
            currentUserLikesGivenRef.removeValue()
            otherUserLikesReceivedRef.removeValue()
            val otherUserLikesGivenRef = database.getReference("likesGiven/$otherUserId/$currentUserId")
            val currentUserLikesReceivedRef = database.getReference("likesReceived/$currentUserId/$otherUserId")
            otherUserLikesGivenRef.removeValue()
            currentUserLikesReceivedRef.removeValue()

            // Optionally, send notifications to both users about the match
        }
    }
}

fun handleSwipeLeft(currentUserId: String, otherUserId: String) {
    val database = FirebaseDatabase.getInstance()
    val timestamp = System.currentTimeMillis()

    val currentUserSwipesRef = database.getReference("swipes/$currentUserId/$otherUserId")

    // Record the swipe left with timestamp
    val swipeData = mapOf(
        "liked" to false,
        "timestamp" to timestamp
    )
    currentUserSwipesRef.setValue(swipeData)
}


@Composable
fun RatingBar(
    rating: Double,
    modifier: Modifier = Modifier,
    stars: Int = 5,
    starSize: Dp = 20.dp,
    starColor: Color = Color.Yellow,
    starBackgroundColor: Color = Color.Gray
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..stars) {
            val filledPortion = when {
                i <= rating -> 1f
                i - rating < 1 -> (rating % 1).toFloat()
                else -> 0f
            }

            Box(
                modifier = Modifier
                    .size(starSize)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Star background",
                    tint = starBackgroundColor,
                    modifier = Modifier.fillMaxSize()
                )

                if (filledPortion > 0f) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Star foreground",
                        tint = starColor,
                        modifier = Modifier
                            .fillMaxSize(filledPortion)
                            .align(Alignment.CenterStart)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}


