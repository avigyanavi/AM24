package com.am24.am24

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult      // 🔸
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts       // 🔸
import androidx.compose.runtime.Composable                             // 🔸
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.am24.am24.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private var authListener: FirebaseAuth.AuthStateListener? = null

    private lateinit var auth: FirebaseAuth

    override fun attachBaseContext(newBase: Context) {
        val prefs        = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        super.attachBaseContext(updateLocale(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // Tiny Compose surface for notification permission + loading UI
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

        val openNotifications =
            intent?.getBooleanExtra("open_notifications", false) ?: false

        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            auth.removeAuthStateListener(authListener!!)
            if (user != null) {
                routeBasedOnUid(user.uid, openNotifications)
            } else {
                startActivity(Intent(this@MainActivity, LandingActivity::class.java))
                finish()
            }
        }
        auth.addAuthStateListener(authListener!!)
    }

    @Composable
    private fun AskNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return          // nothing to do pre-Tiramisu

        val context  = LocalContext.current
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* granted / denied callback */ }

        LaunchedEffect(Unit) {                         // ← runs AFTER composition
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun routeBasedOnUid(uid: String, openNotifications: Boolean) =
        lifecycleScope.launch(Dispatchers.IO) {

            val db   = FirebaseDatabase.getInstance().reference
            val snap = db.child("users").child(uid).get().await()

            val finished = snap.child("registrationFinished")
                .getValue(Boolean::class.java) ?: false
            val step = (snap.child("registrationStep")
                .getValue(Long::class.java) ?: 0L).toInt()

            val target = if (finished) {
                Intent(this@MainActivity, KupidXAppActivity::class.java)
                    .putExtra("open_notifications", openNotifications)
            } else {
                Intent(this@MainActivity, RegistrationActivity::class.java)
                    .putExtra("requestedStartStep", step)
            }

            withContext(Dispatchers.Main) { startActivity(target); finish() }
        }

    override fun onDestroy() {
        authListener?.let { auth.removeAuthStateListener(it) }
        super.onDestroy()
    }
}

