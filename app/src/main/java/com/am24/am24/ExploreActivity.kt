package com.am24.am24

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberImagePainter
import com.google.firebase.database.*
import com.am24.am24.ui.theme.AppTheme
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImagePainter

class ExploreActivity : ComponentActivity() {

    private lateinit var db: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = FirebaseDatabase.getInstance("https://am-twentyfour-default-rtdb.firebaseio.com/").reference

        setContent {
            AppTheme {
                var posts by remember { mutableStateOf<List<Post>>(emptyList()) }

                fetchPostsFromDatabase { postList ->
                    posts = postList
                }

                ExploreScreen(
                    posts = posts,
                    onNavigateToCreatePost = {
                        startActivity(Intent(this, CreatePostActivity::class.java))
                    },
                    onNavigateToProfile = {
                        startActivity(Intent(this, ProfileActivity::class.java))
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
                        post?.let { postList.add(it) }
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
    onNavigateToCreatePost: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    Scaffold(
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = onNavigateToCreatePost) {
                        Text(text = "Create Post")
                    }
                    Button(onClick = onNavigateToProfile) {
                        Text(text = "Profile")
                    }
                }
            }
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
                    items(5) { // Show 5 skeleton items while loading
                        PostSkeleton()
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(posts) { post ->
                        PostItem(post = post)
                    }
                }
            }
        }
    }
}

@Composable
fun DisplayImage(mediaUrl: String) {
    val painter = rememberImagePainter(data = mediaUrl)
    var imageSize by remember { mutableStateOf(Size.Zero) }
    var isImageLoading by remember { mutableStateOf(true) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }

        if (isImageLoading) {
            // Skeleton loader while waiting for the image to load
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
                .height(
                    if (imageSize != Size.Zero) {
                        // Adjust height based on the image aspect ratio
                        with(density) {
                            val imageHeightInPx = (screenWidthPx * imageSize.height / imageSize.width)
                            imageHeightInPx.toDp()
                        }
                    } else {
                        300.dp // Default height while loading
                    }
                ),
            contentScale = ContentScale.Fit // Maintain the aspect ratio of the image without cropping
        )

        // Detect when the image has loaded successfully and extract its size
        LaunchedEffect(painter) {
            if (painter.state is AsyncImagePainter.State.Success) {
                isImageLoading = false
                val painterIntrinsicSize = painter.intrinsicSize
                if (painterIntrinsicSize != Size.Zero) {
                    imageSize = painterIntrinsicSize
                }
            }
        }
    }
}


@Composable
fun PostItem(post: Post) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            text = post.username ?: "Anonymous",
            fontSize = 18.sp,
            fontStyle = FontStyle.Italic
        )
        Text(text = "Posted at: ${formatTimestamp(post.timeOfPost)}", fontSize = 14.sp)
        Text(text = "Location: ${post.locationTag?.joinToString(", ") ?: "Not specified"}", fontSize = 14.sp)

        // Display tags if available
        if (post.tags.isNotEmpty()) {
            Text(text = "Tags: ${post.tags.joinToString(", ")}", fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (post.postType) {
            PostType.TEXT.name -> {
                Text(text = "Blog Post", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(text = post.caption ?: "", fontSize = 14.sp)
            }
            PostType.PHOTO.name -> {
                Text(text = "Photo Post", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                post.mediaUrl?.let { mediaUrl ->
                    DisplayImage(mediaUrl = mediaUrl)
                }
            }
            PostType.VIDEO.name -> {
                Text(text = "Video Post", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
fun VideoPlayer(videoUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = false // Don't play until user interacts
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.playWhenReady = false
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

    // Adjust the player to the screen's width and crop extra spaces
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
            .aspectRatio(9 / 16f) // For portrait orientation (adjust if needed)
    )
}
