package com.am24.am24

import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.tasks.await

/**
 * Centralized helper to decide whether phone-based authentication should be exposed
 * on the client. The Cloud Function keeps the `/config/phoneAuth/enabled` flag in
 * sync with the daily cap; the client simply reads and caches that flag.
 */
object PhoneAuthGatekeeper {
    private val gateRef: DatabaseReference by lazy {
        FirebaseRefs.db.reference.child("config").child("phoneAuth")
    }

    /**
     * Returns `true` when the backend has phone authentication enabled. If the flag
     * is missing or the network request fails, we default to `true` so that the
     * experience degrades gracefully.
     */
    suspend fun isPhoneAuthEnabled(): Boolean {
        return try {
            val snapshot = gateRef.child("enabled").get().await()
            snapshot.getValue(Boolean::class.java) ?: true
        } catch (_: Exception) {
            true
        }
    }
}