package com.am24.am24.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class InterstitialAdManager(private val context: Context) {
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    fun preload(adUnitId: String) {
        if (isLoading || interstitialAd != null) return
        isLoading = true
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    fun show(
        activity: Activity,
        adUnitId: String,
        onDismissed: () -> Unit,
        onFailed: (String?) -> Unit
    ) {
        val ad = interstitialAd
        if (ad == null) {
            preload(adUnitId)
            onFailed(null)
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                preload(adUnitId)
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                preload(adUnitId)
                onFailed(adError.message)
            }
        }
        ad.show(activity)
        interstitialAd = null
    }
}