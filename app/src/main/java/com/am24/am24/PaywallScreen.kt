package com.am24.am24

import android.app.Activity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.am24.am24.billing.BillingManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.collect
import com.facebook.appevents.AppEventsLogger
import java.math.BigDecimal
import java.util.Currency
import com.android.billingclient.api.Purchase
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit


@Composable
fun PaywallScreen(onPaid: () -> Unit) {
    val ctx = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRef = remember(uid) {
        FirebaseRefs.userProfiles.document(uid)
    }

    var userCountry by remember { mutableStateOf<String?>(null) }
    var entryFeePaid by remember { mutableStateOf(false) }
    var plusActive by remember { mutableStateOf(false) }
    var nextRenewal by remember { mutableStateOf<Long?>(null) }
    var entryFeePaidAt by remember { mutableStateOf<Long?>(null) }
    var entryFeeOfferSeen by remember { mutableStateOf<Boolean?>(null) }
    var hasNavigatedAway by remember(uid) { mutableStateOf(false) }
    var hasUsedFreeTrial by remember { mutableStateOf(false) }
    var freeTrialExpiry by remember { mutableStateOf<Long?>(null) }
    var hasLoggedPaymentEvent by remember { mutableStateOf(false) }

    val logger = remember(ctx) { AppEventsLogger.newLogger(ctx) }

    fun logPaymentEventOnce(priceAmountMicros: Long?, currencyCode: String?) {
        if (hasLoggedPaymentEvent || priceAmountMicros == null || currencyCode.isNullOrBlank()) {
            return
        }
        try {
            val amount = BigDecimal.valueOf(priceAmountMicros, 6)
            val currency = Currency.getInstance(currencyCode)
            logger.logPurchase(amount, currency)
            hasLoggedPaymentEvent = true
        } catch (_: Exception) {
            // Ignore analytics failures
        }
    }

    DisposableEffect(userRef) {
        val registration = userRef.addSnapshotListener { snapshot, _ ->
            snapshot ?: return@addSnapshotListener
            userCountry = snapshot.getString("country")
            entryFeePaid = snapshot.getBoolean("isEntryFeePaid") == true
            plusActive = snapshot.getBoolean("isPlus") == true
            nextRenewal = snapshot.getLong("nextRenewal")
            entryFeePaidAt = snapshot.getLong("entryFeePaidAt")
            entryFeeOfferSeen = snapshot.getBoolean("entryFeeOfferSeen")
            hasUsedFreeTrial = snapshot.getBoolean("hasUsedFreeTrial") == true
            freeTrialExpiry = snapshot.getLong("freeTrialExpiry")
        }
        onDispose { registration.remove() }
    }

    LaunchedEffect(entryFeePaid, plusActive, nextRenewal, entryFeePaidAt) {
        val monthInMillis = TimeUnit.DAYS.toMillis(30)
        val hasActiveRenewal = (nextRenewal ?: 0L) > System.currentTimeMillis()

        if ((entryFeePaid || hasActiveRenewal) && !plusActive) {
            userRef.set(mapOf("isPlus" to true), SetOptions.merge())
            plusActive = true
        }

        if (!entryFeePaid) return@LaunchedEffect

        val paidAt = entryFeePaidAt ?: 0L
        if (paidAt > 0L) {
            val minimumRenewal = paidAt + monthInMillis
            val currentRenewal = nextRenewal ?: 0L
            if (currentRenewal < minimumRenewal) {
                userRef.set(mapOf("nextRenewal" to minimumRenewal), SetOptions.merge())
                nextRenewal = minimumRenewal
            }
        }
    }

    val onPaidCallback by rememberUpdatedState(onPaid)
    LaunchedEffect(entryFeePaid, plusActive, nextRenewal, hasNavigatedAway) {
        val hasActiveRenewal = (nextRenewal ?: 0L) > System.currentTimeMillis()
        if (!hasNavigatedAway && (entryFeePaid || plusActive || hasActiveRenewal)) {
            hasNavigatedAway = true
            onPaidCallback()
        }
    }
    val isMexico = CountryUtil.isMexico(ctx, userCountry)
    val isIndia = CountryUtil.isIndia(ctx, userCountry)
    val isUnitedStates = CountryUtil.isUnitedStates(ctx, userCountry)
    val trialActive = hasUsedFreeTrial && (freeTrialExpiry ?: 0L) > System.currentTimeMillis()
    val trialExpiryLabel = freeTrialExpiry?.takeIf { it > 0L }?.let { DateFormat.getDateInstance().format(Date(it)) }

    val products by BillingManager.products.collectAsState()
    val entryProduct = products.firstOrNull { it.productId == "entry_fee" }
    val entryOffer = entryProduct?.oneTimePurchaseOfferDetails
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        BillingManager.purchaseFlowFinished.collect {
            val purchase = BillingManager.purchases.value.firstOrNull { it.products.contains("entry_fee") }
            if (purchase != null && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                isProcessing = true
                val monthInMillis = TimeUnit.DAYS.toMillis(30)
                val purchaseTime = purchase.purchaseTime.takeIf { it > 0L } ?: System.currentTimeMillis()
                val existingRenewal = nextRenewal ?: 0L
                val desiredRenewal = purchaseTime + monthInMillis
                val finalRenewal = maxOf(existingRenewal, desiredRenewal)
                val updates = mutableMapOf<String, Any>(
                    "entryFeePaidAt" to FieldValue.serverTimestamp(),
                    "isEntryFeePaid" to true,
                    "isPlus" to true,
                    "entryFeePlusIntroSeen" to false,
                    "entryFeeOfferSeen" to true,
                    "entryFeeOfferExpiry" to finalRenewal
                )
                updates["nextRenewal"] = finalRenewal
                userRef.set(updates, SetOptions.merge())
                    .addOnSuccessListener {
                        BillingManager.creditAiMessagesOnce(userRef, 25, finalRenewal)
                        val offer = BillingManager.products.value
                            .firstOrNull { it.productId == "entry_fee" }
                            ?.oneTimePurchaseOfferDetails
                        logPaymentEventOnce(
                            offer?.priceAmountMicros,
                            offer?.priceCurrencyCode
                        )
                        entryFeePaid = true
                        plusActive = true
                        entryFeePaidAt = purchaseTime
                        nextRenewal = finalRenewal
                        isProcessing = false
                        if (!hasNavigatedAway) {
                            hasNavigatedAway = true
                            onPaidCallback()
                        }
                    }
                    .addOnFailureListener {
                        isProcessing = false
                    }
            }
        }
    }

    LaunchedEffect(entryFeePaid, nextRenewal, entryOffer) {
        val hasActiveRenewal = (nextRenewal ?: 0L) > System.currentTimeMillis()
        if ((entryFeePaid || hasActiveRenewal) && entryOffer != null) {
            logPaymentEventOnce(entryOffer.priceAmountMicros, entryOffer.priceCurrencyCode)
        }
    }

    val formattedEntryPrice = entryOffer?.formattedPrice

//    val entryFeeMessage = when {
//        formattedEntryPrice != null && isIndia -> stringResource(R.string.paywall_entry_fee_message_india, formattedEntryPrice)
//        formattedEntryPrice != null && isMexico -> stringResource(R.string.paywall_entry_fee_message_mexico, formattedEntryPrice)
//        formattedEntryPrice != null && isUnitedStates -> stringResource(R.string.paywall_entry_fee_message_us, formattedEntryPrice)
//        formattedEntryPrice != null -> stringResource(R.string.paywall_entry_fee_message_generic, formattedEntryPrice)
//        else -> stringResource(R.string.paywall_entry_fee_loading)
//    }

    val buttonLabel = when {
        isProcessing -> stringResource(R.string.paywall_button_processing)
        entryProduct == null -> stringResource(R.string.paywall_button_loading)
        formattedEntryPrice != null && isIndia -> stringResource(R.string.paywall_button_pay_india, formattedEntryPrice)
        formattedEntryPrice != null && isMexico -> stringResource(R.string.paywall_button_pay_mexico, formattedEntryPrice)
        formattedEntryPrice != null && isUnitedStates -> stringResource(R.string.paywall_button_pay_us, formattedEntryPrice)
        formattedEntryPrice != null -> stringResource(R.string.paywall_button_pay_generic, formattedEntryPrice)
        else -> stringResource(R.string.paywall_button_pay_default)
    }

    val trialStatusText = when {
        hasUsedFreeTrial && trialActive && trialExpiryLabel != null ->
            stringResource(R.string.paywall_trial_active_message, trialExpiryLabel)
        hasUsedFreeTrial -> stringResource(R.string.paywall_trial_ended_message)
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Just another step")
        Spacer(Modifier.height(16.dp))
//        Text(text = entryFeeMessage, color = Color.White)
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(24.dp),
//            horizontalAlignment = Alignment.CenterHorizontally,
//            verticalArrangement = Arrangement.Center
//        ) {
//            Text(
//                text = "Just one last step…",
//                color = Color.White,
//                fontSize = 24.sp,
//                fontWeight = FontWeight.Bold
//            )
//
//            Spacer(Modifier.height(16.dp))
//
//            Text(
//                text = "Finalizing your access.",
//                color = Color.White,
//                fontSize = 16.sp
//            )
//
//            Spacer(Modifier.height(8.dp))
//
//            Text(
//                text = "This will only take a moment.",
//                color = Color.Gray,
//                fontSize = 13.sp
//            )
//
//            Spacer(Modifier.height(32.dp))
//
//            CircularProgressIndicator(
//                color = Color(0xFFFF6F00),
//                strokeWidth = 3.dp
//            )
//        }
    }
}