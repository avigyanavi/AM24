package com.am24.am24

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.database.FirebaseDatabase

// later for production: PlayIntegrityAppCheckProviderFactory

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.installAppCheckProviderFactory(
            if (BuildConfig.DEBUG)
                DebugAppCheckProviderFactory.getInstance()          // local / emulator
            else
                PlayIntegrityAppCheckProviderFactory.getInstance()  // Play-store builds
        )

//        FirebaseApp.initializeApp(this)            // usually auto-init

        // ✅ Enable persistence — critical line
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        FirebaseStorage.getInstance("gs://am-twentyfour")
    }
}
