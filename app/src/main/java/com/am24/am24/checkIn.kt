// CheckInFeedScreen.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth

/**
 * A feed that shows every post whose check-in → placeId matches [placeId].
 */
@Composable
fun CheckInFeedScreen(
    placeId: String,
    navController: NavController,
    postViewModel: PostViewModel = viewModel()
) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    LaunchedEffect(userId) {
        if (userId != null) {
            postViewModel.setCurrentUserId(userId)
        }
        Log.d("CheckInFeedScreen", "▶️ opened for placeId=$placeId")
    }

    val postsAtLocation by postViewModel.checkInPosts(placeId).collectAsState(initial = emptyList())
    val currentUserId by postViewModel.currentUserIdFlow.collectAsState(initial = null)
    val myProfile by postViewModel.myProfile.collectAsState()

    LaunchedEffect(postsAtLocation) {
        Log.d("CheckInFeedScreen", "📊 list update – placeId=$placeId, size=${postsAtLocation.size}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check-in Feed") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                currentUserId == null || myProfile == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                postsAtLocation.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No posts at this location")
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(postsAtLocation) { post ->
                            Log.d("CheckInFeedScreen", "📝 showing post=${post.postId} (checkIn.placeId=${post.checkIn?.placeId})")
                            PostCard(
                                post = post,
                                onUserClick = {
                                    navController.navigate("previewUserProfile/${post.userId}")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}