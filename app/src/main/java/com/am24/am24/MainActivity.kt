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

    // ----- launch -----
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // Tiny Compose surface that only requests POST_NOTIFICATIONS once
        setContent { AskNotificationPermission() }

        // Flag injected by a push-notification tap
        val openNotifications =
            intent?.getBooleanExtra("open_notifications", false) ?: false

        val cachedUser = auth.currentUser
        if (cachedUser == null) {
            // --> not signed in at all
            startActivity(Intent(this, LandingActivity::class.java))
            finish()
        } else {
            // --> we know the UID immediately; let the helper decide where to go
            routeBasedOnUid(cachedUser.uid, openNotifications)
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

    private fun routeBasedOnUid(uid: String, openNotifications: Boolean) =
        lifecycleScope.launch(Dispatchers.IO) {

            val db   = FirebaseDatabase.getInstance().reference
            val snap = db.child("users").child(uid).get().await()

            val finished = snap.child("registrationFinished")
                .getValue(Boolean::class.java) ?: false
            val step = snap.child("registrationStep")
                .getValue(Int::class.java) ?: 0

            val target = if (finished) {
                Intent(this@MainActivity, KupidXAppActivity::class.java)
                    .putExtra("open_notifications", openNotifications)
            } else {
                Intent(this@MainActivity, RegistrationActivity::class.java)
                    .putExtra("requestedStartStep", step)
            }

            withContext(Dispatchers.Main) { startActivity(target); finish() }
        }
}

