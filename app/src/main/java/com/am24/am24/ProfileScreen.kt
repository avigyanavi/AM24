@file:OptIn(
    ExperimentalMaterial3Api::class,
)
package com.am24.am24

import android.Manifest
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
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
import coil.request.ImageRequest
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import com.am24.am24.util.CachedFullscreenVideoPlayer
import kotlinx.coroutines.tasks.await

@Composable
fun ProfileScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel,
    postViewModel: PostViewModel,
    modifier: Modifier = Modifier
) {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

// ── 1) replace the existing `needsVerification` val with a mutable state ─────────
    val user                 = FirebaseAuth.getInstance().currentUser
    val isPwdUser            = user?.providerData?.any { it.providerId == "password" } == true
    var needsVerification by remember {         // ← make it mutable
        mutableStateOf(isPwdUser && user?.isEmailVerified == false)
    }

// ── 2) keep the rest of your state ----------------------------------------------
    var showVerifyDialog by remember { mutableStateOf(false) }
    var isSendingEmail   by remember { mutableStateOf(false) }

    // —— your existing state & loading logic ——
    val filtersLoaded by postViewModel.filtersLoaded.collectAsState()
    val currentUserProfile by profileViewModel.currentUserProfile.collectAsState()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    // Fetch profile on screen entry
    LaunchedEffect(currentUserId) {
        Log.d("ProfileScreen", "Fetching profile for userId: $currentUserId")
        profileViewModel.fetchCurrentUserProfile()
        profileViewModel.observeVerificationStatus(currentUserId)
    }

    val verStatus by profileViewModel.verificationStatus.collectAsState()
    val isVerified = verStatus == "accepted"

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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.profile_loading), color = Color.White)
            }
        }
        else -> {
            ProfileLazyScreen(
                navController    = navController,
                profile          = currentUserProfile!!,
                featuredPosts = featuredPosts,
                remainingPosts = remainingPosts,
                profileViewModel = profileViewModel,
                isVerified       = isVerified,
                onVerifyClick    = { navController.navigate("govtIdVerification") }
            )

            // —— 3) if they need to verify, intercept all taps ——
            // ── 3) intercept taps only while we still need verification ---------------------
            if (needsVerification) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { showVerifyDialog = true } }
                )
            }

            // ── 4) revamped AlertDialog ------------------------------------------------------
            if (showVerifyDialog) {
                AlertDialog(
                    onDismissRequest = { showVerifyDialog = false },
                    backgroundColor  = Color(0xFF1A1A1A),
                    contentColor     = Color.White,
                    title  = { Text("Verify Email", fontWeight = FontWeight.Bold) },
                    text   = { Text("Please verify your email address to use the app.") },

                    /** ------------- BUTTON ROW ------------- **/
                    buttons = {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {

                            // a) “Resend link”
                            TextButton(
                                onClick = {
                                    isSendingEmail = true
                                    coroutineScope.launch {
                                        try {
                                            user?.sendEmailVerification()?.await()
                                            Toast.makeText(
                                                context,
                                                "Verification email sent!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                e.message ?: "Error sending email",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        isSendingEmail = false
                                    }
                                },
                                enabled = !isSendingEmail
                            ) {
                                if (isSendingEmail) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Resend link")
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            // b) “I’ve verified”  ← NEW
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            // ① wait for the network call to finish
                                            user?.reload()?.await()      // <-- suspend until done  ✅

                                            // ② THEN read the fresh auth object
                                            val refreshedUser = FirebaseAuth.getInstance().currentUser
                                            if (refreshedUser?.isEmailVerified == true) {
                                                Toast.makeText(
                                                    context,
                                                    "Email verified – enjoy the app!",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                needsVerification = false
                                                showVerifyDialog  = false
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Still not verified — please confirm the link first.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                e.message ?: "Error checking verification",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            ) { Text("I’ve verified") }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileLazyScreen(
    navController: NavController,
    profile: Profile,
    featuredPosts: List<Post>,
    remainingPosts: List<Post>,
    profileViewModel: ProfileViewModel,
    isVerified: Boolean,                 // ← new
    onVerifyClick: () -> Unit           // ← new
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showPostsOverlay by remember { mutableStateOf(false) }
    val currentUserProfile by profileViewModel.currentUserProfile.collectAsState()

    // Use ViewModel's profile if available, otherwise fall back to initial profile
    var currentProfile by remember { mutableStateOf(profile) }

    val leaderboardVm: LeaderboardViewModel = viewModel()
    val allProfiles by leaderboardVm.allProfiles.collectAsState()
    // whenever our currentProfile changes, compute all the ranks
    var displayProfile by remember { mutableStateOf(profile) }
    // Sync with ViewModel's profile
    LaunchedEffect(currentUserProfile) {
        currentUserProfile?.let { updatedProfile ->
            Log.d("ProfileLazyScreen", "Updating currentProfile with isMatrimonyMode: ${updatedProfile.isMatrimonyMode}")
            currentProfile = updatedProfile
        }
    }
    LaunchedEffect(profile, allProfiles) {
        // 1) global composite rank
        val sortedByComposite = allProfiles.sortedByDescending { it.compositeScore }
        val globalIdx = sortedByComposite.indexOfFirst { it.userId == profile.userId }
        val globalRank = if (globalIdx >= 0) globalIdx + 1 else -1

        // 2) city-level rank
        val cityList = allProfiles
            .filter {
                it.city.equals(profile.city, ignoreCase = true) ||
                        it.customCity.equals(profile.city, ignoreCase = true)
            }
            .sortedByDescending { it.compositeScore }
        val cityIdx = cityList.indexOfFirst { it.userId == profile.userId }
        val cityRank = if (cityIdx >= 0) cityIdx + 1 else -1

        // 3) custom-city (if city=="Other")
        val customCityList = allProfiles
            .filter { it.customCity.equals(profile.customCity, ignoreCase = true) }
            .sortedByDescending { it.compositeScore }
        val customCityIdx = customCityList.indexOfFirst { it.userId == profile.userId }
        val customCityRank = if (customCityIdx >= 0) customCityIdx + 1 else -1

        // 4) hometown/locality
        val hoodList = allProfiles
            .filter {
                it.hometown.equals(profile.hometown, ignoreCase = true) ||
                        it.customHometown.equals(profile.hometown, ignoreCase = true)
            }
            .sortedByDescending { it.compositeScore }
        val hoodIdx = hoodList.indexOfFirst { it.userId == profile.userId }
        val hoodRank = if (hoodIdx >= 0) hoodIdx + 1 else -1

        // 5) custom-hood
        val customHoodList = allProfiles
            .filter { it.customHometown.equals(profile.customHometown, ignoreCase = true) }
            .sortedByDescending { it.compositeScore }
        val customHoodIdx = customHoodList.indexOfFirst { it.userId == profile.userId }
        val customHoodRank = if (customHoodIdx >= 0) customHoodIdx + 1 else -1

        // 6) age-ranking (youngest=1? or oldest=1? adjust comparator if you want)
        val ageList = allProfiles.sortedBy { it.age }
        val ageIdx = ageList.indexOfFirst { it.userId == profile.userId }
        val ageRank = if (ageIdx >= 0) ageIdx + 1 else -1

        // 7) high-school
        val hsList = allProfiles
            .filter {
                it.highSchool.equals(profile.highSchool, ignoreCase = true) ||
                        it.customHighSchool.equals(profile.highSchool, ignoreCase = true)
            }
            .sortedByDescending { it.compositeScore }
        val hsIdx = hsList.indexOfFirst { it.userId == profile.userId }
        val hsRank = if (hsIdx >= 0) hsIdx + 1 else -1

        // 8) college
        val collList = allProfiles
            .filter {
                it.college.equals(profile.college, ignoreCase = true) ||
                        it.customCollege.equals(profile.college, ignoreCase = true)
            }
            .sortedByDescending { it.compositeScore }
        val collIdx = collList.indexOfFirst { it.userId == profile.userId }
        val collRank = if (collIdx >= 0) collIdx + 1 else -1

        displayProfile = profile.copy(
            am24Ranking                = globalRank,
            am24RankingCity            = cityRank,
            am24RankingCustomCity      = customCityRank,
            am24RankingHometown        = hoodRank,
            am24RankingCustomHometown  = customHoodRank,
            am24RankingAge             = ageRank,
            am24RankingHighSchool      = hsRank,
            am24RankingCollege         = collRank
        )
    }

    currentProfile = displayProfile

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
                    verified            = isVerified,      // ← changed
                    onEditProfileClick = { navController.navigate("editPicAndVoiceBio") },
                    onPostsClick = { showPostsOverlay = true },
                    onVerifyClick       = onVerifyClick    // ← new
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
private fun localizedLocality(city: String, storedLocality: String): String {
    // pick the correct array for this city
    val arrayRes = when (localizedCity(city)) {
        stringResource(R.string.city_kolkata)     -> R.array.localities_kolkata
        stringResource(R.string.city_howrah)      -> R.array.localities_howrah
        stringResource(R.string.city_durgapur)    -> R.array.localities_durgapur
        stringResource(R.string.city_asansol)     -> R.array.localities_asansol
        stringResource(R.string.city_siliguri)    -> R.array.localities_siliguri
        stringResource(R.string.city_darjeeling)  -> R.array.localities_darjeeling
        stringResource(R.string.city_malda)       -> R.array.localities_malda
        stringResource(R.string.city_jalpaiguri)  -> R.array.localities_jalpaiguri
        stringResource(R.string.city_cooch_behar) -> R.array.localities_cooch_behar
        stringResource(R.string.city_alipurduar)  -> R.array.localities_alipurduar
        stringResource(R.string.city_bankura)     -> R.array.localities_bankura
        stringResource(R.string.city_purulia)     -> R.array.localities_purulia
        stringResource(R.string.city_kharagpur)   -> R.array.localities_kharagpur
        stringResource(R.string.city_midnapore)   -> R.array.localities_midnapore
        stringResource(R.string.city_bardhaman)   -> R.array.localities_bardhaman
        stringResource(R.string.city_hooghly)     -> R.array.localities_hooghly
        stringResource(R.string.city_murshidabad) -> R.array.localities_murshidabad
        stringResource(R.string.city_baharampur)  -> R.array.localities_baharampur
        stringResource(R.string.city_haldia)      -> R.array.localities_haldia
        stringResource(R.string.city_ranaghat)    -> R.array.localities_ranaghat
        stringResource(R.string.city_kalyani)     -> R.array.localities_kalyani
        stringResource(R.string.city_chandannagar)-> R.array.localities_chandannagar
        // … add any remaining ones here …
        else                                     -> R.array.localities_other
    }
    // load that array and see if our stored value still exists
    val allLocs = stringArrayResource(id = arrayRes)
    return allLocs.firstOrNull { it == storedLocality } ?: storedLocality
}

@Composable
private fun localizedCaste(raw: String): String =
    casteNameToRes[raw]?.let { stringResource(it) } ?: raw

@Composable
private fun localizedGender(raw: String): String =
    genderNameToRes[raw]?.let { stringResource(it) } ?: raw

@Composable
private fun localizedCommunity(raw: String): String =
    communityNameToRes[raw]?.let { stringResource(it) } ?: raw

@Composable
private fun localizedReligion(raw: String): String =
    religionNameToRes[raw]?.let { stringResource(it) } ?: raw

@Composable
private fun localizedCity(raw: String): String =
    cityNameToRes[raw]?.let { stringResource(it) } ?: raw

@Composable
private fun localizedLookingFor(raw: String): String =
    lookingForNameToRes[raw]?.let { stringResource(it) } ?: raw

@Composable
private fun localizedLoveLanguage(raw: String): String =
    loveLanguageNameToRes[raw]?.let { stringResource(it) } ?: raw

@Composable
private fun localizedPolitics(raw: String): String =
    politicsNameToRes[raw]?.let { stringResource(it) } ?: raw

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
            fontSize = 9.sp,
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
    verified: Boolean,                   // ← new
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
                    verified = verified,              // ← use the new prop
                    onClick  = onVerifyClick,
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1) a fixed-height, clipped box that scrolls its content
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(IntrinsicSize.Min)
                            .clipToBounds()
                    ) {
                        val scroll = rememberScrollState()
                        // 2) when the name changes, continuously animate back-and-forth
                        LaunchedEffect(profile.name) {
                            // tiny pause before you start
                            delay(800)
                            while (true) {
                                scroll.animateScrollTo(scroll.maxValue)
                                delay(800)
                                scroll.animateScrollTo(0)
                                delay(800)
                            }
                        }
                        // 3) your name/age text, but horizontally scrollable
                        Text(
                            text = if (age > 0) "${profile.username}, $age" else profile.username,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White,
                            modifier = Modifier
                                .horizontalScroll(scroll)
                        )
                    }

                    // 4) your Posts button remains static
                    Button(
                        onClick = onPostsClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00)),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(text = stringResource(R.string.posts_button), color = Color.White, fontSize = 10.sp)
                    }
                }

                // Hometown
                if (profile.hometown.isNotBlank()) {
                    Text(text = stringResource(R.string.from_hometown, profile.hometown), fontSize = 10.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Rating Bar + zodiac side by side
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingBar(rating = profile.averageRating, ratingCount = profile.numberOfRatings)
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
                                fontSize = 10.sp,
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
            fontSize = 10.sp,
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
            fontSize = 10.sp
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
    val ctx = LocalContext.current
    val heightString = if (profile.height2.size == 2)
        "${profile.height2[0]} ft ${profile.height2[1]} in"
    else
        "${profile.height} cm"

    // --- Name, caste, gender, city, locality, username, job, work ---
    ProfileDetailRow(stringResource(R.string.label_username),
        profile.username,
        Icons.Default.AccountCircle)

    ProfileDetailRow(stringResource(R.string.label_height),
        heightString,
        Icons.Default.Straighten)

    ProfileDetailRow(
        stringResource(R.string.caste),
        localizedCaste(profile.caste ?: ""),
        Icons.Default.Groups)

    ProfileDetailRow(
        stringResource(R.string.label_gender),
        localizedGender(profile.gender),
        genderIcon)

    // --- Community, religion, height, date joined ---
    ProfileDetailRow(
        stringResource(R.string.label_community),
        localizedCommunity(profile.community),
        Icons.Default.Groups)

    ProfileDetailRow(
        stringResource(R.string.label_religion),
        localizedReligion(profile.religion),
        Icons.Default.Church)

    ProfileDetailRow(
        stringResource(R.string.city_label),
        localizedCity(profile.city).ifBlank { stringResource(R.string.not_set) },
        Icons.Default.LocationCity)

    ProfileDetailRow(
        stringResource(R.string.label_locality),
        localizedLocality(profile.city, profile.hometown),
        Icons.Default.LocationCity)

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

    ProfileDetailRow(
        stringResource(R.string.label_date_joined),
        formatJoinedOn(ctx, profile.dateOfJoin),
        Icons.Default.DateRange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicInfoEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    // ─── State for all fields ───────────────────────────
    var name               by remember { mutableStateOf(tempProfile.name) }
    var city               by remember { mutableStateOf(tempProfile.city) }
    var cityExpanded       by remember { mutableStateOf(false) }
    var locality           by remember { mutableStateOf(tempProfile.hometown) }
    var localityExpanded   by remember { mutableStateOf(false) }
    var customCity by remember { mutableStateOf(tempProfile.customCity.orEmpty()) }
    var customLocality by remember { mutableStateOf(tempProfile.customHometown.orEmpty()) }
    var customHighSchool by remember { mutableStateOf(tempProfile.customHighSchool.orEmpty()) }
    var customCollege by remember { mutableStateOf(tempProfile.customCollege.orEmpty()) }
    var customPostGrad by remember { mutableStateOf(tempProfile.postGraduation.orEmpty()) }

    // ─── High-School dropdown + year ────────────────────
    val highSchoolOptions     = listOf(
        stringResource(R.string.high_school_andrews_high_school),
        stringResource(R.string.high_school_assembly_of_god_church_school),
        stringResource(R.string.high_school_bdm_international),
        stringResource(R.string.high_school_ballygunge_government_high_school),
        stringResource(R.string.high_school_baranagar_ramakrishna_mission),
        stringResource(R.string.high_school_barasat_mgm_high_school),
        stringResource(R.string.high_school_barasat_peary_charan),
        stringResource(R.string.high_school_barrackpore_government_high_school),
        stringResource(R.string.high_school_bethune_collegiate),
        stringResource(R.string.high_school_bidhannagar_government_high_school),
        stringResource(R.string.high_school_birla_high_school),
        stringResource(R.string.high_school_burdwan_cms_high_school),
        stringResource(R.string.high_school_calcutta_boys_school),
        stringResource(R.string.high_school_calcutta_girls_high_school),
        stringResource(R.string.high_school_darjeeling_government_high_school),
        stringResource(R.string.high_school_dps_durgapur),
        stringResource(R.string.high_school_dps_newtown),
        stringResource(R.string.high_school_dps_ruby_park),
        stringResource(R.string.high_school_don_bosco_park_circus),
        stringResource(R.string.high_school_goethals_memorial),
        stringResource(R.string.high_school_hare_school),
        stringResource(R.string.high_school_hindu_school),
        stringResource(R.string.high_school_howrah_zilla_school),
        stringResource(R.string.high_school_jadavpur_vidyapith),
        stringResource(R.string.high_school_jenkins_school),
        stringResource(R.string.high_school_kalyani_university_experimental),
        stringResource(R.string.high_school_kendriya_vidyalaya_ballygunge),
        stringResource(R.string.high_school_la_martiniere_boys),
        stringResource(R.string.high_school_la_martiniere_girls),
        stringResource(R.string.high_school_loreto_house),
        stringResource(R.string.high_school_mahadevi_birla_world_academy),
        stringResource(R.string.high_school_mitra_institution_main),
        stringResource(R.string.high_school_modern_high_school_girls),
        stringResource(R.string.high_school_nava_nalanda_high_school),
        stringResource(R.string.high_school_north_point_darjeeling),
        stringResource(R.string.high_school_patha_bhavan),
        stringResource(R.string.high_school_purwanchal_vidya_mandir),
        stringResource(R.string.high_school_rahara_ramakrishna_mission),
        stringResource(R.string.high_school_ramakrishna_mission_narendrapur),
        stringResource(R.string.high_school_rani_birla_girls_school),
        stringResource(R.string.high_school_sakhawat_memorial_girls),
        stringResource(R.string.high_school_scottish_church_collegiate),
        stringResource(R.string.high_school_siliguri_boys_high_school),
        stringResource(R.string.high_school_south_point_high_school),
        stringResource(R.string.high_school_st_james_school),
        stringResource(R.string.high_school_st_josephs_north_point),
        stringResource(R.string.high_school_st_lawrence_high_school),
        stringResource(R.string.high_school_st_pauls_mission_school),
        stringResource(R.string.high_school_st_thomas_kidderpore),
        stringResource(R.string.high_school_st_xaviers_collegiate),
        stringResource(R.string.high_school_the_heritage_school),
        stringResource(R.string.high_school_uttarpara_government_high_school),
        stringResource(R.string.high_school_asansol_st_anthonys),
        stringResource(R.string.high_school_bankura_christian_school),
        stringResource(R.string.high_school_berhampore_girls_high_school),
        stringResource(R.string.high_school_contai_high_school),
        stringResource(R.string.high_school_hooghly_collegiate_school),
        stringResource(R.string.high_school_krishnanagar_collegiate_school),
        stringResource(R.string.high_school_malda_zilla_school),
        stringResource(R.string.high_school_midnapore_collegiate_school),
        stringResource(R.string.high_school_ashok_hall),
        stringResource(R.string.high_school_mahadevi_birla_shishu_vihar),
        stringResource(R.string.high_school_jewish_girls),
        stringResource(R.string.high_school_cathedral_john_connon),
        stringResource(R.string.high_school_dhirubhai_ambani),
        stringResource(R.string.high_school_doon_school),
        stringResource(R.string.high_school_mayo_college),
        stringResource(R.string.high_school_modern_school_barakhamba),
        stringResource(R.string.high_school_rishi_valley),
        stringResource(R.string.high_school_scindia_school),
        stringResource(R.string.high_school_shri_ram_vasant_vihar),
        stringResource(R.string.high_school_lawrence_sanawar),
        stringResource(R.string.high_school_welham_girls),
        stringResource(R.string.high_school_other)
    )
    var highSchool            by remember { mutableStateOf(tempProfile.highSchool) }
    var highSchoolExpanded    by remember { mutableStateOf(false) }
    var highSchoolYear        by remember { mutableStateOf(tempProfile.highSchoolGraduationYear) }

    // ─── College dropdown + year + degree w/ limit ─────
    val collegeOptions        = listOf(
        stringResource(R.string.college_acharya_jagadish_chandra_bose_college),
        stringResource(R.string.college_asutosh_college),
        stringResource(R.string.college_bangabasi_college),
        stringResource(R.string.college_barasat_government_college),
        stringResource(R.string.college_barrackpore_rastraguru_surendranath_college),
        stringResource(R.string.college_behala_college),
        stringResource(R.string.college_bethune_college),
        stringResource(R.string.college_bidhannagar_college),
        stringResource(R.string.college_city_college),
        stringResource(R.string.college_derozio_memorial_college),
        stringResource(R.string.college_dinabandhu_andrews_college),
        stringResource(R.string.college_dum_dum_motijheel_college),
        stringResource(R.string.college_goenka_college),
        stringResource(R.string.college_heramba_chandra_college),
        stringResource(R.string.college_hooghly_mohsin_college),
        stringResource(R.string.college_iit_kharagpur),
        stringResource(R.string.college_iem_kolkata),
        stringResource(R.string.college_jadavpur_university),
        stringResource(R.string.college_jogamaya_devi_college),
        stringResource(R.string.college_kalyani_mahavidyalaya),
        stringResource(R.string.college_kazi_nazrul_islam_mahavidyalaya),
        stringResource(R.string.college_krishnanagar_government_college),
        stringResource(R.string.college_lady_brabourne_college),
        stringResource(R.string.college_loreto_college),
        stringResource(R.string.college_maulana_azad_college),
        stringResource(R.string.college_nit_durgapur),
        stringResource(R.string.college_presidency_university),
        stringResource(R.string.college_ramakrishna_mission_narendrapur),
        stringResource(R.string.college_ramakrishna_mission_vidyamandira),
        stringResource(R.string.college_rishi_bankim_chandra_college),
        stringResource(R.string.college_techno_india),
        stringResource(R.string.college_scottish_church_college),
        stringResource(R.string.college_serampore_college),
        stringResource(R.string.college_seth_anandram_jaipuria_college),
        stringResource(R.string.college_shri_shikshayatan_college),
        stringResource(R.string.college_siliguri_college),
        stringResource(R.string.college_southfield_college),
        stringResource(R.string.college_st_xaviers_college),
        stringResource(R.string.college_surendranath_college),
        stringResource(R.string.college_university_of_calcutta),
        stringResource(R.string.college_vidyasagar_college),
        stringResource(R.string.college_west_bengal_state_university),
        stringResource(R.string.college_basanti_devi_college),
        stringResource(R.string.college_gokhale_memorial_girls_college),
        stringResource(R.string.college_gurudas_college),
        stringResource(R.string.college_narasinha_dutt_college),
        stringResource(R.string.college_sivanath_sastri_college),
        stringResource(R.string.college_christ_university),
        stringResource(R.string.college_fergusson_college),
        stringResource(R.string.college_hindu_college),
        stringResource(R.string.college_iisc_bangalore),
        stringResource(R.string.college_iit_kanpur),
        stringResource(R.string.college_iit_roorkee),
        stringResource(R.string.college_lady_shri_ram_college),
        stringResource(R.string.college_loyola_college),
        stringResource(R.string.college_miranda_house),
        stringResource(R.string.college_st_stephens_college),
        stringResource(R.string.college_hansraj_college),
        stringResource(R.string.college_mount_carmel_college),
        stringResource(R.string.college_australian_national_university),
        stringResource(R.string.college_carnegie_mellon_university),
        stringResource(R.string.college_eth_zurich),
        stringResource(R.string.college_harvard_university),
        stringResource(R.string.college_imperial_college_london),
        stringResource(R.string.college_london_school_of_economics),
        stringResource(R.string.college_mcgill_university),
        stringResource(R.string.college_mit),
        stringResource(R.string.college_national_university_singapore),
        stringResource(R.string.college_purdue_university),
        stringResource(R.string.college_sorbonne_university),
        stringResource(R.string.college_stanford_university),
        stringResource(R.string.college_tu_delft),
        stringResource(R.string.college_university_college_london),
        stringResource(R.string.college_university_of_amsterdam),
        stringResource(R.string.college_university_of_british_columbia),
        stringResource(R.string.college_university_of_california_berkeley),
        stringResource(R.string.college_university_of_california_san_diego),
        stringResource(R.string.college_university_of_cambridge),
        stringResource(R.string.college_university_of_edinburgh),
        stringResource(R.string.college_university_of_melbourne),
        stringResource(R.string.college_university_of_michigan),
        stringResource(R.string.college_university_of_oxford),
        stringResource(R.string.college_university_of_queensland),
        stringResource(R.string.college_university_of_sydney),
        stringResource(R.string.college_university_of_toronto),
        stringResource(R.string.college_other)
    )
    var college               by remember { mutableStateOf(tempProfile.college) }
    var collegeExpanded       by remember { mutableStateOf(false) }
    var collegeYear           by remember { mutableStateOf(tempProfile.collegeGraduationYear) }
    var collegeDegree         by remember { mutableStateOf(tempProfile.collegeDegree.orEmpty()) }

    // ─── Post-Grad dropdown + year + degree w/ limit ────
    val postGradOptions       = listOf(
        stringResource(R.string.postgrad_adamas_university),
        stringResource(R.string.postgrad_aliah_university),
        stringResource(R.string.postgrad_amity_university_kolkata),
        stringResource(R.string.postgrad_bankura_university),
        stringResource(R.string.postgrad_bidhan_chandra_krishi_viswavidyalaya),
        stringResource(R.string.postgrad_brainware_university),
        stringResource(R.string.postgrad_cooch_behar_panchanan_barma_university),
        stringResource(R.string.postgrad_darjeeling_hills_university),
        stringResource(R.string.postgrad_diamond_harbour_womens_university),
        stringResource(R.string.postgrad_iacs),
        stringResource(R.string.postgrad_jadavpur_university),
        stringResource(R.string.postgrad_jis_university),
        stringResource(R.string.postgrad_kazi_nazrul_university),
        stringResource(R.string.postgrad_maulana_abul_kalam_azad_university_of_technology),
        stringResource(R.string.postgrad_netaji_subhash_open_university),
        stringResource(R.string.postgrad_north_bengal_university),
        stringResource(R.string.postgrad_presidency_university),
        stringResource(R.string.postgrad_rabindra_bharati_university),
        stringResource(R.string.postgrad_raiganj_university),
        stringResource(R.string.postgrad_ramakrishna_mission_vivekananda),
        stringResource(R.string.postgrad_techno_india),
        stringResource(R.string.postgrad_seacom_skills_university),
        stringResource(R.string.postgrad_sidho_kanho_birsha_university),
        stringResource(R.string.postgrad_sister_nivedita_university),
        stringResource(R.string.postgrad_university_of_burdwan),
        stringResource(R.string.postgrad_university_of_calcutta),
        stringResource(R.string.postgrad_university_of_engineering_and_management),
        stringResource(R.string.postgrad_university_of_kalyani),
        stringResource(R.string.postgrad_uttar_banga_krishi_vishwavidyalaya),
        stringResource(R.string.postgrad_vidyasagar_university),
        stringResource(R.string.postgrad_visva_bharati_university),
        stringResource(R.string.postgrad_west_bengal_state_university),
        stringResource(R.string.postgrad_west_bengal_university_of_animal_and_fishery_sciences),
        stringResource(R.string.postgrad_west_bengal_university_of_health_sciences),
        stringResource(R.string.postgrad_west_bengal_university_of_teachers_training),
        stringResource(R.string.postgrad_iit_bombay),
        stringResource(R.string.postgrad_iit_delhi),
        stringResource(R.string.postgrad_iit_kanpur),
        stringResource(R.string.postgrad_iit_kharagpur),
        stringResource(R.string.postgrad_iit_madras),
        stringResource(R.string.postgrad_iim_ahmedabad),
        stringResource(R.string.postgrad_iim_bangalore),
        stringResource(R.string.postgrad_iim_calcutta),
        stringResource(R.string.postgrad_iisc_bangalore),
        stringResource(R.string.postgrad_jnu),
        stringResource(R.string.postgrad_university_of_delhi),
        stringResource(R.string.postgrad_harvard_university),
        stringResource(R.string.postgrad_stanford_university),
        stringResource(R.string.postgrad_mit),
        stringResource(R.string.postgrad_ucsd),
        stringResource(R.string.postgrad_purdue_university),
        stringResource(R.string.postgrad_uc_berkeley),
        stringResource(R.string.postgrad_university_of_michigan),
        stringResource(R.string.postgrad_university_of_oxford),
        stringResource(R.string.postgrad_university_of_cambridge),
        stringResource(R.string.postgrad_imperial_college_london),
        stringResource(R.string.postgrad_london_school_of_economics),
        stringResource(R.string.postgrad_university_of_toronto),
        stringResource(R.string.postgrad_university_of_british_columbia),
        stringResource(R.string.postgrad_mcgill_university),
        stringResource(R.string.postgrad_university_of_melbourne),
        stringResource(R.string.postgrad_university_of_sydney),
        stringResource(R.string.postgrad_australian_national_university),
        stringResource(R.string.postgrad_tu_delft),
        stringResource(R.string.postgrad_eth_zurich),
        stringResource(R.string.postgrad_university_college_london),
        stringResource(R.string.postgrad_university_of_amsterdam),
        stringResource(R.string.postgrad_sorbonne_university),
        stringResource(R.string.postgrad_other)
    )
    var postGrad              by remember { mutableStateOf(tempProfile.postGraduation.orEmpty()) }
    var postGradExpanded      by remember { mutableStateOf(false) }
    var postGradYear          by remember { mutableStateOf(tempProfile.postGraduationYear) }
    var postGradDegree        by remember { mutableStateOf(tempProfile.postGraduationDegree.orEmpty()) }

    // ─── Religion & Community ────────────────────────────
    var religion             by remember { mutableStateOf(tempProfile.religion) }
    var community            by remember { mutableStateOf(tempProfile.community) }

    // ─── Caste ───────────────────────────────────────────
    var caste                by remember { mutableStateOf(tempProfile.caste) }
    val casteOptions         = listOf(
        stringResource(R.string.caste_kulin_brahmin),
        stringResource(R.string.caste_non_kulin_brahmin),
        stringResource(R.string.caste_kulin_kayastha),
        stringResource(R.string.caste_non_kulin_kayastha),
        stringResource(R.string.caste_baidya),
        stringResource(R.string.caste_kshatriya),
        stringResource(R.string.caste_vaishya),
        stringResource(R.string.caste_rajbonshi),
        stringResource(R.string.caste_sadgop),
        stringResource(R.string.caste_mahishya),
        stringResource(R.string.caste_scheduled_caste),
        stringResource(R.string.caste_scheduled_tribe),
        stringResource(R.string.caste_obc),
        stringResource(R.string.caste_general),
        stringResource(R.string.caste_other)
    )

    // ─── Height ──────────────────────────────────────────
    var isFeet               by remember { mutableStateOf(tempProfile.height2.isNotEmpty()) }
    var feet                 by remember { mutableStateOf(tempProfile.height2.getOrNull(0) ?: 0) }
    var inches               by remember { mutableStateOf(tempProfile.height2.getOrNull(1) ?: 0) }
    var heightCm             by remember { mutableStateOf(tempProfile.height) }

    // ─── Gender ──────────────────────────────────────────
    val genderOptions        = listOf(
        stringResource(R.string.male_option),
        stringResource(R.string.female_option),
        stringResource(R.string.college_other)
    )
    var selectedGender       by remember {
        mutableStateOf(genderOptions.find { it==tempProfile.gender }
            ?: genderOptions.first())
    }

    // ─── Job & Work ──────────────────────────────────────
    val jobRoleOptions = listOf(
        stringResource(R.string.job_role_option_software_developer),
        stringResource(R.string.job_role_option_data_scientist),
        stringResource(R.string.job_role_option_ux_ui_designer),
        stringResource(R.string.job_role_option_civil_engineer),
        stringResource(R.string.job_role_option_mechanical_engineer),
        stringResource(R.string.job_role_option_electrical_engineer),
        stringResource(R.string.job_role_option_project_manager),
        stringResource(R.string.job_role_option_product_manager),
        stringResource(R.string.job_role_option_business_analyst),
        stringResource(R.string.job_role_option_accountant),
        stringResource(R.string.job_role_option_chartered_accountant),
        stringResource(R.string.job_role_option_hr_manager),
        stringResource(R.string.job_role_option_marketing_manager),
        stringResource(R.string.job_role_option_sales_executive),
        stringResource(R.string.job_role_option_director),
        stringResource(R.string.job_role_option_ceo),
        stringResource(R.string.job_role_option_teacher),
        stringResource(R.string.job_role_option_professor),
        stringResource(R.string.job_role_option_researcher),
        stringResource(R.string.job_role_option_scientist),
        stringResource(R.string.job_role_option_doctor),
        stringResource(R.string.job_role_option_surgeon),
        stringResource(R.string.job_role_option_nurse),
        stringResource(R.string.job_role_option_pharmacist),
        stringResource(R.string.job_role_option_lawyer),
        stringResource(R.string.job_role_option_advocate),
        stringResource(R.string.job_role_option_legal_consultant),
        stringResource(R.string.job_role_option_graphic_designer),
        stringResource(R.string.job_role_option_content_writer),
        stringResource(R.string.job_role_option_photographer),
        stringResource(R.string.job_role_option_journalist),
        stringResource(R.string.job_role_option_editor),
        stringResource(R.string.job_role_option_chef),
        stringResource(R.string.job_role_option_barista),
        stringResource(R.string.job_role_option_pilot),
        stringResource(R.string.job_role_option_flight_attendant),
        stringResource(R.string.job_role_option_police_officer),
        stringResource(R.string.job_role_option_firefighter),
        stringResource(R.string.job_role_option_army_officer),
        stringResource(R.string.job_role_option_electrician),
        stringResource(R.string.job_role_option_plumber),
        stringResource(R.string.job_role_option_carpenter),
        stringResource(R.string.job_role_option_mechanic),
        stringResource(R.string.job_role_option_entrepreneur),
        stringResource(R.string.job_role_option_intern),
        stringResource(R.string.job_role_option_other)
    )
    var selectedJobRole      by remember {
        mutableStateOf(jobRoleOptions.find { it==tempProfile.jobRole }
            ?: jobRoleOptions.first())
    }
    var customJobRole        by remember {
        mutableStateOf(if (selectedJobRole==jobRoleOptions.last())
            tempProfile.customJobRole.orEmpty() else "")
    }

    val workOptions = listOf(
        stringResource(R.string.work_option_private_sector),
        stringResource(R.string.work_option_government),
        stringResource(R.string.work_option_information_technology),
        stringResource(R.string.work_option_healthcare),
        stringResource(R.string.work_option_education),
        stringResource(R.string.work_option_construction),
        stringResource(R.string.work_option_manufacturing),
        stringResource(R.string.work_option_agriculture),
        stringResource(R.string.work_option_pharmaceuticals),
        stringResource(R.string.work_option_banking),
        stringResource(R.string.work_option_insurance),
        stringResource(R.string.work_option_real_estate),
        stringResource(R.string.work_option_retail),
        stringResource(R.string.work_option_e_commerce),
        stringResource(R.string.work_option_telecom),
        stringResource(R.string.work_option_automobile),
        stringResource(R.string.work_option_mining),
        stringResource(R.string.work_option_media_entertainment),
        stringResource(R.string.work_option_hospitality),
        stringResource(R.string.work_option_logistics),
        stringResource(R.string.work_option_non_profit),
        stringResource(R.string.work_option_startup),
        stringResource(R.string.work_option_freelance),
        stringResource(R.string.work_option_unemployed),
        stringResource(R.string.work_option_other)
    )
    var selectedWork         by remember {
        mutableStateOf(workOptions.find { it==tempProfile.work }
            ?: workOptions.first())
    }
    var customWork           by remember {
        mutableStateOf(if (selectedWork==workOptions.last())
            tempProfile.customWork.orEmpty() else "")
    }

    // ─── City & Locality arrays ──────────────────────────
    val cityOptionsList      = stringArrayResource(id = R.array.city_names).toList()
    val localityOptionsList: List<String> = when(city) {
        stringResource(R.string.city_kolkata)     -> stringArrayResource(R.array.localities_kolkata).toList()
        stringResource(R.string.city_howrah)      -> stringArrayResource(R.array.localities_howrah).toList()
        stringResource(R.string.city_durgapur)    -> stringArrayResource(R.array.localities_durgapur).toList()
        stringResource(R.string.city_asansol)     -> stringArrayResource(R.array.localities_asansol).toList()
        stringResource(R.string.city_siliguri)    -> stringArrayResource(R.array.localities_siliguri).toList()
        stringResource(R.string.city_darjeeling)  -> stringArrayResource(R.array.localities_darjeeling).toList()
        stringResource(R.string.city_malda)       -> stringArrayResource(R.array.localities_malda).toList()
        stringResource(R.string.city_jalpaiguri)  -> stringArrayResource(R.array.localities_jalpaiguri).toList()
        stringResource(R.string.city_cooch_behar) -> stringArrayResource(R.array.localities_cooch_behar).toList()
        stringResource(R.string.city_alipurduar)  -> stringArrayResource(R.array.localities_alipurduar).toList()
        stringResource(R.string.city_bankura)     -> stringArrayResource(R.array.localities_bankura).toList()
        stringResource(R.string.city_purulia)     -> stringArrayResource(R.array.localities_purulia).toList()
        stringResource(R.string.city_kharagpur)   -> stringArrayResource(R.array.localities_kharagpur).toList()
        stringResource(R.string.city_midnapore)   -> stringArrayResource(R.array.localities_midnapore).toList()
        stringResource(R.string.city_bardhaman)   -> stringArrayResource(R.array.localities_bardhaman).toList()
        stringResource(R.string.city_hooghly)     -> stringArrayResource(R.array.localities_hooghly).toList()
        stringResource(R.string.city_murshidabad) -> stringArrayResource(R.array.localities_murshidabad).toList()
        stringResource(R.string.city_baharampur)  -> stringArrayResource(R.array.localities_baharampur).toList()
        stringResource(R.string.city_haldia)      -> stringArrayResource(R.array.localities_haldia).toList()
        stringResource(R.string.city_ranaghat)    -> stringArrayResource(R.array.localities_ranaghat).toList()
        stringResource(R.string.city_kalyani)     -> stringArrayResource(R.array.localities_kalyani).toList()
        stringResource(R.string.city_chandannagar)-> stringArrayResource(R.array.localities_chandannagar).toList()
        // … all your cities …
        else -> stringArrayResource(R.array.localities_other).toList()
    }

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Name
        OutlinedTextField(
            textStyle = LocalTextStyle.current.copy(color = Color.White),
            value       = name,
            onValueChange = { name = it },
            label       = { Text(stringResource(R.string.label_name), fontSize = 9.sp, color = Color(0xFFFF6F00)) },
            modifier    = Modifier.fillMaxWidth(),
            colors =  OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = Color(0xFFFF6F00),
                unfocusedBorderColor    = Color.Gray,
                cursorColor             = Color.White,
                )
        )

        // Height toggle & fields…
        Text(stringResource(R.string.height_label), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = isFeet, onCheckedChange = { isFeet = it }, colors = SwitchDefaults.colors(
                // thumb (the round “knob”)
                checkedThumbColor   = Color(0xFFFF6F00),
                uncheckedThumbColor = Color.Gray,
                // track (the background line)
                checkedTrackColor   = Color(0xFFFF6F00).copy(alpha = 0.54f),
                uncheckedTrackColor = Color.Gray.copy(alpha = 0.54f)
            ) )
            Spacer(Modifier.width(8.dp))
            Text(if (isFeet) stringResource(R.string.feet_inches_label)
            else stringResource(R.string.centimeters_label), fontSize = 9.sp, color = Color.White)
        }
        if (isFeet) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value           = feet.toString(),
                    onValueChange   = { feet = it.toIntOrNull() ?: 0 },
                    label           = { Text(stringResource(R.string.feet_label), fontSize = 9.sp, color = Color(0xFFFF6F00)) },
                    modifier        = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value           = inches.toString(),
                    onValueChange   = { inches = it.toIntOrNull() ?: 0 },
                    label           = { Text(stringResource(R.string.inches_label), fontSize = 9.sp, color = Color(0xFFFF6F00)) },
                    modifier        = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        } else {
            OutlinedTextField(
                value           = heightCm.toString(),
                onValueChange   = { heightCm = it.toIntOrNull() ?: 0 },
                label           = { Text(stringResource(R.string.centimeters_label), fontSize = 9.sp, color = Color(0xFFFF6F00)) },
                modifier        = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        // Religion chips
        Text(stringResource(R.string.religion_label), fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color(0xFFFF6F00))
        val religionOpts = listOf(
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
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            religionOpts.forEach { option ->
                FilterChip(
                    selected = religion==option,
                    onClick  = { religion = option },
                    label    = { Text(option, fontSize = 9.sp, color = Color.White) },
                    colors   = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor     = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }

        // Community chips
        Text(stringResource(R.string.community_label), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val commOpts = listOf(
                stringResource(R.string.community_other),
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

                /* ——— Arunachal Pradesh ——— */
                stringResource(R.string.community_arunachali),   // ← NEW

                /* ——— Assam plains tribes ——— */
                stringResource(R.string.community_assamese),
                stringResource(R.string.community_sonowal_kachari)
            )
            commOpts.forEach { option ->
                FilterChip(
                    selected = community==option,
                    onClick  = { community = option },
                    label    = { Text(option, fontSize = 9.sp, color = Color.White) },
                    colors   = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor     = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }

        // **Caste chips**
        Text(stringResource(R.string.caste_title), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            casteOptions.forEach { option ->
                FilterChip(
                    selected = caste==option,
                    onClick  = { caste = option },
                    label    = { Text(option, fontSize = 9.sp, color = Color.White) },
                    colors   = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor     = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }

        // Gender chips
        Text(stringResource(R.string.gender_label), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genderOptions.forEach { option ->
                FilterChip(
                    selected = selectedGender==option,
                    onClick  = { selectedGender = option },
                    label    = { Text(option, fontSize = 9.sp, color = Color.White) },
                    colors   = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor     = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }

        // City dropdown…
        SearchableDropdownWithCustomOption(
            title               = stringResource(R.string.city_label),
            options             = cityOptionsList,
            selectedOption      = city,
            onOptionSelected    = { sel ->
                city = sel
            },
            customInput         = customCity,
            onCustomInputChange = { customCity = it!! }
        )
        Spacer(Modifier.height(6.dp))

        SearchableDropdownWithCustomOption(
            title               = stringResource(R.string.label_locality),
            options             = localityOptionsList,
            selectedOption      = locality,
            onOptionSelected    = { sel ->
                locality = sel
            },
            customInput         = customLocality,
            onCustomInputChange = { customLocality = it!! }
        )
        Spacer(Modifier.height(6.dp))

        // Job-Role & Work chips + optional customs…
        Text(stringResource(R.string.job_role_label), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            jobRoleOptions.forEach { option ->
                FilterChip(
                    selected = selectedJobRole==option,
                    onClick  = {
                        selectedJobRole = option
                        if (option!=jobRoleOptions.last()) customJobRole = ""
                    },
                    label    = { Text(option, fontSize = 9.sp, color = Color.White) },
                    colors   = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor     = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }
        if (selectedJobRole==jobRoleOptions.last()) {
            OutlinedTextField(
                value       = customJobRole,
                onValueChange = { customJobRole = it },
                label       = { Text(stringResource(R.string.label_custom_job_role), fontSize = 9.sp, color = Color(0xFFFF6F00)) },
                modifier    = Modifier.fillMaxWidth()
            )
        }

        Text(stringResource(R.string.label_work), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            workOptions.forEach { option ->
                FilterChip(
                    selected = selectedWork==option,
                    onClick  = {
                        selectedWork = option
                        if (option!=workOptions.last()) customWork = ""
                    },
                    label    = { Text(option, fontSize = 9.sp, color = Color.White) },
                    colors   = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor     = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }
        if (selectedWork==workOptions.last()) {
            OutlinedTextField(
                value       = customWork,
                onValueChange = { customWork = it },
                label       = { Text(stringResource(R.string.label_custom_work), fontSize = 9.sp, color = Color(0xFFFF6F00)) },
                modifier    = Modifier.fillMaxWidth()
            )
        }

        // High-School dropdown + Year…
        SearchableDropdownWithCustomOption(
            title               = stringResource(R.string.label_high_school),
            options             = highSchoolOptions,
            selectedOption      = highSchool,
            onOptionSelected    = { sel ->
                highSchool = sel
            },
            customInput         = customHighSchool,
            onCustomInputChange = { customHighSchool = it!! }
        )
        if (highSchool.isNotBlank()) {
            OutlinedTextField(
                value           = highSchoolYear,
                onValueChange   = { highSchoolYear = it.filter { c -> c.isDigit() }.take(4) },
                label           = { Text(stringResource(R.string.high_school_graduation_year), fontSize = 9.sp, color = Color(0xFFFF6F00)) },
                modifier        = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        // College dropdown + Year + Degree…
        SearchableDropdownWithCustomOption(
            title               = stringResource(R.string.college_label),
            options             = collegeOptions,
            selectedOption      = college,
            onOptionSelected    = { sel ->
                college = sel
            },
            customInput         = customCollege,
            onCustomInputChange = { customCollege = it!! }
        )
        if (college.isNotBlank()) {
            OutlinedTextField(
                value           = collegeYear,
                onValueChange   = { collegeYear = it.filter { c -> c.isDigit() }.take(4) },
                label           = { Text(stringResource(R.string.college_graduation_year), fontSize = 9.sp, color = Color(0xFFFF6F00)) },
                modifier        = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value           = collegeDegree,
                onValueChange   = { if (it.length <= 50) collegeDegree = it },
                label           = { Text(stringResource(R.string.label_college_degree), fontSize = 9.sp, color = Color(0xFFFF6F00)) },
                modifier        = Modifier.fillMaxWidth(),
                singleLine      = true
            )
        }

        // Post-Grad dropdown + Year + Degree…
        SearchableDropdownWithCustomOption(
            title               = stringResource(R.string.post_graduation_label),
            options             = postGradOptions,
            selectedOption      = postGrad,
            onOptionSelected    = { sel ->
                postGrad = sel
            },
            customInput         = customPostGrad,
            onCustomInputChange = { customPostGrad = it!! }
        )
        if (postGrad.isNotBlank()) {
            OutlinedTextField(
                value           = postGradYear,
                onValueChange   = { postGradYear = it.filter { c -> c.isDigit() }.take(4) },
                label           = { Text(stringResource(R.string.select_graduation_year_label), fontSize = 9.sp, color = Color(0xFFFF6F00)) },
                modifier        = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value           = postGradDegree,
                onValueChange   = { if (it.length <= 50) postGradDegree = it },
                label           = { Text(stringResource(R.string.label_post_graduation_degree), fontSize = 9.sp, color = Color(0xFFFF6F00)) },
                modifier        = Modifier.fillMaxWidth(),
                singleLine      = true
            )
        }

        // ─── Save / Cancel ───────────────────────────────────
        ButtonRow(
            onSave = {
                onSave(tempProfile.copy(
                    name                       = name,
                    city                       = city,
                    customCity                 = customCity.ifBlank { null },
                    hometown                   = locality,
                    customHometown             = customLocality.ifBlank { null },
                    highSchool                 = highSchool,
                    customHighSchool           = customHighSchool.ifBlank { null },
                    highSchoolGraduationYear   = highSchoolYear,
                    college                    = college,
                    customCollege              = customCollege.ifBlank { null },
                    collegeGraduationYear      = collegeYear,
                    collegeDegree              = collegeDegree.ifBlank { null },
                    postGraduation             = postGrad.ifBlank { null },
                    postGraduationYear         = postGradYear,
                    postGraduationDegree       = postGradDegree.ifBlank { null },
                    religion                   = religion,
                    community                  = community,
                    caste                      = caste,
                    height                     = heightCm,
                    height2                    = if (isFeet) listOf(feet, inches) else emptyList(),
                    gender                     = selectedGender,
                    jobRole                    = selectedJobRole,
                    customJobRole              = selectedJobRole.takeIf { it == jobRoleOptions.last() }?.let { customJobRole },
                    work                       = selectedWork,
                    customWork                 = selectedWork.takeIf { it == workOptions.last() }?.let { customWork }
                ))
            },
            onCancel = onCancel
        )
    }
}

@Composable
fun PerformanceMetricsSection(profile: Profile) {
    var showPerformance by rememberSaveable { mutableStateOf(false) }

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
    ProfileDetailRow(    stringResource(R.string.looking_for_label),
        localizedLookingFor(profile.lookingFor)
            .ifBlank { stringResource(R.string.not_specified) }, Icons.Default.Favorite)
    ProfileDetailRow(stringResource(R.string.label_love_language), localizedLoveLanguage(profile.loveLanguage)
        .ifBlank { stringResource(R.string.not_set) }, Icons.Default.Favorite)
    ProfileDetailRow(stringResource(R.string.label_politics), localizedPolitics(profile.politics)
        .ifBlank { stringResource(R.string.not_set) }, Icons.Default.HowToVote)
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
    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontSize = 7.sp)) {
        Column {
            val lifestyle = profile.lifestyle
            if (isLifestyleEmpty(lifestyle)) {
                Text("No lifestyle specified.", color = Color.Gray, fontSize = 10.sp)
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
        // Social & Community
        "Charity work" to R.string.interest_charity,
        "দান কার্যক্রম" to R.string.interest_charity,
        "चैरिटी कार्य" to R.string.interest_charity,

        "Community organizing" to R.string.interest_community,
        "কমিউনিটি সংগঠন" to R.string.interest_community,
        "समुदाय आयोजन" to R.string.interest_community,

        "Networking" to R.string.interest_networking,
        "নেটওয়ার্কিং" to R.string.interest_networking,
        "नेटवर्किंग" to R.string.interest_networking,

        "Public speaking" to R.string.interest_public_speaking,
        "পাবলিক স্পিকিং" to R.string.interest_public_speaking,
        "पब्लिक स्पीकिंग" to R.string.interest_public_speaking,

        "Writing" to R.string.interest_writing,
        "লেখা" to R.string.interest_writing,
        "लेखन" to R.string.interest_writing,

        "Blogging" to R.string.interest_blogging,
        "ব্লগিং" to R.string.interest_blogging,
        "ब्लॉगिंग" to R.string.interest_blogging,

        "Podcasting" to R.string.interest_podcasting,
        "পডকাস্টিং" to R.string.interest_podcasting,
        "पॉडकास्टिंग" to R.string.interest_podcasting,

        "Social media" to R.string.interest_social_media,
        "সোশ্যাল মিডিয়া" to R.string.interest_social_media,
        "सोशल मीडिया" to R.string.interest_social_media,

        "Online communities" to R.string.interest_online_communities,
        "অনলাইন কমিউনিটি" to R.string.interest_online_communities,
        "ऑनलाइन समुदाय" to R.string.interest_online_communities,

// Adventurous & Thrilling
        "Skydiving" to R.string.interest_skydiving,
        "স্কাইডাইভিং" to R.string.interest_skydiving,
        "स्काईडाइविंग" to R.string.interest_skydiving,

        "Scuba diving" to R.string.interest_scuba_diving,
        "স্কুবা ডাইভিং" to R.string.interest_scuba_diving,
        "स्कूबा डाइविंग" to R.string.interest_scuba_diving,

        "Rock climbing" to R.string.interest_rock_climbing,
        "রক ক্লাইমিং" to R.string.interest_rock_climbing,
        "रॉक क्लाइम्बिंग" to R.string.interest_rock_climbing,

        "Surfing" to R.string.interest_surfing,
        "সার্ফিং" to R.string.interest_surfing,
        "सर्फिंग" to R.string.interest_surfing,

        "Skiing" to R.string.interest_skiing,
        "স্কিইং" to R.string.interest_skiing,
        "स्कीयिंग" to R.string.interest_skiing,

        "Snowboarding" to R.string.interest_snowboarding,
        "স্নোবোর্ডিং" to R.string.interest_snowboarding,
        "स्नोबोर्डिंग" to R.string.interest_snowboarding,

        "Mountain biking" to R.string.interest_mountain_biking,
        "মাউন্টাইন বাইকিং" to R.string.interest_mountain_biking,
        "माउंटेन बाइकिंग" to R.string.interest_mountain_biking,

        "Motorcycling" to R.string.interest_motorcycling,
        "মোটরসাইক্লিং" to R.string.interest_motorcycling,
        "मोटरसाइक्लिंग" to R.string.interest_motorcycling,

        "Car racing" to R.string.interest_car_racing,
        "কার রেসিং" to R.string.interest_car_racing,
        "कार रेसिंग" to R.string.interest_car_racing,

        "Extreme sports" to R.string.interest_extreme_sports,
        "এক্সট্রিম স্পোর্টস" to R.string.interest_extreme_sports,
        "एक्सट्रीम स्पोर्ट्स" to R.string.interest_extreme_sports,

// Relaxation & Leisure
        "Puzzles" to R.string.interest_puzzles,
        "ধাঁধা" to R.string.interest_puzzles,
        "पहेलियाँ" to R.string.interest_puzzles,

        "Board games" to R.string.interest_board_games,
        "বোর্ড গেমস" to R.string.interest_board_games,
        "बोर्ड गेम्स" to R.string.interest_board_games,

        "Video games" to R.string.interest_video_games,
        "ভিডিও গেমস" to R.string.interest_video_games,
        "वीडियो गेम्स" to R.string.interest_video_games,

        "Watching TV" to R.string.interest_watching_tv,
        "টিভি দেখা" to R.string.interest_watching_tv,
        "टीवी देखना" to R.string.interest_watching_tv,

        "Napping" to R.string.interest_napping,
        "ন্যাপিং" to R.string.interest_napping,
        "नैपिंग" to R.string.interest_napping,

        "Spa days" to R.string.interest_spa_days,
        "স্পা ডে" to R.string.interest_spa_days,
        "स्पा दिन" to R.string.interest_spa_days,

        "Beach days" to R.string.interest_beach_days,
        "বিচ ডে" to R.string.interest_beach_days,
        "बीच डे" to R.string.interest_beach_days,

        "Picnics" to R.string.interest_picnics,
        "পিকনিক" to R.string.interest_picnics,
        "पिकनिक" to R.string.interest_picnics,

// Tech & Intellectual
        "Coding" to R.string.interest_coding,
        "কোডিং" to R.string.interest_coding,
        "कोडिंग" to R.string.interest_coding,

        "Robotics" to R.string.interest_robotics,
        "রোবোটিক্স" to R.string.interest_robotics,
        "रोबोटिक्स" to R.string.interest_robotics,

        "Space exploration" to R.string.interest_space,
        "মহাকাশ অন্বেষণ" to R.string.interest_space,
        "अंतरिक्ष अन्वेषण" to R.string.interest_space,

        "Environmentalism" to R.string.interest_environmentalism,
        "পরিবেশবাদ" to R.string.interest_environmentalism,
        "पर्यावरणवाद" to R.string.interest_environmentalism,

// Food & Drink
        "Baking" to R.string.interest_baking,
        "বেকিং" to R.string.interest_baking,
        "बैकिंग" to R.string.interest_baking,

        "Wine tasting" to R.string.interest_wine_tasting,
        "ওয়াইন টেস্টিং" to R.string.interest_wine_tasting,
        "वाइन चखना" to R.string.interest_wine_tasting,

        "Craft beer" to R.string.interest_craft_beer,
        "ক্রাফ্ট বিয়ার" to R.string.interest_craft_beer,
        "क्राफ्ट बियर" to R.string.interest_craft_beer,

        "Coffee" to R.string.interest_coffee,
        "কফি" to R.string.interest_coffee,
        "कॉफ़ी" to R.string.interest_coffee,

// Wellness & Spiritual
        "Yoga" to R.string.interest_yoga,
        "যোগ" to R.string.interest_yoga,
        "योग" to R.string.interest_yoga,

        "Meditation" to R.string.interest_meditation,
        "ধ্যান" to R.string.interest_meditation,
        "ध्यान" to R.string.interest_meditation,

        "Astrology" to R.string.interest_astrology,
        "জ্যোতিষ" to R.string.interest_astrology,
        "ज्योतिष" to R.string.interest_astrology,

        "Romance" to R.string.interest_romance,
        "রোমান্স" to R.string.interest_romance, // Bengali
        "रोमांस" to R.string.interest_romance, // Hindi

        "Crystals" to R.string.interest_crystals,
        "ক্রিস্টালস" to R.string.interest_crystals,
        "क्रिस्टल" to R.string.interest_crystals,

// Style & DIY
        "Vintage clothing" to R.string.interest_vintage_clothing,
        "ভিন্টেজ পোশাক" to R.string.interest_vintage_clothing,
        "विंटेज कपड़े" to R.string.interest_vintage_clothing,

        "Thrift shopping" to R.string.interest_thrift_shopping,
        "থ্রিফট শপিং" to R.string.interest_thrift_shopping,
        "थ्रिफ्ट शॉपिंग" to R.string.interest_thrift_shopping,

        "DIY projects" to R.string.interest_diy,
        "ডিআইওয়াই প্রকল্প" to R.string.interest_diy,
        "डीआईवाई प्रोजेक्ट" to R.string.interest_diy,

        "Home improvement" to R.string.interest_home_improvement,
        "গৃহ উন্নয়ন" to R.string.interest_home_improvement,
        "गृह सुधार" to R.string.interest_home_improvement,

        "Interior design" to R.string.interest_interior_design,
        "অভ্যন্তরীণ নকশা" to R.string.interest_interior_design,
        "इंटीरियर डिज़ाइन" to R.string.interest_interior_design,

// Intellectual
        "History" to R.string.interest_history,
        "ইতিহাস" to R.string.interest_history,
        "इतिहास" to R.string.interest_history,

        "Science" to R.string.interest_science,
        "বিজ্ঞান" to R.string.interest_science,
        "विज्ञान" to R.string.interest_science,

        "Philosophy" to R.string.interest_philosophy,
        "দর্শন শাস্ত্র" to R.string.interest_philosophy,
        "दर्शनशास्त्र" to R.string.interest_philosophy,

        "Politics" to R.string.interest_politics,
        "রাজনীতি" to R.string.interest_politics,
        "राजनीति" to R.string.interest_politics,

        "Economics" to R.string.interest_economics,
        "অর্থনীতি" to R.string.interest_economics,
        "अर्थशास्त्र" to R.string.interest_economics,

// Outdoor & Nature
        "Hiking" to R.string.interest_hiking,
        "হাইকিং" to R.string.interest_hiking,
        "हাইকिंग" to R.string.interest_hiking,

        "Camping" to R.string.interest_camping,
        "ক্যাম্পিং" to R.string.interest_camping,
        "कैंपिंग" to R.string.interest_camping,

        "Fishing" to R.string.interest_fishing,
        "মাছ ধরা" to R.string.interest_fishing,
        "मछली पकड़ना" to R.string.interest_fishing,

        "Hunting" to R.string.interest_hunting,
        "শিকার" to R.string.interest_hunting,
        "शिकार" to R.string.interest_hunting,

// Global Interest
        "Traveling" to R.string.interest_traveling,
        "ভ্রমণ" to R.string.interest_traveling,
        "यात्रा" to R.string.interest_traveling,
        )

    if (profile.interests.isEmpty()) {
        Text(
            text = stringResource(R.string.no_interests),
            color = Color.Gray,
            fontSize = 10.sp
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
    val storageRef = FirebaseRefs.storage.reference
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
        ButtonRow(
            onSave    = { onSave(bio, newVoiceUrl) },
            onCancel  = onCancel,
            modifier  = Modifier.padding(top = 16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PreferencesEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    val notSelected = stringResource(R.string.not_selected)

    // Looking For
    val lookingForOptions = listOf(
        stringResource(R.string.looking_for_romance),
        stringResource(R.string.looking_for_connection),
        stringResource(R.string.looking_for_partner),
        stringResource(R.string.looking_for_marriage)
    )
    var selectedLookingFor by remember { mutableStateOf(tempProfile.lookingFor.ifBlank { notSelected }) }

    // Love Language
    val loveLanguageOptions = listOf(
        stringResource(R.string.love_language_option_words_of_affirmation),
        stringResource(R.string.love_language_option_acts_of_service),
        stringResource(R.string.love_language_option_receiving_gifts),
        stringResource(R.string.love_language_option_quality_time),
        stringResource(R.string.love_language_option_physical_touch),
        stringResource(R.string.love_language_option_other)
    )
    var selectedLoveLanguage by remember { mutableStateOf(tempProfile.loveLanguage.ifBlank { notSelected }) }

    // Politics
    val politicsOptions = listOf(
        stringResource(R.string.politics_option_far_left),
        stringResource(R.string.politics_option_left),
        stringResource(R.string.politics_option_centre_left),
        stringResource(R.string.politics_option_centre),
        stringResource(R.string.politics_option_centre_right),
        stringResource(R.string.politics_option_right),
        stringResource(R.string.politics_option_far_right),
        stringResource(R.string.politics_option_liberal),
        stringResource(R.string.politics_option_conservative),
        stringResource(R.string.politics_option_moderate),
        stringResource(R.string.politics_option_socialist),
        stringResource(R.string.politics_option_communist),
        stringResource(R.string.politics_option_other)
    )
    var selectedPolitics by remember { mutableStateOf(tempProfile.politics.ifBlank { notSelected }) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Looking For ---
        Text(stringResource(R.string.looking_for_label), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lookingForOptions.forEach { option ->
                FilterChip(
                    selected = selectedLookingFor == option,
                    onClick = { selectedLookingFor = option },
                    label = { Text(option, fontSize = 9.sp, color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor     = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }

        // --- Love Language ---
        Text(stringResource(R.string.love_language_label), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            loveLanguageOptions.forEach { option ->
                FilterChip(
                    selected = selectedLoveLanguage == option,
                    onClick = { selectedLoveLanguage = option },
                    label = { Text(option, fontSize = 9.sp, color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor     = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }

        // --- Politics ---
        Text(stringResource(R.string.label_politics), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6F00))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            politicsOptions.forEach { option ->
                FilterChip(
                    selected = selectedPolitics == option,
                    onClick = { selectedPolitics = option },
                    label = { Text(option, fontSize = 9.sp, color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor     = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        ButtonRow(
            onSave = {
                val updated = tempProfile.copy(
                    lookingFor   = selectedLookingFor.takeIf { it != notSelected } ?: "",
                    loveLanguage = selectedLoveLanguage.takeIf { it != notSelected } ?: "",
                    politics     = selectedPolitics.takeIf { it != notSelected } ?: ""
                )
                onSave(updated)
            },
            onCancel = onCancel
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialCausesSection(profile: Profile) {
    if (profile.socialCauses.isEmpty()) {
        Text(stringResource(R.string.no_social_causes), color = Color.Gray, fontSize = 10.sp)
    } else {
        Column {
            Text(stringResource(R.string.social_causes), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement   = Arrangement.spacedBy(8.dp)) {
                profile.socialCauses.forEach { cause ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFF6F00)
                    ) {
                        Text(
                            text = cause,
                            color = Color.White,
                            fontSize = 10.sp,
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
    // Load your predefined list from resources:
    val allCauses = stringArrayResource(R.array.social_causes_list).toList()

    // Track which causes are selected:
    val selectedCauses = remember {
        mutableStateListOf<String>().apply { addAll(tempProfile.socialCauses) }
    }

    val maxSelections = 5

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.social_causes),
            color = Color(0xFFFF6F00),
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            allCauses.forEach { cause ->
                val isSelected = selectedCauses.contains(cause)
                val canSelectMore = selectedCauses.size < maxSelections

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            selectedCauses.remove(cause)
                        } else if (canSelectMore) {
                            selectedCauses.add(cause)
                        }
                    },
                    enabled = isSelected || canSelectMore,
                    label = { Text(cause, fontSize = 9.sp, color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor     = Color(0xFFFF6F00),
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.Black
                    )
                )
            }
        }

        if (selectedCauses.size > maxSelections) {
            Text(
                text = stringResource(R.string.max_social_causes_error, maxSelections),
                color = Color.Red,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        ButtonRow(
            onSave   = { onSave(tempProfile.copy(socialCauses = selectedCauses.toList())) },
            onCancel = onCancel
        )
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
        LifestyleSliderEdit(
            label = stringResource(R.string.lifestyle_culinary_enthusiasm),
            value = localLifestyle.culinary_enthusiasm,
            nouns = listOf(
                stringResource(R.string.not_a_foodie),
                stringResource(R.string.occasional_foodie),
                stringResource(R.string.foodie),
                stringResource(R.string.passionate_foodie),
                stringResource(R.string.gourmet)
            )
        ) { localLifestyle = localLifestyle.copy(culinary_enthusiasm = it) }
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
    // keep a local tempProfile and re-sync whenever the parent `profile` updates
    var tempProfile by remember { mutableStateOf(profile) }
    LaunchedEffect(profile) {
        tempProfile = profile
    }

    var showBioVoice by rememberSaveable { mutableStateOf(false) }
    var editBioVoice by rememberSaveable { mutableStateOf(false) }
    var showBasic by rememberSaveable { mutableStateOf(false) }
    var showPreferences by rememberSaveable { mutableStateOf(false) }
    var showSocialCauses by rememberSaveable { mutableStateOf(false) } // New
    var showLifestyle by rememberSaveable { mutableStateOf(false) }
    var showInterests by rememberSaveable { mutableStateOf(false) }
    var editBasic by rememberSaveable { mutableStateOf(false) }
    var editPreferences by rememberSaveable { mutableStateOf(false) }
    var editSocialCauses by rememberSaveable { mutableStateOf(false) } // New
    var editLifestyle by rememberSaveable { mutableStateOf(false) }
    var editInterests by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        if (tempProfile.isMatrimonyMode) {
            var showMatrimony by rememberSaveable { mutableStateOf(false) }
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
        Spacer(modifier = Modifier.height(12.dp))
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
                    color = Color.White, fontSize = 10.sp
                )
                Spacer(Modifier.height(8.dp))
                if (!tempProfile.voiceNoteUrl.isNullOrEmpty()) {
                    VoicePlayer(url = tempProfile.voiceNoteUrl!!)
                } else {
                    Text(stringResource(R.string.voice_no_voice_bio), color = Color.White, fontSize = 10.sp)
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsEditSection(
    tempProfile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit
) {
    // Reuse the same mapping from raw interest name → stringResource(id)
    val interestNameToResource = mapOf(
        // Global Interests
        "Traveling" to R.string.interest_traveling,
        "ভ্রমণ" to R.string.interest_traveling,        // Bengali
        "यात्रा" to R.string.interest_traveling,       // Hindi

        "Music" to R.string.interest_music,
        "সঙ্গীত" to R.string.interest_music,
        "संगीत" to R.string.interest_music,

        "Food" to R.string.interest_food,
        "খাবার" to R.string.interest_food,
        "भोजन" to R.string.interest_food,

        "Sports" to R.string.interest_sports,
        "খেলাধুলা" to R.string.interest_sports,
        "खेल" to R.string.interest_sports,

        "Movies" to R.string.interest_movies,
        "সিনেমা" to R.string.interest_movies,
        "फ़िल्में" to R.string.interest_movies,

        "Books" to R.string.interest_books,
        "বই" to R.string.interest_books,
        "किताबें" to R.string.interest_books,

        "Art" to R.string.interest_art,
        "শিল্প" to R.string.interest_art,
        "कला" to R.string.interest_art,

        "Photography" to R.string.interest_photography,
        "ফটোগ্রাফি" to R.string.interest_photography,
        "फ़ोटोग्राफी" to R.string.interest_photography,

        "Gaming" to R.string.interest_gaming,
        "গেমিং" to R.string.interest_gaming,
        "गेमिंग" to R.string.interest_gaming,

        "Fitness" to R.string.interest_fitness,
        "ফিটনেস" to R.string.interest_fitness,
        "फ़िटनेस" to R.string.interest_fitness,

        // Specific Outdoor & Nature
        "Hiking" to R.string.interest_hiking,
        "হাইকিং" to R.string.interest_hiking,
        "हाइकिंग" to R.string.interest_hiking,

        "Camping" to R.string.interest_camping,
        "ক্যাম্পিং" to R.string.interest_camping,
        "कैंपिंग" to R.string.interest_camping,

        "Fishing" to R.string.interest_fishing,
        "মাছ ধরা" to R.string.interest_fishing,
        "मछली पकड़ना" to R.string.interest_fishing,

        "Hunting" to R.string.interest_hunting,
        "শিকার" to R.string.interest_hunting,
        "शिकार" to R.string.interest_hunting,

        "Gardening" to R.string.interest_gardening,
        "বাগান করা" to R.string.interest_gardening,
        "बागवानी" to R.string.interest_gardening,

        // Food & Drink
        "Cooking" to R.string.interest_cooking,
        "রান্না" to R.string.interest_cooking,
        "खाना बनाना" to R.string.interest_cooking,

        "Baking" to R.string.interest_baking,
        "বেকিং" to R.string.interest_baking,
        "बैकिंग" to R.string.interest_baking,

        "Wine tasting" to R.string.interest_wine_tasting,
        "ওয়াইন টেস্টিং" to R.string.interest_wine_tasting,
        "वाइन चखना" to R.string.interest_wine_tasting,

        "Craft beer" to R.string.interest_craft_beer,
        "ক্রাফ্ট বিয়ার" to R.string.interest_craft_beer,
        "क्राफ्ट बियर" to R.string.interest_craft_beer,

        "Coffee" to R.string.interest_coffee,
        "কফি" to R.string.interest_coffee,
        "कॉफ़ी" to R.string.interest_coffee,

        // Wellness & Spiritual
        "Yoga" to R.string.interest_yoga,
        "যোগ" to R.string.interest_yoga,
        "योग" to R.string.interest_yoga,

        "Meditation" to R.string.interest_meditation,
        "ধ্যান" to R.string.interest_meditation,
        "ध्यान" to R.string.interest_meditation,

        "Astrology" to R.string.interest_astrology,
        "জ্যোতিষ" to R.string.interest_astrology,
        "ज्योतिष" to R.string.interest_astrology,

        "Romance" to R.string.interest_romance,
        "রোমান্স" to R.string.interest_romance, // Bengali
        "रोमांस" to R.string.interest_romance, // Hindi

        "Crystals" to R.string.interest_crystals,
        "ক্রিস্টালস" to R.string.interest_crystals,
        "क्रिस्टल" to R.string.interest_crystals,

        // Style & DIY
        "Vintage clothing" to R.string.interest_vintage_clothing,
        "ভিন্টেজ পোশাক" to R.string.interest_vintage_clothing,
        "विंटेज कपड़े" to R.string.interest_vintage_clothing,

        "Thrift shopping" to R.string.interest_thrift_shopping,
        "থ্রিফট শপিং" to R.string.interest_thrift_shopping,
        "थ्रिफ्ट शॉपिंग" to R.string.interest_thrift_shopping,

        "DIY projects" to R.string.interest_diy,
        "ডিআইওয়াই প্রকল্প" to R.string.interest_diy,
        "डीआईवाई प्रोजेक्ट" to R.string.interest_diy,

        "Home improvement" to R.string.interest_home_improvement,
        "গৃহ উন্নয়ন" to R.string.interest_home_improvement,
        "गृह सुधार" to R.string.interest_home_improvement,

        "Interior design" to R.string.interest_interior_design,
        "অভ্যন্তরীণ নকশা" to R.string.interest_interior_design,
        "इंटीरियर डिज़ाइन" to R.string.interest_interior_design,

        // Intellectual & Tech
        "History" to R.string.interest_history,
        "ইতিহাস" to R.string.interest_history,
        "इतिहास" to R.string.interest_history,

        "Science" to R.string.interest_science,
        "বিজ্ঞান" to R.string.interest_science,
        "विज्ञान" to R.string.interest_science,

        "Philosophy" to R.string.interest_philosophy,
        "দর্শন শাস্ত্র" to R.string.interest_philosophy,
        "दर्शनशास्त्र" to R.string.interest_philosophy,

        "Politics" to R.string.interest_politics,
        "রাজনীতি" to R.string.interest_politics,
        "राजनीति" to R.string.interest_politics,

        "Economics" to R.string.interest_economics,
        "অর্থনীতি" to R.string.interest_economics,
        "अर्थशास्त्र" to R.string.interest_economics,

        "Technology" to R.string.interest_technology,
        "প্রযুক্তি" to R.string.interest_technology,
        "प्रौद्योगिकी" to R.string.interest_technology,

        "Coding" to R.string.interest_coding,
        "কোডিং" to R.string.interest_coding,
        "कोडिंग" to R.string.interest_coding,

        "Robotics" to R.string.interest_robotics,
        "রোবোটিক্স" to R.string.interest_robotics,
        "रोबोटिक्स" to R.string.interest_robotics,

        "Space exploration" to R.string.interest_space,
        "মহাকাশ অন্বেষণ" to R.string.interest_space,
        "अंतरिक्ष अन्वेषण" to R.string.interest_space,

        "Environmentalism" to R.string.interest_environmentalism,
        "পরিবেশবাদ" to R.string.interest_environmentalism,
        "पर्यावरणवाद" to R.string.interest_environmentalism,

        // Social & Community
        "Volunteering" to R.string.interest_volunteering,
        "স্বেচ্ছাসেবা" to R.string.interest_volunteering,
        "स्वयंसेवा" to R.string.interest_volunteering,

        "Charity work" to R.string.interest_charity,
        "দান কার্যক্রম" to R.string.interest_charity,
        "चैरिटी कार्य" to R.string.interest_charity,

        "Community organizing" to R.string.interest_community,
        "কমিউনিটি সংগঠন" to R.string.interest_community,
        "समुदाय आयोजन" to R.string.interest_community,

        "Networking" to R.string.interest_networking,
        "নেটওয়ার্কিং" to R.string.interest_networking,
        "नेटवर्किंग" to R.string.interest_networking,

        "Public speaking" to R.string.interest_public_speaking,
        "পাবলিক স্পিকিং" to R.string.interest_public_speaking,
        "पब्लिक स्पीकिंग" to R.string.interest_public_speaking,

        "Writing" to R.string.interest_writing,
        "লেখা" to R.string.interest_writing,
        "लेखन" to R.string.interest_writing,

        "Blogging" to R.string.interest_blogging,
        "ব্লগিং" to R.string.interest_blogging,
        "ब्लॉगिंग" to R.string.interest_blogging,

        "Podcasting" to R.string.interest_podcasting,
        "পডকাস্টিং" to R.string.interest_podcasting,
        "पॉडकास्टिंग" to R.string.interest_podcasting,

        "Social media" to R.string.interest_social_media,
        "সোশ্যাল মিডিয়া" to R.string.interest_social_media,
        "सोशल मीडिया" to R.string.interest_social_media,

        "Online communities" to R.string.interest_online_communities,
        "অনলাইন কমিউনিটি" to R.string.interest_online_communities,
        "ऑनलाइन समुदाय" to R.string.interest_online_communities,

        // Adventurous & Thrilling
        "Skydiving" to R.string.interest_skydiving,
        "স্কাইডাইভিং" to R.string.interest_skydiving,
        "स्काईडाइविंग" to R.string.interest_skydiving,

        "Scuba diving" to R.string.interest_scuba_diving,
        "স্কুবা ডাইভিং" to R.string.interest_scuba_diving,
        "स्कूबा डाइविंग" to R.string.interest_scuba_diving,

        "Rock climbing" to R.string.interest_rock_climbing,
        "রক ক্লাইমিং" to R.string.interest_rock_climbing,
        "रॉक क्लाइम्बिंग" to R.string.interest_rock_climbing,

        "Surfing" to R.string.interest_surfing,
        "সার্ফিং" to R.string.interest_surfing,
        "सर्फिंग" to R.string.interest_surfing,

        "Skiing" to R.string.interest_skiing,
        "স্কিইং" to R.string.interest_skiing,
        "स्कीयिंग" to R.string.interest_skiing,

        "Snowboarding" to R.string.interest_snowboarding,
        "স্নোবোর্ডিং" to R.string.interest_snowboarding,
        "स्नोबोर्डिंग" to R.string.interest_snowboarding,

        "Mountain biking" to R.string.interest_mountain_biking,
        "মাউন্টাইন বাইকিং" to R.string.interest_mountain_biking,
        "माउंटेन बाइकिंग" to R.string.interest_mountain_biking,

        "Motorcycling" to R.string.interest_motorcycling,
        "মোটরসাইক্লিং" to R.string.interest_motorcycling,
        "मोटरसाइक्लिंग" to R.string.interest_motorcycling,

        "Car racing" to R.string.interest_car_racing,
        "কার রেসিং" to R.string.interest_car_racing,
        "कार रेसिंग" to R.string.interest_car_racing,

        "Extreme sports" to R.string.interest_extreme_sports,
        "এক্সট্রিম স্পোর্টস" to R.string.interest_extreme_sports,
        "एक्सट्रीम स्पोर्ट्स" to R.string.interest_extreme_sports,

        // Relaxation & Leisure
        "Puzzles" to R.string.interest_puzzles,
        "ধাঁধা" to R.string.interest_puzzles,
        "पहेलियाँ" to R.string.interest_puzzles,

        "Board games" to R.string.interest_board_games,
        "বোর্ড গেমস" to R.string.interest_board_games,
        "बोर्ड गेम्स" to R.string.interest_board_games,

        "Video games" to R.string.interest_video_games,
        "ভিডিও গেমস" to R.string.interest_video_games,
        "वीडियो गेम्स" to R.string.interest_video_games,

        "Watching TV" to R.string.interest_watching_tv,
        "টিভি দেখা" to R.string.interest_watching_tv,
        "टीवी देखना" to R.string.interest_watching_tv,

        "Napping" to R.string.interest_napping,
        "ন্যাপিং" to R.string.interest_napping,
        "नैपिंग" to R.string.interest_napping,

        "Spa days" to R.string.interest_spa_days,
        "স্পা ডে" to R.string.interest_spa_days,
        "स्पा दिन" to R.string.interest_spa_days,

        "Beach days" to R.string.interest_beach_days,
        "বিচ ডে" to R.string.interest_beach_days,
        "बीच डे" to R.string.interest_beach_days,

        "Picnics" to R.string.interest_picnics,
        "পিকনিক" to R.string.interest_picnics,
        "पिकनिक" to R.string.interest_picnics
    )

    // 2) Group by resId, pick one rawName per interest
    val availableInterests = remember {
        interestNameToResource
            .entries
            .groupBy({ it.value }, { it.key })    // Map<resId, List<rawNames>>
            .map { (_, rawNames) -> rawNames.first() }
    }

    // Track which are selected
    val localInterests = remember {
        mutableStateListOf<Interest>().apply { addAll(tempProfile.interests) }
    }
    val maxSelections = 9

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.section_interests),
            color = Color(0xFFFF6F00),
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            availableInterests.forEach { rawName ->
                val resId = interestNameToResource.getValue(rawName)
                val label = stringResource(resId)
                val isSelected = localInterests.any { it.name == rawName }
                val canToggleOn = localInterests.size < maxSelections

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            localInterests.removeAll { it.name == rawName }
                        } else if (canToggleOn) {
                            localInterests.add(Interest(name = rawName, emoji = ""))
                        }
                    },
                    enabled = isSelected || canToggleOn,
                    label = { Text(label, color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF6F00),
                        selectedLabelColor     = Color.White,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.3f),
                        disabledLabelColor     = Color.LightGray
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        ButtonRow(
            onSave   = { onSave(tempProfile.copy(interests = localInterests.toList())) },
            onCancel = onCancel
        )
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
                Text(text = label, color = Color(0xFFFF6F00), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 10.sp,
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
        Text(text = label, color = Color.White, fontSize = 10.sp)
    }
}

@Composable
fun PostItemInProfile(post: Post) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF333333)),
        shape  = RoundedCornerShape(4.dp)
    ) {
        var showVideoDialog by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // — 1) TEXT —
            post.contentText
                .takeIf { !it.isNullOrBlank() }
                ?.let {
                    Text(it, color = Color.White, fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                }

            // — 1.5) CHECK-IN LOCATION —
            post.checkIn
                .takeIf { it?.placeId?.isNotBlank() == true }
                ?.let { checkIn ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Checked in at",
                            tint = Color(0xFFFF6F00),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = checkIn.name
                                .takeIf { it.isNotBlank() }
                                ?: checkIn.address,               // fallback to address if name is blank
                            color = Color(0xFFFF6F00),
                            fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

            // — 2) MEDIA —
            when (post.mediaType.orEmpty().lowercase(Locale.ROOT)) {
                "photo", "image" -> {
                    AsyncImage(
                        model        = post.mediaUrl,
                        contentScale = ContentScale.Crop,
                        placeholder  = painterResource(R.drawable.local_placeholder),
                        error        = painterResource(R.drawable.local_placeholder),
                        contentDescription = "Photo post",
                        modifier     = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp, max = 300.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                }
                "video" -> {
                    // ② Thumbnail + play button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black)
                            .clickable { showVideoDialog = true }
                    ) {
                        AsyncImage(
                            model        = post.mediaThumb ?: post.mediaUrl,
                            contentScale = ContentScale.Crop,
                            modifier     = Modifier.matchParentSize(),
                            contentDescription = "Video thumbnail"
                        )
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.Center)
                        )
                    }
                    Spacer(Modifier.height(6.dp))

                    // ③ Fullscreen cache-backed dialog
                    if (showVideoDialog) {
                        CachedFullscreenVideoPlayer(
                            uri       = Uri.parse(post.mediaUrl),
                            onDismiss = { showVideoDialog = false }
                        )
                    }
                }
                "voice" -> {
                    post.mediaUrl?.let { VoicePlayer(url = it) }
                    Spacer(Modifier.height(6.dp))
                }
                else -> { /* no media */ }
            }

            // — 3) UP / DOWN VOTES —
            Row {
                Text("Upvotes: ${post.upvotes}", color = Color(0xFFFFBF00), fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                Text("Downvotes: ${post.downvotes}", color = Color(0xFFFF6F00), fontSize = 10.sp)
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
            Text(text = label, color = Color.White, fontSize = 9.sp,
                fontWeight = FontWeight.Bold)
        }
        Text(text = displayText, fontSize = 9.sp, color = if (value == -1) Color.Gray else Color.White)
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

@Composable
fun RatingBar(rating: Double, ratingCount: Int) {
    val starSize = 26.dp
    val fullStars = kotlin.math.floor(rating).toInt()
    val fraction = rating - fullStars
    val orange = Color(0xFFFF6F00)

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Full stars
        repeat(fullStars) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = orange,
                modifier = Modifier.size(starSize)
            )
        }
        // Fractional star
        if (fraction > 0) {
            Box(modifier = Modifier.size(starSize)) {
                // Outline for the fractional star
                Icon(
                    imageVector = Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = orange,
                    modifier = Modifier.fillMaxSize()
                )
                // Filled portion of the fractional star
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = orange,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RectangleShape) // Clip to a rectangle
                        .fractionalClip(fraction.toFloat()) // Custom modifier to clip fraction
                        .align(Alignment.CenterStart)
                )
            }
        }
        // Spacer and rating text
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = String.format("%.2f (%d)", rating, ratingCount),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

// Custom modifier to clip the icon to a fraction of its width
fun Modifier.fractionalClip(fraction: Float) = this.then(
    Modifier.drawWithContent {
        val width = size.width * fraction
        clipRect(right = width) {
            this@drawWithContent.drawContent()
        }
    }
)

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
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        if (durationInSeconds.value > 0) {
            Text(
                text = "${elapsedTime}s / ${durationInSeconds.value}s",
                color = Color.Gray,
                fontSize = 10.sp,
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
        Text(label, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
        Text(displayText, fontSize = 9.sp, color = if (value == -1) Color.Gray else Color.White)
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
    val userRef = FirebaseRefs.db.getReference("users").child(currentUserId)

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
        label = stringResource(R.string.marriage_timeline_label) + " ",
        value = profile.marriageTimeline,
        icon = Icons.Default.Schedule
    )
    ProfileDetailRow(
        label = stringResource(R.string.matrimony_relocation_preference) + " ",
        value = profile.relocationPreference,
        icon = Icons.Default.Map
    )
    ProfileDetailRow(
        label = stringResource(R.string.post_marriage_career_plan_label) + " ",
        value = profile.postMarriageCareerPlan,
        icon = Icons.Default.Work
    )
    ProfileDetailRow(
        label = stringResource(R.string.matrimony_traditional_vs_liberal) + " ",
        value = profile.traditionalVsLiberal,
        icon = Icons.Default.HowToVote // or some suitable icon
    )
    ProfileDetailRow(
        label = stringResource(R.string.matrimony_father_occupation) + " ",
        value = profile.fatherOccupation,
        icon = Icons.Default.Person
    )
    ProfileDetailRow(
        label = stringResource(R.string.matrimony_mother_occupation) + " ",
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