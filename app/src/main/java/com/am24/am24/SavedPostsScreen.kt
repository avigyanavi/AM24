// SavedPostsScreen.kt
package com.am24.am24

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.navigation.NavController
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

@Composable
fun SavedPostsScreen(navController: NavController, modifier: Modifier = Modifier) {
    val savedPosts = remember { mutableStateListOf<Post>() }
    val savedPostsRef = FirebaseDatabase.getInstance().getReference("savedPosts").child(FirebaseAuth.getInstance().currentUser?.uid ?: "")
    val userProfiles = remember { mutableStateMapOf<String, Profile>() }
    val context = LocalContext.current

    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        savedPostsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                savedPosts.clear()
                val userIdsToFetch = mutableSetOf<String>()

                for (postSnapshot in snapshot.children) {
                    val post = postSnapshot.getValue(Post::class.java)
                    if (post != null) {
                        savedPosts.add(post)
                        userIdsToFetch.add(post.userId)
                    }
                }

                // Fetch user profiles
                fetchUserProfiles(userIdsToFetch, userProfiles) {
                    loading = false
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load saved posts: ${error.message}", Toast.LENGTH_SHORT).show()
                loading = false
            }
        })
    }

    if (loading) {
        // Show loading indicator
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF00bf63))
        }
    } else if (savedPosts.isNotEmpty()) {
        // Display saved posts
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(Color.Black)
        ) {
            items(savedPosts) { post ->
                val profile = userProfiles[post.userId]
                FeedItem(
                    post = post,
                    userProfile = profile,
                    onUpvote = { /* Implement upvote */ },
                    onDownvote = { /* Implement downvote */ },
                    onUserClick = {
                        navController.navigate("profile/${post.userId}")
                    },
                    onTagClick = { tag ->
                        // Handle tag click
                    },
                    onShare = {
                        // Handle share
                    },
                    onComment = { commentText ->
                        // Handle comment
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    } else {
        // No saved posts
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No saved posts yet.",
                color = Color.Gray,
                fontSize = 18.sp
            )
        }
    }
}