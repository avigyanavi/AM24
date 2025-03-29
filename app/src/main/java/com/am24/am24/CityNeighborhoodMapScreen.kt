package com.am24.am24

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.heatmaps.HeatmapTileProvider

@Composable
fun CityNeighborhoodMapScreen(
    userId: String?,
    cityName: String,
    onNeighborhoodClicked: (String) -> Unit
) {
    val context = LocalContext.current
    val locationManager = remember { LocationManager(context) }

    val cityData = CityDataHolder.getCityData(cityName)
    if (cityData == null) {
        // Unknown city => fallback
        GoogleMap(Modifier.fillMaxSize())
        return
    }

    val cameraPositionState = rememberCameraPositionState()

    // Attempt to read user location from GeoFire
    LaunchedEffect(cityName) {
        if (userId != null) {
            locationManager.getUserLocationFromGeoFire(userId) { lat, lng ->
                if (lat != null && lng != null) {
                    val userLatLng = LatLng(lat, lng)
                    if (cityData.boundingBox.contains(userLatLng)) {
                        cameraPositionState.position =
                            CameraPosition.fromLatLngZoom(userLatLng, cityData.defaultZoom)
                    } else {
                        cameraPositionState.position =
                            CameraPosition.fromLatLngZoom(cityData.initialCenter, cityData.defaultZoom)
                    }
                } else {
                    cameraPositionState.position =
                        CameraPosition.fromLatLngZoom(cityData.initialCenter, cityData.defaultZoom)
                }
            }
        } else {
            cameraPositionState.position =
                CameraPosition.fromLatLngZoom(cityData.initialCenter, cityData.defaultZoom)
        }
    }

    // Build a heatmap from neighborhoods
    val heatmapProvider = remember {
        HeatmapTileProvider.Builder()
            .data(cityData.neighborhoods.map { it.location })
            .build()
    }
    val heatmapOverlayState = rememberTileOverlayState()

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true,
            mapToolbarEnabled = false
        )
    ) {
        MapEffect(cityData.boundingBox) { googleMap ->
            googleMap.setLatLngBoundsForCameraTarget(cityData.boundingBox)
            googleMap.setMinZoomPreference(cityData.minZoom)
        }

        // Place heatmap overlay
        TileOverlay(tileProvider = heatmapProvider, state = heatmapOverlayState)

        // Markers for each neighborhood
        cityData.neighborhoods.forEach { hood ->
            Marker(
                state = MarkerState(hood.location),
                title = hood.name,
                onClick = {
                    onNeighborhoodClicked(hood.name)
                    true
                }
            )
        }
    }
}

// Data classes
data class Neighborhood(val name: String, val location: LatLng)
data class CityMapData(
    val cityName: String,
    val boundingBox: LatLngBounds,
    val initialCenter: LatLng,
    val defaultZoom: Float,
    val minZoom: Float,
    val neighborhoods: List<Neighborhood>
)

// CityDataHolder with bounding boxes for many West Bengal cities
object CityDataHolder {
    // Example bounding boxes (approximate!). Tweak as needed.

    private val kolkataData = CityMapData(
        cityName = "Kolkata",
        boundingBox = LatLngBounds(
            LatLng(22.45, 88.28),
            LatLng(22.70, 88.52)
        ),
        initialCenter = LatLng(22.57, 88.37),
        defaultZoom = 12f,
        minZoom = 9f,
        neighborhoods = listOf(
            Neighborhood("Park Street", LatLng(22.5535, 88.3516)),
            Neighborhood("Tollygunge", LatLng(22.5032, 88.3496)),
            Neighborhood("Behala", LatLng(22.5015, 88.3175))
            // ...
        )
    )

    private val howrahData = CityMapData(
        cityName = "Howrah",
        boundingBox = LatLngBounds(
            LatLng(22.53, 88.17),
            LatLng(22.66, 88.38)
        ),
        initialCenter = LatLng(22.5958, 88.2636),
        defaultZoom = 12f,
        minZoom = 8f,
        neighborhoods = listOf(
            Neighborhood("Shibpur", LatLng(22.5612, 88.3215)),
            Neighborhood("Salkia", LatLng(22.5989, 88.3271))
        )
    )

    private val saltLakeData = CityMapData(
        cityName = "Salt Lake",
        boundingBox = LatLngBounds(
            LatLng(22.56, 88.40),
            LatLng(22.60, 88.44)
        ),
        initialCenter = LatLng(22.5830, 88.4169),
        defaultZoom = 14f,
        minZoom = 10f,
        neighborhoods = listOf(
            Neighborhood("Sector I", LatLng(22.5762, 88.4171)),
            Neighborhood("Sector V", LatLng(22.5748, 88.4292))
        )
    )

    private val newTownData = CityMapData(
        cityName = "New Town",
        boundingBox = LatLngBounds(
            LatLng(22.53, 88.41),
            LatLng(22.62, 88.52)
        ),
        initialCenter = LatLng(22.59, 88.48),
        defaultZoom = 14f,
        minZoom = 10f,
        neighborhoods = listOf(
            Neighborhood("Action Area I", LatLng(22.5805, 88.4673)),
            Neighborhood("Action Area II", LatLng(22.5843, 88.4647))
        )
    )

    private val barrackporeData = CityMapData(
        cityName = "Barrackpore",
        boundingBox = LatLngBounds(
            LatLng(22.62, 88.30),
            LatLng(22.82, 88.50)
        ),
        initialCenter = LatLng(22.7546, 88.3773),
        defaultZoom = 12f,
        minZoom = 8f,
        neighborhoods = listOf(
            Neighborhood("Cantonment", LatLng(22.7616, 88.3641))
        )
    )

    private val barasatData = CityMapData(
        cityName = "Barasat",
        boundingBox = LatLngBounds(
            LatLng(22.64, 88.42),
            LatLng(22.84, 88.52)
        ),
        initialCenter = LatLng(22.7268, 88.4814),
        defaultZoom = 12f,
        minZoom = 8f,
        neighborhoods = listOf(
            Neighborhood("Champadali", LatLng(22.7261, 88.4727))
        )
    )

    private val kharagpurData = CityMapData(
        cityName = "Kharagpur",
        boundingBox = LatLngBounds(
            LatLng(22.25, 87.27),
            LatLng(22.40, 87.48)
        ),
        initialCenter = LatLng(22.3460, 87.4316),
        defaultZoom = 12f,
        minZoom = 9f,
        neighborhoods = listOf(
            Neighborhood("IIT KGP", LatLng(22.3153, 87.3106)),
            Neighborhood("Prembazar", LatLng(22.3403, 87.3221))
        )
    )

    private val asansolData = CityMapData(
        cityName = "Asansol",
        boundingBox = LatLngBounds(
            LatLng(23.61, 86.86),
            LatLng(23.72, 87.00)
        ),
        initialCenter = LatLng(23.6901, 86.9524),
        defaultZoom = 12f,
        minZoom = 9f,
        neighborhoods = listOf(
            Neighborhood("Burnpur", LatLng(23.6659, 86.9286))
        )
    )

    private val durgapurData = CityMapData(
        cityName = "Durgapur",
        boundingBox = LatLngBounds(
            LatLng(23.45, 87.25),
            LatLng(23.60, 87.39)
        ),
        initialCenter = LatLng(23.5204, 87.3119),
        defaultZoom = 12f,
        minZoom = 9f,
        neighborhoods = listOf(
            Neighborhood("B-zone", LatLng(23.5105, 87.3172))
        )
    )

    private val siliguriData = CityMapData(
        cityName = "Siliguri",
        boundingBox = LatLngBounds(
            LatLng(26.65, 88.32),
            LatLng(26.78, 88.48)
        ),
        initialCenter = LatLng(26.7271, 88.3953),
        defaultZoom = 12f,
        minZoom = 9f,
        neighborhoods = listOf(
            Neighborhood("Pradhan Nagar", LatLng(26.7150, 88.4306))
        )
    )

    private val darjeelingData = CityMapData(
        cityName = "Darjeeling",
        boundingBox = LatLngBounds(
            LatLng(26.95, 88.20),
            LatLng(27.16, 88.35)
        ),
        initialCenter = LatLng(27.0333, 88.2667),
        defaultZoom = 12f,
        minZoom = 9f,
        neighborhoods = listOf(
            Neighborhood("Chowrasta", LatLng(27.0420, 88.2663))
        )
    )

    // etc. for more. Tweak bounding boxes & neighborhoods as needed.

    private val cityMap = mapOf(
        "Kolkata" to kolkataData,
        "Howrah" to howrahData,
        "Salt Lake" to saltLakeData,
        "New Town" to newTownData,
        "Barrackpore" to barrackporeData,
        "Barasat" to barasatData,
        "Kharagpur" to kharagpurData,
        "Asansol" to asansolData,
        "Durgapur" to durgapurData,
        "Siliguri" to siliguriData,
        "Darjeeling" to darjeelingData
        // ...
    )

    fun getCityData(cityName: String) = cityMap[cityName]
}
