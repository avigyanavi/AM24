package com.am24.am24

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

object CountryUtil {
    fun isProbablyInIndia(ctx: Context): Boolean {
        val isoBySim   = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.simCountryIso?.uppercase(Locale.US)
        val isoByNet   = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.networkCountryIso?.uppercase(Locale.US)
        val isoByLocale = Locale.getDefault().country.uppercase(Locale.US)

        return listOf(isoBySim, isoByNet, isoByLocale).any { it == "IN" }
    }

    /**
     * Decide whether Razorpay should be used for payments.
     * If the device locale suggests India but the user has selected a
     * different country during registration, prefer PayPal instead.
     */
    fun useRazorpay(ctx: Context, selectedCountry: String?): Boolean {
        val deviceIndia = isProbablyInIndia(ctx)
        if (!deviceIndia) return false
        return selectedCountry?.equals("India", ignoreCase = true) ?: true
    }
}