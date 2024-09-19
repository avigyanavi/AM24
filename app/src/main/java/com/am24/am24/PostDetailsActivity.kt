package com.am24.am24

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class PostDetailsActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance("https://am-twentyfour-default-rtdb.firebaseio.com/").reference

        val postType = intent.getStringExtra("postType")
        val mediaUrl = intent.getStringExtra("mediaUrl")
        val blogTitle = intent.getStringExtra("title")
        val blogContent = intent.getStringExtra("content")

        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        setContent {
            PostDetailsScreen(
                mediaUrl = mediaUrl,
                postType = postType,
                blogTitle = blogTitle,
                blogContent = blogContent,
                onSubmitDetails = { location, tags ->
                    submitPost(postType, mediaUrl, blogTitle, blogContent, location, tags)
                }
            )
        }
    }

    private fun submitPost(
        postType: String?,
        mediaUrl: String?,
        blogTitle: String?,
        blogContent: String?,
        locations: List<String>,
        tags: List<String>
    ) {
        val userId = auth.currentUser?.uid ?: return
        val postId = db.child("posts").push().key ?: return

        val post = Post(
            postType = postType ?: PostType.TEXT.name,
            mediaUrl = mediaUrl,
            title = blogTitle,
            caption = blogContent,
            locationTag = locations,
            tags = tags,
            userId = userId,
            username = auth.currentUser?.displayName ?: "Unknown",
            timeOfPost = System.currentTimeMillis()
        )

        db.child("posts").child(postId).setValue(post)
            .addOnSuccessListener {
                updateUserTagsAndLocations(userId, tags, locations)
                Toast.makeText(this, "Post submitted successfully!", Toast.LENGTH_SHORT).show()

                // Redirect based on post type
                if (postType == PostType.TEXT.name) {
                    startActivity(Intent(this, BlogExploreActivity::class.java))
                } else {
                    startActivity(Intent(this, ExploreActivity::class.java))
                }
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to submit post", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUserTagsAndLocations(
        userId: String,
        newTags: List<String>,
        newLocations: List<String>
    ) {
        val userRef = db.child("users").child(userId)
        userRef.get().addOnSuccessListener { snapshot ->
            val userProfile = snapshot.getValue(Profile::class.java)
            val updatedTags = (userProfile?.tags?.toMutableList() ?: mutableListOf()).apply { addAll(newTags) }
            val updatedLocations = (userProfile?.locationTags?.toMutableList() ?: mutableListOf()).apply { addAll(newLocations) }

            userRef.child("tags").setValue(updatedTags.distinct()) // Remove duplicates
            userRef.child("locationTags").setValue(updatedLocations.distinct()) // Remove duplicates
        }
    }
}

@Composable
fun PostDetailsScreen(
    mediaUrl: String?,
    postType: String?,
    blogTitle: String?,
    blogContent: String?,
    onSubmitDetails: (List<String>, List<String>) -> Unit
) {
    var locationInput by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    val locations = remember { mutableStateListOf<String>() }
    val tags = remember { mutableStateListOf<String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Post Details", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // Display blog details (if postType is TEXT)
        if (postType == PostType.TEXT.name) {
            Text(text = blogTitle ?: "No Title", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = blogContent ?: "No Content", fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            mediaUrl?.let {
                Text(text = "Document: $mediaUrl", color = Color.Blue)
                // Add clickable download button for the document if needed
            }
        }

        // Media display for photos or videos
        mediaUrl?.let {
            when (postType) {
                PostType.PHOTO.name -> {
                    Image(
                        painter = rememberAsyncImagePainter(it),
                        contentDescription = null,
                        modifier = Modifier.size(200.dp)
                    )
                }
                PostType.VIDEO.name -> {
                    VideoPlayer(videoUrl = it)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Location input and display
        Text(text = "Locations (add by clicking the button):")
        Column {
            locations.forEach { location ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text(text = location)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Remove Location",
                        tint = Color.Red,
                        modifier = Modifier.clickable {
                            locations.remove(location) // Remove location on click
                        }
                    )
                }
            }

            // Text field for new location input
            OutlinedTextField(
                value = locationInput,
                onValueChange = { locationInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                textStyle = LocalTextStyle.current.copy(color = androidx.compose.ui.graphics.Color.Black),
                label = { Text("Add Location") }
            )

            Button(onClick = {
                if (locationInput.isNotEmpty()) {
                    locations.add(locationInput) // Add the location to the list
                    locationInput = "" // Clear the input field
                }
            }) {
                Text("Add Location")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tags input and display
        Text(text = "Tags (add by clicking the button):")
        Column {
            tags.forEach { tag ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text(text = tag)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Remove Tag",
                        tint = Color.Red,
                        modifier = Modifier.clickable {
                            tags.remove(tag) // Remove tag on click
                        }
                    )
                }
            }

            // Text field for new tag input
            OutlinedTextField(
                value = tagInput,
                onValueChange = { tagInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                textStyle = LocalTextStyle.current.copy(color = androidx.compose.ui.graphics.Color.Black),
                label = { Text("Add Tag") }
            )

            Button(onClick = {
                if (tagInput.isNotEmpty()) {
                    tags.add(tagInput) // Add the tag to the list
                    tagInput = "" // Clear the input field
                }
            }) {
                Text("Add Tag")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Submit button
        Button(onClick = { onSubmitDetails(locations, tags) }) {
            Text(text = "Submit Post")
        }
    }
}
