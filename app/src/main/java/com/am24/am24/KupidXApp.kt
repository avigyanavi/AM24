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
import androidx.compose.ui.platform.LocalContext
import com.paypal.android.corepayments.CoreConfig
import com.paypal.android.corepayments.Environment
import com.paypal.android.paypalwebpayments.PayPalWebCheckoutClient
import java.util.Locale
import com.paypal.android.paypalwebpayments.PayPalPresentAuthChallengeResult
import com.paypal.android.paypalwebpayments.PayPalWebCheckoutFinishVaultResult
import com.paypal.android.paypalwebpayments.PayPalWebCheckoutFundingSource as FundingSource
import com.paypal.android.paypalwebpayments.PayPalWebCheckoutRequest
import com.paypal.android.paypalwebpayments.PayPalWebCheckoutFinishStartResult
import com.am24.am24.ui.purchase.PaypalCheckoutHost
import com.paypal.android.paypalwebpayments.PayPalWebVaultRequest


private const val PAYPAL_CLIENT_ID =
    "AY6qu9OjnVJXXXwsqSkqpNuM1tNibNF8bh7Z2xvEpUZQSxCEZWSOkRdv50mp5DqeBItRRe0GLS9VpBIt"

class KupidXAppActivity : ComponentActivity(),
    PaymentResultWithDataListener,          // replaces old PaymentResultListener
    ExternalWalletListener,                 // required because we pass wallet listener
    PaymentResultListenerHost,              // used by OneTimePurchaseScreen
    PaypalSubscriptionHost,
    PaypalCheckoutHost {

    /* ------------------------------------------------------------------ state */
    private lateinit var auth: FirebaseAuth
    private lateinit var locationManager: LocationManager
    private val postViewModel: PostViewModel by viewModels()
    private lateinit var paypalClient: PayPalWebCheckoutClient

    // callbacks wired from the Composable screen
    private var paymentSuccessCallback: ((String) -> Unit)? = null
    private var paymentErrorCallback:  ((String) -> Unit)? = null
    private var paypalAuthState: String? = null
    private var paypalCallback: ((String?) -> Unit)? = null
    private var checkoutAuthState: String? = null
    private var checkoutCallback: ((String?) -> Unit)? = null


    // deep-link flag
    private var pendingOpenNotifications = false
    private var pendingOpenUpgradeLanding = false

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

        val paypalEnvironment =
            if (BuildConfig.DEBUG) Environment.SANDBOX else Environment.LIVE
        val paypalConfig = CoreConfig(PAYPAL_CLIENT_ID, paypalEnvironment)
        paypalClient = PayPalWebCheckoutClient(applicationContext, paypalConfig, packageName)

        // Splash UI
        setContent {
            AppTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Color(0xFFFF6F00)) }
            }
        }

        pendingOpenNotifications =
            intent?.getBooleanExtra("open_notifications", false) ?: false
        pendingOpenUpgradeLanding =
            intent?.getBooleanExtra("open_upgrade_landing", false) ?: false

        // 🔸 Start the real setup right away — no extra listener needed
        auth.currentUser?.uid?.let { continueInitialization(it) }
            ?: run {              // should never happen, but stay safe
                startActivity(Intent(this, LandingActivity::class.java))
                finish()
            }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun continueInitialization(uid: String) {
        auth.currentUser?.getIdToken(true)
            ?.addOnSuccessListener { res ->
                res.token?.let { TokenStorageManager.saveToken(this@KupidXAppActivity, it) }
            }
        PushService.uploadCurrentToken()          // <-- add this line
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
                    }
                )
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        paypalAuthState?.let { state ->
            when (val res = paypalClient.finishVault(intent, state)) {
                is PayPalWebCheckoutFinishVaultResult.Success ->
                    paypalCallback?.invoke(res.approvalSessionId)
                else -> paypalCallback?.invoke(null)
            }
            paypalAuthState = null
            paypalCallback = null
        }
        checkoutAuthState?.let { state ->
            when (val res = paypalClient.finishStart(intent, state)) {
                is PayPalWebCheckoutFinishStartResult.Success ->
                    checkoutCallback?.invoke(res.orderId)
                else -> checkoutCallback?.invoke(null)
            }
            checkoutAuthState = null
            checkoutCallback = null
        }
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

    override fun startPaypalSubscription(planSlug: String, onResult: (String?) -> Unit) {
        paypalCallback = onResult
        val startResult = paypalClient.vault(this, PayPalWebVaultRequest(planSlug))
        if (startResult is PayPalPresentAuthChallengeResult.Success) {
            paypalAuthState = startResult.authState
        } else {
            paypalCallback?.invoke(null)
            paypalCallback = null
            paypalAuthState = null
        }
    }

    override fun startPaypalCheckout(orderId: String, onResult: (String?) -> Unit) {
        checkoutCallback = onResult
        val startResult = paypalClient.start(
            this,
            PayPalWebCheckoutRequest(orderId, FundingSource.PAYPAL)
        )
        if (startResult is PayPalPresentAuthChallengeResult.Success) {
            checkoutAuthState = startResult.authState
        } else {
            checkoutCallback?.invoke(null)
            checkoutCallback = null
            checkoutAuthState = null
        }
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
    onLogout: () -> Unit
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
        postViewModel = postViewModel
    )
}
