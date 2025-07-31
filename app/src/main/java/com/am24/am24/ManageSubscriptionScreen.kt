package com.am24.am24

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.am24.am24.ui.TierCard
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.razorpay.Checkout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

private val PLUS_FEATURES = listOf(
    "No ads",
    "Priority Profile in the dating stack",
    "Unlock People Who Liked Me and Change Location Option",
    "Unlock Picture and Voice posts",
    "3 compliments per week",
    "3 boosts per week"
)

private val PREMIUM_FEATURES = listOf(
    "Unlock Video posts and Rank section",
    "Unlimited Swipes",
    "5 compliments per week",
    "5 boosts per week",
    "Unlocked Performance Metrics per profile",
    "Everything in Plus"
)
/* deep link each major UPI app to its AutoPay list */
private const val GPAY_UPI_AUTOPAY   = "https://gpay.app.goo.gl/autopay"          // opens Google Pay → Autopay tab
private const val PHONEPE_AUTOPAY    = "phonepe://upi/manageMandate"              // opens PhonePe mandates list
private const val PAYTM_AUTOPAY      = "paytmmp://upi/mandate?screen=manage"      // opens Paytm → AutoPay
private val UPI_DEEP_LINKS           = arrayOf(GPAY_UPI_AUTOPAY,
    PHONEPE_AUTOPAY,
    PAYTM_AUTOPAY)

private const val CANCEL_URL =
    "https://dashboard.razorpay.com/app/subscriptions"   // ← change if you have a bespoke deeplink
// … PLUS_FEATURES & PREMIUM_FEATURES remain unchanged …

private const val PAYPAL_MANAGE_URL  = "https://www.paypal.com/myaccount/autopay"

private fun cancelKupidxPlusSub(
    fx: FirebaseFunctions,
    scope: CoroutineScope,
    ctx: Context,
    nav: NavController
) {
    scope.launch {
        try {
            fx.getHttpsCallable("cancelKupidxPlusSub").call().await()
            Toast.makeText(ctx, "Subscription cancelled", Toast.LENGTH_LONG).show()
            nav.popBackStack()
        } catch (e: Exception) {
            /* ► Razorpay could not cancel –  show reason & open UPI AutoPay */
            val msg = (e as? FirebaseFunctionsException)?.message
                ?: e.localizedMessage ?: "Unable to cancel via server"
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

            /* attempt to open the first UPI app that’s installed */
            val found = UPI_DEEP_LINKS.firstOrNull { link ->
                ctx.packageManager.resolveActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(link)), 0
                ) != null
            }
            if (found != null) {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(found)))
            } else {
                /* fallback to plain dashboard */
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://dashboard.razorpay.com/app/subscriptions"))
                )
            }
        }
    }
}

/* ───── unified click handler ───── */
private fun handleCancelClick(
    isIndia: Boolean,
    fx: FirebaseFunctions,
    scope: CoroutineScope,
    ctx: Context,
    nav: NavController
) {
    if (isIndia) {
        cancelKupidxPlusSub(fx, scope, ctx, nav)                           // Razorpay  :contentReference[oaicite:0]{index=0}
    } else {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PAYPAL_MANAGE_URL)))   // PayPal  :contentReference[oaicite:1]{index=1}
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSubscriptionScreen(navController: NavController) {

    /* ───── helpers & DI ───── */
    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()
    val fx    = FirebaseFunctions.getInstance("asia-south1")

    val uid   = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRef = FirebaseRefs.db.getReference("users/$uid")

    val host  = ctx as? KupidXAppActivity
    val co    = remember { Checkout().apply { setKeyID(RZP_KEY_ID_PUBLIC) } }

    /* ───── state ───── */
    var premiumTier     by remember { mutableStateOf("Loading…") }
    var expiry          by remember { mutableStateOf("Loading…") }
    var subscriptionId  by remember { mutableStateOf<String?>(null) }
    var isPlus          by remember { mutableStateOf(false) }
    var isPremium       by remember { mutableStateOf(false) }
    var userCountry     by remember { mutableStateOf<String?>(null) }
    var subscriptionStatus by remember { mutableStateOf<String?>(null) }

    /* ───── one-shot fetch ───── */
    LaunchedEffect(uid) {
        val snap = userRef.get().await()
        isPlus       = snap.child("isPlus").getValue(Boolean::class.java)  ?: false
        isPremium    = snap.child("isPremium").getValue(Boolean::class.java) ?: false
        premiumTier  = when {
            isPremium -> "Premium"
            isPlus    -> "Plus"
            else      -> "Free"
        }
        expiry = if (!subscriptionId.isNullOrBlank()) "Never" else
            snap.child("nextRenewal").getValue(Long::class.java)
                ?.let { DateFormat.getDateInstance().format(Date(it)) } ?: "N/A"
        subscriptionId   = snap.child("subscription").child("id").getValue(String::class.java)
        userCountry      = snap.child("country").getValue(String::class.java)
        subscriptionStatus = snap.child("subscriptionStatus").getValue(String::class.java)
    }

    val isIndia = CountryUtil.useRazorpay(ctx, userCountry)
    val scroll  = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Manage Plan") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    /* always-visible cancel */
                    TextButton(
                        onClick = {
                            handleCancelClick(isIndia, fx, scope, ctx, navController)
                        }
                    ) {
                        Text("Cancel", color = KupidxOrange, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            /* ───── status ───── */
            Text("Membership: $premiumTier", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Expires on: $expiry",fontSize = 16.sp)
            val statusText = when (subscriptionStatus) {
                "active" -> stringResource(R.string.subscription_active)
                "inactive" -> stringResource(R.string.subscription_inactive)
                "completed" -> stringResource(R.string.subscription_completed)
                "cancelled" -> stringResource(R.string.subscription_cancelled)
                "suspended" -> stringResource(R.string.subscription_suspended)
                "expired" -> stringResource(R.string.subscription_expired)
                null -> "N/A"
                else -> subscriptionStatus ?: "N/A"
            }
            if (subscriptionStatus != null) {
                val reason = if (subscriptionStatus == "inactive")
                    " \u2013 " + stringResource(R.string.payment_failed) else ""
                Text("Status: $statusText$reason", fontSize = 16.sp)
            }

            /* ───── benefits ───── */
            val featureList = when {
                isPremium -> PREMIUM_FEATURES
                isPlus    -> PLUS_FEATURES
                else      -> emptyList()
            }
            if (featureList.isNotEmpty()) {
                Text("Your Benefits:", fontWeight = FontWeight.SemiBold)
                featureList.forEach { bullet ->
                    Text("• $bullet",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            /* ───── upsell to Premium ───── */
            if (isPlus) {
                TierCard(
                    tier       = Tier.PREMIUM,
                    colour     = Color(0xFFFF6F00),
                    priceWeekly= 29,
                    priceMonth = 99,
                    priceYear  = 999,
                    onAuto     = {
                        navController.navigate("subscription?allowIfSubscribed=true")
                    },
                    onManual   = { p ->
                        launchOneTimeUpi(scope, ctx, host, fx, co, navController, uid, p)
                    },
                    showManual = isIndia
                )
            }

            /* ───── actions ───── */
            when {
                /* change plan for one-time buyers */
                subscriptionId.isNullOrBlank() && (isPlus || isPremium) -> {
                    OutlinedButton(
                        onClick  = { navController.navigate("upgradeLanding") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change Plan", color = Color(0xFFFF6600))
                    }
                }

                /* active recurring subscription → show extra cancel */
                !subscriptionId.isNullOrBlank() -> {
                    Button(
                        onClick = {
                            handleCancelClick(isIndia, fx, scope, ctx, navController)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6600))
                    ) { Text("Cancel Subscription") }
                }
            }
        }
    }
}
// ──────────────────────────────────────────────────────────────────────────────
private fun launchOneTimeUpi(
    scope: CoroutineScope,
    ctx: Context,
    host: KupidXAppActivity?,
    fx: FirebaseFunctions,
    co: Checkout,
    nav: NavController,
    uid: String,
    period: Period,
    tier: Tier = Tier.PREMIUM           // default because we only upsell Plus → Premium
) {
    scope.launch {

        /* ① price table */
        val price = when (tier to period) {
            Tier.PLUS    to Period.WEEK  -> 9
            Tier.PLUS    to Period.MONTH -> 39
            Tier.PLUS    to Period.YEAR  -> 399
            Tier.PREMIUM to Period.WEEK  -> 29
            Tier.PREMIUM to Period.MONTH -> 99
            else                         -> 999      // Premium / Year
        }

        try {
            /* ② create Razorpay *order* via Cloud Function */
            @Suppress("UNCHECKED_CAST")
            val res = fx.getHttpsCallable("createManualSubscriptionOrder")
                .call(mapOf(
                    "amount" to price,
                    "label"  to "${tier.name}_${period.name.lowercase()}"
                ))
                .await()
                .data as Map<*, *>

            val orderId = res["id"]  as String
            val keyId   = res["key"] as String

            /* ③ open native checkout */
            co.open(ctx as Activity, JSONObject().apply {
                put("key",      keyId)
                put("order_id", orderId)
                put("name", "AM24")
            })

            /* ④ credit perks when the Activity fires the callback */
            host?.setPaymentCallbacks(
                onSuccess = {
                    val validityMs = when (period) {
                        Period.WEEK  -> 7L   * 24 * 60 * 60 * 1000
                        Period.MONTH -> 30L  * 24 * 60 * 60 * 1000
                        Period.YEAR  -> 365L * 24 * 60 * 60 * 1000
                    }
                    val now = System.currentTimeMillis()

                    val updates = mutableMapOf<String, Any>(
                        "isPlus"      to (tier == Tier.PLUS),
                        "isPremium"   to (tier == Tier.PREMIUM),
                        "nextRenewal" to now + validityMs,
                        "availableBoosts"       to if (tier == Tier.PREMIUM) 5 else 3,
                        "availableCompliments"  to if (tier == Tier.PREMIUM) 5 else 3,
                        "swipesInfo/remainingSwipes" to if (tier == Tier.PREMIUM) Int.MAX_VALUE else 50
                    ).apply {
                        if (tier == Tier.PREMIUM) put("availableAiMessages", 2)
                    }

                    FirebaseRefs.db.getReference("users/$uid").updateChildren(updates)
                    Toast.makeText(ctx, "Thanks! Enjoy your perks.", Toast.LENGTH_LONG).show()

                    nav.navigate("settings") {
                        popUpTo("upgradeLanding") { inclusive = true }
                    }
                },
                onError = { msg ->
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    nav.navigate("settings") {
                        popUpTo("upgradeLanding") { inclusive = true }
                    }
                }
            )

        } catch (e: Exception) {
            Toast.makeText(ctx, e.message ?: "Something went wrong", Toast.LENGTH_LONG).show()
        }
    }
}

