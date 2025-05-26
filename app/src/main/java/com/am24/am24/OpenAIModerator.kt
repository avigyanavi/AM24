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

/* ---------- hard-coded project key (replace before shipping!) ---------- */
private const val OPENAI_API_KEY =
    "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"

private val client      = OkHttpClient()
private val JSON_TYPE   = "application/json; charset=utf-8".toMediaType()

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

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) false
            else {
                val flagged = JSONObject(resp.body!!.string())
                    .getJSONArray("results")
                    .getJSONObject(0)
                    .getBoolean("flagged")
                flagged
            }
        }
    }

/* ========== UTILITIES you call from ChatScreen ========================= */

fun Bitmap.toBase64(quality: Int = 60): String =
    ByteArrayOutputStream().let { out ->
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

suspend fun Context.uriToBase64(uri: Uri): String = withContext(Dispatchers.IO) {
    contentResolver.openInputStream(uri)!!.use { stream ->
        Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
    }
}

suspend fun Context.videoFramesEvery2s(uri: Uri): List<String> =
    withContext(Dispatchers.Default) {
        val r = MediaMetadataRetriever().apply { setDataSource(this@videoFramesEvery2s, uri) }
        val durMs = r.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )!!.toLong()
        val list = mutableListOf<String>()
        for (t in 0..durMs step 2000) {
            r.getFrameAtTime(t * 1_000L)?.let { bmp -> list += bmp.toBase64() }
        }
        r.release()
        list
    }
