package com.am24.am24

import android.app.Application
import android.os.Build
import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.am24.am24.billing.BillingManager
import com.am24.am24.ui.purchase.PurchaseType
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // ───────── Facebook: EXPLICIT "old style" init for reliable install capture ─────────
        // Make sure Manifest also uses the SAME App ID and fb<APP_ID> scheme.
        FacebookSdk.setApplicationId("606416195185970")                       // ← OLD APP ID
        FacebookSdk.setClientToken("c2deeaa408a6cc080a2f914008805bcd")       // ← Your client token
        FacebookSdk.setAutoInitEnabled(false)                                // we control init manually
        FacebookSdk.setAutoLogAppEventsEnabled(true)
        FacebookSdk.setAdvertiserIDCollectionEnabled(true)
        FacebookSdk.sdkInitialize(applicationContext)                        // ← explicit init
        AppEventsLogger.activateApp(this)                                    // ← install/activate ping

        // ───────── Firebase App Check ─────────
        val appCheck = FirebaseAppCheck.getInstance()
        val providerFactory =
            if (BuildConfig.DEBUG) {
                DebugAppCheckProviderFactory.getInstance()
            } else {
                val isEmulator = Build.FINGERPRINT.contains("generic")
                if (isEmulator) DebugAppCheckProviderFactory.getInstance()
                else PlayIntegrityAppCheckProviderFactory.getInstance()
            }
        appCheck.installAppCheckProviderFactory(providerFactory)

        // ───────── Realtime DB persistence ─────────
        try { FirebaseDatabase.getInstance().setPersistenceEnabled(true) } catch (_: Exception) {}

        // ───────── Play Billing: INAPP packs + SUBS ─────────
        val packQuantities = listOf(5, 10, 20)
        val inappIds = PurchaseType.values()
            .flatMap { t -> packQuantities.map { q -> t.skuFor(q) } }
            .plus("entry_fee")
        val subsIds = listOf("plus", "premium")

        if (!BuildConfig.DEBUG && !isEmulator()) {
            BillingManager.startConnection(this, inappIds = inappIds, subsIds = subsIds)
        } else {
            Log.d("MyApp", "Skipping BillingManager connection in debug/emulator mode")
        }

        // ───────── Storage bucket ─────────
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

    companion object {
        lateinit var instance: MyApp
            private set
    }
}
