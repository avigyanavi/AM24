package com.am24.am24

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.am24.am24.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        super.attachBaseContext(updateLocale(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        lifecycleScope.launch(Dispatchers.IO) {
            val currentUser = auth.currentUser
            withContext(Dispatchers.Main) {
                if (currentUser != null) {
                    // Navigate to the main app screen if logged in
                    startActivity(Intent(this@MainActivity, KupidXAppActivity::class.java))
                } else {
                    // Otherwise, go to the landing screen
                    startActivity(Intent(this@MainActivity, LandingActivity::class.java))
                }
                finish()
            }
        }
    }
}
