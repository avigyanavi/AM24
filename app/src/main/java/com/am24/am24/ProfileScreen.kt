@file:OptIn(
    ExperimentalMaterial3Api::class,
)
package com.am24.am24

import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
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
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
            text = stringResource(R.string.edit_voice_bio),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        IconButton(onClick = toggleRecording) {
            Icon(
                imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = stringResource(R.string.record_voice_bio),
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

@Composable
fun BasicInfoSection(profile: Profile) {
    val genderIcon = when (profile.gender.lowercase()) {
        "male" -> Icons.Default.Male
        "female" -> Icons.Default.Female
        else -> Icons.Default.Transgender
    }
    val heightString = if (profile.height2.isNotEmpty()) {
        profile.height2.joinToString(", ")
    } else {
        profile.height.toString()
    }

    ProfileDetailRow(stringResource(R.string.label_name), profile.name, Icons.Default.Person)
    ProfileDetailRow(stringResource(R.string.label_gender), profile.gender, genderIcon)
    ProfileDetailRow(stringResource(R.string.label_locality), profile.hometown, Icons.Default.LocationCity)
    ProfileDetailRow(stringResource(R.string.label_love_language), profile.loveLanguage.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_set), Icons.Default.Favorite)
    ProfileDetailRow(stringResource(R.string.label_politics), profile.politics.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_set), Icons.Default.HowToVote)
    ProfileDetailRow(stringResource(R.string.label_username), profile.username, Icons.Default.AccountCircle)
    val displayJobRole = if (!profile.customJobRole.isNullOrBlank()) profile.customJobRole else profile.jobRole
    ProfileDetailRow(stringResource(R.string.label_job_role), displayJobRole.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_set), Icons.Default.Work)
    val displayWork = if (!profile.customWork.isNullOrBlank()) profile.customWork else profile.work
    ProfileDetailRow(stringResource(R.string.label_work), displayWork.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_set), Icons.Default.Business)
    ProfileDetailRow(stringResource(R.string.label_high_school), profile.highSchool, Icons.Default.School)
    ProfileDetailRow(stringResource(R.string.label_college), profile.college, Icons.Default.AccountBalance)
    if (!profile.collegeDegree.isNullOrBlank()) {
        ProfileDetailRow(stringResource(R.string.label_college_degree), profile.collegeDegree, Icons.Default.Book)
    }
    ProfileDetailRow(stringResource(R.string.label_post_graduation), profile.postGraduation, Icons.Default.EmojiObjects)
    if (!profile.postGraduationDegree.isNullOrBlank()) {
        ProfileDetailRow(stringResource(R.string.label_post_graduation_degree), profile.postGraduationDegree, Icons.Default.School)
    }
    ProfileDetailRow(stringResource(R.string.label_community), profile.community, Icons.Default.Groups)
    ProfileDetailRow(stringResource(R.string.label_religion), profile.religion, Icons.Default.Church)
    ProfileDetailRow(stringResource(R.string.label_height), heightString, Icons.Default.Straighten)
    ProfileDetailRow(stringResource(R.string.label_date_joined), formatDate(profile.dateOfJoin), Icons.Default.DateRange)
}

@Composable
fun BasicInfoEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(tempProfile.name) }
    var gender by remember { mutableStateOf(tempProfile.gender) }
    var hometown by remember { mutableStateOf(tempProfile.hometown) }

    // Love Language dropdown logic
    val loveLanguageOptions = listOf(stringResource(R.string.love_language_option_not_selected),stringResource(R.string.love_language_option_words_of_affirmation), stringResource(R.string.love_language_option_acts_of_service), stringResource(R.string.love_language_option_receiving_gifts), stringResource(R.string.love_language_option_quality_time), stringResource(R.string.love_language_option_physical_touch), stringResource(R.string.love_language_option_other))
    var notselected = stringResource(R.string.love_language_option_not_selected)
    var other = stringResource(R.string.love_language_option_other)
    var selectedLoveLanguage by remember {
        mutableStateOf(if (loveLanguageOptions.contains(tempProfile.loveLanguage)) tempProfile.loveLanguage else notselected)
    }
    var customLoveLanguage by remember { mutableStateOf(if (selectedLoveLanguage == other) tempProfile.loveLanguage else "") }
    val showCustomLoveLanguageField = remember { mutableStateOf(selectedLoveLanguage == other) }
    var loveLanguageDropdownExpanded by remember { mutableStateOf(false) }

    // Politics dropdown logic
    val politicsOptions = listOf(stringResource(R.string.politics_option_not_selected), stringResource(R.string.politics_option_liberal), stringResource(R.string.politics_option_moderate), stringResource(R.string.politics_option_conservative), stringResource(R.string.politics_option_other))
    var selectedPolitics by remember {
        mutableStateOf(if (politicsOptions.contains(tempProfile.politics)) tempProfile.politics else notselected)
    }
    var customPolitics by remember { mutableStateOf(if (selectedPolitics == other) tempProfile.politics else "") }
    val showCustomPoliticsField = remember { mutableStateOf(selectedPolitics == other) }
    var politicsDropdownExpanded by remember { mutableStateOf(false) }

    // Job Role dropdown logic
    val jobRoleOptions = listOf(stringResource(R.string.job_role_option_not_selected), stringResource(R.string.job_role_option_engineer), stringResource(R.string.job_role_option_teacher), stringResource(R.string.job_role_option_doctor), stringResource(R.string.job_role_option_intern), stringResource(R.string.job_role_option_entrepreneur), stringResource(R.string.job_role_option_other))
    var selectedJobRole by remember { mutableStateOf(tempProfile.jobRole.ifBlank { notselected }) }
    var customJobRole by remember { mutableStateOf(tempProfile.customJobRole ?: "") }
    val showCustomJobRoleField = remember {
        mutableStateOf(selectedJobRole == other || tempProfile.customJobRole?.isNotBlank() == true)
    }
    var jobRoleDropdownExpanded by remember { mutableStateOf(false) }

    // Work dropdown logic
    val workOptions = listOf(stringResource(R.string.work_option_not_selected), stringResource(R.string.work_option_private_sector), stringResource(R.string.work_option_government), stringResource(R.string.work_option_freelance), stringResource(R.string.work_option_unemployed), stringResource(R.string.work_option_other))
    var selectedWork by remember { mutableStateOf(tempProfile.work.ifBlank { notselected }) }
    var customWork by remember { mutableStateOf(tempProfile.customWork ?: "") }
    val showCustomWorkField = remember {
        mutableStateOf(selectedWork == other || tempProfile.customWork?.isNotBlank() == true)
    }
    var workDropdownExpanded by remember { mutableStateOf(false) }

    var highSchool by remember { mutableStateOf(tempProfile.highSchool) }
    var highSchoolGradYear by remember { mutableStateOf(tempProfile.highSchoolGraduationYear) }
    var college by remember { mutableStateOf(tempProfile.college) }
    var collegeGradYear by remember { mutableStateOf(tempProfile.collegeGraduationYear) }
    var collegeDegree by remember { mutableStateOf(tempProfile.collegeDegree ?: "") }
    var postGrad by remember { mutableStateOf(tempProfile.postGraduation ?: "") }
    var postGradYear by remember { mutableStateOf(tempProfile.postGraduationYear) }
    var postGraduationDegree by remember { mutableStateOf(tempProfile.postGraduationDegree ?: "") }
    var community by remember { mutableStateOf(tempProfile.community) }
    var religion by remember { mutableStateOf(tempProfile.religion) }
    var heightInput by remember {
        mutableStateOf(
            if (tempProfile.height2.isNotEmpty()) tempProfile.height2.joinToString(", ")
            else tempProfile.height.toString()
        )
    }

    Column {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.label_name), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = gender, onValueChange = { gender = it }, label = { Text(stringResource(R.string.label_gender), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = hometown, onValueChange = { hometown = it }, label = { Text(stringResource(R.string.label_locality), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        Spacer(modifier = Modifier.height(8.dp))

        // Love Language Dropdown
        Text(stringResource(R.string.love_language_label), color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = { loveLanguageDropdownExpanded = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))) {
            Text(text = if (selectedLoveLanguage.isBlank()) stringResource(R.string.select_love_language) else selectedLoveLanguage, color = Color.White)
        }
        DropdownMenu(expanded = loveLanguageDropdownExpanded, onDismissRequest = { loveLanguageDropdownExpanded = false }) {
            loveLanguageOptions.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    selectedLoveLanguage = option
                    loveLanguageDropdownExpanded = false
                    showCustomLoveLanguageField.value = (option == other)
                })
            }
        }
        if (showCustomLoveLanguageField.value) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = customLoveLanguage, onValueChange = { customLoveLanguage = it }, label = { Text(stringResource(R.string.label_custom_love_language), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Politics Dropdown
        Text(stringResource(R.string.label_politics), color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = { politicsDropdownExpanded = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))) {
            Text(text = if (selectedPolitics.isBlank()) stringResource(R.string.select_politics) else selectedPolitics, color = Color.White)
        }
        DropdownMenu(expanded = politicsDropdownExpanded, onDismissRequest = { politicsDropdownExpanded = false }) {
            politicsOptions.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    selectedPolitics = option
                    politicsDropdownExpanded = false
                    showCustomPoliticsField.value = (option == other)
                })
            }
        }
        if (showCustomPoliticsField.value) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = customPolitics, onValueChange = { customPolitics = it }, label = { Text(stringResource(R.string.label_custom_politics), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Job Role Dropdown
        Text(stringResource(R.string.job_role_label), color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = { jobRoleDropdownExpanded = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))) {
            Text(text = if (selectedJobRole.isBlank()) stringResource(R.string.select_job_role) else selectedJobRole, color = Color.White)
        }
        DropdownMenu(expanded = jobRoleDropdownExpanded, onDismissRequest = { jobRoleDropdownExpanded = false }) {
            jobRoleOptions.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    selectedJobRole = option
                    jobRoleDropdownExpanded = false
                    showCustomJobRoleField.value = (option == other)
                })
            }
        }
        if (showCustomJobRoleField.value) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = customJobRole, onValueChange = { customJobRole = it }, label = { Text(stringResource(R.string.label_custom_job_role), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Work Dropdown
        Text(stringResource(R.string.label_work), color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = { workDropdownExpanded = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))) {
            Text(text = if (selectedWork.isBlank()) stringResource(R.string.select_work) else selectedWork, color = Color.White)
        }
        DropdownMenu(expanded = workDropdownExpanded, onDismissRequest = { workDropdownExpanded = false }) {
            workOptions.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    selectedWork = option
                    workDropdownExpanded = false
                    showCustomWorkField.value = (option == other)
                })
            }
        }
        if (showCustomWorkField.value) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = customWork, onValueChange = { customWork = it }, label = { Text(stringResource(R.string.label_custom_work), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(value = highSchool, onValueChange = { highSchool = it }, label = { Text(stringResource(R.string.label_high_school), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        if (highSchool.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = highSchoolGradYear, onValueChange = { highSchoolGradYear = it }, label = { Text(stringResource(R.string.high_school_graduation_year), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = college, onValueChange = { college = it }, label = { Text(stringResource(R.string.college_label), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        if (college.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = collegeGradYear, onValueChange = { collegeGradYear = it }, label = { Text(stringResource(R.string.college_graduation_year), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = collegeDegree, onValueChange = { collegeDegree = it }, label = { Text(stringResource(R.string.label_college_degree), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = postGrad, onValueChange = { postGrad = it }, label = { Text(stringResource(R.string.post_graduation_label), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        if (postGrad.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = postGradYear ?: "", onValueChange = { postGradYear = it }, label = { Text(stringResource(R.string.select_graduation_year_label), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = postGraduationDegree, onValueChange = { postGraduationDegree = it }, label = { Text(stringResource(R.string.label_post_graduation_degree), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = community, onValueChange = { community = it }, label = { Text(stringResource(R.string.community_label), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = religion, onValueChange = { religion = it }, label = { Text(stringResource(R.string.religion_label), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = heightInput, onValueChange = { heightInput = it }, label = { Text(stringResource(R.string.height_label), color = Color(0xFFFF6F00)) }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFFFF6F00), cursorColor = Color(0xFFFF6F00), focusedTextColor = Color.White))
        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = {
                val finalLoveLanguage = if (selectedLoveLanguage == other) customLoveLanguage else selectedLoveLanguage
                val finalPolitics = if (selectedPolitics == other) customPolitics else selectedPolitics
                val (finalHeight, finalHeight2) = if (heightInput.contains(",")) {
                    try {
                        0 to heightInput.split(",").mapNotNull { it.trim().toIntOrNull() }
                    } catch (e: Exception) {
                        tempProfile.height to tempProfile.height2
                    }
                } else {
                    try {
                        heightInput.toInt() to emptyList()
                    } catch (e: Exception) {
                        tempProfile.height to tempProfile.height2
                    }
                }
                onSave(
                    tempProfile.copy(
                        name = name,
                        gender = gender,
                        hometown = hometown,
                        loveLanguage = if (finalLoveLanguage == notselected) "" else finalLoveLanguage,
                        politics = if (finalPolitics == notselected) "" else finalPolitics,
                        jobRole = if (selectedJobRole == other) "" else if (selectedJobRole == notselected) "" else selectedJobRole,
                        customJobRole = if (selectedJobRole == other) customJobRole else "",
                        work = if (selectedWork == other) "" else if (selectedWork == notselected) "" else selectedWork,
                        customWork = if (selectedWork == other) customWork else "",
                        highSchool = highSchool,
                        highSchoolGraduationYear = highSchoolGradYear,
                        college = college,
                        collegeGraduationYear = collegeGradYear,
                        collegeDegree = collegeDegree.ifBlank { null },
                        postGraduation = postGrad.ifBlank { null },
                        postGraduationYear = postGradYear ?: "",
                        postGraduationDegree = postGraduationDegree.ifBlank { null },
                        community = community,
                        religion = religion,
                        height = finalHeight,
                        height2 = finalHeight2
                    )
                )
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
    if (profile.interests.isEmpty()) {
        Text(stringResource(R.string.no_interests), color = Color.Gray, fontSize = 16.sp)
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
            label = { Text(stringResource(R.string.bio), color = Color(0xFFFF6F00)) },
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

/** Preferences Edit */
@Composable
fun PreferencesEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    var notselected = stringResource(R.string.not_selected)
    val lookingForOptions = listOf(stringResource(R.string.looking_for_not_selected), stringResource(R.string.looking_for_casual_sex), stringResource(R.string.looking_for_connection), stringResource(R.string.looking_for_partner), stringResource(R.string.looking_for_marriage))
    var selectedLookingFor by remember { mutableStateOf(tempProfile.lookingFor.ifBlank { notselected }) }
    var lookingForExpanded by remember { mutableStateOf(false) }

    Column {
        Text(stringResource(R.string.looking_for_label), color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = { lookingForExpanded = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))) {
            Text(text = if (selectedLookingFor.isBlank()) stringResource(R.string.select_looking_for) else selectedLookingFor, color = Color.White)
        }
        DropdownMenu(expanded = lookingForExpanded, onDismissRequest = { lookingForExpanded = false }) {
            lookingForOptions.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    selectedLookingFor = option
                    lookingForExpanded = false
                })
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = {
                onSave(tempProfile.copy(lookingFor = if (selectedLookingFor == notselected) "" else selectedLookingFor))
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
    var showPerformance by rememberSaveable { mutableStateOf(true) }
    var showMatrimony by rememberSaveable { mutableStateOf(true) }
    var showBio by rememberSaveable { mutableStateOf(true) }
    var showVoiceBio by rememberSaveable { mutableStateOf(true) }
    var showBasic by rememberSaveable { mutableStateOf(true) }
    var showPreferences by rememberSaveable { mutableStateOf(true) }
    var showSocialCauses by rememberSaveable { mutableStateOf(true) } // New
    var showLifestyle by rememberSaveable { mutableStateOf(true) }
    var showInterests by rememberSaveable { mutableStateOf(true) }
    var editBio by rememberSaveable { mutableStateOf(false) }
    var editVoiceBio by rememberSaveable { mutableStateOf(false) }
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
            title = stringResource(R.string.section_bio),
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
                        tempProfile = tempProfile.copy(bio = updatedBio)
                        onProfileUpdated(tempProfile)
                        editBio = false
                    },
                    onCancel = { editBio = false }
                )
            } else {
                Text(
                    text = profile.bio ?: stringResource(R.string.bio_no_bio),
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        CollapsibleSection(
            title = stringResource(R.string.section_voice_bio),
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
        "bio" to updatedProfile.bio,
        "gender" to updatedProfile.gender,
        "hometown" to updatedProfile.hometown,
        "highSchool" to updatedProfile.highSchool,
        "highSchoolGraduationYear" to updatedProfile.highSchoolGraduationYear,
        "college" to updatedProfile.college,
        "collegeGraduationYear" to updatedProfile.collegeGraduationYear,
        "loveLanguage" to updatedProfile.loveLanguage, // New
        "politics" to updatedProfile.politics, // New
        "socialCauses" to updatedProfile.socialCauses,

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