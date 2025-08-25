package com.am24.am24

import android.app.Application
import android.os.Build
import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.database.FirebaseDatabase
import com.am24.am24.billing.BillingManager
import com.am24.am24.ui.purchase.PurchaseType
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        FacebookSdk.sdkInitialize(applicationContext)
        FacebookSdk.setAutoInitEnabled(true)
        AppEventsLogger.activateApp(this)

        val appCheck = FirebaseAppCheck.getInstance()
        val providerFactory =
            if (BuildConfig.DEBUG) {
                DebugAppCheckProviderFactory.getInstance()
            }
            else
            {
                val isEmulator = Build.FINGERPRINT.contains("generic")
                if (isEmulator) {
                    DebugAppCheckProviderFactory.getInstance()
                } else {
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                }
            }
        appCheck.installAppCheckProviderFactory(providerFactory)

        try { FirebaseDatabase.getInstance().setPersistenceEnabled(true) } catch (_: Exception) {}

        // ---- Play Billing: INAPP packs + SUBS ----
        val packQuantities = listOf(5, 10, 20)
        val inappIds = PurchaseType.values().flatMap { t -> packQuantities.map { q -> t.skuFor(q) } }
        // Create these 2 subscription product IDs in Play Console (each with base plans):
        // - plus
        // - premium
        val subsIds = listOf("plus", "premium")

        if (!BuildConfig.DEBUG && !isEmulator()) {
            BillingManager.startConnection(this, inappIds = inappIds, subsIds = subsIds)
        } else {
            Log.d("MyApp", "Skipping BillingManager connection in debug/emulator mode")
        }

        FirebaseStorage.getInstance("gs://am-twentyfour")
    }
    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.lowercase().contains("vbox") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion")
    }
}