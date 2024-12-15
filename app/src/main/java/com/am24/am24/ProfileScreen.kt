package com.am24.am24

import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
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
import kotlin.math.floor

@Composable
fun ProfileScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel,
    modifier: Modifier = Modifier,
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var userProfile by remember { mutableStateOf<Profile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isDetailedView by remember { mutableStateOf(false) }

    // Fetch user profile from Firebase
    LaunchedEffect(currentUserId) {
        profileViewModel.fetchUserProfile(currentUserId,
            onSuccess = { profile ->
                userProfile = profile
                isLoading = false
            },
            onFailure = {
                Log.e("ProfileScreen", "Failed to load profile: $it")
                isLoading = false
            }
        )
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFF6F00))
        }
    } else if (userProfile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Failed to load profile.",
                color = Color.White,
                fontSize = 18.sp
            )
        }
    } else {
        if (isDetailedView) {
            ProfileDetailsTabs(
                profile = userProfile!!,
                onCloseClick = { isDetailedView = false }
            )
        } else {
            ProfileContent(
                profile = userProfile!!,
                onEditProfileClick = { navController.navigate("editProfile") },
                onInfoClick = { isDetailedView = true }
            )
        }
    }
}

@Composable
fun ProfileContent(
    profile: Profile,
    onEditProfileClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    var currentPhotoIndex by remember(profile) { mutableStateOf(0) }
    val photoUrls = listOfNotNull(profile.profilepicUrl) + profile.optionalPhotoUrls

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = photoUrls[currentPhotoIndex],
            contentDescription = "Profile Photo",
            placeholder = painterResource(R.drawable.local_placeholder),
            error = painterResource(R.drawable.local_placeholder),
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
                },
            contentScale = ContentScale.Crop
        )

        // Edit and Info Icons
        IconButton(
            onClick = onEditProfileClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Profile",
                tint = Color(0xFFFFFFFF) // Set the Edit Profile icon color to #FFFFFFFF
            )
        }

        IconButton(
            onClick = onInfoClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "View More Info",
                tint = Color.White
            )
        }

        // Profile Details Overlay
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
                if (profile.hometown.isNotEmpty()) {
                    Text(
                        text = "From ${profile.hometown}",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                RatingBar(rating = profile.averageRating,ratingCount = profile.numberOfRatings)
                Text(
                    text = "Vibe Score: ${profile.vibepoints}",
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }

        // Dots Indicator for Photos
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

@Composable
fun ProfileDetailsTabs(profile: Profile, onCloseClick: () -> Unit) {
    val tabTitles = listOf("Profile", "Posts", "Saved")
    var selectedTabIndex by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Tab Row with Close Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.weight(1f)
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, color = Color.White) }
                    )
                }
            }

            // Close Button
            IconButton(
                onClick = { onCloseClick() }, // Calls the back action
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFFFF6F00)
                )
            }
        }

        // Tab Content
        when (selectedTabIndex) {
            0 -> ProfileDetails(profile)
            1 -> ProfilePosts(profile)
            2 -> ProfileSavedPosts(profile)
        }
    }
}

@Composable
fun ProfileDetails(profile: Profile) {
    val sections = listOf(
        "Basic Information" to Icons.Default.Person,
        "Preferences" to Icons.Default.Favorite,
        "Metrics" to Icons.Default.Assessment,
        "Lifestyle Attributes" to Icons.Default.Nature,
        "Interests" to Icons.Default.Star
    )
    var expandedSection by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Color.Black)
            .padding(16.dp)
    ) {
        sections.forEach { (title, icon) ->
            SectionHeader(title = title, icon = icon) {
                expandedSection = if (expandedSection == title) null else title
            }
            if (expandedSection == title) {
                Spacer(modifier = Modifier.height(8.dp))
                when (title) {
                    "Basic Information" -> BasicInfoSection(profile)
                    "Preferences" -> PreferencesSection(profile)
                    "Metrics" -> MetricsSection(profile)
                    "Lifestyle Attributes" -> LifestyleSection(profile)
                    "Interests" -> InterestsSection(profile)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .border(width = 1.dp, color = Color.White, shape = CircleShape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color(0xFFFF6F00),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}

@Composable
fun BasicInfoSection(profile: Profile) {
        ProfileDetailRow("Name", profile.name, Icons.Default.Person)
        ProfileDetailRow("Bio", profile.bio, Icons.Default.Edit)
        ProfileDetailRow("Gender", profile.gender, Icons.Default.Transgender)
        ProfileDetailRow("Hometown", profile.hometown, Icons.Default.LocationCity)
        ProfileDetailRow("High School", profile.highSchool, Icons.Default.School)
        ProfileDetailRow("College", profile.college, Icons.Default.AccountBalance)
        ProfileDetailRow("Post-Graduation", profile.postGraduation, Icons.Default.EmojiObjects)
        ProfileDetailRow("City", profile.city, Icons.Default.Place)
        ProfileDetailRow("Community", profile.community, Icons.Default.Groups)
        ProfileDetailRow("Religion", profile.religion, Icons.Default.Church)
}

@Composable
fun ProfileDetailRow(label: String, value: String?, icon: ImageVector) {
    if (!value.isNullOrBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFFFF6F00),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = value,
                    color = Color(0xFFFF6F00),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}


@Composable
fun PreferencesSection(profile: Profile) {
    ProfileDetailRow("Looking For", profile.lookingFor, Icons.Default.Favorite)
    ProfileDetailRow("Claimed Income Level", profile.claimedIncomeLevel, Icons.Default.AttachMoney)
}


@Composable
fun MetricsSection(profile: Profile) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ProfileDetailRow("Vibe Score", value = profile.am24RankingCompositeScore.toString(), Icons.Default.StackedLineChart)
        ProfileDetailRow(label = "Age Ranking", value = profile.am24RankingAge.toString(), Icons.Default.Cake)
        ProfileDetailRow(label = "High School Ranking", value = profile.am24RankingHighSchool.toString(), Icons.Default.School)
        ProfileDetailRow(label = "College Ranking", value = profile.am24RankingCollege.toString(), Icons.Default.Book)
        ProfileDetailRow(label = "Gender Ranking", value = profile.am24RankingGender.toString(), if( profile.gender == "Male") { Icons.Default.Male} else if( profile.gender == "Female") { Icons.Default.Female} else { Icons.Default.Transgender})
        ProfileDetailRow(label = "Hometown Ranking", value = profile.am24RankingHometown.toString(), Icons.Default.LocationCity)
        ProfileDetailRow("Matches", profile.matchCount.toString(), Icons.Default.People)
        ProfileDetailRow(
            "Match Count per Swipe Right",
            String.format("%.2f", profile.getCalculatedMatchCountPerSwipeRight()),
            Icons.Default.Swipe
        )
        ProfileDetailRow("Cumulative Upvotes", profile.cumulativeUpvotes.toString(), Icons.Default.ThumbUp)
        ProfileDetailRow("Cumulative Downvotes", profile.cumulativeDownvotes.toString(), Icons.Default.ThumbDown)
        ProfileDetailRow(
            label = "Average Upvotes per Post",
            value = String.format("%.2f", profile.averageUpvoteCount),
            Icons.Default.KeyboardDoubleArrowUp
        )
        ProfileDetailRow(
            label = "Average Downvotes per Post",
            value = String.format("%.2f", profile.averageDownvoteCount),
            Icons.Default.KeyboardDoubleArrowDown
        )
        ProfileDetailRow(label = "Date Joined", value = formatDate(profile.dateOfJoin), Icons.Default.DateRange)
    }
}

@Composable
fun LifestyleSection(profile: Profile) {
    Column(modifier = Modifier.fillMaxWidth()) {
        profile.lifestyle?.let { LifestyleSlider(label = "Smoking Level", value = it.smoking, Icons.Default.SmokingRooms) }
        profile.lifestyle?.let { LifestyleSlider(label = "Drinking Level", value = it.drinking, Icons.Default.LocalDrink) }
        profile.lifestyle?.let { LifestyleDropdown(label = "Diet", value = it.diet) }
        profile.lifestyle?.let { LifestyleSlider(label = "Indoorsy to Outdoorsy", value = it.indoorsyToOutdoorsy, Icons.Default.DirectionsWalk) }
        profile.lifestyle?.let { LifestyleSlider(label = "Social Butterfly", value = it.socialButterfly, Icons.Default.Groups2) }
        profile.lifestyle?.let { LifestyleSlider(label = "Work-Life Balance", value = it.workLifeBalance, Icons.Default.WorkOff) }
        profile.lifestyle?.let { LifestyleSlider(label = "Exercise Frequency", value = it.exerciseFrequency, Icons.Default.SportsGymnastics) }
        profile.lifestyle?.let { LifestyleSlider(label = "Family-Oriented", value = it.familyOriented, Icons.Default.FamilyRestroom) }
        // Add other lifestyle sliders similarly
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsSection(profile: Profile) {
    if (profile.interests.isEmpty()) {
        Text(
            text = "No interests specified.",
            color = Color.Gray,
            fontSize = 16.sp
        )
    } else {
        FlowRow(
        ) {
            profile.interests.forEach { interest ->
                Chip(
                    text = "${interest.emoji} ${interest.name}",
                    color = Color(0xFFFF6F00),
                    textColor = Color.White
                )
            }
        }
    }
}

@Composable
fun LifestyleSlider(label: String, value: Int, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Color(0xFFFF6F00))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Slider(
                value = value.toFloat(),
                onValueChange = {},
                valueRange = 0f..10f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF6F00),
                    activeTrackColor = Color(0xFFFF6F00)
                )
            )
        }
    }
}

@Composable
fun LifestyleDropdown(label: String, value: String) {
    Text(
        text = "$label: $value",
        color = Color.White,
        fontSize = 16.sp
    )
}


@Composable
fun ProfileSavedPosts(profile: Profile) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Saved Posts by ${profile.name}",
            color = Color.White,
            fontSize = 16.sp
        )
        // Dynamically fetch and display user's saved posts
    }
}

@Composable
fun Chip(text: String, color: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .background(color, shape = CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = text, color = textColor, fontSize = 14.sp)
    }
}

@Composable
fun RatingBar(rating: Double, ratingCount: Int) {
    val maxStars = 5
    val starSize = 20.dp
    val fullStars = kotlin.math.floor(rating).toInt()
    val fraction = rating - fullStars // fractional part (0.0 to <1.0)
    val orange = Color(0xFFFF6F00)
    val backgroundColor = Color.Black // match your background color

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Draw full stars
        repeat(fullStars) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = orange,
                modifier = Modifier.size(starSize)
            )
        }

        // Draw partially filled star if fraction > 0
        if (fraction > 0) {
            Box(modifier = Modifier.size(starSize)) {
                // Draw star border
                Icon(
                    imageVector = Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = orange,
                    modifier = Modifier.fillMaxSize()
                )

                // Draw full star
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = orange,
                    modifier = Modifier.fillMaxSize()
                )

                // Mask unfilled portion
                val fractionUnfilled = 1 - fraction
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(starSize * fractionUnfilled.toFloat())
                        .align(Alignment.CenterEnd)
                        .background(backgroundColor)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Display rating with two decimal places and the count in brackets
        Text(
            text = String.format("%.2f (%d)", rating, ratingCount), // Add count
            color = orange,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}




fun getLevelBorderColor(rating: Double): Color {
    return when {
        rating in 0.0..1.0 -> Color(0xFF444444)    // 0 to 1 Rating
        rating in 1.1..2.1 -> Color(0xFF555555)    // 1.1 to 2.0 Rating
        rating in 2.1..3.6 -> Color(0xFF886633)    // 2.1 to 3.0 Rating
        rating in 3.6..4.7 -> Color(0xFFAA6633)    // 3.1 to 4.0 Rating (same color as 2.1 to 3.0)
        rating in 4.7..5.0 -> Color(0xFFFF6F00)    // 4.1 to 5.0 Rating
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
