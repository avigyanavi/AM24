@file:OptIn(
    ExperimentalMaterialApi::class, ExperimentalMaterialApi::class,
    ExperimentalLayoutApi::class
)

package com.am24.am24

import DatingViewModel
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import androidx.compose.material.icons.filled.FilterList
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.EmojiEmotions  // or whichever icon you prefer for “Compliment”
import androidx.compose.material.icons.filled.FlashOn         // for “Boost”
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.filled.AttachEmail
import androidx.compose.material.icons.filled.OnlinePrediction
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import java.io.File

data class SwipeData(
    val liked: Boolean = false,
    val timestamp: Long = 0L
)

@Composable
fun BoostedPill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(Color(0xFFFF6F00), RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.FlashOn,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Boosted profile",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
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
    // ① Trigger the load as soon as the composable enters composition
    LaunchedEffect(Unit) {
        profileViewModel.fetchCurrentUserProfile()
    }
    val myProfile by profileViewModel.currentUserProfile.collectAsState()
    if (myProfile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val postViewModel: PostViewModel = viewModel()
    val filters by datingViewModel.datingFilters.collectAsState()
    val filteredProfiles by datingViewModel.filteredProfiles.collectAsState()
    val isLoading by datingViewModel.isLoading.collectAsState()
    val matchPopUpState by profileViewModel.matchPopUpState.collectAsState()
    val boostedUsers by datingViewModel.boostedUsers.collectAsState()
    val userDistanceMap by datingViewModel.userDistanceMap.collectAsState()

    // ← NEW: collect the map of compliments that others have sent you
    val complimentsReceived by datingViewModel.complimentsReceived.collectAsState()
    val complimenters       = complimentsReceived.keys.toList()
    val complimentsLeft by datingViewModel.complimentsLeft.collectAsState()

    var excludedUserIds by remember { mutableStateOf(emptySet<String>()) }
    val remainingSwipes = remember { mutableStateOf(0) }
    var swipesLoaded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var showBoostedOverlay by rememberSaveable { mutableStateOf(false) }

    // constants
    val BOOST_DURATION = 6 * 60 * 60 * 1000L
    val now = remember { System.currentTimeMillis() }
    val last = myProfile?.lastBoostTimestamp ?: 0L
    val inCooldown = now - last < BOOST_DURATION
    val canBoost = myProfile?.availableBoosts!! > 0 && !inCooldown

    val bottomSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )

    var showComplimentDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            excludedUserIds     = fetchExcludedUsers(uid)
            profileViewModel.fetchCurrentUserProfile()
            remainingSwipes.value = loadAndResetSwipesDaily(uid)
            swipesLoaded        = true
            datingViewModel.refreshFilteredProfiles()
        }
    }

// 1) everyone you haven’t yet swiped/matched on
    val base = filteredProfiles.filter { it.userId !in excludedUserIds }

// 2) boosted users (skip those who already complimented you)
    val boostedFirst = boostedUsers
        .filter { it.userId !in complimenters && it.userId !in excludedUserIds }

// 3) then your complimenters
    val complimentFirst = complimenters
        .mapNotNull { id -> base.find { it.userId == id } }

// 4) then everyone else
    val rest = base.filter {
        it.userId !in boostedFirst.map { b -> b.userId } &&
                it.userId !in complimenters
    }

    val displayedProfiles = boostedFirst + complimentFirst + rest

    ModalBottomSheetLayout(
        sheetState   = bottomSheetState,
        sheetContent = {
            FiltersOverlay(
                ageRange           = filters.ageStart..filters.ageEnd,
                onAgeRangeChange   = { datingViewModel.updateDatingFilters(filters.copy(ageStart = it.start, ageEnd = it.endInclusive)) },
                maxDistance        = filters.distance,
                onDistanceChange   = { datingViewModel.updateDatingFilters(filters.copy(distance = it)) },
                selectedGenders    = filters.gender.split(",").toSet(),
                onGenderChange     = { datingViewModel.updateDatingFilters(filters.copy(gender = it.joinToString(","))) },
                selectedCommunity  = filters.community,
                onCommunityChange  = { datingViewModel.updateDatingFilters(filters.copy(community = it)) },
                selectedReligion   = filters.religion,
                onReligionChange   = { datingViewModel.updateDatingFilters(filters.copy(religion = it)) },
                selectedCaste      = filters.caste,
                onCasteChange      = { datingViewModel.updateDatingFilters(filters.copy(caste = it)) },
                selectedHighSchool = filters.highSchool,
                onHighSchoolChange = { datingViewModel.updateDatingFilters(filters.copy(highSchool = it)) },
                selectedCollege    = filters.college,
                onCollegeChange    = { datingViewModel.updateDatingFilters(filters.copy(college = it)) },
                selectedPostGrad   = filters.postGrad,
                onPostGradChange   = { datingViewModel.updateDatingFilters(filters.copy(postGrad = it)) },
                onSaveFilters      = {
                    coroutineScope.launch {
                        bottomSheetState.hide()
                        datingViewModel.refreshFilteredProfiles()
                    }
                },
                onCancel = { coroutineScope.launch { bottomSheetState.hide() } }
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Row(
                modifier           = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { coroutineScope.launch { bottomSheetState.show() } }) {
                    Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filters), tint = Color(0xFFFF6F00))
                }

                Row {
                    // ── compliment button ────────────────────────────────
                    IconWithQuota(
                        quota   = complimentsLeft,
                        icon    = Icons.Default.AttachEmail,
                        enabled = complimentsLeft > 0,
                        onClick = {
                            if (complimentsLeft > 0) showComplimentDialog = true
                        }
                    )

                    Spacer(Modifier.width(16.dp))

// ── boost button  (+ keep your existing canBoost logic) ─────
                    IconWithQuota(
                        quota   = myProfile?.availableBoosts ?: 0,
                        icon    = Icons.Default.FlashOn,
                        tint    = if (canBoost) Color.White else Color.Gray,
                        enabled = canBoost,
                        onClick = {
                            val myUid = FirebaseAuth.getInstance().uid ?: return@IconWithQuota
                            datingViewModel.boostUser(myUid) {
                                profileViewModel.decrementBoostsLocal()          // 👈 instant UI drop
                                showBoostedOverlay = true
                                profileViewModel.fetchCurrentUserProfile()       // keep server-truth
                            }
                        }
                    )
                }
                // ─── yellow flash that fades out ──────────────────────────────────────────
                AnimatedVisibility(
                    visible = showBoostedOverlay,
                    enter   = fadeIn(animationSpec = tween(250)),
                    exit    = fadeOut(animationSpec = tween(600))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x88FFFF00)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FlashOn, null,
                            tint = Color(0xFFFF6F00),
                            modifier = Modifier.size(96.dp))
                    }
                }

                LaunchedEffect(showBoostedOverlay) {
                    if (showBoostedOverlay) {
                        kotlinx.coroutines.delay(2000)
                        showBoostedOverlay = false
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFFF6F00))
                    displayedProfiles.isEmpty() -> NoMoreProfilesScreen()
                    else -> DatingScreenContent(
                        navController, geoFire, profileViewModel, postViewModel, displayedProfiles,
                        onSwipeRight = { if (remainingSwipes.value > 0) remainingSwipes.value-- },
                        onSwipeLeft  = { if (remainingSwipes.value > 0) remainingSwipes.value-- },
                        boostedUsers
                    )
                }

                if (swipesLoaded && remainingSwipes.value <= 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f))
                            .pointerInput(Unit) { detectTapGestures {} },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.no_more_swipes_available),
                            color    = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // standard match popup
        matchPopUpState?.let { (currentUser, matchedUser) ->
            MatchPopUp(
                currentUser.profilepicUrl.orEmpty(),
                matchedUser.profilepicUrl.orEmpty(),
                onChatClick = { navController.navigate("chat/${matchedUser.userId}") },
                onClose    = { profileViewModel.clearMatchPopUp() }
            )
        }

        // ← CHANGED: route through ViewModel.sendCompliment(...)
        if (showComplimentDialog && displayedProfiles.isNotEmpty()) {
            ComplimentDialog(
                complimentsLeft = complimentsLeft,
                onSend = { text, voiceUri ->
                    coroutineScope.launch {
                        val justComplimented = displayedProfiles.first()
                        datingViewModel.sendCompliment(
                            receiverId       = justComplimented.userId,
                            textMessage      = text,
                            voiceUri         = voiceUri,
                            profileViewModel = profileViewModel
                        )
                        excludedUserIds = excludedUserIds + justComplimented.userId
                        showComplimentDialog = false
                    }
                },
                onDismiss = { showComplimentDialog = false }
            )
        }
    }
}


@Composable
fun IconWithQuota(
    quota: Int,
    icon: ImageVector,
    tint: Color = Color.White,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    // size of the icon + ring
    val size    = 42.dp
    val sweep   = remember(quota) { quota / 10f * 360f }   // daily quota = 10
    val strokeW = 4.dp

    Box(
        modifier = Modifier
            .size(size)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // progress ring
        Canvas(Modifier.fillMaxSize()) {
            drawArc(
                color      = Color(0xFFFF6F00),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter  = false,
                style      = Stroke(width = strokeW.toPx(), cap = StrokeCap.Round)
            )
        }
        // icon
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        // tiny number in the centre
        Text(quota.toString(), fontSize = 10.sp, color = Color.White)
    }
}
/**
 * Load swipes from Firebase and reset them to 25 if a new day has started.
 */
suspend fun loadAndResetSwipesDaily(userId: String): Int {
    val swipesRef = FirebaseRefs.db.getReference("users/$userId/swipesInfo")
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
    val swipesRef = FirebaseRefs.db.getReference("users/$userId/swipesInfo")
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
                    listOf(
                        (stringResource(R.string.male_option)),
                        (stringResource(R.string.female_option))
                    ).forEach { gender ->
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
                                backgroundColor = if (selectedGenders.contains(gender)) Color(
                                    0xFFFF6000
                                ) else Color(0xFF1A1A1A)
                            ),
                            border = BorderStroke(
                                2.dp,
                                if (selectedGenders.contains(gender)) Color(0xFFFF6000) else Color.Gray
                            ),
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
                    text = stringResource(
                        R.string.age_range,
                        ageRange.start,
                        ageRange.endInclusive
                    ),
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
                    options = listOf(
                        stringResource(R.string.high_school_andrews_high_school),
                        stringResource(R.string.high_school_ashok_hall),
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
                        stringResource(R.string.high_school_jewish_girls),
                        stringResource(R.string.high_school_kalyani_university_experimental),
                        stringResource(R.string.high_school_kendriya_vidyalaya_ballygunge),
                        stringResource(R.string.high_school_la_martiniere_boys),
                        stringResource(R.string.high_school_la_martiniere_girls),
                        stringResource(R.string.high_school_loreto_house),
                        stringResource(R.string.high_school_mahadevi_birla_world_academy),
                        stringResource(R.string.high_school_mahadevi_birla_shishu_vihar),
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
                    ),
                    selectedOption = selectedHighSchool,
                    onOptionChange = onHighSchoolChange
                )

                Spacer(modifier = Modifier.height(8.dp))

                // College
                DropdownFilter(
                    label = stringResource(R.string.college),
                    options = listOf(
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
                    ),
                    selectedOption = selectedCollege,
                    onOptionChange = onCollegeChange
                )

                Spacer(modifier = Modifier.height(8.dp))

                //Post Grad
                DropdownFilter(
                    label = stringResource(R.string.post_grad),
                    options = listOf(
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
                    ),
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
                        options = listOf(
                            stringResource(R.string.community_bengali),
                            stringResource(R.string.community_santhal),
                            stringResource(R.string.community_oraon),
                            stringResource(R.string.community_munda),
                            stringResource(R.string.community_punjabi),
                            stringResource(R.string.community_tamil),
                            stringResource(R.string.community_gujarati),
                            stringResource(R.string.community_marwari),
                            stringResource(R.string.community_bihari),
                            stringResource(R.string.community_odia),
                            stringResource(R.string.community_assamese),
                            stringResource(R.string.community_telugu),
                            stringResource(R.string.community_kannadiga),
                            stringResource(R.string.community_malayali),
                            stringResource(R.string.community_nepali),
                            stringResource(R.string.community_bangal),
                            stringResource(R.string.community_ghoti),
                            stringResource(R.string.community_other)
                        ),
                        selectedOption = selectedCommunity,
                        onOptionChange = onCommunityChange
                    )

                    DropdownFilter(
                        label = stringResource(R.string.religion),
                        options = listOf(
                            stringResource(R.string.religion_hindu),
                            stringResource(R.string.religion_muslim),
                            stringResource(R.string.religion_christian),
                            stringResource(R.string.religion_buddhist),
                            stringResource(R.string.religion_jain),
                            stringResource(R.string.religion_sikh),
                            stringResource(R.string.religion_indigenous_tribal),
                            stringResource(R.string.religion_no_religion),
                            stringResource(R.string.religion_other)
                        ),
                        selectedOption = selectedReligion,
                        onOptionChange = onReligionChange
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Caste
                Row(modifier = Modifier.fillMaxWidth()) {
                    DropdownFilter(
                        label = stringResource(R.string.caste),
                        options = listOf(
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
                        ),
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
    val clearSelectionText =
        stringResource(R.string.clear_selection) // Resolve string in composable scope
    val allOptions = listOf(clearSelectionText) + options // Add "Clear Selection" option

    Column {
        Text(label, color = Color.White, fontSize = 14.sp)
        Box {
            Button(
                onClick = { expanded = !expanded },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (selectedOption.isNotBlank()) Color(0xFFFF6000) else Color(
                        0xFF1A1A1A
                    )
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
    onSwipeLeft: () -> Unit,
    boostedUsers: List<Profile>          // ← already added in your latest code
) {
    var currentProfileIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { postViewModel.fetchPosts() }

    if (profiles.isEmpty() || currentProfileIndex >= profiles.size) {
        NoMoreProfilesScreen()
        return
    }

    val currentUserId        = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val currentUserProfile   by profileViewModel.currentUserProfile.collectAsState()
    val currentProfile       = profiles[currentProfileIndex]
    val isBoostedProfile     = boostedUsers.any { it.userId == currentProfile.userId }

    var userDistance   by remember { mutableStateOf<Float?>(null) }
    var aiMatchResult  by remember { mutableStateOf<AiMatchCheckResult?>(null) }
    val context        = LocalContext.current

    /* --- distance + optional AI check (unchanged) --- */
    LaunchedEffect(currentProfile.userId) {
        userDistance = calculateDistance(currentUserId, currentProfile.userId, geoFire)
        val ref  = FirebaseRefs.db
            .getReference("aiMatchCheck/$currentUserId/${currentProfile.userId}")
        val snap = ref.get().await()
        val existing = snap.getValue(AiMatchCheckResult::class.java)
        if (existing != null) {
            aiMatchResult = existing
        } else {
            runAiMatchCheck(
                context        = context,
                coroutineScope = this,
                currentUserId  = currentUserId,
                currentUserProfile = currentUserProfile!!,
                otherProfile   = currentProfile
            ) { newResult -> aiMatchResult = newResult }
        }
    }

    userDistance?.let { distance ->
        Column {                          // ← STACK the pill and the card
            if (isBoostedProfile) {
                BoostedPill()
            }

            DatingProfileCard(
                profile         = currentProfile,
                isBoosted       = isBoostedProfile,            // NEW
                aiMatchResult   = aiMatchResult,
                onSwipeRight    = {
                    onSwipeRight()
                    handleSwipeRight(currentUserId, currentProfile.userId, profileViewModel)
                    currentProfileIndex++
                },
                onSwipeLeft     = {
                    onSwipeLeft()
                    handleSwipeLeft(currentUserId, currentProfile.userId)
                    currentProfileIndex++
                },
                navController   = navController,
                userDistance    = distance,
                postViewModel   = postViewModel,
                currentProfile  = currentUserProfile
            )
        }
    }
}

// Updated DatingProfileCard
@Composable
fun DatingProfileCard(
    profile: Profile,
    isBoosted: Boolean,                         // ← NEW
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
                    isBoosted      = isBoosted,            // ← pass through
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
                text = if (age > 0) stringResource(
                    R.string.name_age,
                    profile.name,
                    age
                ) else profile.name,
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
    isBoosted: Boolean,             // ← NEW
    userDistance: Float,
    aiMatchResult: AiMatchCheckResult?,
    currentProfile: Profile? = null
) {
    var currentPhotoIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val photoUrls = listOfNotNull(profile.profilepicUrl) + profile.optionalPhotoUrls
    val datingViewModel: DatingViewModel = viewModel()
    val compliments by datingViewModel.complimentsReceived.collectAsState()
    val compliment = compliments[profile.userId]

    // Prefetch
    LaunchedEffect(photoUrls) {
        photoUrls.forEach { url ->
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .diskCacheKey(url)
                    .memoryCacheKey(url)
                    .build()
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ─── PHOTO + OVERLAYS ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .background(Color.Black)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .pointerInput(photoUrls) {
                    detectTapGestures { offset ->
                        if (photoUrls.size > 1) {
                            currentPhotoIndex = if (offset.x > size.width / 2)
                                (currentPhotoIndex + 1) % photoUrls.size
                            else
                                (currentPhotoIndex - 1 + photoUrls.size) % photoUrls.size
                        }
                    }
                }
        ) {
            // 1) main photo
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
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.no_images), color = Color.White)
                }
            }

            // 2) photo‐position indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                photoUrls.forEachIndexed { idx, _ ->
                    Box(
                        modifier = Modifier
                            .width(if (idx == currentPhotoIndex) 30.dp else 10.dp)
                            .height(4.dp)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (idx == currentPhotoIndex) Color.White else Color.Gray)
                    )
                }
            }

            // 3) interests gradient overlay (only on first photo)
            if (currentPhotoIndex == 0 && profile.interests.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f, endY = Float.POSITIVE_INFINITY
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        profile.interests.take(5).forEach { interest ->
                            Text(
                                text = "${interest.emoji} ${interest.name}",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // 4) Super-swipe compliment badge
            compliment?.let { c ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0xFFE91E63), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.EmojiEmotions,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = c.text,
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (isBoosted) {
                Icon(
                    Icons.Default.FlashOn,
                    contentDescription = "Boosted",
                    tint = Color(0xFFFF6F00),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(26.dp)
                )
            }
        }

        // ─── DISTANCE TAG (moved *below* the photo) ────────────────────────────
        TagBox(
            text = stringResource(R.string.max_distance, userDistance.roundToInt()),
            modifier = Modifier
                .padding(start = 14.dp, top = 8.dp)
        )
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
            value = String.format(
                LocalContext.current.resources.configuration.locale,
                "%.2f",
                profile.averageRating
            ),
            icon = Icons.Default.Star
        )
        ProfileDetailRow(
            label = stringResource(R.string.swipe_right_percentage),
            value = stringResource(
                R.string.percentage,
                (profile.averageSwipeRightsOnUser * 100).roundToInt()
            ),
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
                label = stringResource(
                    R.string.city_ranking,
                    profile.city.ifBlank { stringResource(R.string.city) }),
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
                label = stringResource(
                    R.string.locality_ranking,
                    profile.hometown.ifBlank { stringResource(R.string.locality) }),
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
                    label = stringResource(
                        R.string.high_school_graduation_year,
                        profile.highSchool
                    ),
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
fun ProfileCollapsibleSectionsAll(
    profile: Profile,
    currentUserProfile: Profile?,
    aiMatchResult: AiMatchCheckResult?
) {
    var showVoiceBio by rememberSaveable { mutableStateOf(false) }
    var showBasic by rememberSaveable { mutableStateOf(false) }
    var showPreferences by rememberSaveable { mutableStateOf(false) }
    var showLifestyle by rememberSaveable { mutableStateOf(false) }
    var showInterests by rememberSaveable { mutableStateOf(false) }
    var showAiSection by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var currentAiMatchResult by remember { mutableStateOf(aiMatchResult) }
    var showSocialCauses by rememberSaveable { mutableStateOf(false) } // New state for Social Causes
    val context = LocalContext.current // ✅ declare at the top of the Composable

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
                                context = context, // ← ADD THIS
                                coroutineScope = coroutineScope,
                                currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                                    ?: return@Button,
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
                contentDescription = if (isExpanded) stringResource(R.string.collapse) else stringResource(
                    R.string.expand
                ),
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
    val database = FirebaseRefs.db
    val timestamp = System.currentTimeMillis()

    // 1) Record your swipe-right
    val swipeData = SwipeData(liked = true, timestamp = timestamp)
    database.getReference("swipes/$currentUserId/$otherUserId").setValue(swipeData)
    database.getReference("likesGiven/$currentUserId/$otherUserId").setValue(timestamp)
    database.getReference("likesReceived/$otherUserId/$currentUserId").setValue(timestamp)
    database.getReference("swipesReceived/$otherUserId/$currentUserId").setValue(true)

    // 2) Increment their counters
    database.getReference("users/$otherUserId/numberOfUsersWhoSwiped")
        .get().addOnSuccessListener { snap ->
            val cnt = snap.getValue(Double::class.java) ?: 0.0
            database.getReference("users/$otherUserId/numberOfUsersWhoSwiped")
                .setValue(cnt + 1)
        }
    database.getReference("users/$otherUserId/numberOfSwipeRights")
        .get().addOnSuccessListener { snap ->
            val cnt = snap.getValue(Int::class.java) ?: 0
            database.getReference("users/$otherUserId/numberOfSwipeRights")
                .setValue(cnt + 1)
        }

    // 3) Check if they already liked you
    database.getReference("swipes/$otherUserId/$currentUserId")
        .get().addOnSuccessListener { snap ->
            val theyLikedYou = snap.getValue(SwipeData::class.java)?.liked == true
            if (theyLikedYou) {
                // a) Write match entries
                database.getReference("matches/$currentUserId/$otherUserId")
                    .setValue(timestamp)
                database.getReference("matches/$otherUserId/$currentUserId")
                    .setValue(timestamp)

                // b) Fire the in-app “It’s a Match!” popup
                profileViewModel.triggerMatchPopUp(currentUserId, otherUserId)

                // c) Fetch your compliment and inject it as the first chat message
                database.getReference("compliments/$currentUserId/$otherUserId")
                    .get().addOnSuccessListener { cSnap ->
                        val text     = cSnap.child("text").getValue(String::class.java) ?: ""
                        val voiceUrl = cSnap.child("voiceUrl").getValue(String::class.java)
                        if (text.isNotBlank() || voiceUrl != null) {
                            // generate a push key for the message ID
                            val pushKey = database
                                .reference
                                .child("messages/$currentUserId/$otherUserId")
                                .push().key
                                ?: return@addOnSuccessListener

                            val msg = Message(
                                id         = pushKey,
                                senderId   = currentUserId,
                                receiverId = otherUserId,
                                text       = text,
                                timestamp  = timestamp,
                                read       = false,
                                mediaType  = if (voiceUrl != null) "voice" else null,
                                mediaUrl   = voiceUrl,
                                processed  = false
                            )

                            // write under both users’ message threads
                            database.getReference("messages/$currentUserId/$otherUserId/$pushKey")
                                .setValue(msg)
                            database.getReference("messages/$otherUserId/$currentUserId/$pushKey")
                                .setValue(msg)
                        }
                    }
            }
        }
}

fun handleSwipeLeft(currentUserId: String, otherUserId: String) {
    val database = FirebaseRefs.db
    val timestamp = System.currentTimeMillis()

    val currentUserSwipesRef = database.getReference("swipes/$currentUserId/$otherUserId")
    currentUserSwipesRef.setValue(SwipeData(liked = false, timestamp = timestamp))

    val otherUserTotalSwipesRef =
        database.getReference("swipesReceived/$otherUserId/$currentUserId")
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
    val database = FirebaseRefs.db
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
    context: Context,
    coroutineScope: CoroutineScope,
    currentUserId: String,
    currentUserProfile: Profile,
    otherProfile: Profile,
    onComplete: (AiMatchCheckResult) -> Unit
) {
    val (finalScore, insights) = calculateExhaustiveCompatibilityScore(
        context,
        currentUserProfile,
        otherProfile
    )

    val breakdownText = insights.joinToString(separator = "\n") { "${it.emoji} ${it.text}" }

    val summaryText = context.getString(
        R.string.match_summary_format,
        finalScore,
        breakdownText
    )

    Log.d("runAiMatchCheck", "Compatibility Summary: $summaryText")

    val result = AiMatchCheckResult(
        summary = summaryText,
        totalMatchPercentage = finalScore,
        compatibilityBreakdown = breakdownText,
        timestamp = System.currentTimeMillis()
    )

    FirebaseRefs.db
        .getReference("aiMatchCheck/$currentUserId/${otherProfile.userId}")
        .setValue(result)

    coroutineScope.launch(Dispatchers.Main) {
        onComplete(result)
    }
}


data class MatchInsight(val emoji: String, val text: String, val isPositive: Boolean)

fun calculateExhaustiveCompatibilityScore(
    context: Context,
    profileA: Profile,
    profileB: Profile
): Pair<Int, List<MatchInsight>> {
    var score = 0.0
    val maxScore = 100.0
    val insights = mutableListOf<MatchInsight>()

    fun resolveField(primary: String, fallback: String?): String {
        return if (primary.trim().isNotEmpty()) primary.trim() else (fallback?.trim() ?: "")
    }

    fun compareStringField(a: String, b: String, label: String, points: Double): Double {
        val aTrim = a.orEmpty().trim()
        val bTrim = b.orEmpty().trim()
        return when {
            aTrim.isEmpty() && bTrim.isEmpty() -> {
                insights += MatchInsight("ℹ️", context.getString(R.string.lifestyle_not_set_suffix, label), false)
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

    // 1. Education
    val collegeA = resolveField(profileA.college, profileA.customCollege)
    val collegeB = resolveField(profileB.college, profileB.customCollege)
    if (collegeA.isNotBlank() && collegeA.equals(collegeB, true)) {
        score += 6.0
    } else {
        insights += MatchInsight(
            "⚠️",
            context.getString(R.string.college_mismatch_format, collegeA, collegeB),
            false
        )
    }

    val pgA = resolveField(profileA.postGraduation ?: "", profileA.customPostGraduation)
    val pgB = resolveField(profileB.postGraduation ?: "", profileB.customPostGraduation)
    if (pgA.isNotBlank() && pgA.equals(pgB, true)) {
        score += 6.0
    } else {
        insights += MatchInsight(
            "⚠️",
            context.getString(R.string.post_graduation_mismatch_format, pgA, pgB),
            false
        )
    }

    // 2. Work & Job Role
    score += compareStringField(profileA.work, profileB.work, context.getString(R.string.workplace_label), 8.0)
    score += compareStringField(profileA.jobRole, profileB.jobRole, context.getString(R.string.job_role_label), 5.0)

    // 3. Love Language
    score += compareStringField(profileA.loveLanguage, profileB.loveLanguage, context.getString(R.string.love_language_label), 4.0)

    // 4. Relationship Intent
    score += compareStringField(profileA.lookingFor, profileB.lookingFor, context.getString(R.string.relationship_intent_label), 6.0)

    // 5. Community & Religion
    val communityA = profileA.community.trim()
    val communityB = profileB.community.trim()
    if (communityA.equals(communityB, true) && communityA.isNotBlank()) {
        score += 5.0
        insights += MatchInsight("✅", context.getString(R.string.community_match_format, communityA), true)
    } else {
        insights += MatchInsight("⚠️", context.getString(R.string.community_match_format, "$communityA vs $communityB"), false)
    }

    val religionA = profileA.religion.trim()
    val religionB = profileB.religion.trim()
    if (religionA.equals(religionB, true) && religionA.isNotBlank()) {
        score += 4.0
        insights += MatchInsight("✅", context.getString(R.string.religion_match_format, religionA), true)
    } else {
        insights += MatchInsight("⚠️", context.getString(R.string.religion_match_format, "$religionA vs $religionB"), false)
    }

    // 6. City
    val cityA = profileA.city.trim()
    val cityB = profileB.city.trim()
    if (cityA.equals(cityB, true) && cityA.isNotBlank()) {
        score += 4.0
    } else {
        insights += MatchInsight("⚠️", context.getString(R.string.city_mismatch_format, cityA, cityB), false)
    }


    // 7. Preferred Language
    score += compareStringField(profileA.preferredLanguage, profileB.preferredLanguage, context.getString(R.string.preferred_language_label), 3.0)

    // 8. Interests
    val interestsA = profileA.interests.map { it.name.trim() }.filter { it.isNotEmpty() }.toSet()
    val interestsB = profileB.interests.map { it.name.trim() }.filter { it.isNotEmpty() }.toSet()
    val sharedInterests = interestsA.intersect(interestsB)
    if (sharedInterests.isNotEmpty()) {
        val bonus = (sharedInterests.size * 2).coerceAtMost(8)
        score += bonus.toDouble()
        insights += MatchInsight("✅", context.getString(R.string.shared_interests_prefix, sharedInterests.joinToString()), true)
    } else {
        if (interestsA.isEmpty() && interestsB.isEmpty()) {
            insights += MatchInsight("ℹ️", context.getString(R.string.interests_not_set), false)
        } else {
            insights += MatchInsight("⚠️", context.getString(R.string.no_common_interests), false)
        }
    }

    // 9. Social Causes
    val causesA = profileA.socialCauses.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val causesB = profileB.socialCauses.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val sharedCauses = causesA.intersect(causesB)
    if (sharedCauses.isNotEmpty()) {
        val bonus = (sharedCauses.size * 2).coerceAtMost(6)
        score += bonus.toDouble()
        insights += MatchInsight("✅", context.getString(R.string.shared_social_causes_prefix, sharedCauses.joinToString()), true)
    } else {
        if (causesA.isEmpty() && causesB.isEmpty()) {
            insights += MatchInsight("ℹ️", context.getString(R.string.social_causes_not_set), false)
        } else {
            insights += MatchInsight("⚠️", context.getString(R.string.no_common_social_causes), false)
        }
    }

    // 10. Zodiac
    val zodiacA = if (!profileA.zodiac.isNullOrBlank()) profileA.zodiac!! else deriveZodiac(profileA.dob)
    val zodiacB = if (!profileB.zodiac.isNullOrBlank()) profileB.zodiac!! else deriveZodiac(profileB.dob)
    if (zodiacA != "Unknown" && zodiacB != "Unknown") {
        if (isZodiacCompatible(zodiacA, zodiacB)) {
            score += 5.0
            insights += MatchInsight("✅", context.getString(R.string.zodiac_compatibility_prefix, zodiacA, zodiacB), true)
        } else {
            insights += MatchInsight("⚠️", context.getString(R.string.zodiac_mismatch_prefix, zodiacA, zodiacB), false)
        }
    } else {
        insights += MatchInsight("ℹ️", context.getString(R.string.zodiac_not_set), false)
    }

    // 11. Matrimony Mode
    if (profileA.isMatrimonyMode && profileB.isMatrimonyMode) {
        score += 3.0
        score += compareStringField(profileA.marriageTimeline.orEmpty(), profileB.marriageTimeline.orEmpty(), context.getString(R.string.marriage_timeline_label), 3.0)
    } else if (profileA.isMatrimonyMode != profileB.isMatrimonyMode) {
        insights += MatchInsight("⚠️", context.getString(R.string.matrimony_mismatch), false)
    } else {
        insights += MatchInsight("ℹ️", context.getString(R.string.matrimony_not_set), false)
    }

    // 12. Relocation
    val relocA = profileA.relocationPreference.orEmpty().trim()
    val relocB = profileB.relocationPreference.orEmpty().trim()
    if (relocA.equals(relocB, true) && relocA.isNotBlank()) {
        score += 4.0
    } else {
        insights += MatchInsight("⚠️", context.getString(R.string.relocation_mismatch_format, relocA, relocB), false)
    }

    // 13. Post-Marriage Career
    val careerA = profileA.postMarriageCareerPlan.orEmpty().trim()
    val careerB = profileB.postMarriageCareerPlan.orEmpty().trim()
    if (careerA.equals(careerB, true) && careerA.isNotBlank()) {
        score += 3.0
    } else {
        insights += MatchInsight("⚠️", context.getString(R.string.career_plan_mismatch_format, careerA, careerB), false)
    }

    // 14. Cultural Mindset
    val cultureA = profileA.traditionalVsLiberal.orEmpty().trim()
    val cultureB = profileB.traditionalVsLiberal.orEmpty().trim()
    if (cultureA.equals(cultureB, true) && cultureA.isNotBlank()) {
        score += 3.0
    } else {
        insights += MatchInsight("⚠️", context.getString(R.string.cultural_mindset_mismatch_format, cultureA, cultureB), false)
    }

    // 15. Lifestyle
    val lifestyleFieldNames = listOf(
        context.getString(R.string.lifestyle_smoking),
        context.getString(R.string.lifestyle_drinking),
        context.getString(R.string.lifestyle_indoor_outdoor),
        context.getString(R.string.lifestyle_sexual_activity),
        context.getString(R.string.lifestyle_sociability),
        context.getString(R.string.lifestyle_social_media),
        context.getString(R.string.lifestyle_dietary_preferences),
        context.getString(R.string.lifestyle_sleep),
        context.getString(R.string.lifestyle_work_life_balance),
        context.getString(R.string.lifestyle_exercise),
        context.getString(R.string.lifestyle_adventurousness),
        context.getString(R.string.lifestyle_family_oriented),
        context.getString(R.string.lifestyle_intellectual_curiosity),
        context.getString(R.string.lifestyle_creative_expression),
        context.getString(R.string.lifestyle_physical_fitness),
        context.getString(R.string.lifestyle_spirituality),
        context.getString(R.string.lifestyle_humor),
        context.getString(R.string.lifestyle_professional_ambition),
        context.getString(R.string.lifestyle_environmental_awareness),
        context.getString(R.string.lifestyle_culinary_enthusiasm),
        context.getString(R.string.lifestyle_political_awareness),
        context.getString(R.string.lifestyle_community_engagement),
        context.getString(R.string.lifestyle_sports),
    )

    val lifestylePairs = listOf(
        profileA.lifestyle?.smoking_habit to profileB.lifestyle?.smoking_habit,
        profileA.lifestyle?.drinking_habit to profileB.lifestyle?.drinking_habit,
        profileA.lifestyle?.indoor_outdoor_orientation to profileB.lifestyle?.indoor_outdoor_orientation,
        profileA.lifestyle?.sexual_activity_level to profileB.lifestyle?.sexual_activity_level,
        profileA.lifestyle?.sociability to profileB.lifestyle?.sociability,
        profileA.lifestyle?.social_media_engagement to profileB.lifestyle?.social_media_engagement,
        profileA.lifestyle?.dietary_preferences to profileB.lifestyle?.dietary_preferences,
        profileA.lifestyle?.sleep_pattern to profileB.lifestyle?.sleep_pattern,
        profileA.lifestyle?.work_life_balance to profileB.lifestyle?.work_life_balance,
        profileA.lifestyle?.exercise_frequency to profileB.lifestyle?.exercise_frequency,
        profileA.lifestyle?.adventurousness to profileB.lifestyle?.adventurousness,
        profileA.lifestyle?.family_orientated to profileB.lifestyle?.family_orientated,
        profileA.lifestyle?.intellectual_curiosity to profileB.lifestyle?.intellectual_curiosity,
        profileA.lifestyle?.creative_expression to profileB.lifestyle?.creative_expression,
        profileA.lifestyle?.physical_fitness to profileB.lifestyle?.physical_fitness,
        profileA.lifestyle?.spirituality_mindfulness to profileB.lifestyle?.spirituality_mindfulness,
        profileA.lifestyle?.easy_goingness to profileB.lifestyle?.easy_goingness,
        profileA.lifestyle?.professional_ambition to profileB.lifestyle?.professional_ambition,
        profileA.lifestyle?.environmental_awareness to profileB.lifestyle?.environmental_awareness,
        profileA.lifestyle?.culinary_enthusiasm to profileB.lifestyle?.culinary_enthusiasm,
        profileA.lifestyle?.political_awareness to profileB.lifestyle?.political_awareness,
        profileA.lifestyle?.community_engagement to profileB.lifestyle?.community_engagement,
        profileA.lifestyle?.sports_enthusiasm to profileB.lifestyle?.sports_enthusiasm,
    )

    var lifestyleMatched = 0
    var lifestyleCompared = 0
    val lifestyleMatchesList = mutableListOf<String>()
    val lifestyleMismatchesList = mutableListOf<String>()

    for ((index, pair) in lifestylePairs.withIndex()) {
        val label = lifestyleFieldNames.getOrElse(index) { context.getString(R.string.lifestyle_field_default) }
        val a = pair.first
        val b = pair.second
        when {
            a is Int && b is Int -> {
                if (a == -1 && b == -1) {
                    lifestyleMatchesList.add(context.getString(R.string.lifestyle_not_set_suffix, label))
                } else if (a == -1 || b == -1) {
                    lifestyleCompared++
                    lifestyleMismatchesList.add(context.getString(R.string.lifestyle_one_not_set_suffix, label))
                } else {
                    lifestyleCompared++
                    if (a == b) {
                        lifestyleMatched++
                        lifestyleMatchesList.add(label)
                    } else {
                        lifestyleMismatchesList.add("$label: $a vs $b")
                    }
                }
            }

            a is Boolean && b is Boolean -> {
                lifestyleCompared++
                if (a == b) {
                    lifestyleMatched++
                    lifestyleMatchesList.add(label)
                } else {
                    lifestyleMismatchesList.add("$label: $a vs $b")
                }
            }

            a is String && b is String -> {
                if (a.isBlank() && b.isBlank()) {
                    lifestyleMatchesList.add(context.getString(R.string.lifestyle_not_set_suffix, label))
                } else if (a.isBlank() || b.isBlank()) {
                    lifestyleCompared++
                    lifestyleMismatchesList.add(context.getString(R.string.lifestyle_one_not_set_suffix, label))
                } else {
                    lifestyleCompared++
                    if (a.equals(b, ignoreCase = true)) {
                        lifestyleMatched++
                        lifestyleMatchesList.add(label)
                    } else {
                        lifestyleMismatchesList.add("$label: '$a' vs '$b'")
                    }
                }
            }
        }
    }

    if (lifestyleCompared > 0) {
        if (lifestyleCompared < 5) {
            if (lifestyleMatched > 0) {
                insights += MatchInsight("✅", context.getString(R.string.lifestyle_match_format, lifestyleMatched, lifestyleMatchesList.joinToString(", ")), true)
            } else {
                insights += MatchInsight("⚠️", context.getString(R.string.lifestyle_no_traits_to_compare), false)
            }
        } else {
            val percentage = (lifestyleMatched.toDouble() / lifestyleCompared.toDouble()) * 100
            insights += MatchInsight(
                "✅",
                context.getString(R.string.lifestyle_similarity_format, "%.1f".format(percentage), lifestyleCompared, lifestyleMatchesList.joinToString(", ").ifEmpty { context.getString(R.string.lifestyle_none_common) }),
                true
            )
        }
    } else {
        insights += MatchInsight("ℹ️", context.getString(R.string.lifestyle_not_set_in_profiles), false)
    }

    if (lifestyleMismatchesList.isNotEmpty()) {
        insights += MatchInsight("⚠️", context.getString(R.string.lifestyle_mismatches_format, lifestyleMismatchesList.joinToString("; ")), false)
    }

    // 16. Tags
    val tagsA = profileA.userTags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val tagsB = profileB.userTags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val sharedTags = tagsA.intersect(tagsB)
    if (sharedTags.isNotEmpty()) {
        val bonus = (sharedTags.size).coerceAtMost(2)
        score += bonus.toDouble()
        insights += MatchInsight("✅", context.getString(R.string.shared_tags_prefix, sharedTags.joinToString()), true)
    } else {
        insights += MatchInsight("ℹ️", context.getString(R.string.tags_not_set_or_no_overlap), false)
    }

    val finalScore = score.coerceAtMost(maxScore).toInt()
    return finalScore to insights
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

suspend fun calculateDistance(userId1: String, userId2: String, geoFire: GeoFire): Float? =
    withContext(Dispatchers.Default) {
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

@Composable
fun ComplimentDialog(
    complimentsLeft: Int,                       // NEW
    onSend: (String?, Uri?) -> Unit,
    onDismiss: () -> Unit
){
    var complimentText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var audioFileUri by remember { mutableStateOf<Uri?>(null) }
    var recorder: MediaRecorder? by remember { mutableStateOf(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {
            if (isRecording) {
                recorder?.stop()
                recorder?.release()
                isRecording = false
            }
            onDismiss()
        },
        title = {
            Text(text = "Send a Compliment", color = Color.White)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = complimentText,
                    onValueChange = { complimentText = it },
                    label = { Text("Your message (optional)", color = Color.White.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color(0xFFFF6F00),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.White,
                        textColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                recorder?.stop()
                                recorder?.release()
                                recorder = null
                                isRecording = false
                            } else {
                                val audioFile = File.createTempFile("compliment_", ".aac", context.cacheDir)
                                recorder = MediaRecorder().apply {
                                    setAudioSource(MediaRecorder.AudioSource.MIC)
                                    setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                    setOutputFile(audioFile.absolutePath)
                                    prepare()
                                    start()
                                }
                                audioFileUri = audioFile.toUri()
                                isRecording = true
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isRecording) Color.Red else Color(0xFFFF6F00),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (isRecording) "Recording..." else "Tap to record",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                if (audioFileUri != null && !isRecording) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Voice compliment ready!",
                        color = Color.Green,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = (complimentText.isNotBlank() || audioFileUri != null) && complimentsLeft > 0,
                onClick = {
                    onSend(complimentText.trim().takeIf { it.isNotEmpty() }, audioFileUri)
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00))
            ) {
                Text("Send", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        backgroundColor = Color(0xFF1A1A1A),
        contentColor = Color.White
    )
}
