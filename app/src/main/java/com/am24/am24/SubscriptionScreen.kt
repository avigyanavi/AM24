@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

/* Android & Compose */
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.isSystemInDarkTheme
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import com.am24.am24.billing.BillingManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.functions.FirebaseFunctions
import com.razorpay.Checkout
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import com.am24.am24.ui.purchase.PaymentResultListenerHost
import java.util.Locale
import com.am24.am24.AccountDeletion
import com.am24.am24.LandingActivity
import com.am24.am24.TokenStorageManager

private val PLUS_FEATURES = listOf(
    R.string.feature_no_ads,
    R.string.feature_people_liked_me,
    R.string.feature_picture_voice_posts,
    R.string.feature_50_swipes,
    R.string.feature_3_compliments,
)

private val PREMIUM_FEATURES = listOf(
    R.string.feature_video_rank_section,
    R.string.feature_unlock_maps,
    R.string.feature_unlimited_swipes,
    R.string.feature_5_compliments,
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
private data class OneTimeOffer(
    val sku: String,
    val period: Period,
    val tier: Tier,
    val usdPrice: Double,
    val mxnPrice: Double,
    val inrPrice: Int,
    val thbPrice: Int,
    )

/* all 6 plans */
private val PLANS = listOf(
    Plan(Period.WEEK,  Tier.PLUS,    9,   PLAN_ID_WEEK_PLUS),
    Plan(Period.WEEK,  Tier.PREMIUM, 29,  PLAN_ID_WEEK_PREMIUM),
    Plan(Period.MONTH, Tier.PLUS,    29,  PLAN_ID_MONTH_PLUS),
    Plan(Period.MONTH, Tier.PREMIUM, 99,  PLAN_ID_MONTH_PREMIUM),
    Plan(Period.YEAR,  Tier.PLUS,    299, PLAN_ID_YEAR_PLUS),
    Plan(Period.YEAR,  Tier.PREMIUM, 999, PLAN_ID_YEAR_PREMIUM),
)

private val ONE_TIME_OFFERS = listOf(
    OneTimeOffer(
        sku = "kupidx_plus_one_month",
        period = Period.MONTH,
        tier = Tier.PLUS,
        usdPrice = 0.49,
        mxnPrice = 6.99,
        inrPrice = 29,
        thbPrice = 9,
    ),
    OneTimeOffer(
        sku = "kupidx_plus_one_year",
        period = Period.YEAR,
        tier = Tier.PLUS,
        usdPrice = 3.49,
        mxnPrice = 69.00,
        inrPrice = 299,
        thbPrice = 99,
    ),
    OneTimeOffer(
        sku = "kupidx_premium_one_month",
        period = Period.MONTH,
        tier = Tier.PREMIUM,
        usdPrice = 0.99,
        mxnPrice = 24.00,
        inrPrice = 99,
        thbPrice = 39,
    ),
    OneTimeOffer(
        sku = "kupidx_premium_one_year",
        period = Period.YEAR,
        tier = Tier.PREMIUM,
        usdPrice = 10.99,
        mxnPrice = 239.00,
        inrPrice = 999,
        thbPrice = 449,
    ),
)

private fun OneTimeOffer.displayPrice(
    context: Context,
    isIndia: Boolean,
    isMexico: Boolean,
    isThailand: Boolean
): String = when {
    isIndia -> context.getString(R.string.subscription_one_time_price_inr, inrPrice)
    isMexico -> context.getString(
        R.string.subscription_one_time_price_mxn,
        formatPrice(mxnPrice)
    )
    isThailand -> context.getString(
        R.string.subscription_one_time_price_thb,
        thbPrice
    )
    else -> context.getString(
        R.string.subscription_one_time_price_usd,
        formatPrice(usdPrice)
    )
}

private fun periodLabelRes(period: Period): Int = when (period) {
    Period.WEEK -> R.string.subscription_period_weekly
    Period.MONTH -> R.string.subscription_period_monthly
    Period.YEAR -> R.string.subscription_period_yearly
}

private fun periodDurationRes(period: Period): Int = when (period) {
    Period.WEEK -> R.string.subscription_duration_week
    Period.MONTH -> R.string.subscription_duration_month
    Period.YEAR -> R.string.subscription_duration_year
}

private fun tierLabelRes(tier: Tier): Int = when (tier) {
    Tier.PLUS -> R.string.subscription_tier_plus
    Tier.PREMIUM -> R.string.subscription_tier_premium
}

private fun formatPrice(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun planToSlug(plan: Plan): String = when {
    plan.tier == Tier.PLUS    && plan.period == Period.MONTH -> "plus-monthly"
    plan.tier == Tier.PLUS    && plan.period == Period.YEAR  -> "plus-annual"
    plan.tier == Tier.PREMIUM && plan.period == Period.MONTH -> "premium-monthly"
    else                                                       -> "premium-annual"
}

private fun usdPrice(plan: Plan): Double = when {
    plan.tier == Tier.PLUS    && plan.period == Period.MONTH -> 0.99
    plan.tier == Tier.PREMIUM && plan.period == Period.MONTH -> 1.99
    plan.tier == Tier.PLUS    && plan.period == Period.YEAR  -> 9.99
    else                                                       -> 19.99     // premium-annual
}

private fun mxnPrice(plan: Plan): Double = when {
    plan.tier == Tier.PLUS    && plan.period == Period.MONTH -> 4.99
    plan.tier == Tier.PREMIUM && plan.period == Period.MONTH -> 29.00
    plan.tier == Tier.PLUS    && plan.period == Period.YEAR  -> 49.00
    else                                                       -> 199.00
}

private fun thbPrice(plan: Plan): Int = when {
    plan.tier == Tier.PLUS    && plan.period == Period.MONTH -> 9
    plan.tier == Tier.PLUS    && plan.period == Period.YEAR  -> 99
    plan.tier == Tier.PREMIUM && plan.period == Period.MONTH -> 39
    plan.tier == Tier.PREMIUM && plan.period == Period.YEAR  -> 449
    else                                                       -> 0
}


/* ───────── Subscription screen – new version ───────── */
@Composable
fun SubscriptionScreen(
    navController: NavController,
    allowIfSubscribed: Boolean = false,
    toastMessage: String? = null,
    forceSubscription: Boolean = false,
) {
    val scrollState = rememberScrollState()          // ← add

    /* geo-gate exactly like before */
    val ctx = LocalContext.current
    val locale = Locale.getDefault()

    BackHandler(enabled = forceSubscription) {}
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            val msgRes = when (it) {
                "plus" -> R.string.upgrade_to_plus_to_unlock
                "premium" -> R.string.upgrade_to_premium_to_unlock
                else -> null
            }
            msgRes?.let { id ->
                Toast.makeText(ctx, ctx.getString(id), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRoot = FirebaseDatabase.getInstance().getReference("users/$uid")
    var userCountry by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uid) {
        userCountry = userRoot.child("country").get().await().getValue(String::class.java)
    }
    val useRazorpay = CountryUtil.useRazorpay(ctx, userCountry)
    val isIndiaUser = CountryUtil.isIndia(ctx, userCountry)
    val isMexico = CountryUtil.isMexico(ctx, userCountry)
    val isThailand = CountryUtil.isThailand(ctx, userCountry)

    /* -------------------------------------------------- */
    val db     = FirebaseDatabase.getInstance().reference
    val scope  = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val host        = ctx as? PaymentResultListenerHost   // Razorpay callbacks
    val act         = ctx as FragmentActivity
    val co     = remember { Checkout().apply { setKeyID(RZP_KEY_ID) } }
    val fx     = FirebaseFunctions.getInstance("asia-south1")
    var ui by remember { mutableStateOf(UiState()) }
    var pendingSubId by remember { mutableStateOf<String?>(null) }
    val subs by BillingManager.subsProducts.collectAsState()
    val products by BillingManager.products.collectAsState()
    val productDetailsById = remember(products) { products.associateBy { it.productId } }

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
                put("name", "Kupidx")
                put("description", "${plan.price} ₹ / ${plan.period.label.lowercase()}")
                put("prefill", JSONObject().apply {        // nice to have
                    put("email", FirebaseAuth.getInstance().currentUser?.email)
                })
            }
            co.open(ctx as Activity, opts)
        } catch (e: Exception) {
            Toast.makeText(
                ctx,
                e.message ?: ctx.getString(R.string.toast_unknown_error),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    fun handlePlan(plan: Plan) {
        val slug = planToSlug(plan)
        val selectedId = if (useRazorpay) plan.razorpayId else slug
        ui = UiState(isProcessing = true, selectedPlanId = selectedId)
        if (useRazorpay) {
            launchCheckout(plan)
            return
        }
        val productId = if (slug.startsWith("premium")) "premium" else "plus"
        val pd = subs.firstOrNull { it.productId == productId }
        if (pd != null) {
            BillingManager.launchSubsFlow(act, pd, basePlanId = slug, obfuscatedAccountId = uid)
        } else {
            ui = UiState()
        }
    }

    fun handleOneTime(offer: OneTimeOffer) {
        val pd = productDetailsById[offer.sku]
        if (pd != null) {
            ui = UiState(isProcessing = true, selectedPlanId = offer.sku)
            BillingManager.launchBillingFlow(act, pd, obfuscatedAccountId = uid)
        } else {
            Toast.makeText(
                ctx,
                ctx.getString(R.string.subscription_product_unavailable),
                Toast.LENGTH_LONG
            ).show()
            ui = UiState()
        }
    }

    /* ---------- attach success / error to the host activity ---------- */
    DisposableEffect(Unit) {
        host?.setPaymentCallbacks(
            onSuccess = { _ ->
                val sid = pendingSubId
                if (sid == null) {
                    Toast.makeText(
                        ctx,
                        ctx.getString(R.string.subscription_activated),
                        Toast.LENGTH_LONG
                    ).show()
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
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.subscription_activated),
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.subscription_verification_failed),
                            Toast.LENGTH_LONG
                        ).show()
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

    LaunchedEffect(Unit) {
        BillingManager.purchaseFlowFinished.collect {
            pendingSubId = null
            ui = UiState()
            navController.navigate("settings") {
                popUpTo("subscription") { inclusive = true }
            }
        }
    }

    /* ---------- UI ---------- */

    val availablePeriods = if (useRazorpay)
    Period.values().toList()                  // WEEK, MONTH, YEAR
    else
        listOf(Period.MONTH, Period.YEAR)

    var currentPeriod by remember { mutableStateOf(availablePeriods.first()) }

    /* Reset the selected period if isIndia toggles and the period is no longer available */
    /* Reset the selected period if the India flag toggles and the period is no longer available */
    LaunchedEffect(useRazorpay) {
    if (currentPeriod !in availablePeriods) {
            currentPeriod = availablePeriods.first()
        }
    }

    val kupidxOrange = Color(0xFFFF6F00)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)             // ← add this line
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.subscription_upgrade_title),
            fontSize = 24.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

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
                val periodLabel = stringResource(periodLabelRes(p))
                Tab(
                    selected = selected,
                    onClick  = { currentPeriod = p },
                    text     = { Text(periodLabel,
                        color = if (selected) Color.White else Color.LightGray) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        /* plan cards for the selected period */
        PLANS
            .filter { it.period == currentPeriod }
            .filter { useRazorpay || it.period != Period.WEEK }   // drop weekly for PayPal
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
                                stringResource(tierLabelRes(plan.tier)),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            val periodLabelLower = stringResource(periodLabelRes(plan.period)).lowercase(locale)
                            val priceLabel = when {
                                isIndiaUser -> stringResource(
                                    R.string.subscription_price_label_inr,
                                    plan.price,
                                    periodLabelLower
                                )
                                isMexico -> stringResource(
                                    R.string.subscription_price_label_mxn,
                                    formatPrice(mxnPrice(plan)),
                                    periodLabelLower
                                )
                                isThailand -> stringResource(
                                    R.string.subscription_price_label_thb,
                                    thbPrice(plan),
                                    periodLabelLower
                                )
                                else -> stringResource(
                                    R.string.subscription_price_label_usd,
                                    formatPrice(usdPrice(plan)),
                                    periodLabelLower
                                )
                            }
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
                                ui.selectedPlanId == if (useRazorpay) plan.razorpayId else planToSlug(plan)
                        Button(
                            onClick = { if (!ui.isProcessing) handlePlan(plan) },
                            enabled = !ui.isProcessing,
                            colors = ButtonDefaults.buttonColors(
                                contentColor = Color.Black,
                                disabledContentColor = Color.Black.copy(alpha = 0.38f)
                            )
                        ) {
                            if (processing)
                                CircularProgressIndicator(
                                    color = KupidxOrange,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            else
                                Text(stringResource(R.string.subscription_choose_button), color = KupidxOrange)                        }
                    }
                }
            }

        if (!useRazorpay) {
            ONE_TIME_OFFERS
                .filter { it.period == currentPeriod }
                .forEach { offer ->
                    val productAvailable = productDetailsById[offer.sku] != null
                    val processing = ui.isProcessing && ui.selectedPlanId == offer.sku
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable(
                                enabled = !ui.isProcessing && productAvailable
                            ) {
                                if (!ui.isProcessing && productAvailable) {
                                    handleOneTime(offer)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (offer.tier == Tier.PREMIUM)
                                Color(0xFFFF6F00)
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
                                    stringResource(tierLabelRes(offer.tier)),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    offer.displayPrice(ctx, isIndiaUser, isMexico, isThailand),
                                    color = Color.LightGray,
                                    fontSize = 14.sp
                                )
                                val durationLabel = stringResource(periodDurationRes(offer.period))
                                Text(
                                    stringResource(R.string.subscription_one_time_label, durationLabel),
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                val features =
                                    if (offer.tier == Tier.PREMIUM) PREMIUM_FEATURES else PLUS_FEATURES
                                features.forEach { bulletResId ->
                                    Text(
                                        text = "• ${stringResource(bulletResId)}",
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    if (!ui.isProcessing && productAvailable) {
                                        handleOneTime(offer)
                                    }
                                },
                                enabled = !ui.isProcessing && productAvailable,
                                colors = ButtonDefaults.buttonColors(
                                    contentColor = Color.Black,
                                    disabledContentColor = Color.Black.copy(alpha = 0.38f)
                                )
                            ) {
                                when {
                                    processing -> CircularProgressIndicator(
                                        color = KupidxOrange,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    !productAvailable -> Text(stringResource(R.string.subscription_loading), color = KupidxOrange)
                                    else -> Text(stringResource(R.string.subscription_buy_button), color = KupidxOrange)
                                }
                            }
                        }
                    }
                }
        }

        Spacer(Modifier.height(24.dp))
        if (!forceSubscription) {
            TextButton(onClick = { navController.popBackStack() }) {
                Text(
                    stringResource(R.string.subscription_not_now),
                    color = kupidxOrange
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Divider(color = Color(0xFF1E1E1E))
        Spacer(Modifier.height(16.dp))

        TextButton(
            onClick = { if (!working) showDeleteDialog = true },
            enabled = !working,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = kupidxOrange)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_delete_account), color = kupidxOrange)
        }
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!working) showDeleteDialog = false },
            title = { Text(stringResource(R.string.settings_delete_account), color = kupidxOrange) },
            text = { Text(stringResource(R.string.account_delete_prompt)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (working) return@TextButton
                        working = true
                        scope.launch {
                            try {
                                AccountDeletion.deleteAccount()
                                Toast.makeText(ctx, R.string.account_deleted, Toast.LENGTH_LONG).show()
                                FirebaseAuth.getInstance().signOut()
                                TokenStorageManager.clearToken(ctx)
                                ctx.startActivity(Intent(ctx, LandingActivity::class.java))
                                (ctx as? ComponentActivity)?.finish()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    ctx,
                                    e.message ?: ctx.getString(R.string.toast_unknown_error),
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                working = false
                                showDeleteDialog = false
                            }
                        }
                    },
                    enabled = !working
                ) {
                    Text(stringResource(R.string.delete), color = kupidxOrange)
                }
            },
            dismissButton = {
                if (!working) {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(R.string.cancel), color = kupidxOrange)
                    }
                }
            }
        )
    }
}