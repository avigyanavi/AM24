package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import android.net.Uri
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPostsScreen(
    userId: String,
    navController: NavController,
    postViewModel: PostViewModel,
    profileViewModel: ProfileViewModel
) {
    var profile by remember { mutableStateOf<Profile?>(null) }
    var profileError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        postViewModel.fetchPosts(userId)
        profileViewModel.fetchUserProfile(
            userId = userId,
            onSuccess = { profile = it },
            onFailure = { profileError = it }
        )
    }
    val allPosts by postViewModel.profilePosts.collectAsState()
    val posts = allPosts.filter { it.userId == userId }
    val totalLikes = posts.sumOf { it.upvotes }
    val displayName = profile?.username?.ifBlank { null } ?: posts.firstOrNull()?.username ?: "User"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User posts", color = Color.White) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = displayName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Likes",
                            tint = Color(0xFFFF6F00)
                        )
                        Text(
                            text = totalLikes.toString(),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
                profileError?.let {
                    Text(
                        text = it,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (posts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No posts yet", color = Color.White)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(posts, key = { it.postId }) { post ->
                        UserPostGridTile(
                            post = post,
                            onClick = {
                                val encodedId = Uri.encode(post.postId)
                                navController.navigate("post/$encodedId")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserPostGridTile(
    post: Post,
    onClick: () -> Unit
) {
    val mediaUrl = post.mediaThumb ?: post.mediaUrl
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E1E1E))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when {
            mediaUrl != null -> {
                AsyncImage(
                    model = mediaUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            post.mediaType == "voice" -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice post",
                    tint = Color.White
                )
            }
            else -> {
                Text(
                    text = post.contentText.orEmpty().ifBlank { "Text post" },
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}