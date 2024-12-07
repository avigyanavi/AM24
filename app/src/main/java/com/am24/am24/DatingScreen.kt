@file:OptIn(ExperimentalMaterialApi::class)

package com.am24.am24

import DatingViewModel
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
import androidx.compose.ui.res.painterResource
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Class representing swipe data
data class SwipeData(
    val liked: Boolean = false,
    val timestamp: Long = 0L
)

@Composable
fun DatingScreen(
    navController: NavController,
    geoFire: GeoFire,
    modifier: Modifier = Modifier,
) {
    val profileViewModel: ProfileViewModel = viewModel()
    val datingViewModel: DatingViewModel = viewModel()

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    // Load dating filters
    LaunchedEffect(Unit) {
        datingViewModel.loadDatingFiltersFromFirebase(currentUserId)
    }

    val filtersLoaded by datingViewModel.filtersLoaded.collectAsState()

    // Once filters are loaded, load profiles
    LaunchedEffect(filtersLoaded) {
        if (filtersLoaded) {
            datingViewModel.loadProfiles(currentUserId)
        }
    }

    val filteredProfiles by datingViewModel.filteredProfiles.collectAsState()
    val datingFilters by datingViewModel.datingFilters.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val distances = remember { mutableStateMapOf<String, Float>() }

    // Compute distances whenever filteredProfiles change
    LaunchedEffect(filteredProfiles) {
        withContext(Dispatchers.IO) {
            for (p in filteredProfiles) {
                val d = calculateDistance(currentUserId, p.userId, geoFire)
                distances[p.userId] = d ?: Float.MAX_VALUE
            }
        }
    }

    val fullyFilteredProfiles = remember(filteredProfiles, distances, datingFilters, searchQuery) {
        var result = filteredProfiles

        // Apply all filters (city, localities, highSchool, etc.)
        if (datingFilters.city.isNotEmpty() && datingFilters.city != "All") {
            result = result.filter { it.city.equals(datingFilters.city, ignoreCase = true) }
        }

        if (datingFilters.localities.isNotEmpty()) {
            result = result.filter { datingFilters.localities.contains(it.locality) }
        }

        if (datingFilters.highSchool.isNotBlank()) {
            result = result.filter { it.highSchool.equals(datingFilters.highSchool, ignoreCase = true) }
        }

        if (datingFilters.college.isNotBlank()) {
            result = result.filter { it.college.equals(datingFilters.college, ignoreCase = true) }
        }

        if (datingFilters.postGrad.isNotBlank()) {
            result = result.filter { (it.postGraduation ?: "").equals(datingFilters.postGrad, ignoreCase = true) }
        }

        if (datingFilters.work.isNotBlank()) {
            result = result.filter { it.work.equals(datingFilters.work, ignoreCase = true) }
        }

        if (datingFilters.ageStart != 0 && datingFilters.ageEnd != 0) {
            result = result.filter { profile ->
                val age = calculateAge(profile.dob)
                age != null && age in datingFilters.ageStart..datingFilters.ageEnd
            }
        }

        if (datingFilters.rating.isNotBlank()) {
            val ratingRange = when (datingFilters.rating) {
                "0-1.9" -> 0.0..1.9
                "2-3.9" -> 2.0..3.9
                "4-5" -> 4.0..5.0
                else -> 0.0..5.0
            }
            result = result.filter { it.averageRating in ratingRange }
        }

        if (datingFilters.gender.isNotBlank()) {
            result = result.filter { it.gender.equals(datingFilters.gender, ignoreCase = true) }
        }

        // Distance filter
        if (datingFilters.distance < 100) {
            result = result.filter { p ->
                val dist = distances[p.userId] ?: Float.MAX_VALUE
                dist <= datingFilters.distance
            }
        }

        val query = searchQuery.trim()
        if (query.isNotEmpty()) {
            result = result.filter {
                it.username.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
            }
        }

        result
    }

    if (!filtersLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF00bf63))
        }
    } else {
        // Implement pull-to-refresh
        var isRefreshing by remember { mutableStateOf(false) }
        val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

        // Function to refresh data
        fun refreshData() {
            isRefreshing = true
            // Reload filters and profiles
            datingViewModel.loadDatingFiltersFromFirebase(currentUserId)
            datingViewModel.loadProfiles(currentUserId)
        }

        // Observe changes to filtersLoaded or profiles to end refresh
        LaunchedEffect(filtersLoaded, filteredProfiles) {
            if (filtersLoaded) {
                // Once data is loaded, stop refreshing
                isRefreshing = false
            }
        }

        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { refreshData() }
        ) {
            // Make content scrollable to enable pull-down gesture
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .verticalScroll(rememberScrollState())
            ) {
                DatingScreenContent(
                    navController = navController,
                    geoFire = geoFire,
                    profileViewModel = profileViewModel,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    profiles = fullyFilteredProfiles
                )
            }
        }
    }
}

@Composable
fun DatingScreenContent(
    navController: NavController,
    geoFire: GeoFire,
    profileViewModel: ProfileViewModel,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    profiles: List<Profile>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var currentProfileIndex by remember { mutableStateOf(0) }

    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Search", color = Color.White) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFFFF4500),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color(0xFFFF4500),
                    focusedLabelColor = Color.White,
                    textColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
        }

        if (profiles.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(text = "No more profiles available.", color = Color.White, fontSize = 18.sp)
            }
        } else {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val currentProfile = profiles[currentProfileIndex]

            // Calculate distance for the current profile (optional if we want to show it)
            var userDistance by remember { mutableStateOf<Float?>(null) }
            LaunchedEffect(currentProfile) {
                userDistance = calculateDistance(currentUserId, currentProfile.userId, geoFire)
            }

            userDistance?.let { distance ->
                DatingProfileCard(
                    profile = currentProfile,
                    onSwipeRight = {
                        handleSwipeRight(currentUserId, currentProfile.userId, profileViewModel)
                        if (currentProfileIndex + 1 < profiles.size) {
                            currentProfileIndex++
                        }
                    },
                    onSwipeLeft = {
                        handleSwipeLeft(currentUserId, currentProfile.userId)
                        if (currentProfileIndex + 1 < profiles.size) {
                            currentProfileIndex++
                        }
                    },
                    navController = navController,
                    userDistance = distance
                )
            }
        }
    }
}


@Composable
fun DatingProfileCard(
    profile: Profile,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    userDistance: Float,
    navController: NavController
) {
    var currentPhotoIndex by remember(profile) { mutableStateOf(0) }
    var isDetailedView by remember { mutableStateOf(false) }
    val photoUrls = listOfNotNull(profile.profilepicUrl) + profile.optionalPhotoUrls

    // Reset current photo index when profile changes
    LaunchedEffect(profile) {
        currentPhotoIndex = 0
    }

    val swipeableState = rememberSwipeableState(initialValue = 0)
    val anchors = mapOf(
        -300f to -1, // Swiped left
        0f to 0,     // Neutral
        300f to 1    // Swiped right
    )

    val swipeOffset = swipeableState.offset.value

    LaunchedEffect(swipeableState.currentValue) {
        if (swipeableState.currentValue == -1) {
            onSwipeLeft()
            swipeableState.snapTo(0)
        } else if (swipeableState.currentValue == 1) {
            onSwipeRight()
            swipeableState.snapTo(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isDetailedView) {
            ProfileDetailsTabs(
                profile = profile,
                navController = navController,
                onBack = { isDetailedView = false }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .swipeable(
                        state = swipeableState,
                        anchors = anchors,
                        thresholds = { _, _ -> FractionalThreshold(0.3f) },
                        orientation = Orientation.Horizontal
                    )
                    .offset { IntOffset(swipeOffset.roundToInt(), 0) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(7 / 10f)
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
                    AsyncImage(
                        model = photoUrls[currentPhotoIndex],
                        contentDescription = "Profile Photo",
                        placeholder = painterResource(R.drawable.local_placeholder),
                        error = painterResource(R.drawable.local_placeholder),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    if (swipeableState.offset.value < -100) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(150.dp)
                                .align(Alignment.CenterStart)
                                .background(Color.Red.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dislike",
                                tint = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    } else if (swipeableState.offset.value > 100) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(150.dp)
                                .align(Alignment.CenterEnd)
                                .background(Color.Green.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Like",
                                tint = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "${profile.name}, ${calculateAge(profile.dob)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Distance: ${userDistance.roundToInt()} km away",
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            if (profile.hometown.isNotEmpty()) {
                                Text(
                                    text = "From ${profile.hometown}",
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            RatingBar(rating = profile.averageRating, modifier = Modifier.fillMaxWidth())
                            Text(
                                text = "Vibe Score: ${profile.vibepoints}",
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                        IconButton(
                            onClick = { isDetailedView = true },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "View More Info",
                                tint = Color.White
                            )
                        }
                    }

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
                                    .size(8.dp)
                                    .padding(horizontal = 2.dp)
                                    .clip(CircleShape)
                                    .background(if (index == currentPhotoIndex) Color.White else Color.Gray)
                            )
                        }
                    }
                }
            }
        }
    }
}


fun handleSwipeRight(
    currentUserId: String,
    otherUserId: String,
    profileViewModel: ProfileViewModel
) {
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

    // Check if the other user has swiped right on the current user
    otherUserSwipesRef.get().addOnSuccessListener { snapshot ->
        val otherUserSwipeData = snapshot.getValue(SwipeData::class.java)
        val otherUserSwipedRight = otherUserSwipeData?.liked == true

        if (otherUserSwipedRight) {
            // It's a match!
            val currentUserMatchesRef = database.getReference("matches/$currentUserId/$otherUserId")
            val otherUserMatchesRef = database.getReference("matches/$otherUserId/$currentUserId")
            currentUserMatchesRef.setValue(timestamp)
            otherUserMatchesRef.setValue(timestamp)

            // Send match notifications to both users
            profileViewModel.sendMatchNotification(
                senderId = currentUserId,
                receiverId = otherUserId,
                onSuccess = {
                    Log.d("MatchNotification", "Match notification sent to $otherUserId successfully")
                },
                onFailure = { error ->
                    Log.e("MatchNotification", "Failed to send match notification: $error")
                }
            )
            profileViewModel.sendMatchNotification(
                senderId = otherUserId,
                receiverId = currentUserId,
                onSuccess = {
                    Log.d("MatchNotification", "Match notification sent to $currentUserId successfully")
                },
                onFailure = { error ->
                    Log.e("MatchNotification", "Failed to send match notification: $error")
                }
            )
        } else {
            // The other user hasn't swiped right yet; send a like notification
            profileViewModel.sendLikeNotification(
                senderId = currentUserId,
                receiverId = otherUserId,
                onSuccess = {
                    Log.d("LikeNotification", "Like notification sent to $otherUserId successfully")
                },
                onFailure = { error ->
                    Log.e("LikeNotification", "Failed to send like notification: $error")
                }
            )
        }
    }.addOnFailureListener { exception ->
        Log.e("FirebaseError", "Failed to fetch swipes: ${exception.message}")
    }
}

fun handleSwipeLeft(currentUserId: String, otherUserId: String) {
    val database = FirebaseDatabase.getInstance()
    val timestamp = System.currentTimeMillis()

    val currentUserSwipesRef = database.getReference("swipes/$currentUserId/$otherUserId")
    currentUserSwipesRef.setValue(SwipeData(liked = false, timestamp = timestamp))
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

suspend fun calculateDistance(
    userId1: String,
    userId2: String,
    geoFire: GeoFire
): Float? = withContext(Dispatchers.Default) {
    val location1 = getUserLocation(userId1, geoFire)
    val location2 = getUserLocation(userId2, geoFire)

    if (location1 != null && location2 != null) {
        haversine(
            location1.latitude, location1.longitude,
            location2.latitude, location2.longitude
        )
    } else {
        null
    }
}

@Composable
fun ProfileDetailsTabs(
    profile: Profile,
    navController: NavController,
    onBack: () -> Unit // Callback for back button
) {
    val tabTitles = listOf("Profile", "Posts", "Score")
    var selectedTabIndex by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Back Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onBack() }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "Details",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Tabs
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, color = Color.White) }
                )
            }
        }

        // Tab Content
        when (selectedTabIndex) {
            0 -> ProfileDetails(profile)
            1 -> ProfilePosts(profile)
            2 -> ProfileScore(profile)
        }
    }
}

@Composable
fun ProfileDetails(profile: Profile) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(text = "Name: ${profile.name}", color = Color.White, fontSize = 18.sp)
        Text(text = "Bio: ${profile.bio}", color = Color.White, fontSize = 16.sp)
        Text(text = "Gender: ${profile.gender}", color = Color.White, fontSize = 16.sp)
        Text(text = "Hometown: ${profile.hometown}", color = Color.White, fontSize = 16.sp)
    }
}

@Composable
fun ProfilePosts(profile: Profile) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Posts by ${profile.name}", color = Color.White, fontSize = 16.sp)
        // Dynamically fetch and display user's posts
    }
}

@Composable
fun ProfileScore(profile: Profile) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Rating: ${profile.averageRating}", color = Color.White, fontSize = 16.sp)
        Text(text = "Social Score: ${profile.vibepoints}", color = Color.White, fontSize = 16.sp)
        // Add other metrics, like compatibility score
    }
}


suspend fun getUserLocation(userId: String, geoFire: GeoFire): GeoLocation? = suspendCancellableCoroutine { continuation ->
    geoFire.getLocation(userId, object : LocationCallback {
        override fun onLocationResult(key: String?, location: GeoLocation?) {
            continuation.resume(location)
        }

        override fun onCancelled(databaseError: DatabaseError) {
            continuation.resumeWithException(databaseError.toException())
        }
    })
}

@Composable
fun RatingBar(
    rating: Double,
    modifier: Modifier = Modifier,
    stars: Int = 5,
    starSize: Dp = 20.dp,
    starColor: Color = Color(0xFFFFA500),
    starBackgroundColor: Color = Color.Black
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