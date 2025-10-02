package com.am24.am24

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.platform.LocalContext

import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PrivateAlbumScreen(
    navController: NavController,
    userId: String,
    isOwner: Boolean,
) {
    var urls by remember { mutableStateOf<List<String>>(emptyList()) }
    var fullScreenUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val storageRef = FirebaseRefs.storage.reference

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val ref = storageRef.child("users/$userId/private/${uri.lastPathSegment ?: System.currentTimeMillis()}")
        ref.putFile(uri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    val updated = urls.toMutableList().apply { add(downloadUri.toString()) }
                    urls = updated
                    scope.launch {
                        FirebaseRefs.db.getReference("users/$userId/privateAlbumUrls").setValue(updated)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("UploadMedia", "Private-media upload failed: ${e.message}")
            }
    }

    LaunchedEffect(userId) {
        val snap = FirebaseRefs.db.getReference("users").child(userId).get().await()
        val profile = snap.getValue(Profile::class.java)
        urls = profile?.privateAlbumUrls ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.private_album_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        floatingActionButton = {
            if (isOwner) {
                FloatingActionButton(
                    onClick = {
                        if (urls.size >= 10) {
                            Toast.makeText(context, "Maximum 10 items", Toast.LENGTH_SHORT).show()
                        } else {
                            pickerLauncher.launch("*/*")
                        }
                    },
                    containerColor = Color(0xFFFF6000)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                }
            }
        },
        containerColor = Color.Black
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .padding(innerPadding)
        ) {
            itemsIndexed(urls) { index, url ->
                Box(modifier = Modifier.padding(4.dp)) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { fullScreenUrl = url },
                        contentScale = ContentScale.Crop
                    )
                    if (isOwner) {
                        IconButton(
                            onClick = {
                                val mutable = urls.toMutableList()
                                mutable.removeAt(index)
                                urls = mutable
                                scope.launch {
                                    FirebaseRefs.db.getReference("users/$userId/privateAlbumUrls").setValue(mutable)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    fullScreenUrl?.let { url ->
        Dialog(onDismissRequest = { fullScreenUrl = null }) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentScale = ContentScale.Fit
            )
        }
    }
}