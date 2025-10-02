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
        "MX",  // Mexico
        "TH"  // Thailand
    )
    private data class BoundingBox(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    ) {
        fun contains(lat: Double, lon: Double): Boolean {
            val normalizedLon = when {
                lon < -180.0 -> lon + 360.0
                lon > 180.0 -> lon - 360.0
                else -> lon
            }
            return lat in minLat..maxLat && normalizedLon in minLon..maxLon
        }
    }

    private val COUNTRY_BOUNDARIES: Map<String, List<BoundingBox>> = mapOf(
        "India" to listOf(
            BoundingBox(minLat = 5.0, maxLat = 37.5, minLon = 68.0, maxLon = 97.5)
        ),
        "Mexico" to listOf(
            BoundingBox(minLat = 14.0, maxLat = 33.5, minLon = -118.5, maxLon = -86.0)
        ),
        "United States" to listOf(
            BoundingBox(minLat = 24.0, maxLat = 49.5, minLon = -125.0, maxLon = -66.0),   // Continental US
            BoundingBox(minLat = 18.5, maxLat = 23.0, minLon = -161.0, maxLon = -154.0),  // Hawaii
            BoundingBox(minLat = 51.0, maxLat = 72.0, minLon = -170.0, maxLon = -129.0),  // Alaska
            BoundingBox(minLat = 17.5, maxLat = 18.7, minLon = -67.5, maxLon = -65.0)     // Puerto Rico
        )
    )

    fun countryFromCoordinates(latitude: Double?, longitude: Double?): String? {
        if (latitude == null || longitude == null) return null
        if (latitude == 0.0 && longitude == 0.0) return null

        val lat = latitude
        val lon = longitude

        return COUNTRY_BOUNDARIES.entries.firstOrNull { (_, boxes) ->
            boxes.any { it.contains(lat, lon) }
        }?.key
    }

    fun isProbablyInIndia(ctx: Context): Boolean {
        val isoBySim   = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.simCountryIso?.uppercase(Locale.US)
        val isoByNet   = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.networkCountryIso?.uppercase(Locale.US)
        val isoByLocale = Locale.getDefault().country.uppercase(Locale.US)

        return listOf(isoBySim, isoByNet, isoByLocale).any { it == "IN" }
    }

    fun isIndia(ctx: Context, selectedCountry: String?): Boolean {
        if (canonicalCountry(selectedCountry) == "India") return true
        val telephony = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val isoBySim = telephony?.simCountryIso?.uppercase(Locale.US)
        val isoByNet = telephony?.networkCountryIso?.uppercase(Locale.US)
        val isoByLocale = Locale.getDefault().country.uppercase(Locale.US)
        return listOf(isoBySim, isoByNet, isoByLocale).any { it == "IN" }
    }

    fun isUnitedStates(ctx: Context, selectedCountry: String?): Boolean {
        if (canonicalCountry(selectedCountry) == "United States") return true
        val telephony = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val isoBySim = telephony?.simCountryIso?.uppercase(Locale.US)
        val isoByNet = telephony?.networkCountryIso?.uppercase(Locale.US)
        val isoByLocale = Locale.getDefault().country.uppercase(Locale.US)
        return listOf(isoBySim, isoByNet, isoByLocale).any { it == "US" }
    }

    fun usesKilometers(ctx: Context): Boolean {
        val isoBySim   = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.simCountryIso?.uppercase(Locale.US)
        val isoByNet   = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.networkCountryIso?.uppercase(Locale.US)
        val isoByLocale = Locale.getDefault().country.uppercase(Locale.US)

        return listOf(isoBySim, isoByNet, isoByLocale).any { it in KM_COUNTRIES }
    }
    fun isProbablyInThailand(ctx: Context): Boolean {
        val telephony = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val isoBySim = telephony?.simCountryIso?.uppercase(Locale.US)
        val isoByNet = telephony?.networkCountryIso?.uppercase(Locale.US)
        val isoByLocale = Locale.getDefault().country.uppercase(Locale.US)
        return listOf(isoBySim, isoByNet, isoByLocale).any { it == "TH" }
    }

    fun isMexico(ctx: Context, selectedCountry: String?): Boolean {
        if (canonicalCountry(selectedCountry) == "Mexico") return true
        val isoBySim = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.simCountryIso?.uppercase(Locale.US)
        val isoByNet = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.networkCountryIso?.uppercase(Locale.US)
        val isoByLocale = Locale.getDefault().country.uppercase(Locale.US)
        return listOf(isoBySim, isoByNet, isoByLocale).any { it == "MX" }
    }

    fun isThailand(ctx: Context, selectedCountry: String?): Boolean {
        if (canonicalCountry(selectedCountry) == "Thailand") return true
        val isoBySim = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.simCountryIso?.uppercase(Locale.US)
        val isoByNet = (ctx.getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager)?.networkCountryIso?.uppercase(Locale.US)
        val isoByLocale = Locale.getDefault().country.uppercase(Locale.US)
        return listOf(isoBySim, isoByNet, isoByLocale).any { it == "TH" }
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