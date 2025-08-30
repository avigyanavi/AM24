package com.am24.am24

/**
 * Mapping of Mexican city names (English and Spanish) to representative latitude/longitude pairs.
 * This can be expanded as needed.
 */
object MexicoCityLatLngMap {
    private val map = mapOf(
        "Mexico City" to (19.4326 to -99.1332),
        "Ciudad de México" to (19.4326 to -99.1332),
        "Guadalajara" to (20.6597 to -103.3496),
        "Monterrey" to (25.6866 to -100.3161),
        "Puebla" to (19.0414 to -98.2063),
        "Tijuana" to (32.5149 to -117.0382),
        "Ciudad Juárez" to (31.6904 to -106.4245),
        "Ciudad Juarez" to (31.6904 to -106.4245),
        "León" to (21.1619 to -101.6029),
        "Leon" to (21.1619 to -101.6029),
        "Zapopan" to (20.7167 to -103.4000),
        "Ecatepec de Morelos" to (19.6018 to -99.0507),
        "Ecatepec" to (19.6018 to -99.0507),
        "Nezahualcóyotl" to (19.3980 to -99.0190),
        "Nezahualcoyotl" to (19.3980 to -99.0190),
        "Mérida" to (20.9674 to -89.5926),
        "Merida" to (20.9674 to -89.5926),
        "San Luis Potosí" to (22.1565 to -100.9855),
        "San Luis Potosi" to (22.1565 to -100.9855),
        "Querétaro" to (20.5888 to -100.3899),
        "Queretaro" to (20.5888 to -100.3899),
        "Mexicali" to (32.6245 to -115.4523),
        "Aguascalientes" to (21.8853 to -102.2916),
        "Hermosillo" to (29.0729 to -110.9559),
        "Saltillo" to (25.4262 to -100.9950),
        "Morelia" to (19.7045 to -101.1940),
        "Chihuahua" to (28.6320 to -106.0691),
        "Culiacán" to (24.8091 to -107.3940),
        "Culiacan" to (24.8091 to -107.3940),
        "Cancún" to (21.1619 to -86.8515),
        "Cancun" to (21.1619 to -86.8515),
        "Villahermosa" to (17.9869 to -92.9303),
        "Toluca" to (19.2826 to -99.6557),
        "Reynosa" to (26.0925 to -98.2773),
        "Tlalnepantla" to (19.5401 to -99.1941)
    )

    fun getLatLng(city: String): Pair<Double, Double>? = map[city]
}