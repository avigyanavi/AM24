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
    var idPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selfieUri by remember { mutableStateOf<Uri?>(null) }
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
    val authority = "${context.packageName}.fileprovider"

    // ID capture setup
    val idImageFile = remember { createTempImageFile(context) }
    val idContentUri = remember { FileProvider.getUriForFile(context, authority, idImageFile) }
    val idLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) idPhotoUri = idContentUri
        else Toast.makeText(context, "ID capture failed, please try again", Toast.LENGTH_SHORT).show()
    }

    // Selfie capture setup
    val selfieImageFile = remember { createTempImageFile(context) }
    val selfieContentUri = remember { FileProvider.getUriForFile(context, authority, selfieImageFile) }
    val selfieLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) selfieUri = selfieContentUri
        else Toast.makeText(context, "Selfie capture failed, please try again", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Verify Your Government ID & Selfie", color = Color.White, fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))

        when {
            idPhotoUri == null -> {
                // Capture ID first
                Button(
                    onClick = { idLauncher.launch(idContentUri) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                ) {
                    Text("Capture ID with Camera", color = Color.Black)
                }
            }

            selfieUri == null -> {
                // ID captured, now selfie
                Text("Great! Now please take a selfie holding your ID", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Image(
                    painter = rememberAsyncImagePainter(idPhotoUri),
                    contentDescription = "ID Preview",
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFFFF6F00), RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { selfieLauncher.launch(selfieContentUri) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                ) {
                    Text("Capture Selfie with ID", color = Color.Black)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { idPhotoUri = null }) {
                    Text("Retake ID", color = Color.LightGray)
                }
            }

            else -> {
                // Both captured: previews + submit
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(
                        painter = rememberAsyncImagePainter(idPhotoUri),
                        contentDescription = "ID Preview",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, Color(0xFFFF6F00), RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Image(
                        painter = rememberAsyncImagePainter(selfieUri),
                        contentDescription = "Selfie Preview",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, Color(0xFFFF6F00), RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { idPhotoUri = null }) {
                        Text("Retake ID", color = Color.LightGray)
                    }
                    TextButton(onClick = { selfieUri = null }) {
                        Text("Retake Selfie", color = Color.LightGray)
                    }
                    Button(
                        enabled = !isSubmitting,
                        onClick = {
                            isSubmitting = true
                            idPhotoUri?.let { idUri ->
                                selfieUri?.let { selfieUri ->
                                    // Upload ID then selfie
                                    profileViewModel.uploadGovtId(uid, idUri) { success, _ ->
                                        if (success) {
                                            profileViewModel.uploadGovtSelfie(uid, selfieUri) { success2, _ ->
                                                isSubmitting = false
                                                if (success2) navController.popBackStack()
                                            }
                                        } else {
                                            isSubmitting = false
                                        }
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
            }
        }
    }
}

/** Helper: create a temp JPEG file in cacheDir for the camera to write into */
private fun createTempImageFile(context: Context): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File.createTempFile("ID_$timestamp", ".jpg", context.cacheDir)
}
