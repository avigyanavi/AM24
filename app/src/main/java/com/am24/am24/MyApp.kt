package com.am24.am24

import android.app.Application
import android.os.Build
import android.util.Log
import com.am24.am24.ui.theme.ThemeManager
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.am24.am24.billing.BillingManager
import com.am24.am24.ui.purchase.PurchaseType
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.facebook.FacebookSdk
import com.android.installreferrer.api.ReferrerDetails
import com.facebook.appevents.AppEventsLogger
import com.facebook.appevents.internal.AppEventUtility.isEmulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.SupervisorJob


class MyApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        ThemeManager.initialize(this)


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
        appScope.launch {
            runCatching {
                val appCheck = FirebaseAppCheck.getInstance()
                val providerFactory =
                    if (BuildConfig.DEBUG) {
                        DebugAppCheckProviderFactory.getInstance()
                    } else {
                        if (isEmulator()) DebugAppCheckProviderFactory.getInstance()
                        else PlayIntegrityAppCheckProviderFactory.getInstance()
                    }
                appCheck.installAppCheckProviderFactory(providerFactory)
                Log.d("MyApp", "Firebase App Check initialised off the main thread")
            }.onFailure {
                Log.w("MyApp", "Unable to initialise Firebase App Check", it)
            }
        }

        // ───────── Realtime DB persistence ─────────
        FirebaseRefs.warmUp()

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
        appScope.launch(Dispatchers.IO) {
            try {
                val referrerDetails = fetchInstallReferrer()
                referrerDetails?.installReferrer?.let { installReferrer ->
                    GclidStorageManager.cacheFromQueryString(
                        this@MyApp,
                        installReferrer
                    )
                }
            } catch (e: Exception) {
                Log.w("MyApp", "Unable to initialise install referrer", e)
            }
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
    }


    private suspend fun fetchInstallReferrer(): ReferrerDetails? {
        val referrerClient = InstallReferrerClient.newBuilder(this).build()
        return try {
            suspendCancellableCoroutine { continuation ->
                try {
                    referrerClient.startConnection(object : InstallReferrerStateListener {
                        override fun onInstallReferrerSetupFinished(responseCode: Int) {
                            if (!continuation.isActive) return
                            when (responseCode) {
                                InstallReferrerClient.InstallReferrerResponse.OK -> {
                                    try {
                                        val response = referrerClient.installReferrer
                                        continuation.resume(response)
                                    } catch (e: Exception) {
                                        continuation.resumeWithException(e)
                                    }
                                }
                                InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
                                InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                                    Log.w(
                                        "MyApp",
                                        "Install referrer not available (code=$responseCode)"
                                    )
                                    continuation.resume(null)
                                }
                                else -> {
                                    Log.w(
                                        "MyApp",
                                        "Unexpected install referrer response (code=$responseCode)"
                                    )
                                    continuation.resume(null)
                                }
                            }
                        }

                        override fun onInstallReferrerServiceDisconnected() {
                            // No-op: single fetch only.
                        }
                    })
                } catch (startError: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(startError)
                    }
                }
            }
        } finally {
            try {
                referrerClient.endConnection()
            } catch (closeError: Exception) {
                Log.w("MyApp", "Failed to close install referrer client", closeError)
            }
        }
    }
}
