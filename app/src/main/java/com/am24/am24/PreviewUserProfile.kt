@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import DatingViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalAirport
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.firebase.geofire.GeoFire
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PreviewUserProfileScreen(
    navController    : NavController,
    targetUserId     : String,
    currentUserId    : String,
    geoFire          : GeoFire,
    profileViewModel : ProfileViewModel   = viewModel(),
    datingViewModel  : DatingViewModel,   // pass from Activity
) {
    var profile      by remember { mutableStateOf<Profile?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope        = rememberCoroutineScope()
    var showComplimentDlg by remember { mutableStateOf(false) }
    val showPassAnim       = remember { mutableStateOf(false) }
    val showComplimentAnim = remember { mutableStateOf(false) }
    val showLikeAnim       = remember { mutableStateOf(false) }

    val deckProfiles by datingViewModel.displayingProfiles.collectAsState()
    val isDeckLoading by datingViewModel.isLoading.collectAsState()

    // 🔹 Decide once if we are in "pure preview" mode (no global deck at open time)
    val isPurePreview = remember { deckProfiles.isEmpty() }

    val cardQueue = remember { mutableStateListOf<Profile>() }
    val swipedIds = remember { mutableStateListOf<String>() }

    // 🔹 Track if the user has ever actually seen a card in this preview session
    var hasShownCard by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        profileViewModel.fetchCurrentUserProfile()
    }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            datingViewModel.startInventoryWatcher(currentUserId)
        }
    }

    /* ── one-shot fetch of the target profile ─────────────────────── */
    LaunchedEffect(targetUserId) {
        try {
            val snap = FirebaseRefs.db.getReference("users")
                .child(targetUserId).get().await()
            val fetched = snap.getValue(Profile::class.java)
            if (fetched != null) {
                val withId = if (fetched.userId.isBlank()) {
                    fetched.copy(userId = targetUserId)
                } else {
                    fetched
                }
                profile = withId
                errorMessage = null
            } else {
                errorMessage = "Profile not found"
            }
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    /* ── Build the local queue: [preview profile] + [deckProfiles] minus swiped ─── */
    LaunchedEffect(profile, deckProfiles, swipedIds.size) {
        val swipedSet = swipedIds.toSet()
        val base = cardQueue.filter { it.userId !in swipedSet }
        val merged = mutableListOf<Profile>()
        val seen = mutableSetOf<String>()

        base.forEach { existing ->
            if (existing.userId.isNotBlank() && seen.add(existing.userId)) {
                merged.add(existing)
            }
        }

        profile?.let { loaded ->
            if (loaded.userId.isNotBlank() && loaded.userId !in swipedSet && seen.add(loaded.userId)) {
                merged.add(loaded)
            }
        }

        deckProfiles.forEach { candidate ->
            if (candidate.userId.isNotBlank() && candidate.userId !in swipedSet && seen.add(candidate.userId)) {
                merged.add(candidate)
            }
        }

        cardQueue.clear()
        cardQueue.addAll(merged)
    }

    val currentCard = cardQueue.firstOrNull()

    // 🔹 Whenever we have a real card, mark as "shown" and push it into DatingViewModel.
    LaunchedEffect(currentCard?.userId) {
        if (currentCard != null) {
            hasShownCard = true
            datingViewModel.setCurrentSwipeUserId(currentCard.userId)
        }
        // IMPORTANT: do *not* call setCurrentSwipeUserId(null)
    }

    fun markCardProcessed(userId: String) {
        if (!swipedIds.contains(userId)) {
            swipedIds.add(userId)
        }
        val index = cardQueue.indexOfFirst { it.userId == userId }
        if (index >= 0) {
            cardQueue.removeAt(index)
        }
    }

    // 🔹 Auto-close only if:
    //   - We're in pure preview mode (no deck at open),
    //   - The user has actually seen at least one card,
    //   - We're not loading,
    //   - Queue is now empty.
    LaunchedEffect(cardQueue.size, isDeckLoading, isPurePreview, hasShownCard) {
        if (isPurePreview && hasShownCard && !isDeckLoading && cardQueue.isEmpty()) {
            navController.safePopBackStack()
        }
    }

    // ② collect the match-pop-up state
    val matchPopUpState by profileViewModel.matchPopUpState.collectAsState()
    val complimentsLeft by datingViewModel.complimentsLeft.collectAsState()

    /* ── UI ────────────────────────────────────────────────────────── */
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            currentCard != null -> {
                val activeProfile = currentCard
                val swipeableState = rememberSwipeableState(initialValue = 0)
                val anchors = remember { mapOf(-300f to -1, 0f to 0, 300f to 1) }
                val swipeOffset = swipeableState.offset.value
                val maxDrag = 300f
                val rawAlpha = (abs(swipeOffset) / maxDrag).coerceIn(0f, 1f)
                val showLikeOverlay = swipeOffset > 0f
                val showPassOverlay = swipeOffset < 0f

                LaunchedEffect(activeProfile.userId) {
                    swipeableState.snapTo(0)
                }

                LaunchedEffect(swipeableState.currentValue, activeProfile.userId) {
                    when (swipeableState.currentValue) {
                        -1 -> {
                            showPassAnim.value = true
                            handleSwipeLeft(currentUserId, activeProfile.userId)
                            updateDailySwipeCount(currentUserId)
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("exclude_uid", activeProfile.userId)
                            markCardProcessed(activeProfile.userId)
                        }
                        1 -> {
                            showLikeAnim.value = true
                            handleSwipeRight(currentUserId, activeProfile.userId, profileViewModel)
                            updateDailySwipeCount(currentUserId)
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("exclude_uid", activeProfile.userId)
                            markCardProcessed(activeProfile.userId)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                        .swipeable(
                            state = swipeableState,
                            anchors = anchors,
                            thresholds = { _, _ -> FractionalThreshold(0.3f) },
                            orientation = Orientation.Horizontal
                        )
                ) {
                    MatchedUserProfileScreen(
                        profile          = activeProfile,
                        geoFire          = geoFire,
                        profileViewModel = profileViewModel,
                        navController    = navController,
                        showBackButton   = true,
                        isMatch          = false
                    )

                    if (showPassOverlay) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(24.dp)
                                .alpha(rawAlpha)
                        )
                    }
                    if (showLikeOverlay) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFFF6F00),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(24.dp)
                                .alpha(rawAlpha)
                        )
                    }
                }
            }
            errorMessage != null -> Text(
                text      = errorMessage!!,
                color     = Color.Red,
                modifier  = Modifier.align(Alignment.Center)
            )
            isDeckLoading || (profile == null && cardQueue.isEmpty()) -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color    = Color(0xFFFF6F00)
                )
            }
            else -> {
                // This branch should only show when there *was* a proper deck.
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_card_in_page),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                cardQueue.clear()
                                datingViewModel.refreshFilteredProfiles()
                            }
                        },
                        enabled = !isDeckLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                    ) {
                        Text(text = stringResource(R.string.next_page), color = Color.Black)
                    }
                }
            }
        }

        /* ── ✅ / ❌ / 💌 buttons overlay ─────────────────────────── */
        if (currentCard != null) {
            val activeProfile = currentCard
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                /* ❌ PASS */
                FloatingActionButton(
                    onClick = {
                        showPassAnim.value = true
                        scope.launch {
                            handleSwipeLeft(currentUserId, activeProfile.userId)
                            updateDailySwipeCount(currentUserId)
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("exclude_uid", activeProfile.userId)
                            markCardProcessed(activeProfile.userId)
                        }
                    },
                    shape           = CircleShape,
                    containerColor  = Color.DarkGray
                ) {
                    Icon(Icons.Default.Clear, null, tint = Color.White)
                }

                /* 💌 COMPLIMENT */
                FloatingActionButton(
                    onClick = {
                        showComplimentAnim.value = true
                        if (complimentsLeft > 0) {
                            showComplimentDlg = true
                        } else {
                            navController.navigate("buyCompliments")
                        }
                    },
                    shape          = CircleShape,
                    containerColor = Color(0xFF2196F3)
                ) {
                    Icon(Icons.Default.LocalAirport, null, tint = Color.White)
                }

                /* ✅ LIKE */
                FloatingActionButton(
                    onClick = {
                        showLikeAnim.value = true
                        scope.launch {
                            handleSwipeRight(currentUserId, activeProfile.userId, profileViewModel)
                            updateDailySwipeCount(currentUserId)
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("exclude_uid", activeProfile.userId)
                            markCardProcessed(activeProfile.userId)
                        }
                    },
                    shape          = CircleShape,
                    containerColor = Color(0xFFFF6F00)
                ) {
                    Icon(Icons.Default.Favorite, null, tint = Color.White)
                }

                if (showComplimentDlg) {
                    ComplimentDialog(
                        complimentsLeft = complimentsLeft,
                        onSend = { text ->
                            datingViewModel.sendCompliment(activeProfile.userId, text, profileViewModel)
                            showComplimentDlg = false
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("exclude_uid", activeProfile.userId)
                            markCardProcessed(activeProfile.userId)
                        },
                        onDismiss = { showComplimentDlg = false }
                    )
                }
            }
            if (showPassAnim.value) {
                SwipeFeedbackIcon(
                    flag = showPassAnim,
                    icon = Icons.Default.Clear,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (showComplimentAnim.value) {
                SwipeFeedbackIcon(
                    flag = showComplimentAnim,
                    icon = Icons.Default.LocalAirport,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (showLikeAnim.value) {
                SwipeFeedbackIcon(
                    flag = showLikeAnim,
                    icon = Icons.Default.Favorite,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        matchPopUpState?.let { (_, them) ->
            val yourPic = profileViewModel.currentUserProfile.value?.profilepicUrl.orEmpty()
            MatchPopUp(
                currentUserProfilePic = yourPic,
                otherUserProfilePic   = them.profilepicUrl.orEmpty(),
                onChatClick = {
                    profileViewModel.clearMatchPopUp()
                    navController.navigate("chat/${them.userId}")
                },
                onClose = {
                    profileViewModel.clearMatchPopUp()
                    navController.safePopBackStack()
                }
            )
        }
    }
}

@Composable
private fun SwipeFeedbackIcon(
    flag: MutableState<Boolean>,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = flag.value, modifier = modifier) {
        val offsetY = remember { Animatable(0f) }
        val alpha = remember { Animatable(1f) }

        LaunchedEffect(flag.value) {
            if (flag.value) {
                offsetY.animateTo(
                    targetValue = -80f,
                    animationSpec = tween(durationMillis = 600)
                )
                alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 600)
                )
                offsetY.snapTo(0f)
                alpha.snapTo(1f)
                flag.value = false
            }
        }

        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .offset(y = offsetY.value.dp)
                .alpha(alpha.value)
        )
    }
}

/* decrement remainingSwipes to mirror the legacy swipe flow behaviour */
private suspend fun updateDailySwipeCount(uid: String) {
    if (uid.isBlank()) return
    val ref = FirebaseRefs.db.getReference("users/$uid/swipesInfo/remainingSwipes")
    val current = (ref.get().await().getValue(Int::class.java)
        ?: loadAndResetSwipesDaily(uid)) - 1
    ref.setValue(current.coerceAtLeast(0))
}
