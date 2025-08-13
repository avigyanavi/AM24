@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

/* Android & Compose */
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.functions.FirebaseFunctions
import com.razorpay.Checkout
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import com.am24.am24.ui.purchase.PaymentResultListenerHost

private val PLUS_FEATURES = listOf(
    R.string.feature_no_ads,
    R.string.feature_priority_profile,
    R.string.feature_people_liked_me,
    R.string.feature_picture_voice_posts,
    R.string.feature_50_swipes,
    R.string.feature_3_compliments,
    R.string.feature_3_boosts
)

private val PREMIUM_FEATURES = listOf(
    R.string.feature_video_rank_section,
    R.string.feature_unlimited_swipes,
    R.string.feature_5_compliments,
    R.string.feature_5_boosts,
    R.string.feature_performance_metrics_rank,
    R.string.feature_everything_plus
)
private data class UiState(
    val isProcessing: Boolean = false,
    val selectedPlanId: String? = null
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
    val razorpayId: String
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
    plan.tier == Tier.PLUS    && plan.period == Period.MONTH -> 1.99
    plan.tier == Tier.PREMIUM && plan.period == Period.MONTH -> 4.99
    plan.tier == Tier.PLUS    && plan.period == Period.YEAR  -> 19.99
    else                                                       -> 49.99     // premium-annual
}


/* ───────── Subscription screen – new version ───────── */
@Composable
fun SubscriptionScreen(
    navController: NavController,
    allowIfSubscribed: Boolean = false
) {
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
    val host        = ctx as? PaymentResultListenerHost   // Razorpay callbacks
    val act         = ctx as FragmentActivity
    val co     = remember { Checkout().apply { setKeyID(RZP_KEY_ID) } }
    val fx     = FirebaseFunctions.getInstance("asia-south1")
    var ui by remember { mutableStateOf(UiState()) }
    var pendingSubId by remember { mutableStateOf<String?>(null) }

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
    if ((plus == true || premium == true) && !allowIfSubscribed) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    /* ---------- Razorpay helpers ---------- */

    suspend fun createSub(plan: Plan): String {
        val data = hashMapOf(
            "uid"     to uid,
            "planId"  to plan.razorpayId       // we pass which of the 6 plans the user picked
        )
        @Suppress("UNCHECKED_CAST")
        val res = fx.getHttpsCallable("createKupidxPlusSub").call(data).await().data as Map<*, *>
        return res["subscriptionId"] as String
    }

    fun launchCheckout(plan: Plan) = scope.launch {
        try {
            val subId = createSub(plan)                 // ① create on backend
            pendingSubId = subId
            /* ② open native checkout for first charge */
            val opts = JSONObject().apply {
                put("subscription_id", subId)
                put("name", "AM24")
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
        if (isIndia) { launchCheckout(plan); return }
        val slug = planToSlug(plan)
        navController.navigate("billing?basePlanId=$slug")
    }

    /* ---------- attach success / error to the host activity ---------- */
    DisposableEffect(Unit) {
        host?.setPaymentCallbacks(
            onSuccess = { _ ->
                val sid = pendingSubId
                if (sid == null) {
                    Toast.makeText(ctx, "Subscription activated!", Toast.LENGTH_LONG).show()
                    pendingSubId = null
                    ui = UiState()
                    navController.navigate("settings") {
                        popUpTo("subscription") { inclusive = true }
                    }
                    return@setPaymentCallbacks
                }
                scope.launch {
                    try {
                        fx.getHttpsCallable("verifyKupidxSubscription")
                            .call(mapOf("subscriptionId" to sid))
                            .await()
                        Toast.makeText(ctx, "Subscription activated!", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Verification failed", Toast.LENGTH_LONG).show()
                    } finally {
                        pendingSubId = null
                        ui = UiState()
                        navController.navigate("settings") {
                            popUpTo("subscription") { inclusive = true }
                        }
                    }
                }
            },
            onError = { msg ->
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                pendingSubId = null
                ui = UiState()
                navController.navigate("settings") {
                    popUpTo("subscription") { inclusive = true }
                }
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

    /* Reset the selected period if isIndia toggles and the period is no longer available */
    LaunchedEffect(isIndia) {
        if (currentPeriod !in availablePeriods) {
            currentPeriod = availablePeriods.first()
        }
    }

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

        val selectedTabIndex = availablePeriods.indexOf(currentPeriod).coerceAtLeast(0)
        /* period tabs */
        TabRow(
            selectedTabIndex = selectedTabIndex,
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
                        .clickable {
                            if (!ui.isProcessing) handlePlan(plan)
                        },
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
                            val features =
                                if (plan.tier == Tier.PREMIUM) PREMIUM_FEATURES else PLUS_FEATURES
                            features.forEach { bulletResId ->
                                Text(
                                    text = "• ${stringResource(bulletResId)}",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                        val processing = ui.isProcessing &&
                                ui.selectedPlanId == if (isIndia) plan.razorpayId else planToSlug(plan)
                        Button(
                            onClick = { if (!ui.isProcessing) handlePlan(plan) },
                            enabled = !ui.isProcessing
                        ) {
                            if (processing)
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            else
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