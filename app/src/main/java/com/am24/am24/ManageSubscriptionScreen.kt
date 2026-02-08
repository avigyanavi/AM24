package com.am24.am24

import android.content.Context
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
import com.am24.am24.billing.BillingManager
import com.am24.am24.ui.TierCard
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.awaitCancellation
import com.google.firebase.firestore.ListenerRegistration
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.background
import java.util.concurrent.TimeUnit


private val PLUS_FEATURES = listOf(
    R.string.feature_no_ads,
    R.string.feature_people_liked_me,
    R.string.feature_ai_messages_plus,
    R.string.feature_3_compliments,
)

private val PREMIUM_FEATURES = listOf(
    R.string.feature_ai_messages_premium,
    R.string.feature_unlimited_swipes,
    R.string.feature_unlock_maps,
    R.string.feature_5_compliments,
    R.string.feature_everything_plus
)
/* ───── unified click handler (updated) ───── */
private fun handleCancelClick(
    ctx: Context,
    nav: NavController
) {
        // Try to find a subscription SKU; fall back to the list page
        val subProductId = BillingManager.purchases.value
            .flatMap { it.products }
            .firstOrNull { id -> id.startsWith("plus") || id.startsWith("premium") }

        // Uses the new helper you added earlier
        BillingManager.openPlaySubscriptionManagement(ctx, subProductId)
        Toast.makeText(ctx, "Opening Play subscription settings…", Toast.LENGTH_SHORT).show()
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSubscriptionScreen(navController: NavController) {

    /* ───── helpers & DI ───── */
    val ctx   = LocalContext.current

    val uid   = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userDoc = FirebaseRefs.userProfiles.document(uid)

    /* ───── state ───── */
    var premiumTier     by remember { mutableStateOf("Loading…") }
    var expiry          by remember { mutableStateOf("Loading…") }
    var subscriptionId  by remember { mutableStateOf<String?>(null) }
    var isPlus          by remember { mutableStateOf(false) }
    var isPremium       by remember { mutableStateOf(false) }
    var subscriptionStatus by remember { mutableStateOf<String?>(null) }

    /* ───── one-shot fetch ───── */
    LaunchedEffect(uid) {
        var registration: ListenerRegistration? = null
        registration = userDoc.addSnapshotListener { snap, _ ->
            val data = snap?.data.orEmpty()
            isPlus = data["isPlus"] as? Boolean ?: false
            isPremium = data["isPremium"] as? Boolean ?: false
            premiumTier = when {
                isPremium -> "Premium"
                isPlus -> "Plus"
                else -> "Free"
            }

            subscriptionId = (data["subscription"] as? Map<*, *>)?.get("id") as? String
            val nextRenewalValue = (data["nextRenewal"] as? Number)?.toLong()?.takeIf { it > 0L }
            val loginPlusExpiryValue = (data["loginPlusExpiry"] as? Number)?.toLong() ?: 0L
            val entryFeePaidAtValue = (data["entryFeePaidAt"] as? Number)?.toLong() ?: 0L
            val entryFeeOfferExpiryValue = (data["entryFeeOfferExpiry"] as? Number)?.toLong() ?: 0L
            val now = System.currentTimeMillis()
            val entryFeePaidExpiry = entryFeePaidAtValue
                .takeIf { it > 0L }
                ?.let { it + TimeUnit.DAYS.toMillis(30) }
                ?.takeIf { it > now }
            val entryFeeOfferExpiry = entryFeeOfferExpiryValue
                .takeIf { it > now }
            val activeEntryFeeExpiry = listOfNotNull(entryFeePaidExpiry, entryFeeOfferExpiry)
                .maxOrNull()
            val activeLoginPlusExpiry = loginPlusExpiryValue.takeIf { it > now }
            val resolvedExpiryMillis = nextRenewalValue
                ?: activeEntryFeeExpiry
                ?: activeLoginPlusExpiry
            val resolvedExpiryLabel = resolvedExpiryMillis
                ?.let { DateFormat.getDateInstance().format(Date(it)) }

            expiry = when {
                !subscriptionId.isNullOrBlank() && resolvedExpiryLabel == null -> "Never"
                resolvedExpiryLabel != null -> resolvedExpiryLabel
                else -> "N/A"
            }
            subscriptionStatus = data["subscriptionStatus"] as? String
        }

        try {
            awaitCancellation()
        } finally {
            registration?.remove()
        }
    }

    val scroll  = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Manage Plan") },
                navigationIcon = {
                    IconButton(onClick = { navController.safePopBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    /* always-visible cancel */
                    TextButton(
                        onClick = {
                            handleCancelClick(ctx, navController)
                        }
                    ) {
                        Text("Cancel", color = KupidxOrange, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    ) { padding ->

        val backgroundColor = if (isPlus) Color(0xFF121212) else MaterialTheme.colorScheme.background

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            /* ───── status ───── */
            val statusLabel = when (subscriptionStatus) {
                "active" -> stringResource(R.string.subscription_active)
                "inactive" -> stringResource(R.string.subscription_inactive)
                "completed" -> stringResource(R.string.subscription_completed)
                "cancelled" -> stringResource(R.string.subscription_cancelled)
                "suspended" -> stringResource(R.string.subscription_suspended)
                "expired" -> stringResource(R.string.subscription_expired)
                null -> "N/A"
                else -> subscriptionStatus ?: "N/A"
            }
            val statusDescription = if (subscriptionStatus != null) {
                val reason = if (subscriptionStatus == "inactive")
                    " \u2013 " + stringResource(R.string.payment_failed) else ""
                "Status: $statusLabel$reason"
            } else {
                null            }

            /* ───── benefits ───── */
            val featureList = when {
                isPremium -> PREMIUM_FEATURES
                isPlus    -> PLUS_FEATURES
                else      -> emptyList()
            }
            if (isPlus) {
                CurrentMembershipCard(
                    expiry = expiry,
                    statusText = statusDescription,
                    featureList = featureList
                )
            } else {
                Text("Membership: $premiumTier", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Expires on: $expiry", fontSize = 16.sp)
                statusDescription?.let {
                    Text(it, fontSize = 16.sp)
                }

                if (featureList.isNotEmpty()) {
                    Text("Your Benefits:", fontWeight = FontWeight.SemiBold)
                    featureList.forEach { bulletResId ->
                        Text(
                            text = "• ${stringResource(bulletResId)}",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            /* ───── upsell to Premium ───── */
            if (isPlus) {
                TierCard(
                    tier       = Tier.PREMIUM,
                    colour     = Color(0xFFFF6F00),
                    onAuto     = {
                        navController.navigate("subscription?allowIfSubscribed=true")
                    }
                )
            }

            /* ───── actions ───── */
            when {
                /* change plan for one-time buyers */
                subscriptionId.isNullOrBlank() && (isPlus || isPremium) -> {
                    OutlinedButton(
                        onClick  = { navController.navigate("subscription?allowIfSubscribed=true") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change Plan", color = Color(0xFFFF6600))
                    }
                }

                /* active recurring subscription → show extra cancel */
                !subscriptionId.isNullOrBlank() -> {
                    Button(
                        onClick = { handleCancelClick(ctx, navController) },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6600))
                    ) { Text("Cancel Subscription") }
                }
            }
        }
    }
}

@Composable
private fun CurrentMembershipCard(
    expiry: String,
    statusText: String?,
    featureList: List<Int>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.subscription_tier_plus),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = "Expires: $expiry",
                color = Color.White,
                fontSize = 14.sp
            )
            statusText?.let {
                Text(
                    text = it,
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
            if (featureList.isNotEmpty()) {
                Text(
                    text = "Your Benefits:",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                featureList.forEach { bulletResId ->
                    Text(
                        text = "• ${stringResource(bulletResId)}",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}