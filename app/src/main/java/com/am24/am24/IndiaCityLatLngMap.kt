package com.am24.am24

object IndiaCityLatLngMap {
    private val cityCoordinates = mapOf(
        "Delhi" to (28.6139 to 77.2090),
        "Mumbai" to (19.0760 to 72.8777),
        "Bengaluru" to (12.9716 to 77.5946),
        "Hyderabad" to (17.3850 to 78.4867),
        "Chennai" to (13.0827 to 80.2707),
        "Kolkata" to (22.5726 to 88.3639),
        "Pune" to (18.5204 to 73.8567),
        "Ahmedabad" to (23.0225 to 72.5714),
        "Jaipur" to (26.9124 to 75.7873),
        "Surat" to (21.1702 to 72.8311),
        "Lucknow" to (26.8467 to 80.9462),
        "Indore" to (22.7196 to 75.8577),
        "Bhopal" to (23.2599 to 77.4126),
        "Chandigarh" to (30.7333 to 76.7794),
        "Gurgaon" to (28.4595 to 77.0266),
        "Noida" to (28.5355 to 77.3910),
        "Kochi" to (9.9312 to 76.2673),
        "Thiruvananthapuram" to (8.5241 to 76.9366),
        "Goa" to (15.2993 to 74.1240),
        "Nagpur" to (21.1458 to 79.0882),
        "Visakhapatnam" to (17.6868 to 83.2185),
        "Chandigarh Tricity" to (30.7333 to 76.7794),
        "Coimbatore" to (11.0168 to 76.9558)
    )

    fun getLatLng(city: String): Pair<Double, Double>? {
        return cityCoordinates[city]
    }
}