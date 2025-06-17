package com.am24.am24

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.am24.am24.ui.theme.AppTheme
import com.google.android.gms.ads.MobileAds
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.razorpay.PaymentResultListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.runtime.*
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.withContext

class KupidXAppActivity : ComponentActivity(), PaymentResultListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var locationManager: LocationManager
    private val postViewModel: PostViewModel by viewModels()

    // Payment callbacks
    private var paymentSuccessCallback: ((String) -> Unit)? = null
    private var paymentErrorCallback:  ((String) -> Unit)? = null

    // Deep-link flag (set in onCreate / onNewIntent)
    private var pendingOpenNotifications = false

    // ------------------------------------------------------------------ locale
    override fun attachBaseContext(newBase: Context) {
        val prefs         = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val languageCode  = prefs.getString("language", "en") ?: "en"
        val locale        = Locale(languageCode)
        val updatedCtx    = newBase.createConfigurationContext(
            newBase.resources.configuration.apply { setLocale(locale) }
        )
        super.attachBaseContext(updatedCtx)
    }

    // ------------------------------------------------------------------ permissions
    private val requestLocationPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val fine  = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarse= perms[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            if (fine || coarse) auth.currentUser?.uid?.let { locationManager.updateUserLocation(it) }
            else Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show()
        }

    // ------------------------------------------------------------------ lifecycle
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔸 read the flag the very first time
        pendingOpenNotifications = intent?.getBooleanExtra("open_notifications", false) ?: false

        MobileAds.initialize(this)
        FirebaseStorage.getInstance("gs://am-twentyfour.com")

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LandingActivity::class.java))
            finish()
            return
        }

        locationManager = LocationManager(this)
        checkLocationPermissionsAndUpdate(currentUser.uid)
        postViewModel.loadFiltersFromFirebase(currentUser.uid)

        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .crossfade(true)
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizePercent(0.05)
                        .build()
                }
                .memoryCache { MemoryCache.Builder(applicationContext).maxSizePercent(0.25).build() }
                .build()
        )

        // ----------------------------- COMPOSE UI
        setContent {
            AppTheme {
                KupidXApp(
                    postViewModel = postViewModel,
                    openNotifications = pendingOpenNotifications,          // 🔸 inject flag
                    onFlagConsumed = { pendingOpenNotifications = false }, // 🔸 reset after nav
                    onLogout = {
                        auth.signOut()
                        startActivity(Intent(this, LandingActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }

    // handles subsequent taps when activity already alive ----------------- 🔸
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_notifications", false)) {
            pendingOpenNotifications = true
            // trigger ui update; simplest is to recreate() since Compose is already state-safe
            recreate()
        }
    }

    // ------------------------------------------------------------------ helpers
    private fun checkLocationPermissionsAndUpdate(uid: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED
        ) {
            locationManager.updateUserLocation(uid)
        } else {
            requestLocationPerms.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Payment callback hooks ----------------------------------------------
    fun setPaymentCallbacks(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        paymentSuccessCallback = onSuccess; paymentErrorCallback = onError
    }
    override fun onPaymentSuccess(p0: String?) {
        if (p0 != null) {
            paymentSuccessCallback?.invoke(p0)
        } else {
            paymentErrorCallback?.invoke("Payment succeeded but no payment ID received.")
        }
    }

    override fun onPaymentError(code: Int, response: String?) {
        paymentErrorCallback?.invoke("Payment failed: $response")
    }
}

/*───────────────────────────────────────────────────────────────────────────*/

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun KupidXApp(
    postViewModel: PostViewModel,
    openNotifications: Boolean,
    onFlagConsumed: () -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()

    // 🔸 Navigate once when flag is true
    LaunchedEffect(openNotifications) {
        if (openNotifications) {
            navController.navigate("notifications")
            onFlagConsumed()           // clear so configuration changes don’t re-fire
        }
    }

    MainScreen(
        navController = navController,
        onLogout = onLogout,
        postViewModel = postViewModel
    )
}
