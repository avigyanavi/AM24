@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                    navController    = navController
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
                horizontalArrangement = Arrangement.spacedBy(64.dp)
            ) {
                /* ❌ PASS */
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            handleSwipeLeft(currentUserId, targetUserId)
                            updateDailySwipeCount()          // helper below
                            navController.popBackStack("home", false)
                        }
                    },
                    shape           = CircleShape,
                    containerColor  = Color.DarkGray
                ) {
                    Icon(Icons.Default.Clear, null, tint = Color.White)
                }

                /* ✅ LIKE */
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            handleSwipeRight(currentUserId, targetUserId, profileViewModel)
                            updateDailySwipeCount()
                            navController.popBackStack("home", false)
                        }
                    },
                    shape          = CircleShape,
                    containerColor = Color(0xFFFF6F00)
                ) {
                    Icon(Icons.Default.Favorite, null, tint = Color.White)
                }
            }
        }
    }
}

/* decrement remainingSwipes just like DatingScreen does */
private suspend fun updateDailySwipeCount() {
    val uid = FirebaseAuth.getInstance().uid ?: return
    val ref = FirebaseRefs.db.getReference("users/$uid/swipesInfo/remainingSwipes")
    val current = (ref.get().await().getValue(Int::class.java) ?: 15) - 1
    ref.setValue(current.coerceAtLeast(0))
}
