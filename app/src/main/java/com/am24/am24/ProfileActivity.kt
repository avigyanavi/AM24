@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            var currentTab by rememberSaveable { mutableStateOf(2) } // Default to Profile tab

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
            )

            ProfileScreen(
                navController = navController,
                currentTab = currentTab,
                onTabChange = { currentTab = it }
            )
        }
    }
}

@Composable
fun ProfileScreen(
    navController: NavHostController,
    currentTab: Int,
    onTabChange: (Int) -> Unit,
    isOtherUserProfile: Boolean = false
) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val profile = remember { mutableStateOf(Profile()) }
    var currentPhotoIndex by remember { mutableStateOf(0) }
    var showDetails by remember { mutableStateOf(false) }
    var showMetrics by remember { mutableStateOf(false) }

    val firebaseDatabase = FirebaseDatabase.getInstance()

    // Real-time listeners for profile
    val userRef = if (isOtherUserProfile) {
        // Replace with specific other user ID logic
        firebaseDatabase.getReference("users").child("OTHER_USER_ID")
    } else {
        firebaseDatabase.getReference("users").child(userId)
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = BorderStroke(3.dp, getLevelBorderColor(profile.value.level))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()) // Making the whole profile scrollable
                    .padding(16.dp)
            ) {
                // Profile Photo Carousel with Navigation Bars
                // Profile Photo Carousel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val tapX = offset.x
                                    val photoUrls = listOf(profile.value.profilepicUrl) + profile.value.optionalPhotoUrls // <--- Change here
                                    val photoCount = photoUrls.size
                                    if (photoCount > 1) {
                                        if (tapX > size.width / 2) {
                                            // Tap on right side of the image
                                            currentPhotoIndex = (currentPhotoIndex + 1).coerceAtMost(photoCount - 1)
                                        } else {
                                            // Tap on left side of the image
                                            currentPhotoIndex = (currentPhotoIndex - 1).coerceAtLeast(0)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    val photoUrls = listOf(profile.value.profilepicUrl) + profile.value.optionalPhotoUrls // <--- Add this line to use the profile picture first
                    if (photoUrls.isNotEmpty()) {
                        AsyncImage(
                            model = photoUrls[currentPhotoIndex],
                            contentDescription = "Profile Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit // Adjusted to avoid cropping
                        )

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
                                        .size(8.dp, 8.dp)
                                        .padding(horizontal = 2.dp)
                                        .clip(CircleShape)
                                        .background(if (index == currentPhotoIndex) Color.White else Color.Gray)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No Images",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }


                Spacer(modifier = Modifier.height(16.dp))

                // Username and Edit Button
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

                    // If it's not another user's profile, show the edit profile button.
                    if (!isOtherUserProfile) {
                        val context = LocalContext.current // Access the context to start an activity.

                        IconButton(
                            onClick = {
                                // Start EditProfileActivity using the context.
                                val intent = Intent(context, EditProfileActivity::class.java)
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = Color.White
                            )
                        }
                    }
                }


                Spacer(modifier = Modifier.height(8.dp))

                // Dropdown Arrow for Details
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showDetails = !showDetails }) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Show Details",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Details",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                if (showDetails) {
                    UserInfoSectionBasic(profile = profile.value)

                    // Dropdown Arrow for Metrics
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                }

                if (showMetrics) {
                    UserInfoSectionDetailed(profile = profile.value, onLeaderboardClick = {
                        navController.navigate("leaderboardActivity")
                    })
                }

                // "View Likes" and "View Leaderboard" buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Button(
                        onClick = {
                            navController.navigate("peopleWhoLikeMe")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                    ) {
                        Text(text = "View Likes", color = Color.White)
                    }

                    Button(
                        onClick = {
                            navController.navigate("leaderboard")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                    ) {
                        Text(text = "View Leaderboard", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun UserInfoSectionBasic(profile: Profile) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Name
        Text(text = "Name:", fontSize = 16.sp, color = Color(0xFF00bf63))
        Text(text = profile.name, fontSize = 16.sp, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        // Gender
        Text(text = "Gender:", fontSize = 16.sp, color = Color(0xFF00bf63))
        Text(text = profile.gender, fontSize = 16.sp, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        // Bio
        Text(text = "Bio:", fontSize = 16.sp, color = Color(0xFF00bf63))
        Text(text = profile.bio, fontSize = 16.sp, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        // Interests
        Text(text = "Interests:", fontSize = 16.sp, color = Color(0xFF00bf63))
        Text(
            text = if (profile.interests.isNotEmpty()) {
                profile.interests.joinToString(", ") { interest -> "${interest.emoji} ${interest.name}" }
            } else {
                "No interests specified"
            },
            fontSize = 16.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Hometown
        Text(text = "Hometown:", fontSize = 16.sp, color = Color(0xFF00bf63))
        Text(text = profile.hometown, fontSize = 16.sp, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        // High School
        Text(text = "High School:", fontSize = 16.sp, color = Color(0xFF00bf63))
        Text(text = profile.highSchool, fontSize = 16.sp, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        // College
        Text(text = "College:", fontSize = 16.sp, color = Color(0xFF00bf63))
        Text(text = profile.college, fontSize = 16.sp, color = Color.White)
    }
}


@Composable
fun UserInfoSectionDetailed(profile: Profile, onLeaderboardClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Ratings and Rankings
        ProfileText(label = "Rating", value = if (profile.numberOfRatings > 0) {
            "${profile.rating} from (${profile.numberOfRatings} ratings)"
        } else {
            "No rating yet"
        })
        ProfileText(label = "Global Ranking", value = profile.am24RankingGlobal.toString())
        ProfileText(label = "Age Ranking", value = profile.am24RankingAge.toString())
        ProfileText(label = "High School Ranking", value = profile.am24RankingHighSchool.toString())
        ProfileText(label = "College Ranking", value = profile.am24RankingCollege.toString())
        ProfileText(label = "Gender Ranking", value = profile.am24RankingGender.toString())
        ProfileText(label = "Hometown Ranking", value = profile.am24RankingHometown.toString())
        ProfileText(label = "Kupid Score (Composite)", value = profile.am24RankingCompositeScore.toString())
        ProfileText(label = "Level", value = profile.level.toString())

        Spacer(modifier = Modifier.height(8.dp))

        // Swipe and Match Metrics
        ProfileText(label = "Matches", value = profile.matchCount.toString())
        ProfileText(label = "Number of Swipe Rights", value = profile.numberOfSwipeRights.toString())
        ProfileText(label = "Number of Swipe Lefts", value = profile.numberOfSwipeLefts.toString())
        ProfileText(
            label = "Swipe Right to Left Ratio",
            value = String.format("%.2f", profile.getCalculatedSwipeRightToLeftRatio())
        )
        ProfileText(
            label = "Match Count per Swipe Right",
            value = String.format("%.2f", profile.getCalculatedMatchCountPerSwipeRight())
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Post Metrics
        ProfileText(label = "Cumulative Upvotes", value = profile.cumulativeUpvotes.toString())
        ProfileText(label = "Cumulative Downvotes", value = profile.cumulativeDownvotes.toString())
        ProfileText(
            label = "Average Upvotes per Post",
            value = String.format("%.2f", profile.averageUpvoteCount)
        )
        ProfileText(
            label = "Average Downvotes per Post",
            value = String.format("%.2f", profile.averageDownvoteCount)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Additional Info
        ProfileText(label = "Date Joined", value = formatDate(profile.dateOfJoin))
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}


@Composable
fun ProfileText(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "$label:",
            fontSize = 16.sp,
            color = Color(0xFF00bf63),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            fontSize = 16.sp,
            color = Color.White,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}


fun calculateAge(dob: String?): Int {
    if (dob.isNullOrBlank()) {
        return 0 // Handle missing DOB gracefully
    }
    val formats = listOf(
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
        SimpleDateFormat("dd/M/yyyy", Locale.getDefault())
    )

    for (format in formats) {
        try {
            val birthDate = format.parse(dob)
            if (birthDate != null) {
                val today = Calendar.getInstance()
                val birthDay = Calendar.getInstance().apply { time = birthDate }
                var age = today.get(Calendar.YEAR) - birthDay.get(Calendar.YEAR)
                if (today.get(Calendar.DAY_OF_YEAR) < birthDay.get(Calendar.DAY_OF_YEAR)) {
                    age--
                }
                return age
            }
        } catch (e: ParseException) {
            // Ignore and try the next format
        }
    }

    return 0 // Return 0 if none of the formats work
}


fun getLevelBorderColor(level: Int): Color {
    return when (level) {
        1 -> Color(0xFF00bf63)
        2 -> Color.Cyan
        3 -> Color.Blue
        4 -> Color.Magenta
        5 -> Color.Yellow
        6 -> Color.Red
        7 -> Color(0xFFFF4500) // OrangeRed
        else -> Color.Gray
    }
}
