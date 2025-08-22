package com.am24.am24

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.database.FirebaseDatabase
import com.am24.am24.billing.BillingManager
import com.am24.am24.ui.purchase.PurchaseType

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.installAppCheckProviderFactory(
            if (BuildConfig.DEBUG)
                DebugAppCheckProviderFactory.getInstance()
            else
                PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        try { FirebaseDatabase.getInstance().setPersistenceEnabled(true) } catch (_: Exception) {}

        // ---- Play Billing: INAPP packs + SUBS ----
        val packQuantities = listOf(5, 10, 20)
        val inappIds = PurchaseType.values().flatMap { t -> packQuantities.map { q -> t.skuFor(q) } }
        // Create these 2 subscription product IDs in Play Console (each with base plans):
        // - plus
        // - premium
        val subsIds = listOf("plus", "premium")

        BillingManager.startConnection(this, inappIds = inappIds, subsIds = subsIds)

        FirebaseStorage.getInstance("gs://am-twentyfour")
    }
}