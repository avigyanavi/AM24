package com.am24.am24

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
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

    /* ---------- State ---------- */
    var idPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selfieUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var verifStatus by remember { mutableStateOf<String?>(null) }
    var verifPhotoUrl by remember { mutableStateOf<String?>(null) }

    /* ---------- Runtime-permission plumbing ---------- */
    // holds a lambda we want to run once the user grants permission
    var pendingLaunch by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        val action = pendingLaunch
        pendingLaunch = null
        if (granted) {
            action?.invoke()
        } else {
            Toast.makeText(
                context,
                "Camera permission is required to take a photo",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun requireCameraThen(run: () -> Unit) {
        val cameraGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        val readImagesGranted =
            if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
            } else true

        if (cameraGranted && readImagesGranted) {
            run()
        } else {
            pendingLaunch = run
            val perms =
                if (Build.VERSION.SDK_INT >= 33)
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_MEDIA_IMAGES
                    )
                else arrayOf(Manifest.permission.CAMERA)
            permLauncher.launch(perms)
        }
    }

    /* ---------- Launchers ---------- */
    val authority = "${context.packageName}.fileprovider"

    val idFile = remember { createTempImageFile(context) }
    val idUri = remember { FileProvider.getUriForFile(context, authority, idFile) }
    val idLauncher = rememberLauncherForActivityResult(TakePicture()) { ok ->
        if (ok) idPhotoUri = idUri
        else Toast.makeText(context, "ID capture failed", Toast.LENGTH_SHORT).show()
    }

    val selfieFile = remember { createTempImageFile(context) }
    val selfieUriTemp =
        remember { FileProvider.getUriForFile(context, authority, selfieFile) }
    val selfieLauncher = rememberLauncherForActivityResult(TakePicture()) { ok ->
        if (ok) selfieUri = selfieUriTemp
        else Toast.makeText(context, "Selfie capture failed", Toast.LENGTH_SHORT).show()
    }

    /* ---------- Early-return states ---------- */
    when (verifStatus) {
        "pending" -> {
            PendingView(verifPhotoUrl)
            return
        }

        "accepted" -> {
            AcceptedView()
            return
        }

        "rejected" -> {
            RejectedView()
            return
        }
    }

    /* ---------- Main UI ---------- */
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
                Button(
                    onClick = {
                        requireCameraThen { idLauncher.launch(idUri) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                ) {
                    Text("Capture ID with Camera", color = Color.Black)
                }
            }

            selfieUri == null -> {
                Text(
                    "Great! Now please take a selfie holding your ID",
                    color = Color.White,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(12.dp))
                ImagePreview(idPhotoUri!!)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        requireCameraThen { selfieLauncher.launch(selfieUriTemp) }
                    },
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
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ImagePreview(idPhotoUri!!)
                    ImagePreview(selfieUri!!)
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
                            idPhotoUri?.let { idU ->
                                selfieUri?.let { selfieU ->
                                    profileViewModel.uploadGovtId(uid, idU) { ok, _ ->
                                        if (ok) {
                                            profileViewModel.uploadGovtSelfie(uid, selfieU) { ok2, _ ->
                                                isSubmitting = false
                                                if (ok2) navController.popBackStack()
                                            }
                                        } else isSubmitting = false
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

/* ---------- Small helper composables ---------- */
@Composable
private fun PendingView(photoUrl: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Verification Pending", color = Color.White, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        photoUrl?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = null,
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color.Gray, RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun AcceptedView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("You're already verified", color = Color(0xFF00C853), fontSize = 20.sp)
    }
}

@Composable
private fun RejectedView() {
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
            onClick = { /* retry handled elsewhere */ },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
        ) {
            Text("Capture New ID", color = Color.Black)
        }
    }
}

@Composable
private fun ImagePreview(uri: Uri) {
    Image(
        painter = rememberAsyncImagePainter(uri),
        contentDescription = null,
        modifier = Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, Color(0xFFFF6F00), RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
    )
}

/* ---------- File helper ---------- */
private fun createTempImageFile(context: Context): File {
    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File.createTempFile("ID_$ts", ".jpg", context.cacheDir)
}
