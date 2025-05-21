package com.am24.am24

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.storage.FirebaseStorage

// later for production: PlayIntegrityAppCheckProviderFactory

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)            // usually auto-init
        FirebaseStorage.getInstance("gs://am-twentyfour")

        // Debug provider – good for emulator & dev devices
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
