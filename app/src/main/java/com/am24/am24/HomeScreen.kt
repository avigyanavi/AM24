// HomeScreen.kt - Part 1

@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.am24.am24

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.firebase.geofire.GeoFire
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
fun HomeScreen(navController: NavController, modifier: Modifier = Modifier) {
    val postViewModel: PostViewModel = viewModel()
    val posts by postViewModel.posts.collectAsState()
    val userProfiles by postViewModel.userProfiles.collectAsState()

    var filterOption by remember { mutableStateOf("recent") }
    var filterValue by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf("None") }
    var showSortMenu by remember { mutableStateOf(false) }

    val userId = FirebaseAuth.getInstance().currentUser?.uid

    var userProfile by remember { mutableStateOf<Profile?>(null) }

    //geofire reference
    val geoFireRef = FirebaseDatabase.getInstance().getReference("geoFireLocations")
    val geoFire = remember { GeoFire(geoFireRef) } // Initialize GeoFire with the correct Firebase reference

    // Fetch user's profile
    LaunchedEffect(userId) {
        if (userId != null) {
            withContext(Dispatchers.IO) {
                val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
                userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        // Switch back to Main dispatcher to update UI
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


    // Fetch posts when filters change
    LaunchedEffect(filterOption, filterValue, searchQuery, sortOption) {
        postViewModel.fetchPosts(filterOption, filterValue, searchQuery, sortOption, userId)
    }

    HomeScreenContent(
        navController = navController,
        modifier = modifier,
        posts = posts,
        postViewModel = postViewModel,
        userProfiles = userProfiles,
        filterOption = filterOption,
        filterValue = filterValue,
        searchQuery = searchQuery,
        onFilterOptionChanged = { newOption ->
            filterOption = newOption
            filterValue = ""
        },
        onFilterValueChanged = { newValue ->
            filterValue = newValue
        },
        onSearchQueryChanged = { newQuery ->
            searchQuery = newQuery
        },
        userId = userId,
        userProfile = userProfile,
        sortOption = sortOption, // pass sortOption
        onSortOptionChanged = { newSortOption ->
            sortOption = newSortOption
        },
        showSortMenu = showSortMenu, // pass showSortMenu
        onShowSortMenuChange = { newShowSortMenuState ->
            showSortMenu = newShowSortMenuState
        },
        geoFire = geoFire,
    )
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
    onFilterValueChanged: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    userId: String?,
    userProfile: Profile?,
    sortOption: String, // Add sortOption parameter
    onSortOptionChanged: (String) -> Unit, // Add handler for sort option change
    showSortMenu: Boolean, // Add showSortMenu parameter
    onShowSortMenuChange: (Boolean) -> Unit, // Add handler for toggling sort menu visibility
    geoFire: GeoFire
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current


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
            onQueryChange = onSearchQueryChanged,
            onSearch = {
                // Do nothing, fetchPosts will be called via LaunchedEffect
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
            // Create Post Button (Slightly smaller)
            Button(
                onClick = { navController.navigate("create_post") },
                border = BorderStroke(1.dp, Color(0xFF00bf63)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                modifier = Modifier
                    .weight(0.9f)
                    .padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Post",
                    tint = Color(0xFF00bf63)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Post", color = Color(0xFF00bf63))
            }

            // Filter Button
            Box {
                Button(
                    onClick = { showFilterMenu = !showFilterMenu },
                    border = BorderStroke(1.dp, Color(0xFF00bf63)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Text(
                        text = filterOption.replaceFirstChar { it.uppercaseChar() },
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
                        "recent", "my posts",
                        "city", "age", "level", "gender", "high-school", "college"
                    )
                    filterOptions.filter { it != filterOption }.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.replaceFirstChar { it.uppercaseChar() },
                                    color = Color(0xFF00bf63)
                                )
                            },
                            onClick = {
                                onFilterOptionChanged(option)
                                showFilterMenu = false
                            }
                        )
                    }
                }
            }

            //Sort
            Box {// Sort Button
                IconButton(
                    onClick = { onShowSortMenuChange(!showSortMenu) },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort options",
                        tint = Color(0xFF00bf63)
                    )
                }

                // Sort Dropdown Menu
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { onShowSortMenuChange(false) },
                ) {
                    val sortOptions = listOf("Sort by Upvotes", "Sort by Downvotes", "No Sort")
                    sortOptions.filter { it != sortOption }.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    color = Color(0xFF00bf63)
                                )
                            },
                            onClick = {
                                onSortOptionChanged(option)
                                onShowSortMenuChange(false) // Close menu after selection
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
                                onFilterValueChanged(newValue)
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
                                        onFilterValueChanged(gender)
                                        expanded = false
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
                                onFilterValueChanged(newValue)
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
                        onValueChange = onFilterValueChanged,
                        label = {
                            Text(
                                "Enter ${filterOption.replaceFirstChar { it.uppercaseChar() }}",
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
            },
            geoFire = geoFire, // Pass GeoFire here
        )
    }
}

/**
 * Function to handle the fetched posts snapshot.
 */


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

// HomeScreen.kt - Part 2

@Composable
fun FeedSection(
    navController: NavController,
    posts: List<Post>,
    userId: String?,
    userProfile: Profile?,
    isPosting: Boolean,
    postViewModel: PostViewModel,
    userProfiles: Map<String, Profile>,
    onTagClick: (String) -> Unit,
    geoFire: GeoFire // Pass GeoFire here
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Swipe Refresh State
    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isRefreshing)

    // Launching coroutine for swipe refresh
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            postViewModel.fetchPosts(
                filterOption = "recent",
                filterValue = "",
                searchQuery = "",
                sortOption = "None",
                userId = userId
            )
            delay(1500) // Simulate refresh time
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
                            navController.navigate("profile/${post.userId}")
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
                            username = userProfile?.username ?: "Anonymous",
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
                    geoFire = geoFire // Pass GeoFire here
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
    geoFire: GeoFire // Add this to calculate distance
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    var mediaDuration by remember { mutableStateOf(0L) }

    var userDistance by remember { mutableStateOf<Float?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    // Animation states
    var showUpvoteAnimation by remember { mutableStateOf(false) }
    var showDownvoteAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(userProfile) {
        userProfile?.let {
            if (currentUserId.isNotEmpty() && it.userId.isNotEmpty()) {
                // Fetch and calculate distance
                calculateDistance(currentUserId, it.userId, geoFire) { distance ->
                    userDistance = distance
                }
            }
        }
    }
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
            onLongPress = {
                // Trigger haptic feedback
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                // Trigger downvote action and show animation
                onDownvote()
                showDownvoteAnimation = true

            }
        )
    }
    // Hide animation after a delay
    LaunchedEffect(Unit) {
        delay(1000)
        showDownvoteAnimation = false
    }

    // Hide animation after a delay
    LaunchedEffect(Unit) {
        delay(1000)
        showUpvoteAnimation = false
    }

    val dynamicFontSize = when {
        screenWidth < 360.dp -> 14.sp
        screenWidth < 600.dp -> 16.sp
        else -> 18.sp
    }
    val dynamicPadding = when {
        screenWidth < 360.dp -> 8.dp
        screenWidth < 600.dp -> 12.dp
        else -> 16.dp
    }

    var commentText by remember { mutableStateOf(TextFieldValue("")) }
    var showCommentsDialog by remember { mutableStateOf(false) }
    var showCommentSection by remember { mutableStateOf(false) }
    var recordedVoiceUri by remember { mutableStateOf<Uri?>(null) }

    // State for voice post playback
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isRecordingVoiceComment by remember { mutableStateOf(false) }

    // Annotate post content based on formatting markers
    val annotatedText = buildFormattedText(post.contentText ?: "")

    // Calculate user age from DOB
    val userAge = userProfile?.dob?.let { calculateAge(it) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(2.dp))
            .then(gestureDetector),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(2.dp, Color(0xFF00bf63))
    ) {
        Column(modifier = Modifier.padding(dynamicPadding)) {
            // User Info Row with Delete/Report button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onUserClick() }
                    .padding(bottom = dynamicPadding)
            ) {
                // User profile picture
                if (userProfile?.profilepicUrl != null) {
                    AsyncImage(
                        model = userProfile.profilepicUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(if (screenWidth < 360.dp) 40.dp else 48.dp)
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
                Spacer(modifier = Modifier.width(dynamicPadding))
                Column {
                    Row {
                        Text(
                            text = "${userProfile?.username ?: "Unavailable"}, ${userAge ?: "--"}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = dynamicFontSize
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    RatingBar(rating = userProfile?.rating ?: 0.0)
                    Text(
                        text = "Vibe Score: ${userProfile?.vibepoints}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Show Delete or Report button
                Box(modifier = Modifier.width(IntrinsicSize.Max)) {
                    if (post.userId == currentUserId) {
                        IconButton(onClick = { onDelete(post) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Post",
                                tint = Color(0xFF00bf63)
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

            // Hashtags Below Main Content
            if (post.userTags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    post.userTags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .background(
                                    Color.Black,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    BorderStroke(1.dp, Color(0xFF00bf63)),
                                    RoundedCornerShape(8.dp)
                                )
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

            Spacer(modifier = Modifier.width(12.dp))
            Row(
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = formatTimestamp(post.getPostTimestamp()),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.weight(1f)) // Flexible space to push the distance text to the right
                userDistance?.let {
                    Text(
                        text = "Distance: ${it.roundToInt()} km",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

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

            // Inside FeedItem where the comment section is displayed
            if (showCommentSection) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Hide the text comment input if recording a voice comment
                    if (!isRecordingVoiceComment) {
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
                    }

                    // Record Voice Comment Button
                    RecordVoiceCommentButton(
                        onVoiceRecorded = { voiceUri ->
                            recordedVoiceUri = voiceUri
                        },
                        onStartRecording = {
                            isRecordingVoiceComment = true // Start recording mode
                        },
                        onStopRecording = {
                        }
                    )

                    // Show recorded voice note before submission
                    recordedVoiceUri?.let { uri ->
                        Spacer(modifier = Modifier.height(8.dp))
                        // Add state for playback control
                        var isRecordingPlaying by remember { mutableStateOf(false) }
                        var playbackProgress by remember { mutableStateOf(0f) }
                        var recordedMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
                        val context = LocalContext.current // Get the context once outside

                        // Show playback controls for the recorded audio
                        VoiceCommentPlayer(
                            mediaUrl = uri.toString(),
                            isPlaying = isRecordingPlaying,
                            onPlayToggle = {
                                if (isRecordingPlaying) {
                                    recordedMediaPlayer?.pause()
                                    isRecordingPlaying = false
                                } else {
                                    playVoice(context, uri.toString()) { player ->
                                        recordedMediaPlayer = player
                                        isRecordingPlaying = true

                                        // Set completion listener to stop playback once done
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
                        // Submit and Delete buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    // Delete the recorded voice comment
                                    recordedVoiceUri = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Text("Delete", color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    handleAddVoiceComment(
                                        postId = post.postId,
                                        voiceUri = uri,
                                        userId = currentUserId,
                                        username = userProfile?.username ?: "Unknown User",
                                        onSuccess = {
                                            // Handle success, e.g., show a Toast or update the state to refresh the comments
                                            recordedVoiceUri = null // Reset after submitting
                                            isRecordingVoiceComment = false // Set to false only when the comment is successfully submitted
                                        },
                                        onFailure = {
                                            // Handle failure, e.g., show a Toast or log the error
                                        }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                            ) {
                                Text("Submit Voice Comment", color = Color.White)
                            }
                        }
                    }
                }
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
                            }
                        )
                    }
                )
            }


            Spacer(modifier = Modifier.height(8.dp))
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


fun formatDuration(durationMs: Long): String {
    val minutes = (durationMs / 1000) / 60
    val seconds = (durationMs / 1000) % 60
    return String.format("%d:%02d", minutes, seconds)
}


@Composable
fun CommentsDialog(
    post: Post,
    onDismiss: () -> Unit,
    onUpvoteComment: (String) -> Unit,
    onDownvoteComment: (String) -> Unit,
) {
    // Sort Option State
    var sortOption by remember { mutableStateOf("Recent") }
    var showSortMenu by remember { mutableStateOf(false) }

    // Sorted comments list based on the selected sort option
    val sortedCommentsList by produceState<List<Comment>>(initialValue = emptyList(), key1 = post.comments, key2 = sortOption) {
        value = withContext(Dispatchers.IO) {
            post.comments.values.sortedWith(
                when (sortOption) {
                    "Sort by Upvotes" -> compareByDescending<Comment> { it.upvotes }
                    "Sort by Downvotes" -> compareByDescending<Comment> { it.downvotes }
                    else -> compareByDescending<Comment> { it.getCommentTimestamp() }
                }
            )
        }
    }

    // Dialog box with extended size
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black,
            modifier = Modifier
                .fillMaxWidth(0.95f) // Set the width closer to the full screen width
                .fillMaxHeight(0.8f)
                .padding(8.dp) // Adjust padding for the dialog
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Title and Sort Button
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

                    // Sort Button similar to the HomeScreenContent
                    Box {
                        IconButton(
                            onClick = { showSortMenu = !showSortMenu }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort options",
                                tint = Color(0xFF00bf63)
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            val sortOptions = listOf("Sort by Upvotes", "Sort by Downvotes", "No Sort")
                            sortOptions.filter { it != sortOption }.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option,
                                            color = Color(0xFF00bf63)
                                        )
                                    },
                                    onClick = {
                                        sortOption = option
                                        showSortMenu = false // Close menu after selection
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Comments List
                if (sortedCommentsList.isEmpty()) {
                    Text("No comments yet.", color = Color.White)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxHeight(0.7f)) {
                        items(sortedCommentsList) { comment ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp), // Adjusted padding
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Comment Header
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = comment.username,
                                            color = Color(0xFF00bf63),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            text = formatTimestamp(comment.getCommentTimestamp()),
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Display voice comment if available
                                    if (comment.mediaUrl != null) {
                                        // Voice comment playback UI
                                        var isCommentPlaying by remember { mutableStateOf(false) }
                                        var commentMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
                                        var commentPlaybackProgress by remember { mutableStateOf(0f) }
                                        val context = LocalContext.current // Get the context once outside

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

                                                        // Set up completion listener to stop playback once done
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
                                        // Text Comment
                                        Text(
                                            text = comment.commentText,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }

                                    // Upvote/Downvote Section for Comment
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    ) {
                                        IconButton(onClick = { onUpvoteComment(comment.commentId) }) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = "Upvote Comment",
                                                tint = Color(0xFF00ff00)
                                            )
                                        }
                                        Text(text = "${comment.upvotes}", color = Color.White)
                                        IconButton(onClick = { onDownvoteComment(comment.commentId) }) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = "Downvote Comment",
                                                tint = Color(0xffff0000)
                                            )
                                        }
                                        Text(text = "${comment.downvotes}", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close Button
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))) {
                    Text("Close", color = Color.White)
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
                tint = Color(0xFF00bf63)
            )
        }
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            color = Color(0xFF00bf63),
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
fun RecordVoiceCommentButton(
    modifier: Modifier = Modifier,
    onVoiceRecorded: (Uri) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    val recorder = remember { mutableStateOf<MediaRecorder?>(null) }
    val maxDurationMs = 60 * 1000 // 1 minute

    var recordFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            try {
                recordFile = File(context.cacheDir, "voice_comment_${System.currentTimeMillis()}.aac")
                recorder.value = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(recordFile?.absolutePath)
                    setMaxDuration(maxDurationMs)
                    prepare()
                    start()
                }
                onStartRecording() // Notify that recording has started
            } catch (e: Exception) {
                Log.e("VoiceComment", "Recording error: ${e.message}")
                isRecording = false
                recorder.value?.release()
                recorder.value = null
                Toast.makeText(context, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            try {
                recorder.value?.apply {
                    stop()
                    release()
                }
                recorder.value = null
                val fileUri = recordFile?.let { Uri.fromFile(it) }
                if (fileUri != null) {
                    onVoiceRecorded(fileUri)
                } else {
                }
                onStopRecording() // Notify that recording has stopped
            } catch (e: Exception) {
                Log.e("VoiceComment", "Stop recording error: ${e.message}")
                recorder.value?.release()
                recorder.value = null
                Toast.makeText(context, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Button(
        onClick = { isRecording = !isRecording },
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRecording) Color.Red else Color(0xFF00bf63)
        )
    ) {
        Text(
            text = if (isRecording) "Stop Recording" else "Record Voice Comment",
            color = Color.White
        )
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