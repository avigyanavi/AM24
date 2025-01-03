@file:OptIn(ExperimentalMaterialApi::class)

package com.am24.am24

import DatingViewModel
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.LocationCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlin.math.roundToInt
import kotlin.math.*
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SwipeData(
    val liked: Boolean = false,
    val timestamp: Long = 0L
)

@Composable
fun DatingScreen(
    navController: NavController,
    geoFire: GeoFire,
    modifier: Modifier = Modifier,
    initialQuery: String = ""
) {
    val datingViewModel: DatingViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()
    val postViewModel: PostViewModel = viewModel()

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val filtersLoaded by datingViewModel.filtersLoaded.collectAsState()
    val filteredProfiles by datingViewModel.filteredProfiles.collectAsState()
    val matchPopUpState by profileViewModel.matchPopUpState.collectAsState()

    var searchQuery by remember { mutableStateOf(initialQuery) }
    var excludedUserIds by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(currentUserId) {
        excludedUserIds = fetchExcludedUsers(currentUserId)
    }

    LaunchedEffect(currentUserId) {
        // Start real-time listeners when you enter the DatingScreen
        datingViewModel.startRealTimeProfileUpdates(currentUserId)
        datingViewModel.startRealTimeFilterUpdates(currentUserId)
    }

    DisposableEffect(Unit) {
        // When DatingScreen is disposed (navigated away), stop the real-time updates
        onDispose {
            datingViewModel.pauseProfileUpdates()
            datingViewModel.pauseFilterUpdates(currentUserId)

            // Clear match pop-up if you want
            profileViewModel.clearMatchPopUp()
        }
    }


    val displayedProfiles = remember(filteredProfiles, searchQuery, excludedUserIds) {
        filteredProfiles.filter { profile ->
            profile.userId !in excludedUserIds &&
                    profile.username.contains(searchQuery, ignoreCase = true)
        }
    }


    // Use a Box to handle layering
    Box(modifier = Modifier.fillMaxSize()) {
        // Main content
        if (!filtersLoaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFA500))
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                // Search Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search", color = Color.Gray) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFFF4500),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color(0xFFFF4500),
                            focusedLabelColor = Color.Gray,
                            textColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (displayedProfiles.isEmpty()) {
                    NoMoreProfilesScreen(datingViewModel = datingViewModel)
                } else {
                    DatingScreenContent(
                        navController = navController,
                        geoFire = geoFire,
                        profileViewModel = profileViewModel,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        profiles = displayedProfiles,
                        postViewModel = postViewModel
                    )
                }
            }
        }

        // Pop-up for a match, shown above the stack
        matchPopUpState?.let { (currentUserProfile, matchedUserProfile) ->
            MatchPopUp(
                currentUserProfilePic = currentUserProfile.profilepicUrl ?: "",
                otherUserProfilePic = matchedUserProfile.profilepicUrl ?: "",
                onChatClick = {
                    navController.navigate("chat/${matchedUserProfile.userId}")
                    profileViewModel.clearMatchPopUp()
                },
                onClose = {
                    profileViewModel.clearMatchPopUp()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .align(Alignment.Center)
                    .zIndex(1f) // Ensure it's on top
            )
        }
    }
}


@Composable
fun NoMoreProfilesScreen(datingViewModel: DatingViewModel) {
    val filters by datingViewModel.datingFilters.collectAsState()

    var ageRange by remember { mutableStateOf(filters.ageStart..filters.ageEnd) }
    var maxDistance by remember { mutableStateOf(filters.distance) }
    var selectedGenders by remember { mutableStateOf(filters.gender.split(",").filter { it.isNotBlank() }.toSet()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No more profiles available.", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
        Text("Adjust your filters:", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))

        // Age Range Slider
        Text("Age Range: ${ageRange.start} - ${ageRange.endInclusive}", color = Color.White)
        RangeSlider(
            value = ageRange.start.toFloat()..ageRange.endInclusive.toFloat(),
            onValueChange = { range ->
                ageRange = range.start.roundToInt()..range.endInclusive.roundToInt()
                datingViewModel.updateDatingFilters(
                    filters.copy(ageStart = ageRange.start, ageEnd = ageRange.endInclusive)
                )
            },
            valueRange = 18f..100f,
            steps = 82,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF6F00),
                activeTrackColor = Color(0xFFFF6F00),
                inactiveTrackColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Distance Slider
        val displayedDistanceText = if (maxDistance == 100) "Global" else "$maxDistance km"
        Text("Max Distance: $displayedDistanceText", color = Color.White)
        Slider(
            value = maxDistance.toFloat(),
            onValueChange = { distance ->
                maxDistance = distance.roundToInt()
                datingViewModel.updateDatingFilters(filters.copy(distance = maxDistance))
            },
            valueRange = 0f..100f,
            steps = 10,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF6F00),
                activeTrackColor = Color(0xFFFF6F00),
                inactiveTrackColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Gender Selection Buttons (Toggleable)
        Text("Gender Preference:", color = Color.White)
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            listOf("Male", "Female", "Other").forEach { gender ->
                Button(
                    onClick = {
                        selectedGenders = if (selectedGenders.contains(gender)) {
                            selectedGenders - gender
                        } else {
                            selectedGenders + gender
                        }
                        datingViewModel.updateDatingFilters(
                            filters.copy(gender = selectedGenders.joinToString(","))
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (selectedGenders.contains(gender)) Color(0xFFFF6F00) else Color.White
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(gender, color = Color.Black)
                }
            }
        }
    }
}


@Composable
fun DatingScreenContent(
    navController: NavController,
    geoFire: GeoFire,
    profileViewModel: ProfileViewModel,
    postViewModel: PostViewModel,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    profiles: List<Profile>,
    modifier: Modifier = Modifier,
) {
    var currentProfileIndex by remember { mutableStateOf(0) }

    if (profiles.isEmpty() || currentProfileIndex >= profiles.size) {
        NoMoreProfilesScreen(datingViewModel = viewModel())
    } else {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val currentProfile = profiles[currentProfileIndex]

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
                    } else {
                        currentProfileIndex = profiles.size // Trigger "No more profiles"
                    }
                },
                onSwipeLeft = {
                    handleSwipeLeft(currentUserId, currentProfile.userId)
                    if (currentProfileIndex + 1 < profiles.size) {
                        currentProfileIndex++
                    } else {
                        currentProfileIndex = profiles.size // Trigger "No more profiles"
                    }
                },
                navController = navController,
                userDistance = distance,
                postViewModel = postViewModel
            )
        }
    }
}

suspend fun fetchExcludedUsers(currentUserId: String): Set<String> {
    val database = FirebaseDatabase.getInstance()
    val matchesRef = database.getReference("matches/$currentUserId")
    val likesRef = database.getReference("likesGiven/$currentUserId")

    val oneWeekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L

    return withContext(Dispatchers.IO) {
        val excludedIds = mutableSetOf<String>()

        // Fetch matched users
        matchesRef.get().addOnSuccessListener { snapshot ->
            snapshot.children.forEach { excludedIds.add(it.key!!) }
        }.await()

        // Fetch recently liked users
        likesRef.get().addOnSuccessListener { snapshot ->
            snapshot.children.forEach { likeSnapshot ->
                val timestamp = likeSnapshot.getValue(Long::class.java) ?: 0L
                if (timestamp >= oneWeekAgo) {
                    excludedIds.add(likeSnapshot.key!!)
                }
            }
        }.await()

        excludedIds
    }
}

@Composable
fun DatingProfileCard(
    profile: Profile,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    userDistance: Float,
    navController: NavController,
    postViewModel: PostViewModel
) {
    val swipeableState = rememberSwipeableState(initialValue = 0)
    val anchors = mapOf(-300f to -1, 0f to 0, 300f to 1)
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

    // Gather user’s posts -> featured vs leftover
    val allPosts by postViewModel.filteredPosts.collectAsState()
    val myPosts = allPosts.filter { it.userId == profile.userId }
    val sortedByUpvotes = myPosts.sortedByDescending { it.upvotes }
    val featuredPosts = sortedByUpvotes.take(5)
    val remainingPosts = sortedByUpvotes.drop(5)

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
            .offset { IntOffset(swipeOffset.roundToInt(), 0) }
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // 1) Photo with top and bottom overlays
            item {
                PhotoWithTwoOverlays(profile = profile, userDistance = userDistance)
            }

            // 2) Collapsible sections
            item {
                ProfileCollapsibleSectionsAll(profile)
            }

            // 3) Featured Posts
            if (featuredPosts.isNotEmpty()) {
                item {
                    Text(
                        text = "Featured Posts",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(featuredPosts) { post ->
                    PostItemInProfile(post)
                }
            }

            // 4) Collapsed metrics
            item {
                CollapsedMetricsSection(profile)
            }

            // 5) View More
            if (remainingPosts.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = { /* e.g. open all posts screen */ },
                            colors = ButtonDefaults.buttonColors(Color(0xFFFF6F00))
                        ) {
                            Text(text = "View More Posts", color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun PhotoWithTwoOverlays(
    profile: Profile,
    userDistance: Float
) {
    // Photos
    val photoUrls = listOfNotNull(profile.profilepicUrl) + profile.optionalPhotoUrls
    var currentPhotoIndex by remember { mutableStateOf(0) }

    // Vibe Score
    val vibeScorePercent = (profile.vibepoints * 100).roundToInt().coerceAtLeast(0)
    val age = calculateAge(profile.dob)
    val heightCm = profile.height

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .pointerInput(photoUrls) {
                detectTapGestures(
                    onTap = { offset ->
                        if (photoUrls.size > 1) {
                            if (offset.x > size.width / 2) {
                                currentPhotoIndex = (currentPhotoIndex + 1) % photoUrls.size
                            } else {
                                currentPhotoIndex = (currentPhotoIndex - 1 + photoUrls.size) % photoUrls.size
                            }
                        }
                    }
                )
            }
    ) {
        // Main photo
        if (photoUrls.isNotEmpty()) {
            AsyncImage(
                model = photoUrls[currentPhotoIndex],
                contentDescription = "Profile Photo",
                placeholder = painterResource(R.drawable.local_placeholder),
                error = painterResource(R.drawable.local_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp)
            )

            // Top overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RatingBar(rating = profile.averageRating, ratingCount = profile.numberOfRatings)
                    Spacer(modifier = Modifier.width(12.dp))
                    FlashyVibeScore(scorePercent = vibeScorePercent)
                }
            }

            // Bottom overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(Color.Black.copy(alpha = 0.60f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    // Name, Age, Distance + height
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "${profile.username}, $age", // Updated to username
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${userDistance.roundToInt()} km away",
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                        if (heightCm > 0) {
                            Text(
                                text = "📏${heightCm} cm",
                                fontSize = 14.sp,
                                color = Color(0xFFFFDB00),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Community, locality, religion
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (profile.community.isNotBlank()) {
                            TagBox(profile.community)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (profile.city.isNotBlank()) {
                            TagBox(profile.city)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (profile.religion.isNotBlank()) {
                            TagBox(profile.religion)
                        }
                    }
                }
            }
        }
    }
}


/**
 * TagBox that replicates your #tag style from posts,
 * adapted for overlay usage.
 */
@Composable
fun TagBox(text: String) {
    if (text.isNotBlank()) {
        Box(
            modifier = Modifier
                .padding(horizontal = 1.dp)
                .background(
                    Color.Black,
                    RoundedCornerShape(4.dp)
                )
                .border(
                    BorderStroke(1.dp, Color(0xFFFF6F00)),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}
@Composable
fun TagtwoBox(text: String) {
    if (text.isNotBlank()) {
        Box(
            modifier = Modifier
                .padding(horizontal = 1.dp)
                .background(
                    Color.Black,
                    RoundedCornerShape(4.dp)
                )
                .border(
                    BorderStroke(1.dp, Color(0xFFFFDB00)),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Displays the photo carousel + two overlays:
 *  - Top overlay: rating bar + vibe (small bar)
 *  - Bottom overlay: name, age, distance, community, city, religion, etc.
 *    with a rounded top border.
 * All of this is within a fixed or auto-sized Box so when scrolled,
 * the overlays move away with the photo.
 */


@Composable
fun FlashyVibeScore(scorePercent: Int) {
    // Let's clamp it between 0% and 100%
    val displayPercent = scorePercent.coerceIn(0, 100)

    // Example of a big bold text with a gradient behind it
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFF4500), Color(0xFFFF6F00))
                )
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "Mutual Vibe is at $displayPercent%",
            color = Color.White, // text color stands out on the gradient
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
    }
}


@Composable
fun ProfileCollapsibleSectionsAll(profile: Profile) {
    var showBasic by rememberSaveable { mutableStateOf(true) }
    var showPreferences by rememberSaveable { mutableStateOf(true) }
    var showLifestyle by rememberSaveable { mutableStateOf(true) }
    var showInterests by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        CollapsibleSection(
            title = "Basic Information",
            icon = Icons.Default.Person,
            isExpanded = showBasic,
            onToggle = { showBasic = !showBasic }
        ) {
            BasicInfoSection(profile)
        }
        Spacer(modifier = Modifier.height(12.dp))

        CollapsibleSection(
            title = "Preferences",
            icon = Icons.Default.Favorite,
            isExpanded = showPreferences,
            onToggle = { showPreferences = !showPreferences }
        ) {
            PreferencesSection(profile)
        }
        Spacer(modifier = Modifier.height(12.dp))

        CollapsibleSection(
            title = "Lifestyle Attributes",
            icon = Icons.Default.Nature,
            isExpanded = showLifestyle,
            onToggle = { showLifestyle = !showLifestyle }
        ) {
            LifestyleSection(profile)
        }
        Spacer(modifier = Modifier.height(12.dp))

        CollapsibleSection(
            title = "Interests",
            icon = Icons.Default.Star,
            isExpanded = showInterests,
            onToggle = { showInterests = !showInterests }
        ) {
            InterestsSectionInProfile(profile)
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

    val swipeData = SwipeData(liked = true, timestamp = timestamp)
    currentUserSwipesRef.setValue(swipeData)
    currentUserLikesGivenRef.setValue(timestamp)
    otherUserLikesReceivedRef.setValue(timestamp)

    otherUserSwipesRef.get().addOnSuccessListener { snapshot ->
        val otherUserSwipeData = snapshot.getValue(SwipeData::class.java)
        val otherUserSwipedRight = otherUserSwipeData?.liked == true

        if (otherUserSwipedRight) {
            val currentUserMatchesRef = database.getReference("matches/$currentUserId/$otherUserId")
            val otherUserMatchesRef = database.getReference("matches/$otherUserId/$currentUserId")
            currentUserMatchesRef.setValue(timestamp)
            otherUserMatchesRef.setValue(timestamp)

            // Send match notification
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

            // Trigger match pop-up
            profileViewModel.triggerMatchPopUp(currentUserId, otherUserId)
        } else {
            // Send like notification
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

fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val earthRadius = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadius * c).toFloat()
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
fun ProfilePosts(profile: Profile) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Posts by ${profile.name}", color = Color.White, fontSize = 16.sp)
    }
}

@Composable
fun MatchPopUp(
    currentUserProfilePic: String,
    otherUserProfilePic: String,
    onChatClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp)),
            elevation = 8.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Text(
                    text = "It's a Match!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Profile Pictures
                Row(horizontalArrangement = Arrangement.Center) {
                    AsyncImage(
                        model = currentUserProfilePic,
                        contentDescription = "Your Profile Picture",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFFFF6F00), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    AsyncImage(
                        model = otherUserProfilePic,
                        contentDescription = "Matched Profile Picture",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFFFF6F00), CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Button(
                    onClick = onChatClick,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00))
                ) {
                    Text("Chat Now", color = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onClose) {
                    Text("Close", color = Color.Gray)
                }
            }
        }
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