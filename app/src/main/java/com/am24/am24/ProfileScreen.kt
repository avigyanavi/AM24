@file:OptIn(
    ExperimentalMaterial3Api::class,
)
package com.am24.am24

import android.Manifest
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader

@Composable
fun ProfileScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel,
    postViewModel: PostViewModel,
    modifier: Modifier = Modifier
) {
    val filtersLoaded by postViewModel.filtersLoaded.collectAsState()
    val currentUserProfile by profileViewModel.currentUserProfile.collectAsState()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    // Fetch profile on screen entry
    LaunchedEffect(currentUserId) {
        Log.d("ProfileScreen", "Fetching profile for userId: $currentUserId")
        profileViewModel.fetchCurrentUserProfile()
    }

    if (!filtersLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFF6F00))
        }
        return
    }

    val allPosts by postViewModel.filteredPosts.collectAsState()
    val myPosts = allPosts.filter { it.userId == currentUserId }
    val sortedByUpvotes = myPosts.sortedByDescending { it.upvotes }
    val featuredPosts = sortedByUpvotes.take(5)
    val remainingPosts = sortedByUpvotes.drop(5)

    when {
        currentUserProfile == null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.profile_loading), color = Color.White)
            }
        }
        else -> {
            ProfileLazyScreen(
                navController = navController,
                profile = currentUserProfile!!,
                featuredPosts = featuredPosts,
                remainingPosts = remainingPosts,
                profileViewModel = profileViewModel
            )
        }
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
    var showPostsOverlay by remember { mutableStateOf(false) }
    val currentUserProfile by profileViewModel.currentUserProfile.collectAsState()

    // Use ViewModel's profile if available, otherwise fall back to initial profile
    var currentProfile by remember { mutableStateOf(profile) }

    // Sync with ViewModel's profile
    LaunchedEffect(currentUserProfile) {
        currentUserProfile?.let { updatedProfile ->
            Log.d("ProfileLazyScreen", "Updating currentProfile with isMatrimonyMode: ${updatedProfile.isMatrimonyMode}")
            currentProfile = updatedProfile
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            state = listState
        ) {
            item {
                PhotoCarouselWithOverlay(
                    profile = currentProfile,
                    onEditProfileClick = { navController.navigate("editPicAndVoiceBio") },
                    onPostsClick = { showPostsOverlay = true },
                    onVerifyClick = { navController.navigate("govtIdVerification") }
                )
            }
            item {
                MatrimonyToggleRow(
                    isMatrimony = currentProfile.isMatrimonyMode,
                    onToggle = { checked ->
                        val updatedProfile = currentProfile.copy(isMatrimonyMode = checked)
                        currentProfile = updatedProfile
                        scope.launch {
                            Log.d("ProfileLazyScreen", "Toggling matrimonyMode to: $checked")
                            profileViewModel.saveProfileUpdated(
                                updatedProfile = updatedProfile,
                                onSuccess = {
                                    Log.d("ProfileLazyScreen", "Profile saved with isMatrimonyMode: $checked")
                                },
                                onFailure = { error ->
                                    Log.e("ProfileLazyScreen", "Failed to save profile: $error")
                                }
                            )
                        }
                    }
                )
            }
            if (currentProfile.isMatrimonyMode) {
                item {
                    MatrimonyInfoCard(
                        profile = currentProfile,
                        onSave = { updated ->
                            currentProfile = updated
                            scope.launch {
                                Log.d("ProfileLazyScreen", "Saving matrimony info")
                                profileViewModel.saveProfileUpdated(
                                    updatedProfile = updated,
                                    onSuccess = { Log.d("ProfileLazyScreen", "Matrimony info saved") },
                                    onFailure = { error ->
                                        Log.e("ProfileLazyScreen", "Failed to save matrimony info: $error")
                                    }
                                )
                            }
                        }
                    )
                }
            }
            item {
                ProfileCollapsibleSections(
                    profile = currentProfile,
                    profileViewModel = profileViewModel,
                    onProfileUpdated = { updated ->
                        currentProfile = updated
                        scope.launch {
                            Log.d("ProfileLazyScreen", "Updating collapsible sections")
                            profileViewModel.saveProfileUpdated(
                                updatedProfile = updated,
                                onSuccess = { Log.d("ProfileLazyScreen", "Collapsible sections saved") },
                                onFailure = { error ->
                                    Log.e("ProfileLazyScreen", "Failed to save collapsible sections: $error")
                                }
                            )
                        }
                    }
                )
            }
            if (featuredPosts.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.featured_posts),
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
            if (remainingPosts.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = { showPostsOverlay = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                        ) {
                            Text(text = stringResource(R.string.view_more_posts), color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        if (showPostsOverlay) {
            PostsOverlay(
                posts = featuredPosts + remainingPosts,
                onDismiss = { showPostsOverlay = false }
            )
        }
    }
}

@Composable
fun VerificationBadge(
    verified: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(32.dp)
            .background(
                if (verified) Color(0xFF00C853)          // green when verified
                else Color.Gray.copy(alpha = .55f),       // grey when not
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = if (verified) Icons.Default.Verified else Icons.Default.DoNotDisturbOn,
            contentDescription = if (verified)
                stringResource(R.string.verified_profile_cd)
            else
                stringResource(R.string.verify_profile_cd),
            tint = Color.White
        )
    }
}

@Composable
fun MatrimonyInfoCard(
    profile: Profile,
    onSave: (Profile) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var marriageTimeline by remember { mutableStateOf(profile.marriageTimeline ?: "") }
    var relocationPreference by remember { mutableStateOf(profile.relocationPreference ?: "") }
    var postMarriageCareerPlan by remember { mutableStateOf(profile.postMarriageCareerPlan ?: "") }
    var traditionalVsLiberal by remember { mutableStateOf(profile.traditionalVsLiberal ?: "") }
    var fatherOccupation by remember { mutableStateOf(profile.fatherOccupation ?: "") }
    var motherOccupation by remember { mutableStateOf(profile.motherOccupation ?: "") }

    Card(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, Color(0xFFFF6F00)),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (isEditing) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = marriageTimeline,
                        onValueChange = { marriageTimeline = it },
                        label = { Text(stringResource(R.string.matrimony_marriage_timeline), color = Color(0xFFFF6F00)) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFFF6F00),
                            cursorColor = Color(0xFFFF6F00),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = relocationPreference,
                        onValueChange = { relocationPreference = it },
                        label = { Text(stringResource(R.string.matrimony_relocation_preference), color = Color(0xFFFF6F00)) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFFF6F00),
                            cursorColor = Color(0xFFFF6F00),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = postMarriageCareerPlan,
                        onValueChange = { postMarriageCareerPlan = it },
                        label = { Text(stringResource(R.string.matrimony_post_marriage_career_plan), color = Color(0xFFFF6F00)) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFFF6F00),
                            cursorColor = Color(0xFFFF6F00),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = traditionalVsLiberal,
                        onValueChange = { traditionalVsLiberal = it },
                        label = { Text(stringResource(R.string.matrimony_traditional_vs_liberal), color = Color(0xFFFF6F00)) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFFF6F00),
                            cursorColor = Color(0xFFFF6F00),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fatherOccupation,
                        onValueChange = { fatherOccupation = it },
                        label = { Text(stringResource(R.string.matrimony_father_occupation), color = Color(0xFFFF6F00)) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFFF6F00),
                            cursorColor = Color(0xFFFF6F00),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = motherOccupation,
                        onValueChange = { motherOccupation = it },
                        label = { Text(stringResource(R.string.matrimony_mother_occupation), color = Color(0xFFFF6F00)) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFFF6F00),
                            cursorColor = Color(0xFFFF6F00),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            val updatedProfile = profile.copy(
                                marriageTimeline = marriageTimeline.ifBlank { null },
                                relocationPreference = relocationPreference.ifBlank { null },
                                postMarriageCareerPlan = postMarriageCareerPlan.ifBlank { null },
                                traditionalVsLiberal = traditionalVsLiberal.ifBlank { null },
                                fatherOccupation = fatherOccupation.ifBlank { null },
                                motherOccupation = motherOccupation.ifBlank { null }
                            )
                            onSave(updatedProfile)
                            isEditing = false
                        }) {
                            Text(stringResource(R.string.save), color = Color(0xFF00bf63))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            marriageTimeline = profile.marriageTimeline ?: ""
                            relocationPreference = profile.relocationPreference ?: ""
                            postMarriageCareerPlan = profile.postMarriageCareerPlan ?: ""
                            traditionalVsLiberal = profile.traditionalVsLiberal ?: ""
                            fatherOccupation = profile.fatherOccupation ?: ""
                            motherOccupation = profile.motherOccupation ?: ""
                            isEditing = false
                        }) {
                            Text(stringResource(R.string.cancel), color = Color.Red)
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.matrimony_marriage_timeline), color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
                        Text(text = profile.marriageTimeline ?: stringResource(R.string.not_set), color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.matrimony_relocation_preference), color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
                        Text(text = profile.relocationPreference ?: stringResource(R.string.not_set), color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.matrimony_post_marriage_career_plan), color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
                        Text(text = profile.postMarriageCareerPlan ?: stringResource(R.string.not_set), color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.matrimony_traditional_vs_liberal), color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
                        Text(text = profile.traditionalVsLiberal ?: stringResource(R.string.not_set), color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.matrimony_father_occupation), color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
                        Text(text = profile.fatherOccupation ?: stringResource(R.string.not_set), color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.matrimony_mother_occupation), color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
                        Text(text = profile.motherOccupation ?: stringResource(R.string.not_set), color = Color.White)
                    }
                }
            }
            IconButton(
                onClick = { isEditing = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.7f), shape = CircleShape)
            ) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_matrimony_info_cd), tint = Color(0xFFFF6F00))
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
    editable: Boolean = true,                       // ← NEW
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .border(2.dp, Color(0xFFFF6F00), RoundedCornerShape(10.dp)) // ← new look
            .background(Color(0xFF1A1A1A))                              // ← new look
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color(0xFFFF6F00),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))

        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f)
        )

        /* edit icon only when editable == true */
        if (editable) {
            IconButton(onClick = onEditToggle) {
                Icon(
                    imageVector = if (editMode) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = if (editMode) "Cancel Edit" else "Edit",
                    tint = if (editMode) Color.Red else Color.White
                )
            }
        }

        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (isExpanded)
                    Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = Color.White
            )
        }
    }

    if (isExpanded) {
        Spacer(Modifier.height(8.dp))

        Card(
            colors  = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape   = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(Modifier.padding(12.dp)) { content() }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun PhotoCarouselWithOverlay(
    profile: Profile,
    onEditProfileClick: () -> Unit,
    onPostsClick: () -> Unit,
    onVerifyClick: () -> Unit        // ➊ new
) {
    val context = LocalContext.current
    val photoUrls = listOfNotNull(profile.profilepicUrl) + profile.optionalPhotoUrls
    var currentPhotoIndex by remember { mutableStateOf(0) }

    // Pre-cache images
    LaunchedEffect(photoUrls) {
        photoUrls.forEach { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .diskCacheKey(url)
                .memoryCacheKey(url)
                .build()
            context.imageLoader.enqueue(request)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(7f / 10f) // adjust as needed
            .background(Color.Black)
            .pointerInput(photoUrls) {
                detectTapGestures { offset ->
                    // Tap left or right to cycle photos
                    if (photoUrls.size > 1) {
                        currentPhotoIndex = if (offset.x > size.width / 2)
                            (currentPhotoIndex + 1) % photoUrls.size
                        else
                            (currentPhotoIndex - 1 + photoUrls.size) % photoUrls.size
                    }
                }
            }
    ) {
        // Main photo
        if (photoUrls.isNotEmpty()) {
            if (photoUrls.isNotEmpty()) {
                CachedProfilePhoto(url = photoUrls[currentPhotoIndex], modifier = Modifier.fillMaxSize())
            }

            // Photo indicators at top
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .align(Alignment.TopStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(.65f), Color.Transparent)
                        )
                    )
            ) {
                AnimatedProfileCompletion(
                    completion = profile.profileCompletionPercentage,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
                VerificationBadge(
                    verified  = profile.isConsultantVerified,
                    onClick   = onVerifyClick,
                    modifier  = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)   // leaves room for the edit icon
                )
            }
        }

        // Bottom overlay: name, age, hometown, rating bar, zodiac, posts button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Column {
                // Name + age + Posts button
                val age = calculateAge(profile.dob)
                Row(
                    modifier = Modifier.fillMaxWidth(), // Make the Row take the full available width
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // This Text stays on the start (left)
                    Text(
                        text = if (age > 0) "${profile.name}, $age" else profile.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = Color.White // Make sure this is appropriate for your background
                    )

                    // This Spacer takes up all remaining space in the Row
                    Spacer(Modifier.weight(1f))

                    // This Button is pushed to the end (right)
                    Button(
                        onClick = onPostsClick,
                        // Use containerColor for Material 3, backgroundColor for Material 2
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00)),
                        modifier = Modifier.height(34.dp)
                        // No .align modifier needed here for end alignment
                    ) {
                        Text(text = stringResource(R.string.posts_button), color = Color.White, fontSize = 14.sp)
                    }
                }

                // Hometown
                if (profile.hometown.isNotBlank()) {
                    Text(text = stringResource(R.string.from_hometown, profile.hometown), fontSize = 16.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Rating Bar + zodiac side by side
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingBar3(rating = profile.averageRating, ratingCount = profile.numberOfRatings)
                    Spacer(Modifier.width(8.dp))

                    // Zodiac next to rating bar
                    val zodiac = profile.zodiac ?: deriveZodiac(profile.dob)
                    if (zodiac.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFF6F00)
                        ) {
                            Text(
                                text = zodiac,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Edit icon at top right
        IconButton(
            onClick = onEditProfileClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(Color.Gray.copy(alpha = 0.5f), shape = CircleShape)
                .size(32.dp)
        ) {
            Icon(Icons.Default.Edit, stringResource(R.string.edit_profile_cd), tint = Color.White)
        }
    }
}

@Composable
fun CachedProfilePhoto(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = stringResource(R.string.profile_photo)
) {
    // Build and remember your image painter.
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .diskCacheKey(url)   // Use the URL as a stable key
            .memoryCacheKey(url)
            .crossfade(true)
            .build()
    )
    // Use the painter in the Image composable.
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}



/** Display a horizontal progress for completion. */
@Composable
fun AnimatedProfileCompletion(
    completion: Int,
    modifier: Modifier = Modifier
) {
    if (completion >= 100) return   // <-- early‑exit

    val animated by animateFloatAsState(
        targetValue = completion / 100f,
        animationSpec = tween(600, easing = FastOutSlowInEasing)
    )

    Column(modifier) {
        Text(
            text = stringResource(R.string.profile_percent, completion),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        LinearProgressIndicator(
            progress = animated,
            modifier = Modifier
                .fillMaxWidth(.55f)
                .height(4.dp),
            color = Color(0xFFFF6F00),
            trackColor = Color.White.copy(alpha = .3f)
        )
    }
}

@Composable
fun MatrimonyToggleRow(
    isMatrimony: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.matrimony_mode),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Switch(
            checked = isMatrimony,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFF6F00),
                checkedTrackColor = Color(0xFFFF6F00).copy(alpha = .5f)
            )
        )
    }
}

@Composable
fun BasicInfoSection(profile: Profile) {
    val genderIcon = when (profile.gender.lowercase()) {
        "male"   -> Icons.Default.Male
        "female" -> Icons.Default.Female
        else     -> Icons.Default.Transgender
    }

    val heightString = if (profile.height2.size == 2)
        "${profile.height2[0]} ft ${profile.height2[1]} in"
    else
        "${profile.height} cm"

    // --- Name, caste, gender, city, locality, username, job, work ---
    ProfileDetailRow(stringResource(R.string.label_name),
        profile.name,
        Icons.Default.Person)

    ProfileDetailRow(stringResource(R.string.label_height),
        heightString,
        Icons.Default.Straighten)

    ProfileDetailRow(stringResource(R.string.caste),
        profile.caste ?: stringResource(R.string.not_set),
        Icons.Default.Groups)

    ProfileDetailRow(stringResource(R.string.label_gender),
        profile.gender,
        genderIcon)

    // --- Community, religion, height, date joined ---
    ProfileDetailRow(stringResource(R.string.label_community),
        profile.community,
        Icons.Default.Groups)

    ProfileDetailRow(stringResource(R.string.label_religion),
        profile.religion,
        Icons.Default.Church)

    ProfileDetailRow(stringResource(R.string.city_label),
        profile.city.ifBlank { stringResource(R.string.not_set) },
        Icons.Default.LocationCity)

    ProfileDetailRow(stringResource(R.string.label_locality),
        profile.hometown,
        Icons.Default.LocationCity)

    ProfileDetailRow(stringResource(R.string.label_username),
        profile.username,
        Icons.Default.AccountCircle)

    val displayJobRole = profile.customJobRole
        .takeUnless { it.isNullOrBlank() }
        ?: profile.jobRole
    ProfileDetailRow(stringResource(R.string.label_job_role),
        displayJobRole.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.not_set),
        Icons.Default.Work)

    val displayWork = profile.customWork
        .takeUnless { it.isNullOrBlank() }
        ?: profile.work
    ProfileDetailRow(stringResource(R.string.label_work),
        displayWork.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.not_set),
        Icons.Default.Business)

    // --- High School + Year ---
    val highSchoolText = profile.highSchool
        .takeIf { it.isNotBlank() }
        ?.let { name ->
            profile.highSchoolGraduationYear
                .takeIf { it.isNotBlank() }
                ?.let { ", $it" }
                .let { suffix -> name + (suffix ?: "") }
        }
    ProfileDetailRow(stringResource(R.string.label_high_school),
        highSchoolText,
        Icons.Default.School)

    // --- College + Year ---
    val collegeText = profile.college
        .takeIf { it.isNotBlank() }
        ?.let { name ->
            profile.collegeGraduationYear
                .takeIf { it.isNotBlank() }
                ?.let { ", $it" }
                .let { suffix -> name + (suffix ?: "") }
        }
    ProfileDetailRow(stringResource(R.string.label_college),
        collegeText,
        Icons.Default.AccountBalance)

    // College degree if any
    if (!profile.collegeDegree.isNullOrBlank()) {
        ProfileDetailRow(stringResource(R.string.label_college_degree),
            profile.collegeDegree,
            Icons.Default.Book)
    }

    // --- Post‑Graduation + Year ---
    val postGradText = profile.postGraduation
        .takeIf { it!!.isNotBlank() }
        ?.let { name ->
            profile.postGraduationYear
                .takeIf { it.isNotBlank() }
                ?.let { ", $it" }
                .let { suffix -> name + (suffix ?: "") }
        }
    ProfileDetailRow(stringResource(R.string.label_post_graduation),
        postGradText,
        Icons.Default.EmojiObjects)

    // Post‑grad degree if any
    if (!profile.postGraduationDegree.isNullOrBlank()) {
        ProfileDetailRow(stringResource(R.string.label_post_graduation_degree),
            profile.postGraduationDegree,
            Icons.Default.School)
    }

    ProfileDetailRow(stringResource(R.string.label_date_joined),
        formatDate(profile.dateOfJoin),
        Icons.Default.DateRange)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicInfoEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    // ─── State for all fields ───────────────────────────
    var name by remember { mutableStateOf(tempProfile.name) }
    var city by remember { mutableStateOf(tempProfile.city) }
    var cityDropdownExpanded by remember { mutableStateOf(false) }
    var locality by remember { mutableStateOf(tempProfile.hometown) }
    var localityDropdownExpanded by remember { mutableStateOf(false) }

    var highSchool by remember { mutableStateOf(tempProfile.highSchool) }
    var highSchoolGradYear by remember { mutableStateOf(tempProfile.highSchoolGraduationYear) }

    var college by remember { mutableStateOf(tempProfile.college) }
    var collegeGradYear by remember { mutableStateOf(tempProfile.collegeGraduationYear) }
    var collegeDegree by remember { mutableStateOf(tempProfile.collegeDegree.orEmpty()) }

    var postGrad by remember { mutableStateOf(tempProfile.postGraduation.orEmpty()) }
    var postGradYear by remember { mutableStateOf(tempProfile.postGraduationYear) }
    var postGraduationDegree by remember { mutableStateOf(tempProfile.postGraduationDegree.orEmpty()) }

    var community by remember { mutableStateOf(tempProfile.community) }
    var religion by remember { mutableStateOf(tempProfile.religion) }

    // ─── other state above ─────────────────────────────
    var isHeightInFeet by remember { mutableStateOf(tempProfile.height2.isNotEmpty()) }
    var feet          by remember { mutableStateOf(tempProfile.height2.getOrNull(0) ?: 0) }
    var inches        by remember { mutableStateOf(tempProfile.height2.getOrNull(1) ?: 0) }
    var heightCm      by remember { mutableStateOf(tempProfile.height) }

    // ─── Chip‐based gender ───────────────────────────────
    val genderOptions = listOf(
        stringResource(R.string.male_option),
        stringResource(R.string.female_option),
        stringResource(R.string.college_other)
    )
    var selectedGender by remember {
        mutableStateOf(
            genderOptions.find { it == tempProfile.gender } ?: genderOptions.first()
        )
    }

    // ─── Job & Work ────────────────────────────────────
    val jobRoleOptions = listOf(
        stringResource(R.string.job_role_option_engineer),
        stringResource(R.string.job_role_option_teacher),
        stringResource(R.string.job_role_option_doctor),
        stringResource(R.string.job_role_option_intern),
        stringResource(R.string.job_role_option_entrepreneur),
        stringResource(R.string.job_role_option_other)
    )
    var selectedJobRole by remember {
        mutableStateOf(
            jobRoleOptions.find { it == tempProfile.jobRole } ?: jobRoleOptions.first()
        )
    }
    var customJobRole by remember {
        mutableStateOf(if (selectedJobRole == jobRoleOptions.last()) tempProfile.customJobRole.orEmpty() else "")
    }

    val workOptions = listOf(
        stringResource(R.string.work_option_private_sector),
        stringResource(R.string.work_option_government),
        stringResource(R.string.work_option_freelance),
        stringResource(R.string.work_option_unemployed),
        stringResource(R.string.work_option_other)
    )
    var selectedWork by remember {
        mutableStateOf(
            workOptions.find { it == tempProfile.work } ?: workOptions.first()
        )
    }
    var customWork by remember {
        mutableStateOf(if (selectedWork == workOptions.last()) tempProfile.customWork.orEmpty() else "")
    }

    // ─── City + Locality ────────────────────────────────
    val cityOptions = stringArrayResource(id = R.array.city_names).toList()

    // Load the right array for the selected city.
    val localityOptions = when (city) {
        stringResource(R.string.city_kolkata)     -> stringArrayResource(id = R.array.localities_kolkata).toList()
        stringResource(R.string.city_howrah)      -> stringArrayResource(id = R.array.localities_howrah).toList()
        stringResource(R.string.city_durgapur)    -> stringArrayResource(id = R.array.localities_durgapur).toList()
        stringResource(R.string.city_asansol)     -> stringArrayResource(id = R.array.localities_asansol).toList()
        // …add all your other cities here…
        else                                 -> emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Name
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.label_name), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth()
        )

        // Height label + toggle
        Text(stringResource(R.string.height_label), fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = isHeightInFeet,
                onCheckedChange = { isHeightInFeet = it }
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isHeightInFeet)
                    stringResource(R.string.feet_inches_label)
                else
                    stringResource(R.string.centimeters_label)
            )
        }

        if (isHeightInFeet) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = feet.toString(),
                    onValueChange = { feet = it.toIntOrNull() ?: 0 },
                    label = { Text(stringResource(R.string.feet_label)) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = inches.toString(),
                    onValueChange = { inches = it.toIntOrNull() ?: 0 },
                    label = { Text(stringResource(R.string.inches_label)) },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            OutlinedTextField(
                value = heightCm.toString(),
                onValueChange = { heightCm = it.toIntOrNull() ?: 0 },
                label = { Text(stringResource(R.string.centimeters_label)) },
                modifier = Modifier.fillMaxWidth()
            )
        }

// ─── Community as single‑select chips ───────────────────
        Text(
            text = stringResource(R.string.religion_label),
            fontWeight = FontWeight.Bold
        )
        val communityOptions = listOf(
            stringResource(R.string.religion_other),
            stringResource(R.string.religion_no_religion),
            stringResource(R.string.religion_hindu),
            stringResource(R.string.religion_muslim),
            stringResource(R.string.religion_christian),
            stringResource(R.string.religion_sikh),
            stringResource(R.string.religion_jain),
            stringResource(R.string.religion_buddhist),
            stringResource(R.string.religion_indigenous_tribal),
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            communityOptions.forEach { option ->
                FilterChip(
                    selected = religion == option,
                    onClick = { religion = option },
                    label = { Text(option) },
                    colors  = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

// ─── Religion as single‑select chips ────────────────────
        Text(
            text = stringResource(R.string.community_label),
            fontWeight = FontWeight.Bold
        )
        val religionOptions = listOf(
            stringResource(R.string.religion_other),
            stringResource(R.string.community_bengali),
            stringResource(R.string.community_marwari),
            stringResource(R.string.community_bihari),
            stringResource(R.string.community_punjabi),
            stringResource(R.string.community_santhal),
            stringResource(R.string.community_bangal),
            stringResource(R.string.community_ghoti),
            stringResource(R.string.community_gujarati),
            stringResource(R.string.community_kannadiga),
            stringResource(R.string.community_tamil),
            stringResource(R.string.community_malayali),
            stringResource(R.string.community_odia),
            stringResource(R.string.community_telugu),
            stringResource(R.string.community_nepali),
            stringResource(R.string.community_munda),
            stringResource(R.string.community_oraon),

            /* ——— Himalayan neighbours ——— */
            stringResource(R.string.community_bhutanese),
            stringResource(R.string.community_sikkimese),

            /* ——— Nagaland ——— */
            stringResource(R.string.community_naga),
            stringResource(R.string.community_ao),
            stringResource(R.string.community_angami),
            stringResource(R.string.community_lotha),
            stringResource(R.string.community_sema),
            stringResource(R.string.community_chakhesang),
            stringResource(R.string.community_konyak),
            stringResource(R.string.community_phom),
            stringResource(R.string.community_chang),
            stringResource(R.string.community_rengma),
            stringResource(R.string.community_yimkhiung),
            stringResource(R.string.community_khiamniungan),
            stringResource(R.string.community_zeliang),

            /* ——— Arunachal Pradesh ——— */
            stringResource(R.string.community_arunachali),   // ← NEW
            stringResource(R.string.community_apatani),
            stringResource(R.string.community_adi),
            stringResource(R.string.community_nyishi),
            stringResource(R.string.community_galo),
            stringResource(R.string.community_tagin),
            stringResource(R.string.community_mishmi),
            stringResource(R.string.community_monpa),
            stringResource(R.string.community_sherdukpen),
            stringResource(R.string.community_bugun),
            stringResource(R.string.community_aka),

            /* ——— Manipur ——— */
            stringResource(R.string.community_meitei),
            stringResource(R.string.community_tangkhul),
            stringResource(R.string.community_poumai),
            stringResource(R.string.community_mao),
            stringResource(R.string.community_thadou),
            stringResource(R.string.community_paite),
            stringResource(R.string.community_zou),
            stringResource(R.string.community_anal),
            stringResource(R.string.community_hmar),
            stringResource(R.string.community_maring),

            /* ——— Mizoram ——— */
            stringResource(R.string.community_mizo),
            stringResource(R.string.community_lai),
            stringResource(R.string.community_mara),

            /* ——— Tripura ——— */
            stringResource(R.string.community_tripuri),
            stringResource(R.string.community_reang),
            stringResource(R.string.community_chakma),
            stringResource(R.string.community_halam),

            /* ——— Meghalaya ——— */
            stringResource(R.string.community_khasi),
            stringResource(R.string.community_garo),
            stringResource(R.string.community_jaintia),

            /* ——— Assam plains tribes ——— */
            stringResource(R.string.community_assamese),
            stringResource(R.string.community_bodo),
            stringResource(R.string.community_mishing),
            stringResource(R.string.community_karbi),
            stringResource(R.string.community_dimasa),
            stringResource(R.string.community_rabha),
            stringResource(R.string.community_tiwa),
            stringResource(R.string.community_deori),
            stringResource(R.string.community_sonowal_kachari)
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            religionOptions.forEach { option ->
                FilterChip(
                    selected = community == option,
                    onClick = { community = option },
                    label = { Text(option) },
                    colors  = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }

        // Gender Chips
        Text(stringResource(R.string.gender_label), fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genderOptions.forEach { option ->
                FilterChip(
                    selected = selectedGender == option,
                    onClick = { selectedGender = option },
                    label = { Text(option) },
                    colors  = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }

        // City dropdown
        Text(stringResource(R.string.city_label), fontWeight = FontWeight.Bold)
        Button(onClick = { cityDropdownExpanded = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
        ) {
            Text(if (city.isBlank()) stringResource(R.string.select_city) else city)
        }
        DropdownMenu(
            expanded = cityDropdownExpanded,
            onDismissRequest = { cityDropdownExpanded = false }
        ) {
            cityOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        city = option
                        cityDropdownExpanded = false
                        // reset locality when city changes
                        locality = ""
                    }
                )
            }
        }

        // Locality dropdown
        Text(stringResource(R.string.label_locality), fontWeight = FontWeight.Bold)
        Button(onClick = { localityDropdownExpanded = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
        ) {
            Text(if (locality.isBlank()) stringResource(R.string.locality) else locality)
        }
        DropdownMenu(
            expanded = localityDropdownExpanded,
            onDismissRequest = { localityDropdownExpanded = false }
        ) {
            localityOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        locality = option
                        localityDropdownExpanded = false
                    }
                )
            }
        }

        // Job Role chips + optional custom
        Text(stringResource(R.string.job_role_label), fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            jobRoleOptions.forEach { option ->
                FilterChip(
                    selected = selectedJobRole == option,
                    onClick = {
                        selectedJobRole = option
                        if (option != jobRoleOptions.last()) customJobRole = ""
                    },
                    label = { Text(option) },
                    colors  = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }
        if (selectedJobRole == jobRoleOptions.last()) {
            OutlinedTextField(
                value = customJobRole,
                onValueChange = { customJobRole = it },
                label = { Text(stringResource(R.string.label_custom_job_role), color = Color(0xFFFF6F00)) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Work chips + optional custom
        Text(stringResource(R.string.label_work), fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            workOptions.forEach { option ->
                FilterChip(
                    selected = selectedWork == option,
                    onClick = {
                        selectedWork = option
                        if (option != workOptions.last()) customWork = ""
                    },
                    label = { Text(option) },
                    colors  = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }
        if (selectedWork == workOptions.last()) {
            OutlinedTextField(
                value = customWork,
                onValueChange = { customWork = it },
                label = { Text(stringResource(R.string.label_custom_work), color = Color(0xFFFF6F00)) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Education fields…
        OutlinedTextField(
            value = highSchool,
            onValueChange = { highSchool = it },
            label = { Text(stringResource(R.string.label_high_school), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth()
        )
        if (highSchool.isNotBlank()) {
            OutlinedTextField(
                value = highSchoolGradYear,
                onValueChange = { highSchoolGradYear = it },
                label = { Text(stringResource(R.string.high_school_graduation_year), color = Color(0xFFFF6F00)) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = college,
            onValueChange = { college = it },
            label = { Text(stringResource(R.string.college_label), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth()
        )
        if (college.isNotBlank()) {
            OutlinedTextField(
                value = collegeGradYear,
                onValueChange = { collegeGradYear = it },
                label = { Text(stringResource(R.string.college_graduation_year), color = Color(0xFFFF6F00)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = collegeDegree,
                onValueChange = { collegeDegree = it },
                label = { Text(stringResource(R.string.label_college_degree), color = Color(0xFFFF6F00)) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = postGrad,
            onValueChange = { postGrad = it },
            label = { Text(stringResource(R.string.post_graduation_label), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth()
        )
        if (postGrad.isNotBlank()) {
            OutlinedTextField(
                value = postGradYear,
                onValueChange = { postGradYear = it },
                label = { Text(stringResource(R.string.select_graduation_year_label), color = Color(0xFFFF6F00)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = postGraduationDegree,
                onValueChange = { postGraduationDegree = it },
                label = { Text(stringResource(R.string.label_post_graduation_degree), color = Color(0xFFFF6F00)) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ─── Save / Cancel ───────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    // build your updated Profile and hand it back:
                    val updated = tempProfile.copy(
                        name = name,
                        gender = selectedGender,
                        city = city,
                        hometown = locality,
                        highSchool = highSchool,
                        highSchoolGraduationYear = highSchoolGradYear,
                        college = college,
                        collegeGraduationYear = collegeGradYear,
                        collegeDegree = collegeDegree.ifBlank { null },
                        postGraduation = postGrad.ifBlank { null },
                        postGraduationYear = postGradYear,
                        postGraduationDegree = postGraduationDegree.ifBlank { null },
                        community = community,
                        religion = religion,
                        height   = heightCm,
                        height2  = if (isHeightInFeet) listOf(feet, inches) else emptyList(),
                        jobRole = selectedJobRole,
                        customJobRole = (selectedJobRole.takeIf { it == jobRoleOptions.last() }?.let { customJobRole } ?: null),
                        work = selectedWork,
                        customWork = (selectedWork.takeIf { it == workOptions.last() }?.let { customWork } ?: null)
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BF63)),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.save), color = Color.White)
            }
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.cancel), color = Color.White)
            }
        }
    }
}

@Composable
fun PerformanceMetricsSection(profile: Profile) {
    var showPerformance by rememberSaveable { mutableStateOf(true) }

    CollapsibleSection(
        title       = stringResource(R.string.performance_metrics),
        icon        = Icons.Default.Assessment,
        isExpanded  = showPerformance,
        onToggle    = { showPerformance = !showPerformance },
        editMode    = false,
        editable    = false                       // ← no pencil icon
    ) {
        ProfileDetailRow(stringResource(R.string.matches), profile.matchCount.toString(), Icons.Default.People)
        ProfileDetailRow(stringResource(R.string.rating), String.format("%.2f", profile.averageRating), Icons.Default.Star)
        ProfileDetailRow(
            label = stringResource(R.string.swipe_right_probability),
            value = "${(profile.averageSwipeRightsOnUser * 100).roundToInt()}%",
            icon  = Icons.Default.Swipe
        )
        ProfileDetailRow(
            label = stringResource(R.string.west_bengal_ranking),
            value = profile.am24Ranking.toString(),
            icon  = Icons.Default.Public
        )

        /* city‑level rank */
        val cityRank = if (profile.city == stringResource(R.string.college_other))
            profile.am24RankingCustomCity else profile.am24RankingCity
        if (cityRank > 0) {
            ProfileDetailRow(
                label = "${profile.city.ifBlank { stringResource(R.string.city_label) }} Ranking",
                value = cityRank.toString(),
                icon  = Icons.Default.LocationCity
            )
        }

        /* hometown/locality rank */
        val hoodRank = if (profile.hometown == stringResource(R.string.college_other))
            profile.am24RankingCustomHometown else profile.am24RankingHometown
        if (hoodRank > 0) {
            ProfileDetailRow(
                label = "${profile.hometown.ifBlank { stringResource(R.string.locality_label) }} Ranking",
                value = hoodRank.toString(),
                icon  = Icons.Default.Home
            )
        }

        ProfileDetailRow(stringResource(R.string.age_ranking), profile.am24RankingAge.toString(), Icons.Default.Cake)

        if (profile.highSchool.isNotBlank()) {
            ProfileDetailRow(
                "${profile.highSchool} Ranking",
                profile.am24RankingHighSchool.toString(),
                Icons.Default.School
            )
        }
        if (profile.college.isNotBlank()) {
            ProfileDetailRow(
                "${profile.college} Ranking",
                profile.am24RankingCollege.toString(),
                Icons.Default.Book
            )
        }
    }
}

/** Preferences (View-Only) */
@Composable
fun PreferencesSection(profile: Profile) {
    val lookingForText = profile.lookingFor.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_specified)
    ProfileDetailRow(stringResource(R.string.looking_for_label), lookingForText, Icons.Default.Favorite)
    ProfileDetailRow(stringResource(R.string.label_love_language), profile.loveLanguage.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_set), Icons.Default.Favorite)
    ProfileDetailRow(stringResource(R.string.label_politics), profile.politics.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_set), Icons.Default.HowToVote)
}

fun isLifestyleEmpty(lifestyle: Lifestyle?): Boolean {
    if (lifestyle == null) return true
    return listOf(
        lifestyle.smoking_habit,
        lifestyle.drinking_habit,
        lifestyle.indoor_outdoor_orientation,
        lifestyle.social_media_engagement,
        lifestyle.work_life_balance,
        lifestyle.exercise_frequency,
        lifestyle.family_orientated,
        lifestyle.sleep_pattern,
        lifestyle.adventurousness,
        lifestyle.intellectual_curiosity,
        lifestyle.creative_expression,
        lifestyle.physical_fitness,
        lifestyle.spirituality_mindfulness,
        lifestyle.easy_goingness,
        lifestyle.professional_ambition,
        lifestyle.environmental_awareness,
        lifestyle.sports_enthusiasm,
        lifestyle.sociability,
        lifestyle.sexual_activity_level
    ).all { it == -1 }
}

@Composable
fun LifestyleSection(profile: Profile) {
    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontSize = 12.sp)) {
        Column {
            val lifestyle = profile.lifestyle
            if (isLifestyleEmpty(lifestyle)) {
                Text("No lifestyle specified.", color = Color.Gray, fontSize = 16.sp)
            } else {
                profile.lifestyle?.let { lifestyle ->
                    if (lifestyle.smoking_habit != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_smoking),
                            value = lifestyle.smoking_habit,
                            nouns = listOf(
                                stringResource(R.string.non_smoker),
                                stringResource(R.string.rare_smoker),
                                stringResource(R.string.social_smoker),
                                stringResource(R.string.frequent_smoker),
                                stringResource(R.string.heavy_smoker)
                            ),
                            icon = Icons.Default.SmokingRooms
                        )
                    if (lifestyle.drinking_habit != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_drinking),
                            value = lifestyle.drinking_habit,
                            nouns = listOf(
                                stringResource(R.string.non_drinker),
                                stringResource(R.string.rare_drinker),
                                stringResource(R.string.social_drinker),
                                stringResource(R.string.frequent_drinker),
                                stringResource(R.string.heavy_drinker)
                            ),
                            icon = Icons.Default.LocalDrink
                        )
                    if (lifestyle.indoor_outdoor_orientation != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_going_out),
                            value = lifestyle.indoor_outdoor_orientation,
                            nouns = listOf(
                                stringResource(R.string.very_indoorsy),
                                stringResource(R.string.mostly_indoorsy),
                                stringResource(R.string.balanced),
                                stringResource(R.string.mostly_outdoorsy),
                                stringResource(R.string.very_outdoorsy)
                            ),
                            icon = Icons.Default.DirectionsWalk
                        )
                    if (lifestyle.social_media_engagement != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_social_media),
                            value = lifestyle.social_media_engagement,
                            nouns = listOf(
                                stringResource(R.string.invisible),
                                stringResource(R.string.watcher),
                                stringResource(R.string.casual_participant),
                                stringResource(R.string.engager),
                                stringResource(R.string.influencer)
                            ),
                            icon = Icons.Default.Groups2
                        )
                    if (lifestyle.work_life_balance != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_work_life_balance),
                            value = lifestyle.work_life_balance,
                            nouns = listOf(
                                stringResource(R.string.workaholic),
                                stringResource(R.string.more_work_oriented),
                                stringResource(R.string.balanced),
                                stringResource(R.string.more_life_oriented),
                                stringResource(R.string.relaxed),
                            ),
                            icon = Icons.Default.WorkOff
                        )
                    if (lifestyle.exercise_frequency != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_exercise),
                            value = lifestyle.exercise_frequency,
                            nouns = listOf(
                                stringResource(R.string.inactive),
                                stringResource(R.string.rarely_active),
                                stringResource(R.string.moderately_active),
                                stringResource(R.string.active),
                                stringResource(R.string.very_active)
                            ),
                            icon = Icons.Default.SportsGymnastics
                        )
                    if (lifestyle.family_orientated != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_family_oriented),
                            value = lifestyle.family_orientated,
                            nouns = listOf(
                                stringResource(R.string.independent),
                                stringResource(R.string.slightly_family_oriented),
                                stringResource(R.string.balanced),
                                stringResource(R.string.more_family_oriented),
                                stringResource(R.string.very_family_oriented)
                            ),
                            icon = Icons.Default.FamilyRestroom
                        )
                    if (lifestyle.sleep_pattern != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_sleep),
                            value = lifestyle.sleep_pattern,
                            nouns = listOf(
                                stringResource(R.string.early_riser),
                                stringResource(R.string.morning_person),
                                stringResource(R.string.balanced),
                                stringResource(R.string.night_owl),
                                stringResource(R.string.late_night_enthusiast)
                            ),
                            icon = Icons.Default.Bedtime
                        )
                    if (lifestyle.adventurousness != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_adventurousness),
                            value = lifestyle.adventurousness,
                            nouns = listOf(
                                stringResource(R.string.cautious),
                                stringResource(R.string.slightly_adventurous),
                                stringResource(R.string.moderately_adventurous),
                                stringResource(R.string.adventurous),
                                stringResource(R.string.thrill_seeker)
                            ),
                            icon = Icons.Default.Hiking
                        )
                    if (lifestyle.intellectual_curiosity != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_intellectual_curiosity),
                            value = lifestyle.intellectual_curiosity,
                            nouns = listOf(
                                stringResource(R.string.casual_thinker),
                                stringResource(R.string.inquisitive),
                                stringResource(R.string.knowledge_seeker),
                                stringResource(R.string.intellectual),
                                stringResource(R.string.philosopher)
                            ),
                            icon = Icons.Default.School
                        )
                    if (lifestyle.creative_expression != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_creative_expression),
                            value = lifestyle.creative_expression,
                            nouns = listOf(
                                stringResource(R.string.not_creative),
                                stringResource(R.string.somewhat_creative),
                                stringResource(R.string.creative),
                                stringResource(R.string.very_creative),
                                stringResource(R.string.artistic_genius)
                            ),
                            icon = Icons.Default.Palette
                        )
                    if (lifestyle.physical_fitness != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_physical_fitness),
                            value = lifestyle.physical_fitness,
                            nouns = listOf(
                                stringResource(R.string.sedentary),
                                stringResource(R.string.somewhat_fit),
                                stringResource(R.string.fit),
                                stringResource(R.string.athletic),
                                stringResource(R.string.peak_fitness)
                            ),
                            icon = Icons.Default.FitnessCenter
                        )
                    if (lifestyle.spirituality_mindfulness != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_spirituality),
                            value = lifestyle.spirituality_mindfulness,
                            nouns = listOf(
                                stringResource(R.string.not_spiritual),
                                stringResource(R.string.occasionally_mindful),
                                stringResource(R.string.balanced),
                                stringResource(R.string.spiritual),
                                stringResource(R.string.deeply_mindful)
                            ),
                            icon = Icons.Default.SelfImprovement
                        )
                    if (lifestyle.easy_goingness != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_humor),
                            value = lifestyle.easy_goingness,
                            nouns = listOf(
                                stringResource(R.string.serious),
                                stringResource(R.string.somewhat_easygoing),
                                stringResource(R.string.balanced),
                                stringResource(R.string.humorous),
                                stringResource(R.string.life_of_the_party)
                            ),
                            icon = Icons.Default.SentimentVerySatisfied
                        )
                    if (lifestyle.professional_ambition != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_professional_ambition),
                            value = lifestyle.professional_ambition,
                            nouns = listOf(
                                stringResource(R.string.relaxed),
                                stringResource(R.string.occasionally_driven),
                                stringResource(R.string.balanced),
                                stringResource(R.string.ambitious),
                                stringResource(R.string.high_ambitious)
                            ),
                            icon = Icons.Default.Work
                        )
                    if (lifestyle.environmental_awareness != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_environmental_awareness),
                            value = lifestyle.environmental_awareness,
                            nouns = listOf(
                                stringResource(R.string.not_conscious),
                                stringResource(R.string.occasionally_conscious),
                                stringResource(R.string.balanced),
                                stringResource(R.string.eco_friendly),
                                stringResource(R.string.eco_champion)
                            ),
                            icon = Icons.Default.Eco
                        )
                    if (lifestyle.culinary_enthusiasm != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_culinary_enthusiasm),
                            value = lifestyle.culinary_enthusiasm,
                            nouns = listOf(
                                stringResource(R.string.not_a_foodie),
                                stringResource(R.string.occasional_foodie),
                                stringResource(R.string.foodie),
                                stringResource(R.string.passionate_foodie),
                                stringResource(R.string.gourmet)
                            ),
                            icon = Icons.Default.LocalDining
                        )
                    if (lifestyle.political_awareness != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_political_awareness),
                            value = lifestyle.political_awareness,
                            nouns = listOf(
                                stringResource(R.string.unaware),
                                stringResource(R.string.occasionally_aware),
                                stringResource(R.string.balanced),
                                stringResource(R.string.aware),
                                stringResource(R.string.politically_engaged)
                            ),
                            icon = Icons.Default.Gavel
                        )
                    if (lifestyle.community_engagement != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_community_engagement),
                            value = lifestyle.community_engagement,
                            nouns = listOf(
                                stringResource(R.string.individualistic),
                                stringResource(R.string.occasionally_involved),
                                stringResource(R.string.balanced),
                                stringResource(R.string.community_oriented),
                                stringResource(R.string.community_leader)
                            ),
                            icon = Icons.Default.Groups
                        )
                    if (lifestyle.sports_enthusiasm != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_sports),
                            value = lifestyle.sports_enthusiasm,
                            nouns = listOf(
                                stringResource(R.string.non_sports),
                                stringResource(R.string.casual_viewer),
                                stringResource(R.string.occasional_player),
                                stringResource(R.string.sports_enthusiast),
                                stringResource(R.string.sports_fanatic)
                            ),
                            icon = Icons.Default.SportsSoccer
                        )
                    if (lifestyle.sociability != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_sociability),
                            value = lifestyle.sociability,
                            nouns = listOf(
                                stringResource(R.string.not_introverted),
                                stringResource(R.string.slightly_introverted),
                                stringResource(R.string.moderately_introverted),
                                stringResource(R.string.very_introverted),
                                stringResource(R.string.extremely_introverted)
                            ),
                            icon = Icons.Default.Person
                        )
                    if (lifestyle.sexual_activity_level != -1)
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_sexual_activity),
                            value = lifestyle.sexual_activity_level,
                            nouns = listOf(stringResource(R.string.inactive), stringResource(R.string.low), stringResource(R.string.moderate), stringResource(R.string.high), stringResource(R.string.very_high)),
                            icon = Icons.Default.Favorite
                        )
                }
            }
        }
    }
}

/** Interests (View-Only) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsSectionInProfile(profile: Profile) {
    val interestNameToResource = mapOf(
        // Global Interests
        "Music" to R.string.interest_music,
        "সঙ্গীত" to R.string.interest_music, // Bengali
        "संगीत" to R.string.interest_music, // Hindi
        "Movies" to R.string.interest_movies,
        "সিনেমা" to R.string.interest_movies, // Bengali
        "फ़िल्में" to R.string.interest_movies, // Hindi
        "Sports" to R.string.interest_sports,
        "খেলাধুলা" to R.string.interest_sports, // Bengali
        "खेल" to R.string.interest_sports, // Hindi
        "Books" to R.string.interest_books,
        "বই" to R.string.interest_books, // Bengali
        "किताबें" to R.string.interest_books, // Hindi
        "Travel" to R.string.interest_travel,
        "ভ্রমণ" to R.string.interest_travel, // Bengali
        "यात्रा" to R.string.interest_travel, // Hindi
        "Fitness" to R.string.interest_fitness,
        "ফিটনেস" to R.string.interest_fitness, // Bengali
        "फ़िटनेस" to R.string.interest_fitness, // Hindi
        "Art" to R.string.interest_art,
        "শিল্প" to R.string.interest_art, // Bengali
        "कला" to R.string.interest_art, // Hindi
        "Gaming" to R.string.interest_gaming,
        "গেমিং" to R.string.interest_gaming, // Bengali
        "गेमिंग" to R.string.interest_gaming, // Hindi
        "Photography" to R.string.interest_photography,
        "ফটোগ্রাফি" to R.string.interest_photography, // Bengali
        "फ़ोटोग्राफी" to R.string.interest_photography, // Hindi
        "Cooking" to R.string.interest_cooking,
        "রান্না" to R.string.interest_cooking, // Bengali
        "खाना बनाना" to R.string.interest_cooking, // Hindi
        "Dancing" to R.string.interest_dancing,
        "নাচ" to R.string.interest_dancing, // Bengali
        "नृत्य" to R.string.interest_dancing, // Hindi
        "Gardening" to R.string.interest_gardening,
        "বাগান করা" to R.string.interest_gardening, // Bengali
        "बागवानी" to R.string.interest_gardening, // Hindi
        "Technology" to R.string.interest_technology,
        "প্রযুক্তি" to R.string.interest_technology, // Bengali
        "प्रौद्योगिकी" to R.string.interest_technology, // Hindi
        "Fashion" to R.string.interest_fashion,
        "ফ্যাশন" to R.string.interest_fashion, // Bengali
        "फ़ैशन" to R.string.interest_fashion, // Hindi
        "Volunteering" to R.string.interest_volunteering,
        "স্বেচ্ছাসেবা" to R.string.interest_volunteering, // Bengali
        "स्वयंसेवा" to R.string.interest_volunteering, // Hindi
        "Pets & Animals" to R.string.interest_pets,
        "পোষ্য" to R.string.interest_pets, // Bengali
        "पालतू जानवर" to R.string.interest_pets, // Hindi
        "Food" to R.string.interest_food,
        "খাবার" to R.string.interest_food, // Bengali
        "भोजन" to R.string.interest_food, // Hindi
        "Nature" to R.string.interest_nature,
        "প্রকৃতি" to R.string.interest_nature, // Bengali
        "प्रकृति" to R.string.interest_nature, // Hindi
        "Dance" to R.string.interest_dance,
        "নাচ" to R.string.interest_dance, // Bengali (same as Dancing)
        "नृत्य" to R.string.interest_dance, // Hindi (same as Dancing)
        // West-Bengal Locality Interests
        "Victoria Memorial" to R.string.interest_victoria_memorial,
        "ভিক্টোরিয়া মেমোরিয়াল" to R.string.interest_victoria_memorial, // Bengali
        "विक्टोरिया मेमोरियल" to R.string.interest_victoria_memorial, // Hindi
        "Princep Ghat" to R.string.interest_princep_ghat,
        "প্রিন্সেপ ঘাট" to R.string.interest_princep_ghat, // Bengali
        "प्रिंसप घाट" to R.string.interest_princep_ghat, // Hindi
        "Nicco Park" to R.string.interest_nicco_park,
        "নিক্কো পার্ক" to R.string.interest_nicco_park, // Bengali
        "निक्को पार्क" to R.string.interest_nicco_park, // Hindi
        "Science City" to R.string.interest_science_city,
        "সায়েন্স সিটি" to R.string.interest_science_city, // Bengali
        "साइंस सिटी" to R.string.interest_science_city, // Hindi
        "Dakshineswar Temple" to R.string.interest_dakshineswar_temple,
        "দক্ষিণেশ্বর মন্দির" to R.string.interest_dakshineswar_temple, // Bengali
        "दक्षिणेश्वर मंदिर" to R.string.interest_dakshineswar_temple, // Hindi
        "Howrah Bridge" to R.string.interest_howrah_bridge,
        "হাওড়া ব্রিজ" to R.string.interest_howrah_bridge, // Bengali
        "हावड़ा ब्रिज" to R.string.interest_howrah_bridge, // Hindi
        "IIT Kharagpur Campus" to R.string.interest_iit_kharagpur_campus,
        "আইআইটি খড়গপুর ক্যাম্পাস" to R.string.interest_iit_kharagpur_campus, // Bengali
        "आईआईटी खड़गपुर कैंपस" to R.string.interest_iit_kharagpur_campus, // Hindi
        "Digha Beach" to R.string.interest_digha_beach,
        "দীঘা বিচ" to R.string.interest_digha_beach, // Bengali
        "दीघा बीच" to R.string.interest_digha_beach, // Hindi
        "Tiger Hill" to R.string.interest_tiger_hill,
        "টাইগার হিল" to R.string.interest_tiger_hill, // Bengali
        "टाइगर हिल" to R.string.interest_tiger_hill, // Hindi
        "Mall Road (Darjeeling)" to R.string.interest_mall_road,
        "মল রোড (দার্জিলিং)" to R.string.interest_mall_road, // Bengali
        "मॉल रोड (दार्जिलिंग)" to R.string.interest_mall_road, // Hindi
        "Hazarduari Palace" to R.string.interest_hazarduari_palace,
        "হাজারদুয়ারি প্যালেস" to R.string.interest_hazarduari_palace, // Bengali
        "हज़ारद्वारी पैलेस" to R.string.interest_hazarduari_palace, // Hindi
        "Shantiniketan" to R.string.interest_shantiniketan,
        "শান্তিনিকেতন" to R.string.interest_shantiniketan, // Bengali
        "शांतिनिकेतन" to R.string.interest_shantiniketan, // Hindi
        // Kolkata Cluster
        "CC Block Market" to R.string.interest_cc_block_market,
        "সিসি ব্লক মার্কেট" to R.string.interest_cc_block_market, // Bengali
        "सीसी ब्लॉक मार्केट" to R.string.interest_cc_block_market, // Hindi
        "Sector V IT Hub" to R.string.interest_sector_v_it_hub,
        "সেক্টর ৫ আইটি হাব" to R.string.interest_sector_v_it_hub, // Bengali
        "सेक्टर V आईटी हब" to R.string.interest_sector_v_it_hub, // Hindi
        "Eco Park" to R.string.interest_eco_park,
        "ইকো পার্ক" to R.string.interest_eco_park, // Bengali
        "इको पार्क" to R.string.interest_eco_park, // Hindi
        "City Centre 2" to R.string.interest_city_centre_2,
        "সিটি সেন্টার ২" to R.string.interest_city_centre_2, // Bengali
        "सिटी सेंटर 2" to R.string.interest_city_centre_2, // Hindi
        "Airport Area" to R.string.interest_airport_area,
        "বিমানবন্দর এলাকা" to R.string.interest_airport_area, // Bengali
        "एयरपोर्ट क्षेत्र" to R.string.interest_airport_area, // Hindi
        "Local Market" to R.string.interest_local_market,
        "স্থানীয় বাজার" to R.string.interest_local_market, // Bengali
        "स्थानीय बाज़ार" to R.string.interest_local_market, // Hindi
        "Old Market" to R.string.interest_old_market,
        "পুরনো বাজার" to R.string.interest_old_market, // Bengali
        "पुराना बाज़ार" to R.string.interest_old_market, // Hindi
        "Local Eateries" to R.string.interest_local_eateries,
        "স্থানীয় খাবার দোকান" to R.string.interest_local_eateries, // Bengali
        "स्थानीय भोजनालय" to R.string.interest_local_eateries, // Hindi
        "Night-life" to R.string.interest_nightlife,
        "নাইটলাইফ" to R.string.interest_nightlife, // Bengali
        "नाइट-लाइफ़" to R.string.interest_nightlife, // Hindi
        "Park Street Cafés" to R.string.interest_park_street_cafes,
        "পার্ক স্ট্রিট ক্যাফে" to R.string.interest_park_street_cafes, // Bengali
        "पार्क स्ट्रीट कैफ़े" to R.string.interest_park_street_cafes, // Hindi
        // Hooghly / Chandannagar
        "Chandannagar Strand" to R.string.interest_chandannagar_strand,
        "চন্দননগর স্ট্র্যান্ড" to R.string.interest_chandannagar_strand, // Bengali
        "चंदननगर स्ट्रैंड" to R.string.interest_chandannagar_strand, // Hindi
        "French Heritage" to R.string.interest_french_heritage,
        "ফরাসি ঐতিহ্য" to R.string.interest_french_heritage, // Bengali
        "फ़्रांसीसी विरासत" to R.string.interest_french_heritage, // Hindi
        "Riverside Ghats" to R.string.interest_riverside_ghats,
        "নদীপাড়ের ঘাট" to R.string.interest_riverside_ghats, // Bengali
        "नदी किनारे घाट" to R.string.interest_riverside_ghats, // Hindi
        "Heritage Walks" to R.string.interest_heritage_walks,
        "ঐতিহ্য ভ্রমণ" to R.string.interest_heritage_walks, // Bengali
        "हेरिटेज वॉक" to R.string.interest_heritage_walks, // Hindi
        // Howrah
        "Belur Math" to R.string.interest_belur_math,
        "বেলুড় মঠ" to R.string.interest_belur_math, // Bengali
        "बेलूर मठ" to R.string.interest_belur_math, // Hindi
        "Avani Mall" to R.string.interest_avani_mall,
        "আভানি মল" to R.string.interest_avani_mall, // Bengali
        "अवानी मॉल" to R.string.interest_avani_mall, // Hindi
        // Durgapur / Asansol Belt
        "City Centre Plaza" to R.string.interest_city_centre_plaza,
        "সিটি সেন্টার প্লাজা" to R.string.interest_city_centre_plaza, // Bengali
        "सिटी सेंटर प्लाज़ा" to R.string.interest_city_centre_plaza, // Hindi
        "Steel-Plant Tour" to R.string.interest_steel_plant_tour,
        "স্টিল প্ল্যান্ট ভ্রমণ" to R.string.interest_steel_plant_tour, // Bengali
        "स्टील-प्लांट टूर" to R.string.interest_steel_plant_tour, // Hindi
        "Burnpur Riverside" to R.string.interest_burnpur_riverside,
        "বার্নপুর নদীপাড়" to R.string.interest_burnpur_riverside, // Bengali
        "बर्नपुर रिवरसाइड" to R.string.interest_burnpur_riverside, // Hindi
        "Chittaranjan Park" to R.string.interest_chittaranjan_park,
        "চিত্তরঞ্জন পার্ক" to R.string.interest_chittaranjan_park, // Bengali
        "चित्तरंजन पार्क" to R.string.interest_chittaranjan_park, // Hindi
        // North-Bengal Cluster
        "Hongkong Market" to R.string.interest_hongkong_market,
        "হংকং মার্কেট" to R.string.interest_hongkong_market, // Bengali
        "हॉन्गकॉन्ग मार्केट" to R.string.interest_hongkong_market, // Hindi
        "Mahananda Wildlife Sanctuary" to R.string.interest_mahananda_wls,
        "মহানন্দা বন্যপ্রাণী অভয়ারণ্য" to R.string.interest_mahananda_wls, // Bengali
        "महानंदा वन्यजीव अभयारण्य" to R.string.interest_mahananda_wls, // Hindi
        "Toy-Train" to R.string.interest_toy_train,
        "টয় ট্রেন" to R.string.interest_toy_train, // Bengali
        "टॉय ट्रेन" to R.string.interest_toy_train, // Hindi
        "Tea-Estate Walks" to R.string.interest_tea_estate_walks,
        "চা বাগান ভ্রমণ" to R.string.interest_tea_estate_walks, // Bengali
        "चाय बागान भ्रमण" to R.string.interest_tea_estate_walks, // Hindi
        "Gorumara Safari" to R.string.interest_gorumara_safari,
        "গরুমারা সাফারি" to R.string.interest_gorumara_safari, // Bengali
        "गोरूमारा सफ़ारी" to R.string.interest_gorumara_safari, // Hindi
        "Rafting on Teesta" to R.string.interest_rafting_teesta,
        "তিস্তা র্যাফটিং" to R.string.interest_rafting_teesta, // Bengali
        "तीस्ता राफ्टिंग" to R.string.interest_rafting_teesta, // Hindi
        "Rajbari Palace" to R.string.interest_rajbari_palace,
        "রাজবাড়ি প্রাসাদ" to R.string.interest_rajbari_palace, // Bengali
        "राजबाड़ी महल" to R.string.interest_rajbari_palace, // Hindi
        "Sagar-Dighi" to R.string.interest_sagar_dighi,
        "সাগর-দিঘি" to R.string.interest_sagar_dighi, // Bengali
        "सागर-दिघी" to R.string.interest_sagar_dighi, // Hindi
        "Buxa Fort Trek" to R.string.interest_buxa_fort_trek,
        "বক্সা দুর্গ ট্রেক" to R.string.interest_buxa_fort_trek, // Bengali
        "बक्सा क़िला ट्रेक" to R.string.interest_buxa_fort_trek, // Hindi
        "Jayanti River Picnic" to R.string.interest_jayanti_picnic,
        "জয়ন্তী নদী পিকনিক" to R.string.interest_jayanti_picnic, // Bengali
        "जयंती नदी पिकनिक" to R.string.interest_jayanti_picnic, // Hindi
        // South-West Cluster
        "IIT Campus Walk" to R.string.interest_iit_campus_walk,
        "আইআইটি ক্যাম্পাস হাঁটা" to R.string.interest_iit_campus_walk, // Bengali
        "आईआईटी कैंपस वॉक" to R.string.interest_iit_campus_walk, // Hindi
        "Gol Bazaar Food" to R.string.interest_gol_bazaar_food,
        "গোল বাজার খাবার" to R.string.interest_gol_bazaar_food, // Bengali
        "गोल बाज़ार भोजन" to R.string.interest_gol_bazaar_food, // Hindi
        "Vidyasagar Uni Lake" to R.string.interest_vidyasagar_lake,
        "বিদ্যাসাগর বিশ্ববিদ্যালয় লেক" to R.string.interest_vidyasagar_lake, // Bengali
        "विद्यासागर विश्वविद्यालय झील" to R.string.interest_vidyasagar_lake, // Hindi
        "Khudiram Park" to R.string.interest_khudiram_park,
        "ক্ষুদিরাম পার্ক" to R.string.interest_khudiram_park, // Bengali
        "खुदीराम पार्क" to R.string.interest_khudiram_park, // Hindi
        "River Cruise" to R.string.interest_river_cruise,
        "নৌ ভ্রমণ" to R.string.interest_river_cruise, // Bengali
        "रिवर क्रूज़" to R.string.interest_river_cruise, // Hindi
        "Marine Drive" to R.string.interest_marine_drive,
        "মেরিন ড্রাইভ" to R.string.interest_marine_drive, // Bengali
        "मरीन ड्राइव" to R.string.interest_marine_drive, // Hindi
        // Central WB
        "Curzon Gate Photo-Op" to R.string.interest_curzon_gate_photo,
        "কার্জন গেট ছবি" to R.string.interest_curzon_gate_photo, // Bengali
        "करज़न गेट फ़ोटो-ऑप" to R.string.interest_curzon_gate_photo, // Hindi
        "Sitabhog & Mihidana Tasting" to R.string.interest_sitabhog_mihidana,
        "সিতাভোগ ও মিহিদানা টেস্টিং" to R.string.interest_sitabhog_mihidana, // Bengali
        "सिताभोग और मिहिदाना चखना" to R.string.interest_sitabhog_mihidana, // Hindi
        "Terracotta Art" to R.string.interest_terracotta_art,
        "টেরাকোটা শিল্প" to R.string.interest_terracotta_art, // Bengali
        "टेराकोटा कला" to R.string.interest_terracotta_art, // Hindi
        "Susunia Trek" to R.string.interest_susunia_trek,
        "সুসুনিয়া ট্রেক" to R.string.interest_susunia_trek, // Bengali
        "सुसुनिया ट्रेक" to R.string.interest_susunia_trek, // Hindi
        "Ayodhya Hills" to R.string.interest_ayodhya_hills,
        "অযোধ্যা পাহাড়" to R.string.interest_ayodhya_hills, // Bengali
        "अयोध्या हिल्स" to R.string.interest_ayodhya_hills, // Hindi
        "Chhau Dance" to R.string.interest_chhau_dance,
        "ছাউ নৃত্য" to R.string.interest_chhau_dance, // Bengali
        "छऊ नृत्य" to R.string.interest_chhau_dance, // Hindi
        // Nadia Zone
        "Clay-Doll Lane" to R.string.interest_clay_doll_lane,
        "মাটির পুতুল গলি" to R.string.interest_clay_doll_lane, // Bengali
        "मिट्टी की गुड़िया गली" to R.string.interest_clay_doll_lane, // Hindi
        "Ghurni Artists" to R.string.interest_ghurni_artists,
        "ঘূর্ণি শিল্পী" to R.string.interest_ghurni_artists, // Bengali
        "घूर्णी कलाकार" to R.string.interest_ghurni_artists, // Hindi
        "University Campus Walk" to R.string.interest_university_campus_walk,
        "বিশ্ববিদ্যালয় ক্যাম্পাস হাঁটা" to R.string.interest_university_campus_walk, // Bengali
        "विश्वविद्यालय कैंपस वॉक" to R.string.interest_university_campus_walk, // Hindi
        "Kalyani Lake" to R.string.interest_kalyani_lake,
        "কল্যাণী লেক" to R.string.interest_kalyani_lake, // Bengali
        "कल्याणी झील" to R.string.interest_kalyani_lake, // Hindi
        "Boutique Sarees" to R.string.interest_boutique_sarees,
        "বুটিক শাড়ি" to R.string.interest_boutique_sarees, // Bengali
        "बुटीक साड़ियाँ" to R.string.interest_boutique_sarees, // Hindi
        "Churni Riverbank" to R.string.interest_churni_riverbank,
        "চূর্ণি নদীপাড়" to R.string.interest_churni_riverbank, // Bengali
        "चूर्णी नदी तट" to R.string.interest_churni_riverbank, // Hindi
        // North-Centre / Murshidabad
        "Mango Festival" to R.string.interest_mango_festival,
        "আম উৎসব" to R.string.interest_mango_festival, // Bengali
        "आम महोत्सव" to R.string.interest_mango_festival, // Hindi
        "Gour Ruins" to R.string.interest_gour_ruins,
        "গৌড় ধ্বংসাবশেষ" to R.string.interest_gour_ruins, // Bengali
        "गौर के खंडहर" to R.string.interest_gour_ruins, // Hindi
        "Hazarduari Museum" to R.string.interest_hazar_duari_museum,
        "হাজারদুয়ারি জাদুঘর" to R.string.interest_hazar_duari_museum, // Bengali
        "हज़ारद्वारी संग्रहालय" to R.string.interest_hazar_duari_museum, // Hindi
        "Khusbagh Gardens" to R.string.interest_khusbagh_gardens,
        "খুশবাগ উদ্যান" to R.string.interest_khusbagh_gardens, // Bengali
        "खुशबाग गार्डन" to R.string.interest_khusbagh_gardens, // Hindi
        "Berhampore Silk Shopping" to R.string.interest_berhampore_silk,
        "বহরমপুর সিল্ক শপিং" to R.string.interest_berhampore_silk, // Bengali
        "बहरमपुर रेशम ख़रीदारी" to R.string.interest_berhampore_silk, // Hindi
        "Cossimbazar Rajbari" to R.string.interest_cossimbazar_rajbari,
        "কসিমবাজার রাজবাড়ি" to R.string.interest_cossimbazar_rajbari, // Bengali
        "कूसीमबाज़ार राजबाड़ी" to R.string.interest_cossimbazar_rajbari // Hindi
    )

    if (profile.interests.isEmpty()) {
        Text(
            text = stringResource(R.string.no_interests),
            color = Color.Gray,
            fontSize = 16.sp
        )
    } else {
        FlowRow {
            profile.interests.forEach { interest ->
                val resourceId = interestNameToResource[interest.name]
                val interestLabel = if (resourceId != null) {
                    stringResource(resourceId)
                } else {
                    interest.name // Fallback to raw name if not found in map
                }
                InterestTag(
                    label = buildString {
                        if (!interest.emoji.isNullOrEmpty()) append("${interest.emoji} ")
                        append(interestLabel)
                    }
                )
            }
        }
    }
}

@Composable
fun CombinedBioVoiceEditSection(
    currentBio: String?,
    currentVoiceUrl: String?,
    profileViewModel: ProfileViewModel,
    onSave: (String, String?) -> Unit,
    onCancel: () -> Unit
) {
    val context    = LocalContext.current
    val storageRef = FirebaseStorage.getInstance().reference
    val filePath   = remember { File(context.filesDir, "voice_note_edit.mp3").absolutePath }

    // ——— Bio text —————————————————————————
    var bio by remember { mutableStateOf(currentBio.orEmpty()) }

    // ——— Voice state ——————————————————————
    var isRecording   by remember { mutableStateOf(false) }
    var isPlaying     by remember { mutableStateOf(false) }
    var isVoiceValid  by remember { mutableStateOf(true) }
    var voiceUri      by remember { mutableStateOf<Uri?>(currentVoiceUrl?.let(Uri::parse)) }
    var newVoiceUrl   by remember { mutableStateOf(currentVoiceUrl) }
    var isUploading   by remember { mutableStateOf(false) }
    var voiceProgress by remember { mutableStateOf(0f) }
    var voiceDuration by remember { mutableStateOf(0L) }
    val mediaPlayer   = remember { MediaPlayer() }

    // validate duration <= 60s
    fun validateVoice() {
        try {
            MediaPlayer().apply {
                setDataSource(voiceUri?.path ?: filePath)
                prepare()
                voiceDuration = duration.toLong()
                release()
            }
            isVoiceValid = voiceDuration <= 60_000L
        } catch (e: Exception) {
            isVoiceValid = false
        }
    }

    // permissions launcher
    val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            isRecording = true
            profileViewModel.startVoiceRecording(context, filePath)
        }
    }

    val toggleRecording = {
        if (isRecording) {
            // stop recording & validate
            isRecording = false
            profileViewModel.stopVoiceRecording()
            voiceUri = Uri.fromFile(File(filePath))
            validateVoice()

            if (isVoiceValid && voiceUri != null) {
                isUploading = true
                profileViewModel.uploadVoiceToRealtime(storageRef, voiceUri!!) { downloadUrl ->
                    newVoiceUrl = downloadUrl
                    isUploading   = false
                }
            }
        } else {
            permLauncher.launch(permissions)
        }
    }

    // playback toggle
    val togglePlayback = {
        if (isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
        } else {
            try {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(voiceUri?.path ?: filePath)
                mediaPlayer.prepare()
                mediaPlayer.start()
                isPlaying = true
            } catch (_: IOException) { }
        }
    }

    // track playback progress
    LaunchedEffect(isPlaying) {
        while (isPlaying && mediaPlayer.isPlaying) {
            voiceProgress = (mediaPlayer.currentPosition / voiceDuration.toFloat()).coerceIn(0f,1f)
            delay(200)
        }
        if (!mediaPlayer.isPlaying) {
            isPlaying     = false
            voiceProgress = 0f
        }
    }
    DisposableEffect(Unit) {
        onDispose { mediaPlayer.release() }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        // — Text Bio —
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text(stringResource(R.string.bio)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        // — Voice recorder button —
        IconButton(
            onClick = toggleRecording,
            enabled = !isUploading
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = stringResource(R.string.record_voice_bio),
                tint = if (isRecording) Color.Red else Color.White,
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                    .clip(CircleShape)
            )
        }
        if (!isVoiceValid) {
            Text(
                text = stringResource(R.string.voice_bio_duration_error),
                color = Color.Red
            )
        }
        if (isUploading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))

        // — Playback UI —
        voiceUri?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = togglePlayback) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying)
                            stringResource(R.string.pause_audio)
                        else
                            stringResource(R.string.tap_to_play)
                    )
                }
                Slider(
                    value = voiceProgress,
                    onValueChange = { },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // — Save / Cancel —
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel), color = Color.Red)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { onSave(bio, newVoiceUrl) },
                enabled = isVoiceValid && !isUploading
            ) {
                Text(stringResource(R.string.save), color = Color.Green)
            }
        }
    }
}


/** Preferences Edit */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    val notSelected = stringResource(R.string.not_selected)

    // Looking For
    val lookingForOptions = listOf(
        stringResource(R.string.looking_for_not_selected),
        stringResource(R.string.looking_for_casual_sex),
        stringResource(R.string.looking_for_connection),
        stringResource(R.string.looking_for_partner),
        stringResource(R.string.looking_for_marriage)
    )
    var selectedLookingFor by remember { mutableStateOf(tempProfile.lookingFor.ifBlank { notSelected }) }
    var lookingForExpanded by remember { mutableStateOf(false) }

    // Love Language (no custom/Other)
    val loveLanguageOptions = listOf(
        stringResource(R.string.love_language_option_words_of_affirmation),
        stringResource(R.string.love_language_option_acts_of_service),
        stringResource(R.string.love_language_option_receiving_gifts),
        stringResource(R.string.love_language_option_quality_time),
        stringResource(R.string.love_language_option_physical_touch)
    )
    var selectedLoveLanguage by remember { mutableStateOf(tempProfile.loveLanguage.ifBlank { notSelected }) }
    var loveLanguageExpanded by remember { mutableStateOf(false) }

    // Politics (no custom/Other)
    val politicsOptions = listOf(
        stringResource(R.string.politics_option_liberal),
        stringResource(R.string.politics_option_moderate),
        stringResource(R.string.politics_option_conservative)
    )
    var selectedPolitics by remember { mutableStateOf(tempProfile.politics.ifBlank { notSelected }) }
    var politicsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Looking For ---
        Text(stringResource(R.string.looking_for_label), fontWeight = FontWeight.Bold)
        Button(
            onClick = { lookingForExpanded = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
        ) {
            Text(
                text = if (selectedLookingFor == notSelected)
                    stringResource(R.string.select_looking_for)
                else
                    selectedLookingFor,
                color = Color.White
            )
        }
        DropdownMenu(
            expanded = lookingForExpanded,
            onDismissRequest = { lookingForExpanded = false }
        ) {
            lookingForOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        selectedLookingFor = option
                        lookingForExpanded = false
                    }
                )
            }
        }

        // --- Love Language ---
        Text(stringResource(R.string.love_language_label), fontWeight = FontWeight.Bold)
        Button(onClick = { loveLanguageExpanded = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
        ) {
            Text(
                text = if (selectedLoveLanguage == notSelected)
                    stringResource(R.string.select_love_language)
                else
                    selectedLoveLanguage,
                color = Color.White
            )
        }
        DropdownMenu(
            expanded = loveLanguageExpanded,
            onDismissRequest = { loveLanguageExpanded = false }
        ) {
            loveLanguageOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        selectedLoveLanguage = option
                        loveLanguageExpanded = false
                    }
                )
            }
        }

        // --- Politics ---
        Text(stringResource(R.string.label_politics), fontWeight = FontWeight.Bold)
        Button(onClick = { politicsExpanded = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
        ) {
            Text(
                text = if (selectedPolitics == notSelected)
                    stringResource(R.string.select_politics)
                else
                    selectedPolitics,
                color = Color.White
            )
        }
        DropdownMenu(
            expanded = politicsExpanded,
            onDismissRequest = { politicsExpanded = false }
        ) {
            politicsOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        selectedPolitics = option
                        politicsExpanded = false
                    }
                )
            }
        }

        // --- Save / Cancel ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val updated = tempProfile.copy(
                        lookingFor   = selectedLookingFor.takeIf { it != notSelected } ?: "",
                        loveLanguage = selectedLoveLanguage.takeIf { it != notSelected } ?: "",
                        politics     = selectedPolitics.takeIf { it != notSelected } ?: ""
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BF63)),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.save), color = Color.White)
            }
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.cancel), color = Color.White)
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialCausesSection(profile: Profile) {
    if (profile.socialCauses.isEmpty()) {
        Text(stringResource(R.string.no_social_causes), color = Color.Gray, fontSize = 16.sp)
    } else {
        Column {
            Text(stringResource(R.string.social_causes), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                profile.socialCauses.forEach { cause ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFF6F00)
                    ) {
                        Text(
                            text = cause,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialCausesEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    val socialCauses = remember { mutableStateListOf<String>().apply { addAll(tempProfile.socialCauses) } }
    var newCause by remember { mutableStateOf("") }

    Column {
        Text(stringResource(R.string.social_causes), color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = newCause,
            onValueChange = { newCause = it },
            label = { Text(stringResource(R.string.add_cause), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color(0xFFFF6F00),
                unfocusedLabelColor = Color.White
            ),
            trailingIcon = {
                if (newCause.isNotEmpty()) {
                    IconButton(onClick = {
                        socialCauses.add(newCause)
                        newCause = ""
                    }) {
                        Icon(Icons.Default.Add, stringResource(R.string.add), tint = Color.White)
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            socialCauses.forEach { cause ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFF6F00)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = cause, color = Color.White, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { socialCauses.remove(cause) }, modifier = Modifier.size(16.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.remove), tint = Color.White)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(onClick = {
                onSave(tempProfile.copy(socialCauses = socialCauses.toList()))
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))) {
                Text(stringResource(R.string.save), color = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                Text(stringResource(R.string.cancel), color = Color.White)
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
            label = stringResource(R.string.lifestyle_smoking),
            value = localLifestyle.smoking_habit,
            nouns = listOf(stringResource(R.string.non_smoker), stringResource(R.string.rare_smoker), stringResource(R.string.social_smoker), stringResource(R.string.frequent_smoker), stringResource(R.string.heavy_smoker))
        ) { localLifestyle = localLifestyle.copy(smoking_habit = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_drinking),
            value = localLifestyle.drinking_habit,
            nouns = listOf(stringResource(R.string.non_drinker), stringResource(R.string.rare_drinker), stringResource(R.string.social_drinker), stringResource(R.string.frequent_drinker), stringResource(R.string.heavy_drinker))
        ) { localLifestyle = localLifestyle.copy(drinking_habit = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_indoor_outdoor),
            value = localLifestyle.indoor_outdoor_orientation,
            nouns = listOf(stringResource(R.string.very_indoorsy), stringResource(R.string.mostly_indoorsy), stringResource(R.string.balanced), stringResource(R.string.mostly_outdoorsy), stringResource(R.string.very_outdoorsy))
        ) { localLifestyle = localLifestyle.copy(indoor_outdoor_orientation = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_social_media),
            value = localLifestyle.social_media_engagement,
            nouns = listOf(stringResource(R.string.invisible), stringResource(R.string.watcher), stringResource(R.string.casual_viewer), stringResource(R.string.engager), stringResource(R.string.influencer))
        ) { localLifestyle = localLifestyle.copy(social_media_engagement = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_work_life_balance),
            value = localLifestyle.work_life_balance,
            nouns = listOf(stringResource(R.string.workaholic), stringResource(R.string.more_work_oriented), stringResource(R.string.balanced), stringResource(R.string.more_life_oriented), stringResource(R.string.relaxed))
        ) { localLifestyle = localLifestyle.copy(work_life_balance = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_exercise),
            value = localLifestyle.exercise_frequency,
            nouns = listOf(stringResource(R.string.inactive), stringResource(R.string.rarely_active), stringResource(R.string.moderately_active), stringResource(R.string.active), stringResource(R.string.very_active))
        ) { localLifestyle = localLifestyle.copy(exercise_frequency = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_family_oriented),
            value = localLifestyle.family_orientated,
            nouns = listOf(stringResource(R.string.independent), stringResource(R.string.slightly_family_oriented), stringResource(R.string.balanced), stringResource(R.string.more_family_oriented), stringResource(R.string.very_family_oriented))
        ) { localLifestyle = localLifestyle.copy(family_orientated = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_sleep),
            value = localLifestyle.sleep_pattern,
            nouns = listOf(stringResource(R.string.early_riser), stringResource(R.string.morning_person), stringResource(R.string.balanced), stringResource(R.string.night_owl), stringResource(R.string.late_night_enthusiast))
        ) { localLifestyle = localLifestyle.copy(sleep_pattern = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_adventurousness),
            value = localLifestyle.adventurousness,
            nouns = listOf(stringResource(R.string.cautious), stringResource(R.string.slightly_adventurous), stringResource(R.string.moderately_adventurous), stringResource(R.string.adventurous), stringResource(R.string.thrill_seeker))
        ) { localLifestyle = localLifestyle.copy(adventurousness = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_intellectual_curiosity),
            value = localLifestyle.intellectual_curiosity,
            nouns = listOf(stringResource(R.string.casual_thinker), stringResource(R.string.inquisitive), stringResource(R.string.knowledge_seeker), stringResource(R.string.intellectual), stringResource(R.string.philosopher))
        ) { localLifestyle = localLifestyle.copy(intellectual_curiosity = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_creative_expression),
            value = localLifestyle.creative_expression,
            nouns = listOf(stringResource(R.string.not_creative), stringResource(R.string.somewhat_creative), stringResource(R.string.creative), stringResource(R.string.very_creative), stringResource(R.string.artistic_genius))
        ) { localLifestyle = localLifestyle.copy(creative_expression = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_physical_fitness),
            value = localLifestyle.physical_fitness,
            nouns = listOf(stringResource(R.string.sedentary), stringResource(R.string.somewhat_fit), stringResource(R.string.fit), stringResource(R.string.athletic), stringResource(R.string.peak_fitness))
        ) { localLifestyle = localLifestyle.copy(physical_fitness = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_spirituality),
            value = localLifestyle.spirituality_mindfulness,
            nouns = listOf(stringResource(R.string.not_spiritual), stringResource(R.string.occasionally_mindful), stringResource(R.string.balanced), stringResource(R.string.spiritual), stringResource(R.string.deeply_mindful))
        ) { localLifestyle = localLifestyle.copy(spirituality_mindfulness = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_humor),
            value = localLifestyle.easy_goingness,
            nouns = listOf(stringResource(R.string.serious), stringResource(R.string.somewhat_easygoing), stringResource(R.string.balanced), stringResource(R.string.humorous), stringResource(R.string.life_of_the_party))
        ) { localLifestyle = localLifestyle.copy(easy_goingness = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_professional_ambition),
            value = localLifestyle.professional_ambition,
            nouns = listOf(stringResource(R.string.relaxed), stringResource(R.string.occasionally_driven), stringResource(R.string.balanced), stringResource(R.string.ambitious), stringResource(R.string.high_ambitious))
        ) { localLifestyle = localLifestyle.copy(professional_ambition = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_environmental_awareness),
            value = localLifestyle.environmental_awareness,
            nouns = listOf(stringResource(R.string.not_conscious), stringResource(R.string.occasionally_conscious), stringResource(R.string.balanced), stringResource(R.string.eco_friendly), stringResource(R.string.eco_champion))
        ) { localLifestyle = localLifestyle.copy(environmental_awareness = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.sports_enthusiast),
            value = localLifestyle.sports_enthusiasm,
            nouns = listOf(stringResource(R.string.non_sports), stringResource(R.string.casual_viewer), stringResource(R.string.occasional_player), stringResource(R.string.sports_enthusiast), stringResource(R.string.sports_fanatic))
        ) { localLifestyle = localLifestyle.copy(sports_enthusiasm = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_political_awareness),
            value = localLifestyle.political_awareness,
            nouns = listOf(stringResource(R.string.unaware), stringResource(R.string.occasionally_aware), stringResource(R.string.balanced), stringResource(R.string.aware), stringResource(R.string.politically_engaged))
        ) { localLifestyle = localLifestyle.copy(political_awareness = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_community_engagement),
            value = localLifestyle.community_engagement,
            nouns = listOf(stringResource(R.string.individualistic), stringResource(R.string.occasionally_involved), stringResource(R.string.balanced), stringResource(R.string.community_oriented), stringResource(R.string.community_leader))
        ) { localLifestyle = localLifestyle.copy(community_engagement = it) }
        // NEW SLIDERS ADDED:
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_sociability),
            value = localLifestyle.sociability,
            nouns = listOf(stringResource(R.string.not_introverted), stringResource(R.string.slightly_introverted), stringResource(R.string.moderately_introverted), stringResource(R.string.very_introverted), stringResource(R.string.extremely_introverted))
        ) { localLifestyle = localLifestyle.copy(sociability = it) }
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_sexual_activity),
            value = localLifestyle.sexual_activity_level,
            nouns = listOf(stringResource(R.string.inactive), stringResource(R.string.low), stringResource(R.string.moderate), stringResource(R.string.high), stringResource(R.string.very_high))
        ) { localLifestyle = localLifestyle.copy(sexual_activity_level = it) }
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

@Composable
fun ProfileCollapsibleSections(
    profile: Profile,
    profileViewModel: ProfileViewModel,
    onProfileUpdated: (Profile) -> Unit
) {
    var showBioVoice by rememberSaveable { mutableStateOf(true) }
    var editBioVoice by rememberSaveable { mutableStateOf(false) }
    var showBasic by rememberSaveable { mutableStateOf(true) }
    var showPreferences by rememberSaveable { mutableStateOf(true) }
    var showSocialCauses by rememberSaveable { mutableStateOf(true) } // New
    var showLifestyle by rememberSaveable { mutableStateOf(true) }
    var showInterests by rememberSaveable { mutableStateOf(true) }
    var editBasic by rememberSaveable { mutableStateOf(false) }
    var editPreferences by rememberSaveable { mutableStateOf(false) }
    var editSocialCauses by rememberSaveable { mutableStateOf(false) } // New
    var editLifestyle by rememberSaveable { mutableStateOf(false) }
    var editInterests by rememberSaveable { mutableStateOf(false) }
    var tempProfile by remember { mutableStateOf(profile) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        if (tempProfile.isMatrimonyMode) {
            var showMatrimony by rememberSaveable { mutableStateOf(true) }
            var editMatrimony by rememberSaveable { mutableStateOf(false) }
            CollapsibleSection(
                title = stringResource(R.string.section_matrimony_info),
                icon = Icons.Default.Cake,
                isExpanded = showMatrimony,
                onToggle = { showMatrimony = !showMatrimony },
                editMode = editMatrimony,
                onEditToggle = { editMatrimony = !editMatrimony }
            ) {
                if (editMatrimony) {
                    MatrimonyInfoEditSection(
                        tempProfile = tempProfile,
                        onSave = { updatedProfile ->
                            onProfileUpdated(updatedProfile)
                            editMatrimony = false
                        },
                        onCancel = { editMatrimony = false }
                    )
                } else {
                    MatrimonyInfoSection(profile = tempProfile)
                }
            }
        }
        PerformanceMetricsSection(profile)
        Spacer(modifier = Modifier.height(12.dp))
        CollapsibleSection(
            title       = stringResource(R.string.section_bio_and_voice),
            icon        = Icons.Default.Mic,         // or pick a merged icon
            isExpanded  = showBioVoice,
            onToggle    = { showBioVoice = !showBioVoice },
            editMode    = editBioVoice,
            onEditToggle= { editBioVoice = !editBioVoice }
        ) {
            if (editBioVoice) {
                CombinedBioVoiceEditSection(
                    currentBio         = tempProfile.bio,
                    currentVoiceUrl    = tempProfile.voiceNoteUrl,
                    profileViewModel   = profileViewModel,
                    onSave             = { newBio, newVoiceUrl ->
                        val updated = tempProfile.copy(bio = newBio, voiceNoteUrl = newVoiceUrl)
                        profileViewModel.saveProfileUpdated(
                            updated,
                            onSuccess = {
                                tempProfile = updated
                                onProfileUpdated(updated)
                                editBioVoice = false
                            }
                        )
                    },
                    onCancel = {
                        editBioVoice = false
                    }
                )
            } else {
                // display read‐only
                Text(
                    text = tempProfile.bio ?: stringResource(R.string.bio_no_bio),
                    color = Color.White, fontSize = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                if (!tempProfile.voiceNoteUrl.isNullOrEmpty()) {
                    VoicePlayer(url = tempProfile.voiceNoteUrl!!)
                } else {
                    Text(stringResource(R.string.voice_no_voice_bio), color = Color.White, fontSize = 16.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        CollapsibleSection(
            title = stringResource(R.string.section_basic_information),
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
            title = stringResource(R.string.section_preferences),
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
            title = stringResource(R.string.section_social_causes),
            icon = Icons.Default.VolunteerActivism, // New section
            isExpanded = showSocialCauses,
            onToggle = { showSocialCauses = !showSocialCauses },
            editMode = editSocialCauses,
            onEditToggle = { editSocialCauses = !editSocialCauses }
        ) {
            if (editSocialCauses) {
                SocialCausesEditSection(
                    tempProfile = tempProfile,
                    onSave = { updated ->
                        tempProfile = updated
                        onProfileUpdated(tempProfile)
                        editSocialCauses = false
                    },
                    onCancel = {
                        tempProfile = profile
                        editSocialCauses = false
                    }
                )
            } else {
                SocialCausesSection(tempProfile)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        CollapsibleSection(
            title = stringResource(R.string.section_lifestyle_attributes),
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
            title = stringResource(R.string.section_interests),
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
            label = { Text(stringResource(R.string.add_interest), color = Color(0xFFFF6F00)) },
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
            Text(stringResource(R.string.add_interest), color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(
                onClick = {
                    onSave(tempProfile.copy(interests = localInterests.toList()))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
            ) {
                Text(stringResource(R.string.save), color = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text(stringResource(R.string.cancel), color = Color.White)
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
fun LifestyleSlider(label: String, value: Int, nouns: List<String>, icon: ImageVector) {
    val displayText = if (value == -1) stringResource(R.string.not_selected) else nouns.getOrElse(value) { "Unknown" }

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
    val starSize = 20.dp
    val fullStars = kotlin.math.floor(rating).toInt()
    val fraction = rating - fullStars
    val orange = Color(0xFFFF6F00)
    val backgroundColor = Color(0xFF1A1A1A)

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

@Composable
fun RatingBar3(rating: Double, ratingCount: Int) {
    val starSize = 20.dp
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
            contentDescription = if (isPlaying)
                stringResource(R.string.pause_audio)
            else
                stringResource(R.string.tap_to_play),
            tint = Color(0xFFFFBF00),
            modifier = Modifier.padding(8.dp)
        )
        Text(
            text = if (isPlaying)
                stringResource(R.string.playing_seconds, elapsedTime)
            else
                stringResource(R.string.tap_to_play),
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
    val displayText = if (value == -1) stringResource(R.string.not_selected) else nouns.getOrElse(value) { "Unknown" }

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
            Text(stringResource(R.string.save), color = Color.White)
        }
        ElevatedButton(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Text(stringResource(R.string.cancel), color = Color.White)
        }
    }
}

suspend fun updateProfileInFirebase(updatedProfile: Profile) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserId)

    val updates = mapOf(
        "email" to updatedProfile.email,
        "name" to updatedProfile.name,
        "caste" to updatedProfile.caste,
        "bio" to updatedProfile.bio,
        "gender" to updatedProfile.gender,
        "city" to updatedProfile.city,
        "height"  to updatedProfile.height,
        "height2" to updatedProfile.height2,
        "hometown" to updatedProfile.hometown,
        "highSchool" to updatedProfile.highSchool,
        "highSchoolGraduationYear" to updatedProfile.highSchoolGraduationYear,
        "college" to updatedProfile.college,
        "collegeGraduationYear" to updatedProfile.collegeGraduationYear,
        "loveLanguage" to updatedProfile.loveLanguage, // New
        "politics" to updatedProfile.politics, // New
        "socialCauses" to updatedProfile.socialCauses,
        "caste" to updatedProfile.caste,

        // NEW: For the college degree
        "collegeDegree" to updatedProfile.collegeDegree,

        "postGraduation" to updatedProfile.postGraduation,
        "postGraduationYear" to updatedProfile.postGraduationYear,

        // NEW: For the post-grad degree
        "postGraduationDegree" to updatedProfile.postGraduationDegree,

        "community" to updatedProfile.community,
        "religion" to updatedProfile.religion,
        "lookingFor" to updatedProfile.lookingFor,
        "interests" to updatedProfile.interests.map {
            mapOf("name" to it.name, "emoji" to it.emoji)
        },
        "lifestyle" to updatedProfile.lifestyle,

        // Job & Work
        "jobRole" to updatedProfile.jobRole,
        "customJobRole" to updatedProfile.customJobRole,
        "work" to updatedProfile.work,
        "customWork" to updatedProfile.customWork,

        // Matrimony toggle + details
        "isMatrimonyMode" to updatedProfile.isMatrimonyMode,
        "marriageTimeline" to updatedProfile.marriageTimeline,
        "relocationPreference" to updatedProfile.relocationPreference,
        "postMarriageCareerPlan" to updatedProfile.postMarriageCareerPlan,
        "traditionalVsLiberal" to updatedProfile.traditionalVsLiberal,
        "fatherOccupation" to updatedProfile.fatherOccupation,
        "motherOccupation" to updatedProfile.motherOccupation,
        "isConsultantVerified" to updatedProfile.isConsultantVerified
    )

    userRef.updateChildren(updates).addOnCompleteListener { task ->
        if (!task.isSuccessful) {
            Log.e("ProfileScreen", "Failed to update profile: ${task.exception}")
        } else {
            Log.d("ProfileScreen", "Profile updated successfully!")
        }
    }
}

@Composable
fun MatrimonyInfoSection(profile: Profile) {
    // Show these fields only if they have values
    ProfileDetailRow(
        label = stringResource(R.string.marriage_timeline_label),
        value = profile.marriageTimeline,
        icon = Icons.Default.Schedule
    )
    ProfileDetailRow(
        label = stringResource(R.string.matrimony_relocation_preference),
        value = profile.relocationPreference,
        icon = Icons.Default.Map
    )
    ProfileDetailRow(
        label = stringResource(R.string.post_marriage_career_plan_label),
        value = profile.postMarriageCareerPlan,
        icon = Icons.Default.Work
    )
    ProfileDetailRow(
        label = stringResource(R.string.matrimony_traditional_vs_liberal),
        value = profile.traditionalVsLiberal,
        icon = Icons.Default.HowToVote // or some suitable icon
    )
    ProfileDetailRow(
        label = stringResource(R.string.matrimony_father_occupation),
        value = profile.fatherOccupation,
        icon = Icons.Default.Person
    )
    ProfileDetailRow(
        label = stringResource(R.string.matrimony_mother_occupation),
        value = profile.motherOccupation,
        icon = Icons.Default.Person
    )
}

@Composable
fun MatrimonyInfoEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    var marriageTimeline by remember { mutableStateOf(tempProfile.marriageTimeline ?: "") }
    var relocationPref by remember { mutableStateOf(tempProfile.relocationPreference ?: "") }
    var postMarriagePlan by remember { mutableStateOf(tempProfile.postMarriageCareerPlan ?: "") }
    var traditionalVsLiberal by remember { mutableStateOf(tempProfile.traditionalVsLiberal ?: "") }
    var fatherOccupation by remember { mutableStateOf(tempProfile.fatherOccupation ?: "") }
    var motherOccupation by remember { mutableStateOf(tempProfile.motherOccupation ?: "") }

    // No direct edit for isConsultantVerified here; you (the consultant) set it manually elsewhere.

    Column {
        OutlinedTextField(
            value = marriageTimeline,
            onValueChange = { marriageTimeline = it },
            label = { Text(stringResource(R.string.marriage_timeline_label), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = relocationPref,
            onValueChange = { relocationPref = it },
            label = { Text(stringResource(R.string.relocation_label), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = postMarriagePlan,
            onValueChange = { postMarriagePlan = it },
            label = { Text(stringResource(R.string.post_marriage_career_plan_label), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = traditionalVsLiberal,
            onValueChange = { traditionalVsLiberal = it },
            label = { Text(stringResource(R.string.matrimony_traditional_vs_liberal), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.family_information), color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = fatherOccupation,
            onValueChange = { fatherOccupation = it },
            label = { Text(stringResource(R.string.matrimony_father_occupation), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = motherOccupation,
            onValueChange = { motherOccupation = it },
            label = { Text(stringResource(R.string.matrimony_mother_occupation), color = Color(0xFFFF6F00)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF6F00),
                cursorColor = Color(0xFFFF6F00),
                focusedTextColor = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 5) Save/Cancel Buttons
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(
                onClick = {
                    val updated = tempProfile.copy(
                        marriageTimeline = marriageTimeline.ifBlank { null },
                        relocationPreference = relocationPref.ifBlank { null },
                        postMarriageCareerPlan = postMarriagePlan.ifBlank { null },
                        traditionalVsLiberal = traditionalVsLiberal.ifBlank { null },
                        fatherOccupation = fatherOccupation.ifBlank { null },
                        motherOccupation = motherOccupation.ifBlank { null },
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
            ) {
                Text(stringResource(R.string.save), color = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text(stringResource(R.string.cancel), color = Color.White)
            }
        }
    }
}