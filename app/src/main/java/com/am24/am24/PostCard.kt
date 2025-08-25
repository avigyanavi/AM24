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
import androidx.navigation.NavController
import com.google.firebase.database.ServerValue
import java.util.UUID

@Composable
fun PostCard(
    post: Post,
    navController: NavController,
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
        CircularProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        return
    }

    val isSaved = savedIds.contains(post.postId)
    val matches = remember(userProfiles) {
        userProfiles.values.filter { it.relationship == "match" }.map { it.userId }
    }

    // ── Author profile resolution ─────────────────────────────────────────────
    val authorFromCache = userProfiles[post.userId]

    // If the author's profile isn't cached, try to fetch it (if your VM exposes such a call).
    LaunchedEffect(post.userId) {
        if (authorFromCache == null) {
            // Implement this in your VM if you haven't already:
            // postViewModel.loadUserProfile(post.userId)
        }
    }

    // Avoid showing *my* username ("AM") as a fallback: use a display-safe profile.
    // We keep myProfile's fields (to satisfy FeedItem), but override the username for UI until author loads.
    val displayAuthor = authorFromCache ?: myProfile.copy(username = post.username)

    // Optional: show a tiny inline spinner instead of rendering with a wrong name.
    val authorReady = authorFromCache != null

    if (!authorReady && post.username.isNullOrBlank()) {
        // If even post.username is blank, show a small loader while profile loads.
        CircularProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        )
        return
    }

    FeedItem(
        post = post,
        navController = navController,
        isSaved = isSaved,
        currentUserId = currentUserId,
        currentUserProfile = myProfile,
        matches = matches,
        userProfile = displayAuthor,              // ✅ shows author's name or post.username, not "AM"
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
        onShare = {
            postViewModel.sharePostWithMatches(
                postId = post.postId,
                matches = matches,
                onSuccess = {},
                onFailure = {}
            )
        },
        onComment = { commentText ->
            val comment = Comment(
                commentId = UUID.randomUUID().toString(),
                userId = currentUserId,
                username = myProfile.username, // your (current user) username for the comment
                commentText = commentText,
                timestamp = ServerValue.TIMESTAMP
            )
            postViewModel.addComment(
                postId = post.postId,
                comment = comment,
                onSuccess = {},
                onFailure = {}
            )
        },
        onTagClick = { tag -> postViewModel.setSearchQuery(tag) },
        onUserClick = onUserClick
    )
}
