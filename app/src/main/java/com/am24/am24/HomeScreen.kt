@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.am24.am24

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun HomeScreen(navController: NavController, modifier: Modifier = Modifier) {
    // Initialize PostViewModel
    val postViewModel: PostViewModel = viewModel()
    val posts by postViewModel.posts.collectAsState()

    // Initialize userProfiles as a SnapshotStateMap
    val userProfiles = remember { mutableStateMapOf<String, Profile>() }

    // Call HomeScreenContent with necessary parameters
    HomeScreenContent(
        navController = navController,
        modifier = modifier,
        posts = posts,
        postViewModel = postViewModel,
        userProfiles = userProfiles
    )
}

// HomeScreen.kt

@Composable
fun HomeScreenContent(
    navController: NavController,
    modifier: Modifier = Modifier,
    posts: List<Post>,
    postViewModel: PostViewModel,
    userProfiles: SnapshotStateMap<String, Profile>
) {
    val coroutineScope = rememberCoroutineScope()
    var filterOption by remember { mutableStateOf("recent") }
    var showFilterMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // State variable for filter value
    var filterValue by remember { mutableStateOf("") }

    // State variable for loading state
    var isLoading by remember { mutableStateOf(true) }

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
                    Toast.makeText(context, "Failed to fetch user profile.", Toast.LENGTH_SHORT)
                        .show()
                }
            })
        }
    }

    // Load initial posts when the screen is first composed or when filters change
    LaunchedEffect(filterOption, filterValue, searchQuery, userId, userProfile) {
        isLoading = true
        if (userId != null) {
            loadInitialPosts(
                posts = mutableStateListOf(),
                postsRef = FirebaseDatabase.getInstance().getReference("posts"),
                userProfiles = userProfiles,
                filterOption = filterOption,
                filterValue = filterValue,
                searchQuery = searchQuery,
                userId = userId,
                userProfile = userProfile
            )
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            }
    ) {
        // Search Bar
        CustomSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = {
                // Reload posts based on search query
                coroutineScope.launch {
                    isLoading = true
                    loadInitialPosts(
                        posts = mutableStateListOf(),
                        postsRef = FirebaseDatabase.getInstance().getReference("posts"),
                        userProfiles = userProfiles,
                        filterOption = filterOption,
                        filterValue = filterValue,
                        searchQuery = searchQuery,
                        userId = userId,
                        userProfile = userProfile
                    )
                    isLoading = false
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Create Post and Filter Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Create Post Button (Navigate to CreatePostScreen)
            Button(
                onClick = { navController.navigate("create_post") },
                border = BorderStroke(1.dp, Color(0xFF00bf63)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Post",
                    tint = Color(0xFF00bf63)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Create Post", color = Color(0xFF00bf63))
            }

            // Filter Button
            Box {
                Button(
                    onClick = { showFilterMenu = !showFilterMenu },
                    border = BorderStroke(1.dp, Color(0xFF00bf63)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Text(
                        text = filterOption.replaceFirstChar { it.uppercase() },
                        color = Color(0xFF00bf63)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Filter options",
                        tint = Color(0xFF00bf63)
                    )
                }

                // Dropdown Menu for Filter Options
                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false },
                ) {
                    val filterOptions = listOf(
                        "recent", "popular", "unpopular", "own echoes",
                        "city", "age", "level", "gender", "high-school", "college"
                    )
                    filterOptions.filter { it != filterOption }.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.replaceFirstChar { it.uppercase() },
                                    color = Color(0xFF00bf63)
                                )
                            },
                            onClick = {
                                filterOption = option
                                filterValue = "" // Reset filter value when option changes
                                showFilterMenu = false
                                // Reload posts based on new filter
                                coroutineScope.launch {
                                    isLoading = true
                                    loadInitialPosts(
                                        posts = mutableStateListOf(),
                                        postsRef = FirebaseDatabase.getInstance()
                                            .getReference("posts"),
                                        userProfiles = userProfiles,
                                        filterOption = filterOption,
                                        filterValue = filterValue,
                                        searchQuery = searchQuery,
                                        userId = userId,
                                        userProfile = userProfile
                                    )
                                    isLoading = false
                                }
                            }
                        )
                    }
                }
            }
        }

        // Input Field for Additional Filters
        if (filterOption in listOf("city", "age", "level", "gender", "high-school", "college")) {
            Spacer(modifier = Modifier.height(8.dp))
            when (filterOption) {
                "age" -> {
                    // Integer input field
                    OutlinedTextField(
                        value = filterValue,
                        onValueChange = { newValue ->
                            // Allow only numbers
                            if (newValue.all { it.isDigit() }) {
                                filterValue = newValue
                            }
                        },
                        label = { Text("Enter Age", color = Color.White) },
                        placeholder = { Text("e.g., 25", color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00bf63),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color(0xFF00bf63)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                "gender" -> {
                    // Dropdown with options 'M', 'F', 'NB'
                    var expanded by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        OutlinedTextField(
                            value = filterValue,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Gender", color = Color.White) },
                            placeholder = { Text("M/F/NB", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00bf63),
                                unfocusedBorderColor = Color.Gray,
                                cursorColor = Color(0xFF00bf63)
                            ),
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown",
                                    tint = Color.White,
                                    modifier = Modifier.clickable { expanded = true }
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            listOf("M", "F", "NB").forEach { gender ->
                                DropdownMenuItem(
                                    text = { Text(gender, color = Color(0xFF00bf63)) },
                                    onClick = {
                                        filterValue = gender
                                        expanded = false
                                        // Reload posts based on new filter
                                        coroutineScope.launch {
                                            isLoading = true
                                            loadInitialPosts(
                                                posts = mutableStateListOf(),
                                                postsRef = FirebaseDatabase.getInstance()
                                                    .getReference("posts"),
                                                userProfiles = userProfiles,
                                                filterOption = filterOption,
                                                filterValue = filterValue,
                                                searchQuery = searchQuery,
                                                userId = userId,
                                                userProfile = userProfile
                                            )
                                            isLoading = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                "level" -> {
                    // Integer input field (1-7)
                    OutlinedTextField(
                        value = filterValue,
                        onValueChange = { newValue ->
                            // Allow only numbers between 1 and 7
                            val intValue = newValue.toIntOrNull()
                            if (newValue.all { it.isDigit() } && intValue in 1..7) {
                                filterValue = newValue
                            }
                        },
                        label = { Text("Enter Level (1-7)", color = Color.White) },
                        placeholder = { Text("e.g., 3", color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00bf63),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color(0xFF00bf63)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                else -> {
                    // Text input field
                    OutlinedTextField(
                        value = filterValue,
                        onValueChange = { filterValue = it },
                        label = {
                            Text(
                                "Enter ${filterOption.replaceFirstChar { it.uppercase() }}",
                                color = Color.White
                            )
                        },
                        placeholder = { Text("Type here", color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00bf63),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color(0xFF00bf63)
                        )
                    )
                }
            }

            // Apply filter when filter value changes
            LaunchedEffect(filterValue) {
                coroutineScope.launch {
                    isLoading = true
                    loadInitialPosts(
                        posts = mutableStateListOf(),
                        postsRef = FirebaseDatabase.getInstance().getReference("posts"),
                        userProfiles = userProfiles,
                        filterOption = filterOption,
                        filterValue = filterValue,
                        searchQuery = searchQuery,
                        userId = userId,
                        userProfile = userProfile
                    )
                    isLoading = false
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Feed Section (Use posts from ViewModel)
        FeedSection(
            navController = navController,
            posts = posts,
            filterOption = filterOption,
            filterValue = filterValue,
            searchQuery = searchQuery,
            userId = userId,
            userProfile = userProfile,
            isPosting = false, // Removed isPosting as it's managed via PostViewModel
            postViewModel = postViewModel,
            userProfiles = userProfiles,
            onTagClick = { tag -> searchQuery = tag },
            isLoading = isLoading // Pass loading state
        )
    }
}

    /**
     * Modified FeedSection to handle new parameters and loading state
     */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun FeedSection(
        navController: NavController,
        posts: List<Post>,
        filterOption: String,
        filterValue: String,
        searchQuery: String,
        userId: String?,
        userProfile: Profile?,
        isPosting: Boolean,
        postViewModel: PostViewModel,
        userProfiles: SnapshotStateMap<String, Profile>,
        onTagClick: (String) -> Unit,
        isLoading: Boolean // New parameter to handle loading state
    ) {
        val context = LocalContext.current
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .background(Color.Black)
        ) {
            // Display skeletons when loading
            if (isLoading) {
                items(3) { // Number of skeleton items
                    SkeletonFeedItem()
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } else {
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
                        onUpvote = {
                            postViewModel.upvotePost(
                                postId = post.postId,
                                userId = userId ?: "",
                                onSuccess = {
                                    // Optionally, show a success message or update UI
                                },
                                onFailure = { errorMsg ->
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        onDownvote = {
                            postViewModel.downvotePost(
                                postId = post.postId,
                                userId = userId ?: "",
                                onSuccess = {
                                    // Optionally, show a success message or update UI
                                },
                                onFailure = { errorMsg ->
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        onUserClick = {
                            if (post.userId == userId) {
                                // Navigate to ProfileScreen if it's your own profile
                                navController.navigate("profile")
                            } else {
                                // Navigate to DatingScreen with the other user's ID
                                navController.navigate("dating/${post.userId}")
                            }
                        },
                        onTagClick = { tag ->
                            onTagClick(tag)
                        },
                        onShare = {
                            postViewModel.sharePostWithMatches(
                                postId = post.postId,
                                matches = listOf(), // Replace with actual list of matches
                                onSuccess = {
                                    Toast.makeText(
                                        context,
                                        "Post shared successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onFailure = { errorMsg ->
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        onSave = { postToSave ->
                            // Save post logic calling ViewModel's savePost function
                            postViewModel.savePost(
                                postId = postToSave.postId,
                                userId = userId ?: "",
                                onSuccess = {
                                    // Handle post save success
                                },
                                onFailure = {
                                    // Handle post save failure
                                }
                            )
                        },
                        onComment = { commentText ->
                            // Comment logic using ViewModel
                            val comment = Comment(
                                commentId = UUID.randomUUID().toString(), // Generate a unique ID
                                userId = userId ?: "",
                                username = userProfile?.username ?: "Anonymous",
                                commentText = commentText,
                                timestamp = ServerValue.TIMESTAMP
                            )
                            postViewModel.addComment(
                                postId = post.postId,
                                comment = comment,
                                onSuccess = {
                                    Toast.makeText(context, "Comment added!", Toast.LENGTH_SHORT)
                                        .show()
                                },
                                onFailure = { errorMsg ->
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        currentUserId = userId ?: "",
                        onDelete = { postToDelete ->
                            // Implement delete logic, possibly calling PostViewModel's deletePost
                            postViewModel.deletePost(
                                postId = postToDelete.postId,
                                onSuccess = {
                                },
                                onFailure = {
                                }
                            )
                        },
                        onReport = { postToReport ->
                            // Implement report logic, possibly calling PostViewModel's reportPost
                            postViewModel.reportPost(
                                postId = postToReport.postId,
                                reporterId = userId ?: "",
                                onSuccess = {
                                },
                                onFailure = {
                                }
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Indicator for no more posts
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

        /**
         * Skeleton FeedItem composable to display while loading posts
         */
        @Composable
        fun SkeletonFeedItem() {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                border = BorderStroke(2.dp, Color.Gray),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Skeleton for User Info
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Box(
                                modifier = Modifier
                                    .height(20.dp)
                                    .width(100.dp)
                                    .background(Color.Gray, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .height(12.dp)
                                    .width(80.dp)
                                    .background(Color.Gray, RoundedCornerShape(4.dp))
                            )
                        }
                    }

                    // Skeleton for Post Content
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .height(100.dp)
                            .fillMaxWidth()
                            .background(Color.Gray, RoundedCornerShape(8.dp))
                    )

                    // Skeleton for Hashtags
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Box(
                            modifier = Modifier
                                .height(20.dp)
                                .width(60.dp)
                                .background(Color.Gray, RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .height(20.dp)
                                .width(80.dp)
                                .background(Color.Gray, RoundedCornerShape(8.dp))
                        )
                    }

                    // Skeleton for Interaction Buttons
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .height(20.dp)
                                .width(60.dp)
                                .background(Color.Gray, RoundedCornerShape(8.dp))
                        )
                        Box(
                            modifier = Modifier
                                .height(20.dp)
                                .width(60.dp)
                                .background(Color.Gray, RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        }

        /**
         * Modified FeedItem with additional parameters and fullscreen functionality
         */
        @Composable
        fun FeedItem(
            post: Post,
            userProfile: Profile?,
            onUpvote: () -> Unit,
            onDownvote: () -> Unit,
            onUserClick: () -> Unit,
            onTagClick: (String) -> Unit,
            onShare: () -> Unit,
            onComment: (String) -> Unit,
            currentUserId: String,
            onDelete: (Post) -> Unit,
            onReport: (Post) -> Unit,
            onSave: (Post) -> Unit // <-- Add this line to accept the post in onSave
        ) {
            var commentText by remember { mutableStateOf(TextFieldValue("")) }
            var showCommentsDialog by remember { mutableStateOf(false) }
            var showMediaFullscreen by remember { mutableStateOf(false) }
            var showCommentSection by remember { mutableStateOf(false) }

            // State for voice post playback
            var isPlaying by remember { mutableStateOf(false) }
            var playbackProgress by remember { mutableStateOf(0f) }
            var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

            // Annotate post content based on formatting markers
            val annotatedText = buildFormattedText(post.contentText ?: "")

            // Calculate user age from DOB
            val userAge = userProfile?.dob?.let { calculateAge(it) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 4.dp) // Wider card with less horizontal padding
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                border = BorderStroke(2.dp, Color(0xFF00bf63)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // User Info Row with Delete/Report button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onUserClick() }
                            .padding(bottom = 8.dp)
                    ) {
                        // User profile picture
                        if (userProfile?.profilepicUrl != null) {
                            AsyncImage(
                                model = userProfile.profilepicUrl,
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray)
                            )
                        } else {
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
                                text = "${userProfile?.username ?: "Unavailable"}, ${userAge ?: "--"}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row {
                                Text(
                                    text = "⭐ ${userProfile?.rating ?: 0.0}  🌎 Rank: ${userProfile?.am24RankingGlobal ?: 0}",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = formatTimestamp(post.getPostTimestamp()),
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Show Delete or Report button, Delete icon accent green now and shifted upwards
                        if (post.userId == currentUserId) {
                            IconButton(onClick = { onDelete(post) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Post",
                                    tint = Color(0xFF00bf63) // Accent green delete icon
                                )
                            }
                        } else {
                            IconButton(onClick = { onReport(post) }) {
                                Icon(
                                    imageVector = Icons.Default.Report,
                                    contentDescription = "Report Post",
                                    tint = Color.Yellow
                                )
                            }
                        }
                    }

                    // Post Content (Formatted Text)
                    if (!post.contentText.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = annotatedText,
                            color = Color.White,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 18.sp
                        )
                    }

                    // Media Content - Photo, Video, Voice
                    if (post.mediaType != null && post.mediaUrl != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            when (post.mediaType) {
                                "photo" -> {
                                    AsyncImage(
                                        model = post.mediaUrl,
                                        contentDescription = "Post Image",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }

                                "video" -> {
                                    ExoPlayerComposable(
                                        videoUri = post.mediaUrl,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }

                                "voice" -> {
                                    // Voice Post Playback UI
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (isPlaying) {
                                                        mediaPlayer?.pause()
                                                        isPlaying = false
                                                    } else {
                                                        mediaPlayer?.start()
                                                        isPlaying = true
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Play/Pause",
                                                    tint = Color(0xFF00bf63),
                                                    modifier = Modifier.size(48.dp)
                                                )
                                            }

                                            // Progress bar for voice playback
                                            LinearProgressIndicator(
                                                progress = playbackProgress,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 16.dp),
                                                color = Color(0xFF00bf63),
                                                trackColor = Color.Gray
                                            )

                                            // Duration label moved next to progress bar
                                            Text(
                                                text = formatDuration((mediaPlayer?.duration ?: 0).toLong()),
                                                color = Color.Gray,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Fullscreen Icon for photos/videos
                            if (post.mediaType != "voice") {
                                IconButton(
                                    onClick = { showMediaFullscreen = true },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Fullscreen",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sharing, Saving, Upvote/Downvote Section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row {
                            IconButton(onClick = onShare) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White
                                )
                            }
                            // Added save icon beside the share button
                            IconButton(onClick = { onSave(post) }) {
                                Icon(
                                    Icons.Default.BookmarkBorder,
                                    contentDescription = "Save",
                                    tint = Color.White
                                )
                            }
                        }

                        // Upvote and Downvote Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onUpvote) {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        contentDescription = "Upvote",
                                        tint = Color(0xFF00ff00)
                                    )
                                }
                                Text(
                                    text = "${post.upvotes}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onDownvote) {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        contentDescription = "Downvote",
                                        tint = Color(0xffff0000)
                                    )
                                }
                                Text(
                                    text = "${post.downvotes}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Comment button icon to show/hide comment section
                            IconButton(onClick = { showCommentSection = !showCommentSection }) {
                                Icon(
                                    imageVector = Icons.Default.Comment,
                                    contentDescription = "Show/Hide Comments",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Conditional Comment Section based on showCommentSection state
                    if (showCommentSection) {
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
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = {
                                    if (commentText.text.isNotBlank()) {
                                        onComment(commentText.text.trim())
                                        commentText = TextFieldValue("") // Clear comment input after submitting
                                    }
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
                    }

                    // Comments Dialog
                    if (showCommentsDialog) {
                        CommentsDialog(
                            post = post,
                            onDismiss = { showCommentsDialog = false },
                            onUpvoteComment = { },
                            onDownvoteComment = { }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp)) // Reduced the space here
                }

                // Fullscreen Media View using Dialog
                if (showMediaFullscreen) {
                    Dialog(onDismissRequest = { showMediaFullscreen = false }) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        ) {
                            when (post.mediaType) {
                                "photo" -> {
                                    AsyncImage(
                                        model = post.mediaUrl,
                                        contentDescription = "Fullscreen Image",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }

                                "video" -> {
                                    post.mediaUrl?.let {
                                        ExoPlayerComposable(
                                            videoUri = it,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            // Close Button
                            IconButton(
                                onClick = { showMediaFullscreen = false },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Initialize MediaPlayer and manage playback for voice posts
            DisposableEffect(post.mediaUrl) {
                if (post.mediaType == "voice" && post.mediaUrl != null) {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(post.mediaUrl)
                        prepare()
                        setOnCompletionListener {
                            isPlaying = false
                        }
                    }
                }

                onDispose {
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
            }

            // Update progress for the voice post
            LaunchedEffect(isPlaying) {
                if (isPlaying && mediaPlayer != null) {
                    while (isPlaying && mediaPlayer?.isPlaying == true) {
                        delay(500L)
                        val current = mediaPlayer?.currentPosition ?: 0
                        val duration = mediaPlayer?.duration ?: 1
                        playbackProgress = current.toFloat() / duration.toFloat()
                    }
                } else {
                    playbackProgress = 0f
                }
            }
        }




@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ExoPlayerComposable(
    videoUri: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUri))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(
        AndroidView(factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = true
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }, modifier = modifier)
    ) {
        onDispose {
            exoPlayer.release()
        }
    }
}

            /**
             * SimpleVideoPlayer composable using VideoView
             */
            @Composable
            fun SimpleVideoPlayer(
                videoUri: String,
                modifier: Modifier = Modifier
            ) {
                AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            setVideoURI(Uri.parse(videoUri))
                            setOnPreparedListener { mediaPlayer ->
                                mediaPlayer.isLooping = false
                                start()
                            }
                            setOnCompletionListener {
                                seekTo(0)
                                // Optionally, you can add controls to restart or pause
                            }
                        }
                    },
                    modifier = modifier
                )
            }

            /**
             * CommentsDialog composable with upvote and downvote functionality
             */
            @Composable
            fun CommentsDialog(
                post: Post,
                onDismiss: () -> Unit,
                onUpvoteComment: (String) -> Unit,
                onDownvoteComment: (String) -> Unit
            ) {
                val context = LocalContext.current

                // Convert comments map to list and sort
                var sortOption by remember { mutableStateOf("Recent") }

                val commentsList = remember(post.comments, sortOption) {
                    post.comments.values.toList().sortedWith(
                        when (sortOption) {
                            "Recent" -> compareByDescending<Comment> { it.getCommentTimestamp() }
                            "Popular" -> compareByDescending<Comment> { it.upvotes - it.downvotes }
                            else -> compareByDescending<Comment> { it.getCommentTimestamp() }
                        }
                    )
                }

                AlertDialog(
                    onDismissRequest = onDismiss,
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Comments", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Row {
                                Button(
                                    onClick = { sortOption = "Recent" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (sortOption == "Recent") Color(0xFF00bf63) else Color.Gray
                                    )
                                ) {
                                    Text("Recent", color = Color.White)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { sortOption = "Popular" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (sortOption == "Popular") Color(0xFF00bf63) else Color.Gray
                                    )
                                ) {
                                    Text("Popular", color = Color.White)
                                }
                            }
                        }
                    },
                    text = {
                        if (commentsList.isEmpty()) {
                            Text("No comments yet.", color = Color.White)
                        } else {
                            LazyColumn {
                                items(commentsList) { comment ->
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        // Comment Header
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = comment.username,
                                                color = Color(0xFF00bf63),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                text = formatTimestamp(comment.getCommentTimestamp()),
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }
                                        // Comment Text
                                        Text(
                                            text = comment.commentText,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        // Upvote/Downvote Buttons
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(onClick = { onUpvoteComment(comment.commentId) }) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowUpward,
                                                    contentDescription = "Upvote",
                                                    tint = Color(0xFF00ff00)
                                                )
                                            }
                                            Text(text = "${comment.upvotes}", color = Color.White)
                                            IconButton(onClick = { onDownvoteComment(comment.commentId) }) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDownward,
                                                    contentDescription = "Downvote",
                                                    tint = Color(0xffff0000)
                                                )
                                            }
                                            Text(text = "${comment.downvotes}", color = Color.White)
                                        }
                                    }
                                    Divider(color = Color.Gray, thickness = 0.5.dp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = onDismiss) {
                            Text("Close", color = Color(0xFF00bf63))
                        }
                    },
                    containerColor = Color.Black
                )
            }


            /**
             * Function to load initial posts and fetch user profiles.
             */
            suspend fun loadInitialPosts(
                posts: SnapshotStateList<Post>,
                postsRef: DatabaseReference,
                userProfiles: SnapshotStateMap<String, Profile>,
                filterOption: String,
                filterValue: String,
                searchQuery: String,
                userId: String?,
                userProfile: Profile?
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
                    "city", "age", "level", "gender", "high-school", "college" -> {
                        // For attribute-based filters, we'll fetch all and filter locally
                        postsRef.orderByChild("timestamp").limitToLast(100)
                    }
                    else -> postsRef.orderByChild("timestamp").limitToLast(10)
                }

                try {
                    val snapshot = query.get().await()
                    handlePostSnapshot(
                        snapshot = snapshot,
                        posts = posts,
                        userProfiles = userProfiles,
                        filterOption = filterOption,
                        filterValue = filterValue,
                        searchQuery = searchQuery,
                        userId = userId,
                        userProfile = userProfile
                    )
                } catch (e: Exception) {
                    // Handle error
                    Log.e("HomeScreen", "Error fetching posts: ${e.message}", e)
                }
            }

            /**
             * Function to handle the fetched posts snapshot.
             */
            fun handlePostSnapshot(
                snapshot: DataSnapshot,
                posts: SnapshotStateList<Post>,
                userProfiles: SnapshotStateMap<String, Profile>,
                filterOption: String,
                filterValue: String,
                searchQuery: String,
                userId: String?,
                userProfile: Profile?
            ) {
                val userIdsToFetch = mutableSetOf<String>()
                val newPosts = mutableListOf<Post>()

                for (postSnapshot in snapshot.children) {
                    try {
                        val post = postSnapshot.getValue(Post::class.java)
                        if (post != null) {
                            var includePost = true

                            // Check if we have the user's profile; if not, add to fetch list
                            val profile = userProfiles[post.userId]
                            if (profile == null) {
                                userIdsToFetch.add(post.userId)
                            }

                            // Apply filterOption
                            when (filterOption) {
                                "own echoes" -> includePost = includePost && (post.userId == userId)
                                "city", "age", "level", "gender", "high-school", "college" -> {
                                    if (profile != null) {
                                        includePost = includePost && checkProfileFilter(profile, filterOption, filterValue)
                                    } else {
                                        includePost = false // Exclude for now, will include after fetching profile
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

                                includePost = includePost && (usernameMatch || userTagsMatch)
                            }

                            if (includePost) {
                                newPosts.add(post)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("handlePostSnapshot", "Error deserializing post: ${e.message}", e)
                    }
                }

                // Fetch user profiles if needed
                if (userIdsToFetch.isNotEmpty()) {
                    fetchUserProfiles(userIdsToFetch, userProfiles) {
                        // After fetching profiles, re-apply the filters
                        handlePostSnapshot(snapshot, posts, userProfiles, filterOption, filterValue, searchQuery, userId, userProfile)
                    }
                    return // Exit the function to wait for profiles to be fetched
                }

                // Sort posts
                when (filterOption) {
                    "recent", "own echoes" -> newPosts.sortByDescending { it.getPostTimestamp() }
                    "popular" -> newPosts.sortByDescending { it.upvotes }
                    "unpopular" -> newPosts.sortByDescending { it.downvotes }
                }

                posts.clear()
                posts.addAll(newPosts)
            }

            /**
             * Function to apply profile-based filters.
             */
            fun checkProfileFilter(profile: Profile, filterOption: String, filterValue: String): Boolean {
                return when (filterOption) {
                    "country" -> profile.country.equals(filterValue, ignoreCase = true)
                    "city" -> profile.city.equals(filterValue, ignoreCase = true)
                    "age" -> {
                        val age = calculateAge(profile.dob)
                        val requiredAge = filterValue.toIntOrNull()
                        age != null && requiredAge != null && age == requiredAge
                    }
                    "level" -> {
                        val requiredLevel = filterValue.toIntOrNull()
                        requiredLevel != null && profile.level == requiredLevel
                    }
                    "gender" -> profile.gender.equals(filterValue, ignoreCase = true)
                    "high-school" -> profile.highSchool.equals(filterValue, ignoreCase = true)
                    "college" -> profile.college.equals(filterValue, ignoreCase = true)
                    else -> true
                }
            }

            /**
             * Function to fetch user profiles from Firebase.
             */
            fun fetchUserProfiles(
                userIds: Set<String>,
                userProfiles: SnapshotStateMap<String, Profile>,
                onComplete: () -> Unit
            ) {
                val remaining = userIds.toMutableSet()

                userIds.forEach { uid ->
                    val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
                    userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val profile = snapshot.getValue(Profile::class.java)
                            if (profile != null) {
                                userProfiles[uid] = profile
                            }
                            remaining.remove(uid)
                            if (remaining.isEmpty()) {
                                onComplete()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("fetchUserProfiles", "Error fetching profile: ${error.message}", error.toException())
                            remaining.remove(uid)
                            if (remaining.isEmpty()) {
                                onComplete()
                            }
                        }
                    })
                }
            }

// HomeScreen.kt - Part 3

@Composable
fun buildFormattedText(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", startIndex = i + 2)
                    if (end != -1) {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text.substring(i))
                        break
                    }
                }
                text.startsWith("_", i) -> {
                    val end = text.indexOf("_", startIndex = i + 1)
                    if (end != -1) {
                        withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text.substring(i))
                        break
                    }
                }
                text.startsWith("~", i) -> {
                    val end = text.indexOf("~", startIndex = i + 1)
                    if (end != -1) {
                        withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text.substring(i))
                        break
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
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
            .padding(horizontal = 16.dp),
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

fun formatDuration(durationMs: Long): String {
    val minutes = (durationMs / 1000) / 60
    val seconds = (durationMs / 1000) % 60
    return String.format("%d:%02d", minutes, seconds)
}