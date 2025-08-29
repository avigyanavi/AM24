// HomeScreen.kt - Part 1

@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.am24.am24

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Box
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
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
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import com.am24.am24.ComposeNativeAd
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.material.placeholder
import com.google.accompanist.placeholder.material.shimmer
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.request.ImageRequest
import com.am24.am24.util.TextureFullscreenVideoPlayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    postViewModel: PostViewModel,
    modifier: Modifier = Modifier
) {
    // Get the current user ID from FirebaseAuth.
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    var dailyLoginInfo by remember { mutableStateOf<DailyLoginInfo?>(null) }

    // Immediately update the PostViewModel with the current user ID.
    LaunchedEffect(userId) {
        postViewModel.setCurrentUserId(userId)
        if (userId != null) {
            postViewModel.loadFiltersFromFirebase(userId)
        }
        postViewModel.refreshPosts()
    }

    // Pause the feed when the user navigates away.
    DisposableEffect(Unit) {
        onDispose {
            postViewModel.pauseFeed()
        }
    }

    // Wait for filters to load.
    val filtersLoaded by postViewModel.filtersLoaded.collectAsState()
    if (!filtersLoaded) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFFF6F00))
        }
    } else {
        // Once loaded, collect posts and profiles.
        val posts by postViewModel.filteredPosts.collectAsState()
        Log.d("HomeScreen", "Filtered Posts Count in HomeScreen: ${posts.size}")

        val userProfiles by postViewModel.userProfiles.collectAsState()
        val filterSettings by postViewModel.filterSettings.collectAsState()
        var myMatches by remember { mutableStateOf<List<String>>(emptyList()) }

        // Fetch the current user's own Profile.
        var userProfile by remember { mutableStateOf<Profile?>(null) }
        LaunchedEffect(userId) {
            if (userId != null) {
                withContext(Dispatchers.IO) {
                    val userRef = FirebaseRefs.db.getReference("users").child(userId)
                    userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            snapshot.getValue(Profile::class.java)?.let { fetchedProfile ->
                                userProfile = fetchedProfile
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {
                            // Handle any error if needed.
                        }
                    })
                }
            }
        }

        // 2️⃣ Fetch once from “matches/$userId”
        LaunchedEffect(userId) {
            if (userId != null) {
                FirebaseRefs.db
                    .getReference("matches")
                    .child(userId)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            // Each child key is a matched user’s UID
                            myMatches = snapshot.children.mapNotNull { it.key }
                        }
                        override fun onCancelled(error: DatabaseError) { /* handle error */ }
                    })
            }
        }

        val isPremium = userProfile?.isPremium == true
        val isPlus = userProfile?.isPlus == true

        LaunchedEffect(isPremium, isPlus) {
            if (!isPremium && !isPlus) {
                dailyLoginInfo = checkDailyLoginReward()
            } else {
                dailyLoginInfo = null
            }
        }

        if (!isPremium && !isPlus) {
            dailyLoginInfo?.let { info ->
                val kupidxOrange = Color(0xFFFF6F00)
                AlertDialog(
                    onDismissRequest = { dailyLoginInfo = null },
                    confirmButton = {
                        TextButton(onClick = { dailyLoginInfo = null }) {
                            Text("OK", color = kupidxOrange)
                        }
                    },
                    title = { Text(stringResource(R.string.daily_login_title)) },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (i in 1..5) {
                                    val checked = i <= info.streak
                                    Icon(
                                        imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (checked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.daily_login_message, info.streak))
                            if (info.rewardHours > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.daily_login_plus_award, info.rewardHours))
                            }
                        }
                    }
                )
            }
        }

        // Show HomeScreenContent with the updated data.
        HomeScreenContent(
            navController = navController,
            modifier = modifier,
            posts = posts,
            postViewModel = postViewModel,
            userProfiles = userProfiles,
            matches      = myMatches,
            filterOption = filterSettings.filterOption,
            filterValue = "",  // if extra parameter needed
            searchQuery = filterSettings.searchQuery,
            onFilterOptionChanged = { newOption -> postViewModel.setFilterOption(newOption) },
            onSearchQueryChanged = { newQuery -> postViewModel.setSearchQuery(newQuery) },
            userId = userId,
            userProfile = userProfile,
            sortOption = filterSettings.sortOption,
            onSortOptionChanged = { newSortOption -> postViewModel.setSortOption(newSortOption) },
            listState = rememberLazyListState() // Pass listState for scroll control
        )
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun HomeScreenContent(
    navController: NavController,
    modifier: Modifier = Modifier,
    posts: List<Post>,
    postViewModel: PostViewModel,
    userProfiles: Map<String, Profile>,
    matches: List<String>,
    filterOption: String,
    filterValue: String,
    searchQuery: String,
    onFilterOptionChanged: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    userId: String?,
    userProfile: Profile?,
    sortOption: String,
    onSortOptionChanged: (String) -> Unit,
    listState: LazyListState // Added listState parameter
) {
    val profileViewModel: ProfileViewModel = viewModel()
    val isPremium by profileViewModel.isPremium.collectAsState(initial = false)
    val isPlus    by profileViewModel.isPlus   .collectAsState(initial = false)
    val showAds = !isPremium && !isPlus
    val focusManager = LocalFocusManager.current
    val feedTabs = listOf("everyone", "matches")
    var selectedTab by remember {                   // keeps UI and VM in sync
        mutableStateOf(if (filterOption == "matches") 1 else 0)
    }
    // ① collect your new savedPostIds flow
    val savedIds by postViewModel.savedPostIds.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { focusManager.clearFocus() }
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Only show the search bar when a tag search is active.
            if (searchQuery.isNotEmpty()) {
                CustomSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChanged,
                    onSearch = { /* No extra logic needed */ }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Black,
                contentColor = Color.White,          // label colour
                indicator = {}                    // we’ll draw our own border
            ) {
                feedTabs.forEachIndexed { index, option ->
                    val isSelected = selectedTab == index
                    Tab(
                        selected = isSelected,
                        onClick = {
                            selectedTab = index
                            onFilterOptionChanged(option)   // update ViewModel
                        },
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .border(
                                BorderStroke(1.dp, Color(0xFFFF6F00)),
                                RoundedCornerShape(12.dp)
                            )
                            .background(
                                if (isSelected) Color(0xFF2B2B2B) /* dark-grey */
                                else Color.Black,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = option.replaceFirstChar { it.uppercaseChar() },
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Feed Section.
            FeedSection(
                navController = navController,
                posts = posts,
                userId = userId,
                matches = matches,
                userProfile = userProfile,
                isPosting = false,
                postViewModel = postViewModel,
                userProfiles = userProfiles,
                onTagClick = { tag ->
                    // When a tag is clicked, update the search query so that the search bar appears.
                    onSearchQueryChanged(tag)
                },
                savedPostIds = savedIds,    // ← NEW
                listState = listState, // Pass listState to FeedSection
                showAds =  showAds
            )
        }
    }
}

@OptIn(ExperimentalWearMaterialApi::class)
@Composable
fun FeedSection(
    navController: NavController,
    posts: List<Post>,
    userId: String?,
    matches: List<String>,
    userProfile: Profile?,
    isPosting: Boolean,
    postViewModel: PostViewModel,
    userProfiles: Map<String, Profile>,
    onTagClick: (String) -> Unit,
    savedPostIds: Set<String>,           // ← NEW
    listState: LazyListState, // Added listState parameter
    showAds: Boolean
) {
    val context = LocalContext.current

    // Swipe Refresh State
    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isRefreshing)

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            val currentUser = userId
            if (currentUser != null) {
                // Re-load feed filters from Firebase
                postViewModel.refreshPosts()
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
            state = listState, // Use the passed listState
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (isPosting) {
                item {
                    Card(                                                    // skeleton card
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .height(200.dp)
                            .padding(vertical = 8.dp)
                            .placeholder(
                                visible = true,
                                highlight = PlaceholderHighlight.shimmer(),
                                shape = RoundedCornerShape(6.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                    ) {}
                }
            }

            itemsIndexed(posts) { index, post ->
                val profile = userProfiles[post.userId]
                val isSaved = savedPostIds.contains(post.postId)
                FeedItem(
                    post = post,
                    navController = navController,
                    userProfile = profile,
                    isSaved = isSaved,               // ← NEW
                    matches = matches,
                    userProfiles  = userProfiles,   // ← pass it through
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
                        // grab the Profile you passed in
                        val clickedProfile = userProfiles[post.userId]
                        when {
                            // your own profile → full edit/profile screen
                            post.userId == userId ->
                                navController.navigate("profile")

                            // mutual match → matchedProfile screen
                            matches.contains(post.userId) ->
                                navController.navigate("matchedUserProfile/${post.userId}")

                            // PRIVATE account → block navigation & show a toast
                            clickedProfile?.isPrivate == true -> {
                                Toast.makeText(
                                    context,
                                    "This account is private",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            // public non‐match → one-off preview
                            else ->
                                navController.navigate("previewUserProfile/${post.userId}")
                        }
                    },
                    onTagClick = { tag ->
                        onTagClick(tag)
                    },
                    onShare = {
                        // hand off the real matches list
                        postViewModel.sharePostWithMatches(
                            postId   = post.postId,
                            matches  = matches,
                            onSuccess= {},
                            onFailure= {}
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
                            username = userProfile?.username.orEmpty(),
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
                    currentUserProfile = userProfile,
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
                    postViewModel = postViewModel,
                )
                Spacer(modifier = Modifier.height(16.dp))
                // ─── Native ad every 5 items ───────────────────
                // ─── debug log + native ad every 5 items ─────────────────
                if (showAds && (index + 1) % 5 == 0) {
                    Log.d("HomeScreen", ">>> inserting native ad at index: $index")
                    ComposeNativeAd(
                        adUnitId  = AdUnitIds.native(context),
                        modifier  = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }

            // No more posts indicator
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(R.string.no_more_older_posts), color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedItem(
    post: Post,
    navController: NavController,
    isSaved: Boolean,                // ← NEW
    postViewModel: PostViewModel,
    userProfile: Profile?,
    matches: List<String>,
    userProfiles: Map<String, Profile>,   // ← add this
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onUserClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onComment: (String) -> Unit,
    currentUserId: String,
    currentUserProfile: Profile?,
    onDelete: (Post) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    var mediaDuration by remember { mutableStateOf(0L) }

    val hapticFeedback = LocalHapticFeedback.current

    // Animation states
    var showUpvoteAnimation by remember { mutableStateOf(false) }
    var showDownvoteAnimation by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    // Fallbacks when the author's profile hasn't been loaded yet
    val authorName = userProfile?.username ?: post.username
    val authorPicUrl = userProfile?.profilepicThumbnailUrl
        ?: userProfile?.profilepicUrl
        ?: post.profilepicUrl

    // Local vote state
    var localUpvotes by remember(post.postId) { mutableStateOf(post.upvotes) }
    var localDownvotes by remember(post.postId) { mutableStateOf(post.downvotes) }
    var hasUpvoted by remember(post.postId) {
        mutableStateOf(post.upvotedUsers[currentUserId] == true)
    }
    var hasDownvoted by remember(post.postId) {
        mutableStateOf(post.downvotedUsers[currentUserId] == true)
    }

    val handleUpvote = {
        if (hasUpvoted) {
            localUpvotes--
            hasUpvoted = false
        } else {
            localUpvotes++
            hasUpvoted = true
            if (hasDownvoted) {
                hasDownvoted = false
                localDownvotes--
            }
        }
        onUpvote()
    }

    val handleDownvote = {
        if (hasDownvoted) {
            localDownvotes--
            hasDownvoted = false
        } else {
            localDownvotes++
            hasDownvoted = true
            if (hasUpvoted) {
                hasUpvoted = false
                localUpvotes--
            }
        }
        onDownvote()
    }

    // Gesture detector for double-tap and long-press
    val gestureDetector = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = {
                // Trigger haptic feedback
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                // Trigger upvote action and show animation
                handleUpvote()
                showUpvoteAnimation = true
            },
            onLongPress = {
                // Trigger haptic feedback
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                // Trigger downvote action and show animation
                handleDownvote()
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
        screenWidth < 360.dp -> 14.sp
        screenWidth < 600.dp -> 16.sp // Reduced font size
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

    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason     by remember { mutableStateOf("") }

    // Annotate post content based on formatting markers
    val annotatedText = buildFormattedText(post.contentText ?: "")
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val ctx = LocalContext.current
    var showVideoDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center // Center-align the card within the Box
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .shadow(4.dp, RoundedCornerShape(2.dp))
                .then(gestureDetector),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
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
                    val placeholder = painterResource(R.drawable.local_placeholder)
                    val context = LocalContext.current
                    val model = authorPicUrl?.let { url ->
                        val pathKey = Uri.parse(url).path
                        ImageRequest.Builder(context)
                            .data(url)
                            .diskCacheKey(pathKey)
                            .memoryCacheKey(pathKey)
                            .build()
                    }
                    AsyncImage(
                        model = model,
                        contentDescription = "Profile Picture",
                        placeholder = placeholder,
                        error = placeholder,
                        modifier = Modifier
                            .size(if (screenWidth < 360.dp) 32.dp else 40.dp)
                            .clip(CircleShape)
                            .background(Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(dynamicPadding))
                    Column {
                        Row {
                            Text(
                                text = authorName,
                                color = Color.White,
                                fontWeight = FontWeight.Light,
                                fontSize = dynamicFontSize
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        RatingBar(
                            rating = userProfile?.averageRating ?: 0.0,
                            ratingCount = userProfile?.numberOfRatings ?: 0 // Pass the number of ratings
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    /* 1️⃣  NEW: grey “8 m ago” label */
                    Text(
                        text  = formatRelativeTime(post.getTimestampLong()),
                        color = Color(0xFFB0B0B0),               // light‑grey
                        fontSize = 8.sp,
                        modifier = Modifier.padding(end = 2.dp)
                    )

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
                                    text = { Text(stringResource(R.string.post_edit), color = Color.White) },
                                    onClick = {
                                        moreOptionsExpanded = false
                                        // Implement edit logic or navigate to an edit screen
                                        // For example:
                                        // navController.navigate("edit_post/${post.postId}")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.post_delete), color = Color.White) },
                                    onClick = {
                                        moreOptionsExpanded = false
                                        onDelete(post)
                                    }
                                )
                            } else {
                                // ─────────── replace your old onReport call ───────────
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.post_report), color = Color.White) },
                                    onClick = {
                                        moreOptionsExpanded = false
                                        showReportDialog    = true
                                    }
                                )
                            }
                        }
                    }

                }

                /* ---------- CHECK-IN CHIP ---------- */
                post.checkIn?.let { ci ->
                    Spacer(Modifier.height(4.dp))
                    AssistChip(
                        onClick = {
                            /* optional: open Maps */
                            val gmm = Uri.parse("geo:${ci.lat},${ci.lng}?q=${Uri.encode(ci.name)}")
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, gmm))
                        },
                        label = { Text(ci.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.Place, null) }
                    )
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
                        fontSize = 20.sp,
                        lineHeight = 20.sp,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)
                    )

                    // "See more" Text
                    if (isTextOverflowing && !isExpanded) {
                        Text(
                            text = stringResource(R.string.see_more),
                            color = Color.LightGray,
                            fontSize = 10.sp,
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
                    val ctx = LocalContext.current
                    Box(modifier = Modifier.fillMaxWidth()) {
                        when (post.mediaType) {
                            /* ---------- PHOTO ---------- */
                            "image", "photo" -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .then(
                                            if (postViewModel.canPlayMedia()) {
                                                Modifier.clickable {
                                                    postViewModel.recordMediaPlay()
                                                    // optional: open full-screen photo viewer here
                                                }
                                            } else {
                                                val ms =  stringResource(R.string.free_tier_media_limit_msg)
                                                Modifier
                                                    .blur(16.dp)
                                                    .clickable {
                                                        Toast.makeText(context, ms, Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                        )
                                ) {
                                    AsyncImage(
                                        model = post.mediaUrl ?: "",
                                        contentDescription = "Post photo",
                                        modifier = Modifier.matchParentSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            /* ---------- VIDEO ---------- */
                            "video" -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .then(
                                            if (postViewModel.canPlayMedia()) {
                                                Modifier.clickable {
                                                    postViewModel.recordMediaPlay()
                                                    showVideoDialog = true
                                                }
                                            } else {
                                                Modifier
                                                    .blur(16.dp)
                                                    .clickable {
                                                        Toast
                                                            .makeText(context, "Free tier allows only 5 media views per day", Toast.LENGTH_SHORT)
                                                            .show()
                                                    }
                                            }
                                        )
                                ) {
                                    AsyncImage(
                                        model = post.mediaThumb ?: post.mediaUrl,
                                        contentDescription = "Video thumbnail",
                                        modifier = Modifier.matchParentSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .align(Alignment.Center)
                                    )
                                }
                                if (showVideoDialog) {
                                    TextureFullscreenVideoPlayer(
                                        uri = remember(post.mediaUrl) { post.mediaUrl!!.toUri() },
                                        onDismiss = { showVideoDialog = false }
                                    )
                                }
                            }

                            /* ---------- VOICE ---------- */
                            "voice" -> {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // wrap in Box so blur affects only the button
                                        Box {
                                            val ms =  stringResource(R.string.free_tier_media_limit_msg)
                                            IconButton(
                                                onClick = {
                                                    if (!postViewModel.canPlayMedia()) {
                                                        Toast
                                                            .makeText(context, ms, Toast.LENGTH_SHORT)
                                                            .show()
                                                    } else {
                                                        postViewModel.recordMediaPlay()
                                                        if (isPlaying) {
                                                            mediaPlayer?.pause()
                                                            isPlaying = false
                                                        } else {
                                                            playVoice(context, post.mediaUrl ?: "") { player ->
                                                                mediaPlayer = player
                                                                isPlaying = true
                                                                mediaDuration = player.duration.toLong()
                                                                mediaPlayer?.setOnCompletionListener {
                                                                    isPlaying = false
                                                                    playbackProgress = 0f
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.then(
                                                    if (postViewModel.canPlayMedia()) Modifier else Modifier.blur(16.dp)
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Play/Pause",
                                                    tint = Color(0xFFFFDB00),
                                                    modifier = Modifier.size(70.dp)
                                                )
                                            }
                                        }

                                        // Progress bar for voice playback
                                        LinearProgressIndicator(
                                            progress = playbackProgress,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 16.dp),
                                            color = Color(0xFFFFDB00),
                                            trackColor = Color.White
                                        )

                                        // Duration label
                                        Text(
                                            text = formatDuration(mediaDuration),
                                            color = Color.Gray,
                                            fontSize = 8.sp,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
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

                var showShareDialog by remember { mutableStateOf(false) }
                var selectedMatch by remember { mutableStateOf<String?>(null) }
                val context = LocalContext.current


                // Sharing, Upvote/Downvote, and Comment Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row {
                        // Replace your current Share IconButton with this:
                        IconButton(onClick = { showShareDialog = true }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share), tint = Color.White)
                        }
                        // Add Save Icon
                        IconButton(onClick = { onSave() }) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = stringResource(R.string.save_post),
                                tint = Color.White
                            )
                        }
                    }

                    // When showShareDialog == true, render a dialog of all matches:
                    if (showShareDialog) {
                        AlertDialog(
                            onDismissRequest = { showShareDialog = false },
                            title = { Text(stringResource(R.string.share_with), color = Color.White) },
                            text = {
                                LazyColumn {
                                    items(matches) { matchUid ->
                                        val profile = userProfiles[matchUid]
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedMatch = matchUid }
                                                .padding(vertical = 8.dp, horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = (matchUid == selectedMatch),
                                                onClick = { selectedMatch = matchUid },
                                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFDB00))
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = profile?.username.orEmpty().takeIf { it.isNotEmpty() } ?: matchUid,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    selectedMatch?.let { uid ->
                                        postViewModel.sharePostWithMatches(
                                            postId   = post.postId,
                                            matches  = listOf(uid),
                                            onSuccess= {},
                                            onFailure= {}
                                        )
                                    }
                                    showShareDialog = false
                                }) {
                                    Text(stringResource(R.string.send), color = Color(0xFFFFDB00))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showShareDialog = false }) {
                                    Text(stringResource(R.string.cancel), color = Color.Gray)
                                }
                            },
                            containerColor    = Color(0xFF1A1A1A),
                            titleContentColor = Color.White,
                            textContentColor  = Color.White
                        )
                    }


                    // Upvote and Downvote Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = handleUpvote) {
                                Icon(
                                    imageVector = if (hasUpvoted) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                    contentDescription = "Upvote",
                                    tint = if (hasUpvoted) Color(0xFFFFDB00) else Color.Gray
                                )
                            }
                            Text(
                                text = "${localUpvotes}",
                                color = Color(0xFFFFDB00),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = handleDownvote) {
                                Icon(
                                    imageVector = if (hasDownvoted) Icons.Default.ThumbDown else Icons.Default.ThumbDownOffAlt,
                                    contentDescription = "Downvote",
                                    tint = if (hasDownvoted) Color(0xFFFF6F00) else Color.Gray
                                )
                            }
                            Text(
                                text = "${localDownvotes}",
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
                        text = stringResource(R.string.view_comments_count, post.comments.size),
                        color = Color.White,
                        modifier = Modifier
                            .clickable {
                                showCommentsDialog = true
                            }
                            .padding(start = 8.dp) // Add start padding
                        ,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }


                // In FeedItem, when showing the CommentsDialog:
                if (showCommentsDialog) {
                    CommentsDialog(
                        post = post,
                        navController = navController,
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
                                username = currentUserProfile?.username.orEmpty(),
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
                                username = currentUserProfile?.username.orEmpty(),
                                onSuccess = {
                                    // Show success message or update UI
                                },
                                onFailure = {
                                }
                            )
                        }
                    )
                }


                // ─────────── THE REPORT POST DIALOG ───────────
                if (showReportDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showReportDialog = false
                            reportReason    = ""
                        },
                        title = { Text(stringResource(R.string.post_report)) },
                        text = {
                            Column {
                                Text(stringResource(R.string.report_reason_prompt))
                                Spacer(Modifier.height(8.dp))
                                TextField(
                                    value = reportReason,
                                    onValueChange = { reportReason = it },
                                    placeholder = { Text(stringResource(R.string.report_reason_placeholder)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    colors = TextFieldDefaults.textFieldColors(
                                        containerColor       = Color.DarkGray,
                                        focusedTextColor     = Color.White,
                                        unfocusedTextColor   = Color.White,
                                        focusedIndicatorColor   = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor             = KupidxOrange
                                    )
                                )
                            }
                        },
                        confirmButton = {
                            val msg = stringResource(R.string.reason_required)
                            val msg2 = stringResource(R.string.reported_blocked_unmatched)
                            Button(
                                onClick = {
                                    if (reportReason.isBlank()) {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    scope.launch {
                                        try {
                                            postViewModel.reportAndBlock(
                                                postId         = post.postId,
                                                reporterId     = currentUserId,
                                                reportedUserId = post.userId,
                                                reason         = reportReason
                                            )
                                            Toast.makeText(context, msg2, Toast.LENGTH_SHORT).show()
                                            showReportDialog = false
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                 Text(stringResource(R.string.submit), color = Color.White)
                            }
                        },
                        dismissButton = {
                            Button(
                                onClick = {
                                    showReportDialog = false
                                    reportReason    = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                            ) {
                                Text(stringResource(R.string.cancel), color = Color.White)
                            }
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
                tint = Color(0xFFFFDB00),
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

/* ------------------------------------------------------------------ */
/*  Helpers for image & video – put these anywhere in HomeScreen.kt   */
/* ------------------------------------------------------------------ */

@Composable
fun PostPhoto(url: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = "Post photo",
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)                 // square thumbnail
            .clip(RoundedCornerShape(6.dp)),
        contentScale = ContentScale.Crop
    )
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
    onDownvoteComment: (String) -> Unit,
    onCommentClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onCommentClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF424242))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = comment.username,
                    color = Color(0xFFFFDB00),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = formatRelativeTime(comment.getCommentTimestamp()),
                    color = Color(0xFFFFDB00),
                    fontSize = 8.sp
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
                    fontSize = 11.sp,
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
                        tint = Color(0xFFFFDB00)
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
    navController: NavController,
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
                        fontSize = 12.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = sortOption,
                            color = Color(0xFFFFDB00),
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
                                            color = if (option == sortOption) Color(0xFFFFDB00) else Color(0xFFFF6F00)
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
                                                tint = Color(0xFFFFDB00)
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
                        stringResource(R.string.no_comments_yet),
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
                                onDownvoteComment = onDownvoteComment,
                                onCommentClick = {
                                    navController.navigate("previewUserProfile/${comment.userId}")
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // If recording or recorded voice present, show that UI above input row
                if (isRecording) {
                    Text(stringResource(R.string.recording_time_left, recordingTimeLeft / 1000), color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
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
                            Text(stringResource(R.string.delete), color = Color.White)
                        }
                        Button(
                            onClick = {
                                onVoiceComment(recordedVoiceUri!!)
                                recordedVoiceUri = null
                                recordFile = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFDB00))
                        ) {
                            Text(stringResource(R.string.submit_voice_comment), color = Color.White)
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
                            tint = Color(0xFFFFDB00)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text(stringResource(R.string.add_a_comment), color = Color.Gray) },
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
                        Text(stringResource(R.string.submit), color = Color.White)
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
            color = Color(0xFFFFDB00),
            trackColor = Color.Gray
        )

        // Display duration
        Text(
            text = formatDuration(duration),
            color = Color.Gray,
            fontSize = 8.sp,
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
    database: DatabaseReference = FirebaseRefs.db.getReference("posts"),
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
        placeholder = { Text(stringResource(R.string.search_tags_placeholder), color = Color.Gray, fontSize = 11.sp) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        textStyle = LocalTextStyle.current.copy(color = Color.White),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFFFDB00),
            unfocusedBorderColor = Color.Gray,
            cursorColor = Color(0xFFFF6F00)
        )
    )
}