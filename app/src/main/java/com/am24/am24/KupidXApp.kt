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
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.am24.am24.ui.purchase.PaymentResultListenerHost
import com.am24.am24.ui.theme.AppTheme
import com.google.android.gms.ads.MobileAds
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.razorpay.Checkout                       // NEW
import com.razorpay.ExternalWalletListener        // NEW
import com.razorpay.PaymentData                   // NEW
import com.razorpay.PaymentResultWithDataListener // NEW
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.util.Locale

class KupidXAppActivity : ComponentActivity(),
    PaymentResultWithDataListener,          // replaces old PaymentResultListener
    ExternalWalletListener,                 // required because we pass wallet listener
    PaymentResultListenerHost {             // used by OneTimePurchaseScreen

    /* ------------------------------------------------------------------ state */
    private lateinit var auth: FirebaseAuth
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private lateinit var locationManager: LocationManager
    private val postViewModel: PostViewModel by viewModels()

    // callbacks wired from the Composable screen
    private var paymentSuccessCallback: ((String) -> Unit)? = null
    private var paymentErrorCallback:  ((String) -> Unit)? = null

    // deep-link flag
    private var pendingOpenNotifications = false

    /* ------------------------------------------------------------------ locale */
    override fun attachBaseContext(newBase: Context) {
        val prefs        = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        val updatedCtx   = newBase.createConfigurationContext(
            newBase.resources.configuration.apply { setLocale(Locale(languageCode)) }
        )
        super.attachBaseContext(updatedCtx)
    }

    /* ---------------------------------------------------------------- permissions */
    private val requestLocationPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val fine   = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarse = perms[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            if (fine || coarse) auth.currentUser?.uid?.let { locationManager.updateUserLocation(it) }
            else Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show()
        }

    /* ---------------------------------------------------------------- lifecycle */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Show a loading UI while waiting for auth state
        setContent {
            AppTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFF6F00))
                }
            }
        }

        // read push-flag once
        pendingOpenNotifications =
            intent?.getBooleanExtra("open_notifications", false) ?: false

        // Keep listener active until we get a user or confirm sign-out
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            auth.removeAuthStateListener(authListener!!)
            if (user != null) {
                continueInitialization(user.uid)
            } else {
                startActivity(Intent(this@KupidXAppActivity, LandingActivity::class.java))
                finish()
            }
        }
        auth.addAuthStateListener(authListener!!)
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun continueInitialization(uid: String) {
        MobileAds.initialize(this)
        FirebaseStorage.getInstance("gs://am-twentyfour.com")
        locationManager = LocationManager(this)
        checkLocationPermissionsAndUpdate(uid)
        postViewModel.loadFiltersFromFirebase(uid)

        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .crossfade(true)
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizePercent(0.05)
                        .build()
                }
                .memoryCache {
                    MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.25)
                        .build()
                }
                .build()
        )

        setContent {
            AppTheme {
                KupidXApp(
                    postViewModel       = postViewModel,
                    openNotifications   = pendingOpenNotifications,
                    onFlagConsumed      = { pendingOpenNotifications = false },
                    onLogout            = {
                        auth.signOut()
                        startActivity(Intent(this, LandingActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_notifications", false)) {
            pendingOpenNotifications = true
            recreate()
        }
    }

    /* ---------------------------------------------------------------- helpers */
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

    /* ---------------------------------------------------------------- Razorpay */
    /** Exposed to the Composable so it can register callbacks */
    override fun setPaymentCallbacks(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        paymentSuccessCallback = onSuccess
        paymentErrorCallback   = onError
    }

    /* mandatory overrides for the new listener types */
    override fun onPaymentSuccess(
        razorpayPaymentId: String?,
        paymentData: PaymentData?
    ) {
        val id = razorpayPaymentId ?: paymentData?.paymentId
        if (id != null) paymentSuccessCallback?.invoke(id)
        else paymentErrorCallback?.invoke("Success but paymentId == null")
    }

    override fun onPaymentError(
        code: Int,
        description: String?,
        paymentData: PaymentData?
    ) {
        paymentErrorCallback?.invoke("Payment error $code: $description")
    }

    override fun onExternalWalletSelected(
        externalWalletName: String?,
        paymentData: PaymentData?
    ) {
        Toast.makeText(this,
            "Selected wallet: $externalWalletName", Toast.LENGTH_SHORT).show()
    }

    /* feed Razorpay result back to the SDK */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Checkout.handleActivityResult(
            this,              // Activity
            requestCode,
            resultCode,
            data,
            this,              // PaymentResultWithDataListener
            this               // ExternalWalletListener
        )
    }

    override fun onDestroy() {
        authListener?.let { auth.removeAuthStateListener(it) }
        super.onDestroy()
    }
}

/* ────────────────────────────────────────────────────────────────────────── */

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun KupidXApp(
    postViewModel: PostViewModel,
    openNotifications: Boolean,
    onFlagConsumed: () -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()

    LaunchedEffect(openNotifications) {
        if (openNotifications) {
            navController.navigate("notifications")
            onFlagConsumed()
        }
    }

    MainScreen(
        navController = navController,
        onLogout      = onLogout,
        postViewModel = postViewModel
    )
}
