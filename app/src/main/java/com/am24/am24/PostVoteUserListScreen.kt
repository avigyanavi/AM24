package com.am24.am24

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostVoteUserListScreen(
    navController: NavController,
    postId: String,
    voteType: String,
    postViewModel: PostViewModel = viewModel()
) {
    LaunchedEffect(postId) { postViewModel.startPostListener(postId) }

    val post by postViewModel.postFlow.collectAsState()
    val isUpvoteList = voteType.equals("upvotes", ignoreCase = true)
    val titleRes = if (isUpvoteList) {
        R.string.upvotes_list_title
    } else {
        R.string.downvotes_list_title
    }
    val emptyRes = if (isUpvoteList) {
        R.string.upvotes_list_empty
    } else {
        R.string.downvotes_list_empty
    }

    var isLoading by remember { mutableStateOf(true) }
    val summaries = remember { mutableStateListOf<UserSummary>() }

    LaunchedEffect(post, voteType) {
        val currentPost = post
        if (currentPost == null) {
            isLoading = true
            summaries.clear()
            return@LaunchedEffect
        }

        isLoading = true
        val userIds = if (isUpvoteList) {
            currentPost.upvotedUsers.filterValues { it }.keys
        } else {
            currentPost.downvotedUsers.filterValues { it }.keys
        }
        if (userIds.isEmpty()) {
            summaries.clear()
            isLoading = false
            return@LaunchedEffect
        }

        val fetched = UserSummaryCache.getSummaries(userIds)
        val ordered = userIds.mapNotNull { fetched[it] }
            .sortedBy { it.displayName.lowercase() }
        summaries.clear()
        summaries.addAll(ordered)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.safePopBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFF4500))
                }
            }

            summaries.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(emptyRes),
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(summaries, key = { it.userId }) { summary ->
                        VoteUserRow(
                            summary = summary,
                            onClick = {
                                val encoded = Uri.encode(summary.userId)
                                navController.navigate("previewUserProfile/$encoded")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoteUserRow(
    summary: UserSummary,
    onClick: () -> Unit
) {
    val imageUrl = summary.profilepicThumbnailUrl ?: summary.profilepicUrl
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        ListItem(
            leadingContent = {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = summary.displayName,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.local_placeholder),
                        contentDescription = summary.displayName,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            },
            headlineContent = {
                Text(
                    text = summary.displayName.ifBlank { summary.username },
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                if (summary.username.isNotBlank()) {
                    Text(
                        text = "@${summary.username}",
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
    }
}