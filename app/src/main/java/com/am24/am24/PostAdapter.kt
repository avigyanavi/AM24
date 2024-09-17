package com.am24.am24

import android.content.Context
import android.text.format.DateFormat
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.RecyclerView
import coil.compose.rememberImagePainter
import com.am24.am24.PostAdapter.PostViewHolder
import java.util.Date
import com.am24.am24.formatTimestamp

class PostAdapter(private val posts: List<Post>, private val onRatingChange: (Post, Double) -> Unit) :
    RecyclerView.Adapter<PostViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val composeView = ComposeView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return PostViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        holder.bind(post, onRatingChange)
    }

    override fun getItemCount(): Int = posts.size

    inner class PostViewHolder(private val composeView: ComposeView) : RecyclerView.ViewHolder(composeView) {

        fun bind(post: Post, onRatingChange: (Post, Double) -> Unit) {
            composeView.setContent {
                PostItem(post, onRatingChange)
            }
        }
    }
}

@Composable
fun PostItem(post: Post, onRatingChange: (Post, Double) -> Unit) {
    var showRatingDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Username: ${post.username}",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Image(
            painter = rememberImagePainter(post.mediaUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clickable {
                    showRatingDialog = true // Trigger rating dialog
                }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Location: ${post.locationTag}",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Posted at: ${formatTimestamp(post.timeOfPost)}",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Avg Rating: ${post.avgRating}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        if (showRatingDialog) {
            RatingDialog(
                post = post,
                onRatingSubmit = { rating ->
                    showRatingDialog = false
                    onRatingChange(post, rating)
                },
                onDismiss = { showRatingDialog = false }
            )
        }
    }
}

@Composable
fun RatingDialog(post: Post, onRatingSubmit: (Double) -> Unit, onDismiss: () -> Unit) {
    var rating by remember { mutableStateOf(5f) } // Default rating to 5

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Rate Post")
        },
        text = {
            Column {
                Text(
                    text = "Rate this post from 1 to 10",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Slider(
                    value = rating,
                    onValueChange = { rating = it },
                    valueRange = 1f..10f,
                    steps = 9, // For 1 to 10 range
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                Text(
                    text = "Rating: ${rating.toInt()}",
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onRatingSubmit(rating.toDouble())
                }
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

