package com.am24.am24

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import android.util.Log
import java.io.IOException


/* ---------- hard-coded project key (replace before shipping!) ---------- */
private const val OPENAI_API_KEY =
    "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"

private const val TAG = "OpenAIModerator"

private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)   // TCP/TLS handshake
    .writeTimeout(45, TimeUnit.SECONDS)     // upload big JPEG bodies
    .readTimeout(60, TimeUnit.SECONDS)      // wait for the first response byte
    .callTimeout(90, TimeUnit.SECONDS)      // whole call‐level safety net
    .retryOnConnectionFailure(true)         // automatic retry on flaky links
    .build()
private val JSON_TYPE   = "application/json; charset=utf-8".toMediaType()

var moderationErrorCallback: (() -> Unit)? = null


/* ========== PUBLIC API ================================================= */

/** true == unsafe / flagged  */
suspend fun moderateText(text: String): Boolean =
    postModeration(JSONArray().put(text))

/** supply 1-N Base-64 JPEG strings */
suspend fun moderateImages(base64: List<String>): Boolean {
    val arr = JSONArray()
    base64.forEach { data ->
        arr.put(
            JSONObject()
                .put("type", "image_url")
                .put("image_url",
                    JSONObject().put("url", "data:image/jpeg;base64,$data"))
        )
    }
    return postModeration(arr)
}

/* ========== INTERNAL =================================================== */

private suspend fun postModeration(input: JSONArray): Boolean =
    withContext(Dispatchers.IO) {
        val bodyJson = JSONObject()
            .put("model", "omni-moderation-latest")
            .put("input", input)
            .toString()

        val req = Request.Builder()
            .url("https://api.openai.com/v1/moderations")
            .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
            .post(bodyJson.toRequestBody(JSON_TYPE))
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    false
                } else {
                    val bodyString = resp.body?.string()
                    if (bodyString == null) {
                        moderationErrorCallback?.invoke()
                        false
                    } else {
                        val flagged = JSONObject(bodyString)
                            .getJSONArray("results")
                            .getJSONObject(0)
                            .getBoolean("flagged")
                        flagged
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Moderation request failed", e)
            moderationErrorCallback?.invoke()
            false
        }
    }

/* ========== UTILITIES you call from ChatScreen ========================= */

fun Bitmap.toBase64(quality: Int = 60): String =
    ByteArrayOutputStream().let { out ->
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

suspend fun Context.uriToBase64(uri: Uri): String = withContext(Dispatchers.IO) {
    try {
        contentResolver.openInputStream(uri)?.use { stream ->
            Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
        } ?: ""
    } catch (e: Exception) {
        Log.e(TAG, "uriToBase64 failed", e)
        ""
    }
}

suspend fun Context.videoFramesEvery2s(uri: Uri): List<String> =
    withContext(Dispatchers.Default) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(this@videoFramesEvery2s, uri)
            val durMs = r.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            val list = mutableListOf<String>()
            for (t in 0..durMs step 2000) {
                r.getFrameAtTime(t * 1_000L)?.let { bmp -> list += bmp.toBase64() }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "videoFramesEvery2s failed", e)
            emptyList()
        } finally {
            r.release()
        }
    }
