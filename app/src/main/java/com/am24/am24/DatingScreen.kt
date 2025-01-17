@file:OptIn(ExperimentalMaterialApi::class, ExperimentalMaterialApi::class,
    ExperimentalLayoutApi::class
)

package com.am24.am24

import DatingViewModel
import android.util.Log
import androidx.compose.foundation.*
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.LocationCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.sqrt

data class SwipeData(
    val liked: Boolean = false,
    val timestamp: Long = 0L
)

/**
 * Main “DatingScreen” with:
 *   - a search bar & refresh button
 *   - listing profiles via a “skip filters if search is non-empty” logic
 *   - match popups
 */
/**
 * Main DatingScreen with swipe counter and info overlay beside the Filters button.
 */
@OptIn(ExperimentalMaterialApi::class)
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

    val filters by datingViewModel.datingFilters.collectAsState()
    val filteredProfiles by datingViewModel.filteredProfiles.collectAsState()
    val isLoading by datingViewModel.isLoading.collectAsState()

    val remainingSwipes = remember { mutableStateOf(50) } // Swipe counter
    val coroutineScope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)

    // Save Filters Functionality
    val saveFilters: () -> Unit = {
        datingViewModel.updateDatingFilters(filters)
        datingViewModel.refreshFilteredProfiles()
        coroutineScope.launch { bottomSheetState.hide() } // Close the bottom sheet
    }


    ModalBottomSheetLayout(
        sheetState = bottomSheetState,
        sheetContent = {
            FiltersOverlay(
                ageRange = filters.ageStart..filters.ageEnd,
                onAgeRangeChange = { range ->
                    datingViewModel.updateDatingFilters(filters.copy(ageStart = range.start, ageEnd = range.endInclusive))
                },
                maxDistance = filters.distance,
                onDistanceChange = { distance ->
                    datingViewModel.updateDatingFilters(filters.copy(distance = distance))
                },
                selectedGenders = filters.gender.split(",").toSet(),
                onGenderChange = { genders ->
                    datingViewModel.updateDatingFilters(filters.copy(gender = genders.joinToString(",")))
                },
                selectedCommunity = filters.community,
                onCommunityChange = { community ->
                    datingViewModel.updateDatingFilters(filters.copy(community = community))
                },
                selectedReligion = filters.religion,
                onReligionChange = { religion ->
                    datingViewModel.updateDatingFilters(filters.copy(religion = religion))
                },
                selectedCaste = filters.caste,
                onCasteChange = { caste ->
                    datingViewModel.updateDatingFilters(filters.copy(caste = caste))
                },
                selectedHighSchool = filters.highSchool,
                onHighSchoolChange = { highSchool ->
                    datingViewModel.updateDatingFilters(filters.copy(highSchool = highSchool))
                },
                selectedCollege = filters.college,
                onCollegeChange = { college ->
                    datingViewModel.updateDatingFilters(filters.copy(college = college))
                },
                selectedPostGrad = filters.postGrad,
                onPostGradChange = { postGrad ->
                    datingViewModel.updateDatingFilters(filters.copy(postGrad = postGrad))
                },
                onSaveFilters = saveFilters,
                onCancel = { coroutineScope.launch { bottomSheetState.hide() } }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { coroutineScope.launch { bottomSheetState.show() } },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00)),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Filters", color = Color.White)
                }

                // Info Overlay
                InfoOverlay()

                // Swipe Counter
                SwipeCounter(remainingSwipes.value)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFF6F00))
                    }
                } else {
                    if (filteredProfiles.isEmpty()) {
                        NoMoreProfilesScreen()
                    } else {
                        DatingScreenContent(
                            navController = navController,
                            geoFire = geoFire,
                            profiles = filteredProfiles,
                            profileViewModel = profileViewModel,  // Passing profileViewModel
                            postViewModel = postViewModel,        // Passing postViewModel
                            onSwipeRight = {
                                if (remainingSwipes.value > 0) remainingSwipes.value -= 1
                            },
                            onSwipeLeft = {
                                if (remainingSwipes.value > 0) remainingSwipes.value -= 1
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun FiltersOverlay(
    ageRange: IntRange,
    onAgeRangeChange: (IntRange) -> Unit,
    maxDistance: Int,
    onDistanceChange: (Int) -> Unit,
    selectedGenders: Set<String>,
    onGenderChange: (Set<String>) -> Unit,
    selectedCommunity: String,
    onCommunityChange: (String) -> Unit,
    selectedReligion: String,
    onReligionChange: (String) -> Unit,
    selectedCaste: String,
    onCasteChange: (String) -> Unit,
    selectedHighSchool: String,
    onHighSchoolChange: (String) -> Unit,
    selectedCollege: String,
    onCollegeChange: (String) -> Unit,
    selectedPostGrad: String,
    onPostGradChange: (String) -> Unit,
    onSaveFilters: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(16.dp)
    ) {
        // Save Button at the top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Filters",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Button(
                onClick = {
                    onSaveFilters() // Persist changes to ViewModel
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00)),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Save", color = Color.Black)
            }
        }

        LazyColumn {
            // Basic Filters Section
            item {
                FilterSectionTitle(title = "Basic Filters")
                Spacer(modifier = Modifier.height(8.dp))

                // Gender Selection
                Text("Gender Preference:", color = Color.White)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("Male", "Female").forEach { gender ->
                        Button(
                            onClick = {
                                val updatedGenders = if (selectedGenders.contains(gender)) {
                                    selectedGenders - gender
                                } else {
                                    selectedGenders + gender
                                }
                                onGenderChange(updatedGenders)
                            },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (selectedGenders.contains(gender)) Color(0xFFFF6000) else Color(0xFF1A1A1A)
                            ),
                            border = BorderStroke(2.dp, if (selectedGenders.contains(gender)) Color(0xFFFF6000) else Color.Gray),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.padding(vertical = 4.dp).height(48.dp)
                        ) {
                            Text(gender, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Age Range Slider
                Text("Age Range: ${ageRange.start} - ${ageRange.endInclusive}", color = Color.White)
                RangeSlider(
                    value = ageRange.start.toFloat()..ageRange.endInclusive.toFloat(),
                    onValueChange = { range ->
                        onAgeRangeChange(range.start.roundToInt()..range.endInclusive.roundToInt())
                    },
                    valueRange = 18f..100f,
                    steps = 82,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF6000),
                        activeTrackColor = Color(0xFFFF6000),
                        inactiveTrackColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Max Distance Slider
                Text("Max Distance: ${maxDistance}km", color = Color.White)
                Slider(
                    value = maxDistance.toFloat(),
                    onValueChange = { onDistanceChange(it.roundToInt()) },
                    valueRange = 0f..100f,
                    steps = 10,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF6000),
                        activeTrackColor = Color(0xFFFF6000),
                        inactiveTrackColor = Color.Gray
                    )
                )
            }

            // Section: Education
            item {
                Spacer(modifier = Modifier.height(24.dp))
                FilterSectionTitle(title = "Education")

                Spacer(modifier = Modifier.height(8.dp))

                // High School
                DropdownFilter(
                    label = "High School",
                    options = listOf("School A", "School B", "School C"),
                    selectedOption = selectedHighSchool,
                    onOptionChange = onHighSchoolChange
                )

                Spacer(modifier = Modifier.height(8.dp))

                // College
                DropdownFilter(
                    label = "College",
                    options = listOf("College A", "College B", "College C"),
                    selectedOption = selectedCollege,
                    onOptionChange = onCollegeChange
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Post Grad
                DropdownFilter(
                    label = "Post Grad",
                    options = listOf("PostGrad A", "PostGrad B", "PostGrad C"),
                    selectedOption = selectedPostGrad,
                    onOptionChange = onPostGradChange
                )
            }

            // Section: Preferences
            item {
                Spacer(modifier = Modifier.height(24.dp))
                FilterSectionTitle(title = "Preferences")

                Spacer(modifier = Modifier.height(8.dp))

                // Community, Religion
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DropdownFilter(
                        label = "Community",
                        options = listOf("Community A", "Community B", "Community C"),
                        selectedOption = selectedCommunity,
                        onOptionChange = onCommunityChange
                    )

                    DropdownFilter(
                        label = "Religion",
                        options = listOf("Hindu", "Muslim", "Christian", "Other"),
                        selectedOption = selectedReligion,
                        onOptionChange = onReligionChange
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Caste
                Row(modifier = Modifier.fillMaxWidth()) {
                    DropdownFilter(
                        label = "Caste",
                        options = listOf("Caste 1", "Caste 2", "Caste 3"),
                        selectedOption = selectedCaste,
                        onOptionChange = onCasteChange
                    )
                }
            }
        }
    }
}


@Composable
fun FilterSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFFF6F00)
    )
}

@Composable
fun DropdownFilter(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val allOptions = listOf("Clear Selection") + options // Add "Clear Selection" option

    Column {
        Text(label, color = Color.White, fontSize = 14.sp)
        Box {
            Button(
                onClick = { expanded = !expanded },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (selectedOption.isNotBlank()) Color(0xFFFF6000) else Color(0xFF1A1A1A)
                ),
                border = BorderStroke(2.dp, Color(0xFFFF6000)),
                shape = RoundedCornerShape(50), // Rounded button
                modifier = Modifier.padding(vertical = 4.dp).height(48.dp)
            ) {
                Text(
                    text = selectedOption.ifBlank { "Select $label" },
                    color = Color.White
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.Black)
            ) {
                allOptions.forEach { option ->
                    DropdownMenuItem(
                        onClick = {
                            if (option == "Clear Selection") {
                                onOptionChange("")
                            } else {
                                onOptionChange(option)
                            }
                            expanded = false
                        }
                    ) {
                        Text(option, color = Color.White)
                    }
                }
            }
        }
    }
}


@Composable
fun NoMoreProfilesScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No more profiles available.",
            color = Color.White,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "Please adjust your filters using the 'Filters' button above.",
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/**
 * The core “swipeable” content for showing the next profile.
 */
@Composable
fun DatingScreenContent(
    navController: NavController,
    geoFire: GeoFire,
    profileViewModel: ProfileViewModel,
    postViewModel: PostViewModel,
    profiles: List<Profile>,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit
) {
    var currentProfileIndex by remember { mutableStateOf(0) }

    if (profiles.isEmpty() || currentProfileIndex >= profiles.size) {
        NoMoreProfilesScreen()
    } else {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val currentProfile = profiles[currentProfileIndex]

        var userDistance by remember { mutableStateOf<Float?>(null) }

        // Calculate distance between current user and the profile being viewed.
        LaunchedEffect(currentProfile) {
            userDistance = calculateDistance(currentUserId, currentProfile.userId, geoFire)
        }

        // If distance is available, display the profile card.
        userDistance?.let { distance ->
            DatingProfileCard(
                profile = currentProfile,
                onSwipeRight = {
                    onSwipeRight()
                    handleSwipeRight(currentUserId, currentProfile.userId, profileViewModel)
                    if (currentProfileIndex + 1 < profiles.size) {
                        currentProfileIndex++
                    } else {
                        currentProfileIndex = profiles.size
                    }
                },
                onSwipeLeft = {
                    onSwipeLeft()
                    handleSwipeLeft(currentUserId, currentProfile.userId)
                    if (currentProfileIndex + 1 < profiles.size) {
                        currentProfileIndex++
                    } else {
                        currentProfileIndex = profiles.size
                    }
                },
                navController = navController,
                userDistance = distance,
                postViewModel = postViewModel
            )
        }
    }
}


/**
 * A single “card” for the user: the photo with overlays, collapsible, etc.
 */
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.Black)
        ) {
            // 1) Photo + Overlays
            item {
                PhotoWithDynamicOverlays(profile = profile, userDistance = userDistance)
            }

            // 2) Collapsible
            item {
                ProfileCollapsibleSectionsAll(profile)
            }

            // 3) Featured
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

            // 5) View more
            if (remainingPosts.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { /* Show more posts screen perhaps */ },
                            colors = ButtonDefaults.buttonColors(Color(0xFFFF6F00))
                        ) {
                            Text("View More Posts", color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Info Overlay Component.
 */
@Composable
fun InfoOverlay() {
    var showInfo by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showInfo = true }) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        if (showInfo) {
            AlertDialog(
                onDismissRequest = { showInfo = false },
                title = {
                    Text(
                        text = "Dating Screen Features",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text("- Swipe Right to Like")
                        Text("- Swipe Left to Skip")
                        Text("- Scroll Down for Profile Details")
                        Text("- Metrics: SPS, Rating, etc.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfo = false }) {
                        Text("Got it")
                    }
                }
            )
        }
    }
}

/**
 * Swipe Counter Component.
 */
@Composable
fun SwipeCounter(remainingSwipes: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Remaining Swipes: $remainingSwipes",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6F00)
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = remainingSwipes / 50f,
            color = Color(0xFFFF6F00),
            backgroundColor = Color.Gray,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
    }
}


@Composable
fun PhotoWithTwoOverlays(
    profile: Profile,
    userDistance: Float
) {
    val photoUrls = listOfNotNull(profile.profilepicUrl) + profile.optionalPhotoUrls
    var currentPhotoIndex by remember { mutableStateOf(0) }

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
                                currentPhotoIndex = (currentPhotoIndex + 1) % photoUrls.size // Next photo
                            } else {
                                currentPhotoIndex = (currentPhotoIndex - 1 + photoUrls.size) % photoUrls.size // Previous photo
                            }
                        }
                    }
                )
            }
    ) {
        // Display the current photo
        if (photoUrls.isNotEmpty()) {
            AsyncImage(
                model = photoUrls[currentPhotoIndex],
                contentDescription = "Profile Photo",
                placeholder = painterResource(R.drawable.local_placeholder),
                error = painterResource(R.drawable.local_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp)
            )
        }

        // Top overlay (always dynamic based on the photo index)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.TopCenter)
        ) {
            when (currentPhotoIndex) {
                0 -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RatingBar(rating = profile.averageRating, ratingCount = profile.numberOfRatings)
                        FlashyVibeScore(scorePercent = vibeScorePercent)
                    }
                }
                1 -> {
                    Text(
                        text = "Bio",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
                2 -> {
                    Text(
                        text = "Interests",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
                3 -> {
                    Text(
                        text = "Statistics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Bottom overlay (always dynamic based on the photo index)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .align(Alignment.BottomCenter)
        ) {
            when (currentPhotoIndex) {
                0 -> {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${profile.username}, $age",
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            profile.community.takeIf { it.isNotBlank() }?.let {
                                TagBox(it)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            profile.city.takeIf { it.isNotBlank() }?.let {
                                TagBox(it)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            profile.religion.takeIf { it.isNotBlank() }?.let { TagBox(it) }
                        }
                    }
                }
                1 -> {
                    Text(
                        text = profile.bio ?: "No bio available",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp
                    )
                }
                2 -> {
                    Column {
                        if (profile.interests.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                profile.interests.forEach { TagBox("${it.emoji} ${it.name}") }
                            }
                        }
                        profile.hometown?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Locality: $it",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                3 -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "SPS: ${profile.sps ?: "N/A"}",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Kolkata Ranking: ${profile.am24RankingHometown ?: "N/A"}",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}



/** TagBox is unchanged (for your #tags). */
@Composable
fun TagBox(text: String) {
    if (text.isNotBlank()) {
        Box(
            modifier = Modifier
                .padding(horizontal = 1.dp)
                .background(Color.Black, RoundedCornerShape(4.dp))
                .border(
                    BorderStroke(1.dp, Color(0xFFFF6F00)),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(text = text, color = Color.White, fontSize = 13.sp)
        }
    }
}

/** The “flashy vibe” gradient text. Unchanged. */
@Composable
fun FlashyVibeScore(scorePercent: Int) {
    val displayPercent = scorePercent.coerceIn(0, 100)
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
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
    }
}

/**
 * The collapsible sections: Basic Info, Preferences, Lifestyle, Interests
 * without edit icons for the DatingScreen usage.
 * We assume you do not want the “edit” logic from ProfileScreen here.
 */
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

/** CollapsibleSection that does NOT show edit icon here in the DatingScreen. */
@Composable
fun CollapsibleSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    // Header row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = Color.White, shape = CircleShape)
            .background(Color.Black)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = Color(0xFFFF6F00), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f)
        )
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
        content()
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
        if (otherUserSwipeData?.liked == true) {
            // It's a match
            val currentUserMatchesRef = database.getReference("matches/$currentUserId/$otherUserId")
            val otherUserMatchesRef = database.getReference("matches/$otherUserId/$currentUserId")
            currentUserMatchesRef.setValue(timestamp)
            otherUserMatchesRef.setValue(timestamp)

            // Trigger Match Pop-Up
            profileViewModel.triggerMatchPopUp(currentUserId, otherUserId)
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

/**
 * fetchExcludedUsers => matched or liked recently
 */
suspend fun fetchExcludedUsers(currentUserId: String): Set<String> {
    val database = FirebaseDatabase.getInstance()
    val matchesRef = database.getReference("matches/$currentUserId")
    val likesRef = database.getReference("likesGiven/$currentUserId")
    val oneWeekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L

    return withContext(Dispatchers.IO) {
        val excludedIds = mutableSetOf<String>()
        matchesRef.get().addOnSuccessListener { snapshot ->
            snapshot.children.forEach { excludedIds.add(it.key!!) }
        }.await()
        likesRef.get().addOnSuccessListener { snapshot ->
            snapshot.children.forEach { snap ->
                val ts = snap.getValue(Long::class.java) ?: 0L
                if (ts >= oneWeekAgo) {
                    excludedIds.add(snap.key!!)
                }
            }
        }.await()
        excludedIds
    }
}

/** Haversine + getUserLocation => same as your code */
fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val earthRadius = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadius * c).toFloat()
}

suspend fun calculateDistance(userId1: String, userId2: String, geoFire: GeoFire): Float? = withContext(Dispatchers.Default) {
    val loc1 = getUserLocation(userId1, geoFire)
    val loc2 = getUserLocation(userId2, geoFire)
    if (loc1 != null && loc2 != null) {
        haversine(loc1.latitude, loc1.longitude, loc2.latitude, loc2.longitude)
    } else null
}

suspend fun getUserLocation(userId: String, geoFire: GeoFire): GeoLocation? =
    suspendCancellableCoroutine { continuation ->
        geoFire.getLocation(userId, object : LocationCallback {
            override fun onLocationResult(key: String?, location: GeoLocation?) {
                continuation.resume(location)
            }
            override fun onCancelled(databaseError: DatabaseError) {
                continuation.resumeWithException(databaseError.toException())
            }
        })
    }

/** Standard “MatchPopUp” remains unchanged from your code. */
@Composable
fun MatchPopUp(
    currentUserProfilePic: String,
    otherUserProfilePic: String,
    onChatClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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

@Composable
fun PhotoWithDynamicOverlays(
    profile: Profile,
    userDistance: Float
) {
    PhotoWithTwoOverlays(
        profile = profile,
        userDistance = userDistance
    )
}
