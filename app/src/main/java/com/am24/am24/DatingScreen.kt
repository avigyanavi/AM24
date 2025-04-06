@file:OptIn(ExperimentalMaterialApi::class, ExperimentalMaterialApi::class,
    ExperimentalLayoutApi::class
)

package com.am24.am24

import ChatRequest
import ChatResponse
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*

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
    val coroutineScope = rememberCoroutineScope()
    var forcedProfile by remember { mutableStateOf<Profile?>(null) }

    // State to control overlay visibility
    var visible by remember { mutableStateOf(true) }

    // Show overlays for 1 second when the screen is first visited
    LaunchedEffect(Unit) {
        visible = true
        delay(5000) // 1 second
        visible = false
    }

    val bottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)

    LaunchedEffect(Unit) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId != null) {
            excludedUserIds = fetchExcludedUsers(currentUserId)
            profileViewModel.fetchCurrentUserProfile()
            val loadedSwipes = loadAndResetSwipesDaily(currentUserId)
            remainingSwipes.value = loadedSwipes
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

    val displayedProfiles = if (initialQuery.isNotBlank()) {
        forcedProfile?.let { listOf(it) } ?: emptyList()
    } else {
        filteredProfiles.filter { it.userId !in excludedUserIds }
    }

    val saveFilters: () -> Unit = {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        datingViewModel.updateDatingFilters(filters)
        datingViewModel.refreshFilteredProfiles()
        if (currentUserId != null) {
            coroutineScope.launch {
                excludedUserIds = fetchExcludedUsers(currentUserId)
            }
        }
        coroutineScope.launch { bottomSheetState.hide() }
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
                onHighSchoolChange = { hs ->
                    datingViewModel.updateDatingFilters(filters.copy(highSchool = hs))
                },
                selectedCollege = filters.college,
                onCollegeChange = { college ->
                    datingViewModel.updateDatingFilters(filters.copy(college = college))
                },
                selectedPostGrad = filters.postGrad,
                onPostGradChange = { pg ->
                    datingViewModel.updateDatingFilters(filters.copy(postGrad = pg))
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { coroutineScope.launch { bottomSheetState.show() } },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00)),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Filters", color = Color.White, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    SwipeCounter(remainingSwipes.value)
                }
                InfoOverlay()
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
                // Swipe Left Overlay (Red)
                if (visible) {
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
                            contentDescription = "Swipe Left",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                // Swipe Right Overlay (Green)
                if (visible) {
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
                            contentDescription = "Swipe Right",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
        }
    }

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
                text = "Filters {scroll}",
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
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .height(48.dp)
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
            .padding(16.dp)
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
                CollapsedMetricsSection(profile)
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
                            Text("View More Posts", color = Color.White)
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
                text = if (age > 0) "${profile.name}, $age" else profile.name,
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
                Text("Posts", color = Color.White, fontSize = 12.sp)
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
                Text("No posts available", color = Color.Gray)
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
                Text("Close", color = Color(0xFFFF6F00))
            }
        },
        backgroundColor = Color.Black,
        contentColor = Color.White
    )
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
                        Text("- Metrics: SPR, Rating, etc.")
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
            progress = remainingSwipes / 25f,
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
                contentDescription = "Profile Photo",
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

            // Zodiac overlay at the top left
            if (currentPhotoIndex == 0) {
                TagBox(
                    text = profile.zodiac.toString(),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp, 10.dp)
                )
            }

            // Zodiac overlay at the top left
            if (currentPhotoIndex == 0) {
                TagBox(
                    text = ((profile.averageSwipeRightsOnUser)*100).roundToInt().toString()+"%",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp, 10.dp)
                )
            }

            // Bottom overlays (distance and vibe score)
            if (currentPhotoIndex == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(14.dp, 10.dp)
                ) {
                    TagBox(
                        text = "${userDistance.roundToInt()} km away",
                        modifier = Modifier.align(Alignment.BottomStart)
                    )
                    if (currentProfile != null) {
                        FlashyVibeScore(
                            aiMatchResult = aiMatchResult,
                            currentProfile = currentProfile,
                            otherProfile = profile,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
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
fun TagBox2(text: String) {
    if (text.isNotBlank()) {
        Box(
            modifier = Modifier
                .padding(horizontal = 1.dp)
                .background(Color.Black, shape = RoundedCornerShape(4.dp))
                .border(BorderStroke(1.dp, Color(0xFFFF6F00)), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(text = text, color = Color.White, fontSize = 28.sp)
        }
    }
}

/** Updated FlashyVibeScore with modifier parameter for overlay positioning */
@Composable
fun FlashyVibeScore(
    aiMatchResult: AiMatchCheckResult?,
    currentProfile: Profile,
    otherProfile: Profile,
    modifier: Modifier = Modifier
) {
    var showCompatibilityDialog by remember { mutableStateOf(false) }
    val displayPercent = aiMatchResult?.totalMatchPercentage?.let { "$it%" } ?: "Calculating..."

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFFF4500), Color(0xFFFF6F00))))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clickable { showCompatibilityDialog = true }
    ) {
        Text(
            text = "It's a $displayPercent match",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
    }

    if (showCompatibilityDialog) {
        AlertDialog(
            onDismissRequest = { showCompatibilityDialog = false },
            title = { Text("Compatibility Breakdown", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black) },
            text = {
                if (aiMatchResult != null) {
                    Text(aiMatchResult.compatibilityBreakdown, fontSize = 12.sp)
                } else {
                    Text("Analysis in progress...", fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showCompatibilityDialog = false }) {
                    Text("Close", color = Color(0xFFFF6F00), fontSize = 12.sp)
                }
            },
            backgroundColor = Color.Black,
            contentColor = Color(0xFFFF6F00)
        )
    }
}

@Composable
fun PerformanceMetricsSectionDating(profile: Profile, aiMatchResult: AiMatchCheckResult?) {
    var showPerformance by rememberSaveable { mutableStateOf(true) }
    CollapsibleSection(
        title = "Performance Metrics",
        icon = Icons.Default.Assessment,
        isExpanded = showPerformance,
        onToggle = { showPerformance = !showPerformance },
    ) {
        ProfileDetailRow("Matches", profile.matchCount.toString(), Icons.Default.People)
        ProfileDetailRow("Rating", String.format("%.2f", profile.averageRating), Icons.Default.Star)
        ProfileDetailRow(
            label = "Swipe Right Probability",
            value = "${(profile.averageSwipeRightsOnUser * 100).roundToInt()}%",
            icon = Icons.Default.Swipe
        )
        ProfileDetailRow(
            label = "Compatibility",
            value = aiMatchResult?.totalMatchPercentage?.let { "$it%" } ?: "N/A",
            icon = Icons.Default.HowToVote
        )
        ProfileDetailRow(
            label = "West Bengal Ranking",
            value = profile.am24Ranking.toString(),
            icon = Icons.Default.Public
        )

        val cityRank = if (profile.city == "Other")
            profile.am24RankingCustomCity
        else
            profile.am24RankingCity
        if (cityRank > 0) {
            ProfileDetailRow(
                label = "${profile.city.ifBlank { "City" }} Ranking",
                value = cityRank.toString(),
                icon = Icons.Default.LocationCity
            )
        }

        val hoodRank = if (profile.hometown == "Other")
            profile.am24RankingCustomHometown
        else
            profile.am24RankingHometown
        if (hoodRank > 0) {
            ProfileDetailRow(
                label = "${profile.hometown.ifBlank { "Locality" }} Ranking",
                value = hoodRank.toString(),
                icon = Icons.Default.Home
            )
        }
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
            ProfileDetailRow("${profile.college} Ranking", profile.am24RankingCollege.toString(), Icons.Default.Book)
            if (!profile.collegeGraduationYear.isNullOrBlank()) {
                ProfileDetailRow(
                    "Graduation Year from ${profile.college}",
                    profile.collegeGraduationYear,
                    Icons.Default.Book
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
    var showVoiceBio by rememberSaveable { mutableStateOf(true) }
    var showBasic by rememberSaveable { mutableStateOf(true) }
    var showPreferences by rememberSaveable { mutableStateOf(true) }
    var showLifestyle by rememberSaveable { mutableStateOf(true) }
    var showInterests by rememberSaveable { mutableStateOf(true) }
    var showAiSection by rememberSaveable { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    var currentAiMatchResult by remember { mutableStateOf(aiMatchResult) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(8.dp)
    ) {
        CollapsibleSection(
            title = "AI Match Analysis",
            icon = Icons.Default.Info,
            isExpanded = showAiSection,
            onToggle = { showAiSection = !showAiSection }
        ) {
            Column {
                if (currentAiMatchResult != null) {
                    ShowAiMatchAnalysis(currentAiMatchResult!!)
                } else {
                    Text("Analysis in progress...", color = Color.White)
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
                        .align(Alignment.End)
                ) {
                    Text("Re-run", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        PerformanceMetricsSectionDating(profile, aiMatchResult)
        Spacer(modifier = Modifier.height(12.dp))
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
            title = "Bio",
            icon = Icons.Default.Mic,
            isExpanded = showVoiceBio,
            onToggle = { showVoiceBio = !showVoiceBio }
        ) {
            showVoiceBio(profile = profile)
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
        text = "Analyzed on: ${formatTime(aiResult.timestamp)}",
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
            label = "Bio",
            value = profile.bio ?: "No bio available",
            icon = Icons.Default.BlurOn
        )
    }
}

/**
 * Updated runAiMatchCheck:
 * - The prompt now instructs the AI to output two extra lines:
 *   1. "Total Match: X%" (for total percentage)
 *   2. "Breakdown: ..." (for a detailed breakdown ending with a full stop)
 * - After receiving the GPT reply, two regexes extract these values.
 */
fun runAiMatchCheck(
    coroutineScope: CoroutineScope,
    currentUserId: String,
    currentUserProfile: Profile,
    otherProfile: Profile,
    onComplete: (AiMatchCheckResult) -> Unit
) {
    val displayHighSchool = if (currentUserProfile.highSchool.isNotBlank())
        currentUserProfile.highSchool else currentUserProfile.customHighSchool
    val highSchoolText = "High School: \"$displayHighSchool, graduationYr: ${currentUserProfile.highSchoolGraduationYear}\""

    val displayCollege = if (currentUserProfile.college.isNotBlank())
        currentUserProfile.college else currentUserProfile.customCollege
    val collegeText = "College: \"$displayCollege, graduationYr: ${currentUserProfile.collegeGraduationYear}, ${currentUserProfile.collegeDegree}\""

    val displayPostGrad = if (!currentUserProfile.postGraduation.isNullOrEmpty())
        currentUserProfile.postGraduation else currentUserProfile.customPostGraduation
    val postGradText = "Post Graduation: \"$displayPostGrad, graduationYr: ${currentUserProfile.postGraduationYear}, ${currentUserProfile.postGraduationDegree}\""
    val interestNames = currentUserProfile.interests.joinToString { it.name }
    val displayJobRole = if (currentUserProfile.jobRole.isNotBlank()) currentUserProfile.jobRole else currentUserProfile.customJobRole
    val displayWork = if (currentUserProfile.work.isNotBlank()) currentUserProfile.work else currentUserProfile.customWork

    val displayOtherHighSchool = if (otherProfile.highSchool.isNotBlank())
        otherProfile.highSchool else otherProfile.customHighSchool
    val otherHighSchoolText = "High School: \"$displayOtherHighSchool, graduationYr: ${otherProfile.highSchoolGraduationYear}\""

    val displayOtherCollege = if (otherProfile.college.isNotBlank())
        otherProfile.college else otherProfile.customCollege
    val collegeOtherText = "College: \"$displayOtherCollege, graduationYr: ${otherProfile.collegeGraduationYear}, ${otherProfile.collegeDegree}\""

    val displayOtherPostGrad = if (!otherProfile.postGraduation.isNullOrEmpty())
        otherProfile.postGraduation else otherProfile.customPostGraduation
    val otherPostGradText = "Post Graduation: \"$displayOtherPostGrad, graduationYr: ${otherProfile.postGraduationYear}, ${otherProfile.postGraduationDegree}\""
    val otherInterestNames = otherProfile.interests.joinToString { it.name }
    val displayOtherJobRole = if (otherProfile.jobRole.isNotBlank()) otherProfile.jobRole else otherProfile.customJobRole
    val displayOtherWork = if (otherProfile.work.isNotBlank()) otherProfile.work else otherProfile.customWork

    val userSnippet = """
        Name: ${currentUserProfile.name}
        Gender: ${currentUserProfile.gender}
        DOB: ${currentUserProfile.dob}
        Rating by others: ${currentUserProfile.averageRating} by ${currentUserProfile.numberOfRatings} users
        Community: ${currentUserProfile.community}
        Locality: ${ if (currentUserProfile.hometown != "") currentUserProfile.hometown else currentUserProfile.customHometown }
        Lifestyle: ${currentUserProfile.lifestyle}
        Politics: ${currentUserProfile.politics}
        Interests: $interestNames
        Love Language: ${currentUserProfile.loveLanguage}
        HighSchool: $highSchoolText
        College: $collegeText
        PostGrad: $postGradText
        Work and JobRole: $displayJobRole at $displayWork
        Social Causes: ${currentUserProfile.socialCauses}
        Looking For: ${currentUserProfile.lookingFor}
        Zodiac: ${currentUserProfile.zodiac}
        West Bengal Ranking: ${currentUserProfile.am24Ranking}
        ${ if (currentUserProfile.city != "") currentUserProfile.city else currentUserProfile.customCity } Ranking: ${ if (currentUserProfile.city != "") currentUserProfile.am24RankingCity else currentUserProfile.am24RankingCustomCity }
        ${ if (currentUserProfile.hometown != "") currentUserProfile.hometown else currentUserProfile.customHometown } Ranking: ${ if (currentUserProfile.hometown != "") currentUserProfile.am24RankingHometown else currentUserProfile.am24RankingCustomHometown }

        ...
    """.trimIndent()

    val otherSnippet = """
        Name: ${otherProfile.name}
        Gender: ${otherProfile.gender}
        DOB: ${otherProfile.dob}
        Rating by others: ${otherProfile.averageRating} by ${otherProfile.numberOfRatings} users
        Community: ${otherProfile.community}
        Locality: ${ if (otherProfile.hometown != "") otherProfile.hometown else otherProfile.customHometown }        
        Lifestyle: ${otherProfile.lifestyle}
        Politics: ${otherProfile.politics}
        Interests: $otherInterestNames
        Love Language: ${otherProfile.loveLanguage}
        HighSchool: $otherHighSchoolText
        College: $collegeOtherText
        PostGrad: $otherPostGradText
        Work and JobRole: $displayOtherJobRole at $displayOtherWork
        Social Causes: ${otherProfile.socialCauses}
        Looking For: ${otherProfile.lookingFor}
        Zodiac: ${otherProfile.zodiac}
        West Bengal Ranking: ${otherProfile.am24Ranking}
        ${ if (otherProfile.city != "") otherProfile.city else otherProfile.customCity } Ranking: ${ if (otherProfile.city != "") otherProfile.am24RankingCity else otherProfile.am24RankingCustomCity }
        ${ if (otherProfile.hometown != "") otherProfile.hometown else otherProfile.customHometown } Ranking: ${ if (otherProfile.hometown != "") otherProfile.am24RankingHometown else otherProfile.am24RankingCustomHometown }

        ...
    """.trimIndent()

    val prompt = """
       We have 2 profiles:
       
       [User Profile]
       $userSnippet
       
       [Potential Match]
       $otherSnippet
       
       Compare shared interests, zodiac compatibility, lifestyle overlap, locality match, education and work match.
       
       Provide both short term and long term match prospects using emojis.
       
       Then, on a new line, output the total match percentage in the following format:
       Total Match: [percentage]%
       
       On another new line, output a detailed breakdown of the compatibility score in the following format:
       Breakdown: [detailed breakdown text ending with a full stop - show what affected how much %]
       
       Keep your entire output limited to 420 words and limit the breakdown to 120 words.
    """.trimIndent()

    Log.d("runAiMatchCheck", "Starting runAiMatchCheck for currentUserId: $currentUserId and otherProfileId: ${otherProfile.userId}")
    Log.d("runAiMatchCheck", "Prompt built: $prompt")

    coroutineScope.launch(Dispatchers.IO) {
        val messages = listOf(ChatMessage(role = "user", content = prompt))
        val gptReply = callKupidXApi(messages) ?: return@launch

        val fullText = gptReply.trim()
        val totalRegex = Regex("Total Match:\\s*(\\d+)%")
        val breakdownRegex = Regex("Breakdown:\\s*(.+?)(?:\\n|$)")
        val totalMatchPercentage = totalRegex.find(fullText)?.groupValues?.get(1)?.toInt() ?: 0
        val compatibilityBreakdown = breakdownRegex.find(fullText)?.groupValues?.get(1) ?: ""

        val result = AiMatchCheckResult(
            summary = fullText,
            totalMatchPercentage = totalMatchPercentage,
            compatibilityBreakdown = compatibilityBreakdown,
            timestamp = System.currentTimeMillis()
        )

        FirebaseDatabase.getInstance()
            .getReference("aiMatchCheck/$currentUserId/${otherProfile.userId}")
            .setValue(result)

        withContext(Dispatchers.Main) { onComplete(result) }
    }
}

suspend fun callKupidXApi(messages: List<ChatMessage>): String? {
    val gson = Gson()
    return withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(3000, TimeUnit.SECONDS)
            .readTimeout(3000, TimeUnit.SECONDS)
            .writeTimeout(3000, TimeUnit.SECONDS)
            .build()

        val railwayUrl = "https://am24.org/openai/chat"
        val chatRequest = ChatRequest(model = "llama-3.3-70b-versatile", messages = messages, max_tokens = 8000)
        val jsonBody = gson.toJson(chatRequest)
        Log.d("FinalRequest", "Sending final request: $jsonBody")
        val mediaType = "application/json".toMediaType()
        val reqBody = jsonBody.toRequestBody(mediaType)
        val req = Request.Builder()
            .url(railwayUrl)
            .post(reqBody)
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e("FinalResponse", "Request failed with code: ${resp.code}")
                    return@withContext "Error: ${resp.code}"
                }
                val rBody = resp.body?.string() ?: return@withContext null
                Log.d("FinalResponse", rBody)
                val chatResp = gson.fromJson(rBody, ChatResponse::class.java)
                chatResp.choices.firstOrNull()?.message?.content
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error: ${e.message}"
        }
    }
}

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
                contentDescription = if (isExpanded) "Collapse" else "Expand",
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


/**
 * Updated AiMatchCheckResult with new fields.
 */
data class AiMatchCheckResult(
    val summary: String = "",                     // Full text output from the AI
    val totalMatchPercentage: Int = 0,            // Total match percentage extracted from GPT reply
    val compatibilityBreakdown: String = "",      // Detailed breakdown of compatibility score
    val timestamp: Long = 0L                      // When the analysis was done
)