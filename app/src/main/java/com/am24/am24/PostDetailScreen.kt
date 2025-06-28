package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun PostDetailScreen(
    navController: NavController,
    postId: String,
    commentId: String? = null,
    postViewModel: PostViewModel = viewModel()
) {
    var post by remember { mutableStateOf<Post?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var commentText by remember { mutableStateOf(TextFieldValue("")) }

    val listState: LazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val currentUserId by postViewModel.currentUserIdFlow.collectAsState(initial = null)
    val userProfiles by postViewModel.userProfiles.collectAsState()
    val savedIds by postViewModel.savedPostIds.collectAsState()
    val myProfile by postViewModel.myProfile.collectAsState()

    LaunchedEffect(postId) {
        postViewModel.fetchPostById(
            postId = postId,
            onSuccess = { fetched ->
                post = fetched
                isLoading = false
            },
            onFailure = { isLoading = false }
        )
    }

    // Scroll to the requested comment when data is available
    LaunchedEffect(post, commentId) {
        if (post != null && commentId != null) {
            val comments = post!!.comments.values.toList()
            val index = comments.indexOfFirst { it.commentId == commentId }
            if (index >= 0) {
                scope.launch { listState.scrollToItem(index + 1) } // +1 for post item
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isLoading || currentUserId == null || myProfile == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            post?.let { p ->
                val profile = userProfiles[p.userId]
                val isSaved = savedIds.contains(p.postId)
                val matches =
                    userProfiles.values.filter { it.relationship == "match" }.map { it.userId }

                val comments = p.comments.values.sortedByDescending { it.getCommentTimestamp() }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item {
                        FeedItem(
                            post = p,
                            isSaved = isSaved,
                            postViewModel = postViewModel,
                            userProfile = profile,
                            matches = matches,
                            userProfiles = userProfiles,
                            onUpvote = {
                                currentUserId?.let { uid ->
                                    postViewModel.upvotePost(p.postId, uid, {}, {})
                                }
                            },
                            onDownvote = {
                                currentUserId?.let { uid ->
                                    postViewModel.downvotePost(p.postId, uid, {}, {})
                                }
                            },
                            onUserClick = {
                                navController.navigate("previewUserProfile/${p.userId}")
                            },
                            onTagClick = { _ -> },
                            onShare = {},
                            onSave = {
                                currentUserId?.let { uid ->
                                    if (isSaved) {
                                        postViewModel.unsavePost(p.postId, {}, {})
                                    } else {
                                        postViewModel.savePost(p.postId, uid, {}, {})
                                    }
                                }
                            },
                            onComment = {},
                            currentUserId = currentUserId!!,
                            onDelete = { delPost ->
                                postViewModel.deletePost(delPost.postId, {}, {})
                                navController.popBackStack()
                            }
                        )
                    }
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
                            }
                        )
                    }

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
                                    focusedBorderColor = Color(0xFFFF6F00),
                                    unfocusedBorderColor = Color.Gray,
                                    cursorColor = Color(0xFFFF6F00)
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (commentText.text.isNotBlank()) {
                                        currentUserId?.let { uid ->
                                            val comment = Comment(
                                                commentId = UUID.randomUUID().toString(),
                                                userId = uid,
                                                username = myProfile?.username ?: "",
                                                commentText = commentText.text,
                                                timestamp = ServerValue.TIMESTAMP
                                            )
                                            postViewModel.addComment(p.postId, comment, {}, {})
                                            commentText = TextFieldValue("")
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(
                                        0xFFFF6F00
                                    )
                                )
                            ) {
                                Text("Submit", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
