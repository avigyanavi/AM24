package com.am24.am24

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.am24.am24.Post
import com.am24.am24.Profile
import com.am24.am24.ui.theme.AppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import androidx.navigation.compose.composable
import com.am24.am24.ui.theme.White

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            var currentTab by rememberSaveable { mutableStateOf(1) } // Default to Feed tab

            UnifiedScaffold(
                currentTab = currentTab,
                onTabChange = { currentTab = it },
                navController = navController,
                titleProvider = { tabIndex ->
                    when (tabIndex) {
                        0 -> "DMs"
                        1 -> "Feed"
                        2 -> "Profile"
                        3 -> "Dating"
                        4 -> "Settings"
                        else -> "CupidxKolkata"
                    }
                }
            )
        }
    }
}




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, currentTab: Int, onTabChange: (Int) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var postContent by remember { mutableStateOf(TextFieldValue("")) }
    var filterOption by remember { mutableStateOf("recent") }
    var showFilterMenu by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isPostSectionVisible by remember { mutableStateOf(false) } // Set to false by default
    var searchQuery by remember { mutableStateOf("") }
    val addedUserTags = remember { mutableStateListOf<String>() }
    val addedLocationTags = remember { mutableStateListOf<String>() }

    val context = LocalContext.current
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFilePath by remember { mutableStateOf<String?>(null) }
    var amplitude by remember { mutableStateOf(0f) }

    val focusManager = LocalFocusManager.current

    var isPosting by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    // Start recording function
    fun startRecording(onStart: (MediaRecorder, String) -> Unit) {
        val mediaRecorder = MediaRecorder()
        val audioFile = File(context.externalCacheDir, "recorded_audio_${System.currentTimeMillis()}.m4a")
        val audioFilePath = audioFile.absolutePath

        try {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFilePath)
                prepare()
                start()
            }

            onStart(mediaRecorder, audioFilePath)
            android.util.Log.d("startRecording", "Recording started successfully")
        } catch (e: Exception) {
            android.util.Log.e("startRecording", "Failed to start recording: ${e.message}", e)
            Toast.makeText(context, "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Stop recording function
    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
            android.util.Log.d("stopRecording", "Recording stopped successfully")
        } catch (e: Exception) {
            android.util.Log.e("stopRecording", "Failed to stop recording: ${e.message}", e)
        } finally {
            mediaRecorder = null
        }
    }

    // Delete recording function
    val onDeleteRecording = {
        // Delete the audio file
        audioFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        audioFilePath = null
        isRecording = false

        // Safely handle mediaPlayer
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer!!.isPlaying) {
                    mediaPlayer!!.stop()
                }
                mediaPlayer!!.release()
            } catch (e: Exception) {
                android.util.Log.e("onDeleteRecording", "Error stopping mediaPlayer: ${e.message}", e)
            } finally {
                mediaPlayer = null
                isPlaying = false
            }
        }
    }

    val requestPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startRecording { recorder, path ->
                    mediaRecorder = recorder
                    audioFilePath = path
                }
                isRecording = true
            } else {
                Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    // Fetch user's profile
    var userProfile by remember { mutableStateOf<Profile?>(null) }
    val userId = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(userId) {
        if (userId != null) {
            val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userProfile = snapshot.getValue(Profile::class.java)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                }
            })
        }
    }

    Scaffold(
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                    }
            ){
                // Search Bar
                CustomSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { /* Handle search logic */ }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Minimize Button and Filter Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Minimize Button
                    IconButton(
                        onClick = { isPostSectionVisible = !isPostSectionVisible }
                    ) {
                        Icon(
                            if (isPostSectionVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isPostSectionVisible) "Minimize" else "Expand",
                            tint = Color(0xFF00bf63)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    // Filter Button
                    Button(
                        onClick = { showFilterMenu = !showFilterMenu },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63)),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(text = filterOption.capitalize(), color = Color.White)
                    }

                    // Filter Icon Button
                    Box {
                        IconButton(
                            onClick = { showFilterMenu = !showFilterMenu }
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Filter Options",
                                tint = Color.White
                            )
                        }

                        // Dropdown Menu for Filter Options
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            val filterOptions = listOf("recent", "popular", "unpopular", "hometown", "own echoes")
                            filterOptions.filter { it != filterOption }.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(text = option.capitalize(), color = Color(0xFF00bf63)) },
                                    onClick = {
                                        filterOption = option
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Post Input Section with Minimize Button
                if (isPostSectionVisible) {
                    PostInputSection(
                        postContent = postContent,
                        onValueChange = { postContent = it },
                        onPost = {
                            isPostSectionVisible = false
                            isPosting = true // Start posting
                            coroutineScope.launch(Dispatchers.IO) {
                                val currentUserId =
                                    FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                                val username = userProfile?.username ?: "Anonymous"

                                val database = FirebaseDatabase.getInstance()
                                val postsRef = database.getReference("posts")

                                val postId = postsRef.push().key ?: return@launch

                                // Upload voice note if available
                                var voiceUrl: String? = null
                                if (audioFilePath != null) {
                                    val file = File(audioFilePath!!)
                                    if (file.exists() && file.length() > 0) {
                                        val storageRef = FirebaseStorage.getInstance().reference
                                        val voiceNoteRef =
                                            storageRef.child("voice_notes/$postId.m4a")

                                        val fileUri = Uri.fromFile(file)

                                        try {
                                            val metadata = StorageMetadata.Builder()
                                                .setContentType("audio/m4a")
                                                .build()

                                            voiceNoteRef.putFile(fileUri, metadata).await()
                                            voiceUrl = voiceNoteRef.downloadUrl.await().toString()
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    "Failed to upload voice note: ${e.message}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                "Recording failed or file is empty",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }

                                val post = Post(
                                    postId = postId,
                                    userId = currentUserId,
                                    username = username,
                                    contentText = postContent.text,
                                    voiceUrl = voiceUrl,
                                    transcriptedText = null,
                                    timestamp = ServerValue.TIMESTAMP,
                                    upvotes = 0,
                                    downvotes = 0,
                                    totalComments = 0,
                                    userTags = addedUserTags.toList(),
                                    locationTags = addedLocationTags.toList(),
                                    upvoteToDownvoteRatio = 0.0
                                )

                                try {
                                    postsRef.child(postId).setValue(post).await()
                                    withContext(Dispatchers.Main) {
                                        isPosting = false // Posting complete
                                        postContent = TextFieldValue("")
                                        addedUserTags.clear()
                                        addedLocationTags.clear()
                                        audioFilePath = null
                                        Toast.makeText(
                                            context,
                                            "Post uploaded successfully",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isPosting =
                                            false // Posting complete even if there was an error
                                        Toast.makeText(
                                            context,
                                            "Failed to upload post: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        },
                        addedUserTags = addedUserTags,
                        addedLocationTags = addedLocationTags,
                        isRecording = isRecording,
                        onRecordClick = {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                if (!isRecording) {
                                    // Start recording
                                    startRecording { recorder, path ->
                                        mediaRecorder = recorder
                                        audioFilePath = path
                                    }
                                    isRecording = true
                                } else {
                                    // Stop recording
                                    stopRecording()
                                    isRecording = false
                                }
                            } else {
                                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        amplitude = amplitude,  // Pass amplitude to PostInputSection
                        audioFilePath = audioFilePath,
                        onDeleteRecording = onDeleteRecording,
                        mediaPlayer = mediaPlayer,
                        onMediaPlayerChange = { player ->
                            mediaPlayer = player
                        },
                        isPlaying = isPlaying,
                        onIsPlayingChange = { playing ->
                            isPlaying = playing
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Feed Section (Fetch and display posts from Firebase)
                FeedSection(
                    navController = navController,
                    filterOption = filterOption,
                    searchQuery = searchQuery,
                    userId = userId,
                    userProfile = userProfile,
                    isPosting = isPosting,
                    onTagClick = { tag ->
                        searchQuery = tag
                    }
                )
            }
        }
    )

    // Amplitude updates
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(100)
                try {
                    amplitude = mediaRecorder?.maxAmplitude?.toFloat() ?: 0f
                } catch (e: Exception) {
                    amplitude = 0f
                    android.util.Log.e("Amplitude", "Error reading amplitude: ${e.message}", e)
                    break
                }
            }
        } else {
            amplitude = 0f
        }
    }
}

@Composable
fun FeedSection(
    navController: NavController,
    filterOption: String,
    searchQuery: String,
    userId: String?,
    userProfile: Profile?,
    isPosting: Boolean,
    onTagClick: (String) -> Unit
) {
    val posts = remember { mutableStateListOf<Post>() }
    var currentlyPlayingPostId by remember { mutableStateOf<String?>(null) }
    val postsRef = FirebaseDatabase.getInstance().getReference("posts")
    val userProfiles = remember { mutableStateMapOf<String, Profile>() }
    val context = LocalContext.current

    var loading by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()

    // Load initial posts or refresh when filter or posting status changes
    LaunchedEffect(filterOption, searchQuery, userId, userProfile, isPosting) {
        if (!isPosting) {
            loading = true
            loadInitialPosts(posts, postsRef, userProfiles, filterOption, searchQuery, userId, userProfile) {
                loading = false
            }
        }
    }

    // Scroll to top when search query changes
    LaunchedEffect(searchQuery) {
        listState.animateScrollToItem(0)
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
    } else {
        // Show posts
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .background(Color.Black)
        ) {
            if (isPosting) {
                // Show a loading indicator in place of the post
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00bf63))
                    }
                }
            }

            items(posts) { post ->
                val profile = userProfiles[post.userId]
                FeedItem(
                    post = post,
                    userProfile = profile,
                    currentlyPlayingPostId = currentlyPlayingPostId,
                    onPlay = { postId ->
                        currentlyPlayingPostId = postId
                    },
                    onUpvote = {
                        val postRef = FirebaseDatabase.getInstance().getReference("posts").child(post.postId)
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@FeedItem

                        postRef.runTransaction(object : Transaction.Handler {
                            override fun doTransaction(currentData: MutableData): Transaction.Result {
                                val post = currentData.getValue(Post::class.java) ?: return Transaction.success(currentData)

                                // Initialize the upvoted and downvoted maps if they are null
                                if (post.upvotedUsers == null) {
                                    post.upvotedUsers = mutableMapOf()
                                }
                                if (post.downvotedUsers == null) {
                                    post.downvotedUsers = mutableMapOf()
                                }

                                // Check if the user has already upvoted
                                if (post.upvotedUsers.containsKey(currentUserId)) {
                                    // Remove the upvote
                                    post.upvotedUsers.remove(currentUserId)
                                    post.upvotes -= 1
                                } else {
                                    // If the user has downvoted, remove the downvote first
                                    if (post.downvotedUsers.containsKey(currentUserId)) {
                                        post.downvotedUsers.remove(currentUserId)
                                        post.downvotes -= 1
                                    }
                                    // Add the upvote
                                    post.upvotedUsers[currentUserId] = true
                                    post.upvotes += 1
                                }

                                // Set the updated post data back
                                currentData.value = post
                                return Transaction.success(currentData)
                            }

                            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                                if (error != null) {
                                    Toast.makeText(context, "Failed to update upvote: ${error.message}", Toast.LENGTH_SHORT).show()
                                } else if (committed) {
                                    // Update the UI immediately by finding and updating the corresponding post in your UI state
                                    val updatedPost = currentData?.getValue(Post::class.java)
                                    if (updatedPost != null) {
                                        val index = posts.indexOfFirst { it.postId == updatedPost.postId }
                                        if (index != -1) {
                                            posts[index] = updatedPost
                                        }
                                    }
                                }
                            }
                        })
                    },

                    onDownvote = {
                        val postRef = FirebaseDatabase.getInstance().getReference("posts").child(post.postId)
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@FeedItem

                        postRef.runTransaction(object : Transaction.Handler {
                            override fun doTransaction(currentData: MutableData): Transaction.Result {
                                val post = currentData.getValue(Post::class.java) ?: return Transaction.success(currentData)

                                // Initialize the upvoted and downvoted maps if they are null
                                if (post.upvotedUsers == null) {
                                    post.upvotedUsers = mutableMapOf()
                                }
                                if (post.downvotedUsers == null) {
                                    post.downvotedUsers = mutableMapOf()
                                }

                                // Check if the user has already downvoted
                                if (post.downvotedUsers.containsKey(currentUserId)) {
                                    // Remove the downvote
                                    post.downvotedUsers.remove(currentUserId)
                                    post.downvotes -= 1
                                } else {
                                    // If the user has upvoted, remove the upvote first
                                    if (post.upvotedUsers.containsKey(currentUserId)) {
                                        post.upvotedUsers.remove(currentUserId)
                                        post.upvotes -= 1
                                    }
                                    // Add the downvote
                                    post.downvotedUsers[currentUserId] = true
                                    post.downvotes += 1
                                }

                                // Set the updated post data back
                                currentData.value = post
                                return Transaction.success(currentData)
                            }

                            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                                if (error != null) {
                                    Toast.makeText(context, "Failed to update downvote: ${error.message}", Toast.LENGTH_SHORT).show()
                                } else if (committed) {
                                    // Update the UI immediately by finding and updating the corresponding post in your UI state
                                    val updatedPost = currentData?.getValue(Post::class.java)
                                    if (updatedPost != null) {
                                        val index = posts.indexOfFirst { it.postId == updatedPost.postId }
                                        if (index != -1) {
                                            posts[index] = updatedPost
                                        }
                                    }
                                }
                            }
                        })
                    },
                    onUserClick = {
                        // Navigate to user's profile
                        navController.navigate("user_profile/${post.userId}")
                    },
                    onTagClick = { tag ->
                        onTagClick(tag)
                    },
                    onShare = {
                        sharePostWithMatches(context, post)
                    },
                    onDownload = {
                        if (!post.voiceUrl.isNullOrEmpty()) {
                            downloadVoicePost(context, post.voiceUrl!!, post.postId)
                        }
                    },
                    onComment = { commentText ->
                        // Comment logic
                        val postRef = FirebaseDatabase.getInstance().getReference("posts").child(post.postId)
                        val newCommentId = postRef.child("comments").push().key ?: return@FeedItem
                        val newComment = Comment(
                            userId = userId ?: "",
                            username = userProfile?.username ?: "Anonymous",
                            commentText = commentText,
                            timestamp = ServerValue.TIMESTAMP
                        )
                        postRef.child("comments/$newCommentId").setValue(newComment).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                // Update local post object
                                val updatedComments = post.comments.toMutableMap()
                                updatedComments[newCommentId] = newComment
                                val index = posts.indexOfFirst { it.postId == post.postId }
                                if (index != -1) {
                                    posts[index] = posts[index].copy(comments = updatedComments)
                                }
                            } else {
                                Toast.makeText(context, "Failed to add comment: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // No more posts indicator
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No more older posts",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}



// Function to load initial posts
fun loadInitialPosts(
    posts: SnapshotStateList<Post>,
    postsRef: DatabaseReference,
    userProfiles: SnapshotStateMap<String, Profile>,
    filterOption: String,
    searchQuery: String,
    userId: String?,
    userProfile: Profile?,
    onPostsLoaded: () -> Unit
) {
    posts.clear()
    val query = when (filterOption) {
        "recent" -> postsRef.orderByChild("timestamp").limitToLast(10)
        "popular" -> postsRef.orderByChild("upvotes").limitToLast(10)
        "unpopular" -> postsRef.orderByChild("downvotes").limitToLast(10)
        "own echoes" -> {
            if (userId != null) {
                postsRef.orderByChild("userId").equalTo(userId).limitToLast(10)
            } else {
                postsRef.orderByChild("timestamp").limitToLast(10)
            }
        }
        "hometown" -> postsRef.orderByChild("timestamp").limitToLast(10)
        else -> postsRef.orderByChild("timestamp").limitToLast(10)
    }

    query.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            handlePostSnapshot(snapshot, posts, userProfiles, filterOption, searchQuery, userId, userProfile)
            onPostsLoaded()
        }

        override fun onCancelled(error: DatabaseError) {
            onPostsLoaded()
        }
    })
}

// Function to handle Firebase post snapshot
fun handlePostSnapshot(
    snapshot: DataSnapshot,
    posts: SnapshotStateList<Post>,
    userProfiles: SnapshotStateMap<String, Profile>,
    filterOption: String,
    searchQuery: String,
    userId: String?,
    userProfile: Profile?
) {
    val userIdsToFetch = mutableSetOf<String>()
    val newPosts = mutableListOf<Post>()

    for (postSnapshot in snapshot.children) {
        // Correctly cast DataSnapshot to your model
        try {
            val post = postSnapshot.getValue(Post::class.java)
            if (post != null) {
                var includePost = true

                // Apply filterOption
                when (filterOption) {
                    "own echoes" -> includePost = includePost && (post.userId == userId)
                    "hometown" -> {
                        val hometown = userProfile?.hometown ?: ""
                        if (hometown.isNotEmpty()) {
                            includePost = includePost && post.locationTags.any { tag ->
                                tag.equals(hometown, ignoreCase = true)
                            }
                        } else {
                            includePost = false
                        }
                    }
                }

                // Apply search query filtering
                if (searchQuery.isNotEmpty()) {
                    val lowerSearchQuery = searchQuery.lowercase(Locale.getDefault())
                    val usernameMatch = post.username.lowercase(Locale.getDefault()).contains(lowerSearchQuery)
                    val userTagsMatch = post.userTags.any { tag ->
                        tag.lowercase(Locale.getDefault()).contains(lowerSearchQuery)
                    }
                    val locationTagsMatch = post.locationTags.any { tag ->
                        tag.lowercase(Locale.getDefault()).contains(lowerSearchQuery)
                    }

                    includePost = includePost && (usernameMatch || userTagsMatch || locationTagsMatch)
                }

                if (includePost) {
                    newPosts.add(post)
                    if (!userProfiles.containsKey(post.userId)) {
                        userIdsToFetch.add(post.userId)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("handlePostSnapshot", "Error deserializing post: ${e.message}", e)
        }
    }

    // Sort posts in descending order based on the filter criteria
    when (filterOption) {
        "recent", "own echoes", "hometown" -> newPosts.sortByDescending { it.getPostTimestamp() }
        "popular" -> newPosts.sortByDescending { it.upvotes }
        "unpopular" -> newPosts.sortByDescending { it.downvotes }
    }

    posts.clear()
    posts.addAll(newPosts)

    // Fetch user profiles asynchronously
    CoroutineScope(Dispatchers.IO).launch {
        userIdsToFetch.forEach { uid ->
            val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val profile = snapshot.getValue(Profile::class.java)
                    if (profile != null) {
                        userProfiles[uid] = profile
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("handlePostSnapshot", "Error fetching profile: ${error.message}", error.toException())
                }
            })
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedItem(
    post: Post,
    userProfile: Profile?,
    currentlyPlayingPostId: String?,
    onPlay: (String?) -> Unit,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onUserClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onComment: (String) -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer: MediaPlayer? by remember { mutableStateOf(null) }
    var isPrepared by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    var currentPosition by remember { mutableStateOf(0) }
    var commentText by remember { mutableStateOf(TextFieldValue("")) }
    var showCommentsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(post.voiceUrl) {
        if (!post.voiceUrl.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val mp = MediaPlayer()
                    mp.setDataSource(post.voiceUrl)
                    mp.setOnPreparedListener {
                        duration = it.duration
                        isPrepared = true
                    }
                    mp.prepareAsync()
                    mediaPlayer = mp
                } catch (e: Exception) {
                    // Handle exception, e.g., log it
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    // Handle stopping the MediaPlayer when playing a new post
    LaunchedEffect(currentlyPlayingPostId) {
        if (currentlyPlayingPostId != post.postId) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            currentPosition = 0
        }
    }

    // Update currentPosition while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(100)
            mediaPlayer?.let {
                currentPosition = it.currentPosition
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp)), // Improved shadow for better aesthetics
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(2.dp, Color(0xFF00bf63)), // Increased border thickness
        shape = RoundedCornerShape(16.dp) // Increased corner radius for softer edges
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // User Info Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onUserClick() }
                    .padding(bottom = 8.dp)
            ) {
                if (userProfile?.profilepicUrl != null) {
                    AsyncImage(
                        model = userProfile.profilepicUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(48.dp) // Increased size for better visibility
                            .clip(CircleShape)
                            .background(Color.Gray)
                    )
                } else {
                    // Placeholder image
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Gray)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = userProfile?.username ?: "Unavailable",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp // Increased font size
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⭐ ${userProfile?.rating ?: 0.0}  🌎 Rank: ${userProfile?.am24RankingGlobal ?: 0}",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        if (post.locationTags.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "📍${post.locationTags[0]}",
                                color = Color(0xFF00bf63),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Post Content
            if (!post.contentText.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = post.contentText,
                    color = Color.White,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    lineHeight = 20.sp // Added line height for better readability
                )
            }

            // Voice Note Playback UI (Added seek functionality)
            if (!post.voiceUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                        .border(border = BorderStroke(2.dp, Color.White), shape = CutCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = {
                        if (currentlyPlayingPostId != post.postId) {
                            onPlay(post.postId)
                            mediaPlayer?.stop()
                            mediaPlayer?.release()
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(post.voiceUrl)
                                setOnPreparedListener {
                                    duration = it.duration
                                    it.start()
                                    isPlaying = true
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    currentPosition = 0
                                    release()
                                    mediaPlayer = null
                                    onPlay(null)
                                }
                                prepareAsync()
                            }
                        } else {
                            if (!isPlaying) {
                                mediaPlayer?.start()
                                isPlaying = true
                            } else {
                                mediaPlayer?.pause()
                                isPlaying = false
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color(0xFF00bf63)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Audio Duration (Countdown)
                    Text(
                        text = if (isPrepared) formatDuration((duration - currentPosition)) else "0:00",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                // Slider to allow users to seek within the audio
                if (isPrepared) {
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { value ->
                            mediaPlayer?.seekTo(value.toInt())
                        },
                        valueRange = 0f..duration.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00bf63),
                            activeTrackColor = Color(0xFF00bf63)
                        )
                    )
                }
            }


            // Hashtags Below Main Content
            if (post.userTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    post.userTags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp) // Add padding around each tag to simulate spacing
                                .background(Color(0xFF00bf63), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.clickable { onTagClick(tag) }
                            )
                        }
                    }
                }
            }

            // Comment section
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Add a comment...", color = Color.Gray) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00bf63),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF00bf63)
                    ),
                    shape = RoundedCornerShape(12.dp) // Rounded corners for comment input
                )
                Button(
                    onClick = {
                        onComment(commentText.text)
                        commentText = TextFieldValue("") // Clear comment input after submitting
                    },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                ) {
                    Text("Comment", color = Color.White)
                }

                if (post.comments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "View Comments (${post.comments.size})",
                        color = Color(0xFF00bf63),
                        modifier = Modifier.clickable {
                            showCommentsDialog = true
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Comments Dialog
            if (showCommentsDialog) {
                AlertDialog(
                    onDismissRequest = { showCommentsDialog = false },
                    title = { Text(text = "Comments", color = Color.White) },
                    text = {
                        LazyColumn {
                            items(post.comments.values.toList()) { comment ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = comment.username,
                                        color = Color(0xFF00bf63),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = comment.commentText,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = formatTimestamp(comment.getCommentTimestamp()),
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showCommentsDialog = false }) {
                            Text("Close", color = Color(0xFF00bf63))
                        }
                    },
                    containerColor = Color.Black
                )
            }


            Spacer(modifier = Modifier.height(16.dp))

            // Sharing and Download Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Share Button
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }

                // Download Button
                if (!post.voiceUrl.isNullOrEmpty()) {
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                    }
                }
            }

            // Post Metadata and Actions
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formatTimestamp(post.timestamp as Long),
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                // Upvote and Downvote Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onUpvote) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = "Upvote",
                                tint = Color(0xFF00ff00) // Brighter green for upvote
                            )
                        }
                        Text(text = "${post.upvotes}", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDownvote) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = "Downvote",
                                tint = Color(0xffff0000) // Brighter red for downvote
                            )
                        }
                        Text(text = "${post.downvotes}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}




@Composable
fun PostInputSection(
    postContent: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isRecording: Boolean,
    onRecordClick: () -> Unit,
    onPost: () -> Unit,
    addedUserTags: MutableList<String>,
    addedLocationTags: MutableList<String>,
    amplitude: Float,
    audioFilePath: String?,
    onDeleteRecording: () -> Unit,
    mediaPlayer: MediaPlayer?,
    onMediaPlayerChange: (MediaPlayer?) -> Unit,
    isPlaying: Boolean,
    onIsPlayingChange: (Boolean) -> Unit
) {
    var userTag by remember { mutableStateOf(TextFieldValue("")) }
    var locationTag by remember { mutableStateOf(TextFieldValue("")) }
    var recordingDuration by remember { mutableStateOf(0) } // Initialize recording duration

    val animatedAmplitude by animateFloatAsState(targetValue = amplitude)

    val context = LocalContext.current

    // Increase recording duration every second when recording is in progress
    // Voice Recording Controls (updated duration limit to 600 seconds)
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording && recordingDuration < 600) {
                delay(1000L)
                recordingDuration++
            }

            if (recordingDuration >= 600) {
                // Stop recording if limit is reached
                onRecordClick()
                Toast.makeText(
                    context,
                    "Recording limit reached (10 minutes)",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            recordingDuration = 0 // Reset the recording duration when not recording
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(Color.Black)
    ) {
        // Voice Recording Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            IconButton(onClick = {
                if (recordingDuration >= 150) {
                    Toast.makeText(
                        context,
                        "Recording limit reached (2.5 minutes)",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    onRecordClick()
                }
            }) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                    tint = Color(0xFF00bf63)
                )
            }

            OutlinedTextField(
                value = postContent,
                onValueChange = { onValueChange(it) },
                placeholder = { Text("Type your thoughts/Voice your opinion", color = Color.Gray) },
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 16.sp),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedBorderColor = Color(0xFF00bf63),
                    unfocusedBorderColor = Color(0xFF00bf63)
                )
            )
        }

        // Visual indicator during recording (updated to use animatedAmplitude)
        if (isRecording) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(vertical = 4.dp)
            ) {
                val barWidth = 3.dp.toPx()
                val spaceWidth = 1.dp.toPx()
                val numBars = (size.width / (barWidth + spaceWidth)).toInt()
                val maxAmplitude = 32767f
                val normalizedAmplitude = (animatedAmplitude / maxAmplitude).coerceIn(0f, 1f)
                val barHeight = size.height * normalizedAmplitude

                for (i in 0 until numBars) {
                    val x = i * (barWidth + spaceWidth)
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(x, size.height - barHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                    )
                }
            }
        }


        // Playback and Delete controls after recording is completed
        if (!isRecording && audioFilePath != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                // Playback Control
                IconButton(onClick = {
                    if (!isPlaying) {
                        val player = MediaPlayer().apply {
                            setDataSource(audioFilePath)
                            prepare()
                            start()
                            setOnCompletionListener {
                                onIsPlayingChange(false)
                                release()
                                onMediaPlayerChange(null)
                            }
                        }
                        onMediaPlayerChange(player)
                        onIsPlayingChange(true)
                    } else {
                        mediaPlayer?.let { player ->
                            if (player.isPlaying) {
                                player.stop()
                            }
                            player.release()
                            onMediaPlayerChange(null)
                        }
                        onIsPlayingChange(false)
                    }
                }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        tint = Color(0xFF00bf63)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Delete Recording Control
                IconButton(onClick = {
                    onDeleteRecording()
                    // Reset mediaPlayer and isPlaying states
                    if (isPlaying) {
                        mediaPlayer?.let { player ->
                            player.stop()
                            player.release()
                            onMediaPlayerChange(null)
                        }
                        onIsPlayingChange(false)
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Recording",
                        tint = Color.Red
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // User Tags Input and Add Button
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = userTag,
                onValueChange = {
                    if (it.text.length <= 250 && addedUserTags.size < 10) {
                        userTag = it
                    }
                },
                label = { Text("User Tags (e.g., #funny)", color = Color(0xFF00bf63)) },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00bf63),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color(0xFF00bf63)
                ),
                singleLine = true
            )

            IconButton(
                onClick = {
                    if (userTag.text.isNotBlank() && addedUserTags.size < 10 && userTag.text.length <= 250) {
                        addedUserTags.add(userTag.text.trim())
                        userTag = TextFieldValue("")
                    }
                },
                enabled = addedUserTags.size < 10
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add User Tag", tint = if (addedUserTags.size < 10) Color(0xFF00bf63) else Color.Gray)
            }
        }

        // Display Added User Tags
        if (addedUserTags.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                addedUserTags.forEach { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF00bf63), shape = RoundedCornerShape(16.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = "#$tag", color = Color.White)
                        }
                        IconButton(
                            onClick = { addedUserTags.remove(tag) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove Tag", tint = Color.Red)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Location Tag Input and Add Button
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = locationTag,
                onValueChange = {
                    if (it.text.length <= 22) {
                        locationTag = it
                    }
                },
                label = { Text("Location Tag", color = Color(0xFF00bf63)) },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00bf63),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color(0xFF00bf63)
                ),
                singleLine = true,
                enabled = addedLocationTags.isEmpty() // Limit to 1 location tag
            )

            IconButton(
                onClick = {
                    if (locationTag.text.isNotBlank() && addedLocationTags.isEmpty()) {
                        addedLocationTags.add(locationTag.text.trim())
                        locationTag = TextFieldValue("")
                    }
                },
                enabled = addedLocationTags.isEmpty()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Location Tag", tint = if (addedLocationTags.isEmpty()) Color(0xFF00bf63) else Color.Gray)
            }
        }

        // Display Added Location Tags
        if (addedLocationTags.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                addedLocationTags.forEach { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF00bf63), shape = RoundedCornerShape(16.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = "📍$tag", color = Color.White)
                        }
                        IconButton(
                            onClick = { addedLocationTags.remove(tag) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove Tag", tint = Color.Red)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Post Button
        Button(
            onClick = {
                if (postContent.text.isNotBlank() || (audioFilePath != null && !isRecording)) {
                    onPost()
                } else {
                    Toast.makeText(
                        context,
                        "Please enter text or finish recording before posting",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
        ) {
            Text(text = "Post", color = Color.White)
        }
    }
}


fun formatDuration(durationMs: Int): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%01d:%02d", minutes, seconds)
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun CustomSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = { onQueryChange(it) },
        placeholder = { Text("Search by username or tags", color = Color.Gray) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp)),
        textStyle = LocalTextStyle.current.copy(color = Color.White),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF00bf63),
            unfocusedBorderColor = Color.Gray,
            cursorColor = Color(0xFF00bf63)
        ),
        trailingIcon = {
            IconButton(onClick = onSearch) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF00bf63))
            }
        }
    )
}

fun downloadVoicePost(context: Context, voiceUrl: String, postId: String) {
    val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(voiceUrl)
    val localFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "$postId.m4a")
    storageRef.getFile(localFile).addOnSuccessListener {
        Toast.makeText(context, "Download complete!", Toast.LENGTH_SHORT).show()
        // Optionally, share the downloaded file
        val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(fileUri, "audio/m4a")
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        context.startActivity(intent)
    }.addOnFailureListener {
        Toast.makeText(context, "Download failed: ${it.message}", Toast.LENGTH_SHORT).show()
    }
}

fun sharePostWithMatches(context: Context, post: Post) {
    // Assuming matches list exists, you can implement this logic to share the post in DMs.
    // For now, display a toast or similar action.
    Toast.makeText(context, "Post shared with your Matches!", Toast.LENGTH_SHORT).show()
}