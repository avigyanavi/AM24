package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    navController: NavController,
    postId: String,
    commentId: String? = null,
    postViewModel: PostViewModel = viewModel()
) {
    /* 1️⃣ realtime listener */
    LaunchedEffect(postId) { postViewModel.startPostListener(postId) }

    /* 2️⃣ state from VM */
    val post          by postViewModel.postFlow.collectAsState()
    val currentUserId by postViewModel.currentUserIdFlow.collectAsState(initial = null)
    val userProfiles  by postViewModel.userProfiles.collectAsState()
    val savedIds      by postViewModel.savedPostIds.collectAsState()
    val myProfile     by postViewModel.myProfile.collectAsState()

    val listState = rememberLazyListState()
    val scope      = rememberCoroutineScope()
    var commentText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.post_action)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->

        /* ── only wait for the post itself ─────────────────────────── */
        if (post == null) {                                           // ✱
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        /* ── scroll to a specific comment when both arrive ─────────── */
        LaunchedEffect(post, commentId) {
            if (post != null && commentId != null) {
                val idx = post!!.comments.values
                    .toList()
                    .indexOfFirst { it.commentId == commentId }
                if (idx >= 0) scope.launch { listState.scrollToItem(idx + 1) }
            }
        }

        /* ── main content ──────────────────────────────────────────── */
        val p         = post!!            // safe now
        val profile   = userProfiles[p.userId]
        val isSaved   = savedIds.contains(p.postId)
        val matches   = userProfiles.values
            .filter { it.relationship == "match" }.map { it.userId }
        val comments  = p.comments.values.sortedByDescending { it.getCommentTimestamp() }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            /* Post card */
            item {
                FeedItem(
                    post          = p,
                    navController = navController,
                    isSaved       = isSaved,
                    postViewModel = postViewModel,
                    userProfile   = profile,
                    matches       = matches,
                    userProfiles  = userProfiles,
                    onUpvote      = { currentUserId?.let { uid ->
                        postViewModel.upvotePost(p.postId, uid, {}, {}) }
                    },
                    onDownvote    = { currentUserId?.let { uid ->
                        postViewModel.downvotePost(p.postId, uid, {}, {}) }
                    },
                    onUserClick   = { navController.navigate("previewUserProfile/${p.userId}") },
                    onTagClick    = { _ -> },
                    onShare       = {},
                    onSave        = {
                        currentUserId?.let { uid ->
                            if (isSaved) postViewModel.unsavePost(p.postId, {}, {})
                            else         postViewModel.savePost(p.postId, uid, {}, {})
                        }
                    },
                    onComment     = {},
                    currentUserId = currentUserId ?: "",               // ✱ tolerate null
                    currentUserProfile = myProfile,
                    onDelete      = { delPost ->
                        postViewModel.deletePost(delPost.postId, {}, {})
                        navController.popBackStack()
                    }
                )
            }

            /* Comments */
            items(comments) { comment ->
                CommentCard(
                    comment = comment,
                    onUpvoteComment = { cid ->
                        currentUserId?.let { uid ->
                            postViewModel.upvoteComment(p.postId, cid, uid, {}, {})
                        }
                    },
                    onDownvoteComment = { cid ->
                        currentUserId?.let { uid ->
                            postViewModel.downvoteComment(p.postId, cid, uid, {}, {})
                        }
                    },
                     onCommentClick = {
                         navController.navigate("previewUserProfile/${comment.userId}")
                     }
                )
            }

            /* Add-comment box */
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text("Add a comment...", color = Color.Gray) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Color(0xFFFF6F00),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor          = Color(0xFFFF6F00)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = myProfile != null && currentUserId != null,  // ✱
                        onClick = {
                            if (commentText.isNotBlank()
                                && currentUserId != null
                                && myProfile != null) {

                                val comment = Comment(
                                    commentId   = UUID.randomUUID().toString(),
                                    userId      = currentUserId!!,
                                    username    = myProfile!!.username,           // ✱ safe
                                    commentText = commentText,
                                    timestamp   = ServerValue.TIMESTAMP
                                )
                                postViewModel.addComment(p.postId, comment, {}, {})
                                commentText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6F00)
                        )
                    ) { Text("Submit", color = Color.White) }
                }
            }
        }
    }
}
