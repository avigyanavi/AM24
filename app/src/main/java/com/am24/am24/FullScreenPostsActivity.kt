package com.am24.am24

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

class FullScreenPostsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val postType = intent.getStringExtra("postType")
        val mediaUrl = intent.getStringExtra("mediaUrl")
        val postId = intent.getStringExtra("postId")

        setContent {
            FullScreenMedia(postType = postType, mediaUrl = mediaUrl, postId = postId ?: "")
        }
    }
}

@Composable
fun FullScreenMedia(postType: String?, mediaUrl: String?, postId: String) {
    var rating by remember { mutableStateOf(5f) } // Default rating is 5
    val snackbarHostState = remember { SnackbarHostState() } // For the Snackbar
    var showSnackbar by remember { mutableStateOf(false) } // To trigger the Snackbar

    // Trigger the Snackbar when `showSnackbar` becomes `true`
    LaunchedEffect(showSnackbar) {
        if (showSnackbar) {
            snackbarHostState.showSnackbar(
                message = "Rating submitted: ${rating.toInt()} for post: $postId"
            )
            showSnackbar = false // Reset the state after showing the Snackbar
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            when (postType) {
                PostType.PHOTO.name -> {
                    mediaUrl?.let { url ->
                        Image(
                            painter = rememberAsyncImagePainter(url),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.85f)
                        )
                    }
                }
                PostType.VIDEO.name -> {
                    mediaUrl?.let { url ->
                        VideoPlayer(videoUrl = url)
                    }
                }
            }

            // Slider for rating
            Slider(
                value = rating,
                onValueChange = { rating = it },
                valueRange = 1f..10f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            Text(
                text = "Rating: ${rating.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // Submit rating button (Trigger Snackbar by setting `showSnackbar` to `true`)
            Button(
                onClick = {
                    showSnackbar = true // Trigger the snackbar
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 16.dp)
            ) {
                Text("Submit Rating")
            }
        }
    }
}



