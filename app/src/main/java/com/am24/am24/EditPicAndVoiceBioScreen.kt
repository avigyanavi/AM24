@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.am24.am24.ui.theme.DarkGrayBackground
import com.google.firebase.auth.FirebaseAuth
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "EditPic"

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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Fetch user profile
    LaunchedEffect(currentUserId) {
        Log.d(TAG, "Fetching profile for $currentUserId")
        profileViewModel.fetchUserProfile(
            userId = currentUserId,
            onSuccess = { fetchedProfile ->
                Log.d(TAG, "Profile fetched, pic=${fetchedProfile.profilepicUrl}")
                profile = fetchedProfile
                val combined = mutableListOf<String>()
                fetchedProfile.profilepicUrl?.let { combined.add(it) }
                combined.addAll(fetchedProfile.optionalPhotoUrls)
                while (combined.size < 5) combined.add("")
                photoItems.clear()
                photoItems.addAll(combined.take(5))
                Log.d(TAG, "Initial photoItems=$photoItems")
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to load profile: $error")
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

    // Which slot is being edited
    var slotIndexToReplace by remember { mutableStateOf<Int?>(null) }

    // ------ Launchers ------

    // Crop launcher
    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "UCrop resultCode=${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            val outUri = result.data?.let { UCrop.getOutput(it) }
            if (outUri == null) {
                Log.e(TAG, "UCrop output URI is null")
                return@rememberLauncherForActivityResult
            }
            val idx = slotIndexToReplace
            if (idx == null) {
                Log.e(TAG, "slotIndexToReplace is null on crop result")
                return@rememberLauncherForActivityResult
            }

            Log.d(TAG, "Cropped image uri=$outUri for slot=$idx")

            // 1️⃣ Immediately show cropped image using content:// URI so user sees it
            photoItems[idx] = outUri.toString()

            // 2️⃣ Then compress + moderate + upload in background
            scope.launch {
                try {
                    val jpegBytes = withContext(Dispatchers.IO) {
                        compressImage(context, outUri)
                    }

                    if (jpegBytes == null) {
                        Log.e(TAG, "compressImage returned null before upload")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Image compression failed. Try a different photo.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                    Log.d(TAG, "Compressed image size=${jpegBytes.size} bytes")

                    // Moderation using same bytes
                    val isExplicit = withContext(Dispatchers.IO) {
                        val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                        moderateImages(listOf(b64))
                    }

                    Log.d(TAG, "moderateImages result=$isExplicit")

                    if (isExplicit) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "That photo looks explicit – please choose another.",
                                Toast.LENGTH_LONG
                            ).show()
                            // reset the slot back to empty
                            photoItems[idx] = ""
                        }
                        return@launch
                    }

                    // Upload
                    val fileName = "${System.currentTimeMillis()}.jpg"
                    val imgRef = FirebaseRefs.storage.reference
                        .child("users/$currentUserId/$fileName")

                    Log.d(TAG, "Uploading to path=${imgRef.path}")

                    imgRef.putBytes(jpegBytes)
                        .addOnSuccessListener {
                            it.storage.downloadUrl.addOnSuccessListener { dl ->
                                Log.d(TAG, "Upload success, url=$dl, slot=$idx")
                                Toast.makeText(
                                    context,
                                    "Photo uploaded ✅",
                                    Toast.LENGTH_SHORT
                                ).show()
                                // swap preview URI with network URL
                                photoItems[idx] = dl.toString()
                                Log.d(TAG, "Updated photoItems=$photoItems")
                            }.addOnFailureListener { e ->
                                Log.e(TAG, "Failed to get downloadUrl: ${e.message}", e)
                                Toast.makeText(
                                    context,
                                    "Upload done but URL failed: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Upload failed: ${e.message}", e)
                            Toast.makeText(
                                context,
                                "Upload failed: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                } catch (e: Exception) {
                    Log.e(TAG, "Compression/Upload error: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Image processing failed: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val error = result.data?.let { UCrop.getError(it) }
            Log.e(TAG, "UCrop error", error)
        }
    }

    // Photo picker launcher
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { rawUri: Uri? ->
        rawUri ?: return@rememberLauncherForActivityResult
        val idx = slotIndexToReplace
        if (idx == null) {
            Log.e(TAG, "slotIndexToReplace is null on pickImage result")
            return@rememberLauncherForActivityResult
        }
        Log.d(TAG, "Picked raw uri=$rawUri for slot=$idx")

        val destinationFile = File(
            context.cacheDir,
            "cropped_${System.currentTimeMillis()}.jpg"
        )
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

        Log.d(TAG, "Saving profile with mainPic=$mainPic others=$others")

        val updatedProfile = profile!!.copy(
            profilepicUrl = mainPic,
            optionalPhotoUrls = others
        )

        profileViewModel.saveProfileUpdated(
            updatedProfile = updatedProfile,
            onSuccess = {
                Log.d(TAG, "Profile save success")
                scope.launch {
                    navController.navigateUp()
                }
            },
            onFailure = { e ->
                Log.e(TAG, "Failed to update profile: $e")
                scope.launch {
                    Toast.makeText(
                        context,
                        "Failed to save profile: $e",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // ---------------- UI ----------------

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
                                        Log.d(TAG, "Clicked empty slot index=$index")
                                        slotIndexToReplace = index
                                        pickImageLauncher.launch("image/*")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+",
                                    color = Color.Gray,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                                    if (countNonEmpty == 1 &&
                                        index == photoItems.indexOfFirst { it.isNotBlank() }
                                    ) {
                                        Log.w(TAG, "Cannot remove the only photo.")
                                    } else {
                                        Log.d(TAG, "Removing photo at index=$index")
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
                                        Log.d(TAG, "Moving photo $index -> ${index - 1}")
                                        val temp = photoItems[index - 1]
                                        photoItems[index - 1] = photoItems[index]
                                        photoItems[index] = temp
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        "Move Left",
                                        tint = Color.White
                                    )
                                }
                            }

                            if (index < photoItems.lastIndex) {
                                IconButton(
                                    onClick = {
                                        Log.d(TAG, "Moving photo $index -> ${index + 1}")
                                        val temp = photoItems[index + 1]
                                        photoItems[index + 1] = photoItems[index]
                                        photoItems[index] = temp
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .offset(x = 32.dp, y = (-4).dp)
                                        .size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        "Move Right",
                                        tint = Color.White
                                    )
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
                    Text("❌", color = Color.White)
                }
                Button(
                    onClick = { onSave() },
                    colors = ButtonDefaults.buttonColors(Color(0xFFFF6F00))
                ) {
                    Text("✅", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
