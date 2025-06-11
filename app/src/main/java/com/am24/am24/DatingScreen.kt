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
import android.widget.Toast
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.navigation.compose.currentBackStackEntryAsState
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*
import kotlinx.coroutines.CoroutineScope
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

    // 1) watch for “are we still on the Dating route?”
    val backstackEntry by navController.currentBackStackEntryAsState()
    val currentRoute    = backstackEntry?.destination?.route

    /* Auto-tap counter for empty profiles */
    var autoTapCount by rememberSaveable { mutableStateOf(0) }
    val maxAutoTaps = 0
    val verificationStatuses by datingViewModel.verificationStatuses.collectAsState()

    /* fetch *my* Profile once */
    LaunchedEffect(Unit) {
        profileViewModel.fetchCurrentUserProfile()
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
    val filteredProfiles  by datingViewModel.displayingProfiles.collectAsState()
    val isLoading         by datingViewModel.isLoading.collectAsState()
    val matchPopUpState   by profileViewModel.matchPopUpState.collectAsState()
    val boostedUsers      by datingViewModel.boostedUsers.collectAsState()
    val complimentsLeft   by datingViewModel.complimentsLeft.collectAsState()
    val complimentsRecv   by datingViewModel.complimentsReceived.collectAsState()
    // ── Misc local state ─────────────────────────────────────────────
    var excludedUserIds   by remember { mutableStateOf(emptySet<String>()) }
    var remainingSwipes   by remember { mutableStateOf(0) }
    var swipesLoaded      by remember { mutableStateOf(false) }
    var showBoostFlash    by rememberSaveable { mutableStateOf(false) }

    // constants
    val BOOST_DURATION = 6 * 60 * 60 * 1000L
    val now = remember { System.currentTimeMillis() }
    val last = myProfile?.lastBoostTimestamp ?: 0L
    val inCooldown = now - last < BOOST_DURATION
    val canBoost = myProfile?.availableBoosts!! > 0 && !inCooldown

    var showComplimentDlg by remember { mutableStateOf(false) }

// ── 1) replace the existing `needsVerification` val with a mutable state ─────────
    val user                 = FirebaseAuth.getInstance().currentUser
    val isPwdUser            = user?.providerData?.any { it.providerId == "password" } == true
    var needsVerification by remember {         // ← make it mutable
        mutableStateOf(isPwdUser && user?.isEmailVerified == false)
    }

    // 2) dialog state
    var showVerifyDialog by remember { mutableStateOf(false) }
    var isSendingEmail   by remember { mutableStateOf(false) }
    val context = LocalContext.current

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

    // —— see which Profile objects are being dropped ————————————————
    val excludedProfiles = filteredProfiles.filter { it.userId in excludedUserIds }
    excludedProfiles.dump("EXCLUDED_PROFILES")          // <-- NEW LOG
    // ─────────────────────────────────────────────────────────────────
    //   BUILD DISPLAY LIST  (must come *before* we use it)
    // ─────────────────────────────────────────────────────────────────
    val base = filteredProfiles
        .filter { it.userId !in excludedUserIds }
        // hide private profiles, unless *they* liked you:
        .filter { prof ->
            !prof.isPrivate || prof.userId in likers
        }
        .also { it.dump("BASE") }

    val complimentersList = complimentsRecv.keys
        .mapNotNull { id -> base.find { it.userId == id } }
        .also { it.dump("COMP") }

    val boostedList = boostedUsers
        .filter { it.userId !in complimentersList.map { p -> p.userId } }
        .filter { it.userId !in excludedUserIds }
        .also { it.dump("BOOST") }

    val premiumList = base
        .filter { it.userId !in complimentersList.map { p -> p.userId } }
        .filter { it.userId !in boostedList.map    { p -> p.userId } }
        .filter { it.isPremium }
        .also { it.dump("PREM") }

    val restList = base
        .filter { it.userId !in complimentersList.map { p -> p.userId } }
        .filter { it.userId !in boostedList.map    { p -> p.userId } }
        .filter { !it.isPremium }
        .also { it.dump("REST") }

    val displayedProfiles = complimentersList + boostedList + premiumList + restList
    Log.d("DS-FLOW", "DISPLAYED   size=${displayedProfiles.size}")

    // ── Hoisted deck pointer ─────────────────────────────────────────
    var currentIndex      by rememberSaveable { mutableStateOf(0) }
    val currentSwipeProfile = displayedProfiles.getOrNull(currentIndex)

    // inside DatingScreen (or DatingScreenContent) where you have `currentSwipeProfile`:
    LaunchedEffect(currentSwipeProfile?.userId) {
        Log.d("DatingScreen", "Composable – currentSwipeProfile.userId = ${currentSwipeProfile?.userId}")
        datingViewModel.setCurrentSwipeUserId(currentSwipeProfile?.userId)
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
                onSaveFilters = {
                    coroutineScope.launch {
                        sheetState.hide()
                        datingViewModel.refreshFilteredProfiles()
                    }
                },
                onCancel = { coroutineScope.launch { sheetState.hide() } },
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
            .background(Color.Black)
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
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(27.dp)
                    )
                }

                // RatingBar centered
                currentSwipeProfile?.let { prof ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        RatingBar(
                            rating = prof.averageRating,
                            ratingCount = prof.numberOfRatings
                        )
                    }
                }

                Row {
                    /* 👍  Compliments */
                    WaterIconButton(
                        quota    = complimentsLeft,
                        maxQuota = 15,
                        icon     = Icons.Default.AttachEmail,
                        enabled  = complimentsLeft > 0,
                        onClick   =   {
                                    if (complimentsLeft > 0) showComplimentDlg = true
                        }   // <- see next section }
                    )

                    Spacer(Modifier.width(13.dp))

                    /* ⚡  Boosts */
                    WaterIconButton(
                        quota    = myProfile!!.availableBoosts,
                        maxQuota = 15,
                        icon     = Icons.Default.FlashOn,
                        tint     = if (canBoost) Color.White else Color.Gray,
                        enabled  = canBoost,
                        onClick = {
                            val myUid = FirebaseAuth.getInstance().uid ?: return@WaterIconButton
                            datingViewModel.boostUser(myUid) {
                                profileViewModel.decrementBoostsLocal()
                                showBoostFlash = true
                                profileViewModel.fetchCurrentUserProfile()
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
                        Modifier.fillMaxSize().background(Color(0x88FFFF00)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FlashOn, null,
                            tint = Color(0xFFFF6F00),
                            modifier = Modifier.size(96.dp)
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
                    isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFFFF6F00)
                    )

                    displayedProfiles.isEmpty() -> NoMoreProfilesScreen(
                        autoTapCount = autoTapCount,
                        maxAutoTaps = maxAutoTaps
                    )

                    else -> DatingScreenContent(
                        navController    = navController,
                        verificationStatuses = verificationStatuses,  // pass it down
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
                            currentIndex++
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
                        isPremium = myProfile!!.isPremium
                    ) {
                        showSwipeLimitOverlay = false
                    }
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
            .clickable(enabled = enabled, onClick = onClick),
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
    val swipesRef = FirebaseRefs.db.getReference("users/$userId/swipesInfo")
    val snapshot = swipesRef.get().await()
    var remainingSwipes = 15
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
        remainingSwipes = 15
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

@Composable
fun SwipeLimitOverlay(
    remainingSwipes: Int,
    isPlus: Boolean,
    isPremium: Boolean,
    onDismiss: () -> Unit
) {
    // compute your daily quota
    val quota = when {
        isPremium  -> Int.MAX_VALUE
        isPlus     -> 50
        else       -> 15
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
            .background(Color.Black.copy(alpha = 0.8f))
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
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00))) {
                    Text("OK", color = Color.Black)
                }
            }
        }
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
    onCancel: () -> Unit,

    // ← no change here: these two flags come from DatingScreen’s myProfile!!.isPlus / isPremium
    isPlus: Boolean,
    isPremium: Boolean,

    // the current values of each “power” filter (even if locked)
    minRating: Float,
    onMinRatingChange: (Float) -> Unit,
    maxRanking: Int,
    onMaxRankingChange: (Int) -> Unit
) {
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
            Text(
                text = stringResource(R.string.filters),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Button(
                onClick = { onSaveFilters() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00)),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(48.dp)
            ) {
                Text(stringResource(R.string.save), color = Color.Black)
            }
        }

        LazyColumn {
            // ─── BASIC Filters ─────────────────────────────────────────────
            item {
                FilterSectionTitle(title = stringResource(R.string.basic_filters))
                Spacer(modifier = Modifier.height(8.dp))

                // Gender pills (pre‐populated via selectedGenders)
                Text(stringResource(R.string.gender_preference), color = Color.White)
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

                Spacer(modifier = Modifier.height(16.dp))

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
                    valueRange = 0f..100f,
                    steps = 82,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF6000),
                        activeTrackColor = Color(0xFFFF6000),
                        inactiveTrackColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Max Distance Slider (unchanged)
                Text(
                    text = if (maxDistance == DatingViewModel.WORLDWIDE_DISTANCE)
                        stringResource(R.string.worldwide)
                    else
                        stringResource(R.string.max_distance, maxDistance),
                    color = Color.White
                )
                Slider(
                    value = maxDistance.toFloat(),
                    onValueChange = { onDistanceChange(it.roundToInt()) },
                    valueRange = 0f..DatingViewModel.WORLDWIDE_DISTANCE.toFloat(),
                    steps = 10,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF6000),
                        activeTrackColor = Color(0xFFFF6000),
                        inactiveTrackColor = Color.Gray
                    )
                )
            }

            /* ───── POWER FILTERS ──────────────────────────────────────── */

            /* 1) Minimum Rating (0..5 stars) – visible to everyone, but locked for non‐Plus users */
            item {
                Spacer(Modifier.height(24.dp))
                FilterSectionTitle(stringResource(R.string.rating_label))

                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.min_rating, minRating),
                    color = if (isPlus || isPremium) Color.White else Color.Gray
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
                    enabled = (isPlus || isPremium),
                    colors = SliderDefaults.colors(
                        thumbColor = if (isPlus || isPremium) Color(0xFFFF6000) else Color.Gray,
                        activeTrackColor = if (isPlus || isPremium) Color(0xFFFF6000) else Color.Gray,
                        inactiveTrackColor = Color.DarkGray
                    )
                )
                if (!(isPlus || isPremium)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.upgrade_to_plus_to_unlock),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            /* 2) Top‐N Ranking (1..100) – visible to everyone, but locked for non‐Premium users */
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
                    steps = 99,
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
                        fontSize = 11.sp
                    )
                }
            }

            /* ─── EDUCATION Filters ──────────────────────────────────── */
            item {
                Spacer(Modifier.height(24.dp))
                FilterSectionTitle(title = stringResource(R.string.education))

                Spacer(Modifier.height(8.dp))
                // ─ High School ─
                DropdownFilter(
                    label = stringResource(R.string.high_school),
                    options = listOf(
                        stringResource(R.string.high_school_modern_school_barakhamba),
                        stringResource(R.string.high_school_dps_rk_puram),
                        stringResource(R.string.high_school_shri_ram_gurgaon),
                        stringResource(R.string.high_school_amity_noida),
                        stringResource(R.string.high_school_sanskriti_delhi),
                        stringResource(R.string.high_school_city_montessori_lucknow),
                        stringResource(R.string.high_school_la_martiniere_lucknow),
                        stringResource(R.string.high_school_doon_school),
                        stringResource(R.string.high_school_welham_girls),
                        stringResource(R.string.high_school_lawrence_sanawar),
                        stringResource(R.string.high_school_mayo_college),
                        stringResource(R.string.high_school_st_johns_chandigarh),
                        stringResource(R.string.high_school_birla_pilani),
                        stringResource(R.string.high_school_scindia_school),
                        stringResource(R.string.high_school_yds_srinagar),
                        // West India (Maharashtra, Gujarat, Madhya Pradesh, Goa)
                        stringResource(R.string.high_school_cathedral_john_connon),
                        stringResource(R.string.high_school_dhirubhai_ambani),
                        stringResource(R.string.high_school_bombay_scottish_mahim),
                        stringResource(R.string.high_school_campion_mumbai),
                        stringResource(R.string.high_school_jamnabai_narsee),
                        stringResource(R.string.high_school_st_marys_pune),
                        stringResource(R.string.high_school_symbiosis_pune),
                        stringResource(R.string.high_school_daly_college),
                        stringResource(R.string.high_school_podar_ahmedabad),
                        stringResource(R.string.high_school_anand_niketan),
                        stringResource(R.string.high_school_don_bosco_panaji),
                        stringResource(R.string.high_school_emerald_heights_indore),
                        // South India (Karnataka, Tamil Nadu, Andhra Pradesh, Telangana, Kerala)
                        stringResource(R.string.high_school_bishop_cotton_boys),
                        stringResource(R.string.high_school_bishop_cotton_girls),
                        stringResource(R.string.high_school_mallya_aditi),
                        stringResource(R.string.high_school_national_public_indiranagar),
                        stringResource(R.string.high_school_psbb_chennai),
                        stringResource(R.string.high_school_chettinad_vidyashram),
                        stringResource(R.string.high_school_dav_velachery),
                        stringResource(R.string.high_school_sishya_chennai),
                        stringResource(R.string.high_school_hyderabad_public),
                        stringResource(R.string.high_school_international_hyderabad),
                        stringResource(R.string.high_school_rishi_valley),
                        stringResource(R.string.high_school_loyola_trivandrum),
                        stringResource(R.string.high_school_trivandrum_international),
                        stringResource(R.string.high_school_chinmaya_coimbatore),
                        stringResource(R.string.high_school_sainik_amaravathinagar),
                        // East India (West Bengal, Odisha, Jharkhand, Bihar)
                        stringResource(R.string.high_school_st_xaviers_collegiate),
                        stringResource(R.string.high_school_la_martiniere_boys),
                        stringResource(R.string.high_school_la_martiniere_girls),
                        stringResource(R.string.high_school_modern_high_girls),
                        stringResource(R.string.high_school_south_point),
                        stringResource(R.string.high_school_don_bosco_park_circus),
                        stringResource(R.string.high_school_loreto_house),
                        stringResource(R.string.high_school_calcutta_boys),
                        stringResource(R.string.high_school_calcutta_girls),
                        stringResource(R.string.high_school_hindu_school),
                        stringResource(R.string.high_school_ramakrishna_narendrapur),
                        stringResource(R.string.high_school_st_james_kolkata),
                        stringResource(R.string.high_school_dps_bhubaneswar),
                        stringResource(R.string.high_school_sai_bhubaneswar),
                        stringResource(R.string.high_school_loyola_patna),
                        stringResource(R.string.high_school_st_michaels_patna),
                        stringResource(R.string.high_school_netarhat),
                        stringResource(R.string.high_school_chinmaya_bokaro),
                        stringResource(R.string.high_school_dps_ranchi),
                        // Northeast India (Assam, Meghalaya, Sikkim, Tripura)
                        stringResource(R.string.high_school_assam_valley),
                        stringResource(R.string.high_school_don_bosco_guwahati),
                        stringResource(R.string.high_school_spring_dale_guwahati),
                        stringResource(R.string.high_school_loreto_shillong),
                        stringResource(R.string.high_school_st_anthonys_shillong),
                        stringResource(R.string.high_school_don_bosco_agartala),
                        // Union Territories and Special Cases
                        stringResource(R.string.high_school_north_point_darjeeling),
                        stringResource(R.string.high_school_st_josephs_north_point),
                        stringResource(R.string.high_school_dr_grahams_kalimpong),
                        stringResource(R.string.high_school_lawrence_lovedale),
                        stringResource(R.string.high_school_dps_port_blair),
                        stringResource(R.string.high_school_dps_srinagar),
                        // Catch-all for other high schools
                        stringResource(R.string.high_school_other)
                    ),
                    selectedOption = selectedHighSchool,
                    onOptionChange = onHighSchoolChange
                )
                Spacer(modifier = Modifier.height(8.dp))

                // ─ College ─
                DropdownFilter(
                    label = stringResource(R.string.college),
                    options = listOf(
                        stringResource(R.string.college_srm_institute_of_science_and_technology),
                        stringResource(R.string.college_vellore_institute_of_technology),
                        stringResource(R.string.college_bits_pilani),
                        stringResource(R.string.college_manipal_institute_of_technology),
                        stringResource(R.string.college_iiit_hyderabad),
                        // Private Arts/Science Colleges
                        stringResource(R.string.college_op_jindal_global_university),
                        stringResource(R.string.college_ashoka_university),
                        // Design Colleges in India
                        stringResource(R.string.college_nid_kurukshetra),
                        stringResource(R.string.college_nid_gandhinagar),
                        stringResource(R.string.college_nid_bengaluru),
                        stringResource(R.string.college_nid_bhopal),
                        stringResource(R.string.college_nid_jorhat),
                        stringResource(R.string.college_nid_vijayawada),
                        stringResource(R.string.college_nift),
                        stringResource(R.string.college_srishti_manipal),
                        stringResource(R.string.college_pearl_academy),
                        stringResource(R.string.college_symbiosis_institute_of_design),
                        stringResource(R.string.college_mit_institute_of_design),
                        stringResource(R.string.college_iiad),
                        stringResource(R.string.college_world_university_of_design),
                        stringResource(R.string.college_amity_school_of_fashion_technology),
                        stringResource(R.string.college_jd_institute_of_fashion_technology),
                        stringResource(R.string.college_arch_academy_of_design),
                        stringResource(R.string.college_daiict),
                        // Law Colleges in India
                        stringResource(R.string.college_nlu_delhi),
                        stringResource(R.string.college_wbnujs_kolkata),
                        stringResource(R.string.college_nliu_bhopal),
                        stringResource(R.string.college_gnlu_gandhinagar),
                        stringResource(R.string.college_hnlu_raipur),
                        stringResource(R.string.college_rmlnlu_lucknow),
                        stringResource(R.string.college_rgnul_patiala),
                        stringResource(R.string.college_cnlu_patna),
                        stringResource(R.string.college_nuals_kochi),
                        stringResource(R.string.college_nluo_cuttack),
                        stringResource(R.string.college_nusr_law_ranchi),
                        stringResource(R.string.college_nluja_guwahati),
                        stringResource(R.string.college_tnnlu_tiruchirappalli),
                        stringResource(R.string.college_mnlu_mumbai),
                        stringResource(R.string.college_mnlu_nagpur),
                        stringResource(R.string.college_mnlu_aurangabad),
                        stringResource(R.string.college_hpnlu_shimla),
                        stringResource(R.string.college_dnlu_jabalpur),
                        stringResource(R.string.college_dbranlu_sonipat),
                        stringResource(R.string.college_faculty_of_law_du),
                        stringResource(R.string.college_symbiosis_law_school),
                        stringResource(R.string.college_glc_mumbai),
                        stringResource(R.string.college_ils_law_pune),
                        stringResource(R.string.college_amity_law_school_noida),
                        stringResource(R.string.college_christ_univ_law),
                        stringResource(R.string.college_bhu_faculty_of_law),
                        stringResource(R.string.college_amu_faculty_of_law),
                        stringResource(R.string.college_jamia_law),
                        stringResource(R.string.college_op_jindal_law_school),
                        stringResource(R.string.college_army_institute_of_law_mohali),
                        stringResource(R.string.college_kerala_law_academy),
                        stringResource(R.string.college_school_of_law_calcutta),
                        // Arts Colleges in India
                        stringResource(R.string.college_college_of_art_du),
                        stringResource(R.string.college_sir_jj_school_of_art),
                        stringResource(R.string.college_faculty_visual_arts_bhu),
                        stringResource(R.string.college_msu_fine_arts_vadodara),
                        stringResource(R.string.college_govt_college_art_craft_kolkata),
                        stringResource(R.string.college_chennai_govt_fine_arts),
                        stringResource(R.string.college_rachana_sansad),
                        stringResource(R.string.college_goa_college_of_art),
                        stringResource(R.string.college_amity_school_fine_arts),
                        stringResource(R.string.college_kalakshetra_foundation),
                        stringResource(R.string.college_bharatiya_kala_kendra),
                        stringResource(R.string.college_gandharva_mahavidyalaya),
                        stringResource(R.string.college_nsd),
                        stringResource(R.string.college_ftii_pune),
                        stringResource(R.string.college_srfti_kolkata),
                        stringResource(R.string.college_kathak_kendra),
                        stringResource(R.string.college_drama_thrissur),
                        stringResource(R.string.college_ramjas_college),
                        stringResource(R.string.college_st_xaviers_mumbai),
                        // North India (Delhi, Haryana, Uttar Pradesh, Uttarakhand, Punjab, Rajasthan)
                        stringResource(R.string.college_st_stephens),
                        stringResource(R.string.college_miranda_house),
                        stringResource(R.string.college_hindu_college),
                        stringResource(R.string.college_lady_shri_ram),
                        stringResource(R.string.college_hansraj_college),
                        stringResource(R.string.college_delhi_university),
                        stringResource(R.string.college_iit_delhi),
                        stringResource(R.string.college_jnu_delhi),
                        stringResource(R.string.college_op_jindal_global),
                        stringResource(R.string.college_amity_noida),
                        stringResource(R.string.college_nift_delhi),
                        stringResource(R.string.college_pearl_academy_delhi),
                        stringResource(R.string.college_amity_law_school),
                        stringResource(R.string.college_banaras_hindu_university),
                        stringResource(R.string.college_iit_kanpur),
                        stringResource(R.string.college_iit_roorkee),
                        stringResource(R.string.college_lpu_phagwara),
                        stringResource(R.string.college_chandigarh_university),
                        // West India (Maharashtra, Gujarat, Goa)
                        stringResource(R.string.college_iit_bombay),
                        stringResource(R.string.college_mithibai_college),
                        stringResource(R.string.college_nmims_mumbai),
                        stringResource(R.string.college_university_of_mumbai),
                        stringResource(R.string.college_jj_school_arts),
                        stringResource(R.string.college_fergusson_college),
                        stringResource(R.string.college_symbiosis_liberal_arts),
                        stringResource(R.string.college_flame_university),
                        stringResource(R.string.college_savitribai_phule_pune_university),
                        stringResource(R.string.college_symbiosis_law_school),
                        stringResource(R.string.college_iit_gandhinagar),
                        stringResource(R.string.college_nid_ahmedabad),
                        stringResource(R.string.college_nirma_university),
                        stringResource(R.string.college_goa_university),
                        // South India (Karnataka, Tamil Nadu, Andhra Pradesh, Telangana, Kerala)
                        stringResource(R.string.college_iisc_bangalore),
                        stringResource(R.string.college_christ_university),
                        stringResource(R.string.college_mount_carmel),
                        stringResource(R.string.college_st_josephs_bangalore),
                        stringResource(R.string.college_nlsiu_bangalore),
                        stringResource(R.string.college_iit_madras),
                        stringResource(R.string.college_loyola_college),
                        stringResource(R.string.college_madras_christian_college),
                        stringResource(R.string.college_anna_university),
                        stringResource(R.string.college_stella_maris),
                        stringResource(R.string.college_krea_university),
                        stringResource(R.string.college_osmania_university),
                        stringResource(R.string.college_manipal_academy),
                        stringResource(R.string.college_andhra_university),
                        stringResource(R.string.college_annamalai_university),
                        stringResource(R.string.college_kerala_university),
                        // East India (West Bengal, Odisha, Jharkhand, Bihar)
                        stringResource(R.string.college_jadavpur_university),
                        stringResource(R.string.college_presidency_university),
                        stringResource(R.string.college_st_xaviers_kolkata),
                        stringResource(R.string.college_scottish_church),
                        stringResource(R.string.college_university_of_calcutta),
                        stringResource(R.string.college_iit_kharagpur),
                        stringResource(R.string.college_nit_durgapur),
                        stringResource(R.string.college_loreto_college),
                        stringResource(R.string.college_lady_brabourne),
                        stringResource(R.string.college_bethune_college),
                        stringResource(R.string.college_ramakrishna_narendrapur),
                        stringResource(R.string.college_goenka_college),
                        stringResource(R.string.college_visva_bharati),
                        stringResource(R.string.college_nit_rourkela),
                        stringResource(R.string.college_iit_dhanbad),
                        // Northeast India (Assam, Meghalaya)
                        stringResource(R.string.college_gauhati_university),
                        stringResource(R.string.college_nehu_shillong),
                        stringResource(R.string.college_cotton_university),
                        // Union Territories
                        stringResource(R.string.college_jamia_millia_islamia),
                        // Catch-all for other colleges
                        stringResource(R.string.college_other)
                    ),
                    selectedOption = selectedCollege,
                    onOptionChange = onCollegeChange
                )
                Spacer(modifier = Modifier.height(8.dp))

                // ─ Post‐Grad ─
                DropdownFilter(
                    label = stringResource(R.string.post_grad),
                    options = listOf(
                        stringResource(R.string.postgrad_delhi_university),
                        stringResource(R.string.postgrad_iit_delhi),
                        stringResource(R.string.postgrad_jnu_delhi),
                        stringResource(R.string.postgrad_ashoka_university),
                        stringResource(R.string.postgrad_amity_noida),
                        stringResource(R.string.postgrad_nift_delhi),
                        stringResource(R.string.postgrad_pearl_academy_delhi),
                        stringResource(R.string.postgrad_ili_delhi),
                        stringResource(R.string.postgrad_banaras_hindu_university),
                        stringResource(R.string.postgrad_iit_kanpur),
                        stringResource(R.string.postgrad_iit_roorkee),
                        stringResource(R.string.postgrad_lpu_phagwara),
                        stringResource(R.string.postgrad_chandigarh_university),
                        stringResource(R.string.postgrad_iim_lucknow),
                        stringResource(R.string.postgrad_iim_udaipur),
                        // West India (Maharashtra, Gujarat, Goa)
                        stringResource(R.string.postgrad_iit_bombay),
                        stringResource(R.string.postgrad_university_of_mumbai),
                        stringResource(R.string.postgrad_nmims_mumbai),
                        stringResource(R.string.postgrad_tiss_mumbai),
                        stringResource(R.string.postgrad_tifr_mumbai),
                        stringResource(R.string.postgrad_jj_school_arts),
                        stringResource(R.string.postgrad_savitribai_phule_pune_university),
                        stringResource(R.string.postgrad_symbiosis_law_school),
                        stringResource(R.string.postgrad_iit_gandhinagar),
                        stringResource(R.string.postgrad_nirma_university),
                        stringResource(R.string.postgrad_iim_ahmedabad),
                        stringResource(R.string.postgrad_goa_university),
                        // South India (Karnataka, Tamil Nadu, Andhra Pradesh, Telangana, Kerala)
                        stringResource(R.string.postgrad_iisc_bangalore),
                        stringResource(R.string.postgrad_christ_university),
                        stringResource(R.string.postgrad_nlsiu_bangalore),
                        stringResource(R.string.postgrad_iim_bangalore),
                        stringResource(R.string.postgrad_iit_madras),
                        stringResource(R.string.postgrad_anna_university),
                        stringResource(R.string.postgrad_srmist_chennai),
                        stringResource(R.string.postgrad_vit_vellore),
                        stringResource(R.string.postgrad_osmania_university),
                        stringResource(R.string.postgrad_nalsar_hyderabad),
                        stringResource(R.string.postgrad_manipal_academy),
                        stringResource(R.string.postgrad_andhra_university),
                        stringResource(R.string.postgrad_annamalai_university),
                        stringResource(R.string.postgrad_kerala_university),
                        stringResource(R.string.postgrad_iim_kozhikode),
                        stringResource(R.string.postgrad_nit_warangal),
                        // East India (West Bengal, Odisha, Jharkhand, Bihar)
                        stringResource(R.string.postgrad_jadavpur_university),
                        stringResource(R.string.postgrad_presidency_university),
                        stringResource(R.string.postgrad_university_of_calcutta),
                        stringResource(R.string.postgrad_iit_kharagpur),
                        stringResource(R.string.postgrad_nit_durgapur),
                        stringResource(R.string.postgrad_isi_kolkata),
                        stringResource(R.string.postgrad_iim_calcutta),
                        stringResource(R.string.postgrad_visva_bharati),
                        stringResource(R.string.postgrad_nit_rourkela),
                        stringResource(R.string.postgrad_iit_dhanbad),
                        stringResource(R.string.postgrad_xlri_jamshedpur),
                        // Northeast India (Assam, Meghalaya)
                        stringResource(R.string.postgrad_gauhati_university),
                        stringResource(R.string.postgrad_nehu_shillong),
                        stringResource(R.string.postgrad_cotton_university),
                        stringResource(R.string.postgrad_iim_shillong),
                        // Union Territories
                        stringResource(R.string.postgrad_jamia_millia_islamia),
                        stringResource(R.string.postgrad_iim_vishakhapatnam),
                        // Catch-all for other post-graduate institutions
                        stringResource(R.string.postgrad_other)
                    ),
                    selectedOption = selectedPostGrad,
                    onOptionChange = onPostGradChange
                )
            }

            /* ─── PREFERENCES Filters ───────────────────────────────── */
            item {
                Spacer(modifier = Modifier.height(24.dp))
                FilterSectionTitle(title = stringResource(R.string.preferences))

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DropdownFilter(
                        label = stringResource(R.string.community),
                        options = listOf(
                            stringResource(R.string.community_adi),
                            stringResource(R.string.community_nyishi),
                            stringResource(R.string.community_bodo),
                            stringResource(R.string.community_assamese),
                            stringResource(R.string.community_manipuri),
                            stringResource(R.string.community_khasi),
                            stringResource(R.string.community_mizo),
                            stringResource(R.string.community_naga),
                            stringResource(R.string.community_tripuri),
                            // East India
                            stringResource(R.string.community_bengali),
                            stringResource(R.string.community_santhal),
                            stringResource(R.string.community_oraon),
                            stringResource(R.string.community_munda),
                            // North India
                            stringResource(R.string.community_punjabi),
                            stringResource(R.string.community_marwari),
                            stringResource(R.string.community_rajasthani),
                            stringResource(R.string.community_kashmiri),
                            stringResource(R.string.community_himachali),
                            stringResource(R.string.community_haryanvi),
                            stringResource(R.string.community_garhwali),
                            // Central India
                            stringResource(R.string.community_madhya_pradeshi),
                            stringResource(R.string.community_chhattisgarhi),
                            // South India
                            stringResource(R.string.community_tamil),
                            stringResource(R.string.community_telugu),
                            stringResource(R.string.community_kannadiga),
                            stringResource(R.string.community_malayali),
                            // West India
                            stringResource(R.string.community_gujarati),
                            stringResource(R.string.community_marathi),
                            stringResource(R.string.community_goan),
                            stringResource(R.string.community_konkani),
                            // Other Regions
                            stringResource(R.string.community_odia),
                            stringResource(R.string.community_bihari),
                            stringResource(R.string.community_sikkimese),
                            stringResource(R.string.community_nepali),
                            stringResource(R.string.community_ladakhi),
                            stringResource(R.string.community_andamanese),
                            stringResource(R.string.community_lakhadweepi),
                            // Catch-all
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

                Row(modifier = Modifier.fillMaxWidth()) {
                    DropdownFilter(
                        label = stringResource(R.string.caste),
                        options = listOf(
                            stringResource(R.string.caste_brahmin),
                            stringResource(R.string.caste_kayastha),
                            stringResource(R.string.caste_baidya),
                            stringResource(R.string.caste_kshatriya),
                            stringResource(R.string.caste_vaishya),
                            stringResource(R.string.caste_rajvanshi),
                            stringResource(R.string.caste_sadgop),
                            stringResource(R.string.caste_mahishya),
                            stringResource(R.string.caste_jat),
                            stringResource(R.string.caste_rajput),
                            stringResource(R.string.caste_yadav),
                            stringResource(R.string.caste_vellalar),
                            stringResource(R.string.caste_naidu),
                            stringResource(R.string.caste_ezhava),
                            stringResource(R.string.caste_gowda),
                            stringResource(R.string.caste_patel),
                            stringResource(R.string.caste_maratha),
                            stringResource(R.string.caste_kurmi),
                            stringResource(R.string.caste_lingayat),
                            stringResource(R.string.caste_reddy),
                            stringResource(R.string.caste_bhil),
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
        Text(label, color = Color.White, fontSize = 11.sp)
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
fun NoMoreProfilesScreen(autoTapCount: Int = 0, maxAutoTaps: Int = 0) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.no_more_profiles) + " No profiles found after $maxAutoTaps attempts.",
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
    }
}

@Composable
fun DatingScreenContent(
    navController: NavController,
    verificationStatuses: Map<String,String>,
    geoFire: GeoFire,
    profileViewModel: ProfileViewModel,
    postViewModel: PostViewModel,
    profiles: List<Profile>,
    currentIndex: Int,                 // 🔹  index is now owned by parent
    boostedUsers: List<Profile>,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit
) {
    if (profiles.isEmpty() || currentIndex >= profiles.size) {
        NoMoreProfilesScreen()
        return
    }

    val currentUserId      = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val currentUserProfile by profileViewModel.currentUserProfile.collectAsState()
    val currentProfile     = profiles[currentIndex]
    val isBoostedProfile   = boostedUsers.any { it.userId == currentProfile.userId }
    val isVerified = verificationStatuses[currentProfile.userId] == "accepted"

    Log.d("VERIF", "isVerified = $isVerified")
    /* distance + AI check – unchanged */
    var userDistance  by remember { mutableStateOf<Float?>(null) }
    var aiMatchResult by remember { mutableStateOf<AiMatchCheckResult?>(null) }
    // in DatingProfileCard, before Card:
    val allPosts       by postViewModel.posts.collectAsState()
    val myPosts         = allPosts.filter { it.userId == currentProfile.userId }
    val sortedByUpvotes = myPosts.sortedByDescending { it.upvotes }

    LaunchedEffect(currentProfile.userId) {
        userDistance = calculateDistance(currentUserId, currentProfile.userId, geoFire)
        val ref = FirebaseRefs.db
            .getReference("aiMatchCheck/$currentUserId/${currentProfile.userId}")
        val snap = ref.get().await()
        aiMatchResult = snap.getValue(AiMatchCheckResult::class.java)
    }

    userDistance?.let { distance ->
        DatingProfileCard(
            profile        = currentProfile,
            isVerified    = isVerified,
            isBoosted      = isBoostedProfile,
            aiMatchResult  = aiMatchResult,
            sortedByUpvotes  = sortedByUpvotes,      // ← pass it i
            userDistance   = distance,
            navController  = navController,
            postViewModel  = postViewModel,
            currentProfile = currentUserProfile,
            onSwipeRight   = {
                onSwipeRight()
                handleSwipeRight(currentUserId, currentProfile.userId, profileViewModel)
            },
            onSwipeLeft    = {
                onSwipeLeft()
                handleSwipeLeft(currentUserId, currentProfile.userId)
            }
        )
    }
}

// Updated DatingProfileCard
@Composable
fun DatingProfileCard(
    profile: Profile,
    isVerified: Boolean,
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
                        isVerified = isVerified,
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
                if (sortedByUpvotes.size > 5) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = { /* Show more posts */ },
                                colors = ButtonDefaults.buttonColors(Color(0xFFFF6F00))
                            ) {
                                Text(
                                    text = stringResource(R.string.view_more_posts),
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
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
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 8.dp)
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
                .background(Color.Black),
            color = Color.Black
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
    isVerified: Boolean,  // new param
    isBoosted: Boolean,
    userDistance: Float,
    aiMatchResult: AiMatchCheckResult?,
    sortedByUpvotes: List<Post>,
    currentProfile: Profile? = null
) {
    val photoUrls = listOfNotNull(profile.profilepicUrl) + profile.optionalPhotoUrls
    var currentPhotoIndex by remember(photoUrls) { mutableStateOf(0) }
    val context = LocalContext.current
    val datingViewModel: DatingViewModel = viewModel()
    val compliments by datingViewModel.complimentsReceived.collectAsState()
    val compliment = compliments[profile.userId]
    // bring in age calculation
    val age = calculateAge(profile.dob)

    // bring in posts state
    var showPostsOverlay by remember { mutableStateOf(false) }

    // Prepare interest strings
    val interestsTexts = profile.interests.map { "${it.emoji} ${it.name}" }
    // Prepare preference strings
    val preferencesTexts = listOfNotNull(
        profile.lookingFor.takeIf(String::isNotBlank)?.let { "🎯 $it" },
        profile.loveLanguage.takeIf(String::isNotBlank)?.let { "🗣️ $it" },
        profile.jobRole.takeIf(String::isNotBlank)?.let            { "💼 $it" }
    )

    // Prepare lifestyle entries (value, nounList, emoji)
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
    // You can add more lifestyle attributes similarly if needed

    val lifestyleList = listOfNotNull(
        profile.lifestyle?.smoking_habit?.takeIf { it >= 0 }?.let { Triple(it, smokingNouns, "🚬") },
        profile.lifestyle?.drinking_habit?.takeIf { it >= 0 }?.let { Triple(it, drinkingNouns, "🍷") },
        profile.lifestyle?.exercise_frequency?.takeIf { it >= 0 }?.let { Triple(it, exerciseNouns, "🏃") }
    )
    // Sort by highest numeric rating and take top 3
    val lifestyleTexts = lifestyleList
        .sortedByDescending { it.first }
        .take(3)
        .map { (value, nouns, emoji) -> "$emoji ${nouns.getOrNull(value) ?: ""}" }

    // Sort by highest numeric rating and take top 3
    val lifestyleTexts2 = lifestyleList
        .sortedByDescending { it.first }
        .drop(3)
        .take(6)
        .map { (value, nouns, emoji) -> "$emoji ${nouns.getOrNull(value) ?: ""}" }

    // Prefetch images
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
            // Main photo or placeholder
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
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_images),
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }

            // Photo-position indicators
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
            // ─── NEW NAME & AGE OVERLAY ─────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Verified tick
                if (isVerified) {
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = Color(0xFF2196F3),
                        modifier = Modifier
                            .padding(10.dp)
                            .size(24.dp)
                    )
                }
                Text(
                    text = if (age > 0) "${profile.name}, $age" else profile.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Button(
                    onClick = { showPostsOverlay = true },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.posts),
                        color = Color.White,
                        fontSize = 8.sp
                    )
                }
            }

            // Bottom overlay (always three slots)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black)
                        )
                    )
            ) {
                AutoMarqueeRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    val overlayStrings = when (currentPhotoIndex) {
                        0 -> lifestyleTexts                 // top 3 lifestyle nouns
                        1 -> preferencesTexts.take(3)
                        2 -> interestsTexts.take(4)
                        3 -> interestsTexts.drop(4).take(5)
                        4 -> lifestyleTexts2
                        else -> emptyList()
                    }
                    overlayStrings
                        .filter { it.isNotBlank() }
                        .forEach { text ->
                            TagBox(text = text)
                        }
                }
            }

            // Compliment badge
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
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Boost icon
            if (isBoosted) {
                Icon(
                    Icons.Default.FlashOn,
                    contentDescription = null,
                    tint = Color(0xFFFF6F00),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(26.dp)
                )
            }
        }
        // ─── HOOK UP THE OVERLAY DIALOG ─────────────────
        if (showPostsOverlay) {
            PostsOverlay(
                posts = sortedByUpvotes,
                onDismiss = { showPostsOverlay = false }
            )
        }

        // Distance string below the photo
        Text(
            text = if (userDistance.isNaN())
                stringResource(R.string.worldwide)          // 🔶
            else
                stringResource(R.string.max_distance, userDistance.roundToInt()),
            color = Color.White,
            fontSize = 18.sp,
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
            fontSize = 11.sp,
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(8.dp)
    ) {
        /** ─────────── Compatibility ─────────── */
        if (currentUserProfile?.isPremium == true || currentUserProfile?.isPlus == true) {

            /*  auto-run every time it OPENS  */
            LaunchedEffect(profile.userId) {
                runAiMatchCheck(
                    context = context,
                    coroutineScope = coroutineScope,
                    currentUserId = FirebaseAuth.getInstance().uid
                        ?: return@LaunchedEffect,
                    currentUserProfile = currentUserProfile,
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
        CollapsibleSection(
            title = stringResource(R.string.basic_information),
            icon = Icons.Default.Person
        ) {
            BasicInfoSection(profile)
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

        Spacer(Modifier.height(4.dp))

        /* strengths */
        if (strengths.isNotEmpty()) {
            Text(stringResource(R.string.strengths), color = Color.White, fontWeight = FontWeight.SemiBold)
            FlowRow(
                modifier          = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(6.dp),
                maxLines             = Int.MAX_VALUE) {
                strengths.forEach { TagBox(it.removePrefix("✅").trim()) }
            }
            Spacer(Modifier.height(6.dp))
        }

        /* concerns */
        if (concerns.isNotEmpty()) {
            Text(stringResource(R.string.concerns),
                color = Color.White, fontWeight = FontWeight.SemiBold)
            FlowRow(
                modifier          = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(6.dp),
                maxLines             = Int.MAX_VALUE
            ) {
                concerns.forEach {
                    TagBox(it.removePrefix("⚠️").trim())
                }
            }
            Spacer(Modifier.height(6.dp))
        }


        /* notes */
        if (notes.isNotEmpty()) {
            Text(stringResource(R.string.notes), color = Color.White, fontWeight = FontWeight.SemiBold)
            FlowRow(
                modifier          = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(6.dp),
                maxLines             = Int.MAX_VALUE) {
                notes.forEach { TagBox(it.removePrefix("ℹ️").trim()) }
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
            elevation       = 4.dp,
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
    profileViewModel.sendLikeNotification(currentUserId, otherUserId, {}, {})
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

                profileViewModel.sendMatchNotification(currentUserId, otherUserId, {}, {})
                profileViewModel.sendMatchNotification(otherUserId, currentUserId, {}, {})

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
private suspend fun fetchExcludedUsers(me: String): Set<String> {
    val db            = FirebaseRefs.db
    val oneWeekAgo    = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1_000L
    val excludedIds   = mutableSetOf<String>()

    // ① matches — every matched UID is excluded
    val matchSnap = db.getReference("matches/$me").get().await()
    matchSnap.children.forEach { excludedIds += it.key!! }

    // ② likes you gave in the last 7 days
    val likeSnap  = db.getReference("likesGiven/$me").get().await()
    likeSnap.children.forEach { child ->
        val ts = child.getValue(Long::class.java) ?: 0L
        if (ts >= oneWeekAgo) excludedIds += child.key!!
    }

    return excludedIds
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
        if (isZodiacCompatible(zodiacA, zodiacB)) {
            score += 5.0
            insights += MatchInsight("✅", context.getString(R.string.zodiac_compatibility_prefix, zodiacA, zodiacB), true)
        } else insights += MatchInsight("⚠️", context.getString(R.string.zodiac_mismatch_prefix, zodiacA, zodiacB), false)
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

    val finalScore = if (possible == 0.0) 0 else ((score / possible) * 100).roundToInt().coerceIn(0, 100)
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