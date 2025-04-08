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

data class ChatSuggestions(
    val topics: List<String> = emptyList(),
    val activities: List<ActivitySuggestion> = emptyList(),
    val integrationTips: List<String> = emptyList()
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

data class ChatMessage(val role: String = "", val content: String = "", val timestamp: Long = System.currentTimeMillis())

data class ChatRequest(val model: String, val messages: List<ChatMessage>, val max_tokens: Int)

data class ChatResponse(val choices: List<Choice>)
data class Choice(val message: ChatMessage)

suspend fun getChatSuggestions(
    messages: List<Message>,
    currentUserProfile: Profile,
    otherUserProfile: Profile?,
    context: Context
): ChatSuggestions? {
    val recentMessages = messages.takeLast(10).map { ChatMessage(it.senderId, it.text, it.timestamp) }
    val gson = Gson()

    val prompt = """
You are a "Chat Suggestion Engine" for user-to-user conversations.

**Conversation Context:**
- Recent Messages (last 10): ${recentMessages.joinToString(" | ") { "${it.role}: ${it.content}" }}
- Current User Profile: 
  - Name: ${currentUserProfile.name}
  - Location: Lat: ${currentUserProfile.latitude ?: "Unknown"}, Long: ${currentUserProfile.longitude ?: "Unknown"}
- Other User Profile: 
  - Name: ${otherUserProfile?.name ?: "Unknown"}
  - Looking For: ${otherUserProfile?.lookingFor ?: "Not specified"}
  - Love Language: ${otherUserProfile?.loveLanguage ?: "Not specified"}
  - Politics: ${otherUserProfile?.politics ?: "Not specified"}
  - Social Causes: ${otherUserProfile?.socialCauses?.joinToString() ?: "None"}
  - Job Role: ${otherUserProfile?.jobRole ?: "Not specified"}
  - Work: ${otherUserProfile?.work ?: "Not specified"}
  - Location: Lat: ${otherUserProfile?.latitude ?: "Unknown"}, Long: ${otherUserProfile?.longitude ?: "Unknown"}

**Task:**
1. Identify key topics discussed (2 max) from the messages.
2. Suggest two activities based on the topics and the other user's profile, with smooth integration phrases.
3. Provide general tips (2 max) for maintaining conversation tempo.

Return your response as a valid JSON object in this exact format:
```json
{
  "topics": ["topic1", "topic2"],
  "activities": [
    {"placeName": "Activity 1", "integration": "integration phrase1"},
    {"placeName": "Activity 2", "integration": "integration phrase2"}
  ],
  "integrationTips": ["tip1", "tip2"]
}
Ensure the response is a valid JSON object without additional markers or text.
""".trimIndent()

    val response = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
        val railwayUrl = "https://am24.org/openai/chat"
        val chatRequest = ChatRequest(
            model = "llama-3.3-70b-versatile",
            messages = listOf(ChatMessage("system", prompt)),
            max_tokens = 8000
        )
        val jsonBody = gson.toJson(chatRequest)
        val reqBody = jsonBody.toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(railwayUrl).post(reqBody).build()

        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e("ChatSuggestions", "API failed with code: ${resp.code}")
                    return@withContext null
                }
                val rBody = resp.body?.string() ?: return@withContext null
                Log.d("ChatSuggestions", "Raw API response: $rBody")
                rBody
            }
        } catch (e: Exception) {
            Log.e("ChatSuggestions", "Error: ${e.message}", e)
            null
        }
    } ?: return null

// Parse the response as ChatResponse, then extract and parse the content
    return try {
        val chatResponse = gson.fromJson(response, ChatResponse::class.java)
        val content = chatResponse.choices.firstOrNull()?.message?.content ?: return null
        Log.d("ChatSuggestions", "Extracted content: $content")
        gson.fromJson(content, ChatSuggestions::class.java)
    } catch (e: JsonSyntaxException) {
        Log.e("ChatSuggestions", "Failed to parse response: $response", e)
        null
    } catch (e: Exception) {
        Log.e("ChatSuggestions", "Error processing response: ${e.message}", e)
        null
    }
}

suspend fun getPlaceSuggestions(
    topics: List<String>,
    otherUserProfile: Profile?,
    context: Context
): List<PlaceDetails> {
    val otherUserLatLng = otherUserProfile?.let { LatLng(it.latitude ?: 0.0, it.longitude ?: 0.0) } ?: return emptyList()
    val results = mutableListOf<PlaceDetails>()

    topics.take(2).forEach { topic ->
        val places = searchPlacesWithOkHttp(topic, otherUserLatLng, context)
        places.firstOrNull()?.let { (latLng, name) ->
            results.add(
                PlaceDetails(
                    placeName = name,
                    latLng = latLng,
                    address = fetchAddress(latLng, context)
                )
            )
        }
    }

    return results
}

suspend fun searchPlacesWithOkHttp(query: String, userLocation: LatLng, context: Context): List<Pair<LatLng, String>> = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val apiKey = "AIzaSyBJej3hxm7i7Nvd638k4OSMBQLjrueE9aQ"
    val requestBody = JSONObject()
        .put("textQuery", query)
        .put(
            "locationBias",
            JSONObject()
                .put("circle",
                    JSONObject()
                        .put("center",
                            JSONObject()
                                .put("latitude", userLocation.latitude)
                                .put("longitude", userLocation.longitude)
                        )
                        .put("radius", 10000) // 10 km radius
                )
        )
        .toString()
        .toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url("https://places.googleapis.com/v1/places:searchText")
        .addHeader("Content-Type", "application/json")
        .addHeader("X-Goog-Api-Key", apiKey)
        .addHeader("X-Goog-FieldMask", "places.displayName,places.formattedAddress,places.location")
        .post(requestBody)
        .build()

    val results = mutableListOf<Pair<LatLng, String>>()
    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("PlacesSearch", "Error: ${response.code} - ${response.body?.string()}")
                return@withContext emptyList()
            }
            val json = JSONObject(response.body?.string() ?: return@withContext emptyList())
            val places = json.getJSONArray("places")
            for (i in 0 until places.length()) {
                val place = places.getJSONObject(i)
                val name = place.getJSONObject("displayName").getString("text")
                val location = place.getJSONObject("location")
                val lat = location.getDouble("latitude")
                val lng = location.getDouble("longitude")
                results.add(LatLng(lat, lng) to name)
            }
        }
    } catch (e: Exception) {
        Log.e("PlacesSearch", "Exception: ${e.message}", e)
    }
    return@withContext results
}

suspend fun fetchAddress(latLng: LatLng, context: Context): String? = withContext(Dispatchers.IO) {
    // Placeholder: Use the Places API's formattedAddress if available
    val client = OkHttpClient()
    val apiKey = "AIzaSyBJej3hxm7i7Nvd638k4OSMBQLjrueE9aQ"
    val requestBody = JSONObject()
        .put("textQuery", "${latLng.latitude},${latLng.longitude}")
        .put(
            "locationBias",
            JSONObject()
                .put("circle",
                    JSONObject()
                        .put("center",
                            JSONObject()
                                .put("latitude", latLng.latitude)
                                .put("longitude", latLng.longitude)
                        )
                        .put("radius", 100) // Small radius for precision
                )
        )
        .toString()
        .toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url("https://places.googleapis.com/v1/places:searchText")
        .addHeader("Content-Type", "application/json")
        .addHeader("X-Goog-Api-Key", apiKey)
        .addHeader("X-Goog-FieldMask", "places.formattedAddress")
        .post(requestBody)
        .build()

    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("FetchAddress", "Error: ${response.code}")
                return@withContext "Near ${latLng.latitude}, ${latLng.longitude}"
            }
            val json = JSONObject(response.body?.string() ?: return@withContext "Near ${latLng.latitude}, ${latLng.longitude}")
            val places = json.getJSONArray("places")
            if (places.length() > 0) {
                places.getJSONObject(0).optString("formattedAddress", "Near ${latLng.latitude}, ${latLng.longitude}")
            } else {
                "Near ${latLng.latitude}, ${latLng.longitude}"
            }
        }
    } catch (e: Exception) {
        Log.e("FetchAddress", "Exception: ${e.message}")
        "Near ${latLng.latitude}, ${latLng.longitude}"
    }
}