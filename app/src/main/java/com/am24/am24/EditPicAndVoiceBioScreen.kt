import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.am24.am24.FirebaseRefs
import com.am24.am24.Profile
import com.am24.am24.ProfileViewModel
import com.am24.am24.compressImage
import com.am24.am24.moderateImages
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPicAndVoiceBioScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    // A local "Profile?" from which we will load initial data.
    var profile by remember { mutableStateOf<Profile?>(null) }

    // Up to 5 images in memory. Each item is either a string (Firebase Storage URL) or empty "".
    val photoItems = remember { mutableStateListOf<String>() }

    // 1) Fetch the user’s profile once.
    LaunchedEffect(currentUserId) {
        profileViewModel.fetchUserProfile(
            userId = currentUserId,
            onSuccess = { fetchedProfile ->
                profile = fetchedProfile

                // Build up to 5 slots from profilepicUrl + optionalPhotoUrls
                val combined = mutableListOf<String>()
                fetchedProfile.profilepicUrl?.let { combined.add(it) }
                combined.addAll(fetchedProfile.optionalPhotoUrls)
                // ensure exactly 5
                while (combined.size < 5) combined.add("")
                if (combined.size > 5) {
                    combined.dropLast(combined.size - 5)
                }
                photoItems.clear()
                photoItems.addAll(combined.take(5))
                        },
            onFailure = { error ->
                Log.e("EditPic", "Failed to load profile: $error")
            }
        )
    }

    // If the profile is not loaded yet, show a loading spinner.
    if (profile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFF6F00))
        }
        return
    }

// 2) Image picking. We store which "slot index" the user clicked.
    var slotIndexToReplace by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    /* ─── OpenAI image-moderation helper ─────────────────────────────── */
    suspend fun isExplicit(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val jpeg = compressImage(context, uri)                          // already have this util
        val b64  = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
        moderateImages(listOf(b64))                                     // true == unsafe
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { rawUri: Uri? ->
        rawUri ?: return@rememberLauncherForActivityResult
        val idx = slotIndexToReplace ?: return@rememberLauncherForActivityResult

        scope.launch {
            // 🔍 call OpenAI
            if (isExplicit(rawUri)) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "That photo looks explicit – please choose another.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch                                            // 🚫 block upload
            }

            /* —— existing compress ▶ push to Firebase —— */
            try {
                val jpegBytes = compressImage(context, rawUri)
                val fileName  = "${System.currentTimeMillis()}.jpg"
                val imgRef    = FirebaseRefs.storage.reference
                    .child("users/$currentUserId/$fileName")

                imgRef.putBytes(jpegBytes)
                    .addOnSuccessListener {
                        it.storage.downloadUrl.addOnSuccessListener { dl ->
                            photoItems[idx] = dl.toString()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("PhotoUpload", "Upload failed: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e("PhotoCompress", "Compression error: ${e.message}")
            }
        }
    }

    // 4) Save function
    fun onSave() {
        // Gather non-empty photo URLs
        val nonEmpty = photoItems.filter { it.isNotBlank() }
        val mainPic = nonEmpty.firstOrNull()
        val others = if (nonEmpty.size > 1) nonEmpty.drop(1) else emptyList()

        // Build updated profile
        val updatedProfile = profile!!.copy(
            profilepicUrl = mainPic,
            optionalPhotoUrls = others,
        )

        // Save to Firebase (Realtime DB)
        profileViewModel.saveProfileUpdated(
            updatedProfile = updatedProfile,
            onSuccess = {
                scope.launch(Dispatchers.Main) {
                    navController.navigateUp()
                }
            },
            onFailure = { e -> Log.e("EditPic", "Failed to update: $e") }
        )
    }

    // UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Pictures", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Tap on empty slot to add photo. Use Up/Down to reorder. First photo is your main pic.",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Show the 5 slots in a row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                photoItems.forEachIndexed { index, url ->
                    // Each slot is 100x100
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                    ) {
                        if (url.isBlank()) {
                            // Empty
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, Color.Gray, RectangleShape)
                                    .clickable {
                                        slotIndexToReplace = index
                                        pickImageLauncher.launch("image/*")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", color = Color.Gray, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Non-empty. Show the image plus reorder + remove icons
                            AsyncImage(
                                model = url,
                                contentDescription = "Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Remove icon in top-right corner
                            IconButton(
                                onClick = {
                                    // If this is the only photo => do not remove
                                    val countNonEmpty = photoItems.count { it.isNotBlank() }
                                    if (countNonEmpty == 1 && index == photoItems.indexOfFirst { it.isNotBlank() }) {
                                        // can't remove the only photo
                                        Log.w("EditPic", "Cannot remove the only photo.")
                                    } else {
                                        photoItems[index] = ""
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, "Remove", tint = Color.Red)
                            }

                            // Reorder icons (Up = move left, Down = move right for a row)
                            // We'll place them along the bottom left
                            if (index > 0) {
                                IconButton(
                                    onClick = {
                                        // swap items at index & index-1
                                        val temp = photoItems[index - 1]
                                        photoItems[index - 1] = photoItems[index]
                                        photoItems[index] = temp
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(24.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Left", tint = Color.White)
                                }
                            }
                            if (index < photoItems.lastIndex) {
                                IconButton(
                                    onClick = {
                                        // swap items at index & index+1
                                        val temp = photoItems[index + 1]
                                        photoItems[index + 1] = photoItems[index]
                                        photoItems[index] = temp
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .offset(x = 32.dp, y = (-4).dp)
                                        .size(24.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Right", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Bottom row: Cancel / Save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { navController.navigateUp() },
                    colors = ButtonDefaults.buttonColors(Color.Gray)
                ) {
                    Text("Cancel", color = Color.White)
                }
                Button(
                    onClick = { onSave() },
                    colors = ButtonDefaults.buttonColors(Color(0xFFFF6F00))
                ) {
                    Text("Save", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}