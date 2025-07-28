package com.am24.am24

import android.content.Context

object AdUnitIds {
    private const val NATIVE_IN = "ca-app-pub-5094389629300846/4057317007"
    private const val INTERSTITIAL_IN = "ca-app-pub-5094389629300846/6822241797"
    private const val REWARDED_BOOST_IN = "ca-app-pub-5094389629300846/4203186426"
    private const val REWARDED_COMPLIMENT_IN = "ca-app-pub-5094389629300846/6893779002"
    private const val REWARDED_SWIPE_IN = "ca-app-pub-5094389629300846/2665106990"
    private const val REWARDED_LOTTERY_IN = "ca-app-pub-5094389629300846/6888542594"

    private const val NATIVE_GLOBAL = "ca-app-pub-5094389629300846/2384779661"
    private const val INTERSTITIAL_GLOBAL = "ca-app-pub-5094389629300846/3679665569"
    private const val REWARDED_BOOST_GLOBAL = "ca-app-pub-5094389629300846/2875233243"
    private const val REWARDED_COMPLIMENT_GLOBAL = "ca-app-pub-5094389629300846/7852728769"
    private const val REWARDED_SWIPE_GLOBAL = "ca-app-pub-5094389629300846/8487153585"
    private const val REWARDED_LOTTERY_GLOBAL = "ca-app-pub-5094389629300846/6986369652"

    private fun isIndia(ctx: Context) = CountryUtil.isProbablyInIndia(ctx)

    fun native(ctx: Context) = if (isIndia(ctx)) NATIVE_IN else NATIVE_GLOBAL
    fun interstitial(ctx: Context) = if (isIndia(ctx)) INTERSTITIAL_IN else INTERSTITIAL_GLOBAL
    fun rewardedBoost(ctx: Context) = if (isIndia(ctx)) REWARDED_BOOST_IN else REWARDED_BOOST_GLOBAL
    fun rewardedCompliment(ctx: Context) = if (isIndia(ctx)) REWARDED_COMPLIMENT_IN else REWARDED_COMPLIMENT_GLOBAL
    fun rewardedSwipe(ctx: Context) = if (isIndia(ctx)) REWARDED_SWIPE_IN else REWARDED_SWIPE_GLOBAL
    fun rewardedLottery(ctx: Context) = if (isIndia(ctx)) REWARDED_LOTTERY_IN else REWARDED_LOTTERY_GLOBAL
}