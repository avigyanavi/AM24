package com.am24.am24


import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.util.*
import android.Manifest
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle

class CreatePostActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: DatabaseReference
    private lateinit var storage: FirebaseStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance("https://am-twentyfour-default-rtdb.firebaseio.com/").reference
        storage = FirebaseStorage.getInstance()
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        setContent {
            CreatePostScreen(
                onNavigateToPhotoPost = { navigateToPhotoPost() },
                onNavigateToVideoPost = { navigateToVideoPost() }
            )
        }
    }

    private fun navigateToPhotoPost() {
        startActivity(Intent(this, PhotoPostActivity::class.java))
    }

    private fun navigateToVideoPost() {
        startActivity(Intent(this, VideoPostActivity::class.java))
    }
}

@Composable
fun CreatePostScreen(
    onNavigateToPhotoPost: () -> Unit,
    onNavigateToVideoPost: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Create a Post", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = onNavigateToPhotoPost) {
                Text("Photo")
            }
            Button(onClick = onNavigateToVideoPost) {
                Text("Video")
            }
        }
    }
}


// Updated PhotoPostActivity
class PhotoPostActivity : ComponentActivity() {

    private lateinit var storage: FirebaseStorage
    private lateinit var auth: FirebaseAuth
    private lateinit var db: DatabaseReference
    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>
    private var photoUri: Uri? = null
    private var loading by mutableStateOf(false) // Move loading to be a state variable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        db = FirebaseDatabase.getInstance().reference

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { uploadPhotoAndNavigate(it) }
        }

        cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                photoUri?.let { uploadPhotoAndNavigate(it) }
            } else {
                Toast.makeText(this, "Failed to capture photo", Toast.LENGTH_SHORT).show()
            }
        }
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        setContent {
            PhotoPostScreen(
                onUpload = { launchImagePicker() },
                onTakePhoto = { checkCameraPermissionAndCapturePhoto() },
                loading = loading // Pass loading state to UI
            )
        }
    }

    private fun launchImagePicker() {
        imagePickerLauncher.launch("image/*")
    }

    private fun checkCameraPermissionAndCapturePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            capturePhoto()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        }
    }

    private fun capturePhoto() {
        val photoFile: File? = File.createTempFile("photo_${UUID.randomUUID()}", ".jpg", cacheDir)
        photoUri = photoFile?.let {
            FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", it)
        }
        photoUri?.let {
            cameraLauncher.launch(it)
        }
    }

    private fun uploadPhotoAndNavigate(photoUri: Uri) {
        loading = true // Start showing loading indicator

        val storageRef = storage.reference.child("photos/${UUID.randomUUID()}.jpg")
        storageRef.putFile(photoUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    loading = false // Stop showing loading indicator

                    val intent = Intent(this, PostDetailsActivity::class.java).apply {
                        putExtra("postType", PostType.PHOTO.name)
                        putExtra("mediaUrl", downloadUri.toString())
                    }
                    startActivity(intent)
                    finish()
                }
            }
            .addOnFailureListener {
                loading = false // Stop showing loading indicator
                Toast.makeText(this, "Failed to upload photo", Toast.LENGTH_SHORT).show()
            }
    }
}

@Composable
fun PhotoPostScreen(
    onUpload: () -> Unit,
    onTakePhoto: () -> Unit,
    loading: Boolean // Receive loading state
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Create a Photo Post", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            // Show loading indicator when media is being uploaded
            CircularProgressIndicator(modifier = Modifier.size(50.dp))
        } else {
            // Only show buttons if not loading
            Button(onClick = onUpload) {
                Text("Upload Photo")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onTakePhoto) {
                Text("Take Photo")
            }
        }
    }
}


// Updated VideoPostActivity
class VideoPostActivity : ComponentActivity() {

    private lateinit var storage: FirebaseStorage
    private lateinit var auth: FirebaseAuth
    private lateinit var db: DatabaseReference
    private lateinit var videoPickerLauncher: ActivityResultLauncher<String>
    private lateinit var videoCaptureLauncher: ActivityResultLauncher<Uri>
    private var videoUri: Uri? = null

    // Request code for camera permission
    private val CAMERA_PERMISSION_REQUEST_CODE = 1002

    // State to control the visibility of the loading indicator
    private var loading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        db = FirebaseDatabase.getInstance().reference

        // Initialize video picker launcher
        videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { uploadVideoAndNavigate(it) }
        }

        // Initialize video capture launcher
        videoCaptureLauncher = registerForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
            if (success) {
                videoUri?.let { uploadVideoAndNavigate(it) }
            } else {
                Toast.makeText(this, "Failed to record video", Toast.LENGTH_SHORT).show()
            }
        }
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        setContent {
            VideoPostScreen(
                onUpload = { launchVideoPicker() }, // Video picker trigger function
                onRecordVideo = { checkCameraPermissionAndRecordVideo() }, // Check permission before recording
                loading = loading // Pass loading state to the UI
            )
        }
    }

    // Inline function to trigger video picker
    private fun launchVideoPicker() {
        videoPickerLauncher.launch("video/*")
    }

    // Check for camera permission before recording the video
    private fun checkCameraPermissionAndRecordVideo() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // Permission is granted, proceed to record video
            recordVideo()
        } else {
            // Request the camera permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        }
    }

    // Record video after permission is granted
    private fun recordVideo() {
        val videoFile: File? = File.createTempFile("video_${UUID.randomUUID()}", ".mp4", cacheDir)
        videoUri = videoFile?.let {
            FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", it)
        }
        videoUri?.let {
            videoCaptureLauncher.launch(it)
        }
    }

    // Upload video and navigate to PostDetailsActivity
    private fun uploadVideoAndNavigate(videoUri: Uri) {
        // Start showing loading indicator
        loading = true

        val storageRef = storage.reference.child("videos/${UUID.randomUUID()}.mp4")
        storageRef.putFile(videoUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    // Stop showing loading indicator
                    loading = false

                    val intent = Intent(this, PostDetailsActivity::class.java).apply {
                        putExtra("postType", PostType.VIDEO.name)
                        putExtra("mediaUrl", downloadUri.toString())
                    }
                    startActivity(intent)
                    finish()
                }
            }
            .addOnFailureListener {
                // Stop showing loading indicator
                loading = false

                Toast.makeText(this, "Failed to upload video", Toast.LENGTH_SHORT).show()
            }
    }

    // Handle the result of the camera permission request
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permission granted, proceed with recording the video
                recordVideo()
            } else {
                // Permission denied
                Toast.makeText(this, "Camera permission is required to record a video", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun VideoPostScreen(
    onUpload: () -> Unit,
    onRecordVideo: () -> Unit,
    loading: Boolean // Receive the loading state
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Create a Video Post", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            // Show loading indicator during media upload
            CircularProgressIndicator(modifier = Modifier.size(50.dp))
        } else {
            Button(onClick = onUpload) {
                Text("Upload Video")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onRecordVideo) {
                Text("Record Video")
            }
        }
    }
}
