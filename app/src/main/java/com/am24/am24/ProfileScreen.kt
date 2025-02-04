@file:OptIn(
    ExperimentalMaterial3Api::class,
)

package com.am24.am24

import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun ProfileScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel,
    postViewModel: PostViewModel,
    modifier: Modifier = Modifier
) {
    val filtersLoaded by postViewModel.filtersLoaded.collectAsState()

    if (!filtersLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFF6F00))
        }
        return
    }

    val allPosts by postViewModel.filteredPosts.collectAsState()
    val userProfiles by postViewModel.userProfiles.collectAsState()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

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

    val myPosts = allPosts.filter { it.userId == currentUserId }
    val sortedByUpvotes = myPosts.sortedByDescending { it.upvotes }
    val featuredPosts = sortedByUpvotes.take(5)
    val remainingPosts = sortedByUpvotes.drop(5)

    if (userProfile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading your profile...", color = Color.White)
        }
    } else {
        ProfileLazyScreen(
            navController = navController,
            profile = userProfile!!,
            featuredPosts = featuredPosts,
            remainingPosts = remainingPosts,
            profileViewModel = profileViewModel
        )
    }
}

@Composable
fun ProfileLazyScreen(
    navController: NavController,
    profile: Profile,
    featuredPosts: List<Post>,
    remainingPosts: List<Post>,
    profileViewModel: ProfileViewModel
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        state = listState
    ) {
        item {
            PhotoCarouselWithOverlay(
                profile = profile,
                onEditProfileClick = { navController.navigate("editPicAndVoiceBio") }
            )
        }
        item {
            ProfileCompletionIndicator(profile)
        }
        item {
            ProfileCollapsibleSections(
                profile = profile,
                profileViewModel = profileViewModel,
                onProfileUpdated = { updated ->
                    scope.launch { updateProfileInFirebase(updated) }
                }
            )
        }
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
        item {
            CollapsedMetricsSection(profile = profile)
        }
        if (remainingPosts.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
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

/**
 * Main LazyColumn structure:
 *  1) Photo carousel
 *  2) Profile completion indicator
 *  3) Collapsible sections (Basic Info, Preferences, Lifestyle, Interests)
 *  4) Featured Posts above Metrics
 *  5) Metrics (collapsed)
 *  6) “More Posts” if leftover
 */

@Composable
fun ProfileCollapsibleSections(
    profile: Profile,
    profileViewModel: ProfileViewModel,
    onProfileUpdated: (Profile) -> Unit
) {
    var showBio by rememberSaveable { mutableStateOf(true) }
    var showVoiceBio by rememberSaveable { mutableStateOf(true) }
    var showBasic by rememberSaveable { mutableStateOf(true) }
    var showPreferences by rememberSaveable { mutableStateOf(true) }
    var showLifestyle by rememberSaveable { mutableStateOf(true) }
    var showInterests by rememberSaveable { mutableStateOf(true) }
    var editBio by rememberSaveable { mutableStateOf(false) }
    var editVoiceBio by rememberSaveable { mutableStateOf(false) }
    var editBasic by rememberSaveable { mutableStateOf(false) }
    var editPreferences by rememberSaveable { mutableStateOf(false) }
    var editLifestyle by rememberSaveable { mutableStateOf(false) }
    var editInterests by rememberSaveable { mutableStateOf(false) }
    var tempProfile by remember { mutableStateOf(profile) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        CollapsibleSection(
            title = "Bio",
            icon = Icons.Default.Info,
            isExpanded = showBio,
            onToggle = { showBio = !showBio },
            editMode = editBio,
            onEditToggle = { editBio = !editBio }
        ) {
            if (editBio) {
                BioEditSection(
                    currentBio = tempProfile.bio,
                    onSave = { updatedBio ->
                        // Only update the bio field
                        tempProfile = tempProfile.copy(bio = updatedBio)
                        onProfileUpdated(tempProfile)
                        editBio = false
                    },
                    onCancel = {
                        // Reset any temporary changes if needed
                        editBio = false
                    }
                )
            } else {
                Text(
                    text = profile.bio ?: "No bio available",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        CollapsibleSection(
            title = "Voice Bio",
            icon = Icons.Default.Mic,
            isExpanded = showVoiceBio,
            onToggle = { showVoiceBio = !showVoiceBio },
            editMode = editVoiceBio,
            onEditToggle = { editVoiceBio = !editVoiceBio }
        ) {
            if (editVoiceBio) {
                EditVoiceNoteSection(
                    profileViewModel = profileViewModel,
                    onVoiceNoteUpdated = { updatedUrl ->
                        tempProfile = tempProfile.copy(voiceNoteUrl = updatedUrl)
                        onProfileUpdated(tempProfile)
                        editVoiceBio = false
                    }
                )
            } else {
                if (!profile.voiceNoteUrl.isNullOrEmpty()) {
                    VoicePlayer(url = profile.voiceNoteUrl)
                } else {
                    Text(text = "No voice bio available", color = Color.White, fontSize = 16.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
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

        // Pencil icon or close icon
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
        Spacer(Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }

        Spacer(Modifier.height(8.dp))
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
            onClick = onEditProfileClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Gray.copy(alpha = 0.5f), shape = CircleShape)
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

/** Display a horizontal progress for completion. */
@Composable
fun ProfileCompletionIndicator(profile: Profile) {
    val completion = profile.profileCompletionPercentage

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = "Profile Completion: $completion%", color = Color.White)
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { completion / 100f },
            color = Color(0xFFFF6F00.toInt()),
            trackColor = Color.Gray,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
    }
}

@Composable
fun EditVoiceNoteSection(
    profileViewModel: ProfileViewModel, // or RegistrationViewModel if you prefer
    onVoiceNoteUpdated: (String) -> Unit
) {
    val context = LocalContext.current
    val storageRef = FirebaseStorage.getInstance().reference
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var voiceNoteUri by remember { mutableStateOf<Uri?>(null) }
    var voiceProgress by remember { mutableStateOf(0f) }
    var voiceDuration by remember { mutableStateOf(0L) }
    val mediaPlayer = remember { MediaPlayer() }
    val coroutineScope = rememberCoroutineScope()
    // File path for the new voice note:
    val filePath = remember { File(context.filesDir, "voice_note_edit.mp3").absolutePath }

    // Toggle recording: if recording, stop it and upload the new voice note; if not, start recording.
    val toggleRecording: () -> Unit = {
        if (isRecording) {
            isRecording = false
            profileViewModel.stopVoiceRecording() // Stop the recording
            // Create a Uri from the recorded file
            voiceNoteUri = Uri.fromFile(File(filePath))
            // Upload the voice note; assume uploadVoiceToFirebase updates profileViewModel.voiceNoteUrl
            profileViewModel.uploadVoiceToRealtime(storageRef, voiceNoteUri!!)
            onVoiceNoteUpdated(profileViewModel.voiceNoteUrl ?: "")
        } else {
            isRecording = true
            profileViewModel.startVoiceRecording(context, filePath)
        }
    }

    // Toggle playback for testing the new voice note:
    val togglePlayback: () -> Unit = {
        if (isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
        } else {
            try {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(voiceNoteUri?.path ?: filePath)
                mediaPlayer.prepare()
                mediaPlayer.start()
                isPlaying = true
                voiceDuration = mediaPlayer.duration.toLong().coerceAtLeast(1L)
            } catch (e: IOException) {
                Log.e("EditVoiceNoteSection", "Playback error: ${e.message}")
            }
        }
    }

    // Update playback progress while playing:
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            coroutineScope.launch {
                while (isPlaying && mediaPlayer.isPlaying) {
                    voiceProgress = (mediaPlayer.currentPosition / voiceDuration.toFloat()).coerceIn(0f, 1f)
                    delay(500)
                }
            }
        } else {
            voiceProgress = 0f
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (mediaPlayer.isPlaying) mediaPlayer.stop()
            mediaPlayer.release()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Edit Voice Bio",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        IconButton(onClick = toggleRecording) {
            Icon(
                imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = "Record Voice Bio",
                tint = if (isRecording) Color.Red else Color.White,
                modifier = Modifier
                    .size(64.dp)
                    // Added circular background (see change 3 below for a similar concept)
                    .background(Color.Gray.copy(alpha = 0.5f), shape = CircleShape)
                    .clip(CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (voiceNoteUri != null || filePath.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                IconButton(onClick = togglePlayback) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play Voice Bio",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Slider(
                    value = voiceProgress,
                    onValueChange = {},
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )
            }
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
    ProfileDetailRow("Email", profile.email, Icons.Default.Email)

    val genderIcon = when (profile.gender.lowercase()) {
        "male" -> Icons.Default.Male
        "female" -> Icons.Default.Female
        else -> Icons.Default.Transgender
    }
    ProfileDetailRow("Name", profile.name, Icons.Default.Person)
    ProfileDetailRow(
        label = "Popularity Score",
        value = "${(profile.averageSwipeRightsOnUser * 100).roundToInt()}%",
        icon = Icons.Default.Star
    )
    ProfileDetailRow("Gender", profile.gender, genderIcon)
    ProfileDetailRow("Locality", profile.hometown, Icons.Default.LocationCity)
    ProfileDetailRow("High School", profile.highSchool, Icons.Default.School)
    ProfileDetailRow("College", profile.college, Icons.Default.AccountBalance)
    ProfileDetailRow("Post-Graduation", profile.postGraduation, Icons.Default.EmojiObjects)
    ProfileDetailRow("Community", profile.community, Icons.Default.Groups)
    ProfileDetailRow("Religion", profile.religion, Icons.Default.Church)
}

/** Preferences (View-Only) */
@Composable
fun PreferencesSection(profile: Profile) {
    val lookingForText = profile.lookingFor.takeIf { it.isNotBlank() } ?: "Not specified"
    ProfileDetailRow("Looking For", lookingForText, Icons.Default.Favorite)
}

@Composable
fun LifestyleSection(profile: Profile) {
    Column {
        profile.lifestyle?.let { lifestyle ->
            if (lifestyle.smoking != -1)
                LifestyleSlider(
                    label = "Smoking",
                    value = lifestyle.smoking,
                    nouns = listOf("Non-Smoker", "Rare Smoker", "Social Smoker", "Frequent Smoker", "Heavy Smoker"),
                    icon = Icons.Default.SmokingRooms
                )
            if (lifestyle.drinking != -1)
                LifestyleSlider(
                    label = "Drinking",
                    value = lifestyle.drinking,
                    nouns = listOf("Non-Drinker", "Rare Drinker", "Social Drinker", "Frequent Drinker", "Heavy Drinker"),
                    icon = Icons.Default.LocalDrink
                )
            if (lifestyle.indoorsyToOutdoorsy != -1)
                LifestyleSlider(
                    label = "Going out",
                    value = lifestyle.indoorsyToOutdoorsy,
                    nouns = listOf("Very Indoorsy", "Mostly Indoorsy", "Balanced", "Mostly Outdoorsy", "Very Outdoorsy"),
                    icon = Icons.Default.DirectionsWalk
                )
            if (lifestyle.socialMedia != -1)
                LifestyleSlider(
                    label = "Social Media",
                    value = lifestyle.socialMedia,
                    nouns = listOf("Invisible", "Watcher", "Casual Participant", "Engager", "Influencer"),
                    icon = Icons.Default.Groups2
                )
            if (lifestyle.workLifeBalance != -1)
                LifestyleSlider(
                    label = "Work-Life Balance",
                    value = lifestyle.workLifeBalance,
                    nouns = listOf("Workaholic", "More Work-Oriented", "Balanced", "More Life-Oriented", "Relaxed"),
                    icon = Icons.Default.WorkOff
                )
            if (lifestyle.exerciseFrequency != -1)
                LifestyleSlider(
                    label = "Exercise Frequency",
                    value = lifestyle.exerciseFrequency,
                    nouns = listOf("Inactive", "Rarely Active", "Moderately Active", "Active", "Very Active"),
                    icon = Icons.Default.SportsGymnastics
                )
            if (lifestyle.familyOriented != -1)
                LifestyleSlider(
                    label = "Family-Oriented",
                    value = lifestyle.familyOriented,
                    nouns = listOf("Independent", "Slightly Family-Oriented", "Balanced", "Family-Oriented", "Very Family-Oriented"),
                    icon = Icons.Default.FamilyRestroom
                )
            if (lifestyle.diet.isNotBlank())
                LifestyleDropdown("Diet", lifestyle.diet, Icons.Default.Restaurant)
            if (lifestyle.sleepCycle != -1)
                LifestyleSlider(
                    label = "Sleep Cycle",
                    value = lifestyle.sleepCycle,
                    nouns = listOf("Early Riser", "Morning Person", "Balanced", "Night Owl", "Late Night Enthusiast"),
                    icon = Icons.Default.Bedtime
                )
            if (lifestyle.adventurous != -1)
                LifestyleSlider(
                    label = "Adventurous",
                    value = lifestyle.adventurous,
                    nouns = listOf("Cautious", "Slightly Adventurous", "Moderately Adventurous", "Adventurous", "Thrill Seeker"),
                    icon = Icons.Default.Hiking
                )
            if (lifestyle.petFriendly)
                LifestyleDropdown("Pet Friendly", "Yes", Icons.Default.Pets)
            if (lifestyle.intellectual != -1)
                LifestyleSlider(
                    label = "Intellectual",
                    value = lifestyle.intellectual,
                    nouns = listOf("Casual Thinker", "Inquisitive", "Knowledge Seeker", "Intellectual", "Philosopher"),
                    icon = Icons.Default.School
                )
            if (lifestyle.creativeArtistic != -1)
                LifestyleSlider(
                    label = "Creative/Artistic",
                    value = lifestyle.creativeArtistic,
                    nouns = listOf("Not Creative", "Somewhat Creative", "Creative", "Very Creative", "Artistic Genius"),
                    icon = Icons.Default.Palette
                )
            if (lifestyle.fitnessLevel != -1)
                LifestyleSlider(
                    label = "Fitness Level",
                    value = lifestyle.fitnessLevel,
                    nouns = listOf("Sedentary", "Somewhat Fit", "Fit", "Athletic", "Peak Fitness"),
                    icon = Icons.Default.FitnessCenter
                )
            if (lifestyle.spiritualMindful != -1)
                LifestyleSlider(
                    label = "Spiritual/Mindful",
                    value = lifestyle.spiritualMindful,
                    nouns = listOf("Not Spiritual", "Occasionally Mindful", "Balanced", "Spiritual", "Deeply Mindful"),
                    icon = Icons.Default.SelfImprovement
                )
            if (lifestyle.humorousEasyGoing != -1)
                LifestyleSlider(
                    label = "Humorous/Easy-Going",
                    value = lifestyle.humorousEasyGoing,
                    nouns = listOf("Serious", "Somewhat Easygoing", "Balanced", "Humorous", "Life of the Party"),
                    icon = Icons.Default.SentimentVerySatisfied
                )
            if (lifestyle.professionalAmbitious != -1)
                LifestyleSlider(
                    label = "Professional/Ambitious",
                    value = lifestyle.professionalAmbitious,
                    nouns = listOf("Relaxed", "Occasionally Driven", "Balanced", "Ambitious", "Highly Ambitious"),
                    icon = Icons.Default.Work
                )
            if (lifestyle.environmentallyConscious != -1)
                LifestyleSlider(
                    label = "Environmentally Conscious",
                    value = lifestyle.environmentallyConscious,
                    nouns = listOf("Not Conscious", "Occasionally Conscious", "Balanced", "Eco-Friendly", "Eco-Champion"),
                    icon = Icons.Default.Eco
                )
            if (lifestyle.foodieCulinaryEnthusiast != -1)
                LifestyleSlider(
                    label = "Foodie/Culinary Enthusiast",
                    value = lifestyle.foodieCulinaryEnthusiast,
                    nouns = listOf("Not a Foodie", "Occasionally Foodie", "Foodie", "Passionate Foodie", "Gourmet"),
                    icon = Icons.Default.LocalDining
                )
            if (lifestyle.politicallyAware != -1)
                LifestyleSlider(
                    label = "Politically Aware",
                    value = lifestyle.politicallyAware,
                    nouns = listOf("Unaware", "Occasionally Aware", "Balanced", "Aware", "Politically Engaged"),
                    icon = Icons.Default.Gavel
                )
            if (lifestyle.communityOriented != -1)
                LifestyleSlider(
                    label = "Community Oriented",
                    value = lifestyle.communityOriented,
                    nouns = listOf("Individualistic", "Occasionally Involved", "Balanced", "Community-Oriented", "Community Leader"),
                    icon = Icons.Default.Groups
                )
            if (lifestyle.sportsEnthusiast != -1)
                LifestyleSlider(
                    label = "Sports Enthusiast",
                    value = lifestyle.sportsEnthusiast,
                    nouns = listOf("Non-Sports", "Casual Viewer", "Occasional Player", "Sports Enthusiast", "Sports Fanatic"),
                    icon = Icons.Default.SportsSoccer
                )
            // NEW SLIDERS ADDED:
            if (lifestyle.IE != -1)
                LifestyleSlider(
                    label = "Introvert Level",
                    value = lifestyle.IE,
                    nouns = listOf("Not Introverted", "Slightly Introverted", "Moderately Introverted", "Very Introverted", "Extremely Introverted"),
                    icon = Icons.Default.Person
                )
            if (lifestyle.sal != -1)
                LifestyleSlider(
                    label = "Sexual Activity Level",
                    value = lifestyle.sal,
                    nouns = listOf("Inactive", "Low", "Moderate", "High", "Very High"),
                    icon = Icons.Default.Favorite
                )
            LifestyleBooleanField(label = "Pet Friendly", value = lifestyle.petFriendly)
            LifestyleBooleanField(label = "Cannabis Friendly", value = lifestyle.cannabisFriendly)
// For alcohol type, use a dropdown or a simple text row:
            LifestyleDropdown(label = "Alcohol Type", value = lifestyle.alcoholType, icon = Icons.Default.LocalDrink)
        }
    }
}

@Composable
fun LifestyleBooleanField(label: String, value: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Default.Check, contentDescription = label, tint = Color.White)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: ${if (value) "Yes" else "No"}",
            color = Color.White,
            fontSize = 16.sp
        )
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
        ProfileDetailRow(
            label = "Popularity Score",
            value = "${(profile.averageSwipeRightsOnUser * 100).roundToInt()}%",
            icon = Icons.Default.Star
        )
        ProfileDetailRow("Kolkata Ranking", profile.am24Ranking.toString(), Icons.Filled.Language)
        ProfileDetailRow("Age Ranking", profile.am24RankingAge.toString(), Icons.Default.Cake)

        if (profile.highSchool.isNotBlank()) {
            ProfileDetailRow(
                "${profile.highSchool} Ranking",
                profile.am24RankingHighSchool.toString(),
                Icons.Default.School
            )
            if (!profile.highSchoolGraduationYear.isNullOrBlank()) {
                ProfileDetailRow(
                    "Graduation Year from ${profile.highSchool}",
                    profile.highSchoolGraduationYear,
                    Icons.Default.School
                )
            }
        }

        if (profile.college.isNotBlank()) {
            ProfileDetailRow("College Ranking", profile.am24RankingCollege.toString(), Icons.Default.Book)
            if (!profile.collegeGraduationYear.isNullOrBlank()) {
                ProfileDetailRow(
                    "Graduation Year from ${profile.college}",
                    profile.collegeGraduationYear,
                    Icons.Default.Book
                )
            }
        }

        if (profile.hometown.isNotBlank()) {
            ProfileDetailRow("${profile.hometown} Ranking", profile.am24RankingHometown.toString(), Icons.Default.LocationCity)
        }

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

        if (!profile.postGraduation.isNullOrBlank()) {
            if (!profile.postGraduationYear.isNullOrBlank()) {
                ProfileDetailRow(
                    "Graduation Year from ${profile.postGraduation}",
                    profile.postGraduationYear,
                    Icons.Default.EmojiObjects
                )
            }
        }

        ProfileDetailRow("Date Joined", formatDate(profile.dateOfJoin), Icons.Default.DateRange)
    }
}

@Composable
fun BioEditSection(
    currentBio: String?,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var bio by remember { mutableStateOf(currentBio ?: "") }
    Column {
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
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(
                onClick = { onSave(bio) },
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


/** Basic Info Edit (includes email) */
@Composable
fun BasicInfoEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(tempProfile.name) }
    var gender by remember { mutableStateOf(tempProfile.gender) }
    var hometown by remember { mutableStateOf(tempProfile.hometown) }
    var highSchool by remember { mutableStateOf(tempProfile.highSchool) }
    var highSchoolGradYear by remember { mutableStateOf(tempProfile.highSchoolGraduationYear) }
    var college by remember { mutableStateOf(tempProfile.college) }
    var collegeGradYear by remember { mutableStateOf(tempProfile.collegeGraduationYear) }
    var postGrad by remember { mutableStateOf(tempProfile.postGraduation) }
    var postGradYear by remember { mutableStateOf(tempProfile.postGraduationYear ?: "") }
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

        // High School + Grad Year
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
        if (highSchool.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = highSchoolGradYear,
                onValueChange = { highSchoolGradYear = it },
                label = { Text("High School Graduation Year", color = Color(0xFFFF6F00)) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    cursorColor = Color(0xFFFF6F00),
                    focusedTextColor = Color.White
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // College + Grad Year
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
        if (college.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = collegeGradYear,
                onValueChange = { collegeGradYear = it },
                label = { Text("College Graduation Year", color = Color(0xFFFF6F00)) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    cursorColor = Color(0xFFFF6F00),
                    focusedTextColor = Color.White
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Post Grad + Year
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
            if (postGrad!!.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = postGradYear,
                    onValueChange = { postGradYear = it },
                    label = { Text("Post-Grad Year", color = Color(0xFFFF6F00)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color(0xFFFF6F00),
                        cursorColor = Color(0xFFFF6F00),
                        focusedTextColor = Color.White
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

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
                            gender = gender,
                            hometown = hometown,
                            highSchool = highSchool,
                            highSchoolGraduationYear = highSchoolGradYear,
                            college = college,
                            collegeGraduationYear = collegeGradYear,
                            postGraduation = postGrad,
                            postGraduationYear = postGradYear,
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

@Composable
fun LifestyleEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var localLifestyle by remember { mutableStateOf(tempProfile.lifestyle ?: Lifestyle()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(16.dp)
    ) {
        LifestyleSliderEdit(
            label = "Smoking",
            value = localLifestyle.smoking,
            nouns = listOf("Non-Smoker", "Rare Smoker", "Social Smoker", "Frequent Smoker", "Heavy Smoker")
        ) { localLifestyle = localLifestyle.copy(smoking = it) }
        LifestyleSliderEdit(
            label = "Drinking",
            value = localLifestyle.drinking,
            nouns = listOf("Non-Drinker", "Rare Drinker", "Social Drinker", "Frequent Drinker", "Heavy Drinker")
        ) { localLifestyle = localLifestyle.copy(drinking = it) }
        LifestyleSliderEdit(
            label = "Indoor <-> Outdoor",
            value = localLifestyle.indoorsyToOutdoorsy,
            nouns = listOf("Very Indoorsy", "Mostly Indoorsy", "Balanced", "Mostly Outdoorsy", "Very Outdoorsy")
        ) { localLifestyle = localLifestyle.copy(indoorsyToOutdoorsy = it) }
        LifestyleSliderEdit(
            label = "Social Media",
            value = localLifestyle.socialMedia,
            nouns = listOf("Invisible", "Watcher", "Casual Participant", "Engager", "Influencer")
        ) { localLifestyle = localLifestyle.copy(socialMedia = it) }
        LifestyleSliderEdit(
            label = "Work-Life Balance",
            value = localLifestyle.workLifeBalance,
            nouns = listOf("Workaholic", "More Work-Oriented", "Balanced", "More Life-Oriented", "Relaxed")
        ) { localLifestyle = localLifestyle.copy(workLifeBalance = it) }
        LifestyleSliderEdit(
            label = "Exercise Frequency",
            value = localLifestyle.exerciseFrequency,
            nouns = listOf("Inactive", "Rarely Active", "Moderately Active", "Active", "Very Active")
        ) { localLifestyle = localLifestyle.copy(exerciseFrequency = it) }
        LifestyleSliderEdit(
            label = "Family-Oriented",
            value = localLifestyle.familyOriented,
            nouns = listOf("Independent", "Slightly Family-Oriented", "Balanced", "Family-Oriented", "Very Family-Oriented")
        ) { localLifestyle = localLifestyle.copy(familyOriented = it) }
        LifestyleDropdownEdit("Diet", localLifestyle.diet) { localLifestyle = localLifestyle.copy(diet = it) }
        LifestyleSliderEdit(
            label = "Sleep Cycle",
            value = localLifestyle.sleepCycle,
            nouns = listOf("Early Riser", "Morning Person", "Balanced", "Night Owl", "Late Night Enthusiast")
        ) { localLifestyle = localLifestyle.copy(sleepCycle = it) }
        LifestyleSliderEdit(
            label = "Adventurous",
            value = localLifestyle.adventurous,
            nouns = listOf("Cautious", "Slightly Adventurous", "Moderately Adventurous", "Adventurous", "Thrill Seeker")
        ) { localLifestyle = localLifestyle.copy(adventurous = it) }
        if (localLifestyle.petFriendly)
            LifestyleDropdownEdit("Pet Friendly", "Yes") { localLifestyle = localLifestyle.copy(petFriendly = true) }
        LifestyleSliderEdit(
            label = "Intellectual",
            value = localLifestyle.intellectual,
            nouns = listOf("Casual Thinker", "Inquisitive", "Knowledge Seeker", "Intellectual", "Philosopher")
        ) { localLifestyle = localLifestyle.copy(intellectual = it) }
        LifestyleSliderEdit(
            label = "Creative/Artistic",
            value = localLifestyle.creativeArtistic,
            nouns = listOf("Not Creative", "Somewhat Creative", "Creative", "Very Creative", "Artistic Genius")
        ) { localLifestyle = localLifestyle.copy(creativeArtistic = it) }
        LifestyleSliderEdit(
            label = "Fitness Level",
            value = localLifestyle.fitnessLevel,
            nouns = listOf("Sedentary", "Somewhat Fit", "Fit", "Athletic", "Peak Fitness")
        ) { localLifestyle = localLifestyle.copy(fitnessLevel = it) }
        LifestyleSliderEdit(
            label = "Spiritual/Mindful",
            value = localLifestyle.spiritualMindful,
            nouns = listOf("Not Spiritual", "Occasionally Mindful", "Balanced", "Spiritual", "Deeply Mindful")
        ) { localLifestyle = localLifestyle.copy(spiritualMindful = it) }
        LifestyleSliderEdit(
            label = "Humorous/Easygoing",
            value = localLifestyle.humorousEasyGoing,
            nouns = listOf("Serious", "Somewhat Easygoing", "Balanced", "Humorous", "Life of the Party")
        ) { localLifestyle = localLifestyle.copy(humorousEasyGoing = it) }
        LifestyleSliderEdit(
            label = "Professional/Ambitious",
            value = localLifestyle.professionalAmbitious,
            nouns = listOf("Relaxed", "Occasionally Driven", "Balanced", "Ambitious", "Highly Ambitious")
        ) { localLifestyle = localLifestyle.copy(professionalAmbitious = it) }
        LifestyleSliderEdit(
            label = "Environmentally Conscious",
            value = localLifestyle.environmentallyConscious,
            nouns = listOf("Not Conscious", "Occasionally Conscious", "Balanced", "Eco-Friendly", "Eco-Champion")
        ) { localLifestyle = localLifestyle.copy(environmentallyConscious = it) }
        LifestyleSliderEdit(
            label = "Sports Enthusiast",
            value = localLifestyle.sportsEnthusiast,
            nouns = listOf("Non-Sports", "Casual Viewer", "Occasional Player", "Sports Enthusiast", "Sports Fanatic")
        ) { localLifestyle = localLifestyle.copy(sportsEnthusiast = it) }
        LifestyleSliderEdit(
            label = "Politically Aware",
            value = localLifestyle.politicallyAware,
            nouns = listOf("Unaware", "Occasionally Aware", "Balanced", "Aware", "Politically Engaged")
        ) { localLifestyle = localLifestyle.copy(politicallyAware = it) }
        LifestyleSliderEdit(
            label = "Community-Oriented",
            value = localLifestyle.communityOriented,
            nouns = listOf("Individualistic", "Occasionally Involved", "Balanced", "Community-Oriented", "Community Leader")
        ) { localLifestyle = localLifestyle.copy(communityOriented = it) }
        // NEW SLIDERS ADDED:
        LifestyleSliderEdit(
            label = "Introvert Level",
            value = localLifestyle.IE,
            nouns = listOf("Not Introverted", "Slightly Introverted", "Moderately Introverted", "Very Introverted", "Extremely Introverted")
        ) { localLifestyle = localLifestyle.copy(IE = it) }
        LifestyleSliderEdit(
            label = "Sexual Activity Level",
            value = localLifestyle.sal,
            nouns = listOf("Inactive", "Low", "Moderate", "High", "Very High")
        ) { localLifestyle = localLifestyle.copy(sal = it) }
        // For pet friendly
        LifestyleCheckboxEdit(
            label = "Pet Friendly",
            checked = localLifestyle.petFriendly,
            onCheckedChange = { localLifestyle = localLifestyle.copy(petFriendly = it) }
        )
// For cannabis friendly
        LifestyleCheckboxEdit(
            label = "Cannabis Friendly",
            checked = localLifestyle.cannabisFriendly,
            onCheckedChange = { localLifestyle = localLifestyle.copy(cannabisFriendly = it) }
        )
// For alcohol type
        LifestyleDropdownEdit(
            label = "Alcohol Type",
            value = localLifestyle.alcoholType,
            onValueChange = { localLifestyle = localLifestyle.copy(alcoholType = it) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        ButtonRow(
            onSave = {
                val updatedProfile = tempProfile.copy(lifestyle = localLifestyle)
                scope.launch {
                    updateProfileInFirebase(updatedProfile)
                    onSave(updatedProfile)
                }
            },
            onCancel = onCancel
        )
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
                    localInterests.add(Interest(name = namePart, emoji = emojiPart))
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

/** Reusable UI elements */
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
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = label, color = Color(0xFFFF6F00), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = value,
                    color = Color.White,
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
            .background(Color.Black, shape = CircleShape)
            .border(2.dp, Color(0xFFFF6F00), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = Color.White, fontSize = 14.sp)
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
                    post.mediaUrl?.let { VoicePlayer(url = it) }
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

/** "Read-only" lifestyle UI elements. */
@Composable
fun LifestyleDropdown(label: String, value: String?, icon: ImageVector) {
    val displayValue = value?.takeIf { it.isNotBlank() } ?: "No $label selected"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "$label Icon",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = displayValue,
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun LifestyleSlider(label: String, value: Int, nouns: List<String>, icon: ImageVector) {
    val displayText = if (value == -1) "Not Selected" else nouns.getOrElse(value) { "Unknown" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(text = displayText, color = if (value == -1) Color.Gray else Color.White)
    }
    Spacer(modifier = Modifier.height(4.dp))
    Slider(
        value = value.toFloat(),
        onValueChange = {},
        valueRange = 0f..4f,
        steps = 3,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFFFF6F00),
            activeTrackColor = Color(0xFFFF6F00)
        )
    )
}

/** Simple star rating bar (no vibe score). */
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

/** Utility to format date timestamps. */
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/** Utility to calculate age from dob. */
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

@Composable
fun VoicePlayer(url: String) {
    var isPlaying by remember { mutableStateOf(false) }
    var elapsedTime by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val mediaPlayer = remember { MediaPlayer() }
    val durationInSeconds = remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(url) {
        try {
            mediaPlayer.setDataSource(url)
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener {
                durationInSeconds.value = it.duration / 1000
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                elapsedTime = 0
            }
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error loading audio: ${e.message}")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            coroutineScope.launch {
                while (isPlaying && elapsedTime < durationInSeconds.value) {
                    delay(1000)
                    elapsedTime++
                }
            }
        } else {
            elapsedTime = 0
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(8.dp))
            .clickable {
                isPlaying = if (mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                    false
                } else {
                    mediaPlayer.start()
                    true
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause Audio" else "Play Audio",
            tint = Color(0xFFFFBF00),
            modifier = Modifier.padding(8.dp)
        )
        Text(
            text = if (isPlaying) "Playing: $elapsedTime s" else "Tap to Play Voice Bio",
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        if (durationInSeconds.value > 0) {
            Text(
                text = "${elapsedTime}s / ${durationInSeconds.value}s",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
fun LifestyleSliderEdit(label: String, value: Int, nouns: List<String>, onValueChange: (Int) -> Unit) {
    var sliderValue by remember { mutableStateOf(value.toFloat()) }
    val displayText = if (value == -1) "Not Selected" else nouns.getOrElse(value) { "Unknown" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
        Text(displayText, color = if (value == -1) Color.Gray else Color.White)
    }
    Spacer(modifier = Modifier.height(4.dp))
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onValueChange(sliderValue.toInt()) },
        valueRange = 0f..4f,
        steps = 3,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFFFF6F00),
            activeTrackColor = Color(0xFFFF6F00)
        )
    )
}

@Composable
fun LifestyleDropdownEdit(label: String, value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf(value.ifBlank { "Not Selected" }) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
        Text(selectedOption, color = if (value.isBlank()) Color.Gray else Color.White)
    }
    Spacer(modifier = Modifier.height(4.dp))
    Button(
        onClick = { expanded = true },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
    ) {
        Text(text = selectedOption, color = Color.White)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        listOf("Vegan", "Vegetarian", "Non-Vegetarian").forEach { option ->
            DropdownMenuItem(text = { Text(option) }, onClick = {
                selectedOption = option
                onValueChange(option)
                expanded = false
            })
        }
    }
}

@Composable
fun LifestyleCheckboxEdit(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
@Composable
fun ButtonRow(
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ElevatedButton(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Text("Save", color = Color.White)
        }
        ElevatedButton(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Text("Cancel", color = Color.White)
        }
    }
}

suspend fun updateProfileInFirebase(updatedProfile: Profile) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserId)

    val updates = mapOf(
        "email" to updatedProfile.email,
        "name" to updatedProfile.name,
        "bio" to updatedProfile.bio,
        "gender" to updatedProfile.gender,
        "hometown" to updatedProfile.hometown,
        "highSchool" to updatedProfile.highSchool,
        "highSchoolGraduationYear" to updatedProfile.highSchoolGraduationYear,
        "college" to updatedProfile.college,
        "collegeGraduationYear" to updatedProfile.collegeGraduationYear,
        "postGraduation" to updatedProfile.postGraduation,
        "postGraduationYear" to updatedProfile.postGraduationYear,
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
