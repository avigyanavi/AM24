package com.am24.am24

import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProfileScreen(navController: NavController, modifier: Modifier = Modifier, otherUserId: String? = null) {
    ProfileScreenContent(navController = navController, modifier = modifier, otherUserId = otherUserId)
}

@Composable
fun ProfileScreenContent(
    navController: NavController,
    modifier: Modifier = Modifier,
    isOtherUserProfile: Boolean = false,
    otherUserId: String? = null
) {
    val userId = otherUserId ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
    val profile = remember { mutableStateOf(Profile()) }
    var currentPhotoIndex by remember { mutableStateOf(0) }
    var showDetails by remember { mutableStateOf(false) }
    var showMetrics by remember { mutableStateOf(false) }

    val firebaseDatabase = FirebaseDatabase.getInstance()
    val userRef = firebaseDatabase.getReference("users").child(userId)

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
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = BorderStroke(3.dp, getLevelBorderColor(profile.value.rating))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Profile Photo Carousel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val tapX = offset.x
                                    val photoUrls = listOfNotNull(profile.value.profilepicUrl) + profile.value.optionalPhotoUrls
                                    val photoCount = photoUrls.size
                                    if (photoCount > 1) {
                                        if (tapX > size.width / 2) {
                                            currentPhotoIndex = (currentPhotoIndex + 1).coerceAtMost(photoCount - 1)
                                        } else {
                                            currentPhotoIndex = (currentPhotoIndex - 1).coerceAtLeast(0)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    val photoUrls = listOfNotNull(profile.value.profilepicUrl) + profile.value.optionalPhotoUrls
                    if (photoUrls.isNotEmpty()) {
                        AsyncImage(
                            model = photoUrls[currentPhotoIndex],
                            contentDescription = "Profile Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

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
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "No Images", color = Color.White, fontSize = 16.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Username and Rating Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${profile.value.name}, ${calculateAge(profile.value.dob)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = Color.White
                    )

                    val ratingDisplay = if (profile.value.numberOfRatings > 0) {
                        "${profile.value.rating}★"
                    } else {
                        "No ratings"
                    }

                    Text(
                        text = ratingDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )

                    if (!isOtherUserProfile) {
                        IconButton(onClick = { navController.navigate("editProfile") }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Profile", tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Details Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showDetails = !showDetails }) {
                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Show Details", tint = Color.White)
                    }
                    Text(text = "Details", fontSize = 16.sp, color = Color.White)
                }

                if (showDetails) {
                    UserInfoSectionBasic(profile = profile.value)

                    // Metrics Toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showMetrics = !showMetrics }) {
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Show Metrics", tint = Color.White)
                        }
                        Text(text = "Metrics", fontSize = 16.sp, color = Color.White)
                    }
                }

                if (showMetrics) {
                    UserInfoSectionDetailed(profile = profile.value, onLeaderboardClick = {
                        navController.navigate("leaderboard")
                    })
                }


                Spacer(modifier = Modifier.height(16.dp))

                // View Likes and Leaderboard Buttons
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
        // Name, Gender, Bio, and other details
        ProfileText(label = "Name", value = (profile.name))
        Spacer(modifier = Modifier.height(8.dp))

        ProfileText(label = "Gender", value = profile.gender)
        Spacer(modifier = Modifier.height(8.dp))

        ProfileText(label = "Bio", value = profile.bio)
        Spacer(modifier = Modifier.height(8.dp))

        ProfileText(
            label = "Interests",
            value = if (profile.interests.isNotEmpty()) {
                profile.interests.joinToString(", ") { interest -> "${interest.emoji} ${interest.name}" }
            } else {
                "No interests specified"
            }
        )
        Spacer(modifier = Modifier.height(8.dp))

        ProfileText(label = "Hometown", value = profile.hometown)
        Spacer(modifier = Modifier.height(8.dp))

        ProfileText(label = "High School", value = profile.highSchool)
        Spacer(modifier = Modifier.height(8.dp))

        ProfileText(label = "College", value = profile.college)
        Spacer(modifier = Modifier.height(8.dp))

        ProfileText(label = "Community", value = profile.community)
        Spacer(modifier = Modifier.height(8.dp))

        ProfileText(label = "Religion", value = profile.religion)
        Spacer(modifier = Modifier.height(8.dp))

        ProfileText(label = "Level", value = profile.level.toString())
        Spacer(modifier = Modifier.height(8.dp))

        ProfileText(label = "Kupid Score (Composite)", value = profile.am24RankingCompositeScore.toString())
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun UserInfoSectionDetailed(
    profile: Profile,
    onLeaderboardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        // Detailed Metrics
        ProfileText(label = "Composite Score", value = profile.am24RankingCompositeScore.toString())
        ProfileText(label = "Rating", value = if (profile.numberOfRatings > 0) {
            "${profile.rating} from (${profile.numberOfRatings} ratings)"
        } else {
            "No rating yet"
        })
        ProfileText(label = "Age Ranking", value = profile.am24RankingAge.toString())
        ProfileText(label = "High School Ranking", value = profile.am24RankingHighSchool.toString())
        ProfileText(label = "College Ranking", value = profile.am24RankingCollege.toString())
        ProfileText(label = "Gender Ranking", value = profile.am24RankingGender.toString())
        ProfileText(label = "Hometown Ranking", value = profile.am24RankingHometown.toString())
        ProfileText(label = "Level", value = profile.level.toString())

        Spacer(modifier = Modifier.height(8.dp))

        // Swipe and Match Metrics
        ProfileText(label = "Matches", value = profile.matchCount.toString())
        ProfileText(label = "Number of Swipe Rights", value = profile.numberOfSwipeRights.toString())
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

@Composable
fun ProfileText(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "$label:",
            fontSize = 16.sp,
            color = Color.White,
            fontWeight = FontWeight.Normal
        )
        Text(
            text = value,
            fontSize = 24.sp,
            color = Color(0xFFFF4500),
            modifier = Modifier.padding(start = 15.dp)
        )
    }
}

fun getLevelBorderColor(rating: Double): Color {
    return when {
        rating in 0.0..1.0 -> Color(0xFFFFBE1A)    // 0 to 1 Rating
        rating in 1.1..2.0 -> Color(0xFFFFA500)    // 1.1 to 2.0 Rating
        rating in 2.1..3.0 -> Color(0xFFE68C00)    // 2.1 to 3.0 Rating
        rating in 3.1..4.0 -> Color(0xFFE68C00)    // 3.1 to 4.0 Rating (same color as 2.1 to 3.0)
        rating in 4.1..5.0 -> Color(0xFFF23800)    // 4.1 to 5.0 Rating
        else -> Color.Gray                         // Default color if rating is out of range
    }
}


fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
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