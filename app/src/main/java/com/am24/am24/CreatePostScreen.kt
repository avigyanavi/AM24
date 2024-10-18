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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import coil.compose.rememberAsyncImagePainter
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
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
                    icon = Icons.Default.Photo,
                    label = "Photo Post",
                    onClick = { navController.navigate("create_post/photo") }
                )
                Spacer(modifier = Modifier.height(16.dp))
                PostTypeButton(
                    icon = Icons.Default.Videocam,
                    label = "Video Post",
                    onClick = { navController.navigate("create_post/video") }
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
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63)),
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
    var fontFamily by remember { mutableStateOf("Default") }
    var fontSize by remember { mutableStateOf(14) }

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

                        val userId = FirebaseAuth.getInstance().currentUser?.uid
                        val username = FirebaseAuth.getInstance().currentUser?.displayName ?: "Anonymous"

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
                            fontFamily = fontFamily,
                            fontSize = fontSize,
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
                        Text("Post", color = Color(0xFF00bf63))
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
                        focusedBorderColor = Color(0xFF00bf63),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF00bf63)
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
                        focusedBorderColor = Color(0xFF00bf63),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF00bf63)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Font Family Selection (Dropdown)
                var expandedFontFamily by remember { mutableStateOf(false) }
                val fontFamilies = listOf("Default", "Serif", "Sans-Serif", "Monospace")

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fontFamily,
                        onValueChange = {},
                        label = { Text("Select Font Family", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { expandedFontFamily = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00bf63),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color(0xFF00bf63)
                        )
                    )
                    DropdownMenu(
                        expanded = expandedFontFamily,
                        onDismissRequest = { expandedFontFamily = false }
                    ) {
                        fontFamilies.forEach { family ->
                            DropdownMenuItem(
                                text = { Text(family, color = Color(0xFF00bf63)) },
                                onClick = {
                                    fontFamily = family
                                    expandedFontFamily = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Font Size Selection (Dropdown)
                var expandedFontSize by remember { mutableStateOf(false) }
                val fontSizes = listOf("12", "14", "16", "18", "20", "24")

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fontSize.toString(),
                        onValueChange = {},
                        label = { Text("Select Font Size", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { expandedFontSize = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00bf63),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color(0xFF00bf63)
                        )
                    )
                    DropdownMenu(
                        expanded = expandedFontSize,
                        onDismissRequest = { expandedFontSize = false }
                    ) {
                        fontSizes.forEach { size ->
                            DropdownMenuItem(
                                text = { Text(size, color = Color(0xFF00bf63)) },
                                onClick = {
                                    fontSize = size.toInt()
                                    expandedFontSize = false
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun PhotoPostComposable(
    navController: NavController,
    postViewModel: PostViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var userTags by remember { mutableStateOf("") }

    // Launcher to pick image from gallery
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                selectedImageUri = it
            }
        }
    )

    // Launcher to request storage permission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, "Storage permission is required to select photos.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Photo Post", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        // Validate input
                        if (selectedImageUri == null) {
                            Toast.makeText(context, "Please select a photo.", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }

                        val userId = FirebaseAuth.getInstance().currentUser?.uid
                        val username = FirebaseAuth.getInstance().currentUser?.displayName ?: "Anonymous"

                        if (userId == null) {
                            Toast.makeText(context, "User not authenticated.", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }

                        // Convert userTags string to list
                        val tagsList = userTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                        postViewModel.createPhotoPost(
                            userId = userId,
                            username = username,
                            imageUri = selectedImageUri!!,
                            userTags = tagsList,
                            onSuccess = {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Photo post created successfully.", Toast.LENGTH_SHORT).show()
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
                        Text("Post", color = Color(0xFF00bf63))
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
                // Display selected image
                selectedImageUri?.let { uri ->
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = "Selected Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(Color.Gray)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Button to select image
                Button(
                    onClick = {
                        // Check storage permission
                        if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            // Request permission
                            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        } else {
                            // Launch image picker
                            imagePickerLauncher.launch("image/*")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                ) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = "Select Photo",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Choose from Gallery", color = Color.White)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tags Input
                OutlinedTextField(
                    value = userTags,
                    onValueChange = { userTags = it },
                    label = { Text("Add Tags (comma separated)", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00bf63),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF00bf63)
                    )
                )
            }
        }
    )
}

// VideoPostComposable.kt

@Composable
fun VideoPostComposable(
    navController: NavController,
    postViewModel: PostViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var userTags by remember { mutableStateOf("") }

    // Launcher to pick video from gallery
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                selectedVideoUri = it
            }
        }
    )

    // Launcher to request storage permission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, "Storage permission is required to select videos.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Video Post", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        // Validate input
                        if (selectedVideoUri == null) {
                            Toast.makeText(context, "Please select a video.", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }

                        val userId = FirebaseAuth.getInstance().currentUser?.uid
                        val username = FirebaseAuth.getInstance().currentUser?.displayName ?: "Anonymous"

                        if (userId == null) {
                            Toast.makeText(context, "User not authenticated.", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }

                        // Convert userTags string to list
                        val tagsList = userTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                        postViewModel.createVideoPost(
                            userId = userId,
                            username = username,
                            videoUri = selectedVideoUri!!,
                            userTags = tagsList,
                            onSuccess = {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Video post created successfully.", Toast.LENGTH_SHORT).show()
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
                        Text("Post", color = Color(0xFF00bf63))
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
                // Display selected video as thumbnail
                selectedVideoUri?.let { uri ->
                    // Generate thumbnail
                    val thumbnailBitmap = remember(uri) {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, uri)
                        val bitmap = retriever.frameAtTime
                        retriever.release()
                        bitmap
                    }

                    if (thumbnailBitmap != null) {
                        Image(
                            bitmap = thumbnailBitmap.asImageBitmap(),
                            contentDescription = "Selected Video",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color.Gray)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Button to select video
                Button(
                    onClick = {
                        // Check storage permission
                        if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            // Request permission
                            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        } else {
                            // Launch video picker
                            videoPickerLauncher.launch("video/*")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Select Video",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Choose from Gallery", color = Color.White)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tags Input
                OutlinedTextField(
                    value = userTags,
                    onValueChange = { userTags = it },
                    label = { Text("Add Tags (comma separated)", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00bf63),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF00bf63)
                    )
                )
            }
        }
    )
}
