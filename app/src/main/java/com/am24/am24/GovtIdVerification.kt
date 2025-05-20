package com.am24.am24

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GovtIdVerificationScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel
) {
    val context = LocalContext.current
    // make sure the user is logged in
    val uid = FirebaseAuth.getInstance().currentUser?.uid
        ?: run {
            Toast.makeText(context, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

    // local UI state
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // 1) prepare a temp file, 2) get its content Uri
    val imageFile = remember { createTempImageFile(context) }
    val authority = "${context.packageName}.provider"
    val contentUri = remember { FileProvider.getUriForFile(context, authority, imageFile) }

    // camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) {
            photoUri = contentUri
        } else {
            Toast.makeText(context, "Capture failed, please try again", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Verify Your Government-ID",
            color = Color.White,
            fontSize = 22.sp
        )
        Spacer(Modifier.height(16.dp))

        // if we've got a snapshot, show it
        if (photoUri != null) {
            Image(
                painter = rememberAsyncImagePainter(photoUri),
                contentDescription = "ID Preview",
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color(0xFFFF6F00), RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Retake
                Button(
                    onClick = { photoUri = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Retake", color = Color.White)
                }

                // Submit
                Button(
                    enabled = !isSubmitting,
                    onClick = {
                        photoUri?.let { uri ->
                            isSubmitting = true
                            profileViewModel.uploadGovtId(
                                uid,
                                uri
                            ) { success, message ->
                                isSubmitting = false
                                if (success) {
                                    // mark the user as verified in their profile
                                    profileViewModel.markUserVerified(uid)
                                    navController.popBackStack()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                ) {
                    Text(
                        if (isSubmitting) "Submitting…" else "Submit for Review",
                        color = Color.White
                    )
                }
            }
        } else {
            // no photo yet → launch camera
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { cameraLauncher.launch(contentUri) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
            ) {
                Text("Capture ID with Camera", color = Color.Black)
            }
        }
    }
}

/** Helper: create a temp JPEG file in cacheDir for the camera to write into */
private fun createTempImageFile(context: Context): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File.createTempFile("ID_$timestamp", ".jpg", context.cacheDir)
}
