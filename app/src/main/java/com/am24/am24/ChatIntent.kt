package com.am24.am24

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/* ─────────────────────────── data models ─────────────────────────── */

data class ChatSuggestions(
    val topics:          List<String>        = emptyList(),
    val activities:      List<ActivitySuggestion> = emptyList(),
    val integrationTips: List<String>        = emptyList()
)

data class ActivitySuggestion(val placeName: String, val integration: String)

data class PlaceDetails(
    val placeName: String,
    val latLng:    LatLng,
    val address:   String? = null
)

/* the payload model we POST to the CF */
private data class CfMsg(val role: String, val text: String? = null, val imageUrl: String? = null)

/* ─────────────────────────── constants ─────────────────────────── */

private const val CF_SUGGESTIONS =
    "https://asia-south1-am-twentyfour.cloudfunctions.net/chatSuggestions"   // change if you moved it

private const val PLACES_ENDPOINT = "https://places.googleapis.com/v1/places:searchText"
private const val PLACES_KEY      = "AIzaSyBJej3hxm7i7Nvd638k4OSMBQLjrueE9aQ"          // ✔ keep in a safer place

/* single, reusable OkHttp instance (30 s read) */
private val http by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout  (15, TimeUnit.SECONDS)
        .readTimeout   (30, TimeUnit.SECONDS)
        .build()
}

/* preferred languages depending on device locale */
private fun preferredLangs(context: Context): List<String> =
    when (Locale.getDefault().language.lowercase()) {
        "hi" -> listOf("hi", "bn", "en")
        "bn" -> listOf("bn", "hi", "en")
        else -> listOf("en")                                // fall-back
    }

/* ─────────────────────────── SUGGESTIONS ─────────────────────────── */

suspend fun getChatSuggestions(
    messages:           List<Message>,
    lang:               String = Locale.getDefault().language   // 🆕
): ChatSuggestions? = withContext(Dispatchers.IO) {

    val gson = Gson()

    /* 1️⃣  build payload for the CF, also send the locale */
    /* 1️⃣ Build payload – note the "lang" key */
    val payload = mapOf(
        "messages" to messages.takeLast(10).map { m ->
            CfMsg(
                role     = "assistant",
                text     = m.mediaType?.let { null } ?: m.text.takeIf { it.isNotBlank() },
                imageUrl = if (m.mediaType == "photo") m.mediaUrl else null
            )
        },
        "lang"           to lang                              // 🆕  "hi" / "bn" / "en" …
    )
    val req = Request.Builder()
        .url(CF_SUGGESTIONS)
        .post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
        .build()

    /* optional: log to Logcat */
    Log.d("ChatSuggestions", "POST body: ${gson.toJson(payload)}")
    try {
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.e("ChatSuggestions", "CF error: HTTP ${resp.code}")
                return@withContext null
            }
            val body = resp.body?.string() ?: return@withContext null
            Log.d("ChatSuggestions", "CF response: $body")
            return@withContext gson.fromJson(body, ChatSuggestions::class.java)
        }
    } catch (e: Exception) {
        Log.e("ChatSuggestions", "Network / parse error", e)
        null
    }
}

/* ─────────────────────────── PLACES  ─────────────────────────── */

private fun buildPlacesRequest(
    query:        String,
    latLng:       LatLng,
    languageCode: String
): Request {

    val body = JSONObject()
        .put("textQuery", query)
        .put(
            "locationBias", JSONObject()
                .put("circle", JSONObject()
                    .put("center", JSONObject()
                        .put("latitude",  latLng.latitude)
                        .put("longitude", latLng.longitude))
                    .put("radius", 10_000))
        )
        .put("languageCode", languageCode)              // 🆕 request Hindi / Bengali names
        .toString()
        .toRequestBody("application/json".toMediaType())

    return Request.Builder()
        .url("$PLACES_ENDPOINT?pageSize=3")
        .addHeader("Content-Type",  "application/json")
        .addHeader("X-Goog-Api-Key", PLACES_KEY)
        .addHeader(
            "X-Goog-FieldMask",
            "places.displayName.text,places.location.latitude,places.location.longitude"
        )
        .post(body)
        .build()
}

/* search around the other user – honours preferredLangs() */
suspend fun searchPlacesWithOkHttp(
    query:        String,
    userLocation: LatLng,
    context:      Context
): List<Pair<LatLng, String>> = withContext(Dispatchers.IO) {

    val results = mutableListOf<Pair<LatLng, String>>()

    for (lang in preferredLangs(context)) {
        val req = buildPlacesRequest(query, userLocation, lang)

        try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e("PlacesSearch", "HTTP ${resp.code}  ($lang)")
                }
                val places = JSONObject(resp.body?.string() ?: return@use)
                    .optJSONArray("places") ?: return@use

                for (i in 0 until places.length()) {
                    val p   = places.getJSONObject(i)
                    val loc = p.getJSONObject("location")
                    val lat = loc.getDouble("latitude")
                    val lng = loc.getDouble("longitude")
                    val name= p.getJSONObject("displayName").getString("text")
                    results += LatLng(lat, lng) to name
                }
            }
        } catch (e: Exception) {
            Log.e("PlacesSearch", "network / parse error ($lang)", e)
        }

        if (results.isNotEmpty()) break          // first language that yielded results
    }
    results
}

/* high-level helper used by the UI */
suspend fun getPlaceSuggestions(
    topics:            List<String>,
    otherProfile:      Profile?,
    context:           Context
): List<PlaceDetails> {

    val loc = otherProfile?.let { LatLng(it.latitude ?: 0.0, it.longitude ?: 0.0) }
        ?: return emptyList()

    val out = mutableListOf<PlaceDetails>()
    topics.take(2).forEach { topic ->
        searchPlacesWithOkHttp(topic, loc, context).firstOrNull()?.let { (latLng, name) ->
            out += PlaceDetails(name, latLng, fetchAddress(latLng, context))
        }
    }
    return out
}

/* fetch a *single* formatted address – same language cascade */
suspend fun fetchAddress(latLng: LatLng, context: Context): String = withContext(Dispatchers.IO) {

    for (lang in preferredLangs(context)) {

        val body = JSONObject()
            .put("textQuery", "${latLng.latitude},${latLng.longitude}")
            .put("languageCode", lang)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("$PLACES_ENDPOINT?pageSize=1")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Goog-Api-Key", PLACES_KEY)
            .addHeader("X-Goog-FieldMask", "places.formattedAddress")
            .post(body)
            .build()

        try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e("FetchAddress", "HTTP ${resp.code} ($lang)")
                    return@use
                }
                val arr = JSONObject(resp.body?.string() ?: return@use)
                    .optJSONArray("places") ?: return@use
                if (arr.length() > 0) {
                    return@withContext arr.getJSONObject(0)
                        .optString("formattedAddress")
                }
            }
        } catch (e: Exception) {
            Log.e("FetchAddress", "error ($lang)", e)
        }
    }
    "Near ${latLng.latitude}, ${latLng.longitude}"     // totally-offline fallback
}
