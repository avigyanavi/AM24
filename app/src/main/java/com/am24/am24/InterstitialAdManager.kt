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

    fun show(
        activity: Activity,
        adUnitId: String,
        onDismissed: () -> Unit,
        onFailed: (String?) -> Unit
    ) {
        val ad = interstitialAd
        if (ad != null) {
            showLoadedAd(activity, ad, onDismissed, onFailed)
            return
        }

        if (isLoading) {
            onFailed(null)
            return
        }

        isLoading = true
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loadedAd: InterstitialAd) {
                    interstitialAd = loadedAd
                    isLoading = false
                    showLoadedAd(activity, loadedAd, onDismissed, onFailed)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                    onFailed(error.message)
                }
            }
        )
    }

    private fun showLoadedAd(
        activity: Activity,
        ad: InterstitialAd,
        onDismissed: () -> Unit,
        onFailed: (String?) -> Unit
    ) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                onFailed(adError.message)
            }
        }
        ad.show(activity)
        interstitialAd = null
    }
}