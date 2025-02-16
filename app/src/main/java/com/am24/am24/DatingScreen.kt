@file:OptIn(ExperimentalMaterialApi::class, ExperimentalMaterialApi::class,
    ExperimentalLayoutApi::class
)

package com.am24.am24

import DatingViewModel
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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
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
    val matchPopUpState by profileViewModel.matchPopUpState.collectAsState()

    var excludedUserIds by remember { mutableStateOf(emptySet<String>()) }
    val remainingSwipes = remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    var forcedProfile by remember { mutableStateOf<Profile?>(null) }

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
        val currentUserProfile by profileViewModel.currentUserProfile.collectAsState()
        val currentProfile = profiles[currentProfileIndex]

        var userDistance by remember { mutableStateOf<Float?>(null) }
        var compatibilityScore by remember { mutableStateOf<Double?>(null) }

        // Calculate distance + compatibility
        LaunchedEffect(currentProfile) {
            userDistance = calculateDistance(currentUserId, currentProfile.userId, geoFire)
            currentUserProfile?.let { userProfile ->
                compatibilityScore = userProfile.calculateCompatibility(currentProfile)
            }
        }

        userDistance?.let { distance ->
            DatingProfileCard(
                profile = currentProfile,
                compatibilityScore = compatibilityScore,
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
    compatibilityScore: Double?,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    userDistance: Float,
    navController: NavController,
    postViewModel: PostViewModel
) {
    // 1) Compute the average each time this composable recomposes with updated profile data
    val computedAvg = if (profile.numberOfUsersWhoSwiped > 0) {
        profile.numberOfSwipeRights.toDouble() / profile.numberOfUsersWhoSwiped
    } else 0.0

    // 2) Assign it to the profile’s mutable field
    profile.averageSwipeRightsOnUser = computedAvg

    // (The rest of your swipeable logic remains the same)
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

    // The UI layout stays the same; it just references "profile.averageSwipeRightsOnUser"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .border(
                width = 3.dp,
                color = getLevelBorderColor(profile.averageRating),
                shape = RoundedCornerShape(8.dp)
            )
            .swipeable(
                state = swipeableState,
                anchors = anchors,
                thresholds = { _, _ -> FractionalThreshold(0.3f) },
                orientation = Orientation.Horizontal
            )
            .offset { IntOffset(swipeOffset.roundToInt(), 0) }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 1) Photo + Overlays
            item {
                PhotoWithDynamicOverlays(
                    profile = profile,
                    userDistance = userDistance,
                    compatibilityScore = compatibilityScore
                )
            }

            // 2) Dating header (name, age, hometown, rating)
            item {
                DatingProfileHeader(
                    profile = profile,
                    userDistance = userDistance
                )
            }

            // 2) Collapsible
            item {
                ProfileCollapsibleSectionsAll(profile, compatibilityScore)
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

            // 4) Collapsed metrics, etc.
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

@Composable
fun DatingProfileHeader(
    profile: Profile,
    userDistance: Float
) {
    val age = calculateAge(profile.dob)
    // We'll gather these tag-like items:
    val community = profile.community
    val religion = profile.religion
    val caste = profile.caste

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Row: distance on the left, community/religion/caste on the right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side => distance
            TagBox(text = "${userDistance.roundToInt()} km away")

            // Right side => row of community, religion, caste
            Row {
                // Show each only if not blank
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

        // Second Row: name + age
        Text(
            text = if (age > 0) "${profile.name}, $age" else profile.name,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = Color.White
        )
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
            // Divide by 25 now instead of 50
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
    compatibilityScore: Double?
) {
    val photoUrls = listOfNotNull(profile.profilepicUrl) + profile.optionalPhotoUrls
    var currentPhotoIndex by remember { mutableStateOf(0) }
    val age = calculateAge(profile.dob)
    val heightCm = profile.height

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f) // Set the desired aspect ratio
            .background(Color.Black)
            .pointerInput(photoUrls) {
                detectTapGestures(
                    onTap = { offset ->
                        if (photoUrls.size > 1) {
                            currentPhotoIndex = if (offset.x > size.width / 2) {
                                (currentPhotoIndex + 1) % photoUrls.size
                            } else {
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
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp)
            )
        }
        // Add the horizontal dot row at the top exactly as in your ProfileScreen:
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
//        if (currentPhotoIndex == 0) {
//            // Bottom overlay: Fully transparent, with all details in TagBoxes preserving the original layout
//            Box(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .background(Color.Transparent)
//                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
//                    .padding(horizontal = 14.dp, vertical = 10.dp)
//                    .align(Alignment.BottomCenter)
//            ) {
//                Column {
//                    // First row: left = name & rating (in a Column), right = height (if available)
//                    Row(
//                        modifier = Modifier.fillMaxWidth(),
//                        horizontalArrangement = Arrangement.SpaceBetween,
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Column {
//                            TagBox2(text = "${profile.name}, $age")
//                            Spacer(modifier = Modifier.height(2.dp))
//                        }
//                    }
//                    Spacer(modifier = Modifier.height(4.dp))
//                    // Second row: left = distance, right = row of community, religion, and hometown tags
//                    Row(
//                        modifier = Modifier.fillMaxWidth(),
//                        horizontalArrangement = Arrangement.SpaceBetween,
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        TagBox(text = "${userDistance.roundToInt()} km away")
//                        Row {
//                            profile.community.takeIf { it.isNotBlank() }?.let {
//                                TagBox(text = it)
//                                Spacer(modifier = Modifier.width(4.dp))
//                            }
//                            profile.religion.takeIf { it.isNotBlank() }?.let {
//                                TagBox(text = it)
//                                Spacer(modifier = Modifier.width(4.dp))
//                            }
//                            profile.hometown.takeIf { it.isNotBlank() }?.let {
//                                TagBox(text = it)
//                            }
//                        }
//                    }
//                }
//            }
//        }
    }
}


/** TagBox is unchanged (for your #tags). */
@Composable
fun TagBox(text: String) {
    if (text.isNotBlank()) {
        Box(
            modifier = Modifier
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

/** The “flashy vibe” gradient text. Unchanged. */
@Composable
fun FlashyVibeScore(compatibilityScore: Double?) {
    var showCompatibilityDialog by remember { mutableStateOf(false) }
    val displayPercent = compatibilityScore?.roundToInt()?.coerceIn(0, 100) ?: 0
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFF4500), Color(0xFFFF6F00))
                )
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clickable { showCompatibilityDialog = true }
    ) {
        Text(
            text = "Compatibility: $displayPercent%",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp
        )
    }
    if (showCompatibilityDialog) {
        AlertDialog(
            onDismissRequest = { showCompatibilityDialog = false },
            title = { Text("Compatibility Breakdown", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Shared Interests: +20%", fontSize = 12.sp)
                    Text("Zodiac Match: +10%", fontSize = 12.sp)
                    Text("Lifestyle Overlap: +15%", fontSize = 12.sp)
                    Text("Locality Bonus: +10%", fontSize = 12.sp)
                    Text("\nTotal: $displayPercent%", fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showCompatibilityDialog = false }) {
                    Text("Close", color = Color(0xFFFF6F00), fontSize = 12.sp)
                }
            }
        )
    }
}

@Composable
fun PerformanceMetricsSectionDating(profile: Profile, compatibilityScore: Double?) {
    var showPerformance by rememberSaveable { mutableStateOf(false) }
    CollapsibleSection(
        title = "Performance Metrics",
        icon = Icons.Default.Assessment,
        isExpanded = showPerformance,
        onToggle = { showPerformance = !showPerformance }
    ) {
        ProfileDetailRow(
            label = "Swipe Right Probability",
            value = "${(profile.averageSwipeRightsOnUser * 100).roundToInt()}%",
            icon = Icons.Default.Swipe
        )
        // For Compatibility, if you have a computed value, use it; else, show placeholder.
        ProfileDetailRow(
            label = "Compatibility",
            value = "${compatibilityScore?.roundToInt() ?: 0}%",
            icon = Icons.Default.HowToVote
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
        ProfileDetailRow("Rating", String.format("%.2f", profile.averageRating), Icons.Default.Star)
        RatingBar(profile.averageRating, profile.numberOfRatings)
    }
}

/**
 * The collapsible sections: Basic Info, Preferences, Lifestyle, Interests
 * without edit icons for the DatingScreen usage.
 */
@Composable
fun ProfileCollapsibleSectionsAll(profile: Profile, compatibilityScore: Double?) {
    // Declare state variables for each collapsible section
    var showVoiceBio by rememberSaveable { mutableStateOf(false) }
    var showBasic by rememberSaveable { mutableStateOf(false) }
    var showPreferences by rememberSaveable { mutableStateOf(false) }
    var showLifestyle by rememberSaveable { mutableStateOf(false) }
    var showInterests by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(8.dp)
    ) {
        PerformanceMetricsSectionDating(profile, compatibilityScore)
        Spacer(modifier = Modifier.height(12.dp))
        // Voice & Bio accordion
        CollapsibleSection(
            title = "Bio",
            icon = Icons.Default.Mic,  // Choose a microphone icon
            isExpanded = showVoiceBio,
            onToggle = { showVoiceBio = !showVoiceBio }
        ) {
            showVoiceBio(profile = profile)
        }
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
            .clickable { onToggle() }
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

        // Wrap your content in a card with background color #1A1A1A
        Card(
//            backgroundColor = Color(0xFF1A1A1A),
            backgroundColor = Color.Black,
            elevation = 4.dp,                    // Choose an elevation if you like
            shape = RoundedCornerShape(8.dp),    // Slight rounding
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)      // Indent from screen edges
        ) {
            // Add some padding so it looks “depressed” inside
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }

        Spacer(Modifier.height(8.dp))
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

    // Write swipe data for the current user
    val swipeData = SwipeData(liked = true, timestamp = timestamp)
    currentUserSwipesRef.setValue(swipeData)
    currentUserLikesGivenRef.setValue(timestamp)
    otherUserLikesReceivedRef.setValue(timestamp)

    // ✅ 1) Record that the other user was swiped on (for total "swipes received")
    val otherUserTotalSwipesRef = database.getReference("swipesReceived/$otherUserId/$currentUserId")
    otherUserTotalSwipesRef.setValue(true)

    // ✅ 2) Increment the other user’s total "numberOfUsersWhoSwiped"
    val otherUserProfileRef = database.getReference("users/$otherUserId/numberOfUsersWhoSwiped")
    otherUserProfileRef.get().addOnSuccessListener { snapshot ->
        val currentCount = snapshot.getValue(Double::class.java) ?: 0.0
        otherUserProfileRef.setValue(currentCount + 1)
    }

    // ✅ 3) Increment the other user’s "numberOfSwipeRights" ONLY on a right-swipe
    val otherUserSwipeRightsRef = database.getReference("users/$otherUserId/numberOfSwipeRights")
    otherUserSwipeRightsRef.get().addOnSuccessListener { snapshot ->
        val currentSwipeRights = snapshot.getValue(Int::class.java) ?: 0
        otherUserSwipeRightsRef.setValue(currentSwipeRights + 1)
    }

    // Check if it’s a match
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

    // ✅ Increment total swipes received for other user
    val otherUserTotalSwipesRef = database.getReference("swipesReceived/$otherUserId/$currentUserId")
    otherUserTotalSwipesRef.setValue(true)  // Just track presence

    // ✅ Increment count for numberOfUsersWhoSwiped
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

/** Haversine + getUserLocation => same as your code */
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

/** Standard “MatchPopUp” remains unchanged. */
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
    userDistance: Float,
    compatibilityScore: Double?
) {
    PhotoWithTwoOverlays(
        profile = profile,
        userDistance = userDistance,
        compatibilityScore = compatibilityScore,
    )
}
