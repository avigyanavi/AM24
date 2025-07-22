@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

/* Android & Compose */
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ServerValue.increment
import java.util.Locale
import androidx.core.content.getSystemService
import com.google.firebase.functions.FirebaseFunctions
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

private val PLUS_FEATURES = listOf(
    "No ads",
    "Unlock People Who Liked Me",
    "Minimum 50 Swipes a day",
    "3 compliments per week",
    "3 boosts per week"
)
private val PREMIUM_FEATURES = listOf(
    "Unlock Picture, Video and Voice posts",
    "Priority Profile in the dating stack",
    "Unlimited Swipes",
    "5 compliments per week",
    "5 boosts per week",
    "Unlocked Performance Metrics per profile",
    "Everything in Plus"
)

/* ────────  PLAN IDS (create these in dashboard → Plans) ──────── */
private const val PLAN_ID_WEEK_PLUS     = "plan_QjnGf6wdQAmyi2"
private const val PLAN_ID_WEEK_PREMIUM  = "plan_QjpkdErsuewaUJ"
private const val PLAN_ID_MONTH_PLUS    = "plan_QjpkKQ5S3ur64Q"
private const val PLAN_ID_MONTH_PREMIUM = "plan_QjplxIqveB0BVS"
private const val PLAN_ID_YEAR_PLUS     = "plan_QjpmNjEkEPlObK"
private const val PLAN_ID_YEAR_PREMIUM  = "plan_QjmpS4xg31rg"

/* ────────  Public key (only key_id!) ──────── */
private const val RZP_KEY_ID = "rzp_live_DsoxJLeiCw940M"

/* ─────────  model for UI  ───────── */
enum class Tier { PLUS, PREMIUM }
private data class Plan(
    val period: Period,
    val tier: Tier,
    val price: Int,          // in rupees
    val planId: String
)

/* all 6 plans */
private val PLANS = listOf(
    Plan(Period.WEEK,  Tier.PLUS,    9,   PLAN_ID_WEEK_PLUS),
    Plan(Period.WEEK,  Tier.PREMIUM, 29,  PLAN_ID_WEEK_PREMIUM),
    Plan(Period.MONTH, Tier.PLUS,    39,  PLAN_ID_MONTH_PLUS),
    Plan(Period.MONTH, Tier.PREMIUM, 99,  PLAN_ID_MONTH_PREMIUM),
    Plan(Period.YEAR,  Tier.PLUS,    399, PLAN_ID_YEAR_PLUS),
    Plan(Period.YEAR,  Tier.PREMIUM, 999, PLAN_ID_YEAR_PREMIUM),
)

private fun planToSlug(plan: Plan): String = when {
    plan.tier == Tier.PLUS    && plan.period == Period.MONTH -> "plus-monthly"
    plan.tier == Tier.PLUS    && plan.period == Period.YEAR  -> "plus-annual"
    plan.tier == Tier.PREMIUM && plan.period == Period.MONTH -> "premium-monthly"
    else                                                       -> "premium-annual"
}

private fun usdPrice(plan: Plan): Double = when {
    plan.tier == Tier.PLUS    && plan.period == Period.MONTH -> 4.99
    plan.tier == Tier.PREMIUM && plan.period == Period.MONTH -> 9.99
    plan.tier == Tier.PLUS    && plan.period == Period.YEAR  -> 49.99
    else                                                       -> 99.99     // premium-annual
}


/* ───────── Subscription screen – new version ───────── */
@Composable
fun SubscriptionScreen(navController: NavController) {
    val scrollState = rememberScrollState()          // ← add

    /* geo-gate exactly like before */
    val ctx = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRoot = FirebaseDatabase.getInstance().getReference("users/$uid")
    var userCountry by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uid) {
        userCountry = userRoot.child("country").get().await().getValue(String::class.java)
    }
    val isIndia = CountryUtil.useRazorpay(ctx, userCountry)

    /* -------------------------------------------------- */
    val db     = FirebaseDatabase.getInstance().reference
    val scope  = rememberCoroutineScope()
    val host        = ctx as? KupidXAppActivity           // Razorpay callbacks
    val paypalHost  = ctx as? PaypalSubscriptionHost
    val co     = remember { Checkout().apply { setKeyID(RZP_KEY_ID) } }
    val fx     = FirebaseFunctions.getInstance("asia-south1")

    /* real-time flags to hide the screen if user already subscribed */
    var plus    by remember { mutableStateOf<Boolean?>(null) }
    var premium by remember { mutableStateOf<Boolean?>(null) }

    DisposableEffect(uid) {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                plus    = s.child("isPlus").getValue(Boolean::class.java)
                premium = s.child("isPremium").getValue(Boolean::class.java)
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        db.child("users/$uid").addValueEventListener(l)
        onDispose { db.child("users/$uid").removeEventListener(l) }
    }

    if (plus == null || premium == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color =             Color(0xFFFF6F00)          // ← Kupidx orange
         )
        }
        return
    }
    /* already subscribed → leave */
    if (plus == true || premium == true) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    /* ---------- Razorpay helpers ---------- */

    suspend fun createSub(plan: Plan): String {
        val data = hashMapOf(
            "uid"     to uid,
            "planId"  to plan.planId       // we pass which of the 6 plans the user picked
        )
        @Suppress("UNCHECKED_CAST")
        val res = fx.getHttpsCallable("createKupidxPlusSub").call(data).await().data as Map<*, *>
        return res["subscriptionId"] as String
    }

    fun launchCheckout(plan: Plan) = scope.launch {
        try {
            val subId = createSub(plan)                 // ① create on backend
            /* ② open native checkout for first charge */
            val opts = JSONObject().apply {
                put("subscription_id", subId)
                put("name",      "Kupidx ${plan.tier.name.lowercase().capitalize()}")
                put("description", "${plan.price} ₹ / ${plan.period.label.lowercase()}")
                put("prefill", JSONObject().apply {        // nice to have
                    put("email", FirebaseAuth.getInstance().currentUser?.email)
                })
            }
            co.open(ctx as Activity, opts)
        } catch (e: Exception) {
            Toast.makeText(ctx, e.message ?: "Something went wrong", Toast.LENGTH_LONG).show()
        }
    }
    fun handlePlan(plan: Plan) {
        if (isIndia) {            // Razorpay checkout like before
            launchCheckout(plan)
        } else {
            // Same UI, but jump to correct PayPal page
            val slug = planToSlug(plan)
            paypalHost?.startPaypalSubscription(slug) { subId ->
                if (subId.isNullOrBlank()) return@startPaypalSubscription
                scope.launch {
                    try {
                        val res = fx.getHttpsCallable("verifyPaypalSubscription")
                            .call(mapOf("subscriptionId" to subId))
                            .await().data as Map<*, *>
                        val ok = res["valid"] as? Boolean ?: false
                        if (ok) {
                            Toast.makeText(ctx, "Subscription activated!", Toast.LENGTH_LONG).show()
                            navController.popBackStack()
                        } else {
                            Toast.makeText(ctx, "Subscription verification failed", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Subscription verification failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /* ---------- attach success / error to the host activity ---------- */
    DisposableEffect(Unit) {
        host?.setPaymentCallbacks(
            onSuccess = { paymentId ->
                /* Optional toast – actual flag flip happens in the Cloud Function
                   `verifyKupidxSub` which your webhook calls immediately. */
                Toast.makeText(ctx, "Subscription activated!", Toast.LENGTH_LONG).show()
                navController.popBackStack()            // dismiss the screen
            },
            onError = { msg ->
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
        )
        onDispose { host?.setPaymentCallbacks({},{}) }
    }

    /* ---------- UI ---------- */

    val availablePeriods = if (isIndia)
        Period.values().toList()                  // WEEK, MONTH, YEAR
    else
        listOf(Period.MONTH, Period.YEAR)

    var currentPeriod by remember { mutableStateOf(availablePeriods.first()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)             // ← add this line
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Text("Upgrade your experience",
            fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(24.dp))

        /* period tabs */
        TabRow(
            selectedTabIndex = availablePeriods.indexOf(currentPeriod),
            containerColor = Color.Transparent,
            contentColor = Color.White
        ) {
            availablePeriods.forEach { p ->
                val selected = p == currentPeriod
                Tab(
                    selected = selected,
                    onClick  = { currentPeriod = p },
                    text     = { Text(p.label,
                        color = if (selected) Color.White else Color.LightGray) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        /* plan cards for the selected period */
        PLANS
            .filter { it.period == currentPeriod }
            .filter { isIndia || it.period != Period.WEEK }   // drop weekly for PayPal
            .forEach { plan ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { handlePlan(plan) },   // ← replace old click
                colors = CardDefaults.cardColors(
                    containerColor = if (plan.tier == Tier.PREMIUM)
                        Color(0xFFFF6F00)          // ← Kupidx orange
                         else Color(0xFF1E1E1E)
                )
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            plan.tier.name.lowercase().replaceFirstChar(Char::uppercase),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        val priceLabel = if (isIndia)
                            "₹${plan.price} / ${plan.period.label.lowercase()}"
                        else
                            "$${usdPrice(plan)} / ${plan.period.label.lowercase()}"

                        Text(priceLabel, color = Color.LightGray, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        val features = if (plan.tier == Tier.PREMIUM) PREMIUM_FEATURES else PLUS_FEATURES
                        features.forEach { bullet ->
                            Text("• $bullet",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                    Button(onClick = { handlePlan(plan) }) {
                        Text("Choose", color = Color.White)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { navController.popBackStack() }) {
            Text("Not now", color =             Color(0xFFFF6F00)          // ← Kupidx orange
            )
        }
    }
}

interface PaypalSubscriptionHost {
    fun startPaypalSubscription(planSlug: String, onResult: (String?) -> Unit)
}

/* ───────── PayPal Smart-Button WebView (unchanged) ───────── */
