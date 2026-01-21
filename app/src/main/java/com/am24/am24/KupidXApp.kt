package com.am24.am24

import DatingViewModel
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.am24.am24.ui.purchase.PaymentResultListenerHost
import com.am24.am24.ui.theme.AppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.razorpay.Checkout                       // Razorpay
import com.razorpay.ExternalWalletListener        // Razorpay
import com.razorpay.PaymentData                   // Razorpay
import com.razorpay.PaymentResultWithDataListener // Razorpay
import kotlinx.coroutines.launch
import java.util.Locale
import com.google.firebase.database.ServerValue
import com.am24.am24.ads.InterstitialAdManager
class KupidXAppActivity : AppCompatActivity(),
    PaymentResultWithDataListener,
    ExternalWalletListener,
    PaymentResultListenerHost {

    /* ------------------------------------------------------------------ state */
    private lateinit var auth: FirebaseAuth
    private lateinit var locationManager: LocationManager

    // EXISTING VM
    private val postViewModel: PostViewModel by viewModels()

    // NEW: Lift other feature VMs to Activity scope
    private val profileViewModel: ProfileViewModel by viewModels()
    private val nearbyViewModel: NearbyViewModel by viewModels()
    private val datingViewModel: DatingViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()

    private var presenceRef: DatabaseReference? = null

    // callbacks wired from the Composable screen (Razorpay)
    private var paymentSuccessCallback: ((String) -> Unit)? = null
    private var paymentErrorCallback: ((String) -> Unit)? = null
    private val aiPartnerViewModel: AIPartnerViewModel by viewModels()
    // deep-link flag
    private var pendingOpenNotifications = false
    private var pendingOpenUpgradeLanding = false
    private val interstitialAdManager by lazy { InterstitialAdManager(this) }
    private fun setupPresence(uid: String) {
        val ref = FirebaseRefs.db.getReference("presence").child(uid)
        presenceRef = ref
        ref.setValue(true)
        ref.onDisconnect().removeValue()


        // Also track when the client disconnects so lastActive reflects the offline moment
        FirebaseRefs.db.getReference("users").child(uid).child("lastActive")
            .onDisconnect()
            .setValue(ServerValue.TIMESTAMP)
    }

    /* ------------------------------------------------------------------ locale */
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val defaultLang = defaultLanguageCode()
        val languageCode = prefs.getString("language", defaultLang) ?: defaultLang
        val updatedCtx = newBase.createConfigurationContext(
            newBase.resources.configuration.apply { setLocale(Locale(languageCode)) }
        )
        super.attachBaseContext(updatedCtx)
    }

    /* ---------------------------------------------------------------- permissions */
    private val requestLocationPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val fine = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
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
            val currentUserId = uid
            locationManager = LocationManager(this)

            setContent {
                AppTheme {
                    KupidXApp(
                        currentUserId   = currentUserId,    // NEW
                        postViewModel   = postViewModel,
                        profileViewModel = profileViewModel,
                        nearbyViewModel  = nearbyViewModel,
                        datingViewModel  = datingViewModel,
                        mainViewModel    = mainViewModel,
                        chatViewModel    = chatViewModel,
                        aiPartnerViewModel = aiPartnerViewModel,
                        openNotifications = pendingOpenNotifications,
                        openUpgradeLanding = pendingOpenUpgradeLanding,
                        onNotificationsConsumed = { pendingOpenNotifications = false },
                        onUpgradeConsumed = { pendingOpenUpgradeLanding = false },
                        onLogout = {
                            PushService.updateLastActive()
                            presenceRef?.removeValue()
                            auth.signOut()
                            TokenStorageManager.clearToken(this@KupidXAppActivity)
                            startActivity(Intent(this, LandingActivity::class.java))
                            finish()
                        },
                        locationManager = locationManager
                    )
                }
            }

            lifecycleScope.launch {
                continueInitialization(uid)
            }
            interstitialAdManager.preload(resolveInterstitialAdUnitId())
        } ?: run {
            startActivity(Intent(this, LandingActivity::class.java))
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private suspend fun continueInitialization(uid: String) {
        setupPresence(uid)
        auth.currentUser?.getIdToken(true)
            ?.addOnSuccessListener { res ->
                res.token?.let { TokenStorageManager.saveToken(this@KupidXAppActivity, it) }
            }
        checkLocationPermissions()

        runCatching { PushService.uploadCurrentToken() }.onFailure { it.printStackTrace() }
        runCatching { FirebaseStorage.getInstance("gs://am-twentyfour.appspot.com") }
            .onFailure { it.printStackTrace() }
        runCatching { postViewModel.loadFiltersFromFirebase(uid) }.onFailure { it.printStackTrace() }

        if (hasLocationPermission()) {
            runCatching { locationManager.updateUserLocation(uid) }.onFailure { it.printStackTrace() }
        }

        // 🔸 Server-side reconciliation each login (fire-and-forget; UI doesn't wait)
        try {
            FirebaseFunctions.getInstance("asia-south1")
                .getHttpsCallable("loginEntitlementSweep")
                .call(hashMapOf<String, Any>())
        } catch (_: Exception) {
            // ignore – non-critical
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

    override fun onDestroy() {
        PushService.updateLastActive()
        presenceRef?.removeValue()
        super.onDestroy()
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

    fun showDailyInterstitial(onDismissed: () -> Unit, onFailed: (String?) -> Unit) {
        interstitialAdManager.show(
            activity = this,
            adUnitId = resolveInterstitialAdUnitId(),
            onDismissed = onDismissed,
            onFailed = onFailed
        )
    }

    fun preloadDailyInterstitial() {
        interstitialAdManager.preload(resolveInterstitialAdUnitId())
    }

    private fun resolveInterstitialAdUnitId(): String {
        return if (CountryUtil.isUnitedStates(this, null)) {
            getString(R.string.admob_interstitial_main)
        } else {
            getString(R.string.admob_interstitial_india)
        }
    }

    /* ---------------------------------------------------------------- Razorpay */
    /** Exposed to the Composable so it can register callbacks */
    override fun setPaymentCallbacks(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        paymentSuccessCallback = onSuccess
        paymentErrorCallback = onError
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
        Toast.makeText(
            this,
            "Selected wallet: $externalWalletName",
            Toast.LENGTH_SHORT
        ).show()
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
    currentUserId: String,              // NEW
    postViewModel: PostViewModel,
    profileViewModel: ProfileViewModel,
    nearbyViewModel: NearbyViewModel,
    datingViewModel: DatingViewModel,
    mainViewModel: MainViewModel,
    chatViewModel: ChatViewModel,       // NEW
    aiPartnerViewModel: AIPartnerViewModel,
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
        navController   = navController,
        currentUserId   = currentUserId,     // NEW
        onLogout        = onLogout,
        postViewModel   = postViewModel,
        profileViewModel = profileViewModel,
        nearbyViewModel  = nearbyViewModel,
        datingViewModel  = datingViewModel,
        mainViewModel    = mainViewModel,
        chatViewModel    = chatViewModel,    // NEW
        aiPartnerViewModel = aiPartnerViewModel,
        locationManager  = locationManager
    )
}
