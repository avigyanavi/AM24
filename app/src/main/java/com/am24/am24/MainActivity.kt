package com.am24.am24

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.am24.am24.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun attachBaseContext(newBase: Context) {
        // Apply saved language on startup
        val prefs       = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val languageCode= prefs.getString("language", "en") ?: "en"
        super.attachBaseContext(updateLocale(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // Kick off our navigation logic inside a coroutine
        lifecycleScope.launch(Dispatchers.IO) {
            val currentUser = auth.currentUser

            // Read the “finished registration” flag
            val prefs                 = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val registrationFinished  = prefs.getBoolean("registration_finished", false)

            // Decide where to send the user
            val nextIntent = when {
                currentUser != null && !registrationFinished -> {
                    // Incomplete → clean up and force back to landing
                    cleanupIncompleteUser(
                        auth,
                        FirebaseDatabase.getInstance(),
                        FirebaseStorage.getInstance()
                    )
                    Intent(this@MainActivity, LandingActivity::class.java)
                }
                currentUser != null && registrationFinished  -> {
                    // Fully registered → go into the app
                    Intent(this@MainActivity, KupidXAppActivity::class.java)
                }
                else                                         -> {
                    // Not signed in → show landing
                    Intent(this@MainActivity, LandingActivity::class.java)
                }
            }

            withContext(Dispatchers.Main) {
                startActivity(nextIntent)
                finish()
            }
        }
    }
}
