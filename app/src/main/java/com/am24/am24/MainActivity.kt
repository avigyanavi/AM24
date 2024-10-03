package com.am24.am24

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Launch a coroutine within the lifecycle scope
        lifecycleScope.launch(Dispatchers.IO) {
            val currentUser = auth.currentUser

            withContext(Dispatchers.Main) {
                if (currentUser != null) {
                    // User is logged in, navigate to ExploreActivity
                    startActivity(Intent(this@MainActivity, HomeActivity::class.java))
                } else {
                    // User is not logged in, navigate to LandingActivity
                    startActivity(Intent(this@MainActivity, LandingActivity::class.java))
                }
                finish()  // Finish MainActivity so the user cannot navigate back to it
            }
        }
    }
}
