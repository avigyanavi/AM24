package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPostsScreen(
    userId: String,
    navController: NavController,
    postViewModel: PostViewModel
) {
    LaunchedEffect(userId) { postViewModel.fetchPosts(userId) }
    val allPosts by postViewModel.profilePosts.collectAsState()
    val posts = allPosts.filter { it.userId == userId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Posts", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.safePopBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (posts.isEmpty()) {
                Text("No posts yet", color = Color.White)
            } else {
                LazyColumn {
                    items(posts) { post ->
                        PostItemInProfile(post)
                    }
                }
            }
        }
    }
}