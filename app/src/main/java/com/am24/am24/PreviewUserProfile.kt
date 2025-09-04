@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import DatingViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalAirport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.firebase.geofire.GeoFire
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun PreviewUserProfileScreen(
    navController    : NavController,
    targetUserId     : String,
    currentUserId    : String,
    geoFire          : GeoFire,
    profileViewModel : ProfileViewModel   = viewModel(),
    postViewModel    : PostViewModel      = viewModel(),   // re-use for posts inside the card
) {
    var profile      by remember { mutableStateOf<Profile?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope        = rememberCoroutineScope()
    var showComplimentDlg by remember { mutableStateOf(false) }
    val showPassAnim       = remember { mutableStateOf(false) }
    val showComplimentAnim = remember { mutableStateOf(false) }
    val showLikeAnim       = remember { mutableStateOf(false) }

    val datingViewModel: DatingViewModel = viewModel()

    LaunchedEffect(Unit) {
        profileViewModel.fetchCurrentUserProfile()
    }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            datingViewModel.startInventoryWatcher(currentUserId)
        }
    }

    /* ── one-shot fetch ────────────────────────────────────────────── */
    LaunchedEffect(targetUserId) {
        try {
            val snap = FirebaseRefs.db.getReference("users")
                .child(targetUserId).get().await()
            profile = snap.getValue(Profile::class.java)
            if (profile == null) errorMessage = "Profile not found"
        } catch (e: Exception) {
            errorMessage = e.message
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
            profile != null -> {
                /* reuse the existing long profile view */
                MatchedUserProfileScreen(
                    profile          = profile!!,
                    geoFire          = geoFire,
                    postViewModel    = postViewModel,
                    profileViewModel = profileViewModel,
                    navController    = navController,
                    showBackButton   = true
                )
            }
            errorMessage != null -> Text(
                text      = errorMessage!!,
                color     = Color.Red,
                modifier  = Modifier.align(Alignment.Center)
            )
            else -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color    = Color(0xFFFF6F00)
            )
        }

        /* ── ✅ / ❌ buttons overlay ───────────────────────────────── */
        if (profile != null) {
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
                            handleSwipeLeft(currentUserId, targetUserId)
                            updateDailySwipeCount()          // helper below
                            navController.previousBackStackEntry?.savedStateHandle?.set("exclude_uid", targetUserId)
                            navController.popBackStack()
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
//                        pendingLike = true
                        showLikeAnim.value = true
                        scope.launch {
                            handleSwipeRight(currentUserId, targetUserId, profileViewModel)
                            updateDailySwipeCount()
                            navController.previousBackStackEntry?.savedStateHandle?.set("exclude_uid", targetUserId)
                            navController.popBackStack()
//                            navController.popBackStack("home", false)
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
                            datingViewModel.sendCompliment(targetUserId, text, profileViewModel)
                            showComplimentDlg = false
                            navController.previousBackStackEntry?.savedStateHandle?.set("exclude_uid", targetUserId)
                            navController.popBackStack()
                        },
                        onDismiss = { showComplimentDlg = false }
                    )
                }
            }
            if (showPassAnim.value) {
                SwipeFeedbackIcon(flag = showPassAnim, icon = Icons.Default.Clear, modifier = Modifier.align(Alignment.Center))
            }
            if (showComplimentAnim.value) {
                SwipeFeedbackIcon(flag = showComplimentAnim, icon = Icons.Default.LocalAirport, modifier = Modifier.align(Alignment.Center))
            }
            if (showLikeAnim.value) {
                SwipeFeedbackIcon(flag = showLikeAnim, icon = Icons.Default.Favorite, modifier = Modifier.align(Alignment.Center))
            }
            // ⬇️ Place these OUTSIDE the Row, but still inside the Box:
            matchPopUpState?.let { (you, them) ->
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
                        navController.popBackStack()
                    }
                )
            }
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

/* decrement remainingSwipes just like DatingScreen does */
private suspend fun updateDailySwipeCount() {
    val uid = FirebaseAuth.getInstance().uid ?: return
    val ref = FirebaseRefs.db.getReference("users/$uid/swipesInfo/remainingSwipes")
    val current = (ref.get().await().getValue(Int::class.java)
        ?: loadAndResetSwipesDaily(uid)) - 1
    ref.setValue(current.coerceAtLeast(0))
}
