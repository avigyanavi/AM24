package com.am24.am24

/**
 * Simple mapping of country names to a representative latitude/longitude.
 * Only a handful of major countries are included for demonstration and can
 * be extended as needed.
 */
object CountryLatLngMap {
    private val map = mapOf(
        "United States" to (37.0902 to -95.7129),
        "Mexico"        to (23.6345 to -102.5528),
        "Canada"        to (56.1304 to -106.3468),
        "India"         to (20.5937 to 78.9629),
        "United Kingdom" to (55.3781 to -3.4360),
        "Germany"       to (51.1657 to 10.4515)
    )

    fun getLatLng(country: String): Pair<Double, Double>? = map[country]
}