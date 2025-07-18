// CreatePostScreen.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.widget.Toast
import android.os.Handler
import android.os.Looper
import com.am24.am24.LocationManager as AM24LocationManager
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
import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import coil.compose.rememberAsyncImagePainter
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.am24.am24.ui.theme.DarkGrayBackground
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.io.File
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun CreatePostScreen(
    navController: NavController,
    postViewModel: PostViewModel
) {
    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    var isPremium by remember { mutableStateOf(false) }
    var isPlus by remember { mutableStateOf(false) }

    // Fetch isPremium and isPlus from Firebase
    LaunchedEffect(userId) {
        if (userId != null) {
            try {
                val db = FirebaseDatabase.getInstance()
                val premiumSnapshot = db.getReference("users")
                    .child(userId)
                    .child("isPremium")
                    .get()
                    .await()
                val plusSnapshot = db.getReference("users")
                    .child(userId)
                    .child("isPlus")
                    .get()
                    .await()

                isPremium = premiumSnapshot.getValue(Boolean::class.java) ?: false
                isPlus = plusSnapshot.getValue(Boolean::class.java) ?: false
            } catch (e: Exception) {
                Log.e("CreatePostScreen", "Failed to fetch premium status: ${e.message}")
                isPremium = false
                isPlus = false
            }
        }
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
        containerColor = DarkGrayBackground,
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
//                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // 1) Text posts are always enabled
                PostTypeButton(
                    icon = Icons.Default.TextFields,
                    label = "Text Post",
                    enabled = true,
                    onClick = {
                        navController.navigate("create_post/text")
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2) Image Post – locked for non-premium/non-plus
                PostTypeButton(
                    icon = Icons.Default.Photo,
                    label = "Image Post",
                    enabled = isPremium || isPlus,
                    onClick = {
                        if (isPremium || isPlus) {
                            navController.navigate("create_post/image")
                        } else {
                            Toast.makeText(
                                context,
                                "Upgrade to Plus to create Image Posts",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4) Voice Post – locked for non-premium/non-plus
                PostTypeButton(
                    icon = Icons.Default.Mic,
                    label = "Voice Post",
                    enabled = isPremium || isPlus,
                    onClick = {
                        if (isPremium || isPlus) {
                            navController.navigate("create_post/voice")
                        } else {
                            Toast.makeText(
                                context,
                                "Upgrade to Plus to create Voice Posts",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3) Video Post – locked for non-premium/non-plus
                PostTypeButton(
                    icon = Icons.Default.Videocam,
                    label = "Video Post",
                    enabled = isPremium,
                    onClick = {
                        if (isPremium) {
                            navController.navigate("create_post/video")
                        } else {
                            Toast.makeText(
                                context,
                                "Upgrade to Premium to create Video Posts",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }
    )
}

@Composable
fun PostTypeButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) Color(0xFFFFA500) else Color(0xFF888888)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        // Main icon (text / photo / video / mic)
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) Color.White else Color.LightGray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))

        // Label
        Text(
            text = label,
            color = if (enabled) Color.White else Color.LightGray,
            style = MaterialTheme.typography.bodyLarge
        )

        // If disabled, show a small lock icon at the end
        if (!enabled) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = Color.LightGray,
                modifier = Modifier.size(20.dp)
            )
        }
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
    val isPosting by postViewModel.isUploading.collectAsState(initial = false)
    var localPosting by remember { mutableStateOf(false) }
    val posting = isPosting || localPosting

    var contentText by remember { mutableStateOf("") }
    var userTags by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    /* ───── check-in state ───── */
    var placeQuery by remember { mutableStateOf("") }
    var placeResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var selectedPlace by remember { mutableStateOf<PlaceResult?>(null) }
    var searching by remember { mutableStateOf(false) }
    val menuExpanded = placeResults.isNotEmpty()

    val userId = FirebaseAuth.getInstance().currentUser?.uid
    // Fetch username from the database
    LaunchedEffect(userId) {
        username = userId?.let { fetchUsernameById(it) } ?: "Anonymous"
    }

    /* side-effect: live place search (debounced) */
    LaunchedEffect(placeQuery) {
        if (placeQuery.length < 3) {
            placeResults = emptyList(); return@LaunchedEffect
        }
        delay(400)                                       // debounce
        searching = true
        val bias = AM24LocationManager.getLastKnownLocation(context)
            ?.let { LatLng(it.first, it.second) }
            ?: LatLng(22.5726, 88.3639)                  // Kolkata fallback
        placeResults = searchPlacesRich(placeQuery, bias)
        searching = false
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
                    if (isPosting) {
                        // show spinner instead of button
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp),
                            color = Color(0xFFFF4500),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = {
                            localPosting = true
                            coroutineScope.launch {
                                // Validate input
                                if (contentText.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        "Post content cannot be empty.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    localPosting = false
                                    return@launch
                                }

                                if (userId == null) {
                                    Toast.makeText(
                                        context,
                                        "User not authenticated.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    localPosting = false
                                    return@launch
                                }

                                /* suspend call is now legal */
                                val flagged = moderateText(contentText)
                                if (flagged && !askProceed(
                                        context,
                                        "This may be explicit. Post anyway?"
                                    )
                                )
                                    localPosting = false
                                    return@launch                                     // user pressed “Retake”

                                // Convert userTags string to list
                                val tagsList =
                                    userTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                                postViewModel.createTextPost(
                                    userId = userId,
                                    username = username,
                                    contentText = contentText,
                                    userTags = tagsList,
                                    checkIn = selectedPlace?.let {
                                        CheckIn(
                                            it.placeId, it.name, it.address,
                                            it.latLng.latitude, it.latLng.longitude
                                        )
                                    },
                                    fontFamily = "Default", // Default font family since it's removed
                                    fontSize = 14, // Default font size since it's removed
                                    onSuccess = {
                                        coroutineScope.launch {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    "Posted ✔",
                                                    Toast.LENGTH_SHORT
                                                )
                                                    .show()
                                                navController.popBackStack(
                                                    "home",
                                                    inclusive = false
                                                )
                                                localPosting = false
                                            }
                                        }
                                    },
                                    onFailure = { error ->
                                        coroutineScope.launch {
                                            Toast.makeText(
                                                context,
                                                "Failed to create post: $error",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            localPosting = false
                                        }
                                    }
                                )
                            }
                        }, enabled = !(isPosting || localPosting)) {
                            Text(
                                "Post",
                                color = Color(0xFFFF4500)
                            ) // Dark orange for the "Post" button
                        }
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = DarkGrayBackground,
        content = { pad ->
            LazyColumn(
                modifier = Modifier
                    .padding(pad)
//                    .padding(16.dp)
                    .fillMaxSize()
                    .imePadding(),
                horizontalAlignment = Alignment.Start
            ) {
                item {
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
                }

                item { Spacer(Modifier.height(16.dp)) }
                item {
                    OutlinedTextField(
                        value = userTags, onValueChange = { userTags = it },
                        label = { Text("Add Tags (comma separated)", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = otfColors()
                    )
                }

                /* ---------- CHECK-IN UI ---------- */
                item { Spacer(Modifier.height(20.dp)) }
                item {
                    Text(
                        "Add a location (optional)",
                        fontSize = 14.sp,
                        color = Color.LightGray
                    )
                }

                item {
                    ExposedDropdownMenuBox(
                        expanded = menuExpanded,
                        onExpandedChange = { /* menu driven by placeResults */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = placeQuery,
                            onValueChange = {
                                placeQuery = it; selectedPlace = null    // reset chip on typing
                            },
                            label = { Text("Search place") },
                            singleLine = true,
                            trailingIcon = {
                                if (searching)
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                else Icon(Icons.Default.Search, null, tint = Color(0xFFFFA500))
                            },
                            colors = otfColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()                              // Material 3 anchor
                        )

                        ExposedDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { placeResults = emptyList() },
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(6.dp))
                                .border(
                                    BorderStroke(1.dp, Color(0x33000000)),
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            placeResults.forEach { res ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(res.name, color = Color.Black)
                                            if (res.address.isNotBlank())
                                                Text(
                                                    res.address,
                                                    color = Color.DarkGray,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Place,
                                            null,
                                            tint = Color(0xFFFFA500)
                                        )
                                    },
                                    onClick = {
                                        selectedPlace = res
                                        placeQuery = res.name
                                        placeResults = emptyList()
                                    }
                                )
                            }
                        }
                    }
                }

                /* chip */
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

                item { Spacer(Modifier.height(32.dp)) }
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
    val isPosting by postViewModel.isUploading.collectAsState(initial = false)

    /* fetch display name once */
    LaunchedEffect(Unit) { username = fetchUsernameById(userId) ?: "Anonymous" }

    /* camera / gallery helpers */
    fun tmpImg() = File.createTempFile("img_${System.currentTimeMillis()}", ".jpg", ctx.cacheDir)
        .apply { deleteOnExit() }

    val camFile = remember { tmpImg() }
    val camUri  = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", camFile)

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
        if (it) imageUri = camUri
    }
    val pickGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let { uri -> imageUri = uri }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            takePicture.launch(camUri)
        } else {
            Toast.makeText(ctx, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
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
                    if (isPosting) {
                        // show spinner instead of button
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp),
                            color = Color(0xFFFF4500),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = {
                            scope.launch {
                                if (imageUri == null) {
                                    Toast.makeText(ctx, "Select an image first", Toast.LENGTH_SHORT)
                                        .show()
                                    return@launch
                                }
                                val tags =
                                    userTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                                /* ①   moderate the single JPEG */
                                val b64 = ctx.uriToBase64(imageUri!!)
                                val flagged = moderateImages(listOf(b64))

                                /* ②   hard-block if unsafe  */
                                if (flagged) {
                                    Toast.makeText(
                                        ctx,
                                        "Image appears explicit – please retake.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    imageUri = null            // force user to pick another
                                    return@launch
                                }

                                postViewModel.createMediaPost(
                                    userId = userId,
                                    username = username,
                                    mediaUri = imageUri!!,
                                    mediaType = "image",
                                    caption = caption,
                                    userTags = tags,
                                    checkIn = selectedPlace?.let {
                                        CheckIn(
                                            it.placeId, it.name, it.address,
                                            it.latLng.latitude, it.latLng.longitude
                                        )
                                    },
                                    onDone = {
                                        scope.launch {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(ctx, "Posted ✔", Toast.LENGTH_SHORT)
                                                    .show()
                                                navController.popBackStack(
                                                    "home",
                                                    inclusive = false
                                                )
                                            }
                                        }
                                    },
                                    onError = { e ->
                                        Handler(Looper.getMainLooper()).post {
                                            Toast.makeText(ctx, e, Toast.LENGTH_SHORT).show()
                                        }
                                    })
                            }
                        }, enabled = !isPosting) { Text("Post", color = Color(0xFFFF4500)) }
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = DarkGrayBackground
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
//                .padding(16.dp)
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
                    IconButton({
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }) {
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
    val isPosting by postViewModel.isUploading.collectAsState(initial = false)

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
    fun tmpVid() = File.createTempFile("vid_${System.currentTimeMillis()}", ".mp4")
        .apply { deleteOnExit() }

    val camFile = remember { tmpVid() }
    val camUri  = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", camFile)

    val videoCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // the camera activity will write to our camUri
            videoUri = camUri
        }
    }

    // wrap the intent in a helper function so we can re-use it in the button below
    fun makeVideoCaptureIntent(): Intent =
        Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, camUri)
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30)  // cap at 30s
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)    // high quality
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            videoCaptureLauncher.launch(makeVideoCaptureIntent())
        } else {
            Toast.makeText(ctx, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val tooLong = MediaMetadataRetriever().run {
                setDataSource(ctx, it)
                val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                release()
                d > 30_000L
            }
            if (tooLong) {
                Toast.makeText(ctx, "Please select a video ≤ 30 s", Toast.LENGTH_SHORT).show()
            } else {
                videoUri = it
            }
        }
    }

    fun videoTooLong(uri: Uri): Boolean = MediaMetadataRetriever().run {
        return@run try {
            setDataSource(ctx, uri)
            (extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L) > 30_000
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
                    if (isPosting) {
                        // show spinner instead of button
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp),
                            color = Color(0xFFFF4500),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = {
                            scope.launch {
                                if (videoUri == null) {
                                    Toast.makeText(ctx, "Select a video first", Toast.LENGTH_SHORT)
                                        .show()
                                    return@launch
                                }
                                if (videoTooLong(videoUri!!)) {
                                    Toast.makeText(
                                        ctx,
                                        "Video longer than 30 s",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                    return@launch
                                }
                                val tags =
                                    userTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                                /* ①   extract JPEG frames every 2 s and moderate */
                                val frames = ctx.videoFramesEvery2s(videoUri!!)
                                val flagged = moderateImages(frames)

                                /* ②   block if unsafe */
                                if (flagged) {
                                    Toast.makeText(
                                        ctx,
                                        "Video appears explicit – please retake.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    videoUri = null
                                    return@launch
                                }

                                postViewModel.createMediaPost(
                                    userId = userId,
                                    username = username,
                                    mediaUri = videoUri!!,
                                    mediaType = "video",
                                    caption = caption,
                                    userTags = tags,
                                    checkIn = selectedPlace?.let {
                                        CheckIn(
                                            it.placeId, it.name, it.address,
                                            it.latLng.latitude, it.latLng.longitude
                                        )
                                    },
                                    onDone = {
                                        scope.launch {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(ctx, "Posted ✔", Toast.LENGTH_SHORT)
                                                    .show()
                                                navController.popBackStack(
                                                    "home",
                                                    inclusive = false
                                                )
                                            }
                                        }
                                    },
                                    onError = { e ->
                                        Handler(Looper.getMainLooper()).post {
                                            Toast.makeText(ctx, e, Toast.LENGTH_SHORT).show()
                                        }
                                    })
                            }
                        }, enabled = !isPosting) { Text("Post", color = Color(0xFFFF4500)) }
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = DarkGrayBackground
    ) { pad ->

        LazyColumn(
            modifier = Modifier
                .padding(pad)
//                .padding(16.dp)
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
                    IconButton({
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }) {
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