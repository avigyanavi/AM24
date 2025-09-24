package com.am24.am24

import android.content.Context

object AdUnitIds {

    private const val REWARDED_BOOST_IN = "ca-app-pub-5094389629300846/4203186426"
    private const val REWARDED_COMPLIMENT_INDIA = "ca-app-pub-1814829133495225/1475614911"
    private const val REWARDED_COMPLIMENT_MEXICO = "ca-app-pub-1814829133495225/4101778252"
    private const val REWARDED_COMPLIMENT_USA = "ca-app-pub-1814829133495225/7793611256"
    private const val REWARDED_BOOST_GLOBAL = "ca-app-pub-5094389629300846/2875233243"

    private const val REWARDED_SWIPE_INDIA = "ca-app-pub-1814829133495225/6672101273"
    private const val REWARDED_SWIPE_MEXICO = "ca-app-pub-1814829133495225/5715061069"
    private const val REWARDED_SWIPE_USA = "ca-app-pub-1814829133495225/8691759951"
    private fun isIndia(ctx: Context) = CountryUtil.isProbablyInIndia(ctx)

    fun rewardedBoost(ctx: Context) = if (isIndia(ctx)) REWARDED_BOOST_IN else REWARDED_BOOST_GLOBAL
    fun rewardedCompliment(ctx: Context, selectedCountry: String? = null): String {
        return when {
            CountryUtil.isIndia(ctx, selectedCountry) -> REWARDED_COMPLIMENT_INDIA
            CountryUtil.isMexico(ctx, selectedCountry) -> REWARDED_COMPLIMENT_MEXICO
            CountryUtil.isUnitedStates(ctx, selectedCountry) -> REWARDED_COMPLIMENT_USA
            else -> REWARDED_COMPLIMENT_MEXICO
        }
    }

    fun rewardedSwipe(ctx: Context, selectedCountry: String? = null): String {
        return when {
            CountryUtil.isIndia(ctx, selectedCountry) -> REWARDED_SWIPE_INDIA
            CountryUtil.isMexico(ctx, selectedCountry) -> REWARDED_SWIPE_MEXICO
            CountryUtil.isUnitedStates(ctx, selectedCountry) -> REWARDED_SWIPE_USA
            else -> REWARDED_SWIPE_MEXICO
        }
    }
}