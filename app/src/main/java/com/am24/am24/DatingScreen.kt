@file:OptIn(
    ExperimentalMaterialApi::class, ExperimentalMaterialApi::class,
    ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class
)
package com.am24.am24

import DatingViewModel
import android.app.Activity
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import android.widget.Toast
/* Material 3 (add these – they won’t clash with existing M2 widgets) */
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme     as M3Theme
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
/* Icons */
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.EmojiEmotions  // or whichever icon you prefer for “Compliment”
import androidx.compose.material.icons.filled.FlashOn         // for “Boost”
import androidx.compose.material.icons.filled.Info
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.filled.AttachEmail
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.IconButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.navigation.compose.currentBackStackEntryAsState
import com.am24.am24.ui.CompatibilityMeter
import com.am24.am24.ui.theme.DarkGrayBackground
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.io.File

/* DatingScreen.kt  – add near the top, after imports */
private fun Iterable<*>.dump(tag: String) =
    Log.d("DS-FLOW", "$tag  size=${count()}  →  ${joinToString { (it as? Profile)?.userId ?: it.toString() }}")

/* Prints a plain Set<String> nicely */
private fun Set<*>.dump(tag: String) =
    Log.d("DS-FLOW", "$tag  size=${size}  →  ${joinToString()}")

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
            fontSize = 11.sp,
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
    datingViewModel: DatingViewModel = viewModel(),
    modifier: Modifier = Modifier,
    initialQuery: String = ""
) {
    val profileViewModel: ProfileViewModel = viewModel()
    val postViewModel: PostViewModel       = viewModel()
    val coroutineScope                     = rememberCoroutineScope()
    var showSwipeLimitOverlay by remember { mutableStateOf(false) }
    val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var likers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dailyLoginInfo by remember { mutableStateOf<DailyLoginInfo?>(null) }

    // 1) watch for “are we still on the Dating route?”
    val backstackEntry by navController.currentBackStackEntryAsState()
    val currentRoute    = backstackEntry?.destination?.route

    /* Auto-tap counter for empty profiles */
    var autoTapCount by rememberSaveable { mutableStateOf(0) }
    val maxAutoTaps = 0

    /* fetch *my* Profile once */
    LaunchedEffect(Unit) {
        profileViewModel.fetchCurrentUserProfile()
        datingViewModel.startInventoryWatcher(myUid)        // NEW  ←───────────────★
        dailyLoginInfo = checkDailyLoginReward()
    }

    // ── StateFlows ────────────────────────────────────────────────────
    val myProfile by profileViewModel.currentUserProfile.collectAsState()
    if (myProfile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val filters           by datingViewModel.datingFilters.collectAsState()
    var localDistance by remember(filters.distance) { mutableStateOf(filters.distance) }
    val filteredProfiles  by datingViewModel.displayingProfiles.collectAsState()
    val allProfiles       by datingViewModel.allProfiles.collectAsState()
    val isLoading         by datingViewModel.isLoading.collectAsState()
    val loadingProgress   by datingViewModel.loadingProgress.collectAsState()
    val matchPopUpState   by profileViewModel.matchPopUpState.collectAsState()
    val boostedUsers      by datingViewModel.boostedUsers.collectAsState()
    val complimentsLeft   by datingViewModel.complimentsLeft.collectAsState()
    val complimentsRecv   by datingViewModel.complimentsReceived.collectAsState()
    val isIndian = myProfile?.country.equals("India", true)
    // ── Misc local state ─────────────────────────────────────────────
    var excludedUserIds   by remember { mutableStateOf(emptySet<String>()) }
    var remainingSwipes   by remember { mutableStateOf(0) }
    var swipesLoaded      by remember { mutableStateOf(false) }
    var showBoostFlash    by rememberSaveable { mutableStateOf(false) }
    val isPremium by profileViewModel.isPremium.collectAsState(false)
    val isPlus    by profileViewModel.isPlus   .collectAsState(false)

    // constants
    val BOOST_DURATION = 1 * 60 * 60 * 1000L
    val now = remember { System.currentTimeMillis() }
    val last = myProfile?.lastBoostTimestamp ?: 0L
    val inCooldown = now - last < BOOST_DURATION
    val availableBoosts = myProfile?.availableBoosts ?: 0
    val canBoost = availableBoosts > 0 && !inCooldown

    var showComplimentDlg by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = LocalContext.current as Activity
    val rewardedBoostManager = remember { RewardedAdManager(activity, AdUnitIds.rewardedBoost(activity)) }
    val rewardedComplimentManager = remember { RewardedAdManager(activity, AdUnitIds.rewardedCompliment(activity)) }
    val rewardedSwipeManager = remember { RewardedAdManager(activity, AdUnitIds.rewardedSwipe(activity)) }

    DisposableEffect(Unit) {
        onDispose {
            rewardedBoostManager.clearCallbacks()
            rewardedComplimentManager.clearCallbacks()
            rewardedSwipeManager.clearCallbacks()
        }
    }

    LaunchedEffect(Unit) {
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            excludedUserIds = fetchExcludedUsers(uid)
            excludedUserIds.dump("EXCLUDED_UIDS")   // <-- NEW LOG LINE
            profileViewModel.fetchCurrentUserProfile()
            remainingSwipes = loadAndResetSwipesDaily(uid)
            swipesLoaded    = true
            datingViewModel.refreshFilteredProfiles()
        }
    }


    LaunchedEffect(myUid) {
        // grab everyone who’s liked me
        val snap = FirebaseRefs.db
            .getReference("likesReceived/$myUid")
            .get()
            .await()
        likers = snap.children.mapNotNull { it.key }.toSet()
    }

    if (!isPremium && !isIndian) dailyLoginInfo?.let { info ->
        val kupidxOrange = Color(0xFFFF6F00)
        AlertDialog(
            onDismissRequest = { dailyLoginInfo = null },
            confirmButton = {
                TextButton(onClick = { dailyLoginInfo = null }) { Text("OK", color = kupidxOrange) }
            },
            title = { Text(stringResource(R.string.daily_login_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (i in 1..5) {
                            val checked = i <= info.streak
                            Icon(
                                imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (checked) Color(0xFF4CAF50) else MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.daily_login_message, info.streak))
                    if (info.rewardHours > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.daily_login_plus_award, info.rewardHours))
                    }
                }
            }
        )
    }


    // —— see which Profile objects are being dropped ————————————————
    val excludedProfiles = filteredProfiles.filter { it.userId in excludedUserIds }
    excludedProfiles.dump("EXCLUDED_PROFILES")          // <-- NEW LOG
    // ─────────────────────────────────────────────────────────────────
    //   BUILD DISPLAY LIST  (must come *before* we use it)
    // ─────────────────────────────────────────────────────────────────
    val base by remember(filteredProfiles, excludedUserIds, likers) {
        derivedStateOf {
            filteredProfiles
                .filter { it.userId.isNotBlank() }
                .filter { it.userId !in excludedUserIds }
                // hide private profiles, unless *they* liked you:
                .filter { prof -> !prof.isPrivate || prof.userId in likers }
                .also { it.dump("BASE") }
        }
    }

    val premiumList by remember(base) {
        derivedStateOf { base.also { it.dump("PREM") }.filter { it.isPremium } }
    }
    val plusList by remember(base, premiumList) {
        derivedStateOf {
            base.filter { !it.isPremium }            // not premium
                .filter { it.isPlus }                 // plus only
                .filter { it.userId !in premiumList.map { p -> p.userId } }
                .also { it.dump("PLUS") }
        }
    }
    val complimentersList by remember(complimentsRecv, base, premiumList, plusList) {
        derivedStateOf {
            complimentsRecv.keys
                .mapNotNull { id -> base.find { it.userId == id } }
                .filter { it.userId !in premiumList.map { p -> p.userId } }
                .filter { it.userId !in plusList.map { p -> p.userId } }
                .also { it.dump("COMP") }
        }
    }
    val boostedList by remember(boostedUsers, premiumList, plusList, complimentersList, excludedUserIds) {
        derivedStateOf {
            boostedUsers
                .filter { it.userId !in premiumList.map { p -> p.userId } }
                .filter { it.userId !in plusList.map { p -> p.userId } }
                .filter { it.userId !in complimentersList.map { p -> p.userId } }
                .filter { it.userId !in excludedUserIds }
                .also { it.dump("BOOST") }
        }
    }
    val restList by remember(base, premiumList, plusList, complimentersList, boostedList) {
        derivedStateOf {
            base
                .filter { it.userId !in premiumList.map { p -> p.userId } }
                .filter { it.userId !in plusList.map { p -> p.userId } }
                .filter { it.userId !in complimentersList.map { p -> p.userId } }
                .filter { it.userId !in boostedList.map { p -> p.userId } }
                .filter { !it.isPremium && !it.isPlus }
                .also { it.dump("REST") }
        }
    }

    val displayedProfiles = premiumList + plusList + complimentersList + boostedList + restList
    Log.d("DS-FLOW", "DISPLAYED   size=${displayedProfiles.size}")

    // ── Hoisted deck pointer ─────────────────────────────────────────
    var currentIndex      by rememberSaveable { mutableStateOf(0) }
    val currentSwipeProfile by remember(currentIndex, displayedProfiles) {
        derivedStateOf { displayedProfiles.getOrNull(currentIndex) }
    }
    var aiMatchResult by remember { mutableStateOf<AiMatchCheckResult?>(null) }

    LaunchedEffect(allProfiles) { currentIndex = 0 }

//    // Keep the same top card when profiles list updates
//    LaunchedEffect(sortedDisplayedProfiles) {
//        val id = datingViewModel.currentSwipeUserId.value
//        id?.let { uid ->
//            sortedDisplayedProfiles.indexOfFirst { it.userId == uid }
//                .takeIf { it >= 0 }
//                ?.let { currentIndex = it }
//        }
//    }

    // inside DatingScreen (or DatingScreenContent) where you have `currentSwipeProfile`:
    LaunchedEffect(currentSwipeProfile?.userId) {
        Log.d("DatingScreen", "Composable – currentSwipeProfile.userId = ${currentSwipeProfile?.userId}")
        datingViewModel.setCurrentSwipeUserId(currentSwipeProfile?.userId)
        aiMatchResult = null
        val myId = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val myProf = myProfile ?: return@LaunchedEffect
        val otherId = currentSwipeProfile?.userId ?: return@LaunchedEffect
        val ref = FirebaseRefs.db
            .getReference("aiMatchCheck/$myId/$otherId")
        val snap = ref.get().await()
        val existing = snap.getValue(AiMatchCheckResult::class.java)
        if (existing != null) {
            aiMatchResult = existing
        } else {
            runAiMatchCheck(
                context = context,
                coroutineScope = coroutineScope,
                currentUserId = myId,
                currentUserProfile = myProf,
                otherProfile = currentSwipeProfile!!
            ) { result -> aiMatchResult = result }
        }
    }
    // add this:
    var initialProcessed by remember { mutableStateOf(false) }
    LaunchedEffect(initialQuery, displayedProfiles) {
        if (!initialProcessed && initialQuery.isNotBlank()) {
            // find the deep-link target
            val idx = displayedProfiles.indexOfFirst { it.userId == initialQuery }
            if (idx >= 0) {
                currentIndex = idx
            }
            initialProcessed = true
        }
        displayedProfiles.forEach { datingViewModel.loadVerification(it.userId) }
    }

    /* Auto-tap dating icon when profiles are empty */
    LaunchedEffect(currentRoute, displayedProfiles, isLoading) {
        if (currentRoute == "dating"                     // only run if we’re still here
            && !isLoading
            && displayedProfiles.isEmpty()
            && autoTapCount < maxAutoTaps
        ) {
            delay(1_000)  // give Firebase a chance to come back
            navController.navigate("dating") {
                launchSingleTop = true
                restoreState    = true
            }
            autoTapCount++
        }
    }

    /* bottom-sheet for filters */
    val sheetState = rememberModalBottomSheetState(
        initialValue     = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )

    /* cannot go on until *my* profile has loaded */
    if (myProfile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    ModalBottomSheetLayout(
        sheetState   = sheetState,
        sheetContent = {
            FiltersOverlay(
                ageRange           = filters.ageStart..filters.ageEnd,
                onAgeRangeChange   = { datingViewModel.updateDatingFilters(filters.copy(ageStart = it.start, ageEnd = it.endInclusive)) },
                maxDistance        = localDistance
                    .coerceAtMost(
                        if (isIndian && !(myProfile!!.isPlus || myProfile!!.isPremium))
                            DatingViewModel.INDIA_MAX_DISTANCE
                        else
                            DatingViewModel.WORLDWIDE_DISTANCE
                    ),
                onDistanceChange   = {
                    val limit = if (isIndian && !(myProfile!!.isPlus || myProfile!!.isPremium))
                        DatingViewModel.INDIA_MAX_DISTANCE
                    else
                        DatingViewModel.WORLDWIDE_DISTANCE
                    localDistance = it.coerceAtMost(limit)
                },
                selectedGenders    = filters.gender.split(",").toSet(),
                onGenderChange     = { datingViewModel.updateDatingFilters(filters.copy(gender = it.joinToString(","))) },
                selectedOrientation = filters.sexualOrientation,
                onOrientationChange = { datingViewModel.updateDatingFilters(filters.copy(sexualOrientation = it)) },
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
                selectedEthnicity  = filters.ethnicity,
                onEthnicityChange  = { datingViewModel.updateDatingFilters(filters.copy(ethnicity = it)) },
                selectedIncomeLevel = filters.incomeLevel,
                onIncomeLevelChange = { datingViewModel.updateDatingFilters(filters.copy(incomeLevel = it)) },
                selectedCity       = filters.city,
                onCityChange       = { datingViewModel.updateDatingFilters(filters.copy(city = it)) },
                selectedLocalities = filters.localities,
                onLocalitiesChange = { datingViewModel.updateDatingFilters(filters.copy(localities = it)) },
                onSaveFilters = {
                    coroutineScope.launch {
                        datingViewModel.updateDatingFilters(filters.copy(distance = localDistance))
                        sheetState.hide()
                        datingViewModel.refreshFilteredProfiles()
                    }
                },
                onCancel = { coroutineScope.launch { sheetState.hide() } },
                isIndian = isIndian,
                isPlus = myProfile!!.isPlus,
                isPremium = myProfile!!.isPremium,
                minRating = filters.minRating,
                onMinRatingChange = { datingViewModel.updateDatingFilters(filters.copy(minRating = it)) },
                maxRanking = filters.maxRanking,
                onMaxRankingChange = { datingViewModel.updateDatingFilters(filters.copy(maxRanking = it)) }
            )
        }
    ) {
        Column(Modifier
            .fillMaxSize()
            .background(DarkGrayBackground)
        ) {

            // ── TOOLBAR ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { coroutineScope.launch { sheetState.show() } },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Default.FilterAlt,
                        contentDescription = null,
                        tint = Color(0xFFFF6F00),
                        modifier = Modifier.size(27.dp)
                    )
                }

                // RatingBar centered
                currentSwipeProfile?.let {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        CompatibilityMeter(
                            percent = aiMatchResult?.totalMatchPercentage?.toDouble() ?: 0.0
                        )
                    }
                }

                Row {
                    /* 👍  Compliments */
                    WaterIconButton(
                        quota    = complimentsLeft,
                        maxQuota = 5,
                        icon     = Icons.Default.AttachEmail,
                        enabled  = complimentsLeft > 0,
                        tint     = if (complimentsLeft > 0) Color.White else Color.Gray,
                        onClick  = {
                            if (complimentsLeft > 0) {
                                showComplimentDlg = true
                            } else {
                                // zero left → go buy more
                                if (isIndian) {
                                    navController.navigate("buyCompliments")
                                } else {
                                    rewardedComplimentManager.showWithDailyLimit(
                                        userId = FirebaseAuth.getInstance().uid ?: return@WaterIconButton,
                                        onReward = {
                                            datingViewModel.incrementComplimentsLocal()
                                        val uid = FirebaseAuth.getInstance().uid
                                        if (uid != null) {
                                            val newVal = complimentsLeft + 1
                                            coroutineScope.launch {
                                                FirebaseRefs.db.getReference("users/$uid/availableCompliments")
                                                    .setValue(newVal)
                                            }
                                            profileViewModel.incrementComplimentsLocal()
                                        }
                                        }
                                    )
                                }
                            }
                        }
                    )

                    Spacer(Modifier.width(13.dp))

                    /* ⚡  Boosts */
                    WaterIconButton(
                        quota    = myProfile!!.availableBoosts,
                        maxQuota = 5,
                        icon     = Icons.Default.FlashOn,
                        tint     = if (canBoost) Color.White else Color.Gray,
                        enabled  = canBoost,
                        onClick  = {
                            if (canBoost) {
                                val myUid = FirebaseAuth.getInstance().uid ?: return@WaterIconButton
                                datingViewModel.boostUser(myUid) {
                                    profileViewModel.decrementBoostsLocal()
                                    showBoostFlash = true
                                    profileViewModel.fetchCurrentUserProfile()
                                }
                            } else {
                                // no boosts → go buy more
                                if (isIndian) {
                                    navController.navigate("buyBoosts")
                                } else {
                                    rewardedBoostManager.showWithDailyLimit(
                                        userId = FirebaseAuth.getInstance().uid ?: return@WaterIconButton,
                                        onReward = {
                                        profileViewModel.incrementBoostsLocal()
                                        datingViewModel.incrementBoostsLocal()
                                        val uid = FirebaseAuth.getInstance().uid
                                        if (uid != null) {
                                            val newVal = myProfile!!.availableBoosts + 1
                                            coroutineScope.launch {
                                                FirebaseRefs.db.getReference("users/$uid/availableBoosts")
                                                    .setValue(newVal)
                                            }
                                        }
                                        }
                                    )
                                }
                            }
                        }
                    )
                }

                /* yellow flash overlay on successful boost */
                AnimatedVisibility(
                    visible = showBoostFlash,
                    enter   = fadeIn(animationSpec = tween(250)),
                    exit    = fadeOut(animationSpec = tween(600))
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .border(3.dp, Color(0xFFFF6F00), CircleShape)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FlashOn, null,
                            tint = Color(0xFFFF6F00),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                LaunchedEffect(showBoostFlash) {
                    if (showBoostFlash) {
                        delay(2000)
                        showBoostFlash = false
                    }
                }
            }

            // ── DECK / LOADING / EMPTY STATES ────────────────────────
            Box(Modifier.fillMaxSize()) {
                when {
                    isLoading -> Box(
                        modifier = Modifier.align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = loadingProgress / 100f,
                            color = Color(0xFFFF6F00)
                        )
                        Text(
                            text = stringResource(R.string.percentage, loadingProgress),
                            color = KupidxOrange,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    displayedProfiles.isEmpty() -> NoMoreProfilesScreen(
                        autoTapCount = autoTapCount,
                        maxAutoTaps = maxAutoTaps,
                        onRefresh = {
                            datingViewModel.refreshFilteredProfiles()
                        }
                    )

                    else -> DatingScreenContent(
                        navController    = navController,
                        geoFire          = geoFire,
                        profileViewModel = profileViewModel,
                        postViewModel    = postViewModel,
                        profiles         = displayedProfiles,
                        currentIndex     = currentIndex,   // 🔹
                        boostedUsers     = boostedUsers,
                        onSwipeRight     = {
                            if (remainingSwipes > 0) remainingSwipes--
                            updateSwipesInFirebase(remainingSwipes)         // ← persist
                            currentIndex++
                        },
                        onSwipeLeft      = {
                            if (remainingSwipes > 0) remainingSwipes--
                            updateSwipesInFirebase(remainingSwipes)
                            currentIndex++
                        },
                        excludedUserIds = excludedUserIds,
                        onExcludeUser = { excludedUserIds = excludedUserIds + it },
                        onRefreshProfiles = {
                            datingViewModel.refreshFilteredProfiles()
                        }
                    )
                }
                // ── if they’ve exhausted swipes, show your overlay (below) ──
                if (swipesLoaded && remainingSwipes <= 0 && !showSwipeLimitOverlay) {
                    showSwipeLimitOverlay = true
                }

                // ② if they've used up all their swipes, show the pretty overlay:
                if (showSwipeLimitOverlay) {
                    SwipeLimitOverlay(
                        remainingSwipes = remainingSwipes,
                        isPlus = myProfile!!.isPlus,
                        isPremium = myProfile!!.isPremium,
                        isIndian = isIndian,
                        onWatchAd = {
                            if (isIndian) {
                                navController.navigate("buySwipes")
                                showSwipeLimitOverlay = false
                            } else {
                                rewardedSwipeManager.showWithDailyLimit(
                                    userId = FirebaseAuth.getInstance().uid ?: return@SwipeLimitOverlay,
                                    onReward = {
                                        remainingSwipes += 5
                                        updateSwipesInFirebase(remainingSwipes)
                                    },
                                    afterAd = { showSwipeLimitOverlay = false }
                                )
                            }
                        }
                    )
                }
            }
            }
        /* match pop-up, compliment dialog – unchanged from your code */
        matchPopUpState?.let { (you, them) ->
            MatchPopUp(
                you.profilepicUrl.orEmpty(),
                them.profilepicUrl.orEmpty(),
                onChatClick = {
                    profileViewModel.clearMatchPopUp()
                    navController.navigate("chat/${them.userId}")
                },
                onClose = { profileViewModel.clearMatchPopUp() }
            )
        }
        if (showComplimentDlg && displayedProfiles.isNotEmpty()) {
            ComplimentDialog(
                complimentsLeft = complimentsLeft,
                onSend = { text, voiceUri ->
                    coroutineScope.launch {
                        val receiver = displayedProfiles[currentIndex]
                        datingViewModel.sendCompliment(
                            receiverId       = receiver.userId,
                            textMessage      = text,
                            voiceUri         = voiceUri,
                            profileViewModel = profileViewModel
                        )
                        excludedUserIds += receiver.userId
                        showComplimentDlg = false
                    }
                },
                onDismiss = { showComplimentDlg = false }
            )
        }
    }
}

/* new composable – put near IconWithQuota, or in the same file */
@Composable
fun WaterIconButton(
    quota: Int,
    maxQuota: Int,
    icon: ImageVector,
    enabled: Boolean = true,
    tint: Color = Color.White,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = quota.coerceIn(0, maxQuota) / maxQuota.toFloat(),
        animationSpec = tween(400)
    )
    val side = 34.dp
    Box(
        modifier = modifier
            .size(side)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        /* container + “water” fill */
        Canvas(Modifier.matchParentSize()) {
            val h = size.height
            // dark vessel
            drawCircle(Color.DarkGray)
            // orange water that rises / falls
            drawRect(
                color = if (enabled) Color(0xFFFF6F00) else Color.Gray,
                topLeft = Offset(0f, h * (1f - progress)),
                size = Size(size.width, h * progress)
            )
        }
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}


/**
 * Load swipes from Firebase and reset them to 15 if a new day has started.
 */
suspend fun loadAndResetSwipesDaily(userId: String): Int {
    val userRef  = FirebaseRefs.db.getReference("users/$userId")
    val userSnap = userRef.get().await()

    val isPremium = userSnap.child("isPremium").getValue(Boolean::class.java) ?: false
    val isPlus    = userSnap.child("isPlus").getValue(Boolean::class.java) ?: false
    val quota     = when {
        isPremium -> Int.MAX_VALUE
        isPlus    -> 50
        else      -> 20
    }

    val swipesRef = userRef.child("swipesInfo")
    val snap      = swipesRef.get().await()

    val snapRemaining = snap.child("remainingSwipes").getValue(Int::class.java) ?: quota
    val lastReset     = snap.child("lastResetDayOfYear").getValue(Int::class.java) ?: -1
    val today   = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val newDay  = today != lastReset

    /* ── New day?  Top-up only if user was below their quota ─────── */
    /* ── New day or tier upgrade?  Always restore to quota ────────── */
    var remaining = snapRemaining

    /* ── New day?  Always restore to quota ───────────────────────── */
    if (newDay) {
        remaining = quota
    }

    /* ── Persist back if anything changed ────────────────────────── */
    if (newDay || remaining != snapRemaining) {
        swipesRef.child("remainingSwipes").setValue(remaining)
        swipesRef.child("lastResetDayOfYear").setValue(today)
    }

    return remaining
}

/** Updates the user's remainingSwipes in Firebase. */
fun updateSwipesInFirebase(newSwipesCount: Int) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val swipesRef = FirebaseRefs.db.getReference("users/$userId/swipesInfo")
    swipesRef.child("remainingSwipes").setValue(newSwipesCount)
}


@Composable
fun SwipeLimitOverlay(
    remainingSwipes: Int,
    isPlus: Boolean,
    isPremium: Boolean,
    isIndian: Boolean,
    onWatchAd: () -> Unit
) {
    // compute your daily quota
    val quota = when {
        isPremium  -> Int.MAX_VALUE
        isPlus     -> 50
        else       -> 20
    }

    // countdown to midnight
    var timeLeft by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while(true) {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE,      0)
                set(Calendar.SECOND,      0)
                set(Calendar.MILLISECOND, 0)
            }
            val diff = cal.timeInMillis - now
            val h = diff / 3_600_000
            val m = (diff % 3_600_000) / 60_000
            val s = (diff % 60_000) / 1000
            timeLeft = String.format("%02d:%02d:%02d", h, m, s)
            delay(1000)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DarkGrayBackground.copy(alpha = 0.8f))
            .pointerInput(Unit) {},   // eat all touches
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                Modifier
                    .background(Color(0xFF1A1A1A))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Swipes Remaining", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("$remainingSwipes / ${if (quota == Int.MAX_VALUE) "∞" else quota}", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Text("Resets in: $timeLeft", color = Color.Gray)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = onWatchAd,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00))
                    ) {
                        val label = if (isIndian) "Buy Swipes" else "Watch ad for 5 Swipes"
                        Text(label, color = Color.Black)
                    }
                }
            }
        }
    }
}


/* ––– Extra helper: quick Premium‑lock composable ––– */
@Composable fun Locked(label: String) {
    val ctx = LocalContext.current       // ← add this line
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .background(Color.DarkGray, RoundedCornerShape(8.dp))
            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ctx.getString(R.string.upgrade_to_premium_to_unlock),
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
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
    selectedOrientation: String,
    onOrientationChange: (String) -> Unit,
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
    selectedCity: String,
    onCityChange: (String) -> Unit,
    selectedEthnicity: String,
    onEthnicityChange: (String) -> Unit,
    selectedIncomeLevel: String,
    onIncomeLevelChange: (String) -> Unit,
    selectedLocalities: List<String>,
    onLocalitiesChange: (List<String>) -> Unit,
    onSaveFilters: () -> Unit,
    onCancel: () -> Unit,
    isIndian: Boolean,

    // ← no change here: these two flags come from DatingScreen’s myProfile!!.isPlus / isPremium
    isPlus: Boolean,
    isPremium: Boolean,

    // the current values of each “power” filter (even if locked)
    minRating: Float,
    onMinRatingChange: (Float) -> Unit,
    maxRanking: Int,
    onMaxRankingChange: (Int) -> Unit
) {
    val context = LocalContext.current

    val limit = if (isIndian && !(isPlus || isPremium))
        DatingViewModel.INDIA_MAX_DISTANCE
    else
        DatingViewModel.WORLDWIDE_DISTANCE

    var pendingDistance by remember { mutableStateOf(maxDistance.coerceAtMost(limit)) }

    LaunchedEffect(maxDistance, limit) {
        pendingDistance = maxDistance.coerceAtMost(limit)
    }
    // Local state for place search
    var highSchoolQuery by remember { mutableStateOf(selectedHighSchool) }
    var collegeQuery by remember { mutableStateOf(selectedCollege) }
    var postGradQuery by remember { mutableStateOf(selectedPostGrad) }

    var highSchoolResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var collegeResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var postGradResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }

    var highSchoolSearching by remember { mutableStateOf(false) }
    var collegeSearching by remember { mutableStateOf(false) }
    var postGradSearching by remember { mutableStateOf(false) }

    var isHighSchoolFieldFocused by remember { mutableStateOf(false) }
    var isCollegeFieldFocused by remember { mutableStateOf(false) }
    var isPostGradFieldFocused by remember { mutableStateOf(false) }


    var cityInput      by remember { mutableStateOf(selectedCity) }
    var localitiesInput by remember { mutableStateOf(selectedLocalities.joinToString(", ")) }


    LaunchedEffect(highSchoolQuery) {
        if (highSchoolQuery.length < 3) {
            highSchoolResults = emptyList()
        } else {
            delay(400)
            highSchoolSearching = true
            highSchoolResults = searchPlacesRich(highSchoolQuery)
            highSchoolSearching = false
        }
    }

    LaunchedEffect(collegeQuery) {
        if (collegeQuery.length < 3) {
            collegeResults = emptyList()
        } else {
            delay(400)
            collegeSearching = true
            collegeResults = searchPlacesRich(collegeQuery)
            collegeSearching = false
        }
    }

    LaunchedEffect(postGradQuery) {
        if (postGradQuery.length < 3) {
            postGradResults = emptyList()
        } else {
            delay(400)
            postGradSearching = true
            postGradResults = searchPlacesRich(postGradQuery)
            postGradSearching = false
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(16.dp)
    ) {
        // ─── “Filters” title + Save button ───────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    onDistanceChange(pendingDistance)
                    onSaveFilters()
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00)),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FilterAltOff,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.save), color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            // ─── BASIC Filters ─────────────────────────────────────────────
            item {
                FilterSectionTitle(title = stringResource(R.string.basic_filters))
                Spacer(modifier = Modifier.height(8.dp))


                Spacer(Modifier.height(24.dp))

                // Gender pills (pre‐populated via selectedGenders)
                Text(stringResource(R.string.gender_preference), color = Color.White)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        stringResource(R.string.male_option),
                        stringResource(R.string.female_option)
                    ).forEach { gender ->
                        Button(
                            onClick = {
                                val updated = if (selectedGenders.contains(gender)) {
                                    selectedGenders - gender
                                } else {
                                    selectedGenders + gender
                                }
                                onGenderChange(updated)
                            },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (selectedGenders.contains(gender))
                                    Color(0xFFFF6000)
                                else
                                    Color(0xFF1A1A1A)
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

                Spacer(modifier = Modifier.height(24.dp))

                // Age Range Slider (unchanged)
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
                        onAgeRangeChange(
                            range.start.roundToInt()..
                                    range.endInclusive.roundToInt()
                        )
                    },
                    valueRange = 18f..100f,
                    steps = 20,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF6000),
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (pendingDistance == DatingViewModel.WORLDWIDE_DISTANCE)
                        stringResource(R.string.worldwide)
                    else
                        DistanceUtil.formatDistance(context, pendingDistance.toFloat()),
                    color = Color.White
                )
                Slider(
                    value = pendingDistance.toFloat(),
                    onValueChange = {
                        var newDistance = it.roundToInt().coerceAtMost(limit)
                        if (newDistance != DatingViewModel.WORLDWIDE_DISTANCE) {
                            newDistance = (newDistance / 5) * 5
                        }
                        pendingDistance = newDistance
                    },
                    valueRange = 0f..limit.toFloat(),
                    steps = ((limit / 5) - 1).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF6000),
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.Gray
                    )
                )
            }

            // ── City & Locality (Premium) ─────────────────────────
            item {
                OutlinedTextField(
                    value = cityInput,
                    onValueChange = {
                        cityInput = it
                        onCityChange(it)
                    },
                    label = { Text(stringResource(R.string.city_label), color = Color.White) },
                    enabled = true,
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color(0xFFFF6F00),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.Gray,
                        disabledBorderColor = Color.DarkGray,
                        disabledLabelColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = localitiesInput,
                    onValueChange = {
                        localitiesInput = it
                        val list = it.split(',').map { it.trim() }.filter { it.isNotBlank() }
                        onLocalitiesChange(list)
                    },
                    label = { Text(stringResource(R.string.locality_label), color = Color.White) },
                    enabled = true,
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color(0xFFFF6F00),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.Gray,
                        disabledBorderColor = Color.DarkGray,
                        disabledLabelColor  = Color.Gray
                    )
                )
            }

            // bottom save button visible to everyone, before premium gate
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        onDistanceChange(pendingDistance)
                        onSaveFilters()
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00)),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(stringResource(R.string.save), color = Color.White)
                }
            }

            /* ───── POWER FILTERS ──────────────────────────────────────── */

            /* 2) Top‐N Ranking (1..100) – visible to everyone, but locked for non‐Premium users */

            /* Show locked overlay for non‑Premium users once */
            if (!isPremium) {
                // show the lock message only
                item {
                    Locked("advanced")
                }
            } else {

                /* ─── EDUCATION Filters ──────────────────────────────────── */
                item {
                    Spacer(Modifier.height(24.dp))
//                FilterSectionTitle(title = stringResource(R.string.education))

                    Spacer(Modifier.height(8.dp))
                    // ─ High School ─
                    PlaceSearchDropdown(
                        label = stringResource(R.string.high_school),
                        query = highSchoolQuery,
                        onQueryChange = {
                            highSchoolQuery = it
                            onHighSchoolChange(it)
                        },
                        results = highSchoolResults,
                        searching = highSchoolSearching,
                        onResultSelect = {
                            highSchoolQuery = it
                            onHighSchoolChange(it)
                            highSchoolResults = emptyList()
                        },
                        isFieldFocused = isHighSchoolFieldFocused,
                        onFieldFocusChange = { isHighSchoolFieldFocused = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // ─ College ─
                    PlaceSearchDropdown(
                        label = stringResource(R.string.college),
                        query = collegeQuery,
                        onQueryChange = {
                            collegeQuery = it
                            onCollegeChange(it)
                        },
                        results = collegeResults,
                        searching = collegeSearching,
                        onResultSelect = {
                            collegeQuery = it
                            onCollegeChange(it)
                            collegeResults = emptyList()
                        },
                        isFieldFocused = isCollegeFieldFocused,
                        onFieldFocusChange = { isCollegeFieldFocused = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // ─ Post‐Grad ─
                    PlaceSearchDropdown(
                        label = stringResource(R.string.post_grad),
                        query = postGradQuery,
                        onQueryChange = {
                            postGradQuery = it
                            onPostGradChange(it)
                        },
                        results = postGradResults,
                        searching = postGradSearching,
                        onResultSelect = {
                            postGradQuery = it
                            onPostGradChange(it)
                            postGradResults = emptyList()
                        },
                        isFieldFocused = isPostGradFieldFocused,
                        onFieldFocusChange = { isPostGradFieldFocused = it },
                    )
                }

                /* ─── PREFERENCES Filters ───────────────────────────────── */
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    // Sexual orientation filter
                    val orientationOptions = stringArrayResource(R.array.sexual_orientation_options).toList()
                    DropdownFilter(
                        label = stringResource(R.string.sexual_orientation_label),
                        options = orientationOptions,
                        selectedOption = selectedOrientation,
                        onOptionChange = onOrientationChange
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    if (isIndian) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DropdownFilter(
                                label = stringResource(R.string.community),
                                options = listOf(
                                    stringResource(R.string.community_other),
                                    stringResource(R.string.community_adi),
                                    stringResource(R.string.community_andamanese),
                                    stringResource(R.string.community_anglo_indian),
                                    stringResource(R.string.community_assamese),
                                    stringResource(R.string.community_awadhi),
                                    stringResource(R.string.community_banjara),
                                    stringResource(R.string.community_bengali),
                                    stringResource(R.string.community_bhil),
                                    stringResource(R.string.community_bihari),
                                    stringResource(R.string.community_bodo),
                                    stringResource(R.string.community_bhojpuri),
                                    stringResource(R.string.community_chhattisgarhi),
                                    stringResource(R.string.community_coorgi),
                                    stringResource(R.string.community_dogra),
                                    stringResource(R.string.community_garhwali),
                                    stringResource(R.string.community_goan),
                                    stringResource(R.string.community_gond),
                                    stringResource(R.string.community_gujarati),
                                    stringResource(R.string.community_haryanvi),
                                    stringResource(R.string.community_himachali),
                                    stringResource(R.string.community_kannadiga),
                                    stringResource(R.string.community_kashmiri),
                                    stringResource(R.string.community_khasi),
                                    stringResource(R.string.community_konkani),
                                    stringResource(R.string.community_kumaoni),
                                    stringResource(R.string.community_ladakhi),
                                    stringResource(R.string.community_lakhadweepi),
                                    stringResource(R.string.community_lepcha),
                                    stringResource(R.string.community_madhya_pradeshi),
                                    stringResource(R.string.community_malayali),
                                    stringResource(R.string.community_malayali_mappila),
                                    stringResource(R.string.community_manipuri),
                                    stringResource(R.string.community_marathi),
                                    stringResource(R.string.community_marwari),
                                    stringResource(R.string.community_mizo),
                                    stringResource(R.string.community_munda),
                                    stringResource(R.string.community_naga),
                                    stringResource(R.string.community_nepali),
                                    stringResource(R.string.community_nyishi),
                                    stringResource(R.string.community_odia),
                                    stringResource(R.string.community_oraon),
                                    stringResource(R.string.community_parsi),
                                    stringResource(R.string.community_punjabi),
                                    stringResource(R.string.community_rajasthani),
                                    stringResource(R.string.community_santhal),
                                    stringResource(R.string.community_sikkimese),
                                    stringResource(R.string.community_sindhi),
                                    stringResource(R.string.community_tamil),
                                    stringResource(R.string.community_telugu),
                                    stringResource(R.string.community_tibetan),
                                    stringResource(R.string.community_tripuri),
                                    stringResource(R.string.community_urdu_speaker)
                                ),
                                selectedOption = selectedCommunity,
                                onOptionChange = onCommunityChange
                            )
                        }
                    }
                    // Religion options
                    val nonIndianReligions = listOf(
                        stringResource(R.string.religion_other),
                        stringResource(R.string.religion_buddhist),
                        stringResource(R.string.religion_christian),
                        stringResource(R.string.religion_christian_catholic),
                        stringResource(R.string.religion_christian_protestant_mainline),
                        stringResource(R.string.religion_christian_evangelical),
                        stringResource(R.string.religion_christian_orthodox),
                        stringResource(R.string.religion_christian_latter_day_saint),
                        stringResource(R.string.religion_christian_jehovahs_witness),
                        stringResource(R.string.religion_christian_other),
                        stringResource(R.string.religion_hindu),
                        stringResource(R.string.religion_jain),
                        stringResource(R.string.religion_jewish),
                        stringResource(R.string.religion_muslim),
                        stringResource(R.string.religion_muslim_sunni),
                        stringResource(R.string.religion_muslim_shia),
                        stringResource(R.string.religion_muslim_ahmadiyya),
                        stringResource(R.string.religion_muslim_sufi),
                        stringResource(R.string.religion_muslim_other),
                        stringResource(R.string.religion_no_religion),
                        stringResource(R.string.religion_parsi),
                        stringResource(R.string.religion_sikh),
                        stringResource(R.string.religion_indigenous_tribal),
                        stringResource(R.string.religion_santeria),
                        stringResource(R.string.religion_voodou),
                        stringResource(R.string.religion_candomble),
                        stringResource(R.string.religion_umbanda),
                        stringResource(R.string.religion_palo_mayombe),
                        stringResource(R.string.religion_native_traditional),
                        stringResource(R.string.religion_native_church),
                        stringResource(R.string.religion_vision_quest),
                        stringResource(R.string.religion_african_traditional),
                        stringResource(R.string.religion_obeah),
                        stringResource(R.string.religion_hoodoo),
                        stringResource(R.string.religion_rastafari),
                        stringResource(R.string.religion_black_protestant),
                    )

                    DropdownFilter(
                        label = stringResource(R.string.religion_label),
                        options = nonIndianReligions,
                        selectedOption = selectedReligion,
                        onOptionChange = onReligionChange
                    )


                    Spacer(modifier = Modifier.height(8.dp))

                    DropdownFilter(
                        label = stringResource(R.string.ethnicity_label),
                        options = listOf(
                            stringResource(R.string.ethnicity_option_white),
                            stringResource(R.string.ethnicity_option_black),
                            stringResource(R.string.ethnicity_option_hispanic),
                            stringResource(R.string.ethnicity_option_asian),
                            stringResource(R.string.ethnicity_option_native_american),
                            stringResource(R.string.ethnicity_option_middle_eastern),
                            stringResource(R.string.ethnicity_option_pacific_islander),
                            stringResource(R.string.ethnicity_option_mixed_other)
                        ),
                        selectedOption = selectedEthnicity,
                        onOptionChange = onEthnicityChange
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    DropdownFilter(
                        label = stringResource(R.string.income_level_label),
                        options = listOf(
                            stringResource(R.string.income_level_under_25k),
                            stringResource(R.string.income_level_25k_50k),
                            stringResource(R.string.income_level_50k_75k),
                            stringResource(R.string.income_level_75k_100k),
                            stringResource(R.string.income_level_100k_150k),
                            stringResource(R.string.income_level_over_150k)
                        ),
                        selectedOption = selectedIncomeLevel,
                        onOptionChange = onIncomeLevelChange
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isIndian) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            DropdownFilter(
                                label = stringResource(R.string.caste),
                                options = listOf(
                                    stringResource(R.string.caste_other),
                                    stringResource(R.string.caste_baidya),
                                    stringResource(R.string.caste_bhumihar),
                                    stringResource(R.string.caste_bhil),
                                    stringResource(R.string.caste_brahmin),
                                    stringResource(R.string.caste_ezhava),
                                    stringResource(R.string.caste_general),
                                    stringResource(R.string.caste_gowda),
                                    stringResource(R.string.caste_gurjar),
                                    stringResource(R.string.caste_jat),
                                    stringResource(R.string.caste_kayastha),
                                    stringResource(R.string.caste_kshatriya),
                                    stringResource(R.string.caste_kurmi),
                                    stringResource(R.string.caste_lingayat),
                                    stringResource(R.string.caste_mahishya),
                                    stringResource(R.string.caste_maratha),
                                    stringResource(R.string.caste_naidu),
                                    stringResource(R.string.caste_nair),
                                    stringResource(R.string.caste_obc),
                                    stringResource(R.string.caste_patel),
                                    stringResource(R.string.caste_rajput),
                                    stringResource(R.string.caste_rajvanshi),
                                    stringResource(R.string.caste_reddy),
                                    stringResource(R.string.caste_sadgop),
                                    stringResource(R.string.caste_scheduled_caste),
                                    stringResource(R.string.caste_scheduled_tribe),
                                    stringResource(R.string.caste_vaishya),
                                    stringResource(R.string.caste_vellalar),
                                    stringResource(R.string.caste_yadav),
                                ),
                                selectedOption = selectedCaste,
                                onOptionChange = onCasteChange
                            )
                        }
                    }
                }
                /* 1) Minimum Rating (0..5 stars) – visible to everyone, but locked for non‐Plus users */
                item {
                    Spacer(Modifier.height(24.dp))
                    FilterSectionTitle(stringResource(R.string.rating_label))

                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.min_rating, minRating),
                        color = if (isPremium) Color.White else Color.Gray
                    )
                    Slider(
                        value = minRating,
                        onValueChange = {
                            if (isPlus || isPremium) {
                                onMinRatingChange(it)
                            }
                        },
                        valueRange = 0f..5f,
                        steps = 4,
                        enabled = (isPremium),
                        colors = SliderDefaults.colors(
                            thumbColor = if (isPlus || isPremium) Color(0xFFFF6000) else Color.Gray,
                            activeTrackColor = if (isPlus || isPremium) Color(0xFFFF6000) else Color.Gray,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                    if (!(isPremium)) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.upgrade_to_premium_to_unlock),
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                item {
                    Spacer(Modifier.height(24.dp))
                    FilterSectionTitle(stringResource(R.string.ranking_label))

                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            R.string.top_n_ranking,
                            if (maxRanking == 0) "∞" else maxRanking
                        ),
                        color = if (isPremium) Color.White else Color.Gray
                    )
                    Slider(
                        value = (if (maxRanking == 0) 100f else maxRanking.toFloat()),
                        onValueChange = {
                            if (isPremium) {
                                onMaxRankingChange(it.roundToInt())
                            }
                        },
                        valueRange = 1f..100f,
                        steps = 30,
                        enabled = isPremium,
                        colors = SliderDefaults.colors(
                            thumbColor = if (isPremium) Color(0xFFFF6000) else Color.Gray,
                            activeTrackColor = if (isPremium) Color(0xFFFF6000) else Color.Gray,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                    if (!isPremium) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.upgrade_to_premium_to_unlock),
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

val BrandOrange = Color(0xFFFF6600)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchDropdown(
    label: String,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<PlaceResult>,
    searching: Boolean,
    onResultSelect: (String) -> Unit,
    isFieldFocused: Boolean,
    onFieldFocusChange: (Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val expanded     = results.isNotEmpty() && isFieldFocused
    val clearLabel   = stringResource(R.string.clear_selection)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { /* menu is driven by focus – leave empty */ },
    ) {
        val focusRequester = remember { FocusRequester() }

        /* ---- anchor text-field ---- */
        OutlinedTextField(
            value         = query,
            onValueChange = onQueryChange,
            label         = { Text(label) },
            singleLine    = true,
            trailingIcon  = {
                if (searching) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier    = Modifier.size(18.dp),
                        color       = BrandOrange               // tint spinner
                    )
                } else {
                    Icon(
                        imageVector      = Icons.Default.Search,
                        contentDescription = null,
                        tint              = BrandOrange        // tint search icon
                    )
                }
            },

            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(   // <- CHANGE
                /* input text */
                textColor   = Color.White,
                /* border */
                focusedBorderColor   = Color.White,
                unfocusedBorderColor = Color.White,

                /* label (the in-field “College / Post Grad” you see before typing) */
                focusedLabelColor   = Color.White,
                unfocusedLabelColor = Color.White,

                /* placeholder (if you use it) */
                placeholderColor   = Color.White,

                /* cursor & icons for completeness */
                cursorColor                = Color.White,
                focusedTrailingIconColor   = BrandOrange,
            ),

            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    onFieldFocusChange(state.isFocused)
                    if (!state.isFocused) onResultSelect(query)
                }
        )

        /* ---- dropdown menu ---- */
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                onFieldFocusChange(false)
                focusManager.clearFocus()
            },
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(6.dp))
                .border(
                    BorderStroke(1.dp, Color.Black.copy(alpha = .15f)),
                    RoundedCornerShape(6.dp)
                )
        ) {
            /* clear-selection row */
            if (query.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text(clearLabel) },
                    onClick = {
                        onQueryChange("")
                        onResultSelect("")
                        focusManager.clearFocus()
                    }
                )
            }

            /* places results */
            results.forEach { res ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(res.name)
                            if (res.address.isNotBlank()) {
                                Text(
                                    res.address,
                                    style = M3Theme.typography.bodySmall,
                                    color = Color.DarkGray
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = BrandOrange
                        )
                    },
                    onClick = {
                        onQueryChange(res.name)
                        onResultSelect(res.name)
                        focusManager.clearFocus()
                    }
                )
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
        Text(label, color = Color.White, fontSize = 16.sp)
        Box {
            Button(
                onClick = { expanded = !expanded },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (selectedOption.isNotBlank()) Color(0xFFFF6000) else Color(
                        0xFF1A1A1A
                    )
                ),
                border = BorderStroke(1.dp, Color.White),
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
fun NoMoreProfilesScreen(
    autoTapCount: Int = 0,
    maxAutoTaps: Int = 0,
    onRefresh: () -> Unit = {}
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshState = rememberSwipeRefreshState(isRefreshing)

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            onRefresh()
            delay(500)
            isRefreshing = false
        }
    }

    SwipeRefresh(
        state = refreshState,
        onRefresh = { isRefreshing = true }
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGrayBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.no_more_profiles),
            color = Color.White,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = stringResource(R.string.adjust_filters),
            color = Color.White,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = stringResource(R.string.or_click_date_to_refresh),
            color = Color.White,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Button(
            onClick = { isRefreshing = true },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00)),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
            Spacer(Modifier.width(8.dp))
            Text("Refresh", color = Color.Black)
        }
    }
    }
}

@Composable
fun DatingScreenContent(
    navController: NavController,
    geoFire: GeoFire,
    profileViewModel: ProfileViewModel,
    postViewModel: PostViewModel,
    profiles: List<Profile>,
    currentIndex: Int,                 // 🔹  index is now owned by parent
    boostedUsers: List<Profile>,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    excludedUserIds: Set<String>,    // ← here!
    onExcludeUser: (String) -> Unit,
    onRefreshProfiles: () -> Unit = {}
) {
    if (profiles.isEmpty() || currentIndex >= profiles.size) {
        NoMoreProfilesScreen(onRefresh = onRefreshProfiles)
        return
    }

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val currentUserProfile by profileViewModel.currentUserProfile.collectAsState()
    val isPremiumUser = currentUserProfile?.isPremium == true || currentUserProfile?.isPlus == true
    val currentProfile = profiles[currentIndex]
    val isBoostedProfile = boostedUsers.any { it.userId == currentProfile.userId }
    val datingViewModel: DatingViewModel = viewModel()   // ← add this line
    /* distance + AI check – unchanged */
    var userDistance by remember { mutableStateOf<Float?>(null) }
    var aiMatchResult by remember { mutableStateOf<AiMatchCheckResult?>(null) }
    // in DatingProfileCard, before Card:
    val allPosts by postViewModel.posts.collectAsState()
    val myPosts = allPosts.filter { it.userId == currentProfile.userId }
    val sortedByUpvotes = myPosts.sortedByDescending { it.upvotes }

    val todayWeek = remember { Calendar.getInstance().get(Calendar.WEEK_OF_YEAR) }
    var smartMatchAvailable by remember(currentUserProfile?.lastSmartMatchWeekOfYear) {
        mutableStateOf(currentUserProfile?.lastSmartMatchWeekOfYear != todayWeek)
    }
    val context = LocalContext.current

    LaunchedEffect(currentProfile.userId) {
        snapshotFlow { currentProfile.userId }
            .filterNotNull()
            .distinctUntilChanged()
            .debounce(300)                       // ⏳ throttle rapid swipes
            .collectLatest { otherId ->
                Log.d("DS‑FLOW", "distance request → $currentUserId ⇄ $otherId")

                // ✅ call the function on *datingViewModel*, NOT profileViewModel
                userDistance = datingViewModel
                    .distanceBetween(currentUserId, otherId, geoFire)

                // existing AI‑match lookup
                val snap = FirebaseRefs.db
                    .getReference("aiMatchCheck/$currentUserId/$otherId")
                    .get()
                    .await()
                aiMatchResult = snap.getValue(AiMatchCheckResult::class.java)
            }
    }

    Box(Modifier.fillMaxSize()) {
        // Treat “no location” as Float.NaN; your UI already renders that as “Worldwide”
        val dist = userDistance ?: Float.NaN
        DatingProfileCard(
            profile = currentProfile,
            isBoosted = isBoostedProfile,
            aiMatchResult = aiMatchResult,
            sortedByUpvotes = sortedByUpvotes,      // ← pass it i
            userDistance = dist,
            navController = navController,
            postViewModel = postViewModel,
            currentProfile = currentUserProfile,
            onSwipeRight = {
                onSwipeRight()
                handleSwipeRight(currentUserId, currentProfile.userId, profileViewModel)
                onExcludeUser(currentProfile.userId)
            },
            onSwipeLeft = {
                onSwipeLeft()
                handleSwipeLeft(currentUserId, currentProfile.userId)
                onExcludeUser(currentProfile.userId)
            }
        )
        if (isPremiumUser) {
            if (smartMatchAvailable) {
                Button(
                    onClick = {
                        smartMatchAvailable = false
                        handleSmartMatchDating(
                            currentUserId,
                            currentProfile,
                            currentUserProfile,
                            context
                        )
                        onExcludeUser(currentProfile.userId)
                        onSwipeRight()
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF4500)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Text("Smart Match", color = Color.White, fontSize = 10.sp)
                }
            }
        }
    }
}


// Updated DatingProfileCard
@Composable
fun DatingProfileCard(
    profile: Profile,
    isBoosted: Boolean,
    aiMatchResult: AiMatchCheckResult?,
    sortedByUpvotes: List<Post>,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    userDistance: Float,
    navController: NavController,
    postViewModel: PostViewModel,
    currentProfile: Profile?
) {
    // 1) Create a swipeableState and define anchors
    val swipeableState = rememberSwipeableState(initialValue = 0)
    val anchors = mapOf(-300f to -1, 0f to 0, 300f to 1)
    val swipeOffset = swipeableState.offset.value

    // 2) Whenever the swipeableState snaps to -1 or +1, invoke callbacks and reset to 0
    LaunchedEffect(swipeableState.currentValue) {
        when (swipeableState.currentValue) {
            -1 -> {
                onSwipeLeft()
                swipeableState.snapTo(0)
            }
            1 -> {
                onSwipeRight()
                swipeableState.snapTo(0)
            }
        }
    }

    // 3) Compute an alpha for the overlay icon based on drag distance (0..300 → 0f..1f)
    val maxDrag = 300f
    val rawAlpha = (abs(swipeOffset) / maxDrag).coerceIn(0f, 1f)

    // 4) Decide which overlay to show
    val showCheck = swipeOffset > 0f
    val showClose = swipeOffset < 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Offset horizontally by swipeOffset
            .offset { IntOffset(swipeOffset.roundToInt(), 0) }
            // Attach swipeable behavior
            .swipeable(
                state = swipeableState,
                anchors = anchors,
                thresholds = { _, _ -> FractionalThreshold(0.3f) },
                orientation = Orientation.Horizontal
            )
    ) {
        // ─── The original Card content ───────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            backgroundColor = DarkGrayBackground,
            shape = RoundedCornerShape(8.dp),
//            border = BorderStroke(3.dp, getLevelBorderColor(profile.averageRating))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkGrayBackground)
            ) {
                item {
                    PhotoWithTwoOverlays(
                        profile = profile,
                        isBoosted = isBoosted,
                        userDistance = userDistance,
                        aiMatchResult = aiMatchResult,
                        sortedByUpvotes = sortedByUpvotes,
                        currentProfile = currentProfile
                    )
                }
                item {
                    DatingProfileHeader(
                        profile = profile,
                        userDistance = userDistance,
                        sortedByUpvotes = sortedByUpvotes,
                        isBoosted = isBoosted
                    )
                }
                item {
                    ProfileCollapsibleSectionsAll(profile, currentProfile, aiMatchResult)
                }
                if (sortedByUpvotes.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.featured_posts),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(sortedByUpvotes) { post ->
                        PostItemInProfile(post)
                    }
                }
            }
        }

        // ─── Overlay icons ───────────────────────────────────────────────
        if (showCheck) {
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = "Swipe Right",
                tint = Color.Green.copy(alpha = rawAlpha),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
            )
        }
        if (showClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Swipe Left",
                tint = Color.Red.copy(alpha = rawAlpha),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
            )
        }
    }
}

@Composable
fun DatingProfileHeader(
    profile: Profile,
    userDistance: Float,
    sortedByUpvotes: List<Post>,
    isBoosted: Boolean                 // ⚡ NEW PARAM
) {
    val community = profile.community
    val religion = profile.religion
    val caste = profile.caste

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkGrayBackground)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            /* ———  BOOST PILL ——— */
            if (isBoosted) {
                BoostedPill()              // ⬅️ inject the orange “Boosted profile” chip
                Spacer(Modifier.width(6.dp))
            }
            Row {
                Spacer(modifier = Modifier.width(6.dp))
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
    }
}

@Composable
fun PostsOverlay(posts: List<Post>, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkGrayBackground),
            color = DarkGrayBackground
        ) {
            Box {
                // — Full-screen scrollable list —
                if (posts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_posts), color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 56.dp)  // leave room for the close button
                    ) {
                        items(posts) { post ->
                            PostItemInProfile(post)
                        }
                    }
                }

                // — Close button overlayed in top-right —
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(32.dp)
                        .background(Color.Gray.copy(alpha = 0.5f), shape = CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoWithTwoOverlays(
    profile: Profile,
    isBoosted: Boolean,
    userDistance: Float,
    aiMatchResult: AiMatchCheckResult?,
    sortedByUpvotes: List<Post>,
    currentProfile: Profile? = null
) {
    val ctx = LocalContext.current
    val placeholderRes = R.drawable.local_placeholder
    val photoUrls: List<Any> = buildList {
        if (profile.profilepicUrl.isNullOrBlank()) add(placeholderRes) else add(profile.profilepicUrl!!)
        addAll(profile.optionalPhotoUrls)
    }
    var idx by remember(photoUrls) { mutableStateOf(0) }
    val datingViewModel: DatingViewModel = viewModel()
    val compliments by datingViewModel.complimentsReceived.collectAsState()
    val compliment = compliments[profile.userId]
    val age = calculateAge(profile.dob)
    var showPostsOverlay by remember { mutableStateOf(false) }
    val interestsTexts = profile.interests.map { "${it.emoji} ${it.name}" }
    val preferencesTexts = listOfNotNull(
        profile.lookingFor.takeIf(String::isNotBlank)?.let { "🎯 $it" },
        profile.loveLanguage.takeIf(String::isNotBlank)?.let { "🗣️ $it" },
        profile.jobRole.takeIf(String::isNotBlank)?.let { "💼 $it" }
    )
    val smokingNouns = listOf(
        stringResource(R.string.non_smoker),
        stringResource(R.string.rare_smoker),
        stringResource(R.string.social_smoker),
        stringResource(R.string.frequent_smoker),
        stringResource(R.string.heavy_smoker)
    )
    val drinkingNouns = listOf(
        stringResource(R.string.non_drinker),
        stringResource(R.string.rare_drinker),
        stringResource(R.string.social_drinker),
        stringResource(R.string.frequent_drinker),
        stringResource(R.string.heavy_drinker)
    )
    val exerciseNouns = listOf(
        stringResource(R.string.inactive),
        stringResource(R.string.rarely_active),
        stringResource(R.string.moderately_active),
        stringResource(R.string.active),
        stringResource(R.string.very_active)
    )
    val lifestyleList = listOfNotNull(
        profile.lifestyle?.smoking_habit?.takeIf { it >= 0 }?.let { Triple(it, smokingNouns, "🚬") },
        profile.lifestyle?.drinking_habit?.takeIf { it >= 0 }?.let { Triple(it, drinkingNouns, "🍷") },
        profile.lifestyle?.exercise_frequency?.takeIf { it >= 0 }?.let { Triple(it, exerciseNouns, "🏃") }
    )
    val orientationTag = profile.sexualOrientation.takeIf { it.isNotBlank() }?.let { "🏳️‍🌈 $it" }
    val lifestyleTexts = buildList {
        orientationTag?.let { add(it) }
        addAll(
            lifestyleList.sortedByDescending { it.first }.take(3)
                .map { (v, nouns, e) -> "$e ${nouns.getOrNull(v) ?: ""}" }
        )
    }
    val lifestyleTexts2 = lifestyleList.sortedByDescending { it.first }.drop(3).take(6)
        .map { (v, nouns, e) -> "$e ${nouns.getOrNull(v) ?: ""}" }

    LaunchedEffect(photoUrls) {
        photoUrls.filterIsInstance<String>().forEach { url ->
            ctx.imageLoader.enqueue(ImageRequest.Builder(ctx).data(url).build())
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .background(DarkGrayBackground)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .pointerInput(photoUrls) {
                    detectTapGestures {
                        if (photoUrls.size > 1) {
                            idx = if (it.x > size.width / 2) (idx + 1) % photoUrls.size
                            else (idx - 1 + photoUrls.size) % photoUrls.size
                        }
                    }
                }
        ) {
            when (val model = photoUrls[idx]) {
                is Int -> Image(
                    painter = painterResource(model),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                else -> AsyncImage(
                    model = model,
                    placeholder = painterResource(placeholderRes),
                    error = painterResource(placeholderRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                photoUrls.forEachIndexed { i, _ ->
                    Box(
                        Modifier
                            .width(if (i == idx) 30.dp else 10.dp)
                            .height(4.dp)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i == idx) Color.White else Color.Gray)
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                val displayName = profile.name.ifBlank { profile.username }
                Text(
                    text = if (age > 0) "$displayName, $age" else displayName,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black)
                        )
                    )
            ) {
                AutoMarqueeRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val overlayStrings = when (idx) {
                        0 -> lifestyleTexts
                        1 -> preferencesTexts.take(3)
                        2 -> interestsTexts.take(4)
                        3 -> interestsTexts.drop(4).take(5)
                        4 -> lifestyleTexts2
                        else -> emptyList()
                    }
                    val tags = overlayStrings.filter { it.isNotBlank() }
                    if (tags.isEmpty()) {
                        profile.kinks.forEach { TagBox(it) }
                    } else {
                        tags.forEach { TagBox(it) }
                    }
                }
            }

            compliment?.let { c ->
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0xFFE91E63), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.EmojiEmotions,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            c.text,
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

//            if (idx == 0 && aiMatchResult != null) {
//                val score = aiMatchResult.totalMatchPercentage.coerceIn(0, 100)
//                Box(
//                    Modifier
//                        .align(Alignment.TopEnd)
//                        .padding(8.dp)
//                        .size(36.dp)
//                ) {
//                    CircularProgressIndicator(
//                        progress = score / 100f,
//                        color = Color(0xFFFF6F00),
//                        strokeWidth = 3.dp,
//                        modifier = Modifier.fillMaxSize()
//                    )
//                    Text(
//                        "$score%",
//                        Modifier.align(Alignment.Center),
//                        fontSize = 9.sp,
//                        fontWeight = FontWeight.Bold,
//                        color = Color.White
//                    )
//                }
//            }

            if (isBoosted) {
                Icon(
                    Icons.Default.FlashOn,
                    null,
                    tint = Color(0xFFFF6F00),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(26.dp)
                )
            }
        }

        if (showPostsOverlay) {
            PostsOverlay(sortedByUpvotes) { showPostsOverlay = false }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (userDistance.isNaN())
                    stringResource(R.string.worldwide)
                else
                    DistanceUtil.formatDistance(ctx, userDistance),
                color = Color.White,
                fontSize = 18.sp
            )
            IconButton(
                onClick  = { showPostsOverlay = true },
                modifier = Modifier
                    .padding(end = 4.dp)
                    .background(Color.Gray.copy(alpha = 0.5f), shape = CircleShape)
                    .size(28.dp)
            ) {
                Icon(Icons.Default.PostAdd,
                    stringResource(R.string.posts_button),
                    tint = Color.White
                )
            }
        }
    }
}


@Composable
fun TagBox(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .wrapContentWidth()      // only take as much width as you need
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .background(Color.Black, shape = RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFFFF6F00), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            maxLines = 10,                      // force a single line
            softWrap = true
        )
    }
}

@Composable
private fun AutoMarqueeRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val scroll = rememberScrollState()

    /* keep gliding left ⇄ right forever */
    LaunchedEffect(Unit) {
        delay(500)                            // let Compose settle
        while (true) {
            scroll.animateScrollTo(scroll.maxValue)
            delay(1_500)
            scroll.animateScrollTo(0)
            delay(1_500)
        }
    }

    Row(
        modifier = modifier.horizontalScroll(scroll, enabled = true),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}


@Composable
fun PerformanceMetricsSectionDating(profile: Profile) {
    var showPerformance by rememberSaveable { mutableStateOf(false) }
    CollapsibleSection(
        title = stringResource(R.string.performance_metrics),
        icon = Icons.Default.Assessment
    ) {
        ProfileDetailRow(
            label = stringResource(R.string.matches),
            value = stringResource(R.string.number, profile.matchCount),
            icon = Icons.Default.People
        )
        ProfileDetailRow(
            label = stringResource(R.string.rating),
            value = String.format(
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
    val coroutineScope = rememberCoroutineScope()
    var currentAiMatchResult by remember { mutableStateOf(aiMatchResult) }
    val context = LocalContext.current // ✅ declare at the top of the Composable
    val activity = LocalContext.current as Activity
    val rewardedSwipeManager = remember { RewardedAdManager(activity, AdUnitIds.rewardedSwipe(activity)) }

    DisposableEffect(Unit) {
        onDispose { rewardedSwipeManager.clearCallbacks() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(8.dp)
    ) {
        CollapsibleSection(
            title = stringResource(R.string.basic_information),
            icon = Icons.Default.Person
        ) {
            BasicInfoSection(profile)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (currentUserProfile?.isPremium == true) {
            PerformanceMetricsSectionDating(profile)
            Spacer(modifier = Modifier.height(12.dp))
        }
        CollapsibleSection(
            title = stringResource(R.string.bio),
            icon = Icons.Default.Mic
        ) {
            showVoiceBio(profile = profile)
        }
        Spacer(modifier = Modifier.height(8.dp))
        /** ─────────── Compatibility ─────────── */
        /*  auto-run every time it OPENS  */
        LaunchedEffect(profile.userId) {
            runAiMatchCheck(
                context = context,
                coroutineScope = coroutineScope,
                currentUserId = FirebaseAuth.getInstance().uid
                    ?: return@LaunchedEffect,
                currentUserProfile = currentUserProfile!!,
                otherProfile = profile
            ) { result -> currentAiMatchResult = result }
        }
        CollapsibleSection(
            title = stringResource(R.string.compatibility_check),
            icon = Icons.Default.Info, // expand / collapse
        ) {
            if (currentAiMatchResult != null) {
                ShowAiMatchAnalysis(currentAiMatchResult!!)
            } else {
                /* tiny placeholder while it’s working */
                Text(stringResource(R.string.run_analysis), color = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleSection(
            title = stringResource(R.string.preferences),
            icon = Icons.Default.Favorite
        ) {
            PreferencesSection(profile)
        }
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleSection(
            title = stringResource(R.string.lifestyle_attributes),
            icon = Icons.Default.Nature
        ) {
            LifestyleSection(profile)
        }
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleSection(
            title = stringResource(R.string.interests),
            icon = Icons.Default.Star
        ) {
            InterestsSectionInProfile(profile)
        }
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleSection(
            title = stringResource(R.string.social_causes),
            icon = Icons.Default.Favorite
        ) {
            SocialCausesSection(profile)
        }
    }
}

@Composable
fun ShowAiMatchAnalysis(result: AiMatchCheckResult) {

    /* 1 ── pull the individual insight lines out of the breakdown */
    val rawLines = result.compatibilityBreakdown
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }           // ←-- kills the “blank-chip” problem

    /* 2 ── classify lines */
    val strengths = rawLines.filter { it.startsWith("✅") }
    val concerns  = rawLines.filter { it.startsWith("⚠️") }
    val notes     = rawLines.filter { it.startsWith("ℹ️") }

    Column(modifier = Modifier.padding(8.dp)) {

        /* headline */
        Text(
            text  = stringResource(R.string.total_match_label, result.totalMatchPercentage),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(Modifier.height(10.dp))

        /* strengths */
        if (strengths.isNotEmpty()) {
            Text(stringResource(R.string.strengths), color = Color.White, fontWeight = FontWeight.SemiBold)
            Column(modifier = Modifier.fillMaxWidth()) {
                strengths.forEach {
                    Text(
                        text = "\u2022 " + it.removePrefix("✅").trim(),
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        /* concerns */
        if (concerns.isNotEmpty()) {
            Text(stringResource(R.string.concerns),
                color = Color.White, fontWeight = FontWeight.SemiBold)
            Column(modifier = Modifier.fillMaxWidth()) {
                concerns.forEach {
                    Text(
                        text = "\u2022 " + it.removePrefix("⚠️").trim(),
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }


        /* notes */
        if (notes.isNotEmpty()) {
            Text(stringResource(R.string.notes), color = Color.White, fontWeight = FontWeight.SemiBold)
            Column(modifier = Modifier.fillMaxWidth()) {
                notes.forEach {
                    Text(
                        text = "\u2022 " + it.removePrefix("ℹ️").trim(),
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        /* timestamp */
        Text(
            text = stringResource(R.string.analyzed_on, formatTime(result.timestamp)),
            color = Color.Gray,
            fontSize = 11.sp
        )
    }
}

private fun formatInsights(ctx: Context, list: List<MatchInsight>): String {
    val positives = list.filter { it.isPositive }.take(4)
    val concerns  = list.filter { !it.isPositive && it.emoji == "⚠️" }.take(4)
    val infos     = list.filter { it.emoji == "ℹ️" }.take(2)

    fun block(title: Int, items: List<MatchInsight>) =
        if (items.isEmpty()) "" else
            ctx.getString(title) + ":\n" +
                    items.joinToString("\n") { "${it.emoji} ${it.text}" }

    return listOf(
        block(R.string.strengths, positives),
        block(R.string.concerns,  concerns),
        block(R.string.notes,     infos)
    ).filter { it.isNotBlank() }
        .joinToString("\n\n")
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
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(4.dp))
    Card(
        backgroundColor = Color(0xFF1A1A1A),
        elevation       = 14.dp,
        shape           = RoundedCornerShape(8.dp),
        modifier        = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            // cap the height so it never grows off‐screen
            .heightIn(min = 100.dp, max = 400.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // make the content inside scroll vertically
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            content()
        }
    }
    Spacer(Modifier.height(4.dp))
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.Center) {
                    val placeholder = painterResource(R.drawable.local_placeholder)
                    AsyncImage(
                        model = currentUserProfilePic.takeIf { it.isNotBlank() },
                        contentDescription = stringResource(R.string.your_profile_picture),
                        placeholder = placeholder,
                        error = placeholder,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFFFF6F00), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    AsyncImage(
                        model = otherUserProfilePic.takeIf { it.isNotBlank() },
                        contentDescription = stringResource(R.string.matched_profile_picture),
                        placeholder = placeholder,
                        error = placeholder,
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
    profileViewModel.sendLikeNotification(currentUserId, otherUserId, {}, {})
    database.getReference("swipesReceived/$otherUserId/$currentUserId").setValue(true)

    // Increment swipe count and mark permanent exclusion after 3 swipes
    val swipeCountRef = database.getReference("users/$currentUserId/swipeCounts/$otherUserId")
    swipeCountRef.runTransaction(object : Transaction.Handler {
        override fun doTransaction(mutableData: MutableData): Transaction.Result {
            val current = mutableData.getValue(Int::class.java) ?: 0
            mutableData.value = current + 1
            return Transaction.success(mutableData)
        }

        override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
            if (committed) {
                val count = snapshot?.getValue(Int::class.java) ?: 0
                if (count >= 3) {
                    database.getReference("users/$currentUserId/permanentExcludes/$otherUserId")
                        .setValue(true)
                }
            }
        }
    })

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

                profileViewModel.sendMatchNotification(currentUserId, otherUserId, {}, {})
                profileViewModel.sendMatchNotification(otherUserId, currentUserId, {}, {})

                incrementMatchStats(currentUserId)
                incrementMatchStats(otherUserId)

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

    // Increment swipe count and mark permanent exclusion after 3 swipes
    val swipeCountRef = database.getReference("users/$currentUserId/swipeCounts/$otherUserId")
    swipeCountRef.runTransaction(object : Transaction.Handler {
        override fun doTransaction(mutableData: MutableData): Transaction.Result {
            val current = mutableData.getValue(Int::class.java) ?: 0
            mutableData.value = current + 1
            return Transaction.success(mutableData)
        }

        override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
            if (committed) {
                val count = snapshot?.getValue(Int::class.java) ?: 0
                if (count >= 3) {
                    database.getReference("users/$currentUserId/permanentExcludes/$otherUserId")
                        .setValue(true)
                }
            }
        }
    })

    // 🆕 track dislikes for 14‑day exclusion
    database.getReference("dislikesGiven/$currentUserId/$otherUserId").setValue(timestamp)

    val otherUserTotalSwipesRef =
        database.getReference("swipesReceived/$otherUserId/$currentUserId")
    otherUserTotalSwipesRef.setValue(true)


    val otherUserProfileRef = database.getReference("users/$otherUserId/numberOfUsersWhoSwiped")
    otherUserProfileRef.get().addOnSuccessListener { snapshot ->
        val currentCount = snapshot.getValue(Double::class.java) ?: 0.0
        otherUserProfileRef.setValue(currentCount + 1)
    }
}
private fun handleSmartMatchDating(
    currentUserId: String,
    otherProfile: Profile,
    currentUserProfile: Profile?,
    context: Context
) {
    val database = FirebaseRefs.db
    val week = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
    database.getReference("users/$currentUserId/lastSmartMatchWeekOfYear").setValue(week)
    currentUserProfile?.lastSmartMatchWeekOfYear = week
    createMatch(database, currentUserId, otherProfile.userId)
    Toast.makeText(context, "Matched with ${otherProfile.username}!", Toast.LENGTH_SHORT).show()
}

private fun createMatch(
    database: FirebaseDatabase,
    currentUserId: String,
    otherUserId: String
) {
    val ts = System.currentTimeMillis()
    val updates = mapOf(
        "matches/$currentUserId/$otherUserId" to ts,
        "matches/$otherUserId/$currentUserId" to ts
    )
    database.reference.updateChildren(updates)
}

private fun incrementMatchStats(userId: String) {
    val db = FirebaseRefs.db
    val matchCountRef = db.getReference("users/$userId/matchCount")
    matchCountRef.runTransaction(object : Transaction.Handler {
        override fun doTransaction(mutableData: MutableData): Transaction.Result {
            val current = mutableData.getValue(Int::class.java) ?: 0
            mutableData.value = current + 1
            return Transaction.success(mutableData)
        }

        override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
            if (!committed) {
                Log.e("incrementMatchStats", "Failed for $userId: ${error?.message}")
                return
            }
            val newCount = snapshot?.getValue(Int::class.java) ?: 0
            db.getReference("users/$userId/numberOfSwipeRights")
                .get().addOnSuccessListener { srSnap ->
                    val swipes = srSnap.getValue(Int::class.java) ?: 0
                    val ratio = if (swipes > 0) newCount.toDouble() / swipes else 0.0
                    db.getReference("users/$userId/matchCountPerSwipeRight")
                        .setValue(ratio)
                }
        }
    })
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

    val breakdownText = formatInsights(context, insights)

    val summaryText   = context.getString(
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

    var score    = 0.0
    var possible = 0.0
    val insights = mutableListOf<MatchInsight>()

    fun compareStringField(a: String?, b: String?, label: String, pts: Double) {
        val aTrim = a.orEmpty().trim()
        val bTrim = b.orEmpty().trim()
        when {
            aTrim.isEmpty() && bTrim.isEmpty() ->
                insights += MatchInsight("ℹ️", context.getString(R.string.lifestyle_not_set_suffix, label), false)
            aTrim.isEmpty() || bTrim.isEmpty() ->
                insights += MatchInsight("ℹ️", context.getString(R.string.lifestyle_one_not_set_suffix, label), false)
            aTrim.equals(bTrim, true) -> {
                score    += pts
                possible += pts
                insights += MatchInsight("✅", "$label: $aTrim", true)
            }
            else -> {
                possible += pts
                insights += MatchInsight("⚠️", "$label: \"$aTrim\" vs \"$bTrim\"", false)
            }
        }
    }

    fun resolveField(p: String, f: String?) = if (p.trim().isNotEmpty()) p.trim() else f.orEmpty().trim()

    val ageA = profileA.age
    val ageB = profileB.age
    if (ageA > 0 && ageB > 0) {
        possible += 5.0
        val aScore = ageCompatibilityScore(ageA, ageB)
        score += 5.0 * aScore
        val pct = (aScore * 100).roundToInt()
        val positive = aScore >= 0.5
        val emoji = if (positive) "✅" else "⚠️"
        insights += MatchInsight(
            emoji,
            context.getString(R.string.age_compatibility_prefix, ageA, ageB, pct),
            positive
        )
    } else insights += MatchInsight("ℹ️", context.getString(R.string.age_not_set), false)

    // ───── Lifestyle Compatibility ─────
    val (lifeScore, lifeCount, lifeCommonKeys) = lifestyleCompatibilityMetrics(profileA.lifestyle, profileB.lifestyle)
    if (lifeCount > 0) {
        possible += 8.0
        score += 8.0 * lifeScore

        val keyToLabel = mapOf(
            "smoking_habit" to R.string.lifestyle_smoking,
            "drinking_habit" to R.string.lifestyle_drinking,
            "indoor_outdoor_orientation" to R.string.lifestyle_indoor_outdoor,
            "sexual_activity_level" to R.string.lifestyle_sexual_activity,
            "sociability" to R.string.lifestyle_sociability,
            "social_media_engagement" to R.string.lifestyle_social_media,
            "sleep_pattern" to R.string.lifestyle_sleep,
            "work_life_balance" to R.string.lifestyle_work_life_balance,
            "exercise_frequency" to R.string.lifestyle_exercise,
            "adventurousness" to R.string.lifestyle_adventurousness,
            "family_orientated" to R.string.lifestyle_family_oriented,
            "intellectual_curiosity" to R.string.lifestyle_intellectual_curiosity,
            "creative_expression" to R.string.lifestyle_creative_expression,
            "physical_fitness" to R.string.lifestyle_physical_fitness,
            "spirituality_mindfulness" to R.string.lifestyle_spirituality,
            "easy_goingness" to R.string.lifestyle_humor,
            "professional_ambition" to R.string.lifestyle_professional_ambition,
            "environmental_awareness" to R.string.lifestyle_environmental_awareness,
            "culinary_enthusiasm" to R.string.lifestyle_culinary_enthusiasm,
            "political_awareness" to R.string.lifestyle_political_awareness,
            "community_engagement" to R.string.lifestyle_community_engagement,
            "sports_enthusiasm" to R.string.lifestyle_sports,
            "dietary_preferences" to R.string.lifestyle_dietary_preferences,
        )
        val commonNames = lifeCommonKeys.mapNotNull { keyToLabel[it] }.map { context.getString(it) }
        val listStr = if (commonNames.isEmpty()) context.getString(R.string.lifestyle_none_common) else commonNames.joinToString()
        val pct = (lifeScore * 100).roundToInt()
        val positive = pct >= 50
        val emoji = if (positive) "✅" else "⚠️"
        insights += MatchInsight(
            emoji,
            context.getString(R.string.lifestyle_similarity_format, pct, lifeCount, listStr),
            positive
        )
    } else {
        if (profileA.lifestyle == null && profileB.lifestyle == null) {
            insights += MatchInsight("ℹ️", context.getString(R.string.lifestyle_not_set_in_profiles), false)
        } else {
            insights += MatchInsight("ℹ️", context.getString(R.string.lifestyle_no_traits_to_compare), false)
        }
    }

    val collegeA = resolveField(profileA.college, profileA.customCollege)
    val collegeB = resolveField(profileB.college, profileB.customCollege)
    if (collegeA.isNotBlank() || collegeB.isNotBlank()) {
        possible += 6.0
        if (collegeA.equals(collegeB, true)) score += 6.0
        else insights += MatchInsight("⚠️", context.getString(R.string.college_mismatch_format, collegeA, collegeB), false)
    }

    val pgA = resolveField(profileA.postGraduation ?: "", profileA.customPostGraduation)
    val pgB = resolveField(profileB.postGraduation ?: "", profileB.customPostGraduation)
    if (pgA.isNotBlank() || pgB.isNotBlank()) {
        possible += 6.0
        if (pgA.equals(pgB, true)) score += 6.0
        else insights += MatchInsight("⚠️", context.getString(R.string.post_graduation_mismatch_format, pgA, pgB), false)
    }

    compareStringField(profileA.work,              profileB.work,              context.getString(R.string.workplace_label),          8.0)
    compareStringField(profileA.jobRole,           profileB.jobRole,           context.getString(R.string.job_role_label),           5.0)
    compareStringField(profileA.loveLanguage,      profileB.loveLanguage,      context.getString(R.string.love_language_label),      4.0)
    compareStringField(profileA.lookingFor,        profileB.lookingFor,        context.getString(R.string.relationship_intent_label),6.0)
    compareStringField(profileA.community,         profileB.community,         context.getString(R.string.community),                5.0)
    compareStringField(profileA.religion,          profileB.religion,          context.getString(R.string.religion),                 4.0)
    compareStringField(profileA.city,              profileB.city,              context.getString(R.string.city),                     4.0)
    compareStringField(profileA.preferredLanguage, profileB.preferredLanguage, context.getString(R.string.preferred_language_label), 3.0)

    val interestsA = profileA.interests.map { it.name.trim() }.filter { it.isNotEmpty() }.toSet()
    val interestsB = profileB.interests.map { it.name.trim() }.filter { it.isNotEmpty() }.toSet()
    if (interestsA.isNotEmpty() && interestsB.isNotEmpty()) {
        possible += 8.0
        val shared = interestsA intersect interestsB
        if (shared.isNotEmpty()) {
            val pts = (shared.size * 2).coerceAtMost(8)
            score += pts
            insights += MatchInsight("✅", context.getString(R.string.shared_interests_prefix, shared.joinToString()), true)
        } else insights += MatchInsight("⚠️", context.getString(R.string.no_common_interests), false)
    } else insights += MatchInsight("ℹ️", context.getString(R.string.interests_not_set), false)

    val causesA = profileA.socialCauses.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val causesB = profileB.socialCauses.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    if (causesA.isNotEmpty() && causesB.isNotEmpty()) {
        possible += 6.0
        val shared = causesA intersect causesB
        if (shared.isNotEmpty()) {
            val pts = (shared.size * 2).coerceAtMost(6)
            score += pts
            insights += MatchInsight("✅", context.getString(R.string.shared_social_causes_prefix, shared.joinToString()), true)
        } else insights += MatchInsight("⚠️", context.getString(R.string.no_common_social_causes), false)
    } else insights += MatchInsight("ℹ️", context.getString(R.string.social_causes_not_set), false)

    val zodiacA = if (!profileA.zodiac.isNullOrBlank()) profileA.zodiac!! else deriveZodiac(profileA.dob)
    val zodiacB = if (!profileB.zodiac.isNullOrBlank()) profileB.zodiac!! else deriveZodiac(profileB.dob)
    if (zodiacA != "Unknown" && zodiacB != "Unknown") {
        possible += 5.0
        val zScore = zodiacCompatibilityScore(zodiacA, zodiacB)
        score += 5.0 * zScore
        val pct = (zScore * 100).roundToInt()
        val positive = zScore >= 0.5
        val emoji = if (positive) "✅" else "⚠️"
        insights += MatchInsight(
            emoji,
            context.getString(
                R.string.zodiac_compatibility_prefix,
                zodiacA,
                zodiacB,
                pct
            ),
            positive
        )
    } else insights += MatchInsight("ℹ️", context.getString(R.string.zodiac_not_set), false)

    if (profileA.isMatrimonyMode || profileB.isMatrimonyMode) {
        possible += 3.0
        if (profileA.isMatrimonyMode && profileB.isMatrimonyMode) {
            score += 3.0
            compareStringField(profileA.marriageTimeline, profileB.marriageTimeline,
                context.getString(R.string.marriage_timeline_label), 3.0)
        } else insights += MatchInsight("⚠️", context.getString(R.string.matrimony_mismatch), false)
    } else insights += MatchInsight("ℹ️", context.getString(R.string.matrimony_not_set), false)

    val relocA = profileA.relocationPreference.orEmpty().trim()
    val relocB = profileB.relocationPreference.orEmpty().trim()
    if (relocA.isNotBlank() || relocB.isNotBlank()) {
        possible += 4.0
        if (relocA.equals(relocB, true)) score += 4.0
        else insights += MatchInsight("⚠️", context.getString(R.string.relocation_mismatch_format, relocA, relocB), false)
    }

    val careerA = profileA.postMarriageCareerPlan.orEmpty().trim()
    val careerB = profileB.postMarriageCareerPlan.orEmpty().trim()
    if (careerA.isNotBlank() || careerB.isNotBlank()) {
        possible += 3.0
        if (careerA.equals(careerB, true)) score += 3.0
        else insights += MatchInsight("⚠️", context.getString(R.string.career_plan_mismatch_format, careerA, careerB), false)
    }

    val cultureA = profileA.traditionalVsLiberal.orEmpty().trim()
    val cultureB = profileB.traditionalVsLiberal.orEmpty().trim()
    if (cultureA.isNotBlank() || cultureB.isNotBlank()) {
        possible += 3.0
        if (cultureA.equals(cultureB, true)) score += 3.0
        else insights += MatchInsight("⚠️", context.getString(R.string.cultural_mindset_mismatch_format, cultureA, cultureB), false)
    }

    val tagsA = profileA.userTags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val tagsB = profileB.userTags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    if (tagsA.isNotEmpty() && tagsB.isNotEmpty()) {
        possible += 2.0
        val shared = tagsA intersect tagsB
        if (shared.isNotEmpty()) {
            val pts = shared.size.coerceAtMost(2)
            score += pts
            insights += MatchInsight("✅", context.getString(R.string.shared_tags_prefix, shared.joinToString()), true)
        } else insights += MatchInsight("⚠️", context.getString(R.string.tags_not_set_or_no_overlap), false)
    } else insights += MatchInsight("ℹ️", context.getString(R.string.tags_not_set_or_no_overlap), false)

// The compatibility percent now blends this match calculation with the
    // target user's overall composite score for better weighting.
    // base: score achieved vs total possible (0–100)
    val base = if (possible == 0.0) 0.0 else (score / possible) * 100.0
    val targetCompositePct = profileB.compositeScorePct
    val blended = ((2 * base) + targetCompositePct) / 3.0
    val finalScore = blended.roundToInt().coerceIn(0, 100)
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
                        focusedTextColor = Color.White
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
                        fontSize = 11.sp
                    )
                }

                if (audioFileUri != null && !isRecording) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Voice compliment ready!",
                        color = Color.Green,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isRecording &&
                        (complimentText.isNotBlank() || audioFileUri != null) &&
                        complimentsLeft > 0,
                onClick = {
                    if (isRecording) {
                        recorder?.stop()
                        recorder?.release()
                        recorder = null
                        isRecording = false
                    }
                    onSend(
                        complimentText.trim().takeIf { it.isNotEmpty() },
                        audioFileUri
                    )
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