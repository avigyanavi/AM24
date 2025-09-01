@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.navigation.NavController
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.util.UUID

@Composable
fun SavedPostsScreen(
    navController: NavController,
    postViewModel: PostViewModel,
    modifier: Modifier = Modifier
) {
    // Get current user ID
    val userId = FirebaseAuth.getInstance().currentUser?.uid

    // Collect saved posts and user profiles as State
    val savedPosts by postViewModel.savedPosts.collectAsState(initial = emptyList())
    val userProfiles by postViewModel.userProfiles.collectAsState(initial = emptyMap())
    val savedIds = remember(savedPosts) { savedPosts.map { it.postId }.toSet() }
    val myProfile by postViewModel.myProfile.collectAsState()

    var myMatches by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(userId) {
        if (userId != null) {
            FirebaseRefs.db
                .getReference("matches")
                .child(userId)
                .addListenerForSingleValueEvent(object: ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        myMatches = snapshot.children.mapNotNull { it.key }
                    }
                    override fun onCancelled(error: DatabaseError) { /* … */ }
                })
        }
    }

    // Trigger loading saved posts when userId becomes available
    LaunchedEffect(userId) {
        userId?.let { postViewModel.loadSavedPosts(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.saved_posts_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { innerPadding ->
        if (savedPosts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.saved_posts_empty),
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.Black),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(
                    items = savedPosts,
                    key = { it.postId }
                ) { post ->
                    val profile = userProfiles[post.userId]
                    // ③ figure out if this one is currently saved
                    val isSaved = savedIds.contains(post.postId)
                    FeedItem(
                        post = post,
                        navController = navController,
                        postViewModel = postViewModel,
                        userProfile = profile,
                        matches       = myMatches,                 // ← here
                        userProfiles  = userProfiles,   // ← pass it through
                        isSaved = isSaved,
                        onUpvote = {
                            postViewModel.upvotePost(
                                postId = post.postId,
                                userId = userId.orEmpty(),
                                onSuccess = {},
                                onFailure = {}
                            )
                        },
                        onDownvote = {
                            postViewModel.downvotePost(
                                postId = post.postId,
                                userId = userId.orEmpty(),
                                onSuccess = {},
                                onFailure = {}
                            )
                        },
                        onUserClick = {
                            if (post.userId == userId) {
                                navController.navigate("profile")
                            } else {
                                navController.navigate("dating_screen?initialQuery=${post.userId}")
                            }
                        },
                        onTagClick = { /* no-op */ },
                             onShare       = {
                                   postViewModel.sharePostWithMatches(
                                         postId   = post.postId,
                                         matches  = myMatches,      // ← use real matches
                                         onSuccess= { /* toast…*/ },
                                         onFailure= { /*…*/ }
                                       )
                        },
                        onSave = {
                            postViewModel.savePost(
                                postId = post.postId,
                                userId = userId.orEmpty(),
                                onSuccess = {},
                                onFailure = {}
                            )
                        },
                        onComment = { commentText ->
                            val comment = Comment(
                                commentId = UUID.randomUUID().toString(),
                                userId = userId.orEmpty(),
                                username = myProfile?.username.orEmpty(),
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
                        currentUserId = userId.orEmpty(),
                        currentUserProfile = myProfile,
                        onDelete = { postToDelete ->
                            postViewModel.deletePost(
                                postId = postToDelete.postId,
                                onSuccess = {},
                                onFailure = {}
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}