@file:OptIn(ExperimentalWearMaterialApi::class)

package com.am24.am24

import android.os.Bundle
import android.util.Log
import android.widget.RatingBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.FractionalThreshold
import androidx.wear.compose.material.rememberSwipeableState
import androidx.wear.compose.material.swipeable
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class DatingProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseDatabase.getInstance().setLogLevel(Logger.Level.DEBUG)

        val otherUserId = intent.getStringExtra("otherUserId")

        setContent {
            val navController = rememberNavController()
            var currentTab by remember { mutableStateOf(3) } // Default to Dating tab

            UnifiedScaffold(
                currentTab = currentTab,
                onTabChange = { currentTab = it },
                navController = navController,
                titleProvider = { tabIndex ->
                    when (tabIndex) {
                        0 -> "DMs"
                        1 -> "Feed"
                        2 -> "Profile"
                        3 -> "Dating"
                        4 -> "Settings"
                        else -> "Kupidx"
                    }
                }
            ) {
                if (otherUserId != null) {
                    DatingProfileScreen(userId = otherUserId, navController = navController)
                } else {
                    DatingProfileStackScreen(navController = navController)
                }
            }
        }
    }
}

@OptIn(ExperimentalWearMaterialApi::class)
@Composable
fun DatingProfileScreen(userId: String, navController: NavController) {
    val profile = remember { mutableStateOf(Profile()) }
    var currentPhotoIndex by remember { mutableStateOf(0) }
    var selectedTab by remember { mutableStateOf(0) }
    var showDetails by remember { mutableStateOf(true) }  // Collapsible state for details
    val scrollState = rememberScrollState()

    val firebaseDatabase = FirebaseDatabase.getInstance()
    val userRef = firebaseDatabase.getReference("users").child(userId)

    LaunchedEffect(userId) {
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                profile.value = snapshot.getValue(Profile::class.java) ?: Profile()
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle the error here
            }
        })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .swipeable( // Add swipeable state for left/right swiping
                state = rememberSwipeableState(initialValue = 0),
                anchors = mapOf(0f to 0, 1f to 1),
                thresholds = { _, _ -> FractionalThreshold(0.5f) },
                orientation = Orientation.Horizontal
            )
    ) {
        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState) // Make it scrollable
                .background(Color.Black)
        ) {
            // Image Carousel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .border(BorderStroke(3.dp, getLevelBorderColor(profile.value.level)))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                val photoUrls = listOf(profile.value.profilepicUrl) + profile.value.optionalPhotoUrls
                                val photoCount = photoUrls.size
                                if (photoCount > 1) {
                                    if (offset.x > size.width / 2) {
                                        currentPhotoIndex = (currentPhotoIndex + 1).coerceAtMost(photoCount - 1)
                                    } else {
                                        currentPhotoIndex = (currentPhotoIndex - 1).coerceAtLeast(0)
                                    }
                                }
                            }
                        )
                    }
            ) {
                val photoUrls = listOf(profile.value.profilepicUrl) + profile.value.optionalPhotoUrls
                if (photoUrls.isNotEmpty()) {
                    AsyncImage(
                        model = photoUrls[currentPhotoIndex],
                        contentDescription = "Profile Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Username and Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${profile.value.username}, ${calculateAge(profile.value.dob)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = Color.White
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Rating: ${profile.value.rating}", color = Color.White)
                    Text(text = "Composite Score: ${profile.value.am24RankingCompositeScore}", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Collapsible Details Section
            AnimatedVisibility(visible = showDetails) {
                UserInfoSectionBasicDating(profile = profile.value)
            }

            Button(onClick = { showDetails = !showDetails }) {
                Text(text = if (showDetails) "Hide Details" else "Show Details", color = Color.White)
            }

            // Metrics Section (Always visible)
            UserInfoSectionDetailedDating(profile = profile.value, onLeaderboardClick = { /* TODO */ })

            Spacer(modifier = Modifier.height(16.dp))

            // Tick and Cross buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { /* Handle swipe left */ }) {
                    Icon(Icons.Default.Close, contentDescription = "Cross", tint = Color.Red, modifier = Modifier.size(64.dp))
                }

                IconButton(onClick = { /* Handle swipe right */ }) {
                    Icon(Icons.Default.Check, contentDescription = "Tick", tint = Color.Green, modifier = Modifier.size(64.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalWearMaterialApi::class)
@Composable
fun DatingProfileStackScreen(navController: NavController) {
    val profiles = remember { mutableStateListOf<Profile>() }
    var currentProfileIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current

    // Firebase references to fetch profiles
    val firebaseAuth = FirebaseAuth.getInstance()
    val currentUser = firebaseAuth.currentUser

    if (currentUser != null) {
        val firebaseDatabase = FirebaseDatabase.getInstance()
        val usersRef = firebaseDatabase.getReference("users")

        // Fetch profiles
        LaunchedEffect(Unit) {
            usersRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    profiles.clear()
                    for (userSnapshot in snapshot.children) {
                        val profile = userSnapshot.getValue(Profile::class.java)
                        if (profile != null) {
                            profiles.add(profile)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseError", "DatabaseError: ${error.message}")
                    Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        // Ensure current profile index is valid
        if (profiles.isNotEmpty() && currentProfileIndex >= profiles.size) {
            currentProfileIndex = 0 // Reset to first profile
        }

        // Swipe logic and display current profile
        if (profiles.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .swipeable(
                        state = rememberSwipeableState(initialValue = 0),
                        anchors = mapOf(0f to 0, 1f to 1),
                        thresholds = { _, _ -> FractionalThreshold(0.5f) },
                        orientation = Orientation.Horizontal
                    )
            ) {
                val currentProfile = profiles[currentProfileIndex]

                // Display the profile in the card
                DatingProfileCard(
                    profile = currentProfile,
                    onSwipeRight = {
                        currentProfileIndex = (currentProfileIndex + 1) % profiles.size
                    },
                    onSwipeLeft = {
                        currentProfileIndex = if (currentProfileIndex > 0) currentProfileIndex - 1 else profiles.size - 1
                    },
                    navController = navController
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No profiles available", color = Color.White)
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "User not authenticated", color = Color.White)
        }
    }
}


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DatingProfileCard(
    profile: Profile,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    navController: NavController
) {
    var currentPhotoIndex by remember { mutableStateOf(0) }
    var showMetrics by remember { mutableStateOf(false) } // Only metrics is minimizable
    val photoUrls = listOf(profile.profilepicUrl) + profile.optionalPhotoUrls

    // Create swipeable state
    val swipeableState = rememberSwipeableState(initialValue = 0)
    val swipeAnchors = mapOf(
        -1f to -1, // Swiped left
        0f to 0,   // Neutral position
        1f to 1    // Swiped right
    )

    // Animation based on swipe offset
    val swipeProgress by animateFloatAsState(
        targetValue = swipeableState.offset.value,
        animationSpec = tween(300)
    )

    // Check swipe direction after swipe completes
    LaunchedEffect(swipeableState.currentValue) {
        if (swipeableState.currentValue == -1) {
            onSwipeLeft()
        } else if (swipeableState.currentValue == 1) {
            onSwipeRight()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .swipeable(
                state = swipeableState,
                anchors = swipeAnchors,
                thresholds = { _, _ -> FractionalThreshold(0.3f) }, // Sensitivity for swipe detection
                orientation = Orientation.Horizontal
            )
            .offset { IntOffset(swipeProgress.roundToInt(), 0) } // Add swipe animation
            .padding(16.dp)
            .verticalScroll(rememberScrollState()) // Scroll for vertical content
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(16.dp)
        ) {
            // Picture Carousel with Border
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .border(BorderStroke(2.dp, getLevelBorderColor(profile.level)))
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Name, Age, Rating, Composite Score - Overlay on image
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

            // Details Section (Always Visible)
            ProfileText(label = "Name", value = profile.name)
            ProfileText(label = "Gender", value = profile.gender)
            ProfileText(label = "Bio", value = profile.bio)
            ProfileText(label = "Locality", value = profile.locality)
            ProfileText(label = "High School", value = profile.highSchool)
            ProfileText(label = "College", value = profile.college)

            Spacer(modifier = Modifier.height(8.dp))

            // Toggle Metrics Section
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showMetrics = !showMetrics }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Show Metrics",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Metrics",
                    fontSize = 16.sp,
                    color = Color.White
                )
            }

            if (showMetrics) {
                UserInfoSectionDetailed(profile = profile, onLeaderboardClick = {
                    navController.navigate("leaderboard")
                })
            }

            // Button Row (Swipe Left/Right/Up -> Add buttons for future features)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Swipe Left Button
                IconButton(
                    onClick = onSwipeLeft,
                    modifier = Modifier
                        .size(50.dp) // Smaller button
                        .clip(CircleShape)
                        .background(Color.Red)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Swipe Left", tint = Color.White)
                }

                // Future swipe up button or other actions can go here
                // IconButton for Swipe Up or other buttons (placeholder for future)

                // Swipe Right Button
                IconButton(
                    onClick = onSwipeRight,
                    modifier = Modifier
                        .size(50.dp) // Smaller button
                        .clip(CircleShape)
                        .background(Color.Green)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Swipe Right", tint = Color.White)
                }
            }
        }
    }
}




@Composable
fun RatingBar(
    rating: Double,  // Rating should be a value between 0 and 5 (for 5 stars)
    modifier: Modifier = Modifier,
    stars: Int = 5,  // Total number of stars, default is 5
    starSize: Dp = 20.dp,  // Size of each star
    starColor: Color = Color.Yellow,  // Color of the stars
    starBackgroundColor: Color = Color.Gray // Background color for unfilled stars
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Loop through each star index and create a filled or unfilled star
        for (i in 1..stars) {
            val filledPortion = when {
                i <= rating -> 1f  // Full star
                i - rating < 1 -> rating % 1  // Partial star
                else -> 0f  // Empty star
            }

            Box(
                modifier = Modifier
                    .size(starSize)
                    .clip(CircleShape)
            ) {
                // Background star
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Star background",
                    tint = starBackgroundColor,
                    modifier = Modifier.fillMaxSize()
                )

                // Foreground star (for filled portion)
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Star foreground",
                    tint = starColor,
                    modifier = Modifier
                        .fillMaxSize(filledPortion as Float)
                        .align(Alignment.CenterStart)  // Align filled portion to the left
                )
            }

            // Add space between stars
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
fun DetailsSection(profile: Profile, expanded: Boolean, onExpandToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onExpandToggle() }
        ) {
            Text(text = "Details", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White
            )
        }

        if (expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp)) {
                Text(text = "Name: ${profile.name}", color = Color.White)
                Text(text = "Gender: ${profile.gender}", color = Color.White)
                Text(text = "Locality: ${profile.locality}", color = Color.White)
                Text(text = "High School: ${profile.highSchool}", color = Color.White)
                Text(text = "College: ${profile.college}", color = Color.White)
            }
        }
    }
}

@Composable
fun MetricsSection(profile: Profile, expanded: Boolean, onExpandToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onExpandToggle() }
        ) {
            Text(text = "Metrics", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White
            )
        }

        if (expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp)) {
                Text(text = "Matches: ${profile.matchCount}", color = Color.White)
                Text(text = "Swipe Rights: ${profile.numberOfSwipeRights}", color = Color.White)
                Text(text = "Swipe Lefts: ${profile.numberOfSwipeLefts}", color = Color.White)
                Text(text = "Swipe Right to Left Ratio: ${profile.getCalculatedSwipeRightToLeftRatio()}", color = Color.White)
            }
        }
    }
}

@Composable
fun UserInfoSectionBasicDating(profile: Profile) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Display basic profile information
        Text(text = "Name: ${profile.name}", color = Color.White)
        Text(text = "Gender: ${profile.gender}", color = Color.White)
        Text(text = "Bio: ${profile.bio}", color = Color.White)
        Text(
            text = if (profile.interests.isNotEmpty()) {
                profile.interests.joinToString(", ") { interest -> "${interest.emoji} ${interest.name}" }
            } else {
                "No interests specified"
            },
            color = Color.White
        )
    }
}

@Composable
fun UserInfoSectionDetailedDating(profile: Profile, onLeaderboardClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Ratings and Rankings
        Text(text = "Rating: ${profile.rating} from (${profile.numberOfRatings} ratings)", color = Color.White)
        Text(text = "Global Ranking: ${profile.am24RankingGlobal}", color = Color.White)
        Text(text = "Age Ranking: ${profile.am24RankingAge}", color = Color.White)
        Text(text = "High School Ranking: ${profile.am24RankingHighSchool}", color = Color.White)
        Text(text = "College Ranking: ${profile.am24RankingCollege}", color = Color.White)
        Text(text = "Locality Ranking: ${profile.am24RankingLocality}", color = Color.White)

        // Swipe and Match Metrics
        Text(text = "Matches: ${profile.matchCount}", color = Color.White)
        Text(text = "Swipe Rights: ${profile.numberOfSwipeRights}", color = Color.White)
        Text(text = "Swipe Lefts: ${profile.numberOfSwipeLefts}", color = Color.White)
        Text(text = "Swipe Right to Left Ratio: ${profile.getCalculatedSwipeRightToLeftRatio()}", color = Color.White)

        Spacer(modifier = Modifier.height(8.dp))

        // View Leaderboard Button
        Button(onClick = onLeaderboardClick) {
            Text(text = "View Leaderboard")
        }
    }
}
