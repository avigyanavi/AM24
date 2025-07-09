package com.am24.am24

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class RewardedAdManager(private val activity: Activity, private val adUnitId: String) {
    private var ad: RewardedAd? = null

    init {
        load()
    }

    private fun load() {
        val request = AdRequest.Builder().build()
        RewardedAd.load(activity, adUnitId, request, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(loaded: RewardedAd) {
                ad = loaded
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                ad = null
            }
        })
    }

    fun show(onReward: (RewardItem) -> Unit, afterAd: () -> Unit = {}) {
        val current = ad
        if (current == null) {
            afterAd()
            return
        }
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                load()
                afterAd()
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                ad = null
                load()
                afterAd()
            }
        }
        current.show(activity) { reward ->
            onReward(reward)
        }
    }

    fun clearCallbacks() {
        ad?.fullScreenContentCallback = null
    }
}