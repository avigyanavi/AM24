package com.am24.am24

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Stores the last email+password and can try a silent auto-login.
 *
 * ⚠️ This stores RAW PASSWORD on device. Use only as a deliberate hack.
 */
object CredentialsStorageManager {

    private const val PREF_NAME = "auth_prefs"
    private const val KEY_EMAIL = "last_email"
    private const val KEY_PASSWORD = "last_password"

    /** Save the last-used email/password pair. */
    fun saveCredentials(context: Context, email: String, password: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_EMAIL, email.trim().lowercase())
            putString(KEY_PASSWORD, password)
        }
    }

    /** Clear any stored email/password pair. */
    fun clearCredentials(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            remove(KEY_EMAIL)
            remove(KEY_PASSWORD)
        }
    }

    private fun getEmailAndPassword(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val email = prefs.getString(KEY_EMAIL, null)
        val password = prefs.getString(KEY_PASSWORD, null)
        return if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
            email to password
        } else {
            null
        }
    }

    /**
     * Try to silently log in using the stored email/password.
     *
     * @return true if login succeeded, false otherwise.
     */
    suspend fun tryAutoLogin(context: Context): Boolean {
        val creds = getEmailAndPassword(context) ?: return false

        val (email, password) = creds
        return try {
            FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(email, password)
                .await()
            true
        } catch (_: Exception) {
            false
        }
    }
}
