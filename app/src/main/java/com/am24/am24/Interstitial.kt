package com.am24.am24

import android.app.Activity
import android.content.Context
import com.facebook.ads.*

// InterstitialAdManager.kt
class InterstitialAdManager(
    private val activity: Activity,
    private val adUnitId: String
) {
    private var interstitial: InterstitialAd? = null
    private var afterAd: (() -> Unit)? = null

    // --- NEW: load guards ---
    private var isLoading = false
    private var lastLoadAt = 0L
    private var retryMs = 2000L
    private val minCooldownMs = 10_000L   // 10s between load attempts (tune to 30s if needed)
    // ------------------------

    init { loadIfAllowed(reason = "init") }

    private fun canLoadNow(): Boolean {
        val now = System.currentTimeMillis()
        return !isLoading && (now - lastLoadAt) >= minCooldownMs
    }

    private fun loadIfAllowed(reason: String) {
        if (!canLoadNow()) {
            android.util.Log.d("FAN", "Skip load ($reason): isLoading=$isLoading, sinceLast=${System.currentTimeMillis() - lastLoadAt}ms")
            return
        }
        isLoading = true
        lastLoadAt = System.currentTimeMillis()

        val ad = InterstitialAd(activity, adUnitId)
        interstitial = ad
        android.util.Log.d("FAN", "Loading interstitial ($reason)")

        ad.loadAd(
            ad.buildLoadAdConfig()
                .withAdListener(object : InterstitialAdListener {
                    override fun onAdLoaded(ad: Ad) {
                        android.util.Log.d("FAN", "Ad loaded")
                        isLoading = false
                        retryMs = 2000L
                    }

                    override fun onError(ad: Ad?, error: AdError) {
                        android.util.Log.e("FAN", "Load error: ${error.errorMessage}")
                        isLoading = false
                        interstitial = null
                        // Exponential backoff + respect cooldown on next call
                        activity.window.decorView.postDelayed(
                            { loadIfAllowed(reason = "backoff") },
                            retryMs
                        )
                        retryMs = (retryMs * 2).coerceAtMost(60_000L)
                    }

                    override fun onInterstitialDisplayed(ad: Ad) {
                        android.util.Log.d("FAN", "Shown")
                    }

                    override fun onInterstitialDismissed(ad: Ad) {
                        android.util.Log.d("FAN", "Dismissed → queue next load")
                        interstitial?.destroy()
                        interstitial = null
                        // reset cooldown timer to avoid “too frequent” right after dismiss
                        lastLoadAt = System.currentTimeMillis()
                        activity.window.decorView.postDelayed(
                            { loadIfAllowed(reason = "afterDismiss") },
                            2_000L
                        )
                        afterAd?.invoke()
                        afterAd = null
                    }

                    override fun onAdClicked(ad: Ad) {}
                    override fun onLoggingImpression(ad: Ad) {}
                })
                .build()
        )
    }

    /** Show if ready; otherwise just run fallback and **do not** spam new loads */
    fun show(afterAd: () -> Unit) {
        val ad = interstitial
        if (ad?.isAdLoaded == true && !ad.isAdInvalidated) {
            this.afterAd = afterAd
            ad.show()
        } else {
            android.util.Log.d("FAN", "Not ready; run fallback")
            afterAd()
            // gently request a load if allowed; no storm
            loadIfAllowed(reason = "show-not-ready")
        }
    }

    fun destroy() {
        interstitial?.destroy()
        interstitial = null
        isLoading = false
    }
}

