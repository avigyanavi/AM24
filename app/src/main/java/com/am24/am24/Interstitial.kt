package com.am24.am24

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.*

/**
 * Small wrapper that:  ▸ loads one ad  ▸ shows it  ▸ auto-reloads the next one.
 */
class InterstitialAdManager(
    private val activity: Activity,
    private val adUnitId: String
) {
    private var ad: InterstitialAd? = null

    init { load() }

    private fun load() {
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            activity, adUnitId, request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) { ad = loaded }
                override fun onAdFailedToLoad(err: LoadAdError) { ad = null }
            }
        )
    }

    /**
     * Shows the ad *if ready*, otherwise runs [afterAd].
     * Always reloads a fresh one afterwards.
     */
    fun show(afterAd: () -> Unit) {
        ad?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                load()
                afterAd()
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                ad = null
                load()
                afterAd()
            }
        }
        ad?.show(activity) ?: afterAd()          // show or fall back immediately
    }
}

