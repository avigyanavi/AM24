package com.am24.am24

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import com.am24.am24.ui.theme.AppTheme
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController

class KupidXAppActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var locationManager: LocationManager

    // Registering the permission result launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineLocationGranted || coarseLocationGranted) {
                // Permissions granted, update user's location
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    locationManager.updateUserLocation(currentUser.uid)
                }
            } else {
                // Permission denied, show a message
                Toast.makeText(this, "Location permission denied. Cannot retrieve location.", Toast.LENGTH_SHORT).show()
            }
        }

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

        // Initialize LocationManager
        locationManager = LocationManager(this)

        // Check location permissions and update user's location
        checkLocationPermissionsAndUpdate(currentUser.uid)

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

    private fun checkLocationPermissionsAndUpdate(userId: String) {
        // Check if location permissions are granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED) {
            // Permissions already granted, update user's location
            locationManager.updateUserLocation(userId)
        } else {
            // Request permissions if not granted
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}

@Composable
fun KupidXApp(onLogout: () -> Unit) {
    val navController = rememberNavController()

    MainScreen(navController = navController, onLogout = onLogout)
}
