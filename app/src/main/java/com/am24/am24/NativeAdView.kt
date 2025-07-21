package com.am24.am24

import android.content.Context
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import android.widget.Button as AndroidButton
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

// 1) Composable that loads a NativeAd into state, once:
@Composable
fun ComposeNativeAd(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    // kick off load
    LaunchedEffect(adUnitId) {
        val loader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                nativeAd?.destroy()
                nativeAd = ad
            }
            .build()
        loader.loadAd(AdRequest.Builder().build())
    }

    if (nativeAd == null) {
        // still loading: reserve a 200dp slot and center spinner
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color(0xFFFF6F00),
                strokeWidth = 2.dp
            )
        }
    } else {
        // loaded: let the ad measure itself naturally
        NativeAdCard(
            ad = nativeAd!!,
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight()
        )
    }
}

// 2) Pure-Compose “layout” for a single NativeAd
@Composable
fun NativeAdCard(
    ad: NativeAd,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        factory = { ctx ->
            val density = ctx.resources.displayMetrics.density

            // 1) NativeAdView container
            val adView = NativeAdView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(AndroidColor.parseColor("#1A1A1A"))
            }

            // 2) Asset views
            val headlineView = TextView(ctx).apply {
                setTextColor(AndroidColor.WHITE); textSize = 4f
            }
            val bodyView     = TextView(ctx).apply {
                setTextColor(AndroidColor.LTGRAY); textSize = 14f
            }
            val iconView     = ImageView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    (40 * density).toInt(),
                    (40 * density).toInt()
                )
            }
            // ← changed here: use WRAP_CONTENT for height
            val mediaView    = MediaView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val ctaView      = AndroidButton(ctx).apply {
                text = ad.callToAction
                setBackgroundColor(AndroidColor.parseColor("#FFDB00"))
                setTextColor(AndroidColor.BLACK)
            }

            // 3) Wire up the asset views
            adView.headlineView     = headlineView
            adView.bodyView         = bodyView
            adView.iconView         = iconView
            adView.mediaView        = mediaView
            adView.callToActionView = ctaView

            // 4) Layout in code
            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(headlineView)
                addView(iconView)
                addView(mediaView)
                addView(ctaView)
            }
            adView.addView(container)

            // 5) Register the ad
            adView.setNativeAd(ad)
            adView
        },
        update = { adView ->
            adView.setNativeAd(ad)
        }
    )
}