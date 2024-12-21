// HomeScreen.kt - Part 1

@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.am24.am24

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelStoreOwner
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun HomeScreen(navController: NavController, postViewModel: PostViewModel, modifier: Modifier = Modifier) {

    val filtersLoaded by postViewModel.filtersLoaded.collectAsState()

    // Check if filters are loaded
    if (filtersLoaded) {
        // Collect filteredPosts instead of posts
        val posts by postViewModel.filteredPosts.collectAsState()
        Log.d("HomeScreen", "Filtered Posts Count in Homescreen: ${posts.size}")

        val userProfiles by postViewModel.userProfiles.collectAsState()
        val filterSettings by postViewModel.filterSettings.collectAsState()

        val userId = FirebaseAuth.getInstance().currentUser?.uid

        // Set current user ID in ViewModel
        LaunchedEffect(userId) {
            postViewModel.setCurrentUserId(userId)
        }

        var userProfile by remember { mutableStateOf<Profile?>(null) }


        // Fetch user's profile
        LaunchedEffect(userId) {
            if (userId != null) {
                withContext(Dispatchers.IO) {
                    val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
                    userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            snapshot.getValue(Profile::class.java)?.let { fetchedProfile ->
                                userProfile = fetchedProfile
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            // Handle error
                        }
                    })
                }
            }
        }

        HomeScreenContent(
            navController = navController,
            modifier = modifier,
            posts = posts, // Use filteredPosts here
            postViewModel = postViewModel,
            userProfiles = userProfiles,
            filterOption = filterSettings.filterOption,
            filterValue = "",
            searchQuery = filterSettings.searchQuery,
            onFilterOptionChanged = { newOption ->
                postViewModel.setFilterOption(newOption)
            },
            onSearchQueryChanged = { newQuery ->
                postViewModel.setSearchQuery(newQuery)
            },
            userId = userId,
            userProfile = userProfile,
            sortOption = filterSettings.sortOption,
            onSortOptionChanged = { newSortOption ->
                postViewModel.setSortOption(newSortOption)
            }
        )
    } else {
        // Show a loading indicator or placeholder
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFFBF00))
        }
    }
}

@Composable
fun HomeScreenContent(
    navController: NavController,
    modifier: Modifier = Modifier,
    posts: List<Post>,
    postViewModel: PostViewModel,
    userProfiles: Map<String, Profile>,
    filterOption: String,
    filterValue: String,
    searchQuery: String,
    onFilterOptionChanged: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    userId: String?,
    userProfile: Profile?,
    sortOption: String,
    onSortOptionChanged: (String) -> Unit
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var isVoiceOnly by remember { mutableStateOf(false) }


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
        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        CustomSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChanged,
            onSearch = { /* Logic handled via ViewModel */ }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Create Post, Filter, and Sort Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp), // Add padding to ensure spacing from screen edges
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Create Post Button
            Button(
                onClick = { navController.navigate("create_post") },
                border = BorderStroke(1.dp, Color(0xFFFF6F00)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                modifier = Modifier
                    .weight(0.9f)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Post",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp) // Adjust icon size for better fit
                )
            }

            Spacer(modifier = Modifier.width(8.dp)) // Add spacing between buttons

            Button(
                onClick = {
                    isVoiceOnly = !isVoiceOnly
                    postViewModel.setIsVoiceOnly(isVoiceOnly)
                },
                border = BorderStroke(1.dp, Color(0xFFFF6F00)),
                colors = ButtonDefaults.buttonColors(containerColor = if (isVoiceOnly) Color(0xFFFFBF00) else Color.Black),
            ) {
                Text(
                    text = if (isVoiceOnly) "Voice Only" else "All Posts",
                    color = if (isVoiceOnly) Color.Black else Color.White,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp)) // Add spacing between buttons

            // Filter Button
            Box {
                Button(
                    onClick = { showFilterMenu = !showFilterMenu },
                    border = BorderStroke(1.dp, Color(0xFFFF6F00)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Text(
                        text = filterOption.replaceFirstChar { it.uppercaseChar() },
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Filter options",
                        tint = Color(0xFFFF6F00)
                    )
                }

                // Dropdown Menu for Filter Options
                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false },
                ) {
                    val filterOptions = listOf("everyone", "matches", "my posts")
                    filterOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.replaceFirstChar { it.uppercaseChar() },
                                    color = if (option == filterOption) Color(0xFFFFBF00) else Color.White
                                )
                            },
                            onClick = {
                                onFilterOptionChanged(option)
                                showFilterMenu = false
                            },
                            leadingIcon = {
                                if (option == filterOption) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFFFFBF00)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Sort Button
            Box {
                IconButton(
                    onClick = { showSortMenu = !showSortMenu },
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort options",
                        tint = Color(0xFFFF6F00)
                    )
                }

                // Dropdown Menu for Sort Options
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    val sortOptions = listOf("Sort by Upvotes", "Sort by Downvotes", "No Sort")
                    sortOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    color = if (option == sortOption) Color(0xFFFFBF00) else Color.White,
                                    fontSize = 14.sp
                                )
                            },
                            onClick = {
                                onSortOptionChanged(option)
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (option == sortOption) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFFFFBF00)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Feed Section
        FeedSection(
            navController = navController,
            posts = posts,
            userId = userId,
            userProfile = userProfile,
            isPosting = false,
            postViewModel = postViewModel,
            userProfiles = userProfiles,
            onTagClick = { tag ->
                onSearchQueryChanged(tag)
            }
        )
    }
}



@Composable
fun FeedSection(
    navController: NavController,
    posts: List<Post>,
    userId: String?,
    userProfile: Profile?,
    isPosting: Boolean,
    postViewModel: PostViewModel,
    userProfiles: Map<String, Profile>,
    onTagClick: (String) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Swipe Refresh State
    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isRefreshing)

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            val currentUser = userId
            if (currentUser != null) {
                // Re-load feed filters from Firebase
                postViewModel.loadFiltersFromFirebase(currentUser)

                // Re-fetch user profiles (a new function you'll write)
                postViewModel.refreshUserProfiles()
            }

            delay(500)
            isRefreshing = false
        }
    }


    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = { isRefreshing = true }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
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
                        CircularProgressIndicator(color = Color(0xFFFF6F00))
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
                            val username = userProfiles[post.userId]?.name ?: "Unknown"
                            navController.navigate("dating_screen?initialQuery=$username")
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
                                Toast.makeText(context, "Post shared successfully!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { errorMsg ->
                                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    onSave = {
                        postViewModel.savePost(
                            postId = post.postId,
                            userId = userId ?: "",
                            onSuccess = {
                                Toast.makeText(context, "Post saved successfully!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = {
                            }
                        )
                    },
                    onComment = { commentText ->
                        // Comment logic using ViewModel
                        val comment = Comment(
                            commentId = UUID.randomUUID().toString(), // Generate a unique ID
                            userId = userId ?: "",
                            username = userProfile?.username.toString(),
                            commentText = commentText,
                            timestamp = ServerValue.TIMESTAMP
                        )
                        postViewModel.addComment(
                            postId = post.postId,
                            comment = comment,
                            onSuccess = {
                                Toast.makeText(context, "Comment added!", Toast.LENGTH_SHORT).show()
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
                                // Handle post deletion success
                            },
                            onFailure = {
                                // Handle post deletion failure
                            }
                        )
                    },
                    onReport = { postToReport ->
                        // Implement report logic, possibly calling PostViewModel's reportPost
                        postViewModel.reportPost(
                            postId = postToReport.postId,
                            reporterId = userId ?: "",
                            onSuccess = {
                                // Handle post report success
                            },
                            onFailure = {
                                // Handle post report failure
                            }
                        )
                    },
                    postViewModel = postViewModel,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedItem(
    post: Post,
    postViewModel: PostViewModel,
    userProfile: Profile?,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onUserClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onComment: (String) -> Unit,
    currentUserId: String,
    onDelete: (Post) -> Unit,
    onReport: (Post) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    var mediaDuration by remember { mutableStateOf(0L) }

    val hapticFeedback = LocalHapticFeedback.current

    // Animation states
    var showUpvoteAnimation by remember { mutableStateOf(false) }
    var showDownvoteAnimation by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }


    // Gesture detector for double-tap and long-press
    val gestureDetector = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = {
                // Trigger haptic feedback
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                // Trigger upvote action and show animation
                onUpvote()
                showUpvoteAnimation = true
            },
            onTap = {
                // Trigger haptic feedback
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                // Trigger downvote action and show animation
                onDownvote()
                showDownvoteAnimation = true

            }
        )
    }

    // Hide upvote animation after a delay
    LaunchedEffect(showUpvoteAnimation) {
        if (showUpvoteAnimation) {
            delay(500)
            showUpvoteAnimation = false
        }
    }

    // Hide downvote animation after a delay
    LaunchedEffect(showDownvoteAnimation) {
        if (showDownvoteAnimation) {
            delay(500)
            showDownvoteAnimation = false
        }
    }


    val dynamicFontSize = when {
        screenWidth < 360.dp -> 12.sp
        screenWidth < 600.dp -> 13.sp // Reduced font size
        else -> 14.sp
    }
    val dynamicPadding = when {
        screenWidth < 360.dp -> 4.dp   // Reduced padding
        screenWidth < 600.dp -> 6.dp
        else -> 8.dp
    }

    var showCommentsDialog by remember { mutableStateOf(false) }

    // State for voice post playback
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Annotate post content based on formatting markers
    val annotatedText = buildFormattedText(post.contentText ?: "")

    // Calculate user age from DOB
    val userAge = userProfile?.dob?.let { calculateAge(it) }


    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center // Center-align the card within the Box
    ) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .shadow(4.dp, RoundedCornerShape(2.dp))
            .then(gestureDetector),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(2.dp, getLevelBorderColor(userProfile?.averageRating ?: 0.0)) // Dynamic border color
    ) {
        Column(modifier = Modifier.padding(dynamicPadding)) {
            // User Info Row with Delete/Report button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onUserClick() }
                    .padding(start = 8.dp, bottom = dynamicPadding)
            ) {
                // User profile picture
                if (userProfile?.profilepicUrl != null) {
                    AsyncImage(
                        model = userProfile.profilepicUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(if (screenWidth < 360.dp) 32.dp else 40.dp)
                            .clip(CircleShape)
                            .background(Color.Gray)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Gray)
                    )
                }
                Spacer(modifier = Modifier.width(dynamicPadding))
                Column {
                    Row {
                        Text(
                            text = userProfile?.username.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Light,
                            fontSize = dynamicFontSize
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    RatingBar(
                        rating = userProfile?.averageRating ?: 0.0,
                        ratingCount = userProfile?.numberOfRatings ?: 0 // Pass the number of ratings
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                var moreOptionsExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.width(IntrinsicSize.Max)) {
                    IconButton(onClick = { moreOptionsExpanded = !moreOptionsExpanded }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = moreOptionsExpanded,
                        onDismissRequest = { moreOptionsExpanded = false }
                    ) {
                        if (post.userId == currentUserId) {
                            // The user's own post
                            DropdownMenuItem(
                                text = { Text("Edit Post", color = Color.White) },
                                onClick = {
                                    moreOptionsExpanded = false
                                    // Implement edit logic or navigate to an edit screen
                                    // For example:
                                    // navController.navigate("edit_post/${post.postId}")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Post", color = Color.White) },
                                onClick = {
                                    moreOptionsExpanded = false
                                    onDelete(post)
                                }
                            )
                        } else {
                            // Another user's post
                            DropdownMenuItem(
                                text = { Text("Report Post", color = Color.White) },
                                onClick = {
                                    moreOptionsExpanded = false
                                    onReport(post)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hide from feed", color = Color.White) },
                                onClick = {
                                    moreOptionsExpanded = false
                                    // Implement hide from feed logic here
                                    // Possibly call a function in postViewModel to update user's feed preferences
                                }
                            )
                        }
                    }
                }

            }


            // Post Content (Formatted Text)
            if (!post.contentText.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))

                val collapsedCharLimit = 700
                val isTextOverflowing = annotatedText.length > collapsedCharLimit

                val displayText = if (isExpanded || !isTextOverflowing) {
                    annotatedText
                } else {
                    // Truncate the text and add ellipsis
                    buildAnnotatedString {
                        append(annotatedText.subSequence(0, collapsedCharLimit))
                        append("...")  // Indicate that text is truncated
                    }
                }

                // Text Content
                Text(
                    text = displayText,
                    color = Color.White,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)
                )

                // "See more" Text
                if (isTextOverflowing && !isExpanded) {
                    Text(
                        text = "See more",
                        color = Color.LightGray,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .clickable { isExpanded = true }
                            .padding(start = 8.dp, bottom = 4.dp)
                    )
                }
            }



            // Media Content - Photo, Video, Voice
            if (post.mediaType != null && post.mediaUrl != null) {
                val context = LocalContext.current // Get the context once outside
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    when (post.mediaType) {
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
                                                // Play using caching logic
                                                playVoice(context, post.mediaUrl ?: "") { player ->
                                                    mediaPlayer = player
                                                    isPlaying = true

                                                    // Fetch and set the duration for the media
                                                    mediaDuration = player.duration.toLong()

                                                    // Set completion listener to stop playback once done
                                                    mediaPlayer?.setOnCompletionListener {
                                                        isPlaying = false
                                                        playbackProgress = 0f
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color(0xFFFFBF00),
                                            modifier = Modifier.size(70.dp)
                                        )
                                    }

                                    // Progress bar for voice playback
                                    LinearProgressIndicator(
                                        progress = playbackProgress,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 16.dp),
                                        color = Color(0xFFFFBF00),
                                        trackColor = Color.White
                                    )

                                    // Duration label
                                    Text(
                                        text = formatDuration(mediaDuration),
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

// Place time/distance first
            Spacer(modifier = Modifier.width(4.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.padding(horizontal = 8.dp) // Add horizontal padding
            ) {
                Text(
                    text = formatRelativeTime(post.getTimestampLong()),
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.weight(1f)) // Pushes distance to the right
            }
            Spacer(modifier = Modifier.height(4.dp))

// Now place tags below time/distance
            if (post.userTags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp) // Add horizontal padding
                ) {
                    post.userTags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .background(
                                    Color.Black,
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    BorderStroke(1.dp, Color(0xFFFF6F00)),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                modifier = Modifier.clickable { onTagClick(tag) }
                            )
                        }
                    }
                }
            }


            // Sharing, Upvote/Downvote, and Comment Section
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
                    // Add Save Icon
                    IconButton(onClick = { onSave() }) {
                        Icon(
                            Icons.Default.BookmarkBorder,  // Bookmark or save icon
                            contentDescription = "Save Post",
                            tint = Color.White
                        )
                    }
                }

                // Upvote and Downvote Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onUpvote) {
                            Icon(
                                Icons.Default.ThumbUpOffAlt,
                                contentDescription = "Upvote",
                                tint = Color(0xFFFFBF00)
                            )
                        }
                        Text(
                            text = "${post.upvotes}",
                            color = Color(0xFFFFBF00),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDownvote) {
                            Icon(
                                Icons.Default.ThumbDownOffAlt,
                                contentDescription = "Downvote",
                                tint = Color(0xFFFF6F00)
                            )
                        }
                        Text(
                            text = "${post.downvotes}",
                            color = Color(0xFFFF6F00),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Comment button icon to show/hide comment section
                    IconButton(onClick = { showCommentsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Comment,
                            contentDescription = "Show Comments",
                            tint = Color.White
                        )
                    }
                }
            }
            if (post.comments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "View Comments (${post.comments.size})",
                    color = Color.White,
                    modifier = Modifier
                        .clickable {
                        showCommentsDialog = true
                    }
                        .padding(start = 8.dp) // Add start padding
                    ,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }


            // In FeedItem, when showing the CommentsDialog:
            if (showCommentsDialog) {
                CommentsDialog(
                    post = post,
                    onDismiss = { showCommentsDialog = false },
                    onUpvoteComment = { commentId ->
                        postViewModel.upvoteComment(
                            postId = post.postId,
                            commentId = commentId,
                            userId = currentUserId,
                            onSuccess = {
                                // Handle success
                            },
                            onFailure = {
                                // Handle failure
                            }
                        )
                    },
                    onDownvoteComment = { commentId ->
                        postViewModel.downvoteComment(
                            postId = post.postId,
                            commentId = commentId,
                            userId = currentUserId,
                            onSuccess = {
                                // Handle success
                            },
                            onFailure = {
                                // Handle failure
                            }
                        )
                    },
                    onComment = { commentText ->
                        // Handle text comment submission
                        val comment = Comment(
                            commentId = UUID.randomUUID().toString(),
                            userId = currentUserId,
                            username = userProfile?.username.toString(),
                            commentText = commentText,
                            timestamp = ServerValue.TIMESTAMP
                        )
                        postViewModel.addComment(
                            postId = post.postId,
                            comment = comment,
                            onSuccess = {
                                // Show success message or update UI
                            },
                            onFailure = {
                            }
                        )
                    },
                    onVoiceComment = { voiceUri ->
                        // Handle voice comment submission
                        handleAddVoiceComment(
                            postId = post.postId,
                            voiceUri = voiceUri,
                            userId = currentUserId,
                            username = userProfile?.username.toString(),
                            onSuccess = {
                                // Show success message or update UI
                            },
                            onFailure = {
                            }
                        )
                    }
                )
            }
        }
    }
        // Overlay the Icon when showUpvoteAnimation or showDownvoteAnimation is true
        if (showUpvoteAnimation) {
            Icon(
                imageVector = Icons.Default.ThumbUpOffAlt,
                contentDescription = null,
                tint = Color(0xFFFFBF00),
                modifier = Modifier.size(100.dp)
            )
        }

        if (showDownvoteAnimation) {
            Icon(
                imageVector = Icons.Default.ThumbDownOffAlt,
                contentDescription = null,
                tint = Color(0xFFFF6F00),
                modifier = Modifier.size(100.dp)
            )
        }
    }

    // Removed the initialization logic for voice posts here

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

fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val months = days / 30
    val years = days / 365

    return when {
        seconds < 60 -> "${seconds}s ago"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 30 -> "${days}d ago"
        months < 12 -> "${months}mo ago"
        else -> "${years}y ago"
    }
}

fun formatDuration(durationMs: Long): String {
    val minutes = (durationMs / 1000) / 60
    val seconds = (durationMs / 1000) % 60
    return String.format("%d:%02d", minutes, seconds)
}

@Composable
fun CommentCard(
    comment: Comment,
    onUpvoteComment: (String) -> Unit,
    onDownvoteComment: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = comment.username,
                    color = Color(0xFFFFBF00),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = formatRelativeTime(comment.getCommentTimestamp()),
                    color = Color(0xFFFFBF00),
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Check if it's a voice comment or text comment
            if (comment.mediaUrl != null) {
                var isCommentPlaying by remember { mutableStateOf(false) }
                var commentMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
                var commentPlaybackProgress by remember { mutableStateOf(0f) }
                val context = LocalContext.current

                VoiceCommentPlayer(
                    mediaUrl = comment.mediaUrl,
                    isPlaying = isCommentPlaying,
                    onPlayToggle = {
                        if (isCommentPlaying) {
                            commentMediaPlayer?.pause()
                            isCommentPlaying = false
                        } else {
                            playVoice(context, comment.mediaUrl) { player ->
                                commentMediaPlayer = player
                                isCommentPlaying = true
                                commentMediaPlayer?.setOnCompletionListener {
                                    isCommentPlaying = false
                                    commentPlaybackProgress = 0f
                                }
                            }
                        }
                    },
                    progress = commentPlaybackProgress,
                    duration = commentMediaPlayer?.duration?.toLong() ?: 0L
                )
            } else {
                Text(
                    text = comment.commentText,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                IconButton(onClick = { onUpvoteComment(comment.commentId) }) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Upvote Comment",
                        tint = Color(0xFFFFBF00)
                    )
                }
                Text(text = "${comment.upvotes}", color = Color.White)
                IconButton(onClick = { onDownvoteComment(comment.commentId) }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Downvote Comment",
                        tint = Color(0xFFFF6F00)
                    )
                }
                Text(text = "${comment.downvotes}", color = Color.White)
            }
        }
    }
}

fun playLocalVoice(context: Context, voiceUri: Uri, onPlay: (MediaPlayer) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(context, voiceUri)
            prepare()
        }
        withContext(Dispatchers.Main) {
            mediaPlayer.start()
            onPlay(mediaPlayer)
        }
    }
}


@Composable
fun CommentsDialog(
    post: Post,
    onDismiss: () -> Unit,
    onUpvoteComment: (String) -> Unit,
    onDownvoteComment: (String) -> Unit,
    onComment: (String) -> Unit,
    onVoiceComment: (Uri) -> Unit
) {
    // State variables
    var sortOption by remember { mutableStateOf("No Sort") }
    var showSortMenu by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf(TextFieldValue("")) }

    // Voice recording states
    var isRecording by remember { mutableStateOf(false) }
    var recorder: MediaRecorder? by remember { mutableStateOf(null) }
    var recordFile: File? by remember { mutableStateOf(null) }
    var recordedVoiceUri by remember { mutableStateOf<Uri?>(null) }
    val maxDurationMs = 60 * 1000 // 1 minute
    var recordingTimeLeft by remember { mutableStateOf(maxDurationMs) }

    // Playback states for recorded voice note
    var isRecordingPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var recordedMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val context = LocalContext.current

    // Permission launcher for RECORD_AUDIO
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isRecording = true
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required to record audio.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val sortedCommentsList by produceState<List<Comment>>(
        initialValue = emptyList(),
        key1 = post.comments,
        key2 = sortOption
    ) {
        value = withContext(Dispatchers.IO) {
            post.comments.values.sortedWith(
                when (sortOption) {
                    "Sort by Upvotes" -> compareByDescending<Comment> { it.upvotes }
                    "Sort by Downvotes" -> compareByDescending<Comment> { it.downvotes }
                    "No Sort" -> compareByDescending<Comment> { it.getCommentTimestamp() }
                    else -> compareByDescending<Comment> { it.getCommentTimestamp() }
                }
            )
        }
    }

    // After a new comment, scroll to top (reverseLayout = true)
    val listState = rememberLazyListState()
    LaunchedEffect(sortedCommentsList.size) {
        if (sortedCommentsList.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Handle recording logic
    LaunchedEffect(isRecording) {
        if (isRecording) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    withContext(Dispatchers.IO) {
                        recorder?.release()
                        recorder = null

                        recordFile =
                            File(context.cacheDir, "voice_comment_${System.currentTimeMillis()}.aac")

                        recorder = MediaRecorder().apply {
                            setAudioSource(MediaRecorder.AudioSource.MIC)
                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            setOutputFile(recordFile?.absolutePath)
                            setMaxDuration(maxDurationMs)
                            prepare()
                            start()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        recordingTimeLeft = maxDurationMs
                    }

                    // Start countdown timer
                    while (isRecording && recordingTimeLeft > 0) {
                        delay(1000)
                        withContext(Dispatchers.Main) {
                            recordingTimeLeft -= 1000
                        }
                    }

                    // Stop if time up
                    if (isRecording && recordingTimeLeft <= 0) {
                        withContext(Dispatchers.Main) {
                            isRecording = false
                        }
                    }

                } catch (e: Exception) {
                    Log.e("VoiceComment", "Recording error: ${e.message}")
                    withContext(Dispatchers.Main) {
                        isRecording = false
                        Toast.makeText(
                            context,
                            "Recording failed: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    withContext(Dispatchers.IO) {
                        recorder?.release()
                        recorder = null
                    }
                }
            } else {
                isRecording = false
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            try {
                withContext(Dispatchers.IO) {
                    recorder?.apply {
                        stop()
                        release()
                    }
                    recorder = null
                }
                val fileUri = recordFile?.let { Uri.fromFile(it) }
                withContext(Dispatchers.Main) {
                    if (fileUri != null) {
                        recordedVoiceUri = fileUri
                    }
                    recordingTimeLeft = maxDurationMs
                }
            } catch (e: Exception) {
                Log.e("VoiceComment", "Stop recording error: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                withContext(Dispatchers.IO) {
                    recorder?.release()
                    recorder = null
                }
            }
        }
    }

    // Handle playback of recorded voice note
    LaunchedEffect(isRecordingPlaying) {
        if (isRecordingPlaying && recordedMediaPlayer != null) {
            while (isRecordingPlaying && recordedMediaPlayer?.isPlaying == true) {
                delay(500L)
                val current = recordedMediaPlayer?.currentPosition ?: 0
                val duration = recordedMediaPlayer?.duration ?: 1
                playbackProgress = current.toFloat() / duration.toFloat()
            }
        } else {
            playbackProgress = 0f
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Add imePadding() to respect the keyboard and ensure input row stays visible
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .imePadding() // ensures we move content up when keyboard appears
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter)
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Comments",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = sortOption,
                            color = Color(0xFFFFBF00),
                            modifier = Modifier.clickable { showSortMenu = !showSortMenu }
                        )
                        IconButton(
                            onClick = { showSortMenu = !showSortMenu }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort options",
                                tint = Color(0xFFFF6F00)
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            val sortOptions = listOf("No Sort", "Sort by Upvotes", "Sort by Downvotes")
                            sortOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option,
                                            color = if (option == sortOption) Color(0xFFFFBF00) else Color(0xFFFF6F00)
                                        )
                                    },
                                    onClick = {
                                        sortOption = option
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (option == sortOption) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFFFFBF00)
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onDismiss
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFFF6F00)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (sortedCommentsList.isEmpty()) {
                    Text(
                        "No comments yet.",
                        color = Color.White,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    // Comments list (reverseLayout = true)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f), // Take available space for comments
                        state = listState
                    ) {
                        items(sortedCommentsList) { comment ->
                            CommentCard(
                                comment = comment,
                                onUpvoteComment = onUpvoteComment,
                                onDownvoteComment = onDownvoteComment
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // If recording or recorded voice present, show that UI above input row
                if (isRecording) {
                    Text(
                        text = "Recording... Time left: ${recordingTimeLeft / 1000}s",
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else if (recordedVoiceUri != null) {
                    // Show playback controls and submit/delete for recorded voice
                    VoiceCommentPlayer(
                        mediaUrl = recordedVoiceUri.toString(),
                        isPlaying = isRecordingPlaying,
                        onPlayToggle = {
                            if (isRecordingPlaying) {
                                recordedMediaPlayer?.pause()
                                isRecordingPlaying = false
                            } else {
                                playLocalVoice(context, recordedVoiceUri!!) { player ->
                                    recordedMediaPlayer = player
                                    isRecordingPlaying = true
                                    recordedMediaPlayer?.setOnCompletionListener {
                                        isRecordingPlaying = false
                                        playbackProgress = 0f
                                    }
                                }
                            }
                        },
                        progress = playbackProgress,
                        duration = recordedMediaPlayer?.duration?.toLong() ?: 0L
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                recordedVoiceUri = null
                                recordFile = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Delete", color = Color.White)
                        }
                        Button(
                            onClick = {
                                onVoiceComment(recordedVoiceUri!!)
                                recordedVoiceUri = null
                                recordFile = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFBF00))
                        ) {
                            Text("Submit Voice Comment", color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Comment input row with mic icon on the left
                // Add more bottom padding to lift it higher above screen bottom
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 64.dp), // Increased bottom padding to lift input row higher
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mic / Stop icon on the left of the text field
                    IconButton(onClick = {
                        if (isRecording) {
                            // Stop recording
                            isRecording = false
                        } else {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                // Start recording
                                isRecording = true
                                // Clear text and recordedVoiceUri since starting fresh recording
                                commentText = TextFieldValue("")
                                recordedVoiceUri = null
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Record",
                            tint = Color(0xFFFFBF00)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text("Add a comment...", color = Color.Gray) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF6F00),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color(0xFFFF6F00)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (commentText.text.isNotBlank()) {
                                onComment(commentText.text)
                                commentText = TextFieldValue("")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                    ) {
                        Text("Submit", color = Color.White)
                    }
                }
            }
        }
    }
}





@Composable
fun VoiceCommentPlayer(
    mediaUrl: String,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    progress: Float,
    duration: Long
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onPlayToggle() }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color(0xFFFF6F00)
            )
        }
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            color = Color(0xFFFFBF00),
            trackColor = Color.Gray
        )

        // Display duration
        Text(
            text = formatDuration(duration),
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}



fun getCachedVoiceFile(context: Context, voiceUrl: String): File? {
    val cacheDir = File(context.cacheDir, "voice_cache")
    val fileName = voiceUrl.hashCode().toString() + ".aac"
    val cachedFile = File(cacheDir, fileName)

    return if (cachedFile.exists()) cachedFile else null
}

fun cacheVoiceFile(context: Context, voiceUrl: String, onDownloadComplete: (File?) -> Unit) {
    val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(voiceUrl)
    val cacheDir = File(context.cacheDir, "voice_cache")
    if (!cacheDir.exists()) {
        cacheDir.mkdirs()
    }

    val localFile = File(cacheDir, voiceUrl.hashCode().toString() + ".aac")
    storageRef.getFile(localFile).addOnSuccessListener {
        onDownloadComplete(localFile)
    }.addOnFailureListener {
        Log.e("VoiceDownload", "Failed to download voice file: ${it.message}")
        onDownloadComplete(null)
    }
}


fun playVoice(context: Context, voiceUrl: String, onPlay: (MediaPlayer) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        val cachedFile = getCachedVoiceFile(context, voiceUrl)

        if (cachedFile != null) {
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(cachedFile.absolutePath)
                prepare()
            }
            withContext(Dispatchers.Main) {
                mediaPlayer.start()
                onPlay(mediaPlayer)
            }
        } else {
            cacheVoiceFile(context, voiceUrl) { downloadedFile ->
                if (downloadedFile != null) {
                    val mediaPlayer = MediaPlayer().apply {
                        setDataSource(downloadedFile.absolutePath)
                        prepare()
                    }
                    CoroutineScope(Dispatchers.Main).launch {
                        mediaPlayer.start()
                        onPlay(mediaPlayer)
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Failed to play voice note.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}


// Define a function to handle voice comment addition
fun handleAddVoiceComment(
    postId: String,
    voiceUri: Uri,
    userId: String,
    username: String,
    database: DatabaseReference = FirebaseDatabase.getInstance().getReference("posts"),
    storage: FirebaseStorage = FirebaseStorage.getInstance(),
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) {
    // Create a storage reference to upload the voice file
    val voiceRef = storage.reference.child("voice_comments/$postId/${voiceUri.lastPathSegment}")

    // Upload the voice file to Firebase Storage
    voiceRef.putFile(voiceUri)
        .addOnSuccessListener { taskSnapshot ->
            // Get the download URL for the uploaded voice file
            voiceRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                // Prepare the comment data with the download URL
                val commentId = database.child(postId).child("comments").push().key
                if (commentId == null) {
                    onFailure("Failed to generate comment ID.")
                    return@addOnSuccessListener
                }

                val commentData = mapOf(
                    "commentId" to commentId,
                    "userId" to userId,
                    "username" to username,
                    "mediaUrl" to downloadUrl.toString(),
                    "upvotes" to 0,
                    "downvotes" to 0,
                    "timestamp" to ServerValue.TIMESTAMP // Capture the server-side timestamp
                )

                // Save the comment data to the database
                database.child(postId).child("comments").child(commentId)
                    .setValue(commentData)
                    .addOnSuccessListener {
                        onSuccess()
                    }
                    .addOnFailureListener { exception ->
                        onFailure(exception.message ?: "Failed to add comment to database.")
                    }
            }.addOnFailureListener { exception ->
                onFailure(exception.message ?: "Failed to get download URL.")
            }
        }
        .addOnFailureListener { exception ->
            onFailure(exception.message ?: "Voice file upload failed.")
        }
}

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
    val sdf = SimpleDateFormat("MMM dd, yyyy, hh:mm:ss", Locale.getDefault())
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
        placeholder = { Text("Search by name or tags", color = Color.Gray, fontSize = 12.sp) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        textStyle = LocalTextStyle.current.copy(color = Color.White),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFFFBF00),
            unfocusedBorderColor = Color.Gray,
            cursorColor = Color(0xFFFF6F00)
        )
    )
}