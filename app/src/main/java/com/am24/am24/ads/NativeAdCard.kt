package com.am24.am24.ads

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.am24.am24.R
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

@Composable
fun NativeAdCard(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(adUnitId) {
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { loadedAd ->
                nativeAd?.destroy()
                nativeAd = loadedAd
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    nativeAd = null
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())

        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LayoutInflater.from(ctx).inflate(R.layout.ad_native, null) as NativeAdView
        },
        update = { adView ->
            val ad = nativeAd ?: return@AndroidView
            val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
            val bodyView = adView.findViewById<TextView>(R.id.ad_body)
            val ctaView = adView.findViewById<Button>(R.id.ad_call_to_action)
            val mediaView = adView.findViewById<MediaView>(R.id.ad_media)

            headlineView.text = ad.headline
            adView.headlineView = headlineView
            adView.mediaView = mediaView

            if (ad.body.isNullOrBlank()) {
                bodyView.visibility = View.GONE
            } else {
                bodyView.text = ad.body
                bodyView.visibility = View.VISIBLE
                adView.bodyView = bodyView
            }

            if (ad.callToAction.isNullOrBlank()) {
                ctaView.visibility = View.GONE
            } else {
                ctaView.text = ad.callToAction
                ctaView.visibility = View.VISIBLE
                adView.callToActionView = ctaView
            }

            if (ad.mediaContent == null) {
                mediaView.visibility = View.GONE
            } else {
                mediaView.visibility = View.VISIBLE
                mediaView.setMediaContent(ad.mediaContent)
            }

            adView.setNativeAd(ad)
        }
    )
}