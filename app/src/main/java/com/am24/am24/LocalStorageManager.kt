package com.am24.am24

import android.content.Context
import androidx.core.content.edit

object LocalStorageManager {
    private const val PREF_NAME = "RegistrationPrefs"
    private const val KEY_PASSWORD_PREFIX = "password_for_"

    /**
     * Stores a password for a given email in SharedPreferences.
     */
    fun saveEmailPassword(context: Context, email: String, password: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // Convert the email to a normalized (lowercase) key
        val key = KEY_PASSWORD_PREFIX + email.lowercase()
        prefs.edit {
            putString(key, password)
        }
    }

    /**
     * Retrieves the locally stored password for the given email, or null if none stored.
     */
    fun getSavedPasswordForEmail(context: Context, email: String): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = KEY_PASSWORD_PREFIX + email.lowercase()
        return prefs.getString(key, null)
    }

    /**
     * Removes a locally stored password for a given email.
     */
    fun clearEmailPassword(context: Context, email: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = KEY_PASSWORD_PREFIX + email.lowercase()
        prefs.edit {
            remove(key)
        }
    }
}
