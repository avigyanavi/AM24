package com.am24.am24

import android.content.Context

object AdUnitIds {
    private const val NATIVE_IN = "ca-app-pub-3206974923602803/4088886768"
    private const val INTERSTITIAL_IN = "ca-app-pub-3206974923602803/6227964942"
    private const val REWARDED_BOOST_IN = "ca-app-pub-5094389629300846/4203186426"
    private const val REWARDED_COMPLIMENT_IN = "ca-app-pub-3206974923602803/3321781760"
    private const val REWARDED_SWIPE_IN = "ca-app-pub-3206974923602803/9341213447"

    private const val NATIVE_GLOBAL = "ca-app-pub-3206974923602803/8662556597"
    private const val REWARDED_BOOST_GLOBAL = "ca-app-pub-5094389629300846/2875233243"
    private const val INTERSTITIAL_GLOBAL = "ca-app-pub-3206974923602803/4963016556"
    private const val REWARDED_COMPLIMENT_GLOBAL = "ca-app-pub-3206974923602803/5577244161"
    private const val REWARDED_SWIPE_GLOBAL = "ca-app-pub-3206974923602803/2564068016"

    private fun isIndia(ctx: Context) = CountryUtil.isProbablyInIndia(ctx)

    fun native(ctx: Context) = if (isIndia(ctx)) NATIVE_IN else NATIVE_GLOBAL
    fun interstitial(ctx: Context) = if (isIndia(ctx)) INTERSTITIAL_IN else INTERSTITIAL_GLOBAL
    fun rewardedBoost(ctx: Context) = if (isIndia(ctx)) REWARDED_BOOST_IN else REWARDED_BOOST_GLOBAL
    fun rewardedCompliment(ctx: Context) = if (isIndia(ctx)) REWARDED_COMPLIMENT_IN else REWARDED_COMPLIMENT_GLOBAL
    fun rewardedSwipe(ctx: Context) = if (isIndia(ctx)) REWARDED_SWIPE_IN else REWARDED_SWIPE_GLOBAL
}