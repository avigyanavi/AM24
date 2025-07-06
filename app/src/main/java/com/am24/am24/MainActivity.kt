package com.am24.am24

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.am24.am24.ui.theme.AppTheme
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private var authListener: FirebaseAuth.AuthStateListener? = null

    private fun currentProvider(): String {
        val providers = FirebaseAuth.getInstance().currentUser?.providerData
            ?.map { it.providerId } ?: return "unknown"
        return when {
            GoogleAuthProvider.PROVIDER_ID   in providers -> "google"
            FacebookAuthProvider.PROVIDER_ID in providers -> "facebook"
            PhoneAuthProvider.PROVIDER_ID    in providers -> "phone"
            EmailAuthProvider.PROVIDER_ID    in providers -> "emailPassword"
            else                                      -> "unknown"
        }
    }

    // A flag to prevent multiple navigations
    private var isNavigationInProgress = false

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        super.attachBaseContext(updateLocale(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setContent {
            AppTheme {
                AskNotificationPermission()
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFF6F00))
                }
            }
        }

        val openNotifications = intent?.getBooleanExtra("open_notifications", false) ?: false
        val openUpgradeLanding = intent?.getBooleanExtra("open_upgrade_landing", false) ?: false

        // Define the listener
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // User is signed in, route them
                routeBasedOnUid(user, openNotifications, openUpgradeLanding)
            } else {
                // User is signed out, go to Landing
                navigateToLanding()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // It's best practice to attach the listener in onStart()
        authListener?.let { auth.addAuthStateListener(it) }
    }

    override fun onStop() {
        super.onStop()
        // And remove it in onStop() to avoid memory leaks
        authListener?.let { auth.removeAuthStateListener(it) }
    }

    private fun navigateToLanding() {
        if (isNavigationInProgress) return
        isNavigationInProgress = true

        startActivity(Intent(this@MainActivity, LandingActivity::class.java))
        finish()
    }

    @Composable
    private fun AskNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return

        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* granted / denied callback */ }

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun routeBasedOnUid(
        user: FirebaseUser,
        openNotifications: Boolean,
        openUpgradeLanding: Boolean
    ) {
        if (isNavigationInProgress) return
        isNavigationInProgress = true

        lifecycleScope.launch(Dispatchers.IO) {
            val db = FirebaseDatabase.getInstance().reference
            val snap = db.child("users").child(user.uid).get().await()

            val finished = snap.child("registrationFinished")
                .getValue(Boolean::class.java) ?: false
            val step = (snap.child("registrationStep")
                .getValue(Long::class.java) ?: 1L).toInt()

            if (!finished && step >= 8) {
                auth.signOut()
                withContext(Dispatchers.Main) {
                    startActivity(Intent(this@MainActivity, LandingActivity::class.java))
                    finish()
                }
            }
            else {
                val target = if (finished) {
                    Intent(this@MainActivity, KupidXAppActivity::class.java)
                        .putExtra("open_notifications", openNotifications)
                        .putExtra("open_upgrade_landing", openUpgradeLanding)
                } else {
                    Intent(this@MainActivity, RegistrationActivity::class.java)
                        .putExtra("requestedStartStep", step)
                        .putExtra("signInProvider", currentProvider())
                }

                withContext(Dispatchers.Main) {
                    startActivity(target)
                    finish()
                }
            }
        }
    }

    // The `onDestroy` method is no longer needed for the listener,
    // as it's handled in onStop, which is more lifecycle-aware.
}