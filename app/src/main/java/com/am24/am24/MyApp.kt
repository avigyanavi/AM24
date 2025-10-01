package com.am24.am24

import android.app.Application
import android.os.Build
import android.util.Log
import com.am24.am24.ui.theme.ThemeManager
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.am24.am24.billing.BillingManager
import com.am24.am24.ui.purchase.PurchaseType
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.am24.am24.AppOpenAdManager
import com.google.android.gms.ads.MobileAds

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        MobileAds.initialize(this)

        ThemeManager.initialize(this)

        appOpenAdManager = AppOpenAdManager(
            this,
            "ca-app-pub-5266481866342618/4937350764",
        )
        appOpenAdManager.loadAd()

// ───────── Facebook: Explicit init ─────────
        FacebookSdk.setApplicationId("606416195185970")
        FacebookSdk.setClientToken("c2deeaa408a6cc080a2f914008805bcd")
        FacebookSdk.setAutoInitEnabled(true) // let it auto-init at boot
        FacebookSdk.setAutoLogAppEventsEnabled(true)
        FacebookSdk.setAdvertiserIDCollectionEnabled(true)

// Kick off init synchronously
        FacebookSdk.sdkInitialize(applicationContext)
        AppEventsLogger.activateApp(this) // safe even if called multiple times

        Log.d("MyApp", "Facebook SDK initialized synchronously")

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
        val oneTimePlusPremium = listOf(
            "kupidx_plus_one_month",
            "kupidx_plus_one_year",
            "kupidx_premium_one_month",
            "kupidx_premium_one_year",
        )

        val inappIds = PurchaseType.values()
            .flatMap { t -> packQuantities.map { q -> t.skuFor(q) } }
            .plus("entry_fee")
            .plus(oneTimePlusPremium)
        val subsIds = listOf("plus", "premium")

        if (!BuildConfig.DEBUG && !isEmulator()) {
            BillingManager.startConnection(this, inappIds = inappIds, subsIds = subsIds)
        } else {
            Log.d("MyApp", "Skipping BillingManager connection in debug/emulator mode")
        }

        // ───────── Storage bucket ─────────
        FirebaseStorage.getInstance("gs://am-twentyfour")
        try {
            val referrerClient = InstallReferrerClient.newBuilder(this).build()
            referrerClient.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    when (responseCode) {
                        InstallReferrerClient.InstallReferrerResponse.OK -> {
                            try {
                                val response = referrerClient.installReferrer
                                GclidStorageManager.cacheFromQueryString(
                                    this@MyApp,
                                    response.installReferrer
                                )
                            } catch (e: Exception) {
                                Log.w("MyApp", "Failed to read install referrer", e)
                            } finally {
                                referrerClient.endConnection()
                            }
                        }
                        InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
                        InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                            referrerClient.endConnection()
                        }
                        else -> referrerClient.endConnection()
                    }
                }

                override fun onInstallReferrerServiceDisconnected() {
                    // No-op: we only need a single fetch.
                }
            })
        } catch (e: Exception) {
            Log.w("MyApp", "Unable to initialise install referrer", e)
        }
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

        lateinit var appOpenAdManager: AppOpenAdManager
            private set
    }
}
