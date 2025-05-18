// CreatePostScreen.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.widget.Toast
import androidx.compose.foundation.lazy.items
import com.am24.am24.LocationManager as AM24LocationManager
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
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import coil.compose.rememberAsyncImagePainter
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.IOException
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.PopupProperties
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay


@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun CreatePostScreen(
    navController: NavController,
    postViewModel: PostViewModel
) {
    val context  = LocalContext.current
    val userId   = FirebaseAuth.getInstance().currentUser?.uid
    var isPremium by remember { mutableStateOf(false) }

    // quick check — replace with your own premium flag
    LaunchedEffect(userId) {
        isPremium = FirebaseRefs.db
            .getReference("users").child(userId ?: "")
            .child("premiumUntil").get().await().getValue(Long::class.java)
            ?.let { it > System.currentTimeMillis() } ?: false
    }
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
        content = {  innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize(),
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
                if (!isPremium) {           // <---- premium gate
                    Spacer(Modifier.height(16.dp))
                    PostTypeButton(icon = Icons.Default.Photo, label = "Image Post", onClick = {
                        navController.navigate("create_post/image")
                    })
                    Spacer(Modifier.height(16.dp))
                    PostTypeButton(icon = Icons.Default.Videocam, label = "Video Post", onClick = {
                        navController.navigate("create_post/video")
                    })
                }
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
        val userRef = FirebaseRefs.db.getReference("users").child(userId)
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

/* --------------- small helper reused by both screens --------------- */
@Composable
private fun otfColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Color(0xFFFFA500),
    unfocusedBorderColor = Color.Gray,
    cursorColor          = Color(0xFFFFA500)
)


/* ---------------------------------------------------------------- */
/*                   MAIN “IMAGE POST” COMPOSABLE                   */
/* ---------------------------------------------------------------- */
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePostComposable(
    navController: NavController,
    postViewModel : PostViewModel
) {
    val ctx      = LocalContext.current
    val scope    = rememberCoroutineScope()
    val userId   = FirebaseAuth.getInstance().currentUser?.uid ?: return

    /* ——— UI state ——— */
    var username       by remember { mutableStateOf("") }
    var imageUri       by remember { mutableStateOf<Uri?>(null) }
    var caption        by remember { mutableStateOf("") }
    var userTags       by remember { mutableStateOf("") }

    /* location-search state */
    var placeQuery     by remember { mutableStateOf("") }
    var placeResults   by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var selectedPlace  by remember { mutableStateOf<PlaceResult?>(null) }
    var searching      by remember { mutableStateOf(false) }
    val menuExpanded   = placeResults.isNotEmpty()

    /* fetch display name once */
    LaunchedEffect(Unit) { username = fetchUsernameById(userId) ?: "Anonymous" }

    /* camera / gallery helpers */
    fun tmpImg() = File.createTempFile("img_${System.currentTimeMillis()}", ".jpg", ctx.cacheDir)
        .apply { deleteOnExit() }

    val camFile = remember { tmpImg() }
    val camUri  = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", camFile)

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
        if (it) imageUri = camUri
    }
    val pickGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let { uri -> imageUri = uri }
    }

    /* ---------------- Scaffold ---------------- */
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Image Post", color = Color.White) },
                navigationIcon = {
                    IconButton({ navController.popBackStack() }) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (imageUri == null) {
                            Toast.makeText(ctx, "Select an image first", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val tags = userTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                        postViewModel.createMediaPost(
                            userId   = userId,
                            username = username,
                            mediaUri = imageUri!!,
                            mediaType= "image",
                            caption  = caption,
                            userTags = tags,
                            checkIn  = selectedPlace?.let {
                                CheckIn(it.placeId, it.name, it.address,
                                    it.latLng.latitude, it.latLng.longitude)
                            },
                            onDone = {
                                Toast.makeText(ctx, "Posted ✔", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            },
                            onError = { e -> Toast.makeText(ctx, e, Toast.LENGTH_SHORT).show() }
                        )
                    }) { Text("Post", color = Color(0xFFFF4500)) }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
            )
        }
    ) { pad ->

        /* --------------- SEARCH side-effect --------------- */
        LaunchedEffect(placeQuery) {
            if (placeQuery.length < 3) { placeResults = emptyList(); return@LaunchedEffect }
            delay(400)                       // debounce
            searching = true
            val bias = AM24LocationManager.getLastKnownLocation(ctx)
                ?.let { LatLng(it.first, it.second) }
                ?: LatLng(22.5726, 88.3639)  // Kolkata fallback
            placeResults = searchPlacesRich(placeQuery, bias)
            searching = false
        }

        /* --------------- CONTENT --------------- */
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.Start
        ) {

            /* preview */
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        Image(painter = rememberAsyncImagePainter(imageUri),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize())
                    } else {
                        Text("Tap icons below to add photo", color = Color.LightGray)
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    Arrangement.SpaceEvenly
                ) {
                    IconButton({ takePicture.launch(camUri) }) {
                        Icon(Icons.Default.PhotoCamera, null, tint = Color(0xFFFFA500))
                    }
                    IconButton({ pickGallery.launch("image/*") }) {
                        Icon(Icons.Default.Collections, null, tint = Color(0xFFFFA500))
                    }
                }
            }

            /* caption / tags */
            item { Spacer(Modifier.height(16.dp)) }
            item {
                OutlinedTextField(
                    value = caption, onValueChange = { caption = it },
                    label  = { Text("Caption", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = otfColors()
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                OutlinedTextField(
                    value = userTags, onValueChange = { userTags = it },
                    label  = { Text("Tags (comma separated)", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = otfColors()
                )
            }

            /* ------------ PLACE SEARCH ------------ */
            item { Spacer(Modifier.height(20.dp)) }
            item { Text("Add a location (optional)", fontSize = 14.sp, color = Color.LightGray) }

            /* the anchored box */
            item {
                ExposedDropdownMenuBox(
                    expanded = menuExpanded,
                    onExpandedChange = { /* handled by placeResults state */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = placeQuery,
                        onValueChange = {
                            placeQuery = it
                            selectedPlace = null                 // clear chip when typing again
                        },
                        label = { Text("Search place") },
                        singleLine = true,
                        trailingIcon = {
                            if (searching)
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            else Icon(Icons.Default.Search, null, tint = Color(0xFFFFA500))
                        },
                        colors = otfColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()                         // important!
                    )

                    /* white popup */
                    ExposedDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { placeResults = emptyList() },
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(6.dp))
                            .border(BorderStroke(1.dp, Color(0x33000000)), RoundedCornerShape(6.dp))
                    ) {
                        placeResults.forEach { res ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(res.name, color = Color.Black)
                                        if (res.address.isNotBlank())
                                            Text(res.address,
                                                color = Color.DarkGray,
                                                style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Place, null, tint = Color(0xFFFFA500))
                                },
                                onClick = {
                                    selectedPlace = res
                                    placeQuery    = res.name
                                    placeResults  = emptyList()
                                }
                            )
                        }
                    }
                }
            }

            /* selected chip */
            item {
                selectedPlace?.let {
                    Spacer(Modifier.height(6.dp))
                    AssistChip(
                        onClick = { selectedPlace = null },
                        label = { Text("✓ ${it.name}") },
                        leadingIcon = { Icon(Icons.Default.Place, null) }
                    )
                }
            }

            /* bottom spacer so last item isn’t hidden */
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/* ------------------------------------------------------------------- */
/*                            VIDEO POST                               */
/* ------------------------------------------------------------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPostComposable(
    navController: NavController,
    postViewModel: PostViewModel
) {
    val ctx     = LocalContext.current
    val scope   = rememberCoroutineScope()
    val userId  = FirebaseAuth.getInstance().currentUser?.uid ?: return

    /* ---------- UI state ---------- */
    var username by remember { mutableStateOf("") }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var caption  by remember { mutableStateOf("") }
    var userTags by remember { mutableStateOf("") }

    /* ---------- “check-in” state ---------- */
    var placeQuery    by remember { mutableStateOf("") }
    var placeResults  by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var selectedPlace by remember { mutableStateOf<PlaceResult?>(null) }
    var searching     by remember { mutableStateOf(false) }
    val menuExpanded  = placeResults.isNotEmpty()

    /* fetch username once */
    LaunchedEffect(Unit) { username = fetchUsernameById(userId) ?: "Anonymous" }

    /* ------------- camera / gallery helpers ------------- */
    fun tmpVid() = File.createTempFile("vid_${System.currentTimeMillis()}", ".mp4", ctx.cacheDir)
        .apply { deleteOnExit() }

    val camFile = remember { tmpVid() }
    val camUri  = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", camFile)

    val captureVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { ok -> if (ok) videoUri = camUri }

    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { videoUri = it } }

    fun videoTooLong(uri: Uri): Boolean = MediaMetadataRetriever().run {
        return@run try {
            setDataSource(ctx, uri)
            (extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L) > 15_000
        } finally { release() }
    }

    /* ------------- place search side-effect ------------- */
    LaunchedEffect(placeQuery) {
        if (placeQuery.length < 3) { placeResults = emptyList(); return@LaunchedEffect }
        delay(400)                                  // debounce
        searching = true
        val bias = AM24LocationManager.getLastKnownLocation(ctx)
            ?.let { LatLng(it.first, it.second) }
            ?: LatLng(22.5726, 88.3639)             // Kolkata fallback
        placeResults = searchPlacesRich(placeQuery, bias)
        searching = false
    }

    /* ------------------------ UI ------------------------ */
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Video Post", color = Color.White) },
                navigationIcon = {
                    IconButton({ navController.popBackStack() }) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (videoUri == null) {
                            Toast.makeText(ctx, "Select a video first", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        if (videoTooLong(videoUri!!)) {
                            Toast.makeText(ctx, "Video longer than 15 s", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val tags = userTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                        postViewModel.createMediaPost(
                            userId   = userId,
                            username = username,
                            mediaUri = videoUri!!,
                            mediaType= "video",
                            caption  = caption,
                            userTags = tags,
                            checkIn  = selectedPlace?.let {
                                CheckIn(it.placeId, it.name, it.address,
                                    it.latLng.latitude, it.latLng.longitude)
                            },
                            onDone = {
                                Toast.makeText(ctx, "Posted ✔", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            },
                            onError = { e -> Toast.makeText(ctx, e, Toast.LENGTH_SHORT).show() }
                        )
                    }) { Text("Post", color = Color(0xFFFF4500)) }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
            )
        }
    ) { pad ->

        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.Start
        ) {

            /* ------------ preview frame ------------ */
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (videoUri != null) {
                        val frameBitmap = remember(videoUri) {
                            try {
                                MediaMetadataRetriever().run {
                                    setDataSource(ctx, videoUri)
                                    val bmp = getFrameAtTime(0L)
                                    release();  bmp
                                }
                            } catch (_: Exception) { null }
                        }
                        frameBitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
                    } else {
                        Text("Tap icons below to add video", color = Color.LightGray)
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    Arrangement.SpaceEvenly
                ) {
                    IconButton({ captureVideo.launch(camUri) }) {
                        Icon(Icons.Default.Videocam, null, tint = Color(0xFFFFA500))
                    }
                    IconButton({ pickVideo.launch("video/*") }) {
                        Icon(Icons.Default.Collections, null, tint = Color(0xFFFFA500))
                    }
                }
            }

            /* caption / tags */
            item { Spacer(Modifier.height(16.dp)) }
            item {
                OutlinedTextField(
                    value = caption, onValueChange = { caption = it },
                    label  = { Text("Caption", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = otfColors()
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                OutlinedTextField(
                    value = userTags, onValueChange = { userTags = it },
                    label  = { Text("Tags (comma separated)", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = otfColors()
                )
            }

            /* ---------------- place search ---------------- */
            item { Spacer(Modifier.height(20.dp)) }
            item { Text("Add a location (optional)", fontSize = 14.sp, color = Color.LightGray) }

            item {
                ExposedDropdownMenuBox(
                    expanded = menuExpanded,
                    onExpandedChange = { /* controlled by placeResults */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = placeQuery,
                        onValueChange = {
                            placeQuery    = it
                            selectedPlace = null     // clear chip when typing again
                        },
                        label = { Text("Search place") },
                        singleLine = true,
                        trailingIcon = {
                            if (searching)
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            else Icon(Icons.Default.Search, null, tint = Color(0xFFFFA500))
                        },
                        colors = otfColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { placeResults = emptyList() },
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(6.dp))
                            .border(BorderStroke(1.dp, Color(0x33000000)), RoundedCornerShape(6.dp))
                    ) {
                        placeResults.forEach { res ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(res.name, color = Color.Black)
                                        if (res.address.isNotBlank())
                                            Text(res.address,
                                                color = Color.DarkGray,
                                                style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Place, null, tint = Color(0xFFFFA500)) },
                                onClick = {
                                    selectedPlace = res
                                    placeQuery    = res.name
                                    placeResults  = emptyList()
                                }
                            )
                        }
                    }
                }
            }

            /* selected chip */
            item {
                selectedPlace?.let {
                    Spacer(Modifier.height(6.dp))
                    AssistChip(
                        onClick = { selectedPlace = null },
                        label    = { Text("✓ ${it.name}") },
                        leadingIcon = { Icon(Icons.Default.Place, null) }
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}