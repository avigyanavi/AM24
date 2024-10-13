// KupidXAppActivity.kt
package com.am24.am24

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.am24.am24.ui.theme.AppTheme
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController

class KupidXAppActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Check if the user is authenticated and email is verified
        val currentUser = auth.currentUser
        if (currentUser == null || !currentUser.isEmailVerified) {
            // User is not authenticated, navigate back to LandingActivity
            startActivity(Intent(this, LandingActivity::class.java))
            finish()
            return
        }

        setContent {
            AppTheme {
                KupidXApp(
                    onLogout = {
                        // Handle logout
                        auth.signOut()
                        startActivity(Intent(this, LandingActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}


@Composable
fun KupidXApp(onLogout: () -> Unit) {
    val navController = rememberNavController()

    MainScreen(navController = navController, onLogout = onLogout)
}
