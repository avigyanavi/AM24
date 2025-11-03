package com.am24.am24

import DatingViewModel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.Log
import androidx.navigation.NavController
import com.am24.am24.Post
import com.am24.am24.ui.CompatibilityMeter
import com.firebase.geofire.GeoFire
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun MatchedUserProfileScreen(
    profile: Profile,
    geoFire: GeoFire,
    postViewModel: PostViewModel,
    profileViewModel: ProfileViewModel,
    navController: NavController,
    isMatch: Boolean = false,
    showBackButton: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val currentUserProfile by profileViewModel.currentUserProfile.collectAsState()
    val allPosts by postViewModel.profilePosts.collectAsState() // Changed to profilePosts
    val isLoading by postViewModel.isLoading.collectAsState()
    var postsLoaded by remember { mutableStateOf(false) }
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
        postViewModel.fetchPosts()
        profileViewModel.fetchCurrentUserProfile()
        println("MatchedUserProfileScreen: Fetch requests dispatched")
    }

    // Update postsLoaded when fetch completes
    LaunchedEffect(isLoading) {
        if (!isLoading && !postsLoaded) {
            postsLoaded = true
            println("MatchedUserProfileScreen: Posts loaded, size=${allPosts.size}")
        }
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

    // Filter posts
    val myPosts = allPosts.filter { it.userId == profile.userId } // Adjust to "userId" if needed
    val sortedByUpvotes = myPosts.sortedByDescending { it.upvotes }
    val featuredPosts = sortedByUpvotes.take(5)
    val remainingPosts = sortedByUpvotes.drop(5)
    val isBoosted = false

    // Main layout
    Box(modifier = modifier.fillMaxSize()) {
        println("Rendering UI: isLoading=$isLoading, allPosts.size=${allPosts.size}, myPosts.size=${myPosts.size}, postsLoaded=$postsLoaded")
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFFFF6F00)
                )
            }
            userDistance != null && currentUserProfile != null && postsLoaded -> {
                Card(
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
                                    IconButton(onClick = { navController.popBackStack() }) {
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
                                sortedByUpvotes = sortedByUpvotes,
                            currentProfile = currentUserProfile
                            )
                        }
                        item {
                            DatingProfileHeader(
                                profile = profile,
                                userDistance = userDistance!!,
                                sortedByUpvotes = sortedByUpvotes,
                            )
                        }
                        item {
                            ProfileCollapsibleSectionsAll(profile, currentUserProfile, aiMatchResult, showLocation)
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
                        if (remainingPosts.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Button(
                                        onClick = { navController.navigate("userPosts/${profile.userId}") },
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
            else -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFFFF6F00)
                )
            }
        }
    }
}