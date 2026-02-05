package com.am24.am24

import DatingViewModel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.am24.am24.ui.CompatibilityMeter
import com.firebase.geofire.GeoFire
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun MatchedUserProfileScreen(
    profile: Profile,
    geoFire: GeoFire,
    profileViewModel: ProfileViewModel,
    navController: NavController,
    isMatch: Boolean = false,
    showBackButton: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val currentUserProfile by profileViewModel.currentUserProfile.collectAsState()
    val context = LocalContext.current // ✅ declare at the top of the Composable

    var userDistance by remember { mutableStateOf<Float?>(null) }
    var aiMatchResult by remember { mutableStateOf<AiMatchCheckResult?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var isVerified by remember { mutableStateOf(false) }
    val datingViewModel: DatingViewModel = viewModel()
    val showLocation = if (isMatch) profile.allowLocationForMatches else profile.allowLocationPublic

    // Fetch data on initial load
    LaunchedEffect(Unit) {
        println("MatchedUserProfileScreen: Starting initial fetch")
        profileViewModel.fetchCurrentUserProfile()
        println("MatchedUserProfileScreen: Fetch requests dispatched")
    }

    // Fetch distance and AI match result
    LaunchedEffect(profile.userId, currentUserProfile) {
        println("LaunchedEffect for profile: ${profile.userId}, currentUserProfile: $currentUserProfile")
        try {
            val distance = datingViewModel.distanceBetween(currentUserId, profile.userId, geoFire)
            userDistance = distance
            println("Distance calculated: $distance km")
        } catch (e: Exception) {
            println("Error calculating distance: ${e.message}")
            userDistance = null
        }

        val ref = FirebaseRefs.db.getReference("aiMatchCheck/$currentUserId/${profile.userId}")
        try {
            val snap = ref.get().await()
            val existing = snap.getValue(AiMatchCheckResult::class.java)
            if (existing != null) {
                aiMatchResult = existing
                println("AI match result fetched: $existing")
            } else if (currentUserProfile != null) {
                println("Running AI match check since no existing result found")
                runAiMatchCheck(
                    context = context, // ← ADD THIS
                    coroutineScope = coroutineScope,
                    currentUserId = currentUserId,
                    currentUserProfile = currentUserProfile!!,
                    otherProfile = profile
                ) { newResult ->
                    aiMatchResult = newResult
                    println("AI match result computed: $newResult")
                }
            }
        } catch (e: Exception) {
            println("Error fetching AI match result: ${e.message}")
        }

        // verification
        val statusSnap  = FirebaseRefs.db
            .getReference("verifications")
            .child(profile.userId)
            .child("status")
            .get()
            .await()

        val status = statusSnap.getValue(String::class.java)
        if (status == "accepted")
        {
            isVerified = true
        } else
        {
            isVerified = false
        }
    }

    // Main layout
    Box(modifier = modifier.fillMaxSize()) {
        when {
            userDistance != null && currentUserProfile != null -> {                Card(
                    modifier = Modifier
                        .fillMaxSize(),
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // keep the compatibility meter centered to match the legacy swipe UI
                                if (showBackButton) {
                                    IconButton(onClick = { navController.safePopBackStack() }) {
                                        Icon(
                                            Icons.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color.White
                                        )
                                    }
                                }
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
                                if (showBackButton) {
                                    Spacer(modifier = Modifier.width(48.dp))
                                }
                            }
                        }
                        item {
                            PhotoWithTwoOverlays(
                                profile = profile,
                                userDistance = userDistance!!,
                                aiMatchResult = aiMatchResult,
                                currentProfile = currentUserProfile
                            )
                        }
                        item {
                            DatingProfileHeader(
                                profile = profile,
                                userDistance = userDistance!!
                            )
                        }
                        item {
                            Button(
                                onClick = { navController.navigate("userPosts/${profile.userId}") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFFFF6F00),
                                    contentColor = Color.White
                                )
                            ) {
                                Text(text = "View posts", fontSize = 14.sp)
                            }
                        }
                        item {
                            ProfileCollapsibleSectionsAll(profile, currentUserProfile, aiMatchResult, showLocation)
                        }
                    }
                }
            }
            else -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFFFF6F00)
                )
            }
        }
    }
}