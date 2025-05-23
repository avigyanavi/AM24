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
    val uid = FirebaseAuth.getInstance().currentUser?.uid
        ?: run {
            Toast.makeText(context, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

    // --- UI state ---
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var verifStatus by remember { mutableStateOf<String?>(null) }
    var verifPhotoUrl by remember { mutableStateOf<String?>(null) }

    // 1) Pending view
    if (verifStatus == "pending") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Verification Pending", color = Color.White, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))
            verifPhotoUrl?.let { url ->
                Image(
                    painter = rememberAsyncImagePainter(url),
                    contentDescription = "ID Pending Review",
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, Color.Gray, RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
        return
    }

    // 2) Accepted view
    if (verifStatus == "accepted") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("You're already verified", color = Color(0xFF00C853), fontSize = 20.sp)
        }
        return
    }

    // 3) Rejected view
    if (verifStatus == "rejected") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Photo rejected, try again", color = Color.Red, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { /* retry logic already triggered in listener */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
            ) {
                Text("Capture New ID", color = Color.Black)
            }
        }
        return
    }

    // --- initial or no verification yet ---
    // prepare temp file + Uri
    val imageFile = remember { createTempImageFile(context) }
    val authority = "${context.packageName}.fileprovider"
    val contentUri = remember { FileProvider.getUriForFile(context, authority, imageFile) }
    val cameraLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) photoUri = contentUri
        else Toast.makeText(context, "Capture failed, please try again", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Verify Your Government-ID", color = Color.White, fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))

        if (photoUri != null) {
            // preview + actions
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

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(
                    onClick = { photoUri = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Retake", color = Color.White)
                }
                Button(
                    enabled = !isSubmitting,
                    onClick = {
                        photoUri?.let { uri ->
                            isSubmitting = true
                            profileViewModel.uploadGovtId(uid, uri) { success, _ ->
                                isSubmitting = false
                                if (success) {
                                    // navigate back; pending state will show on next visit
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
            // capture button
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