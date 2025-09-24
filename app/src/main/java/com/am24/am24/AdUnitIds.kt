package com.am24.am24

import android.content.Context

object AdUnitIds {

    private const val REWARDED_BOOST_IN = "ca-app-pub-5094389629300846/4203186426"
    // private const val REWARDED_COMPLIMENT_IN = "ca-app-pub-3206974923602803/3321781760"
    private const val REWARDED_COMPLIMENT_IN = "ca-app-pub-5094389629300846/6893779002"
    // private const val REWARDED_SWIPE_IN = "ca-app-pub-3206974923602803/9341213447"
    private const val REWARDED_SWIPE_IN = "ca-app-pub-5094389629300846/2665106990"
    private const val REWARDED_BOOST_GLOBAL = "ca-app-pub-5094389629300846/2875233243"
    // private const val REWARDED_COMPLIMENT_GLOBAL = "ca-app-pub-3206974923602803/5577244161"
    private const val REWARDED_COMPLIMENT_GLOBAL = "ca-app-pub-5094389629300846/7852728769"
    // private const val REWARDED_SWIPE_GLOBAL = "ca-app-pub-3206974923602803/2564068016"
    private const val REWARDED_SWIPE_GLOBAL = "ca-app-pub-5094389629300846/8487153585"
    private fun isIndia(ctx: Context) = CountryUtil.isProbablyInIndia(ctx)

    fun rewardedBoost(ctx: Context) = if (isIndia(ctx)) REWARDED_BOOST_IN else REWARDED_BOOST_GLOBAL
    fun rewardedCompliment(ctx: Context) = if (isIndia(ctx)) REWARDED_COMPLIMENT_IN else REWARDED_COMPLIMENT_GLOBAL
    fun rewardedSwipe(ctx: Context) = if (isIndia(ctx)) REWARDED_SWIPE_IN else REWARDED_SWIPE_GLOBAL
}