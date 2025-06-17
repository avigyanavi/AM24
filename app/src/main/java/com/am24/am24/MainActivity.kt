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

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun attachBaseContext(newBase: Context) {
        val prefs        = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        super.attachBaseContext(updateLocale(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // 🔸 Show a tiny Compose surface ONLY to ask for the runtime permission once.
        setContent { AskNotificationPermission() }

        // Propagate deep-link info from push tap
        val openNotifications = intent?.getBooleanExtra("open_notifications", false) ?: false

        lifecycleScope.launch(Dispatchers.IO) {
            val dbRef       = FirebaseDatabase.getInstance().reference
            val currentUser = auth.currentUser

            val nextIntent = when {
                // ── Not signed in at all ──
                currentUser == null -> {
                    Intent(this@MainActivity, LandingActivity::class.java)
                }
                else -> {
                    val uid  = currentUser.uid
                    val usernameSnap = dbRef.child("users").child(uid)
                        .child("username").get().await()
                    val username = usernameSnap.getValue(String::class.java)

                    if (username.isNullOrBlank()) {
                        Intent(this@MainActivity, RegistrationActivity::class.java)
                            .putExtra("requestedStartStep", 2)
                    } else {
                        val finished = dbRef.child("publicUsers").child(username)
                            .child("registrationFinished").get().await()
                            .getValue(Boolean::class.java) ?: false

                        if (finished) {
                            Intent(this@MainActivity, KupidXAppActivity::class.java)
                                .putExtra("open_notifications", openNotifications)   // 🔸 forward
                        } else {
                            Intent(this@MainActivity, RegistrationActivity::class.java)
                                .putExtra("requestedStartStep", 2)
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                startActivity(nextIntent)
                finish()
            }
        }
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
}
