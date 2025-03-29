package com.am24.am24

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Composable
fun MapScreen(
    userId: String,
    locationManager: LocationManager,
    geoFireDatabaseRef: DatabaseReference,
    onProfileMarkerClicked: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    val cameraPositionState = rememberCameraPositionState()
    val markersState = remember { mutableStateListOf<MarkerData>() }
    var searchMarker by remember { mutableStateOf<LatLng?>(null) }

    // 1. Get user location
    LaunchedEffect(userId) {
        locationManager.getUserLocationFromGeoFire(userId) { lat, lng ->
            if (lat != null && lng != null) {
                val userLatLng = LatLng(lat, lng)
                cameraPositionState.position = CameraPosition.fromLatLngZoom(userLatLng, 12f)

                val geoFire = GeoFire(geoFireDatabaseRef)
                val query: GeoQuery = geoFire.queryAtLocation(GeoLocation(lat, lng), 10.0)
                query.addGeoQueryEventListener(object : GeoQueryEventListener {
                    override fun onKeyEntered(key: String, location: GeoLocation) {
                        markersState.add(
                            MarkerData(
                                userId = key,
                                position = LatLng(location.latitude, location.longitude)
                            )
                        )
                    }
                    override fun onKeyExited(key: String) {
                        markersState.removeAll { it.userId == key }
                    }
                    override fun onKeyMoved(key: String, location: GeoLocation) {
                        markersState.replaceAll { existing ->
                            if (existing.userId == key) existing.copy(position = LatLng(location.latitude, location.longitude))
                            else existing
                        }
                    }
                    override fun onGeoQueryReady() {}
                    override fun onGeoQueryError(error: DatabaseError) {
                        Toast.makeText(context, "GeoQuery error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            } else {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                    LatLng(22.5726, 88.3639), 7f
                )
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
                    .background(Color.White)
                    .padding(8.dp),
                textStyle = TextStyle(color = Color.Black)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        val result = searchPlaceWithOkHttp(searchQuery)
                        if (result != null) {
                            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(result, 17f))
                            searchMarker = result
                        } else {
                            Toast.makeText(context, "No results found", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) {
                Text("Search", color = Color.White)
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            val heatmapProvider = remember(markersState) {
                if (markersState.isNotEmpty()) {
                    HeatmapTileProvider.Builder()
                        .data(markersState.map { it.position })
                        .build()
                } else null
            }
            val heatmapState = rememberTileOverlayState()

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    mapToolbarEnabled = false
                )
            ) {
                markersState.forEach { markerData ->
                    Marker(
                        state = MarkerState(position = markerData.position),
                        title = "User: ${markerData.userId}",
                        onClick = {
                            onProfileMarkerClicked(markerData.userId)
                            true
                        }
                    )
                }

                searchMarker?.let { latLng ->
                    Marker(
                        state = MarkerState(position = latLng),
                        title = "Search Result"
                    )
                }

                if (heatmapProvider != null) {
                    TileOverlay(tileProvider = heatmapProvider, state = heatmapState)
                }
            }
        }
    }
}

data class MarkerData(
    val userId: String,
    val position: LatLng
)

suspend fun searchPlaceWithOkHttp(query: String): LatLng? = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val apiKey = "AIzaSyBJej3hxm7i7Nvd638k4OSMBQLjrueE9aQ"

    val requestBody = JSONObject()
        .put("textQuery", query)
        .toString()
        .toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url("https://places.googleapis.com/v1/places:searchText")
        .addHeader("Content-Type", "application/json")
        .addHeader("X-Goog-Api-Key", apiKey)
        .addHeader(
            "X-Goog-FieldMask",
            "places.displayName,places.formattedAddress,places.location"
        )
        .post(requestBody)
        .build()

    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("PlacesSearch", "Error: ${response.code} - ${response.body?.string()}")
                return@withContext null
            }

            val json = JSONObject(response.body?.string() ?: return@withContext null)
            val places = json.getJSONArray("places")
            if (places.length() > 0) {
                val first = places.getJSONObject(0)
                val location = first.getJSONObject("location")
                val lat = location.getDouble("latitude")
                val lng = location.getDouble("longitude")
                return@withContext LatLng(lat, lng)
            }
        }
    } catch (e: Exception) {
        Log.e("PlacesSearch", "Exception: ${e.message}", e)
    }

    return@withContext null
}
