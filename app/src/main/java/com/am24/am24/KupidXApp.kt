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
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.am24.am24.ui.purchase.PaymentResultListenerHost
import kotlinx.coroutines.launch
import java.util.Locale



class KupidXAppActivity : AppCompatActivity(),
    PaymentResultWithDataListener,
    ExternalWalletListener,
    PaymentResultListenerHost {

    /* ------------------------------------------------------------------ state */
    private lateinit var auth: FirebaseAuth
    private lateinit var locationManager: LocationManager
    private val postViewModel: PostViewModel by viewModels()

    // callbacks wired from the Composable screen
    private var paymentSuccessCallback: ((String) -> Unit)? = null
    private var paymentErrorCallback:  ((String) -> Unit)? = null



    // deep-link flag
    private var pendingOpenNotifications = false
    private var pendingOpenUpgradeLanding = false

    /* ------------------------------------------------------------------ locale */
    override fun attachBaseContext(newBase: Context) {
        val prefs      = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val defaultLang = if (Locale.getDefault().country.equals("MX", true)) "es" else "en"
        val languageCode = prefs.getString("language", defaultLang) ?: defaultLang
        val updatedCtx = newBase.createConfigurationContext(
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


        pendingOpenNotifications =
            intent?.getBooleanExtra("open_notifications", false) ?: false
        pendingOpenUpgradeLanding =
            intent?.getBooleanExtra("open_upgrade_landing", false) ?: false

        auth.currentUser?.uid?.let { uid ->
            locationManager = LocationManager(this)

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
                        postViewModel          = postViewModel,
                        openNotifications      = pendingOpenNotifications,
                        openUpgradeLanding     = pendingOpenUpgradeLanding,
                        onNotificationsConsumed= { pendingOpenNotifications = false },
                        onUpgradeConsumed      = { pendingOpenUpgradeLanding = false },
                        onLogout               = {
                            auth.signOut()
                            TokenStorageManager.clearToken(this@KupidXAppActivity)
                            startActivity(Intent(this, LandingActivity::class.java))
                            finish()
                        },
                        locationManager        = locationManager
                    )
                }
            }

            lifecycleScope.launch {
                continueInitialization(uid)
            }
        } ?: run {
            startActivity(Intent(this, LandingActivity::class.java))
            finish()
        }
        }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private suspend fun continueInitialization(uid: String) {
        auth.currentUser?.getIdToken(true)
            ?.addOnSuccessListener { res ->
                res.token?.let { TokenStorageManager.saveToken(this@KupidXAppActivity, it) }
            }
        checkLocationPermissions()

        runCatching { PushService.uploadCurrentToken() }.onFailure { it.printStackTrace() }
        runCatching { MobileAds.initialize(this) }.onFailure { it.printStackTrace() }
        runCatching { FirebaseStorage.getInstance("gs://am-twentyfour.com") }.onFailure { it.printStackTrace() }
        runCatching { postViewModel.loadFiltersFromFirebase(uid) }.onFailure { it.printStackTrace() }

        if (hasLocationPermission()) {
            runCatching { locationManager.updateUserLocation(uid) }.onFailure { it.printStackTrace() }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        var changed = false
        if (intent.getBooleanExtra("open_notifications", false)) {
            pendingOpenNotifications = true
            changed = true
        }
        if (intent.getBooleanExtra("open_upgrade_landing", false)) {
            pendingOpenUpgradeLanding = true
            changed = true
        }
        if (changed) recreate()
    }

    /* ---------------------------------------------------------------- helpers */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED
    }

    private fun checkLocationPermissions() {
        if (!hasLocationPermission()) {
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
}

/* ────────────────────────────────────────────────────────────────────────── */

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun KupidXApp(
    postViewModel: PostViewModel,
    openNotifications: Boolean,
    openUpgradeLanding: Boolean,
    onNotificationsConsumed: () -> Unit,
    onUpgradeConsumed: () -> Unit,
    onLogout: () -> Unit,
    locationManager: LocationManager
) {
    val navController = rememberNavController()
    val ctx = LocalContext.current
    val isIndia = CountryUtil.isProbablyInIndia(ctx)

    LaunchedEffect(openNotifications) {
        if (openNotifications) {
            navController.navigate("notifications")
            onNotificationsConsumed()
        }
    }

    LaunchedEffect(openUpgradeLanding) {
        if (openUpgradeLanding) {
            if (isIndia) {
                navController.navigate("upgradeLanding")
            } else {
                navController.navigate("subscription")
            }
            onUpgradeConsumed()
        }
    }

    MainScreen(
        navController = navController,
        onLogout      = onLogout,
        postViewModel = postViewModel,
        locationManager = locationManager
    )
}
