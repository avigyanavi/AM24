@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

/* Android & Compose */
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import com.google.firebase.firestore.ListenerRegistration
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
import kotlinx.coroutines.launch
import java.util.Locale
import com.am24.am24.AccountDeletion
import com.am24.am24.LandingActivity
import com.am24.am24.TokenStorageManager
import com.am24.am24.safePopBackStack


enum class Period(val label: String) {
    WEEK("Weekly"),
    MONTH("Monthly"),
    YEAR("Yearly")
}

private val PLUS_FEATURES = listOf(
    R.string.feature_no_ads,
    R.string.feature_people_liked_me,
    R.string.feature_ai_messages_plus,
    R.string.feature_50_swipes,
    R.string.feature_3_compliments,
)

private val PREMIUM_FEATURES = listOf(
    R.string.feature_video_rank_section,
    R.string.feature_unlock_maps,
    R.string.feature_unlimited_swipes,
    R.string.feature_5_compliments,
    R.string.feature_ai_messages_premium
    )
private data class UiState(
    val isProcessing: Boolean = false,
    val selectedPlanId: String? = null
)

/* ─────────  model for UI  ───────── */
enum class Tier { PLUS, PREMIUM }
private data class Plan(
    val period: Period,
    val tier: Tier
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
    Plan(Period.MONTH, Tier.PLUS),
    Plan(Period.MONTH, Tier.PREMIUM),
    Plan(Period.YEAR,  Tier.PLUS),
    Plan(Period.YEAR,  Tier.PREMIUM),
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

private fun OneTimeOffer.displayPrice(context: Context): String = context.getString(
    R.string.subscription_one_time_price_usd,
    formatPrice(usdPrice)
)


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
    val userDoc = FirebaseRefs.userProfiles.document(uid)
    /* -------------------------------------------------- */
    val scope  = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val act         = ctx as FragmentActivity
    var ui by remember { mutableStateOf(UiState()) }
    val subs by BillingManager.subsProducts.collectAsState()
    val products by BillingManager.products.collectAsState()
    val subsDetailsById = remember(subs) { subs.associateBy { it.productId } }
    val inappDetailsById = remember(products) { products.associateBy { it.productId } }

    /* real-time flags to hide the screen if user already subscribed */
    var plus    by remember { mutableStateOf<Boolean?>(null) }
    var premium by remember { mutableStateOf<Boolean?>(null) }

    DisposableEffect(uid) {
        var registration: ListenerRegistration? = null
        registration = userDoc.addSnapshotListener { snap, _ ->
            val data = snap?.data.orEmpty()
            plus = data["isPlus"] as? Boolean
            premium = data["isPremium"] as? Boolean
        }
        onDispose { registration?.remove() }
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
     LaunchedEffect(Unit) { navController.safePopBackStack() }
        return
    }

    fun handlePlan(plan: Plan) {
        val slug = planToSlug(plan)
        val selectedId = slug
        ui = UiState(isProcessing = true, selectedPlanId = selectedId)
        val productId = if (slug.startsWith("premium")) "premium" else "plus"
        val pd = subs.firstOrNull { it.productId == productId }
        if (pd != null) {
            BillingManager.launchSubsFlow(act, pd, basePlanId = slug, obfuscatedAccountId = uid)
        } else {
            ui = UiState()
        }
    }

    fun handleOneTime(offer: OneTimeOffer) {
        val pd = inappDetailsById[offer.sku]
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
    LaunchedEffect(Unit) {
        BillingManager.purchaseFlowFinished.collect {
            ui = UiState()
            navController.navigate("settings") {
                popUpTo("subscription") { inclusive = true }
            }
        }
    }

    fun playSubscriptionPrice(plan: Plan): String? {
        val slug = planToSlug(plan)
        val productId = if (slug.startsWith("premium")) "premium" else "plus"
        val pd = subsDetailsById[productId] ?: return null
        val offer = pd.subscriptionOfferDetails?.firstOrNull { it.basePlanId == slug }
            ?: pd.subscriptionOfferDetails?.firstOrNull()
        val pricingPhase = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()
        return pricingPhase?.formattedPrice
    }

    fun playOneTimePrice(offer: OneTimeOffer): String? {
        return inappDetailsById[offer.sku]
            ?.oneTimePurchaseOfferDetails
            ?.formattedPrice
    }

    /* ---------- UI ---------- */

    val availablePeriods = listOf(Period.MONTH, Period.YEAR)

    var currentPeriod by remember { mutableStateOf(availablePeriods.first()) }

    /* Reset the selected period if the available periods change */
    LaunchedEffect(Unit) {
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
                            val playPriceLabel = playSubscriptionPrice(plan)

                            val priceLabel = playPriceLabel ?: stringResource(
                                R.string.subscription_price_label_usd,
                                formatPrice(usdPrice(plan)),
                                periodLabelLower
                            )
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
                                ui.selectedPlanId == planToSlug(plan)
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

        ONE_TIME_OFFERS
            .filter { it.period == currentPeriod }
            .forEach { offer ->
                val productAvailable = inappDetailsById[offer.sku] != null
                val processing = ui.isProcessing && ui.selectedPlanId == offer.sku
                val playPrice = playOneTimePrice(offer)
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
                            if (playPrice != null) {
                                Text(
                                    playPrice,
                                    color = Color.LightGray,
                                    fontSize = 14.sp
                                )
                            } else {
                                Text(
                                    offer.displayPrice(ctx),
                                    color = Color.LightGray,
                                    fontSize = 14.sp
                                )
                            }
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

        Spacer(Modifier.height(24.dp))
        if (!forceSubscription) {
            TextButton(onClick = { navController.safePopBackStack() }) {
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