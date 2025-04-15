@file:OptIn(ExperimentalMaterialApi::class, ExperimentalMaterialApi::class,
    ExperimentalLayoutApi::class
)

package com.am24.am24

import DatingViewModel
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Swipe
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.LocationCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*

// Assuming R is imported from your project's resources
import com.am24.am24.R

data class SwipeData(
    val liked: Boolean = false,
    val timestamp: Long = 0L
)

/**
 * Main DatingScreen with swipe counter and info overlay beside the Filters button.
 */
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
    val matchPopUpState by profileViewModel.matchPopUpState.collectAsState()

    var excludedUserIds by remember { mutableStateOf(emptySet<String>()) }
    val remainingSwipes = remember { mutableStateOf(0) }
    var swipesLoaded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var forcedProfile by remember { mutableStateOf<Profile?>(null) }

    // State to control overlay visibility (e.g. for the swipe direction overlays)
    var visible by remember { mutableStateOf(true) }

    // Show overlays when first visited
    LaunchedEffect(Unit) {
        visible = true
        delay(5000) // Showing for 5 seconds (adjust as needed)
        visible = false
    }

    val bottomSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )

    // Load excluded users and swipes
    LaunchedEffect(Unit) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId != null) {
            excludedUserIds = fetchExcludedUsers(currentUserId)
            profileViewModel.fetchCurrentUserProfile()
            val loadedSwipes = loadAndResetSwipesDaily(currentUserId)
            remainingSwipes.value = loadedSwipes
            swipesLoaded = true  // Mark that swipes are loaded
            datingViewModel.refreshFilteredProfiles()
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            val snapshot = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(initialQuery)
                .get()
                .await()
            forcedProfile = snapshot.getValue(Profile::class.java)
        }
    }

    // Choose profiles to display (forced profile if query provided, otherwise filtered)
    val displayedProfiles = if (initialQuery.isNotBlank()) {
        forcedProfile?.let { listOf(it) } ?: emptyList()
    } else {
        filteredProfiles.filter { it.userId !in excludedUserIds }
    }

    // Local lambdas for the new buttons. Here we’re using the first profile in the list as the “current” one.
    val handleSuperSwipe = {
        if (remainingSwipes.value > 0 && displayedProfiles.isNotEmpty()) {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserId != null) {
                // Example: perform a “right swipe” action with extra logic (e.g. boosted match)
                handleSwipeRight(currentUserId, displayedProfiles.first().userId, profileViewModel)
                remainingSwipes.value--
                updateSwipesInFirebase(remainingSwipes.value)
                Log.d("DatingScreen", "Super Swipe performed on ${displayedProfiles.first().userId}")
            }
        }
    }

    val handleForceMatch = {
        if (remainingSwipes.value > 0 && displayedProfiles.isNotEmpty()) {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            val targetUser = displayedProfiles.first()
            if (currentUserId != null) {
                // Example: directly trigger a match popup bypassing the usual swipe logic
                profileViewModel.triggerMatchPopUp(currentUserId, targetUser.userId)
                remainingSwipes.value--
                updateSwipesInFirebase(remainingSwipes.value)
                Log.d("DatingScreen", "Force Match triggered for ${targetUser.userId}")
            }
        }
    }

    ModalBottomSheetLayout(
        sheetState = bottomSheetState,
        sheetContent = {
            FiltersOverlay(
                ageRange = filters.ageStart..filters.ageEnd,
                onAgeRangeChange = { range ->
                    datingViewModel.updateDatingFilters(
                        filters.copy(ageStart = range.start, ageEnd = range.endInclusive)
                    )
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
                onCommunityChange = { community -> datingViewModel.updateDatingFilters(filters.copy(community = community)) },
                selectedReligion = filters.religion,
                onReligionChange = { religion -> datingViewModel.updateDatingFilters(filters.copy(religion = religion)) },
                selectedCaste = filters.caste,
                onCasteChange = { caste -> datingViewModel.updateDatingFilters(filters.copy(caste = caste)) },
                selectedHighSchool = filters.highSchool,
                onHighSchoolChange = { hs -> datingViewModel.updateDatingFilters(filters.copy(highSchool = hs)) },
                selectedCollege = filters.college,
                onCollegeChange = { college -> datingViewModel.updateDatingFilters(filters.copy(college = college)) },
                selectedPostGrad = filters.postGrad,
                onPostGradChange = { pg -> datingViewModel.updateDatingFilters(filters.copy(postGrad = pg)) },
                onSaveFilters = {
                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                    datingViewModel.updateDatingFilters(filters)
                    datingViewModel.refreshFilteredProfiles()
                    if (currentUserId != null) {
                        coroutineScope.launch {
                            excludedUserIds = fetchExcludedUsers(currentUserId)
                        }
                    }
                    coroutineScope.launch { bottomSheetState.hide() }
                },
                onCancel = { coroutineScope.launch { bottomSheetState.hide() } }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // --- Top App Bar Row Modified ---
            // Removed the SwipeCounter and added two new buttons for Super Swipe and Force Match.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { coroutineScope.launch { bottomSheetState.show() } },
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.filters),
                        tint = Color(0xFFFF6F00), // Orange color
                        modifier = Modifier.size(54.dp)
                    )
                }
                // New buttons for extra swipe actions
                Row {
                    Button(
                        onClick = { handleSuperSwipe() },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00)),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(stringResource(R.string.super_swipe), color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { handleForceMatch() },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00)),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(stringResource(R.string.force_match), color = Color.White)
                    }
                }
            }
            // -------------------------------------------------

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFF6F00))
                    }
                } else {
                    if (displayedProfiles.isEmpty()) {
                        NoMoreProfilesScreen()
                    } else {
                        DatingScreenContent(
                            navController = navController,
                            geoFire = geoFire,
                            profiles = displayedProfiles,
                            profileViewModel = profileViewModel,
                            postViewModel = postViewModel,
                            onSwipeRight = {
                                if (remainingSwipes.value > 0) {
                                    remainingSwipes.value--
                                    updateSwipesInFirebase(remainingSwipes.value)
                                }
                            },
                            onSwipeLeft = {
                                if (remainingSwipes.value > 0) {
                                    remainingSwipes.value--
                                    updateSwipesInFirebase(remainingSwipes.value)
                                }
                            }
                        )
                    }
                }
                // Existing swipe direction overlays (if still needed)
                if (visible) {
                    // Left arrow overlay
                    Surface(
                        modifier = Modifier
                            .size(100.dp)
                            .align(Alignment.CenterStart),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Red.copy(alpha = 0.2f),
                        elevation = 4.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = stringResource(R.string.swipe_left),
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    // Right arrow overlay
                    Surface(
                        modifier = Modifier
                            .size(100.dp)
                            .align(Alignment.CenterEnd),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Green.copy(alpha = 0.2f),
                        elevation = 4.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = stringResource(R.string.swipe_right),
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    // Animated "Swipe!" text overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(durationMillis = 500)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 500)),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(
                            text = stringResource(R.string.swipe),
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // --- Overlay for When Swipes Are Over ---
    // This overlay covers the screen if remainingSwipes reaches zero,
    // intercepts all touches, and displays a "No more swipes available" message.
    if (swipesLoaded && remainingSwipes.value <= 0) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { /* Consume all touches to disallow swipes */ })
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No more swipes available",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // Existing MatchPopUp code, etc.
    matchPopUpState?.let { (currentUserProfile, matchedUserProfile) ->
        MatchPopUp(
            currentUserProfilePic = currentUserProfile.profilepicUrl.orEmpty(),
            otherUserProfilePic = matchedUserProfile.profilepicUrl.orEmpty(),
            onChatClick = {
                navController.navigate("chat/${matchedUserProfile.userId}")
                profileViewModel.clearMatchPopUp()
            },
            onClose = { profileViewModel.clearMatchPopUp() },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
        )
    }
}

/**
 * Load swipes from Firebase and reset them to 25 if a new day has started.
 */
suspend fun loadAndResetSwipesDaily(userId: String): Int {
    val swipesRef = FirebaseDatabase.getInstance().getReference("users/$userId/swipesInfo")
    val snapshot = swipesRef.get().await()
    var remainingSwipes = 25
    var lastResetDayOfYear = -1
    snapshot.child("remainingSwipes").getValue(Int::class.java)?.let {
        remainingSwipes = it
    }
    snapshot.child("lastResetDayOfYear").getValue(Int::class.java)?.let {
        lastResetDayOfYear = it
    }
    val calendar = Calendar.getInstance()
    val todayDayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
    if (todayDayOfYear != lastResetDayOfYear) {
        remainingSwipes = 25
        lastResetDayOfYear = todayDayOfYear
    }
    swipesRef.child("remainingSwipes").setValue(remainingSwipes)
    swipesRef.child("lastResetDayOfYear").setValue(lastResetDayOfYear)
    return remainingSwipes
}

/** Updates the user's remainingSwipes in Firebase. */
fun updateSwipesInFirebase(newSwipesCount: Int) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val swipesRef = FirebaseDatabase.getInstance().getReference("users/$userId/swipesInfo")
    swipesRef.child("remainingSwipes").setValue(newSwipesCount)
}

@OptIn(ExperimentalMaterialApi::class)
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
                text = stringResource(R.string.filters),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Button(
                onClick = {
                    onSaveFilters()
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00)),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(48.dp)
            ) {
                Text(stringResource(R.string.save), color = Color.Black)
            }
        }

        LazyColumn {
            // Basic Filters Section
            item {
                FilterSectionTitle(title = stringResource(R.string.basic_filters))
                Spacer(modifier = Modifier.height(8.dp))

                // Gender Selection
                Text(stringResource(R.string.gender_preference), color = Color.White)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf((stringResource(R.string.male_option)), (stringResource(R.string.female_option))).forEach { gender ->
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
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .height(48.dp)
                        ) {
                            Text(gender, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Age Range Slider
                Text(
                    text = stringResource(R.string.age_range, ageRange.start, ageRange.endInclusive),
                    color = Color.White
                )
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
                Text(
                    text = stringResource(R.string.max_distance, maxDistance),
                    color = Color.White
                )
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
                FilterSectionTitle(title = stringResource(R.string.education))

                Spacer(modifier = Modifier.height(8.dp))

                // High School
                DropdownFilter(
                    label = stringResource(R.string.high_school),
                    options = listOf("School A", "School B", "School C"),
                    selectedOption = selectedHighSchool,
                    onOptionChange = onHighSchoolChange
                )

                Spacer(modifier = Modifier.height(8.dp))

                // College
                DropdownFilter(
                    label = stringResource(R.string.college),
                    options = listOf("College A", "College B", "College C"),
                    selectedOption = selectedCollege,
                    onOptionChange = onCollegeChange
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Post Grad
                DropdownFilter(
                    label = stringResource(R.string.post_grad),
                    options = listOf("PostGrad A", "PostGrad B", "PostGrad C"),
                    selectedOption = selectedPostGrad,
                    onOptionChange = onPostGradChange
                )
            }

            // Section: Preferences
            item {
                Spacer(modifier = Modifier.height(24.dp))
                FilterSectionTitle(title = stringResource(R.string.preferences))

                Spacer(modifier = Modifier.height(8.dp))

                // Community, Religion
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DropdownFilter(
                        label = stringResource(R.string.community),
                        options = listOf("Community A", "Community B", "Community C"),
                        selectedOption = selectedCommunity,
                        onOptionChange = onCommunityChange
                    )

                    DropdownFilter(
                        label = stringResource(R.string.religion),
                        options = listOf("Hindu", "Muslim", "Christian", "Other"),
                        selectedOption = selectedReligion,
                        onOptionChange = onReligionChange
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Caste
                Row(modifier = Modifier.fillMaxWidth()) {
                    DropdownFilter(
                        label = stringResource(R.string.caste),
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
    val clearSelectionText = stringResource(R.string.clear_selection) // Resolve string in composable scope
    val allOptions = listOf(clearSelectionText) + options // Add "Clear Selection" option

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
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .height(48.dp)
            ) {
                Text(
                    text = selectedOption.ifBlank { stringResource(R.string.select_label, label) },
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
                            if (option == clearSelectionText) { // Use the resolved string
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
            text = stringResource(R.string.no_more_profiles),
            color = Color.White,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = stringResource(R.string.adjust_filters),
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

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

    // Ensure posts are fetched
    LaunchedEffect(Unit) {
        postViewModel.fetchPosts()
    }

    if (profiles.isEmpty() || currentProfileIndex >= profiles.size) {
        NoMoreProfilesScreen()
    } else {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val currentUserProfile by profileViewModel.currentUserProfile.collectAsState()
        val currentProfile = profiles[currentProfileIndex]

        var userDistance by remember { mutableStateOf<Float?>(null) }
        var aiMatchResult by remember { mutableStateOf<AiMatchCheckResult?>(null) }

        LaunchedEffect(currentProfile.userId) {
            userDistance = calculateDistance(currentUserId, currentProfile.userId, geoFire)
            val ref = FirebaseDatabase.getInstance().getReference("aiMatchCheck/$currentUserId/${currentProfile.userId}")
            val snap = ref.get().await()
            val existing = snap.getValue(AiMatchCheckResult::class.java)
            if (existing != null) {
                aiMatchResult = existing
            } else {
                runAiMatchCheck(
                    coroutineScope = this,
                    currentUserId = currentUserId,
                    currentUserProfile = currentUserProfile!!,
                    otherProfile = currentProfile
                ) { newResult ->
                    aiMatchResult = newResult
                }
            }
        }

        userDistance?.let { distance ->
            DatingProfileCard(
                profile = currentProfile,
                aiMatchResult = aiMatchResult,
                onSwipeRight = {
                    onSwipeRight()
                    handleSwipeRight(currentUserId, currentProfile.userId, profileViewModel)
                    if (currentProfileIndex + 1 < profiles.size) currentProfileIndex++ else currentProfileIndex = profiles.size
                },
                onSwipeLeft = {
                    onSwipeLeft()
                    handleSwipeLeft(currentUserId, currentProfile.userId)
                    if (currentProfileIndex + 1 < profiles.size) currentProfileIndex++ else currentProfileIndex = profiles.size
                },
                navController = navController,
                userDistance = distance,
                postViewModel = postViewModel,
                currentProfile = currentUserProfile
            )
        }
    }
}

// Updated DatingProfileCard
@Composable
fun DatingProfileCard(
    profile: Profile,
    aiMatchResult: AiMatchCheckResult?,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    userDistance: Float,
    navController: NavController,
    postViewModel: PostViewModel,
    currentProfile: Profile?
) {
    val computedAvg = if (profile.numberOfUsersWhoSwiped > 0) {
        profile.numberOfSwipeRights.toDouble() / profile.numberOfUsersWhoSwiped
    } else 0.0
    profile.averageSwipeRightsOnUser = computedAvg

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

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .offset { IntOffset(swipeOffset.roundToInt(), 0) }
            .swipeable(
                state = swipeableState,
                anchors = anchors,
                thresholds = { _, _ -> FractionalThreshold(0.3f) },
                orientation = Orientation.Horizontal
            ),
        backgroundColor = Color.Black,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(3.dp, getLevelBorderColor(profile.averageRating))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            item {
                PhotoWithTwoOverlays(
                    profile = profile,
                    userDistance = userDistance,
                    aiMatchResult = aiMatchResult,
                    currentProfile = currentProfile
                )
            }
            item {
                DatingProfileHeader(
                    profile = profile,
                    userDistance = userDistance,
                    sortedByUpvotes = sortedByUpvotes // Pass the list here
                )
            }
            item {
                ProfileCollapsibleSectionsAll(profile, currentProfile, aiMatchResult)
            }
            if (featuredPosts.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.featured_posts),
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
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { /* Show more posts screen perhaps */ },
                            colors = ButtonDefaults.buttonColors(Color(0xFFFF6F00))
                        ) {
                            Text(stringResource(R.string.view_more_posts), color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun DatingProfileHeader(
    profile: Profile,
    userDistance: Float,
    sortedByUpvotes: List<Post>
) {
    val age = calculateAge(profile.dob)
    val community = profile.community
    val religion = profile.religion
    val caste = profile.caste
    var showPostsOverlay by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                if (community.isNotBlank()) {
                    TagBox(text = community)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (religion.isNotBlank()) {
                    TagBox(text = religion)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (caste.isNotBlank()) {
                    TagBox(text = caste)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (age > 0) stringResource(R.string.name_age, profile.name, age) else profile.name,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(end = 8.dp)
            )
            Button(
                onClick = { showPostsOverlay = true },
                colors = ButtonDefaults.buttonColors(Color(0xFFFF6F00)),
                modifier = Modifier.height(30.dp)
            ) {
                Text(stringResource(R.string.posts), color = Color.White, fontSize = 12.sp)
            }
        }
    }

    if (showPostsOverlay) {
        PostsOverlay(
            posts = sortedByUpvotes,
            onDismiss = { showPostsOverlay = false }
        )
    }
}

@Composable
fun PostsOverlay(posts: List<Post>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("", color = Color.Black) },
        text = {
            if (posts.isEmpty()) {
                Text(stringResource(R.string.no_posts), color = Color.Gray)
            } else {
                LazyColumn {
                    items(posts) { post ->
                        PostItemInProfile(post)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = Color(0xFFFF6F00))
            }
        },
        backgroundColor = Color.Black,
        contentColor = Color.White
    )
}

@Composable
fun PhotoWithTwoOverlays(
    profile: Profile,
    userDistance: Float,
    aiMatchResult: AiMatchCheckResult?,
    currentProfile: Profile? = null
) {
    var currentPhotoIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val photoUrls = listOfNotNull(profile.profilepicUrl) + profile.optionalPhotoUrls

    // Prefetch images with caching
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
            .aspectRatio(0.75f)
            .background(Color.Black)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .pointerInput(photoUrls) {
                detectTapGestures(onTap = { offset ->
                    if (photoUrls.size > 1) {
                        currentPhotoIndex = if (offset.x > size.width / 2)
                            (currentPhotoIndex + 1) % photoUrls.size
                        else
                            (currentPhotoIndex - 1 + photoUrls.size) % photoUrls.size
                    }
                })
            }
    ) {
        if (photoUrls.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photoUrls[currentPhotoIndex])
                    .diskCacheKey(photoUrls[currentPhotoIndex])
                    .memoryCacheKey(photoUrls[currentPhotoIndex])
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.profile_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp)
            )

            // Photo indicators
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

            // Top overlay (distance)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(14.dp, 10.dp)
            ) {
                TagBox(
                    text = stringResource(R.string.max_distance, userDistance.roundToInt()),
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }

            // Interests overlay with fade-to-black gradient on the first photo
            if (currentPhotoIndex == 0 && profile.interests.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val displayedInterests = profile.interests.take(5)
                        displayedInterests.forEach { interest ->
                            Text(
                                text = "${interest.emoji} ${interest.name}",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.no_images), color = Color.White)
            }
        }
    }
}

@Composable
fun TagBox(
    text: String,
    modifier: Modifier = Modifier
) {
    if (text.isNotBlank()) {
        Box(
            modifier = modifier
                .padding(horizontal = 1.dp)
                .background(Color.Black, shape = RoundedCornerShape(4.dp))
                .border(BorderStroke(1.dp, Color(0xFFFF6F00)), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(text = text, color = Color.White, fontSize = 15.sp)
        }
    }
}

@Composable
fun PerformanceMetricsSectionDating(profile: Profile) {
    var showPerformance by rememberSaveable { mutableStateOf(false) }
    CollapsibleSection(
        title = stringResource(R.string.performance_metrics),
        icon = Icons.Default.Assessment,
        isExpanded = showPerformance,
        onToggle = { showPerformance = !showPerformance },
    ) {
        ProfileDetailRow(
            label = stringResource(R.string.matches),
            value = stringResource(R.string.number, profile.matchCount),
            icon = Icons.Default.People
        )
        ProfileDetailRow(
            label = stringResource(R.string.rating),
            value = String.format(LocalContext.current.resources.configuration.locale, "%.2f", profile.averageRating),
            icon = Icons.Default.Star
        )
        ProfileDetailRow(
            label = stringResource(R.string.swipe_right_percentage),
            value = stringResource(R.string.percentage, (profile.averageSwipeRightsOnUser * 100).roundToInt()),
            icon = Icons.Default.Swipe
        )
        ProfileDetailRow(
            label = stringResource(R.string.west_bengal_ranking),
            value = stringResource(R.string.number, profile.am24Ranking),
            icon = Icons.Default.Public
        )

        val cityRank = if (profile.city == "Other")
            profile.am24RankingCustomCity
        else
            profile.am24RankingCity
        if (cityRank > 0) {
            ProfileDetailRow(
                label = stringResource(R.string.city_ranking, profile.city.ifBlank { stringResource(R.string.city) }),
                value = stringResource(R.string.number, cityRank),
                icon = Icons.Default.LocationCity
            )
        }

        val hoodRank = if (profile.hometown == "Other")
            profile.am24RankingCustomHometown
        else
            profile.am24RankingHometown
        if (hoodRank > 0) {
            ProfileDetailRow(
                label = stringResource(R.string.locality_ranking, profile.hometown.ifBlank { stringResource(R.string.locality) }),
                value = stringResource(R.string.number, hoodRank),
                icon = Icons.Default.Home
            )
        }
        ProfileDetailRow(
            label = stringResource(R.string.age_ranking),
            value = stringResource(R.string.number, profile.am24RankingAge),
            icon = Icons.Default.Cake
        )

        if (profile.highSchool.isNotBlank()) {
            ProfileDetailRow(
                label = stringResource(R.string.high_school_ranking, profile.highSchool),
                value = stringResource(R.string.number, profile.am24RankingHighSchool),
                icon = Icons.Default.School
            )
            if (!profile.highSchoolGraduationYear.isNullOrBlank()) {
                ProfileDetailRow(
                    label = stringResource(R.string.high_school_graduation_year, profile.highSchool),
                    value = profile.highSchoolGraduationYear,
                    icon = Icons.Default.School
                )
            }
        }

        if (profile.college.isNotBlank()) {
            ProfileDetailRow(
                label = stringResource(R.string.college_ranking, profile.college),
                value = stringResource(R.string.number, profile.am24RankingCollege),
                icon = Icons.Default.Book
            )
            if (!profile.collegeGraduationYear.isNullOrBlank()) {
                ProfileDetailRow(
                    label = stringResource(R.string.college_graduation_year, profile.college),
                    value = profile.collegeGraduationYear,
                    icon = Icons.Default.Book
                )
            }
        }
    }
}
/**
 * The collapsible sections: Basic Info, Preferences, Lifestyle, Interests.
 */
@Composable
fun ProfileCollapsibleSectionsAll(profile: Profile, currentUserProfile: Profile?, aiMatchResult: AiMatchCheckResult?) {
    var showVoiceBio by rememberSaveable { mutableStateOf(false) }
    var showBasic by rememberSaveable { mutableStateOf(false) }
    var showPreferences by rememberSaveable { mutableStateOf(false) }
    var showLifestyle by rememberSaveable { mutableStateOf(false) }
    var showInterests by rememberSaveable { mutableStateOf(false) }
    var showAiSection by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var currentAiMatchResult by remember { mutableStateOf(aiMatchResult) }
    var showSocialCauses by rememberSaveable { mutableStateOf(false) } // New state for Social Causes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(8.dp)
    ) {
        CollapsibleSection(
            title = stringResource(R.string.compatibility_check),
            icon = Icons.Default.Info,
            isExpanded = showAiSection,
            onToggle = { showAiSection = !showAiSection }
        ) {
            Column {
                if (currentAiMatchResult != null) {
                    ShowAiMatchAnalysis(currentAiMatchResult!!)
                } else {
                    Text(stringResource(R.string.run_analysis), color = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (currentUserProfile != null) {
                            runAiMatchCheck(
                                coroutineScope = coroutineScope,
                                currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@Button,
                                currentUserProfile = currentUserProfile,
                                otherProfile = profile
                            ) { newResult ->
                                currentAiMatchResult = newResult
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00)),
                    modifier = Modifier
                        .height(36.dp)
                ) {
                    Text("Run", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        PerformanceMetricsSectionDating(profile)
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleSection(
            title = stringResource(R.string.bio),
            icon = Icons.Default.Mic,
            isExpanded = showVoiceBio,
            onToggle = { showVoiceBio = !showVoiceBio }
        ) {
            showVoiceBio(profile = profile)
        }
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleSection(
            title = stringResource(R.string.basic_information),
            icon = Icons.Default.Person,
            isExpanded = showBasic,
            onToggle = { showBasic = !showBasic }
        ) {
            BasicInfoSection(profile)
        }
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleSection(
            title = stringResource(R.string.preferences),
            icon = Icons.Default.Favorite,
            isExpanded = showPreferences,
            onToggle = { showPreferences = !showPreferences }
        ) {
            PreferencesSection(profile)
        }
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleSection(
            title = stringResource(R.string.lifestyle_attributes),
            icon = Icons.Default.Nature,
            isExpanded = showLifestyle,
            onToggle = { showLifestyle = !showLifestyle }
        ) {
            LifestyleSection(profile)
        }
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleSection(
            title = stringResource(R.string.interests),
            icon = Icons.Default.Star,
            isExpanded = showInterests,
            onToggle = { showInterests = !showInterests }
        ) {
            InterestsSectionInProfile(profile)
        }
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleSection(
            title = stringResource(R.string.social_causes),
            icon = Icons.Default.Favorite, // Use a suitable icon (VolunteerActivism if available)
            isExpanded = showSocialCauses,
            onToggle = { showSocialCauses = !showSocialCauses }
        ) {
            SocialCausesSection(profile)
        }
    }
}

@Composable
fun ShowAiMatchAnalysis(aiResult: AiMatchCheckResult) {
    Text(
        text = aiResult.summary,
        color = Color.White,
        fontSize = 16.sp,
        modifier = Modifier.padding(8.dp)
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.analyzed_on, formatTime(aiResult.timestamp)),
        color = Color.Gray,
        fontSize = 12.sp
    )
}

fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

@Composable
fun showVoiceBio(profile: Profile) {
    Column {
        if (!profile.voiceNoteUrl.isNullOrEmpty()) {
            VoicePlayer(url = profile.voiceNoteUrl)
            Spacer(modifier = Modifier.height(8.dp))
        }
        ProfileDetailRow(
            label = stringResource(R.string.bio),
            value = profile.bio ?: stringResource(R.string.no_bio_available),
            icon = Icons.Default.BlurOn
        )
    }
}

// ... (Rest of the functions remain unchanged: runAiMatchCheck, calculateExhaustiveCompatibilityScore, etc.)

@Composable
fun CollapsibleSection(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .border(width = 1.dp, color = Color.White, shape = CircleShape)
            .background(Color.Black)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = title,
            tint = Color(0xFFFF6F00),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (isExpanded) {
        Spacer(Modifier.height(4.dp))
        Card(
            backgroundColor = Color(0xFF1A1A1A),
            elevation = 4.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ... (Rest of the functions remain unchanged: handleSwipeRight, handleSwipeLeft, fetchExcludedUsers, etc.)

/** Standard “MatchPopUp” */
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
                    text = stringResource(R.string.its_a_match),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.Center) {
                    AsyncImage(
                        model = currentUserProfilePic,
                        contentDescription = stringResource(R.string.your_profile_picture),
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFFFF6F00), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    AsyncImage(
                        model = otherUserProfilePic,
                        contentDescription = stringResource(R.string.matched_profile_picture),
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
                    Text(stringResource(R.string.chat_now), color = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.close), color = Color.Gray)
                }
            }
        }
    }
}

/**
 * Updated AiMatchCheckResult with new fields.
 */
data class AiMatchCheckResult(
    val summary: String = "",                     // Full text output from the AI
    val totalMatchPercentage: Int = 0,            // Total match percentage extracted from GPT reply
    val compatibilityBreakdown: String = "",      // Detailed breakdown of compatibility score
    val timestamp: Long = 0L                      // When the analysis was done
)

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

    val otherUserTotalSwipesRef = database.getReference("swipesReceived/$otherUserId/$currentUserId")
    otherUserTotalSwipesRef.setValue(true)

    val otherUserProfileRef = database.getReference("users/$otherUserId/numberOfUsersWhoSwiped")
    otherUserProfileRef.get().addOnSuccessListener { snapshot ->
        val currentCount = snapshot.getValue(Double::class.java) ?: 0.0
        otherUserProfileRef.setValue(currentCount + 1)
    }

    val otherUserSwipeRightsRef = database.getReference("users/$otherUserId/numberOfSwipeRights")
    otherUserSwipeRightsRef.get().addOnSuccessListener { snapshot ->
        val currentSwipeRights = snapshot.getValue(Int::class.java) ?: 0
        otherUserSwipeRightsRef.setValue(currentSwipeRights + 1)
    }

    otherUserSwipesRef.get().addOnSuccessListener { snapshot ->
        val otherUserSwipeData = snapshot.getValue(SwipeData::class.java)
        if (otherUserSwipeData?.liked == true) {
            val currentUserMatchesRef = database.getReference("matches/$currentUserId/$otherUserId")
            val otherUserMatchesRef = database.getReference("matches/$otherUserId/$currentUserId")
            currentUserMatchesRef.setValue(timestamp)
            otherUserMatchesRef.setValue(timestamp)

            profileViewModel.triggerMatchPopUp(currentUserId, otherUserId)
        }
    }
}

fun handleSwipeLeft(currentUserId: String, otherUserId: String) {
    val database = FirebaseDatabase.getInstance()
    val timestamp = System.currentTimeMillis()

    val currentUserSwipesRef = database.getReference("swipes/$currentUserId/$otherUserId")
    currentUserSwipesRef.setValue(SwipeData(liked = false, timestamp = timestamp))

    val otherUserTotalSwipesRef = database.getReference("swipesReceived/$otherUserId/$currentUserId")
    otherUserTotalSwipesRef.setValue(true)

    val otherUserProfileRef = database.getReference("users/$otherUserId/numberOfUsersWhoSwiped")
    otherUserProfileRef.get().addOnSuccessListener { snapshot ->
        val currentCount = snapshot.getValue(Double::class.java) ?: 0.0
        otherUserProfileRef.setValue(currentCount + 1)
    }
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
fun runAiMatchCheck(
    coroutineScope: CoroutineScope,
    currentUserId: String,
    currentUserProfile: Profile,
    otherProfile: Profile,
    onComplete: (AiMatchCheckResult) -> Unit
) {
    // Compute the mathematical compatibility score and insights.
    val (finalScore, insights) = calculateExhaustiveCompatibilityScore(currentUserProfile, otherProfile)

    // Join insights into a breakdown string.
    val breakdownText = insights.joinToString(separator = "\n") { "${it.emoji} ${it.text}" }

    // Create a summary text that shows both the percentage and the breakdown details.
    val summaryText = "Total Match: $finalScore%\nBreakdown:\n$breakdownText"

    Log.d("runAiMatchCheck", "Calculated compatibility for currentUserId: $currentUserId and otherProfileId: ${otherProfile.userId}")
    Log.d("runAiMatchCheck", "Compatibility Summary: $summaryText")

    // Create an AiMatchCheckResult instance based solely on the computed values.
    val result = AiMatchCheckResult(
        summary = summaryText,
        totalMatchPercentage = finalScore,
        compatibilityBreakdown = breakdownText,
        timestamp = System.currentTimeMillis()
    )

    // Save the result in Firebase.
    FirebaseDatabase.getInstance()
        .getReference("aiMatchCheck/$currentUserId/${otherProfile.userId}")
        .setValue(result)

    // Invoke the onComplete callback on the Main thread.
    coroutineScope.launch(Dispatchers.Main) {
        onComplete(result)
    }
}
data class MatchInsight(val emoji: String, val text: String, val isPositive: Boolean)

fun calculateExhaustiveCompatibilityScore(
    profileA: Profile,
    profileB: Profile
): Pair<Int, List<MatchInsight>> {
    var score = 0.0
    val maxScore = 100.0
    val insights = mutableListOf<MatchInsight>()

    // Helper: Resolve a field with fallback and trim it.
    fun resolveField(primary: String, fallback: String?): String {
        return if (primary.trim().isNotEmpty()) primary.trim() else (fallback?.trim() ?: "")
    }

    // Helper: Compare string fields.
    // If both fields are empty then add an "etc" note and return 0.
    fun compareStringField(a: String, b: String, label: String, points: Double): Double {
        val aTrim = a.orEmpty().trim()
        val bTrim = b.orEmpty().trim()
        return when {
            aTrim.isEmpty() && bTrim.isEmpty() -> {
                insights += MatchInsight("ℹ️", "$label: not set by both", false)
                0.0
            }
            aTrim.equals(bTrim, ignoreCase = true) -> {
                insights += MatchInsight("✅", "$label match: Both are \"$aTrim\"", true)
                points
            }
            else -> {
                insights += MatchInsight("⚠️", "$label mismatch: \"$aTrim\" vs \"$bTrim\"", false)
                0.0
            }
        }
    }

    // 1. Education (Total: 12 points)
    val collegeA = resolveField(profileA.college, profileA.customCollege)
    val collegeB = resolveField(profileB.college, profileB.customCollege)
    score += compareStringField(collegeA, collegeB, "College", 6.0)

    val postGradA = resolveField(profileA.postGraduation ?: "", profileA.customPostGraduation)
    val postGradB = resolveField(profileB.postGraduation ?: "", profileB.customPostGraduation)
    score += compareStringField(postGradA, postGradB, "Post-Graduation", 6.0)

    // 2. Work & Job Role (Total: 13 points)
    score += compareStringField(profileA.work, profileB.work, "Workplace", 8.0)
    score += compareStringField(profileA.jobRole, profileB.jobRole, "Job Role", 5.0)

    // 3. Love Language (4 points)
    score += compareStringField(profileA.loveLanguage, profileB.loveLanguage, "Love Language", 4.0)

    // 4. Relationship Intent (6 points)
    score += compareStringField(profileA.lookingFor, profileB.lookingFor, "Relationship intent", 6.0)

    // 5. Community & Religion (Total: 9 points)
    score += compareStringField(profileA.community, profileB.community, "Community", 5.0)
    score += compareStringField(profileA.religion, profileB.religion, "Religion", 4.0)

    // 6. Location: City (4 points)
    score += compareStringField(profileA.city, profileB.city, "City", 4.0)

    // 7. Preferred Language (3 points)
    score += compareStringField(profileA.preferredLanguage, profileB.preferredLanguage, "Preferred Language", 3.0)

    // 8. Interests (Up to 8 points)
    val interestsA = profileA.interests.map { it.name.trim() }.filter { it.isNotEmpty() }.toSet()
    val interestsB = profileB.interests.map { it.name.trim() }.filter { it.isNotEmpty() }.toSet()
    val sharedInterests = interestsA.intersect(interestsB)
    if (sharedInterests.isNotEmpty()) {
        val bonus = (sharedInterests.size * 2).coerceAtMost(8)
        score += bonus.toDouble()
        insights += MatchInsight("✅", "Shared interests: ${sharedInterests.joinToString()}", true)
    } else {
        if (interestsA.isEmpty() && interestsB.isEmpty()) {
            insights += MatchInsight("ℹ️", "Interests: not set by both", false)
        } else {
            insights += MatchInsight("⚠️", "No common interests", false)
        }
    }

    // 9. Social Causes (Up to 6 points)
    val causesA = profileA.socialCauses.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val causesB = profileB.socialCauses.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val sharedCauses = causesA.intersect(causesB)
    if (sharedCauses.isNotEmpty()) {
        val bonus = (sharedCauses.size * 2).coerceAtMost(6)
        score += bonus.toDouble()
        insights += MatchInsight("✅", "Shared social causes: ${sharedCauses.joinToString()}", true)
    } else {
        if (causesA.isEmpty() && causesB.isEmpty()) {
            insights += MatchInsight("ℹ️", "Social causes: not set by both", false)
        } else {
            insights += MatchInsight("⚠️", "No common social causes", false)
        }
    }

    // 10. Zodiac (5 points)
    val zodiacA = if (!profileA.zodiac.isNullOrBlank()) profileA.zodiac.orEmpty().trim() else deriveZodiac(profileA.dob)
    val zodiacB = if (!profileB.zodiac.isNullOrBlank()) profileB.zodiac.orEmpty().trim() else deriveZodiac(profileB.dob)
    if (zodiacA != "Unknown" && zodiacB != "Unknown") {
        if (isZodiacCompatible(zodiacA, zodiacB)) {
            score += 5.0
            insights += MatchInsight("✅", "Zodiac compatibility: $zodiacA + $zodiacB", true)
        } else {
            insights += MatchInsight("⚠️", "Zodiac mismatch: $zodiacA vs $zodiacB", false)
        }
    } else {
        insights += MatchInsight("ℹ️", "Zodiac: not set", false)
    }

    // 11. Matrimony Mode (Total: 6 points)
    if (profileA.isMatrimonyMode && profileB.isMatrimonyMode) {
        score += 3.0
        score += compareStringField(profileA.marriageTimeline.orEmpty(), profileB.marriageTimeline.orEmpty(), "Marriage timeline", 3.0)
    } else if (profileA.isMatrimonyMode != profileB.isMatrimonyMode) {
        insights += MatchInsight("⚠️", "Matrimony mismatch: one is in marriage mode, the other is not", false)
    } else {
        insights += MatchInsight("ℹ️", "Matrimony mode: not set in both", false)
    }

    // 12. Relocation Preference (4 points)
    score += compareStringField(
        profileA.relocationPreference.orEmpty(),
        profileB.relocationPreference.orEmpty(),
        "Relocation",
        4.0
    )

    // 13. Post-Marriage Career Plan (3 points)
    score += compareStringField(
        profileA.postMarriageCareerPlan.orEmpty(),
        profileB.postMarriageCareerPlan.orEmpty(),
        "Post-marriage career plan",
        3.0
    )

    // 14. Tradition vs. Liberal (3 points)
    score += compareStringField(
        profileA.traditionalVsLiberal.orEmpty(),
        profileB.traditionalVsLiberal.orEmpty(),
        "Cultural mindset",
        3.0
    )

    // 15. Exhaustive Lifestyle Comparison (10 points)
    val lifestyleFieldNames = listOf(
        "Smoking", "Drinking", "Indoor/Outdoor", "Sexual activity", "Sociability",
        "Social media", "Dietary Preferences", "Sleep", "Work-life balance", "Exercise", "Adventurousness",
        "Family oriented", "Intellectual curiosity", "Creative expression",
        "Physical fitness", "Spirituality", "Easy going", "Professional ambition",
        "Environmental awareness", "Culinary enthusiasm", "Political awareness",
        "Community engagement", "Sports"
    )
    val lifestylePairs = listOf<Pair<Any?, Any?>>(
        Pair(profileA.lifestyle?.smoking_habit, profileB.lifestyle?.smoking_habit),
        Pair(profileA.lifestyle?.drinking_habit, profileB.lifestyle?.drinking_habit),
        Pair(profileA.lifestyle?.indoor_outdoor_orientation, profileB.lifestyle?.indoor_outdoor_orientation),
        Pair(profileA.lifestyle?.sexual_activity_level, profileB.lifestyle?.sexual_activity_level),
        Pair(profileA.lifestyle?.sociability, profileB.lifestyle?.sociability),
        Pair(profileA.lifestyle?.social_media_engagement, profileB.lifestyle?.social_media_engagement),
        Pair(profileA.lifestyle?.dietary_preferences, profileB.lifestyle?.dietary_preferences),
        Pair(profileA.lifestyle?.sleep_pattern, profileB.lifestyle?.sleep_pattern),
        Pair(profileA.lifestyle?.work_life_balance, profileB.lifestyle?.work_life_balance),
        Pair(profileA.lifestyle?.exercise_frequency, profileB.lifestyle?.exercise_frequency),
        Pair(profileA.lifestyle?.adventurousness, profileB.lifestyle?.adventurousness),
        Pair(profileA.lifestyle?.family_orientated, profileB.lifestyle?.family_orientated),
        Pair(profileA.lifestyle?.intellectual_curiosity, profileB.lifestyle?.intellectual_curiosity),
        Pair(profileA.lifestyle?.creative_expression, profileB.lifestyle?.creative_expression),
        Pair(profileA.lifestyle?.physical_fitness, profileB.lifestyle?.physical_fitness),
        Pair(profileA.lifestyle?.spirituality_mindfulness, profileB.lifestyle?.spirituality_mindfulness),
        Pair(profileA.lifestyle?.easy_goingness, profileB.lifestyle?.easy_goingness),
        Pair(profileA.lifestyle?.professional_ambition, profileB.lifestyle?.professional_ambition),
        Pair(profileA.lifestyle?.environmental_awareness, profileB.lifestyle?.environmental_awareness),
        Pair(profileA.lifestyle?.culinary_enthusiasm, profileB.lifestyle?.culinary_enthusiasm),
        Pair(profileA.lifestyle?.political_awareness, profileB.lifestyle?.political_awareness),
        Pair(profileA.lifestyle?.community_engagement, profileB.lifestyle?.community_engagement),
        Pair(profileA.lifestyle?.sports_enthusiasm, profileB.lifestyle?.sports_enthusiasm),
    )

    var lifestyleMatched = 0
    var lifestyleCompared = 0
    val lifestyleMatchesList = mutableListOf<String>()
    val lifestyleMismatchesList = mutableListOf<String>()

    for ((index, pair) in lifestylePairs.withIndex()) {
        val fieldName = lifestyleFieldNames.getOrElse(index) { "Lifestyle Field" }
        val a = pair.first
        val b = pair.second
        when {
            a is Int && b is Int -> {
                if (a == -1 && b == -1) {
                    // Both not set; list note but do not count
                    lifestyleMatchesList.add("$fieldName: not set by both")
                } else if (a == -1 || b == -1) {
                    lifestyleCompared++
                    lifestyleMismatchesList.add("$fieldName: one not set")
                } else {
                    lifestyleCompared++
                    val diff = kotlin.math.abs(a - b)
                    if (diff == 0) {
                        lifestyleMatched++
                        lifestyleMatchesList.add(fieldName)
                    } else {
                        lifestyleMismatchesList.add("$fieldName: $a vs $b")
                    }
                }
            }
            a is Boolean && b is Boolean -> {
                lifestyleCompared++
                if (a == b) {
                    lifestyleMatched++
                    lifestyleMatchesList.add(fieldName)
                } else {
                    lifestyleMismatchesList.add("$fieldName: $a vs $b")
                }
            }
            a is String && b is String -> {
                val aTrim = a.trim()
                val bTrim = b.trim()
                if (aTrim.isEmpty() && bTrim.isEmpty()) {
                    lifestyleMatchesList.add("$fieldName: not set by both")
                } else if (aTrim.isEmpty() || bTrim.isEmpty()) {
                    lifestyleCompared++
                    lifestyleMismatchesList.add("$fieldName: one not set")
                } else {
                    lifestyleCompared++
                    if (aTrim.equals(bTrim, ignoreCase = true)) {
                        lifestyleMatched++
                        lifestyleMatchesList.add(fieldName)
                    } else {
                        lifestyleMismatchesList.add("$fieldName: '$aTrim' vs '$bTrim'")
                    }
                }
            }
        }
    }

    // Create aggregated insight for Lifestyle.
    if (lifestyleCompared > 0) {
        if (lifestyleCompared < 5) {
            if (lifestyleMatched > 0) {
                // List exactly which traits matched.
                val matchedTraits = lifestyleMatchesList.filter { !it.contains("not set") }
                insights += MatchInsight("✅", "Both of you align on these $lifestyleMatched lifestyle traits: ${matchedTraits.joinToString(", ")}", true)
            } else {
                insights += MatchInsight("⚠️", "No lifestyle traits sufficiently set to compare", false)
            }
        } else {
            val percentage = (lifestyleMatched.toDouble() / lifestyleCompared.toDouble()) * 100
            val commonTraits = lifestyleMatchesList.filter { !it.contains("not set") }
            insights += MatchInsight("✅", "Lifestyle similarity: ${"%.1f".format(percentage)}% match over $lifestyleCompared factors. Common traits: ${if(commonTraits.isNotEmpty()) commonTraits.joinToString(", ") else "None"}", true)
        }
    } else {
        insights += MatchInsight("ℹ️", "Lifestyle: not set in both profiles", false)
    }
    if (lifestyleMismatchesList.isNotEmpty()) {
        insights += MatchInsight("⚠️", "Lifestyle mismatches: ${lifestyleMismatchesList.joinToString("; ")}", false)
    }

    // 17. User Tags (Bonus: up to 2 points)
    val tagsA = profileA.userTags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val tagsB = profileB.userTags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val sharedTags = tagsA.intersect(tagsB)
    if (sharedTags.isNotEmpty()) {
        val bonus = (sharedTags.size * 1).coerceAtMost(2)
        score += bonus.toDouble()
        insights += MatchInsight("✅", "Shared tags: ${sharedTags.joinToString()}", true)
    } else {
        insights += MatchInsight("ℹ️", "User tags: not set or no overlap", false)
    }

    val finalScore = score.coerceAtMost(maxScore).toInt()
    return Pair(finalScore, insights)
}

/** Haversine + getUserLocation */
fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val earthRadius = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
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