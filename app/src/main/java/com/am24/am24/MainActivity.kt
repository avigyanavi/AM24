package com.am24.am24

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.google.firebase.auth.FirebaseAuth
import com.am24.am24.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        setContent {
            AppTheme {
                MainScreen(
                    onNavigateToLanding = {
                        startActivity(Intent(this, LandingActivity::class.java))
                        finish()
                    },
                    onNavigateToExplore = {
                        startActivity(Intent(this, ExploreActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    onNavigateToLanding: () -> Unit,
    onNavigateToExplore: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    if (currentUser != null) {
        // User is logged in, navigate to Explore page
        onNavigateToExplore()
    } else {
        // No user is logged in, navigate to Landing page
        onNavigateToLanding()
    }
}
