import android.app.Activity
import android.graphics.Bitmap
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
import com.am24.am24.ui.theme.DarkGrayBackground
import com.google.firebase.auth.FirebaseAuth
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPicAndVoiceBioScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    // Local profile state
    var profile by remember { mutableStateOf<Profile?>(null) }

    // Up to 5 photo slots
    val photoItems = remember { mutableStateListOf<String>() }

    // Fetch user profile
    LaunchedEffect(currentUserId) {
        profileViewModel.fetchUserProfile(
            userId = currentUserId,
            onSuccess = { fetchedProfile ->
                profile = fetchedProfile
                val combined = mutableListOf<String>()
                fetchedProfile.profilepicUrl?.let { combined.add(it) }
                combined.addAll(fetchedProfile.optionalPhotoUrls)
                while (combined.size < 5) combined.add("")
                if (combined.size > 5) combined.dropLast(combined.size - 5)
                photoItems.clear()
                photoItems.addAll(combined.take(5))
            },
            onFailure = { error ->
                Log.e("EditPic", "Failed to load profile: $error")
            }
        )
    }

    // Loading state
    if (profile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFF6F00))
        }
        return
    }

    // Image picking and cropping setup
    var slotIndexToReplace by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Image moderation helper
    suspend fun isExplicit(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val jpeg = compressImage(context, uri) ?: return@withContext false
        val b64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
        moderateImages(listOf(b64))
    }

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val outUri = result.data?.let { UCrop.getOutput(it) }
                ?: return@rememberLauncherForActivityResult
            val idx = slotIndexToReplace ?: return@rememberLauncherForActivityResult
            scope.launch {
                if (isExplicit(outUri)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "That photo looks explicit – please choose another.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                try {
                    val jpegBytes = compressImage(context, outUri) ?: return@launch
                    val fileName = "${'$'}{System.currentTimeMillis()}.jpg"
                    val imgRef = FirebaseRefs.storage.reference
                        .child("users/${'$'}currentUserId/${'$'}fileName")

                    imgRef.putBytes(jpegBytes)
                        .addOnSuccessListener {
                            it.storage.downloadUrl.addOnSuccessListener { dl ->
                                photoItems[idx] = dl.toString()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("PhotoUpload", "Upload failed: ${'$'}{e.message}")
                        }
                } catch (e: Exception) {
                    Log.e("PhotoCompress", "Compression error: ${'$'}{e.message}")
                }
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val error = result.data?.let { UCrop.getError(it) }
            error?.let { Log.e("UCrop", "Crop failed", it) }
        }
    }

    // Photo picker launcher
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { rawUri: Uri? ->
        rawUri ?: return@rememberLauncherForActivityResult
        val idx = slotIndexToReplace ?: return@rememberLauncherForActivityResult
        val destinationFile = File(context.cacheDir, "cropped_${'$'}{System.currentTimeMillis()}.jpg")
        val destinationUri = Uri.fromFile(destinationFile)
        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setHideBottomControls(false)
            setFreeStyleCropEnabled(false)
        }
        val uCrop = UCrop.of(rawUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(800, 800)
            .withOptions(options)
        cropLauncher.launch(uCrop.getIntent(context))
    }

    // Save function
    fun onSave() {
        val nonEmpty = photoItems.filter { it.isNotBlank() }
        val mainPic = nonEmpty.firstOrNull()
        val others = if (nonEmpty.size > 1) nonEmpty.drop(1) else emptyList()

        val updatedProfile = profile!!.copy(
            profilepicUrl = mainPic,
            optionalPhotoUrls = others
        )

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
        containerColor = DarkGrayBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkGrayBackground)
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                photoItems.forEachIndexed { index, url ->
                    Box(modifier = Modifier.size(100.dp)) {
                        if (url.isBlank()) {
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
                            AsyncImage(
                                model = url,
                                contentDescription = "Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = {
                                    val countNonEmpty = photoItems.count { it.isNotBlank() }
                                    if (countNonEmpty == 1 && index == photoItems.indexOfFirst { it.isNotBlank() }) {
                                        Log.w("EditPic", "Cannot remove the only photo.")
                                    } else {
                                        photoItems[index] = ""
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, "Remove", tint = Color.Red)
                            }
                            if (index > 0) {
                                IconButton(
                                    onClick = {
                                        val temp = photoItems[index - 1]
                                        photoItems[index - 1] = photoItems[index]
                                        photoItems[index] = temp
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(24.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, "Move Left", tint = Color.White)
                                }
                            }
                            if (index < photoItems.lastIndex) {
                                IconButton(
                                    onClick = {
                                        val temp = photoItems[index + 1]
                                        photoItems[index + 1] = photoItems[index]
                                        photoItems[index] = temp
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .offset(x = 32.dp, y = (-4).dp)
                                        .size(24.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, "Move Right", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

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