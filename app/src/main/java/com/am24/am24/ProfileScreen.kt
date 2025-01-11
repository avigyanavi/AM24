@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.am24.am24.ui.theme.White
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProfileScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel,
    postViewModel: PostViewModel,
    modifier: Modifier = Modifier
) {
    // 1) Wait for filters (and thus posts) to be loaded
    val filtersLoaded by postViewModel.filtersLoaded.collectAsState()

    if (!filtersLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFF6F00))
        }
        return
    }

    // 2) Grab all filtered posts + userProfiles
    val allPosts by postViewModel.filteredPosts.collectAsState()
    val userProfiles by postViewModel.userProfiles.collectAsState()

    // 3) Identify the current user
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    // 4) Attempt to get user’s profile from userProfiles or by fetching from Firebase
    var userProfile by remember { mutableStateOf<Profile?>(null) }
    LaunchedEffect(currentUserId) {
        val potentialProfile = userProfiles[currentUserId]
        if (potentialProfile != null) {
            userProfile = potentialProfile
        } else {
            profileViewModel.fetchUserProfile(
                userId = currentUserId,
                onSuccess = { fetchedProfile -> userProfile = fetchedProfile },
                onFailure = { error ->
                    Log.e("ProfileScreen", "Failed to load profile: $error")
                }
            )
        }
    }

    // 5) Filter user’s posts -> sort by upvotes desc -> top 5 are “featured”
    val myPosts = allPosts.filter { it.userId == currentUserId }
    val sortedByUpvotes = myPosts.sortedByDescending { it.upvotes }
    val featuredPosts = sortedByUpvotes.take(5)
    val remainingPosts = sortedByUpvotes.drop(5)

    // 6) Render once we have the profile
    if (userProfile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading your profile...", color = Color.White)
        }
    } else {
        ProfileLazyScreen(
            navController = navController,
            profile = userProfile!!,
            featuredPosts = featuredPosts,
            remainingPosts = remainingPosts
        )
    }
}

/**
 * Main LazyColumn structure:
 *  1) Photo carousel (no vibe score displayed)
 *  2) Collapsible sections (Basic Info, Preferences, Lifestyle, Interests)
 *  3) **Featured Posts** above Metrics
 *  4) Metrics (collapsed)
 *  5) “More Posts” if leftover
 */
@Composable
fun ProfileLazyScreen(
    navController: NavController,
    profile: Profile,
    featuredPosts: List<Post>,
    remainingPosts: List<Post>
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Helper to update profile in Firebase
    suspend fun updateProfileInFirebase(updatedProfile: Profile) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserId)

        val updates = mapOf(
            "name" to updatedProfile.name,
            "bio" to updatedProfile.bio,
            "gender" to updatedProfile.gender,
            "hometown" to updatedProfile.hometown,
            "highSchool" to updatedProfile.highSchool,
            "college" to updatedProfile.college,
            "postGraduation" to updatedProfile.postGraduation,
            "city" to updatedProfile.city,
            "community" to updatedProfile.community,
            "religion" to updatedProfile.religion,
            "lookingFor" to updatedProfile.lookingFor,
            "interests" to updatedProfile.interests.map {
                mapOf("name" to it.name, "emoji" to it.emoji)
            },
            "lifestyle" to updatedProfile.lifestyle
        )

        userRef.updateChildren(updates).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e("ProfileScreen", "Failed to update profile: ${task.exception}")
            } else {
                Log.d("ProfileScreen", "Profile updated successfully!")
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        state = listState
    ) {
        // 1) Photo Carousel
        item {
            PhotoCarouselWithOverlay(
                profile = profile,
                onEditProfileClick = {
                    // Instead of toggling an in-line edit mode, navigate to a new screen:
                    navController.navigate("editPicAndVoiceBio")
                }
            )
        }

// Right below the carousel, show the user’s voice note if it exists:
        item {
            if (!profile.voiceNoteUrl.isNullOrEmpty()) {
                // Some heading maybe:
                Text(
                    text = "Voice Bio",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(16.dp)
                )
                // e.g., a small placeholder or an actual audio player
                VoicePostPlaceholder() // or VoicePlayer(url = profile.voiceNoteUrl)
            }
        }


        // 2) Collapsible sections
        item {
            ProfileCollapsibleSections(
                profile = profile,
                onProfileUpdated = { updated ->
                    scope.launch {
                        updateProfileInFirebase(updated)
                    }
                }
            )
        }

        // 3) Featured Posts ABOVE Metrics
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

        // 4) Metrics (collapsed)
        item {
            CollapsedMetricsSection(profile = profile)
        }

        // 5) “More Posts” button
        if (remainingPosts.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            // Possibly navigate to a full "MyPostsScreen"
                            Log.d("Profile", "View More Posts clicked!")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                    ) {
                        Text(text = "View More Posts", color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/** Collapsible sections with inline editing. */
@Composable
fun ProfileCollapsibleSections(
    profile: Profile,
    onProfileUpdated: (Profile) -> Unit
) {
    // Collapsible states
    var showBasic by rememberSaveable { mutableStateOf(true) }
    var showPreferences by rememberSaveable { mutableStateOf(true) }
    var showLifestyle by rememberSaveable { mutableStateOf(true) }
    var showInterests by rememberSaveable { mutableStateOf(true) }

    // Edit states
    var editBasic by rememberSaveable { mutableStateOf(false) }
    var editPreferences by rememberSaveable { mutableStateOf(false) }
    var editLifestyle by rememberSaveable { mutableStateOf(false) }
    var editInterests by rememberSaveable { mutableStateOf(false) }

    // Local copy of profile for editing
    var tempProfile by remember { mutableStateOf(profile) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Basic Info
        CollapsibleSection(
            title = "Basic Information",
            icon = Icons.Default.Person,
            isExpanded = showBasic,
            onToggle = { showBasic = !showBasic },
            editMode = editBasic,
            onEditToggle = { editBasic = !editBasic }
        ) {
            if (editBasic) {
                BasicInfoEditSection(
                    tempProfile = tempProfile,
                    onSave = { updated ->
                        tempProfile = updated
                        onProfileUpdated(tempProfile)
                        editBasic = false
                    },
                    onCancel = {
                        tempProfile = profile
                        editBasic = false
                    }
                )
            } else {
                BasicInfoSection(tempProfile)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Preferences
        CollapsibleSection(
            title = "Preferences",
            icon = Icons.Default.Favorite,
            isExpanded = showPreferences,
            onToggle = { showPreferences = !showPreferences },
            editMode = editPreferences,
            onEditToggle = { editPreferences = !editPreferences }
        ) {
            if (editPreferences) {
                PreferencesEditSection(
                    tempProfile = tempProfile,
                    onSave = { updated ->
                        tempProfile = updated
                        onProfileUpdated(tempProfile)
                        editPreferences = false
                    },
                    onCancel = {
                        tempProfile = profile
                        editPreferences = false
                    }
                )
            } else {
                PreferencesSection(tempProfile)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Lifestyle
        CollapsibleSection(
            title = "Lifestyle Attributes",
            icon = Icons.Default.Nature,
            isExpanded = showLifestyle,
            onToggle = { showLifestyle = !showLifestyle },
            editMode = editLifestyle,
            onEditToggle = { editLifestyle = !editLifestyle }
        ) {
            if (editLifestyle) {
                LifestyleEditSection(
                    tempProfile = tempProfile,
                    onSave = { updated ->
                        tempProfile = updated
                        onProfileUpdated(tempProfile)
                        editLifestyle = false
                    },
                    onCancel = {
                        tempProfile = profile
                        editLifestyle = false
                    }
                )
            } else {
                LifestyleSection(tempProfile)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Interests
        CollapsibleSection(
            title = "Interests",
            icon = Icons.Default.Star,
            isExpanded = showInterests,
            onToggle = { showInterests = !showInterests },
            editMode = editInterests,
            onEditToggle = { editInterests = !editInterests }
        ) {
            if (editInterests) {
                InterestsEditSection(
                    tempProfile = tempProfile,
                    onSave = { updated ->
                        tempProfile = updated
                        onProfileUpdated(tempProfile)
                        editInterests = false
                    },
                    onCancel = {
                        tempProfile = profile
                        editInterests = false
                    }
                )
            } else {
                InterestsSectionInProfile(tempProfile)
            }
        }
    }
}

/**
 * A CollapsibleSection with an optional EDIT icon in the header.
 *
 *  - editMode: whether currently editing
 *  - onEditToggle: function to toggle edit mode
 */
@Composable
fun CollapsibleSection(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    editMode: Boolean = false,
    onEditToggle: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = Color.White, shape = CircleShape)
            .background(Color.Black)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color(0xFFFF6F00),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))

        // Title
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f)
        )

        // Always show an icon button, toggling pencil vs. close
        IconButton(onClick = onEditToggle) {
            Icon(
                imageVector = if (editMode) Icons.Default.Close else Icons.Default.Edit,
                contentDescription = if (editMode) "Cancel Edit" else "Edit",
                tint = if (editMode) Color.Red else Color.White
            )
        }

        // Expand/Collapse icon
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = Color.White
            )
        }
    }

    if (isExpanded) {
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

/** Photo carousel with top-right Edit icon */
@Composable
fun PhotoCarouselWithOverlay(
    profile: Profile,
    onEditProfileClick: () -> Unit
) {
    val photoUrls = listOfNotNull(profile.profilepicUrl) + profile.optionalPhotoUrls
    var currentPhotoIndex by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(7f / 10f)
            .background(Color.Black)
            .pointerInput(photoUrls) {
                detectTapGestures(
                    onTap = { offset ->
                        if (photoUrls.size > 1) {
                            if (offset.x > size.width / 2) {
                                currentPhotoIndex = (currentPhotoIndex + 1) % photoUrls.size
                            } else {
                                currentPhotoIndex =
                                    (currentPhotoIndex - 1 + photoUrls.size) % photoUrls.size
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
                placeholder = painterResource(R.drawable.local_placeholder),
                error = painterResource(R.drawable.local_placeholder),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Multi-photo "dots" at the top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                photoUrls.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .width(if (index == currentPhotoIndex) 30.dp else 10.dp)
                            .height(4.dp)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (index == currentPhotoIndex) Color.White else Color.Gray)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No Images", color = Color.White)
            }
        }

        // A bottom overlay for name, age, hometown, rating, etc.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 1f))
                .padding(16.dp)
        ) {
            Column {
                val age = calculateAge(profile.dob)
                Text(
                    text = if (age > 0) "${profile.name}, $age" else profile.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = Color.White
                )
                if (profile.hometown.isNotBlank()) {
                    Text("From ${profile.hometown}", fontSize = 16.sp, color = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
                RatingBar(rating = profile.averageRating, ratingCount = profile.numberOfRatings)
            }
        }

        // Top-right edit icon => navigates to another screen
        IconButton(
            onClick = onEditProfileClick, // we’ll define it to navigate away
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Profile / Photos",
                tint = Color.White
            )
        }
    }
}


/** Metrics collapsible */
@Composable
fun CollapsedMetricsSection(profile: Profile) {
    var showMetrics by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = Color.White, shape = CircleShape)
                .background(Color.Black)
                .clickable { showMetrics = !showMetrics }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Assessment,
                contentDescription = "Metrics",
                tint = Color(0xFFFF6F00),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Metrics",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (showMetrics) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (showMetrics) "Collapse" else "Expand",
                tint = Color.White
            )
        }
        if (showMetrics) {
            Spacer(modifier = Modifier.height(8.dp))
            MetricsSection(profile)
        }
    }
}

/** Basic Info (View-Only) */
@Composable
fun BasicInfoSection(profile: Profile) {
    ProfileDetailRow("Name", profile.name, Icons.Default.Person)
    ProfileDetailRow("Bio", profile.bio, Icons.Default.Info)
    ProfileDetailRow("Gender", profile.gender, Icons.Default.Transgender)
    ProfileDetailRow("Hometown", profile.hometown, Icons.Default.LocationCity)
    ProfileDetailRow("High School", profile.highSchool, Icons.Default.School)
    ProfileDetailRow("College", profile.college, Icons.Default.AccountBalance)
    ProfileDetailRow("Post-Graduation", profile.postGraduation, Icons.Default.EmojiObjects)
    ProfileDetailRow("City", profile.city, Icons.Default.Place)
    ProfileDetailRow("Community", profile.community, Icons.Default.Groups)
    ProfileDetailRow("Religion", profile.religion, Icons.Default.Church)
}

/** Preferences (View-Only) */
@Composable
fun PreferencesSection(profile: Profile) {
    ProfileDetailRow("Looking For", profile.lookingFor, Icons.Default.Favorite)
}

/** Lifestyle (View-Only) */
@Composable
fun LifestyleSection(profile: Profile) {
    Column {
        profile.lifestyle?.let {
            LifestyleSlider("Smoking Level", it.smoking, Icons.Default.SmokingRooms)
            LifestyleSlider("Drinking Level", it.drinking, Icons.Default.LocalDrink)
            LifestyleDropdown("Diet", it.diet)
            LifestyleSlider("Indoorsy to Outdoorsy", it.indoorsyToOutdoorsy, Icons.Default.DirectionsWalk)
            LifestyleSlider("Social Butterfly", it.socialMedia, Icons.Default.Groups2)
            LifestyleSlider("Work-Life Balance", it.workLifeBalance, Icons.Default.WorkOff)
            LifestyleSlider("Exercise Frequency", it.exerciseFrequency, Icons.Default.SportsGymnastics)
            LifestyleSlider("Family-Oriented", it.familyOriented, Icons.Default.FamilyRestroom)
        }
    }
}

/** Interests (View-Only) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsSectionInProfile(profile: Profile) {
    if (profile.interests.isEmpty()) {
        Text("No interests specified.", color = Color.Gray, fontSize = 16.sp)
    } else {
        FlowRow {
            profile.interests.forEach { interest ->
                InterestTag(
                    label = buildString {
                        if (!interest.emoji.isNullOrEmpty()) append("${interest.emoji} ")
                        append(interest.name)
                    }
                )
            }
        }
    }
}

/** Metrics (View-Only) */
@Composable
fun MetricsSection(profile: Profile) {
    Column {
        ProfileDetailRow("Kolkata Ranking", profile.am24Ranking.toString(), Icons.Filled.Language)
        ProfileDetailRow("Age Ranking", profile.am24RankingAge.toString(), Icons.Default.Cake)
        ProfileDetailRow("High School Ranking", profile.am24RankingHighSchool.toString(), Icons.Default.School)
        ProfileDetailRow("College Ranking", profile.am24RankingCollege.toString(), Icons.Default.Book)
        ProfileDetailRow(
            "Gender Ranking",
            profile.am24RankingGender.toString(),
            if (profile.gender == "Male") Icons.Default.Male else if (profile.gender == "Female") Icons.Default.Female else Icons.Default.Transgender
        )
        ProfileDetailRow("${profile.hometown} Ranking", profile.am24RankingHometown.toString(), Icons.Default.LocationCity)
        ProfileDetailRow("Matches", profile.matchCount.toString(), Icons.Default.People)
        ProfileDetailRow(
            "Match Count per Swipe Right",
            String.format("%.2f", profile.getCalculatedMatchCountPerSwipeRight()),
            Icons.Default.Swipe
        )
        ProfileDetailRow("Cumulative Upvotes", profile.cumulativeUpvotes.toString(), Icons.Default.ThumbUp)
        ProfileDetailRow("Cumulative Downvotes", profile.cumulativeDownvotes.toString(), Icons.Default.ThumbDown)
        ProfileDetailRow(
            "Average Upvotes per Post",
            String.format("%.2f", profile.averageUpvoteCount),
            Icons.Default.KeyboardDoubleArrowUp
        )
        ProfileDetailRow(
            "Average Downvotes per Post",
            String.format("%.2f", profile.averageDownvoteCount),
            Icons.Default.KeyboardDoubleArrowDown
        )
        ProfileDetailRow("Date Joined", formatDate(profile.dateOfJoin), Icons.Default.DateRange)
    }
}

/** --- Edit Composables --- */

/** Basic Info Edit */
@Composable
fun BasicInfoEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(tempProfile.name) }
    var bio by remember { mutableStateOf(tempProfile.bio) }
    var gender by remember { mutableStateOf(tempProfile.gender) }
    var hometown by remember { mutableStateOf(tempProfile.hometown) }
    var highSchool by remember { mutableStateOf(tempProfile.highSchool) }
    var college by remember { mutableStateOf(tempProfile.college) }
    var postGrad by remember { mutableStateOf(tempProfile.postGraduation) }
    var city by remember { mutableStateOf(tempProfile.city) }
    var community by remember { mutableStateOf(tempProfile.community) }
    var religion by remember { mutableStateOf(tempProfile.religion) }

    Column {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name", color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text("Bio", color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = gender,
            onValueChange = { gender = it },
            label = { Text("Gender", color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = hometown,
            onValueChange = { hometown = it },
            label = { Text("Hometown", color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = highSchool,
            onValueChange = { highSchool = it },
            label = { Text("High School", color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = college,
            onValueChange = { college = it },
            label = { Text("College", color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        postGrad?.let {
            OutlinedTextField(
                value = it,
                onValueChange = { postGrad = it },
                label = { Text("Post-Graduation", color = Color(0xFFFF6F00)) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    cursorColor = Color(0xFFFF6F00),
                    focusedTextColor = Color.White
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("City", color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = community,
            onValueChange = { community = it },
            label = { Text("Community", color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = religion,
            onValueChange = { religion = it },
            label = { Text("Religion", color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(
                onClick = {
                    onSave(
                        tempProfile.copy(
                            name = name,
                            bio = bio,
                            gender = gender,
                            hometown = hometown,
                            highSchool = highSchool,
                            college = college,
                            postGraduation = postGrad,
                            city = city,
                            community = community,
                            religion = religion
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
            ) {
                Text("Save", color = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Cancel", color = Color.White)
            }
        }
    }
}

/** Preferences Edit */
@Composable
fun PreferencesEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    var lookingFor by remember { mutableStateOf(tempProfile.lookingFor) }

    Column {
        OutlinedTextField(
            value = lookingFor,
            onValueChange = { lookingFor = it },
            label = { Text("Looking For", color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(
                onClick = {
                    onSave(tempProfile.copy(lookingFor = lookingFor))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
            ) {
                Text("Save", color = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Cancel", color = Color.White)
            }
        }
    }
}

/** Lifestyle Edit */
@Composable
fun LifestyleEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    var localLifestyle by remember { mutableStateOf(tempProfile.lifestyle ?: Lifestyle()) }

    Column {
        // Smoking
        Text("Smoking Level", color = Color.White)
        Slider(
            value = localLifestyle.smoking.toFloat(),
            onValueChange = { localLifestyle = localLifestyle.copy(smoking = it.toInt()) },
            valueRange = 0f..10f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00bf63),
                activeTrackColor = Color(0xFF00bf63)
            )
        )
        Text("Current: ${localLifestyle.smoking}", color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        // Drinking
        Text("Drinking Level", color = Color.White)
        Slider(
            value = localLifestyle.drinking.toFloat(),
            onValueChange = { localLifestyle = localLifestyle.copy(drinking = it.toInt()) },
            valueRange = 0f..10f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00bf63),
                activeTrackColor = Color(0xFF00bf63)
            )
        )
        Text("Current: ${localLifestyle.drinking}", color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        // ... repeat for other attributes if needed

        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(
                onClick = {
                    onSave(tempProfile.copy(lifestyle = localLifestyle))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
            ) {
                Text("Save", color = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Cancel", color = Color.White)
            }
        }
    }
}

/** Interests Edit */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    val localInterests = remember {
        mutableStateListOf<Interest>().apply { addAll(tempProfile.interests) }
    }
    var newInterest by remember { mutableStateOf("") }

    Column {
        // Existing interests
        FlowRow {
            localInterests.forEach { interest ->
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .background(Color(0xFFFF6F00), shape = CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable { localInterests.remove(interest) },
                ) {
                    Text(text = "${interest.emoji} ${interest.name}", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Add new interest
        OutlinedTextField(
            value = newInterest,
            onValueChange = { newInterest = it },
            label = { Text("Add new interest", color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val splitted = newInterest.trim().split(" ", limit = 2)
                val emojiPart = if (splitted.size > 1) splitted[0] else ""
                val namePart = if (splitted.size > 1) splitted[1] else splitted[0]

                if (namePart.isNotBlank()) {
                    localInterests.add(
                        Interest(name = namePart, emoji = emojiPart)
                    )
                }
                newInterest = ""
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
        ) {
            Text("Add Interest", color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(
                onClick = {
                    onSave(tempProfile.copy(interests = localInterests.toList()))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
            ) {
                Text("Save", color = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Cancel", color = Color.White)
            }
        }
    }
}

/** Reusable building blocks */
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
fun InterestTag(label: String) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .background(Color(0xFFFF6F00), shape = CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun VoicePostPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF333333)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.AudioFile,
            contentDescription = "Voice Post",
            tint = Color(0xFFFFBF00),
            modifier = Modifier.padding(8.dp)
        )
        Text("Voice Post (Tap to Play/Pause)", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun PostItemInProfile(post: Post) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (!post.contentText.isNullOrBlank()) {
                Text(post.contentText, color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }
            when (post.mediaType) {
                "photo" -> {
                    AsyncImage(
                        model = post.mediaUrl,
                        contentDescription = "Photo Post",
                        placeholder = painterResource(R.drawable.local_placeholder),
                        error = painterResource(R.drawable.local_placeholder),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp, max = 300.dp)
                    )
                }
                "voice" -> {
                    VoicePostPlaceholder()
                }
                "video" -> {
                    Text("Video Post (placeholder UI)", color = Color.White, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text("Upvotes: ${post.upvotes}", color = Color(0xFFFFBF00), fontSize = 12.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Downvotes: ${post.downvotes}", color = Color(0xFFFF6F00), fontSize = 12.sp)
            }
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
                onValueChange = {}, // read-only
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

/** RatingBar (no vibe score) */
@Composable
fun RatingBar(rating: Double, ratingCount: Int) {
    val starSize = 25.dp
    val fullStars = kotlin.math.floor(rating).toInt()
    val fraction = rating - fullStars
    val orange = Color(0xFFFF6F00)
    val backgroundColor = Color.Black

    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(fullStars) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = orange,
                modifier = Modifier.size(starSize)
            )
        }
        if (fraction > 0) {
            Box(modifier = Modifier.size(starSize)) {
                Icon(
                    imageVector = Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = orange,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = orange,
                    modifier = Modifier.fillMaxSize()
                )
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
        Text(
            text = String.format("%.2f (%d)", rating, ratingCount),
            color = White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

/** Helpers */
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun calculateAge(dob: String?): Int {
    if (dob.isNullOrBlank()) return 0
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
        } catch (_: ParseException) {
        }
    }
    return 0
}
