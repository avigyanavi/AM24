package com.am24.am24

import android.app.Activity
import android.widget.Toast
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.util.Calendar

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

    fun showWithDailyLimit(
        userId: String,
        onReward: (RewardItem) -> Unit,
        afterAd: () -> Unit = {}
    ) {
        val userRef = FirebaseRefs.db.getReference("users").child(userId)
        userRef.get().addOnSuccessListener { snap ->
            val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            val lastDay = snap.child("lastRewardAdDayOfYear").getValue(Int::class.java)
            var count = snap.child("rewardedAdsToday").getValue(Int::class.java) ?: 0
            if (lastDay == null || lastDay != today) {
                count = 0
                userRef.updateChildren(
                    mapOf(
                        "rewardedAdsToday" to 0,
                        "lastRewardAdDayOfYear" to today
                    )
                )
            }

            if (count >= 1) {
                Toast.makeText(activity, "Daily limit reached", Toast.LENGTH_SHORT).show()
                afterAd()
                return@addOnSuccessListener
            }

            show(onReward = { reward ->
                onReward(reward)
                userRef.child("rewardedAdsToday").setValue(count + 1)
            }, afterAd = afterAd)
        }.addOnFailureListener {
            afterAd()
        }
    }

    fun clearCallbacks() {
        ad?.fullScreenContentCallback = null
    }
}