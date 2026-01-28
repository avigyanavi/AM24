@file:androidx.annotation.OptIn(UnstableApi::class)
@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.net.Uri
import android.media.MediaPlayer
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import coil.request.ImageRequest
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.Button
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.max
@Composable
fun ExploreScreen(
    postViewModel: PostViewModel,
    profileViewModel: ProfileViewModel,
    modifier: Modifier = Modifier
) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    val filtersLoaded by postViewModel.filtersLoaded.collectAsState()
    val isInitialFeedLoading by postViewModel.isInitialFeedLoading.collectAsState()
    val posts by postViewModel.filteredPosts.collectAsState()
    val hasMorePosts by postViewModel.hasMorePosts.collectAsState()
    val isLoadingMore by postViewModel.isLoadingMore.collectAsState()
    val feedErrorMessage by postViewModel.feedErrorMessage.collectAsState()
    val currentUserId by postViewModel.currentUserIdFlow.collectAsState(initial = null)
    val currentUserProfile by profileViewModel.currentUserProfile.collectAsState()

    LaunchedEffect(userId) {
        postViewModel.setCurrentUserId(userId)
        if (userId == null) {
            postViewModel.refreshPosts()
            return@LaunchedEffect
        }
        profileViewModel.fetchCurrentUserProfile()
        if (!postViewModel.filtersLoaded.value) {
            postViewModel.loadFiltersFromFirebase(userId)
        }
        if (postViewModel.postsLoaded.value) {
            postViewModel.resumeFeed()
        } else {
            postViewModel.refreshPosts()
        }
    }

    val explorePosts = remember(posts) {
        posts.filter {
            it.mediaType == "voice" ||
                    !it.mediaUrl.isNullOrBlank() ||
                    !it.contentText.isNullOrBlank()
        }
    }
    val pageSize = postViewModel.feedPageSize
    var pageIndex by rememberSaveable { mutableStateOf(0) }
    var pendingNext by remember { mutableStateOf(false) }
    val pagedPosts = remember(explorePosts, pageSize) {
        if (explorePosts.isEmpty()) listOf(emptyList()) else explorePosts.chunked(pageSize)
    }
    val pageCount = max(1, pagedPosts.size)
    val pagePosts = pagedPosts.getOrNull(pageIndex) ?: explorePosts

    LaunchedEffect(pagedPosts.size, pendingNext) {
        if (pendingNext && pagedPosts.size - 1 > pageIndex) {
            pageIndex = pagedPosts.lastIndex
            pendingNext = false
        }
        val safeIndex = pageIndex.coerceIn(0, pagedPosts.lastIndex)
        if (pageIndex != safeIndex) {
            pageIndex = safeIndex
        }
    }

    var selectedPostId by remember { mutableStateOf<String?>(null) }
    var queuedPostIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var commentsPostId by remember { mutableStateOf<String?>(null) }
    var commentDraft by remember { mutableStateOf("") }
    var isSendingComment by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        if (!filtersLoaded || isInitialFeedLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (feedErrorMessage != null && explorePosts.isEmpty() && filtersLoaded && !isInitialFeedLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = feedErrorMessage ?: "",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (explorePosts.isEmpty() && filtersLoaded && !isInitialFeedLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.explore_empty_prompt),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            ExploreGrid(
                posts = pagePosts,
                pageIndex = pageIndex,
                pageCount = pageCount,
                canGoBack = pageIndex > 0,
                canGoNext = pageIndex < pageCount - 1 || hasMorePosts,
                isLoadingMore = isLoadingMore,
                onBackPage = { if (pageIndex > 0) pageIndex-- },
                onNextPage = {
                    if (pageIndex < pageCount - 1) {
                        pageIndex++
                    } else if (hasMorePosts && !isLoadingMore) {
                        pendingNext = true
                        postViewModel.loadMorePosts()
                    }
                },
                onPostClick = { index ->
                    queuedPostIds = pagePosts.map { it.postId }
                    selectedPostId = pagePosts.getOrNull(index)?.postId
                }
            )
        }

        selectedPostId?.let { postId ->
            val queuedPosts = remember(pagePosts, queuedPostIds) {
                queuedPostIds.mapNotNull { id -> pagePosts.firstOrNull { it.postId == id } }
            }
            ExploreMediaQueueDialog(
                posts = queuedPosts,
                initialPostId = postId,
                currentUserId = currentUserId,
                onClose = { selectedPostId = null },
                onLike = { targetPostId ->
                    currentUserId?.let { uid ->
                        postViewModel.upvotePost(targetPostId, uid, {}, {})
                    }
                },
                onComments = { targetPostId ->
                    commentsPostId = targetPostId
                    scope.launch { sheetState.show() }
                }
            )
        }

        val commentsPost = commentsPostId?.let { id -> posts.firstOrNull { it.postId == id } }
        if (commentsPost != null) {
            LaunchedEffect(commentsPost.postId) {
                commentDraft = ""
                isSendingComment = false
            }
            ModalBottomSheet(
                onDismissRequest = {
                    scope.launch { sheetState.hide() }
                    commentsPostId = null
                },
                sheetState = sheetState
            ) {
                ExploreCommentsSheet(
                    post = commentsPost,
                    draft = commentDraft,
                    isSending = isSendingComment,
                    onDraftChange = { commentDraft = it },
                    onSend = {
                        val authorId = currentUserId
                        val profile = currentUserProfile
                        if (authorId != null && profile != null) {
                            val trimmed = commentDraft.trim()
                            if (trimmed.isNotBlank()) {
                                isSendingComment = true
                                val comment = Comment(
                                    commentId = "",
                                    userId = authorId,
                                    username = profile.username,
                                    commentText = trimmed,
                                    timestamp = ServerValue.TIMESTAMP
                                )
                                postViewModel.addComment(
                                    postId = commentsPost.postId,
                                    comment = comment,
                                    onSuccess = {
                                        isSendingComment = false
                                        commentDraft = ""
                                    },
                                    onFailure = {
                                        isSendingComment = false
                                    }
                                )
                            }
                        }
                    },
                    onClose = {
                        scope.launch { sheetState.hide() }
                        commentsPostId = null
                    }
                )
            }
        }
    }
}

@Composable
private fun ExploreGrid(
    posts: List<Post>,
    pageIndex: Int,
    pageCount: Int,
    canGoBack: Boolean,
    canGoNext: Boolean,
    isLoadingMore: Boolean,
    onBackPage: () -> Unit,
    onNextPage: () -> Unit,
    onPostClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val gridImageSizePx = remember(configuration, density) {
        val sizeDp = configuration.screenWidthDp.dp / 3
        with(density) { sizeDp.roundToPx() }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp)
    ) {
        itemsIndexed(posts) { index, post ->
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onPostClick(index) },
                contentAlignment = Alignment.Center
            ) {
                when {
                    post.mediaType == "voice" -> {
                        ExploreVoiceGridTile(post = post)
                    }
                    !post.mediaUrl.isNullOrBlank() -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(post.mediaThumb?.takeIf { it.isNotBlank() } ?: post.mediaUrl)
                                .size(gridImageSizePx)
                                .build(),
                            contentDescription = stringResource(R.string.explore_media_grid_desc),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        if (post.mediaType == "video") {
                            Surface(
                                color = Color(0x88000000),
                                shape = CircleShape,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.explore_video_badge_desc),
                                    tint = Color.White,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                    !post.contentText.isNullOrBlank() -> {
                        ExploreTextGridTile(post = post)
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = stringResource(R.string.explore_text_badge_desc),
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        if (posts.isNotEmpty() || isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onBackPage,
                            enabled = canGoBack
                        ) {
                            Text(stringResource(R.string.back))
                        }
                        Text(
                            text = stringResource(
                                R.string.page_of,
                                pageIndex + 1,
                                max(1, pageCount)
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = onNextPage,
                            enabled = canGoNext && !isLoadingMore
                        ) {
                            Text(stringResource(R.string.next_page))
                        }
                    }
                    if (isLoadingMore) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreMediaQueueDialog(
    posts: List<Post>,
    initialPostId: String,
    currentUserId: String?,
    onClose: () -> Unit,
    onLike: (String) -> Unit,
    onComments: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidthPx = remember(configuration, density) {
        with(density) { configuration.screenWidthDp.dp.roundToPx() }
    }
    val screenHeightPx = remember(configuration, density) {
        with(density) { configuration.screenHeightDp.dp.roundToPx() }
    }

    val initialIndex = remember(posts, initialPostId) {
        posts.indexOfFirst { it.postId == initialPostId }.coerceAtLeast(0)
    }

    LaunchedEffect(initialIndex, posts.size) {
        if (posts.isNotEmpty()) {
            listState.scrollToItem(initialIndex)
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(posts) { _, post ->
                    ExploreQueueItem(
                        post = post,
                        screenHeight = screenHeight,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        currentUserId = currentUserId,
                        onLike = { onLike(post.postId) },
                        onComments = { onComments(post.postId) }
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color(0x66000000), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
            }
        }
    }
}

@Composable
private fun ExploreTextGridTile(post: Post) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = Icons.Default.TextFields,
            contentDescription = stringResource(R.string.explore_text_badge_desc),
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = post.contentText.orEmpty(),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExploreVoiceGridTile(post: Post) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = stringResource(R.string.explore_voice_badge_desc),
            tint = Color(0xFFFFDB00),
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = stringResource(R.string.explore_voice_label),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
        if (post.voiceDuration != null) {
            Text(
                text = formatDuration(post.voiceDuration.toLong() * 1000L),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ExploreQueueItem(
    post: Post,
    screenHeight: Dp,
    screenWidthPx: Int,
    screenHeightPx: Int,
    currentUserId: String?,
    onLike: () -> Unit,
    onComments: () -> Unit
) {
    val context = LocalContext.current
    val mediaUrl = post.mediaUrl
    val scope = rememberCoroutineScope()
    var showHeart by remember { mutableStateOf(false) }
    var heartJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val heartScale by animateFloatAsState(
        targetValue = if (showHeart) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "heartScale"
    )
    val heartAlpha by animateFloatAsState(
        targetValue = if (showHeart) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "heartAlpha"
    )
    val triggerLikeAnimation: () -> Unit = {
        onLike()
        heartJob?.cancel()
        showHeart = true
        heartJob = scope.launch {
            delay(700)
            showHeart = false
        }
    }

    val isUpvoted = currentUserId != null && post.upvotedUsers.containsKey(currentUserId)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(screenHeight)
            .background(Color.Black)
            .pointerInput(post.postId) {
                detectTapGestures(onDoubleTap = { triggerLikeAnimation() })
            }
    ) {
        when {
            post.mediaType == "video" && !mediaUrl.isNullOrBlank() -> {
                ExploreVideoPlayerFullscreen(
                    url = mediaUrl,
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    onDoubleTap = triggerLikeAnimation
                )
            }
            post.mediaType == "voice" && !mediaUrl.isNullOrBlank() -> {
                ExploreVoicePlayer(
                    mediaUrl = mediaUrl,
                    onDoubleTap = triggerLikeAnimation
                )
            }
            !mediaUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(post.mediaThumb?.takeIf { it.isNotBlank() } ?: mediaUrl)
                        .size(screenWidthPx, screenHeightPx)
                        .build(),
                    contentDescription = stringResource(R.string.explore_media_queue_desc),
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(post.postId) {
                            detectTapGestures(onDoubleTap = { triggerLikeAnimation() })
                        },
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (post.contentText.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.explore_text_empty_label),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = post.contentText.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = Color(0xFFE74C3C),
            modifier = Modifier
                .align(Alignment.Center)
                .size(96.dp)
                .graphicsLayer {
                    scaleX = heartScale
                    scaleY = heartScale
                    alpha = heartAlpha
                }
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onLike) {
                    Icon(
                        if (isUpvoted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_like),
                        tint = if (isUpvoted) Color(0xFFE74C3C) else Color.White
                    )
                }
                Text(
                    text = post.upvotes.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onComments) {
                    Icon(
                        Icons.Default.Comment,
                        contentDescription = stringResource(R.string.cd_comments),
                        tint = Color.White
                    )
                }
                Text(
                    text = post.totalComments.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        val caption = post.contentText?.takeIf { it.isNotBlank() }
        if (caption != null) {
            Surface(
                color = Color(0x66000000),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    caption,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ExploreVoicePlayer(
    mediaUrl: String,
    onDoubleTap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var durationMs by remember { mutableStateOf(0L) }

    LaunchedEffect(isPlaying, mediaPlayer) {
        while (isPlaying) {
            val player = mediaPlayer
            if (player != null && durationMs > 0) {
                playbackProgress = player.currentPosition.toFloat() / durationMs.toFloat()
            }
            delay(200)
        }
    }

    DisposableEffect(mediaPlayer) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onDoubleTap) {
                if (onDoubleTap != null) {
                    detectTapGestures(onDoubleTap = { onDoubleTap() })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        mediaPlayer?.pause()
                        isPlaying = false
                    } else {
                        playVoice(context, mediaUrl) { player ->
                            mediaPlayer = player
                            durationMs = player.duration.toLong()
                            isPlaying = true
                            player.setOnCompletionListener {
                                isPlaying = false
                                playbackProgress = 0f
                            }
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.cd_voice_play_pause),
                    tint = Color(0xFFFFDB00),
                    modifier = Modifier.size(64.dp)
                )
            }
            LinearProgressIndicator(
                progress = playbackProgress,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(6.dp),
                color = Color(0xFFFFDB00),
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            Text(
                text = formatDuration(durationMs),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ExploreCommentsSheet(
    post: Post,
    draft: String,
    isSending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit
) {
    val comments = remember(post) {
        post.comments.values.sortedByDescending { it.getCommentTimestamp() }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.comments_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(comments) { _, comment ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            text = comment.username.ifBlank { stringResource(R.string.default_username) },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(comment.commentText)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = { Text(stringResource(R.string.write_comment_label)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(10.dp))
            IconButton(
                onClick = onSend,
                enabled = !isSending && draft.trim().isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = stringResource(R.string.cd_send_comment))
            }
        }

        Spacer(Modifier.height(6.dp))
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
@Composable
private fun ExploreVideoPlayerFullscreen(
    url: String,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onDoubleTap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val cache = remember { VideoCacheProvider.getInstance(context) }
    val cacheFactory = remember {
        val upstream = DefaultDataSource.Factory(context)
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
    }

    val player = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                prepare()
                playWhenReady = true
            }
    }

    androidx.compose.runtime.DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                    this.resizeMode = resizeMode
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            }
        )
        if (onDoubleTap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(onDoubleTap) {
                        detectTapGestures(onDoubleTap = { onDoubleTap() })
                    }
            )
        }
    }
}