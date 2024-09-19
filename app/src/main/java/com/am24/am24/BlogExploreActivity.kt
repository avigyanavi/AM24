package com.am24.am24

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.am24.am24.ui.theme.BlogTheme
import com.google.firebase.database.*

class BlogExploreActivity : ComponentActivity() {

    private lateinit var db: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = FirebaseDatabase.getInstance("https://am-twentyfour-default-rtdb.firebaseio.com/").reference
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        setContent {
            BlogTheme {
                var blogs by remember { mutableStateOf<List<Post>>(emptyList()) }

                fetchBlogsFromDatabase { blogList ->
                    blogs = blogList
                }

                BlogScreen(
                    blogs = blogs,
                    onNavigateToCreateBlog = {
                        startActivity(Intent(this, BlogPostActivity::class.java))
                    },
                    onDownloadDocument = { mediaUrl ->
                        openDocument(mediaUrl)
                    }
                )
            }
        }
    }

    private fun fetchBlogsFromDatabase(onBlogsFetched: (List<Post>) -> Unit) {
        db.child("posts")
            .orderByChild("postType")
            .equalTo(PostType.TEXT.name) // Fetch only blog posts
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val blogList = mutableListOf<Post>()
                    for (postSnapshot in dataSnapshot.children) {
                        val post = postSnapshot.getValue(Post::class.java)
                        post?.let { blogList.add(it) }
                    }
                    onBlogsFetched(blogList)
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    // Handle database error if necessary
                }
            })
    }

    private fun openDocument(mediaUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(mediaUrl)
            type = "application/pdf"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Handle if no application can handle the intent
            Toast.makeText(this, "No application available to view this document", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun BlogScreen(
    blogs: List<Post>,
    onNavigateToCreateBlog: () -> Unit,
    onDownloadDocument: (String) -> Unit // Function to download or open document
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToCreateBlog) {
                Text("+")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (blogs.isEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(5) { // Show 5 skeleton items while loading
                        BlogSkeleton()
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(blogs) { blog ->
                        BlogPostItem(blog = blog, onDownloadDocument = onDownloadDocument)
                    }
                }
            }
        }
    }
}

@Composable
fun BlogPostItem(blog: Post, onDownloadDocument: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            text = blog.title ?: "Untitled",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Posted by: ${blog.username ?: "Anonymous"}",
            fontSize = 16.sp
        )

        Text(
            text = "Location: ${blog.locationTag.joinToString(", ") ?: "Not specified"}",
            fontSize = 16.sp
        )

        // Display tags if available
        if (blog.tags.isNotEmpty()) {
            Text(
                text = "Tags: ${blog.tags.joinToString(", ")}",
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Document download button
        if (!blog.mediaUrl.isNullOrEmpty()) {
            Button(onClick = { onDownloadDocument(blog.mediaUrl!!) }) {
                Text("Download Document")
            }
        }

        Text(
            text = blog.caption ?: "",
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))
        Divider()
    }
}

@Composable
fun BlogSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(20.dp)
                .background(Color.Gray.copy(alpha = 0.3f))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color.Gray.copy(alpha = 0.3f))
        )
        Spacer(modifier = Modifier.height(16.dp))
        Divider()
    }
}
