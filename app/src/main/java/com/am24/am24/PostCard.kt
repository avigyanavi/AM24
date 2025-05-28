// PostCard.kt
package com.am24.am24

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PostCard(
    post: Post,
    onUserClick: () -> Unit,
    postViewModel: PostViewModel = viewModel()
) {
    val savedIds by postViewModel.savedPostIds.collectAsState()
    val currentUserIdFlow = postViewModel.currentUserIdFlow.collectAsState(initial = null)
    val userProfiles by postViewModel.userProfiles.collectAsState()
    val myProfile = postViewModel.myProfile.collectAsState().value
    val currentUserId = currentUserIdFlow.value

    if (currentUserId == null || myProfile == null) {
        Log.e("PostCard", "Skipping render: currentUserId=$currentUserId, myProfile=$myProfile")
        CircularProgressIndicator(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp))
        return
    }

    val isSaved = savedIds.contains(post.postId)
    val matches = remember(userProfiles) {
        userProfiles.values.filter { it.relationship == "match" }.map { it.userId }
    }

    FeedItem(
        post = post,
        isSaved = isSaved,
        currentUserId = currentUserId,
        matches = matches,
        userProfile = myProfile,
        userProfiles = userProfiles,
        postViewModel = postViewModel,
        onUpvote = { postViewModel.upvotePost(post.postId, currentUserId, {}, {}) },
        onDownvote = { postViewModel.downvotePost(post.postId, currentUserId, {}, {}) },
        onSave = {
            if (isSaved) {
                postViewModel.unsavePost(post.postId, {}, {})
            } else {
                postViewModel.savePost(post.postId, currentUserId, {}, {})
            }
        },
        onDelete = {
            if (currentUserId == post.userId) {
                postViewModel.deletePost(post.postId, {}, {})
            }
        },
        onShare = { /* TODO */ },
        onComment = { /* TODO */ },
        onTagClick = { tag -> /* TODO */ },
        onUserClick = onUserClick
    )
}