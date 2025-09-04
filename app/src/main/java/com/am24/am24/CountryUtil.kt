package com.am24.am24

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

object CountryUtil {
    private val KM_COUNTRIES = setOf(
        "IN", // India
        "GB", "UK", "GBR", // United Kingdom / England
        "DE", // Germany
        "AU", // Australia
        "SG", // Singapore
        "FR", // France
        "NL", // Netherlands
        "ES", // Spain
        "HK", // Hong Kong
        "CN", // China
        "IE",  // Ireland
        "MX"  // Mexico
    )

    fun isProbablyInIndia(ctx: Context): Boolean {
        val isoBySim   = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.simCountryIso?.uppercase(Locale.US)
        val isoByNet   = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.networkCountryIso?.uppercase(Locale.US)
        val isoByLocale = Locale.getDefault().country.uppercase(Locale.US)

        return listOf(isoBySim, isoByNet, isoByLocale).any { it == "IN" }
    }

    fun usesKilometers(ctx: Context): Boolean {
        val isoBySim   = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.simCountryIso?.uppercase(Locale.US)
        val isoByNet   = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.networkCountryIso?.uppercase(Locale.US)
        val isoByLocale = Locale.getDefault().country.uppercase(Locale.US)

        return listOf(isoBySim, isoByNet, isoByLocale).any { it in KM_COUNTRIES }
    }

    fun isMexico(ctx: Context, selectedCountry: String?): Boolean {
        if (selectedCountry?.equals("Mexico", ignoreCase = true) == true ||
            selectedCountry?.equals("México", ignoreCase = true) == true) return true
        val isoBySim = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.simCountryIso?.uppercase(Locale.US)
        val isoByNet = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.networkCountryIso?.uppercase(Locale.US)
        val isoByLocale = Locale.getDefault().country.uppercase(Locale.US)
        return listOf(isoBySim, isoByNet, isoByLocale).any { it == "MX" }
    }

    /**
     * Decide whether Razorpay should be used for payments.
     * If the device locale suggests India but the user has selected a
     * different country during registration, prefer PayPal instead.
     */
    @Suppress("UNUSED_PARAMETER")
    fun useRazorpay(ctx: Context, selectedCountry: String?): Boolean {
//        val deviceIndia = isProbablyInIndia(ctx)
//        if (!deviceIndia) return false
//        return selectedCountry?.equals("India", ignoreCase = true) ?: true
        return false
    }
}