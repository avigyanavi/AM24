package com.am24.am24

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberTileOverlayState
import com.google.maps.android.heatmaps.HeatmapTileProvider

/**
 * A Jetpack Compose screen that displays a map of West Bengal
 * with a bounding box, clickable markers for major cities,
 * and an optional heatmap overlay.
 *
 * @param onCityClicked A callback that lets you navigate or show info
 *                      whenever a user taps one of the city markers.
 */
@Composable
fun MapScreen(
    onCityClicked: (String) -> Unit
) {
    // 1) Approximate bounding box for West Bengal
    val westBengalBounds = LatLngBounds(
        LatLng(21.42, 85.82), // southwestern corner
        LatLng(27.13, 89.82)  // northeastern corner
    )

    // 2) Camera position near Kolkata
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(22.5726, 88.3639),
            7f
        )
    }

    // 3) Prepare a heatmap provider
    val heatmapPoints = listOf(
        LatLng(22.5726, 88.3639), // Kolkata
        LatLng(23.5204, 87.3119), // Durgapur
        LatLng(23.6901, 86.9524), // Asansol
        LatLng(26.7271, 88.3953), // Siliguri
        // Add more if you want ...
    )
    val heatmapProvider = remember {
        HeatmapTileProvider.Builder()
            .data(heatmapPoints)
            .build()
    }
    val heatmapOverlayState = rememberTileOverlayState()

    // 4) Draw the map
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true,
            mapToolbarEnabled = false
        )
    ) {
        // Restrict camera movement to the bounding box
        MapEffect(westBengalBounds) { googleMap ->
            googleMap.setLatLngBoundsForCameraTarget(westBengalBounds)
            googleMap.setMinZoomPreference(6.0f)
        }

        // 5) Clickable markers for major cities
        Marker(
            state = MarkerState(position = LatLng(22.5726, 88.3639)),
            title = "Kolkata",
            onClick = {
                onCityClicked("Kolkata")
                true
            }
        )
        Marker(
            state = MarkerState(position = LatLng(26.7271, 88.3953)),
            title = "Siliguri",
            onClick = {
                onCityClicked("Siliguri")
                true
            }
        )
        Marker(
            state = MarkerState(position = LatLng(23.6901, 86.9524)),
            title = "Asansol",
            onClick = {
                onCityClicked("Asansol")
                true
            }
        )
        Marker(
            state = MarkerState(position = LatLng(23.5204, 87.3119)),
            title = "Durgapur",
            onClick = {
                onCityClicked("Durgapur")
                true
            }
        )

        // 6) Optionally add a heatmap overlay
        TileOverlay(
            tileProvider = heatmapProvider,
            state = heatmapOverlayState
        )
    }
}
