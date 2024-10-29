@file:OptIn(ExperimentalMaterialApi::class)

package com.am24.am24

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.LocationCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.*
import androidx.compose.material3.Card


// Class representing swipe data
data class SwipeData(
    val liked: Boolean = false,
    val timestamp: Long = 0L
)

@Composable
fun DatingScreen(navController: NavController, geoFire: GeoFire, modifier: Modifier = Modifier) {
    DatingScreenContent(navController = navController, geoFire = geoFire, modifier = modifier)
}

@Composable
fun DatingScreenContent(navController: NavController, geoFire: GeoFire, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val profiles = remember { mutableStateListOf<Profile>() }
    var currentProfileIndex by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showAIAnalysisPopup by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val usersRef = FirebaseDatabase.getInstance().getReference("users")

    // Fetch profiles
    LaunchedEffect(Unit) {
        isLoading = true
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val fetchedProfiles = mutableListOf<Profile>()
                for (userSnapshot in snapshot.children) {
                    val profile = userSnapshot.getValue(Profile::class.java)
                    if (profile != null && profile.userId != currentUserId) {
                        fetchedProfiles.add(profile)
                    }
                }
                profiles.clear()
                profiles.addAll(fetchedProfiles)
                isLoading = false
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "DatabaseError: ${error.message}")
                Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                isLoading = false
            }
        })
    }

    // Filter profiles by username search
    val filteredProfiles = profiles.filter { it.username.contains(searchQuery, ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Search Bar and AI Analysis button row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search bar
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Gray, CircleShape)
                    .padding(8.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(16.dp))

            // AI Analysis Button
            IconButton(
                onClick = { showAIAnalysisPopup = !showAIAnalysisPopup },
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00bf63))
            ) {
                Icon(Icons.Default.Analytics, contentDescription = "AI Analysis", tint = Color.White)
            }
        }

        // Show loading icon when profiles are loading
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF00bf63))
            }
        } else if (filteredProfiles.isNotEmpty()) {
            // Display profile cards
            val currentProfile = filteredProfiles[currentProfileIndex]
            var userDistance by remember { mutableStateOf<Float?>(null) }

            // Calculate distance asynchronously
            LaunchedEffect(currentProfile) {
                calculateDistance(currentUserId, currentProfile.userId, geoFire) { distance ->
                    userDistance = distance
                }
            }

            userDistance?.let { distance ->
                Box(modifier = Modifier.fillMaxSize()) {
                    DatingProfileCard(
                        profile = currentProfile,
                        onSwipeRight = {
                            handleSwipeRight(currentUserId, currentProfile.userId)
                            // Remove the current profile and show next profile or "no more profiles" message
                            if (currentProfileIndex + 1 < filteredProfiles.size) {
                                currentProfileIndex += 1
                            } else {
                                profiles.clear()  // Clear all profiles when none are left
                            }
                        },
                        onSwipeLeft = {
                            handleSwipeLeft(currentUserId, currentProfile.userId)
                            // Remove the current profile and show next profile or "no more profiles" message
                            if (currentProfileIndex + 1 < filteredProfiles.size) {
                                currentProfileIndex += 1
                            } else {
                                profiles.clear()  // Clear all profiles when none are left
                            }
                        },
                        navController = navController,
                        userDistance = distance
                    )

                    // AI Analysis Pop-up
                    if (showAIAnalysisPopup) {
                        Dialog(onDismissRequest = { showAIAnalysisPopup = false }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable { showAIAnalysisPopup = false }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(16.dp)
                                        .background(Color.White, CircleShape)
                                        .padding(32.dp)
                                ) {
                                    Text(text = "AI Analysis Placeholder", color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Display no profiles message
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No more profiles available.",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Haversine formula to calculate the distance between two geographic points
fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val earthRadius = 6371.0 // Radius of the Earth in kilometers
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadius * c).toFloat() // Distance in kilometers
}

fun calculateDistance(
    userId1: String,
    userId2: String,
    geoFire: GeoFire,
    callback: (Float?) -> Unit
) {
    geoFire.getLocation(userId1, object : LocationCallback {
        override fun onLocationResult(key1: String?, location1: GeoLocation?) {
            if (location1 == null) {
                callback(null) // Location not found for user 1
                return
            }
            geoFire.getLocation(userId2, object : LocationCallback {
                override fun onLocationResult(key2: String?, location2: GeoLocation?) {
                    if (location2 == null) {
                        callback(null) // Location not found for user 2
                        return
                    }
                    // Both locations are available, calculate the distance
                    val distance = haversine(
                        location1.latitude, location1.longitude,
                        location2.latitude, location2.longitude
                    )
                    callback(distance)
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(null) // Handle error for user 2 location retrieval
                }
            })
        }

        override fun onCancelled(error: DatabaseError) {
            callback(null) // Handle error for user 1 location retrieval
        }
    })
}


@Composable
fun DatingProfileCard(
    profile: Profile,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    navController: NavController,
    userDistance: Float // Distance to display on the card
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
                // Display user distance on top left
                Text(
                    text = "${userDistance.roundToInt()} km away",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )

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
                            tint = Color(0xFF00bf63)
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
                        text = "Vibe Score: ${profile.vibepoints}",
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
    val swipeData = SwipeData(liked = true, timestamp = timestamp)
    currentUserSwipesRef.setValue(swipeData)

    // Update likesGiven and likesReceived
    currentUserLikesGivenRef.setValue(timestamp)
    otherUserLikesReceivedRef.setValue(timestamp)

    // Check if the other user has swiped right on current user
    otherUserSwipesRef.get().addOnSuccessListener { snapshot ->
        val otherUserSwipeData = snapshot.getValue(SwipeData::class.java)
        val otherUserSwipedRight = otherUserSwipeData?.liked == true

        if (otherUserSwipedRight) {
            // It's a match!
            val currentUserMatchesRef = database.getReference("matches/$currentUserId/$otherUserId")
            val otherUserMatchesRef = database.getReference("matches/$otherUserId/$currentUserId")
            currentUserMatchesRef.setValue(timestamp)
            otherUserMatchesRef.setValue(timestamp)

            // Optionally, send notifications to both users about the match
        }
    }.addOnFailureListener { exception ->
        Log.e("FirebaseError", "Failed to fetch swipes: ${exception.message}")
    }
}

fun handleSwipeLeft(currentUserId: String, otherUserId: String) {
    val database = FirebaseDatabase.getInstance()
    val timestamp = System.currentTimeMillis()

    val currentUserSwipesRef = database.getReference("swipes/$currentUserId/$otherUserId")

    // Record the swipe left with timestamp
    val swipeData = SwipeData(liked = false, timestamp = timestamp)
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
