// ExploreActivity.kt

package com.am24.am24

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberImagePainter
import com.google.firebase.database.*
import com.am24.am24.ui.theme.AppTheme
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import coil.compose.AsyncImagePainter

class ExploreActivity : ComponentActivity() {

    private lateinit var db: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = FirebaseDatabase.getInstance("https://am-twentyfour-default-rtdb.firebaseio.com/").reference
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        setContent {
            AppTheme {
                var posts by remember { mutableStateOf<List<Post>>(emptyList()) }

                // Function to refresh the posts
                fun refreshPosts() {
                    fetchPostsFromDatabase { postList ->
                        posts = postList
                    }
                }

                fetchPostsFromDatabase { postList ->
                    posts = postList
                }

                ExploreScreen(
                    posts = posts,
                    onRefreshExplore = { refreshPosts() },
                    onNavigateToProfile = {
                        startActivity(Intent(this, ProfileActivity::class.java))
                    },
                    onNavigateToBlog = {
                        startActivity(Intent(this, BlogExploreActivity::class.java))
                    },
                    onNavigateToFullScreen = { post ->
                        val intent = Intent(this, FullScreenPostsActivity::class.java).apply {
                            putExtra("postType", post.postType)
                            putExtra("mediaUrl", post.mediaUrl)
                            putExtra("postId", post.postId)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    private fun fetchPostsFromDatabase(onPostsFetched: (List<Post>) -> Unit) {
        db.child("posts")
            .orderByChild("timeOfPost")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val postList = mutableListOf<Post>()
                    for (postSnapshot in dataSnapshot.children) {
                        val post = postSnapshot.getValue(Post::class.java)
                        post?.let {
                            if (post.postType != PostType.TEXT.name) { // Exclude blog posts (TEXT type)
                                postList.add(it)
                            }
                        }
                    }
                    onPostsFetched(postList)
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    // Handle database error if necessary
                }
            })
    }
}


@Composable
fun ExploreScreen(
    posts: List<Post>,
    onRefreshExplore: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToBlog: () -> Unit,
    onNavigateToFullScreen: (Post) -> Unit
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onRefreshExplore,
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                containerColor = Color(0xFF6200EE),
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add", tint = Color.White)
            }
        },
        topBar = {
            TopNavigationMenu(
                onNavigateToExplore = onRefreshExplore,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToBlog = onNavigateToBlog
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (posts.isEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(5) {
                        PostSkeleton()
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(posts) { post ->
                        PostItem(post = post, onDoubleTap = { onNavigateToFullScreen(post) })
                    }
                }
            }
        }
    }
}

@Composable
fun PostItem(post: Post, onDoubleTap: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() }
                )
            }
    ) {
        Text(
            text = post.username ?: "Anonymous",
            fontSize = 18.sp,
            fontStyle = FontStyle.Italic,
            letterSpacing = 0.5.sp
        )
        Text(text = "Posted at: ${formatTimestamp(post.timeOfPost)}", fontSize = 14.sp)
        Text(text = "Location: ${post.locationTag.joinToString(", ") ?: "Not specified"}", fontSize = 14.sp)

        if (post.tags.isNotEmpty()) {
            Text(text = "Tags: ${post.tags.joinToString(", ")}", fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (post.postType) {
            PostType.PHOTO.name -> {
                Text(text = "Photo Post", fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                post.mediaUrl?.let { mediaUrl ->
                    DisplayImage(mediaUrl = mediaUrl)
                }
            }
            PostType.VIDEO.name -> {
                Text(text = "Video Post", fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                post.mediaUrl?.let { mediaUrl ->
                    VideoPlayer(videoUrl = mediaUrl)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
    }
}

@Composable
fun VideoPlayer(videoUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = false // Don't autoplay
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_DESTROY -> exoPlayer.release()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9 / 16f) // Adjust aspect ratio for portrait videos
    )
}

@Composable
fun PostSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(20.dp)
                .background(Color.Gray.copy(alpha = 0.3f))
        )
        Spacer(modifier = Modifier.height(8.dp))
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

@Composable
fun DisplayImage(mediaUrl: String) {
    val painter = rememberImagePainter(data = mediaUrl)
    var isImageLoading by remember { mutableStateOf(true) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        if (isImageLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color.Gray.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(12.dp)) // Rounded corners for a softer look
                .background(Color.White),
            contentScale = ContentScale.Crop
        )

        LaunchedEffect(painter) {
            if (painter.state is AsyncImagePainter.State.Success) {
                isImageLoading = false
            }
        }
    }
}

