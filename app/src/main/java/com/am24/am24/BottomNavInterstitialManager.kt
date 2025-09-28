package com.am24.am24

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class BottomNavInterstitialManager {

    private var interstitialAd: InterstitialAd? = null
    private var isLoadingAd = false
    private var shouldShowAds = false
    private var adUnitId: String? = null
    private var selectedCountry: String? = null

    fun updateEligibility(context: Context, selectedCountry: String?, shouldShow: Boolean) {
        this.selectedCountry = selectedCountry
        shouldShowAds = shouldShow

        if (!shouldShow) {
            Log.d(TAG, "Bottom-nav interstitials disabled")
            interstitialAd = null
            adUnitId = null
            isLoadingAd = false
            return
        }

        val resolvedId = AdUnitIds.bottomNavInterstitial(context, selectedCountry)
        if (resolvedId == null) {
            Log.d(TAG, "No bottom-nav interstitial configured for this region")
            interstitialAd = null
            adUnitId = null
            isLoadingAd = false
            return
        }

        if (resolvedId != adUnitId) {
            Log.d(TAG, "Switching bottom-nav interstitial ad unit")
            adUnitId = resolvedId
            interstitialAd = null
        }

        if (interstitialAd == null && !isLoadingAd) {
            loadAd(context)
        }
    }

    fun show(activity: Activity?, onFinished: () -> Unit) {
        if (activity == null) {
            onFinished()
            return
        }

        if (!shouldShowAds) {
            onFinished()
            return
        }

        if (adUnitId == null) {
            adUnitId = AdUnitIds.bottomNavInterstitial(activity, selectedCountry)
            if (adUnitId == null) {
                onFinished()
                return
            }
        }

        val ad = interstitialAd
        if (ad == null) {
            loadAd(activity)
            onFinished()
            return
        }

        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Bottom-nav interstitial dismissed")
                loadAd(activity)
                onFinished()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Failed to show bottom-nav interstitial: ${adError.message}")
                loadAd(activity)
                onFinished()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Bottom-nav interstitial shown")
            }
        }

        ad.show(activity)
    }

    private fun loadAd(context: Context) {
        if (!shouldShowAds) return
        if (isLoadingAd) return

        val unitId = adUnitId ?: AdUnitIds.bottomNavInterstitial(context, selectedCountry) ?: return

        isLoadingAd = true
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            unitId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Bottom-nav interstitial loaded")
                    interstitialAd = ad
                    isLoadingAd = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Failed to load bottom-nav interstitial: ${error.message}")
                    interstitialAd = null
                    isLoadingAd = false
                }
            }
        )
    }

    companion object {
        private const val TAG = "BottomNavInterstitial"
    }
}