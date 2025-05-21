package com.am24.am24.util

import android.net.Uri
import android.view.LayoutInflater
import com.am24.am24.R        // or your actual app package for R
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.am24.am24.VideoCacheProvider

/**
 * Shows a video in a Dialog, backed by ExoPlayer + SimpleCache.
 */
@OptIn(UnstableApi::class)
@Composable
fun CachedFullscreenVideoPlayer(
    uri: Uri,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // ① grab your singleton SimpleCache
    val cache        = VideoCacheProvider.getInstance(context)
    val upstream     = DefaultDataSource.Factory(context)
    val cacheFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)

    // ② build & remember an ExoPlayer that uses our cache-aware source
    var player = remember(uri) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory))
            .build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(uri) {
        onDispose { player.release() }
    }

    // ③ wrap in a full-screen Dialog
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize()) {
            AndroidView({ ctx ->
                PlayerView(ctx).apply {
                    player       = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) // Optional: Show buffering indicator
                }
            }, Modifier.fillMaxSize())

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
@OptIn(UnstableApi::class)
@Composable
fun TextureFullscreenVideoPlayer(
    uri: Uri,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // ① your cache + data source
    val cache        = VideoCacheProvider.getInstance(context)
    val upstream     = DefaultDataSource.Factory(context)
    val cacheFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)

    // ② build & remember ExoPlayer
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(uri) {
        onDispose { exoPlayer.release() }
    }

    // ③ show in Dialog, inflate our XML
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier
            .fillMaxSize()
            .background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    LayoutInflater.from(ctx)
                        .inflate(R.layout.fullscreen_player, null)
                        .also { view ->
                            (view as PlayerView).player = exoPlayer
                        }
                },
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

