// CreatePostScreen.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import coil.compose.rememberAsyncImagePainter
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.IOException

@Composable
fun CreatePostScreen(
    navController: NavController,
    postViewModel: PostViewModel
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create New Post", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Choose Post Type",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(24.dp))
                PostTypeButton(
                    icon = Icons.Default.TextFields,
                    label = "Text Post",
                    onClick = { navController.navigate("create_post/text") }
                )
                Spacer(modifier = Modifier.height(16.dp))
                PostTypeButton(
                    icon = Icons.Default.Mic,
                    label = "Voice Post",
                    onClick = { navController.navigate("create_post/voice") }
                )
            }
        }
    )
}

@Composable
fun PostTypeButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500)),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}

// Add the fetchUsernameById function
suspend fun fetchUsernameById(userId: String): String? {
    return try {
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
        val snapshot = userRef.child("username").get().await()
        snapshot.getValue(String::class.java)
    } catch (e: Exception) {
        Log.e("CreatePostScreen", "Failed to fetch username: ${e.message}")
        null
    }
}

// TextPostComposable.kt

@Composable
fun TextPostComposable(
    navController: NavController,
    postViewModel: PostViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var contentText by remember { mutableStateOf("") }
    var userTags by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    val userId = FirebaseAuth.getInstance().currentUser?.uid
    // Fetch username from the database
    LaunchedEffect(userId) {
        username = userId?.let { fetchUsernameById(it) } ?: "Anonymous"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Text Post", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        // Validate input
                        if (contentText.isBlank()) {
                            Toast.makeText(context, "Post content cannot be empty.", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }

                        if (userId == null) {
                            Toast.makeText(context, "User not authenticated.", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }

                        // Convert userTags string to list
                        val tagsList = userTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                        postViewModel.createTextPost(
                            userId = userId,
                            username = username,
                            contentText = contentText,
                            userTags = tagsList,
                            fontFamily = "Default", // Default font family since it's removed
                            fontSize = 14, // Default font size since it's removed
                            onSuccess = {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Text post created successfully.", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                }
                            },
                            onFailure = { error ->
                                coroutineScope.launch {
                                    Toast.makeText(context, "Failed to create post: $error", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }) {
                        Text("Post", color = Color(0xFFFF4500)) // Dark orange for the "Post" button
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top
            ) {
                OutlinedTextField(
                    value = contentText,
                    onValueChange = { contentText = it },
                    label = { Text("What's on your mind?", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFA500), // Light orange
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFFFFA500) // Light orange
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = userTags,
                    onValueChange = { userTags = it },
                    label = { Text("Add Tags (comma separated)", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF4500),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFFFF4500)
                    )
                )
            }
        }
    )
}

