package com.am24.am24

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**  Tiny DTO shown to the user when they pick a place  */
data class PlaceResult(
    val placeId : String,
    val name    : String,
    val address : String,
    val latLng  : LatLng
)

/**  What we finally write into the post object  */
data class CheckIn(
    val placeId : String = "",
    val name    : String = "",
    val address : String = "",
    val lat     : Double = 0.0,
    val lng     : Double = 0.0
)

/**
 * A “rich” nearby search that also returns the formatted address.
 * Uses the same Places API key you already have.
 */
suspend fun searchPlacesRich(
    query: String,
    userLatLng: LatLng,
    radiusMeters: Int = 10_000
): List<PlaceResult> = withContext(Dispatchers.IO) {

    val tag = "PlacesSearch"
    Log.d(tag, "→ searchPlacesRich(q=\"$query\", bias=${userLatLng.latitude},${userLatLng.longitude})")

    val client  = OkHttpClient()
    val apiKey  = "AIzaSyBJej3hxm7i7Nvd638k4OSMBQLjrueE9aQ"          // ← already in your repo

    val bodyJson = JSONObject()
        .put("textQuery", query)
        .put("locationBias", JSONObject()
            .put("circle", JSONObject()
                .put("center", JSONObject()
                    .put("latitude",  userLatLng.latitude)
                    .put("longitude", userLatLng.longitude))
                .put("radius", radiusMeters)
            )
        )

    val req = Request.Builder()
        .url("https://places.googleapis.com/v1/places:searchText")
        .addHeader("Content-Type", "application/json")
        .addHeader("X-Goog-Api-Key", apiKey)
        .addHeader(
            "X-Goog-FieldMask",
            "places.displayName,places.formattedAddress,places.location,places.id"
        )
        .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
        .build()

    val out = mutableListOf<PlaceResult>()
    try {
        client.newCall(req).execute().use { rsp ->
            Log.d(tag, "HTTP status = ${rsp.code}")
            if (!rsp.isSuccessful) {
                Log.e(tag, "HTTP ${rsp.code}: ${rsp.body?.string()}")
                return@withContext emptyList()
            }
            val js  = JSONObject(rsp.body?.string() ?: "")
            val arr = js.optJSONArray("places") ?: return@withContext emptyList()
            for (i in 0 until arr.length()) {
                val p      = arr.getJSONObject(i)
                val name   = p.getJSONObject("displayName").getString("text")
                val addr   = p.optString("formattedAddress", "")
                val id     = p.getString("id")
                val loc    = p.getJSONObject("location")
                val latLng = LatLng(loc.getDouble("latitude"), loc.getDouble("longitude"))
                out += PlaceResult(id, name, addr, latLng)
            }
        }
    } catch (e: Exception) {
        Log.e(tag, "searchPlacesRich failed: ${e.message}", e)
    }

    Log.d(tag, "← parsed ${out.size} result(s)")
    return@withContext out
}
