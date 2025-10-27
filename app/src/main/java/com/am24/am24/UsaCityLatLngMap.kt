package com.am24.am24

/**
 * Mapping of major U.S. cities to representative latitude/longitude pairs.
 */
object UsaCityLatLngMap {
    private val cityCoordinates = mapOf(
        "New York" to (40.7128 to -74.0060),
        "Los Angeles" to (34.0522 to -118.2437),
        "Chicago" to (41.8781 to -87.6298),
        "Houston" to (29.7604 to -95.3698),
        "Phoenix" to (33.4484 to -112.0740),
        "Philadelphia" to (39.9526 to -75.1652),
        "San Antonio" to (29.4241 to -98.4936),
        "San Diego" to (32.7157 to -117.1611),
        "Dallas" to (32.7767 to -96.7970),
        "San Jose" to (37.3382 to -121.8863),
        "Austin" to (30.2672 to -97.7431),
        "Jacksonville" to (30.3322 to -81.6557),
        "San Francisco" to (37.7749 to -122.4194),
        "Columbus" to (39.9612 to -82.9988),
        "Fort Worth" to (32.7555 to -97.3308),
        "Indianapolis" to (39.7684 to -86.1581),
        "Charlotte" to (35.2271 to -80.8431),
        "Seattle" to (47.6062 to -122.3321),
        "Denver" to (39.7392 to -104.9903),
        "Washington" to (38.9072 to -77.0369),
        "Boston" to (42.3601 to -71.0589),
        "Nashville" to (36.1627 to -86.7816),
        "Detroit" to (42.3314 to -83.0458),
        "Portland" to (45.5152 to -122.6784),
        "Las Vegas" to (36.1699 to -115.1398),
        "Miami" to (25.7617 to -80.1918),
        "Atlanta" to (33.7490 to -84.3880)
    )

    fun getLatLng(city: String): Pair<Double, Double>? = cityCoordinates[city]
}