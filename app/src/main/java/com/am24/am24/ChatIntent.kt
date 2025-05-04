package com.am24.am24

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// Data classes for chat suggestions

data class ChatSuggestions(
    val topics: List<String> = emptyList(),
    val activities: List<ActivitySuggestion> = emptyList(),
    val integrationTips: List<String> = emptyList()
)

// ‼️  Put these just below the imports (or anywhere, top-level).
private const val CF_SUGGESTIONS =
    "https://asia-south1-am-twentyfour.cloudfunctions.net/chatSuggestions"   // ← change if region/URL differs

private data class CfMsg(
    val role: String,          // "user" | "assistant"
    val text: String?   = null,
    val imageUrl: String? = null
)

data class ActivitySuggestion(
    val placeName: String,
    val integration: String
)

data class PlaceDetails(
    val placeName: String,
    val latLng: LatLng,
    val address: String? = null
)


suspend fun getChatSuggestions(
    messages: List<Message>,
    currentUserProfile: Profile,
    otherUserProfile:   Profile?,
    context: Context        // kept for signature compatibility (unused here)
): ChatSuggestions? = withContext(Dispatchers.IO) {

    val gson = Gson()
    val http by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout  (15, TimeUnit.SECONDS)
        .readTimeout   (30, TimeUnit.SECONDS)   // > round-trip to US + payload
        .build()
}

    /* 1️⃣  Build payload expected by the CF */
    val payload = mapOf(
        "messages" to messages.takeLast(10).map { m ->
            CfMsg(
                role     = if (m.senderId == currentUserProfile.userId) "user" else "assistant",
                text     = m.mediaType?.let { null } ?: m.text.takeIf { it.isNotBlank() },
                imageUrl = if (m.mediaType == "photo") m.mediaUrl else null
            )
        },
        "currentProfile" to currentUserProfile,
        "otherProfile"   to otherUserProfile
    )

    /* 2️⃣  POST to the Cloud Function */
    val request = Request.Builder()
        .url(CF_SUGGESTIONS)
        .post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
        .build()

    try {
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.e("ChatSuggestions", "CF error: ${resp.code}")
                return@withContext null
            }
            val body = resp.body?.string() ?: return@withContext null
            Log.d("ChatSuggestions", "CF response: $body")
            return@withContext gson.fromJson(body, ChatSuggestions::class.java)
        }
    } catch (e: Exception) {
        Log.e("ChatSuggestions", "Network/parse error", e)
        null
    }
}

/**

Returns a list of suggested places based on the provided topics and the other user's profile. */
suspend fun getPlaceSuggestions( topics: List<String>, otherUserProfile: Profile?, context: Context ): List<PlaceDetails>
{ val otherUserLatLng = otherUserProfile?.let { LatLng(it.latitude ?: 0.0, it.longitude ?: 0.0) } ?: return emptyList()
    val results = mutableListOf<PlaceDetails>()
    topics.take(2).forEach { topic -> val places = searchPlacesWithOkHttp(topic, otherUserLatLng, context)
        places.firstOrNull()?.let { (latLng, name) -> results.add(
            PlaceDetails( placeName = name, latLng = latLng, address = fetchAddress(latLng, context) ) ) } }
    return results }

/**

Searches for places using the Google Places API Text Search. */
suspend fun searchPlacesWithOkHttp(query: String, userLocation: LatLng, context: Context): List<Pair<LatLng, String>> = withContext(Dispatchers.IO) { val client = OkHttpClient()
    val apiKey = "AIzaSyBJej3hxm7i7Nvd638k4OSMBQLjrueE9aQ"
    val requestBody = JSONObject() .put("textQuery", query) .put( "locationBias", JSONObject() .put("circle", JSONObject() .put("center", JSONObject() .put("latitude", userLocation.latitude) .put("longitude", userLocation.longitude) ) .put("radius", 10000) ) ) .toString() .toRequestBody("application/json".toMediaType())
    val request = Request.Builder() .url("https://places.googleapis.com/v1/places:searchText") .addHeader("Content-Type", "application/json") .addHeader("X-Goog-Api-Key", apiKey) .addHeader("X-Goog-FieldMask", "places.displayName,places.formattedAddress,places.location") .post(requestBody) .build()
    val results = mutableListOf<Pair<LatLng, String>>()
    try { client.newCall(request).execute().use { response -> if (!response.isSuccessful)
    { Log.e("PlacesSearch", "Error: ${response.code} - ${response.body?.string()}")
        return@withContext emptyList() }
        val json = JSONObject(response.body?.string() ?: return@withContext emptyList())
        val places = json.getJSONArray("places")
        for (i in 0 until places.length()) { val place = places.getJSONObject(i)
            val name = place.getJSONObject("displayName").getString("text")
            val location = place.getJSONObject("location")
            val lat = location.getDouble("latitude")
            val lng = location.getDouble("longitude")
            results.add(LatLng(lat, lng) to name) } } } catch (e: Exception) { Log.e("PlacesSearch", "Exception: ${e.message}", e) }
    results }

suspend fun fetchAddress(latLng: LatLng, context: Context): String? = withContext(Dispatchers.IO)
{
    val client = OkHttpClient()
    val apiKey = "AIzaSyBJej3hxm7i7Nvd638k4OSMBQLjrueE9aQ"
    val requestBody = JSONObject() .put("textQuery", "${latLng.latitude},${latLng.longitude}") .put( "locationBias", JSONObject() .put("circle", JSONObject() .put("center", JSONObject() .put("latitude", latLng.latitude) .put("longitude", latLng.longitude) ) .put("radius", 100) ) ) .toString() .toRequestBody("application/json".toMediaType())
    val request = Request.Builder() .url("https://places.googleapis.com/v1/places:searchText") .addHeader("Content-Type", "application/json") .addHeader("X-Goog-Api-Key", apiKey) .addHeader("X-Goog-FieldMask", "places.formattedAddress") .post(requestBody) .build()
    try
    {
        client.newCall(request).execute().use { response -> if (!response.isSuccessful)
        { Log.e("FetchAddress", "Error: ${response.code}")
        return@withContext "Near ${latLng.latitude}, ${latLng.longitude}"
        }
            val json = JSONObject(response.body?.string() ?: return@withContext "Near ${latLng.latitude}, ${latLng.longitude}")
        val places = json.getJSONArray("places")
            if (places.length() > 0)
            {
                places.getJSONObject(0).optString("formattedAddress", "Near ${latLng.latitude}, ${latLng.longitude}")
            }
            else
            { "Near ${latLng.latitude}, ${latLng.longitude}" }
        }
    } catch (e: Exception) {
        Log.e("FetchAddress", "Exception: ${e.message}")
        "Near ${latLng.latitude}, ${latLng.longitude}"
    }
}

