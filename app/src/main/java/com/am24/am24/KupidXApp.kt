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
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.Coil
import com.am24.am24.ui.theme.AppTheme
import com.google.firebase.auth.FirebaseAuth
import com.razorpay.PaymentResultListener
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.storage.FirebaseStorage
import java.util.Locale

class KupidXAppActivity : ComponentActivity(), PaymentResultListener {
    private lateinit var auth: FirebaseAuth
    private lateinit var locationManager: LocationManager
    private val postViewModel: PostViewModel by viewModels()

    // Payment callbacks
    private var paymentSuccessCallback: ((String) -> Unit)? = null
    private var paymentErrorCallback: ((String) -> Unit)? = null

    // Override attachBaseContext to apply the locale from SharedPreferences
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en" // Default to "en" if not set
        val locale = Locale(languageCode)
        val config = newBase.resources.configuration.apply {
            setLocale(locale)
        }
        val updatedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(updatedContext)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineLocationGranted || coarseLocationGranted) {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    locationManager.updateUserLocation(currentUser.uid)
                }
            } else {
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // Set up global Coil caching
        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .crossfade(true)
                .diskCache {
                    DiskCache.Builder()
                        .directory(applicationContext.cacheDir.resolve("image_cache"))
                        .maxSizePercent(0.05) // Adjust cache size (5% of available storage)
                        .build()
                }
                .memoryCache {
                    MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.25) // 25% of available memory
                        .build()
                }
                .build()
        )

        setContent {
            AppTheme {
                KupidXApp(
                    onLogout = {
                        auth.signOut()
                        startActivity(Intent(this, LandingActivity::class.java))
                        finish()
                    },
                    postViewModel = postViewModel
                )
            }
        }
    }

    private fun checkLocationPermissionsAndUpdate(userId: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED
        ) {
            locationManager.updateUserLocation(userId)
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Set payment callbacks from SubscriptionScreen
    fun setPaymentCallbacks(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        paymentSuccessCallback = onSuccess
        paymentErrorCallback = onError
    }

    override fun onPaymentSuccess(paymentId: String?) {
        if (paymentId != null) {
            paymentSuccessCallback?.invoke(paymentId)
        } else {
            paymentErrorCallback?.invoke("Payment succeeded but no payment ID received.")
        }
    }

    override fun onPaymentError(code: Int, response: String?) {
        paymentErrorCallback?.invoke("Payment failed: $response")
    }
}

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun KupidXApp(onLogout: () -> Unit, postViewModel: PostViewModel) {
    val navController = rememberNavController()
    MainScreen(navController = navController, onLogout = onLogout, postViewModel = postViewModel)
}