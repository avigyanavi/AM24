package com.am24.am24

import android.content.Context
import kotlin.math.roundToInt

object DistanceUtil {
    fun formatDistance(context: Context, km: Float): String {
        return if (CountryUtil.usesKilometers(context)) {
            context.getString(R.string.max_distance, km.roundToInt())
        } else {
            val miles = (km * 0.621371f).roundToInt()
            context.getString(R.string.max_distance_miles, miles)
        }
    }
}