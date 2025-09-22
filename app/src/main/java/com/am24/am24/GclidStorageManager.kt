package com.am24.am24

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.tasks.await

/**
 * Persists a pending Google Click Identifier (GCLID) between the marketing deeplink
 * and the moment a Firebase UID becomes available.
 */
object GclidStorageManager {
    private const val PREF_NAME = "gclid_prefs"
    private const val KEY_VALUE = "pending_gclid"
    private const val KEY_TIMESTAMP = "pending_gclid_timestamp"
    private const val TAG = "GclidStorage"

    /** Persist a raw GCLID value if it is non-empty. */
    fun cacheGclid(context: Context, gclid: String?): Boolean {
        val value = gclid?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_VALUE, value)
            putLong(KEY_TIMESTAMP, System.currentTimeMillis())
        }
        return true
    }

    /** Extracts the gclid query parameter from a URI and stores it if present. */
    fun cacheFromUri(context: Context, uri: Uri?): Boolean {
        val gclid = uri?.getQueryParameter("gclid")
        return cacheGclid(context, gclid)
    }

    /** Extracts the gclid query parameter from a raw query string (e.g. install referrer). */
    fun cacheFromQueryString(context: Context, queryString: String?): Boolean {
        if (queryString.isNullOrBlank()) return false
        val gclid = try {
            Uri.parse("https://dummy.com/?$queryString").getQueryParameter("gclid")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse referrer for gclid", e)
            null
        }
        return cacheGclid(context, gclid)
    }

    /** Returns any cached GCLID waiting to be uploaded. */
    fun getPendingGclid(context: Context, maxAgeMs: Long = 30L * 24 * 60 * 60 * 1000): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val ts = prefs.getLong(KEY_TIMESTAMP, 0L)
        if (System.currentTimeMillis() - ts > maxAgeMs) return null
        return prefs.getString(KEY_VALUE, null)
    }

    /** Optional helper for analytics/debugging. */
    fun getCachedTimestamp(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_TIMESTAMP)) prefs.getLong(KEY_TIMESTAMP, 0L) else null
    }

    /** Clears any cached GCLID after it has been uploaded or intentionally discarded. */
    fun clearPendingGclid(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            remove(KEY_VALUE)
            remove(KEY_TIMESTAMP)
        }
    }

    /** Writes the supplied GCLID into Realtime Database under /users/{uid}/gclid. */
    suspend fun saveGclidToFirebase(uid: String, gclid: String) {
        if (gclid.isBlank()) return
        FirebaseRefs.db.reference
            .child("users")
            .child(uid)
            .child("gclid")
            .setValue(gclid)
            .await()
    }

    /** Upload any locally cached GCLID if the remote record has not already been stamped. */
    suspend fun flushPendingGclid(context: Context, uid: String, remoteValue: String? = null) {
        val pending = getPendingGclid(context)?.takeIf { it.isNotBlank() } ?: return
        if (!remoteValue.isNullOrBlank()) {
            // Already stored remotely – clear the cache and exit.
            clearPendingGclid(context)
            return
        }
        try {
            saveGclidToFirebase(uid, pending)
            clearPendingGclid(context)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to upload pending gclid", e)
        }
    }
}