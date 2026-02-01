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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.am24.am24.ui.theme.AppTheme
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// ---------- AUTH STATE + VIEWMODEL ----------

sealed class AuthState {
    object Loading : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
    object Unauthenticated : AuthState()
}

class AuthViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _authState.value = if (user != null) {
            AuthState.Authenticated(user)
        } else {
            AuthState.Unauthenticated
        }
    }

    init {
        // Initial snapshot + small delay to avoid "false null" on cold start
        val current = auth.currentUser
        if (current != null) {
            _authState.value = AuthState.Authenticated(current)
        } else {
            viewModelScope.launch {
                _authState.value = AuthState.Loading
                delay(400) // brief hydration window
                val u = auth.currentUser
                _authState.value = if (u != null) {
                    AuthState.Authenticated(u)
                } else {
                    AuthState.Unauthenticated
                }
            }
        }

        auth.addAuthStateListener(listener)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(listener)
        super.onCleared()
    }
}

// ---------- MAIN ACTIVITY ----------

class MainActivity : ComponentActivity() {

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

    // Prevent multiple navigations
    private var isNavigationInProgress = false

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val defaultLang = defaultLanguageCode()
        val languageCode = prefs.getString("language", defaultLang) ?: defaultLang
        super.attachBaseContext(updateLocale(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GclidStorageManager.cacheFromUri(this, intent?.data)

        val openNotifications = intent?.getBooleanExtra("open_notifications", false) ?: false
        val openUpgradeLanding = intent?.getBooleanExtra("open_upgrade_landing", false) ?: false
        val openChatUserId = intent?.getStringExtra("open_chat_user_id")
        setContent {
            AppTheme {
                AskNotificationPermission()

                val authViewModel: AuthViewModel = viewModel()
                val authState by authViewModel.authState.collectAsState()

                when (authState) {
                    AuthState.Loading -> {
                        // Show spinner while we don't know yet
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFFF6F00))
                        }
                    }

                    is AuthState.Authenticated -> {
                        val user = (authState as AuthState.Authenticated).user

                        // Navigate once when authenticated
                        LaunchedEffect(user.uid, openNotifications, openUpgradeLanding) {
                            routeBasedOnUid(
                                user = user,
                                openNotifications = openNotifications,
                                openUpgradeLanding = openUpgradeLanding,
                                openChatUserId = openChatUserId
                            )
                        }

                        // Still show a loading screen while navigation happens
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFFF6F00))
                        }
                    }

                    AuthState.Unauthenticated -> {
                        // Navigate to Landing once when we *know* there is no user
                        LaunchedEffect(Unit) {
                            navigateToLanding()
                        }

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFFF6F00))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        GclidStorageManager.cacheFromUri(this, intent.data)
    }

    private fun navigateToLanding() {
        if (isNavigationInProgress) return
        isNavigationInProgress = true

        startActivity(Intent(this@MainActivity, LandingActivity::class.java))
        finishAfterTransitionDelay()
    }

    private fun finishAfterTransitionDelay() {
        window.decorView.post { finish() }
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
        openUpgradeLanding: Boolean,
        openChatUserId: String?
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
                    .putExtra("open_chat_user_id", openChatUserId)
            } else {
                Intent(this@MainActivity, RegistrationActivity::class.java)
                    .putExtra("requestedStartStep", step)
                    .putExtra("signInProvider", currentProvider())
            }
            withContext(Dispatchers.Main) {
                startActivity(target)
                finishAfterTransitionDelay()
            }
        }
    }
}
