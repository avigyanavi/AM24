package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun PostDetailScreen(
    navController: NavController,
    postId: String,
    commentId: String? = null,
    postViewModel: PostViewModel = viewModel()
) {
    var post by remember { mutableStateOf<Post?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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
        if (isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            post?.let { p ->
                val comments = p.comments.values.toList()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = p.username, color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            p.contentText?.let { Text(text = it, color = Color.White) }
                        }
                    }
                    items(comments) { comment ->
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = comment.username, color = Color.Yellow, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(text = comment.commentText, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}