package com.am24.am24

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.lang.ref.WeakReference

class AppOpenAdManager {

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTime: Long = 0L
    private var shouldShowAds = false
    private var adUnitId: String? = null
    private var selectedCountry: String? = null
    private var currentActivityRef: WeakReference<Activity>? = null
    private var showOnLoad = false

    fun updateEligibility(context: Context, selectedCountry: String?, shouldShow: Boolean) {
        this.selectedCountry = selectedCountry
        shouldShowAds = shouldShow
        if (!shouldShow) {
            Log.d(TAG, "App open ads disabled for current user")
            showOnLoad = false
            appOpenAd = null
            return
        }

        val resolvedId = resolveAdUnitId(context, selectedCountry)
        if (resolvedId == null) {
            Log.d(TAG, "No app open ad unit configured for this region")
            adUnitId = null
            appOpenAd = null
            showOnLoad = false
            return
        }

        if (resolvedId != adUnitId) {
            Log.d(TAG, "Switching app open ad unit to region-specific id")
            adUnitId = resolvedId
            appOpenAd = null
        }

        if (!isAdAvailable()) {
            loadAd(context)
        }
    }

    fun showAdIfAvailable(activity: Activity) {
        currentActivityRef = WeakReference(activity)
        if (!shouldShowAds) {
            showOnLoad = false
            return
        }
        if (isShowingAd) {
            Log.d(TAG, "App open ad already showing; skipping")
            return
        }
        if (adUnitId == null) {
            adUnitId = resolveAdUnitId(activity, selectedCountry)
        }
        if (adUnitId == null) {
            Log.d(TAG, "App open ad unit is null; cannot show")
            return
        }

        if (!isAdAvailable()) {
            Log.d(TAG, "App open ad not ready yet; loading")
            showOnLoad = true
            loadAd(activity)
            return
        }

        showOnLoad = false
        val ad = appOpenAd ?: return
        isShowingAd = true

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "App open ad dismissed")
                appOpenAd = null
                isShowingAd = false
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Failed to show app open ad: ${adError.message}")
                appOpenAd = null
                isShowingAd = false
                loadAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App open ad displayed")
            }
        }

        ad.show(activity)
    }

    private fun loadAd(context: Context) {
        if (!shouldShowAds) return
        if (isLoadingAd) return

        val unitId = adUnitId ?: resolveAdUnitId(context, selectedCountry) ?: return
        if (isAdAvailable()) return

        isLoadingAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            unitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "App open ad loaded")
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = System.currentTimeMillis()
                    if (showOnLoad) {
                        showOnLoad = false
                        currentActivity()?.let { activity ->
                            if (!activity.isFinishing && !activity.isDestroyedCompat()) {
                                showAdIfAvailable(activity)
                            }
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Failed to load app open ad: ${error.message}")
                    isLoadingAd = false
                    appOpenAd = null
                }
            }
        )
    }

    private fun isAdAvailable(): Boolean {
        val ad = appOpenAd ?: return false
        val elapsed = System.currentTimeMillis() - loadTime
        return elapsed < AD_VALIDITY_MS
    }

    private fun resolveAdUnitId(context: Context, selectedCountry: String?): String? {
        return when {
            CountryUtil.isIndia(context, selectedCountry) -> AD_UNIT_INDIA
            CountryUtil.isMexico(context, selectedCountry) -> AD_UNIT_MEXICO
            CountryUtil.isUnitedStates(context, selectedCountry) -> AD_UNIT_USA
            else -> null
        }
    }

    private fun currentActivity(): Activity? = currentActivityRef?.get()

    private fun Activity.isDestroyedCompat(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            isDestroyed
        } else {
            false
        }
    }

    companion object {
        private const val TAG = "AppOpenAdManager"
        private const val AD_VALIDITY_MS = 4 * 60 * 60 * 1000L // 4 hours

        private const val AD_UNIT_INDIA = "ca-app-pub-1814829133495225/1128130556"
        private const val AD_UNIT_MEXICO = "ca-app-pub-1814829133495225/7901901393"
        private const val AD_UNIT_USA = "ca-app-pub-1814829133495225/9901378772"
    }
}