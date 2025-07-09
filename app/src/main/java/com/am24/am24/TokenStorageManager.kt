package com.am24.am24

import android.content.Context
import androidx.core.content.edit

/**
 * Simple helper for persisting the Firebase ID token.
 */
object TokenStorageManager {
    private const val PREF_NAME = "token_prefs"
    private const val KEY_TOKEN = "firebase_id_token"

    /** Save an ID token into SharedPreferences. */
    fun saveToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_TOKEN, token) }
    }

    /** Retrieve the stored ID token, or null if not present. */
    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, null)
    }

    /** Remove any cached ID token. */
    fun clearToken(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove(KEY_TOKEN) }
    }
}