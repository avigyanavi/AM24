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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private var authListener: FirebaseAuth.AuthStateListener? = null

    // NEW: Prevent early false-null redirect
    private var startupTime: Long = 0L
    private var ignoreNullsUntil: Long = 0L

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

    private var isNavigationInProgress = false

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val defaultLang = defaultLanguageCode()
        val languageCode = prefs.getString("language", defaultLang) ?: defaultLang
        super.attachBaseContext(updateLocale(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        GclidStorageManager.cacheFromUri(this, intent?.data)

        // NEW
        startupTime = System.currentTimeMillis()
        ignoreNullsUntil = startupTime + 500  // 500 ms window to avoid false nulls

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

        // 🔥 NEW: One-shot snapshot check
        val initialUser = auth.currentUser
        if (initialUser != null) {
            routeBasedOnUid(initialUser, openNotifications, openUpgradeLanding)
        }

        // ORIGINAL LISTENER, but with null-ignore logic added
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            val now = System.currentTimeMillis()

            if (user != null) {
                user.getIdToken(true)
                    .addOnSuccessListener { res ->
                        res.token?.let { TokenStorageManager.saveToken(this@MainActivity, it) }
                    }
                routeBasedOnUid(user, openNotifications, openUpgradeLanding)
            } else {
                // ❗ Ignore nulls during first 500ms — the hydration window
                if (now < ignoreNullsUntil) {
                    return@AuthStateListener
                }
                navigateToLanding()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        authListener?.let { auth.addAuthStateListener(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        GclidStorageManager.cacheFromUri(this, intent.data)
    }

    override fun onStop() {
        super.onStop()
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
        ) { /* callback */ }

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
            val db = FirebaseRefs.db.reference
            val userRef = db.child("users").child(user.uid)
            val snap = userRef.get().await()

            val now = System.currentTimeMillis()
            val isPlus = snap.child("isPlus").getValue(Boolean::class.java) == true
            val isEntryFeePaid = snap.child("isEntryFeePaid").getValue(Boolean::class.java) == true
            val loginPlusExpiry = snap.child("loginPlusExpiry").getValue(Long::class.java) ?: 0L
            val entryFeePaidAt = snap.child("entryFeePaidAt").getValue(Long::class.java) ?: 0L
            val currentRenewal = snap.child("nextRenewal").getValue(Long::class.java) ?: 0L

            val entryFeeExpiryFromPaidAt = if (entryFeePaidAt > 0L) {
                entryFeePaidAt + TimeUnit.DAYS.toMillis(30)
            } else 0L
            val entryFeeActive = isEntryFeePaid && (
                    loginPlusExpiry > now ||
                            (entryFeeExpiryFromPaidAt > now && entryFeeExpiryFromPaidAt > 0L) ||
                            currentRenewal > now
                    )

            if (!isPlus && entryFeeActive) {
                val resolvedRenewal = listOf(loginPlusExpiry, entryFeeExpiryFromPaidAt)
                    .filter { it > now }
                    .maxOrNull()

                val updates = mutableMapOf<String, Any>("isPlus" to true)
                val desiredRenewal = resolvedRenewal ?: 0L
                if (desiredRenewal > 0L && desiredRenewal != currentRenewal) {
                    updates["nextRenewal"] = desiredRenewal
                }

                if (updates.isNotEmpty()) {
                    userRef.updateChildren(updates).await()
                }
            }

            GclidStorageManager.flushPendingGclid(
                this@MainActivity,
                user.uid,
                snap.child("gclid").getValue(String::class.java)
            )

            val finished = snap.child("registrationFinished")
                .getValue(Boolean::class.java) ?: false
            val step = (snap.child("registrationStep")
                .getValue(Long::class.java) ?: 1L).toInt()

            val hasProfile = !snap.child("username")
                .getValue(String::class.java)
                .isNullOrBlank()

            val target = if (finished || hasProfile) {
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
