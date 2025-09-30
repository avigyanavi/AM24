package com.am24.am24

import android.app.Activity
import android.app.Application
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

/**
 * Simple helper to load and display App Open ads. The implementation keeps a single
 * AppOpenAd instance in memory and refreshes it after each display.
 */
class AppOpenAdManager(
    private val application: Application,
    private val adUnitId: String,
) {

    private var isLoadingAd = false
    private var isShowingAd = false
    private var appOpenAd: AppOpenAd? = null

    fun loadAd() {
        if (isLoadingAd || appOpenAd != null) {
            return
        }

        isLoadingAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            application,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "App open ad loaded")
                    appOpenAd = ad
                    isLoadingAd = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(
                        TAG,
                        "App open ad failed to load: ${error.message ?: error.toString()}"
                    )
                    isLoadingAd = false
                }
            },
        )
    }

    fun showAdIfAvailable(activity: Activity, onAdDismissed: () -> Unit = {}) {
        if (isShowingAd) {
            return
        }

        val ad = appOpenAd
        if (ad == null) {
            loadAd()
            onAdDismissed()
            return
        }

        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App open ad shown")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "App open ad dismissed")
                isShowingAd = false
                appOpenAd = null
                onAdDismissed()
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(
                    TAG,
                    "App open ad failed to show: ${error.message ?: error.toString()}"
                )
                isShowingAd = false
                appOpenAd = null
                onAdDismissed()
                loadAd()
            }
        }

        ad.show(activity)
    }

    fun isAdAvailable(): Boolean = appOpenAd != null

    companion object {
        private const val TAG = "AppOpenAdManager"
    }
}