@file:OptIn(
    ExperimentalMaterialApi::class, ExperimentalMaterialApi::class,
    ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class
)
package com.am24.am24

import DatingViewModel
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
/* Material 3 (add these – they won’t clash with existing M2 widgets) */
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme     as M3Theme
/* Icons */
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search

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
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.currentBackStackEntryAsState
import com.am24.am24.ui.CompactCompatBadge
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
import java.util.Locale

/* ────────────────────────────────────────────────────────────────────── */
/*                   LOCAL EXCLUSION SETTINGS (14 days)                  */
/* ────────────────────────────────────────────────────────────────────── */
private const val EXCLUSION_TTL_MS = 14L * 24L * 60L * 60L * 1000L // 14 days rolling

/* DatingScreen.kt  – add near the top, after imports */
private fun Iterable<*>.dump(tag: String) =
    Log.d("DS-FLOW", "$tag  size=${count()}  →  ${joinToString { (it as? Profile)?.userId ?: it.toString() }}")

/* Prints a plain Set<String> nicely */
private fun Set<*>.dump(tag: String) =
    Log.d("DS-FLOW", "$tag  size=${size}  →  ${joinToString()}")

private fun deaccent(s: String): String {
    val nfd = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
    return nfd.replace(Regex("\\p{Mn}+"), "")
}

private fun canonicalGender(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val t = deaccent(raw).trim().lowercase(Locale.ROOT)
    return when (t) {
        "male", "m", "man", "hombre", "hombres", "masculino", "male_option" -> "male"
        "female", "f", "woman", "mujer", "mujeres", "femenino", "female_option" -> "female"
        "other", "others", "non-binary", "nonbinary", "nb",
        "otro", "otra", "otros", "otras", "no binario", "no-binario", "no_binario", "nobinario",
        "gender_other" -> "other"
        else -> t
    }
}

/* Write a temp exclusion that expires in 14 days */
private fun addToExclusions(currentUserId: String, otherUserId: String, reason: String) {
    val ref = FirebaseRefs.db.getReference("exclusions/$currentUserId/$otherUserId")
    val payload = mapOf("ts" to System.currentTimeMillis(), "reason" to reason)
    ref.setValue(payload)
}

data class SwipeData(
    val liked: Boolean = false,
    val timestamp: Long = 0L
)

/**
 * Main DatingScreen with:
 *  - Gender selector on-screen (persists + refreshes VM)
 *  - NO local plus/premium sorting (VM fetches 20/50/100 by tier)
 *  - Temp exclusions for like/dislike/compliment (14 days)
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
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    // 1) watch for “are we still on the Dating route?”
    val backstackEntry by navController.currentBackStackEntryAsState()
    val currentRoute    = backstackEntry?.destination?.route

    /* Auto-tap counter for empty profiles */
    var autoTapCount by rememberSaveable { mutableStateOf(0) }
    val maxAutoTaps = 0

    /* fetch *my* Profile once */
    LaunchedEffect(Unit) {
        profileViewModel.fetchCurrentUserProfile()
        datingViewModel.startInventoryWatcher(myUid)
    }

    // ── StateFlows ────────────────────────────────────────────────────
    val myProfile by profileViewModel.currentUserProfile.collectAsState()
    if (myProfile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val baseProfiles      by datingViewModel.displayingProfiles.collectAsState()
    val allProfiles       by datingViewModel.allProfiles.collectAsState()
    val isLoading         by datingViewModel.isLoading.collectAsState()
    val loadingProgress   by datingViewModel.loadingProgress.collectAsState()
    val matchPopUpState   by profileViewModel.matchPopUpState.collectAsState()
    val complimentsLeft   by datingViewModel.complimentsLeft.collectAsState()
    val isIndian = canonicalCountry(myProfile?.country) == "India"

    // gender-only filtering; lastActive order already applied later
    val filteredProfiles by remember(baseProfiles, myProfile) {
        derivedStateOf {
            baseProfiles
                .asSequence()
                .filter { prof -> prof.userId.isNotBlank() }
                .sortedByDescending { it.lastActive }
                .toList()
        }
    }

    // ── Misc local state ─────────────────────────────────────────────
    var excludedUserIds   by remember { mutableStateOf(emptySet<String>()) }
    var remainingSwipes   by remember { mutableStateOf(0) }
    var swipesLoaded      by remember { mutableStateOf(false) }

    var showComplimentDlg by remember { mutableStateOf(false) }
    val aiResults = remember { mutableStateMapOf<String, AiMatchCheckResult>() }

    val context = LocalContext.current
    val activity = LocalContext.current as Activity
    val rewardedComplimentManager = remember { RewardedAdManager(activity, AdUnitIds.rewardedCompliment(activity)) }
    val rewardedSwipeManager = remember { RewardedAdManager(activity, AdUnitIds.rewardedSwipe(activity)) }

    DisposableEffect(Unit) {
        onDispose {
            rewardedComplimentManager.clearCallbacks()
            rewardedSwipeManager.clearCallbacks()
        }
    }

    LaunchedEffect(Unit) {
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            excludedUserIds = fetchExcludedUsers(uid)
            excludedUserIds.dump("EXCLUDED_UIDS")
            profileViewModel.fetchCurrentUserProfile()
            remainingSwipes = loadAndResetSwipesDaily(uid)
            swipesLoaded    = true
            // Full VM refresh (tier-sized, by country)
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

    // SEE which Profile objects are being dropped
    val excludedProfiles = filteredProfiles.filter { it.userId in excludedUserIds }
    excludedProfiles.dump("EXCLUDED_PROFILES")

    // BUILD DISPLAY LIST – no local plus/premium ordering
    val displayedProfiles by remember(filteredProfiles, excludedUserIds, likers) {
        derivedStateOf {
            filteredProfiles
                .filter { it.userId.isNotBlank() }
                .filter { it.userId !in excludedUserIds }
                // hide private profiles, unless they liked you:
                .filter { prof -> !prof.isPrivate || prof.userId in likers }
                .also { it.dump("DISPLAYED") }
        }
    }

    // ── Hoisted deck pointer ─────────────────────────────────────────
    var currentIndex      by rememberSaveable { mutableStateOf(0) }
    val currentSwipeProfile by remember(currentIndex, displayedProfiles) {
        derivedStateOf { displayedProfiles.getOrNull(currentIndex) }
    }
    var aiMatchResult by remember { mutableStateOf<AiMatchCheckResult?>(null) }
    val aiForCurrent = currentSwipeProfile?.userId?.let { aiResults[it] }


    LaunchedEffect(allProfiles) { currentIndex = 0 }

    // inside DatingScreen (or DatingScreenContent) where you have `currentSwipeProfile`:
    LaunchedEffect(currentSwipeProfile?.userId) {
        Log.d("DatingScreen", "Composable – currentSwipeProfile.userId = ${currentSwipeProfile?.userId}")
        datingViewModel.setCurrentSwipeUserId(currentSwipeProfile?.userId)
        aiMatchResult = null
        val myId = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val myProf = myProfile ?: return@LaunchedEffect
        val otherId = currentSwipeProfile?.userId ?: return@LaunchedEffect

        // ── NEW: prefetch distance for current + next
        datingViewModel.prefetchDistance(myId, otherId, geoFire)
        displayedProfiles.getOrNull(currentIndex + 1)?.userId?.let { nextId ->
            datingViewModel.prefetchDistance(myId, nextId, geoFire)
        }

        val ref = FirebaseRefs.db
            .getReference("aiMatchCheck/$myId/$otherId")
        val snap = ref.get().await()
        val existing = snap.getValue(AiMatchCheckResult::class.java)
        if (existing != null) {
            aiResults[otherId] = existing
        } else {
            runAiMatchCheck(
                context = context,
                coroutineScope = coroutineScope,
                currentUserId = myId,
                currentUserProfile = myProf,
                otherProfile = currentSwipeProfile!!
            ) { result ->
                aiResults[otherId] = result
            }
        }
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
        if (currentRoute == "dating"
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

    /* cannot go on until *my* profile has loaded */
    if (myProfile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DarkGrayBackground)
    ) {
        // ── TOOLBAR (center meter + gender filter) ─────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Center: Compatibility meter when there is a profile selected
            currentSwipeProfile?.let {
                Box(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    CompactCompatBadge(
                        percent = aiForCurrent?.totalMatchPercentage?.toDouble() ?: 0.0
                    )
                }
            }

            Spacer(Modifier.size(30.dp)) // right spacer for centering
        }

        // ── DECK / LOADING / EMPTY STATES ─────────────────────────────
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
                        color = BrandOrange,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                displayedProfiles.isEmpty() -> NoMoreProfilesScreen(
                    autoTapCount = autoTapCount,
                    maxAutoTaps  = maxAutoTaps,
                    onRefresh    = { datingViewModel.refreshFilteredProfiles() }
                )

                else -> DatingScreenContent(
                    navController    = navController,
                    geoFire          = geoFire,
                    profileViewModel = profileViewModel,
                    postViewModel    = postViewModel,
                    profiles         = displayedProfiles,
                    currentIndex     = currentIndex,
                    aiMatchResult = aiForCurrent,
                    onComplimentUser = { uid ->
                        datingViewModel.setCurrentSwipeUserId(uid)
                        if (complimentsLeft > 0) {
                            showComplimentDlg = true
                        } else {
                            if (isIndian) {
                                navController.navigate("buyCompliments")
                            } else {
                                val uidLocal = FirebaseAuth.getInstance().uid
                                if (uidLocal != null) {
                                    rewardedComplimentManager.showWithDailyLimit(
                                        userId = uidLocal,
                                        onReward = {
                                            datingViewModel.incrementComplimentsLocal()
                                            val newVal = complimentsLeft + 1
                                            coroutineScope.launch {
                                                FirebaseRefs.db
                                                    .getReference("users/$uidLocal/availableCompliments")
                                                    .setValue(newVal)
                                            }
                                            profileViewModel.incrementComplimentsLocal()
                                        }
                                    )
                                }
                            }
                        }
                    },
                    onSwipeRight     = {
                        if (remainingSwipes > 0) remainingSwipes--
                        updateSwipesInFirebase(remainingSwipes)
                        currentIndex++
                    },
                    onSwipeLeft      = {
                        if (remainingSwipes > 0) remainingSwipes--
                        updateSwipesInFirebase(remainingSwipes)
                        currentIndex++
                    },
                    excludedUserIds = excludedUserIds,
                    onExcludeUser   = { excludedUserIds = excludedUserIds + it },
                    onRefreshProfiles = { datingViewModel.refreshFilteredProfiles() }
                )
            }
            // ── If they’ve exhausted swipes, show overlay ──
            if (swipesLoaded && remainingSwipes <= 0 && !showSwipeLimitOverlay) {
                showSwipeLimitOverlay = true
            }

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

    /* match pop-up, compliment dialog – unchanged except exclusions on send */
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
            onSend = { text ->
                coroutineScope.launch {
                    val receiver = displayedProfiles[currentIndex]
                    val me = FirebaseAuth.getInstance().uid ?: return@launch
                    datingViewModel.sendCompliment(
                        receiverId       = receiver.userId,
                        textMessage      = text,
                        profileViewModel = profileViewModel
                    )
                    // Temp exclude for 14 days on compliment
                    addToExclusions(me, receiver.userId, "compliment")
                    ExclusionEventBus.emit(receiver.userId)
                    excludedUserIds += receiver.userId
                    showComplimentDlg = false
                }
            },
            onDismiss = { showComplimentDlg = false }
        )
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
    currentIndex: Int,
    aiMatchResult: AiMatchCheckResult?,          // ← add this
    onComplimentUser: (String) -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    excludedUserIds: Set<String>,
    onExcludeUser: (String) -> Unit,
    onRefreshProfiles: () -> Unit = {}
) {
    if (profiles.isEmpty() || currentIndex >= profiles.size) {
        NoMoreProfilesScreen(onRefresh = onRefreshProfiles)
        return
    }

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val currentProfile = profiles[currentIndex]
    val currentUserProfile = remember(currentProfile.userId) {
        profileViewModel.currentUserProfile.value
    }
    val isPremiumUser = currentUserProfile?.isPremium == true || currentUserProfile?.isPlus == true
    val datingViewModel: DatingViewModel = viewModel()
    /* distance + AI check – unchanged */
    val frozenDistance = remember(currentProfile.userId) {
        datingViewModel.getCachedDistance(currentUserId, currentProfile.userId) ?: Float.NaN
    }
    // in DatingProfileCard, before Card:
// AFTER (snapshot, won’t update)
    val sortedByUpvotes = remember(currentProfile.userId) {
        postViewModel.posts.value
            .filter { it.userId == currentProfile.userId }
            .sortedByDescending { it.upvotes }
    }
    val todayWeek = remember { Calendar.getInstance().get(Calendar.WEEK_OF_YEAR) }
    var smartMatchAvailable by remember(currentProfile.userId) {
        mutableStateOf(currentUserProfile?.lastSmartMatchWeekOfYear != todayWeek)
    }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        // Treat “no location” as Float.NaN; your UI already renders that as “Worldwide”
        DatingProfileCard(
            profile = currentProfile,
            aiMatchResult = aiMatchResult,
            sortedByUpvotes = sortedByUpvotes,
            onSwipeRight = {
                onSwipeRight()
                handleSwipeRight(currentUserId, currentProfile.userId, profileViewModel)
                // Temp exclude for 14 days on LIKE
                addToExclusions(currentUserId, currentProfile.userId, "like")
                ExclusionEventBus.emit(currentProfile.userId)
                onExcludeUser(currentProfile.userId)
            },
            onSwipeLeft = {
                onSwipeLeft()
                handleSwipeLeft(currentUserId, currentProfile.userId)
                // Temp exclude for 14 days on DISLIKE
                addToExclusions(currentUserId, currentProfile.userId, "dislike")
                ExclusionEventBus.emit(currentProfile.userId)
                onExcludeUser(currentProfile.userId)
            },
            userDistance = frozenDistance,
            navController = navController,
            postViewModel = postViewModel,
            currentProfile = currentUserProfile,
            profileViewModel = profileViewModel,
            onComplimentUser = onComplimentUser
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
                        addToExclusions(currentUserId, currentProfile.userId, "smart_match")
                        ExclusionEventBus.emit(currentProfile.userId)
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


// Updated DatingProfileCard (boosts removed)
@Composable
fun DatingProfileCard(
    profile: Profile,
    aiMatchResult: AiMatchCheckResult?,
    sortedByUpvotes: List<Post>,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    userDistance: Float,
    navController: NavController,
    postViewModel: PostViewModel,
    currentProfile: Profile?,
    profileViewModel: ProfileViewModel,
    onComplimentUser: (String) -> Unit
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
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkGrayBackground)
            ) {
                item {
                    PhotoWithTwoOverlays(
                        profile = profile,
                        userDistance = userDistance,
                        aiMatchResult = aiMatchResult,
                        sortedByUpvotes = sortedByUpvotes,
                        currentProfile = currentProfile,
                        onCompliment = { onComplimentUser(profile.userId) }
                    )
                }
                item {
                    DatingProfileHeader(
                        profile = profile,
                        userDistance = userDistance,
                        sortedByUpvotes = sortedByUpvotes
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
    sortedByUpvotes: List<Post>
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
                if (posts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_posts), color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 56.dp)
                    ) {
                        items(posts) { post ->
                            PostItemInProfile(post)
                        }
                    }
                }

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
    userDistance: Float,
    aiMatchResult: AiMatchCheckResult?,
    sortedByUpvotes: List<Post>,
    currentProfile: Profile? = null,
    onCompliment: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val placeholderRes = R.drawable.local_placeholder
    val photoUrls: List<Any> = buildList {
        if (profile.profilepicUrl.isNullOrBlank()) add(placeholderRes) else add(profile.profilepicUrl!!)
        addAll(profile.optionalPhotoUrls)
    }
    var idx by remember(photoUrls) { mutableStateOf(0) }
    val datingViewModel: DatingViewModel = viewModel()
    val compliment = remember(profile.userId) {
        datingViewModel.complimentsReceived.value[profile.userId]
    }
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
    val lifestyleTexts = buildList {
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
            .wrapContentWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .background(Color.Black, shape = RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFFFF6F00), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            maxLines = 10,
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

    LaunchedEffect(Unit) {
        delay(500)
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
    val context = LocalContext.current
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
        LaunchedEffect(profile.userId) {
            runAiMatchCheck(
                context = context,
                coroutineScope = coroutineScope,
                currentUserId = FirebaseAuth.getInstance().uid
                    ?: return@LaunchedEffect,
                currentUserProfile = currentUserProfile!!,
                otherProfile = profile
            ) { }
        }
        CollapsibleSection(
            title = stringResource(R.string.compatibility_check),
            icon = Icons.Default.Info,
        ) {
            if (aiMatchResult != null) {
                ShowAiMatchAnalysis(aiMatchResult)
            } else {
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

    val rawLines = result.compatibilityBreakdown
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val strengths = rawLines.filter { it.startsWith("✅") }
    val concerns  = rawLines.filter { it.startsWith("⚠️") }
    val notes     = rawLines.filter { it.startsWith("ℹ️") }

    Column(modifier = Modifier.padding(8.dp)) {

        Text(
            text  = stringResource(R.string.total_match_label, result.totalMatchPercentage),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(Modifier.height(10.dp))

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

        if (profile.bio.isNotBlank()) {
            ProfileDetailRow(
                label = stringResource(R.string.bio),
                value = profile.bio,
                icon = Icons.Default.BlurOn
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (profile.averageRating > 0.0 && profile.numberOfRatings > 0) {
            RatingBar(rating = profile.averageRating, ratingCount = profile.numberOfRatings)
            Spacer(modifier = Modifier.height(8.dp))
        }
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
            .heightIn(min = 100.dp, max = 400.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
    val summary: String = "",
    val totalMatchPercentage: Int = 0,
    val compatibilityBreakdown: String = "",
    val timestamp: Long = 0L
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

                            database.getReference("messages/$currentUserId/$otherUserId/$pushKey")
                                .setValue(msg)
                            database.getReference("messages/$otherUserId/$currentUserId/$pushKey")
                                .setValue(msg)
                        }
                    }
            }
        }
    // notify other screens to exclude this user
    ExclusionEventBus.emit(otherUserId)
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

    // 14-day exclusion signal (legacy; kept)
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
    var kinkScore = 0.0
    var kinkWeight = 0.0

    fun compareStringField(a: String?, b: String?, label: String, pts: Double) {
        val aTrim = a.orEmpty().trim()
        val bTrim = b.orEmpty().trim()
        if (aTrim.isEmpty() || bTrim.isEmpty()) return
        possible += pts
        if (aTrim.equals(bTrim, true)) {
            score    += pts
            insights += MatchInsight("✅", "$label: $aTrim", true)
        }
    }

    fun rolesAreCompatible(a: Set<String>, b: Set<String>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val normA = a.map { it.lowercase() }
        val normB = b.map { it.lowercase() }
        val flexible = listOf("switch", "vers", "open", "side")
        if (normA.any { r -> flexible.any { r.contains(it) } } ||
            normB.any { r -> flexible.any { r.contains(it) } }) return true
        val topA = normA.any { it.contains("top") }
        val bottomA = normA.any { it.contains("bottom") }
        val domA = normA.any { it.contains("dom") }
        val subA = normA.any { it.contains("sub") }
        val topB = normB.any { it.contains("top") }
        val bottomB = normB.any { it.contains("bottom") }
        val domB = normB.any { it.contains("dom") }
        val subB = normB.any { it.contains("sub") }
        return (topA && bottomB) || (bottomA && topB) || (domA && subB) || (subA && domB)
    }

    // Advanced compatibility
    val rolesA = profileA.roles.toSet()
    val rolesB = profileB.roles.toSet()
    if (rolesAreCompatible(rolesA, rolesB)) {
        insights += MatchInsight("✅", context.getString(R.string.roles_compatible), true)
    }

    val tribesA = profileA.tribes.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val tribesB = profileB.tribes.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val sharedTribes = tribesA intersect tribesB
    if (sharedTribes.isNotEmpty()) {
        insights += MatchInsight("✅", context.getString(R.string.shared_tribes_prefix, sharedTribes.joinToString()), true)
    }

    val kinksA = profileA.kinks.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    val kinksB = profileB.kinks.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    if (kinksA.isNotEmpty() && kinksB.isNotEmpty()) {
        kinkWeight = 0.25
        val shared = kinksA intersect kinksB
        val union = kinksA union kinksB
        kinkScore = if (union.isNotEmpty()) (shared.size.toDouble() / union.size.toDouble()) * 100.0 else 0.0
        if (shared.isNotEmpty()) {
            insights += MatchInsight("✅", context.getString(R.string.shared_kinks_prefix, shared.joinToString()), true)
        }
    }

    val ageA = profileA.age
    val ageB = profileB.age
    if (ageA > 0 && ageB > 0) {
        possible += 5.0
        val aScore = ageCompatibilityScore(ageA, ageB)
        score += 5.0 * aScore
        val pct = (aScore * 100).roundToInt()
        if (aScore >= 0.5) {
            insights += MatchInsight(
                "✅",
                context.getString(R.string.age_compatibility_prefix, ageA, ageB, pct),
                true
            )
        }
    }

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
        if (pct >= 50) {
            insights += MatchInsight(
                "✅",
                context.getString(R.string.lifestyle_similarity_format, pct, lifeCount, listStr),
                true
            )
        }
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
        }
    }

    val causesA = profileA.socialCauses.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val causesB = profileB.socialCauses.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    if (causesA.isNotEmpty() && causesB.isNotEmpty()) {
        possible += 6.0
        val shared = causesA intersect causesB
        if (shared.isNotEmpty()) {
            val pts = (shared.size * 2).coerceAtMost(6)
            score += pts
            insights += MatchInsight("✅", context.getString(R.string.shared_social_causes_prefix, shared.joinToString()), true)
        }
    }

    val zodiacA = if (!profileA.zodiac.isNullOrBlank()) profileA.zodiac!! else deriveZodiac(profileA.dob)
    val zodiacB = if (!profileB.zodiac.isNullOrBlank()) profileB.zodiac!! else deriveZodiac(profileB.dob)
    if (zodiacA != "Unknown" && zodiacB != "Unknown") {
        possible += 5.0
        val zScore = zodiacCompatibilityScore(zodiacA, zodiacB)
        score += 5.0 * zScore
        val pct = (zScore * 100).roundToInt()
        if (zScore >= 0.5) {
            insights += MatchInsight(
                "✅",
                context.getString(
                    R.string.zodiac_compatibility_prefix,
                    zodiacA,
                    zodiacB,
                    pct
                ),
                true
            )
        }
    }

    if (profileA.isMatrimonyMode && profileB.isMatrimonyMode) {
        possible += 3.0
        score += 3.0
        compareStringField(profileA.marriageTimeline, profileB.marriageTimeline,
            context.getString(R.string.marriage_timeline_label), 3.0)
    }

    val relocA = profileA.relocationPreference.orEmpty().trim()
    val relocB = profileB.relocationPreference.orEmpty().trim()
    if (relocA.isNotBlank() && relocB.isNotBlank()) {
        possible += 4.0
        if (relocA.equals(relocB, true)) score += 4.0
        else insights += MatchInsight("⚠️", context.getString(R.string.relocation_mismatch_format, relocA, relocB), false)
    }

    val careerA = profileA.postMarriageCareerPlan.orEmpty().trim()
    val careerB = profileB.postMarriageCareerPlan.orEmpty().trim()
    if (careerA.isNotBlank() && careerB.isNotBlank()) {
        possible += 3.0
        if (careerA.equals(careerB, true)) score += 3.0
        else insights += MatchInsight("⚠️", context.getString(R.string.career_plan_mismatch_format, careerA, careerB), false)
    }

    val cultureA = profileA.traditionalVsLiberal.orEmpty().trim()
    val cultureB = profileB.traditionalVsLiberal.orEmpty().trim()
    if (cultureA.isNotBlank() && cultureB.isNotBlank()) {
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
        }
    }

    val base = if (possible == 0.0) 0.0 else (score / possible) * 100.0
    val combinedBase = base * (1 - kinkWeight) + kinkScore * kinkWeight
    val targetCompositePct = profileB.compositeScorePct
    val blended = ((2 * combinedBase) + targetCompositePct) / 3.0
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

/* ====== existing helpers kept as-is (place search, dropdowns, etc.) ====== */

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
        onExpandedChange = { },
    ) {
        val focusRequester = remember { FocusRequester() }

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
                        color       = BrandOrange
                    )
                } else {
                    Icon(
                        imageVector      = Icons.Default.Search,
                        contentDescription = null,
                        tint              = BrandOrange
                    )
                }
            },

            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                textColor   = Color.White,
                focusedBorderColor   = Color.White,
                unfocusedBorderColor = Color.White,
                focusedLabelColor   = Color.White,
                unfocusedLabelColor = Color.White,
                placeholderColor   = Color.White,
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
    val clearSelectionText = stringResource(R.string.clear_selection)
    val allOptions = listOf(clearSelectionText) + options

    Column {
        Text(label, color = Color.White, fontSize = 16.sp)
        Box {
            Button(
                onClick = { expanded = !expanded },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (selectedOption.isNotBlank()) Color(0xFFFF6000) else Color(0xFF1A1A1A)
                ),
                border = BorderStroke(1.dp, Color.White),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .height(dimensionResource(id = R.dimen.btn_height))
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
                            if (option == clearSelectionText) {
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

/**
 * Load swipes from Firebase and reset quota if a new day has started.
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

    var remaining = snapRemaining

    if (newDay) {
        remaining = quota
    }

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
    val quota = when {
        isPremium  -> Int.MAX_VALUE
        isPlus     -> 50
        else       -> 20
    }

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
            .pointerInput(Unit) {},
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
