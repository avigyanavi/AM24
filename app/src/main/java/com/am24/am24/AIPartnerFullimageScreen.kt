@file:OptIn(ExperimentalFoundationApi::class)

package com.am24.am24

import android.util.Base64
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.tasks.await

@Composable
fun AIPartnerFullImageScreen(
    navController: NavController,
    userId: String,
    timestamp: Long
) {
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // ---------- Load from cache, then RTDB if needed ----------
    LaunchedEffect(userId, timestamp) {
        // 1) try in-memory cache first
        val cached = AIPartnerImageCache.get(timestamp)
        if (cached != null) {
            bytes = cached
            isLoading = false
            return@LaunchedEffect
        }

        // 2) fallback → fetch from RTDB once
        try {
            val snap = FirebaseRefs.db
                .getReference("aiPartnerChats")
                .child(userId)
                .child(timestamp.toString())
                .get()
                .await()

            if (!snap.exists()) {
                error = "Image not found."
            } else {
                val b64 = snap.child("imageB64").getValue(String::class.java)
                if (b64.isNullOrBlank()) {
                    error = "Image not available."
                } else {
                    val decoded = Base64.decode(b64, Base64.DEFAULT)
                    bytes = decoded
                    AIPartnerImageCache.put(timestamp, decoded)   // cache it
                }
            }
        } catch (_: Exception) {
            error = "Failed to load image."
        } finally {
            isLoading = false
        }
    }

    // ---------- Zoom + pan state ----------
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)

        if (newScale == 1f) {
            // reset pan when fully zoomed out
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX += panChange.x
            offsetY += panChange.y
        }
        scale = newScale
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = error ?: "Error", color = Color.White)
                }
            }

            bytes != null -> {
                AsyncImage(
                    model = bytes!!,
                    contentDescription = "AI Partner Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                        .transformable(transformState),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // ---------- Top overlay with back button ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
                .align(Alignment.TopCenter)
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .padding(8.dp)
                    .size(38.dp)
                    .align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }
    }
}
