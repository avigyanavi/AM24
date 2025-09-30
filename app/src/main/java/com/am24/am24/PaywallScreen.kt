package com.am24.am24

import android.app.Activity
import android.widget.Toast
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
import com.google.firebase.database.*
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
        FirebaseDatabase.getInstance().getReference("users/$uid")
    }

    var userCountry by remember { mutableStateOf<String?>(null) }
    var entryFeePaid by remember { mutableStateOf(false) }
    var plusActive by remember { mutableStateOf(false) }
    var loginPlusExpiry by remember { mutableStateOf<Long?>(null) }
    var entryFeePaidAt by remember { mutableStateOf<Long?>(null) }
    var entryFeeOfferSeen by remember { mutableStateOf<Boolean?>(null) }
    var hasNavigatedAway by remember(uid) { mutableStateOf(false) }
    var hasUsedFreeTrial by remember { mutableStateOf(false) }
    var freeTrialExpiry by remember { mutableStateOf<Long?>(null) }
    var skipProcessing by remember { mutableStateOf(false) }
    DisposableEffect(userRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userCountry = snapshot.child("country").getValue(String::class.java)
                entryFeePaid = snapshot.child("isEntryFeePaid").getValue(Boolean::class.java) == true
                plusActive = snapshot.child("isPlus").getValue(Boolean::class.java) == true
                loginPlusExpiry = snapshot.child("loginPlusExpiry").getValue(Long::class.java)
                entryFeePaidAt = snapshot.child("entryFeePaidAt").getValue(Long::class.java)
                entryFeeOfferSeen = snapshot.child("entryFeeOfferSeen").getValue(Boolean::class.java)
                hasUsedFreeTrial = snapshot.child("hasUsedFreeTrial").getValue(Boolean::class.java) == true
                freeTrialExpiry = snapshot.child("freeTrialExpiry").getValue(Long::class.java)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        userRef.addValueEventListener(listener)
        onDispose { userRef.removeEventListener(listener) }
    }

    LaunchedEffect(entryFeePaid, plusActive, loginPlusExpiry, entryFeePaidAt) {
        if (!entryFeePaid) return@LaunchedEffect

        val monthInMillis = TimeUnit.DAYS.toMillis(30)

        if (!plusActive) {
            userRef.child("isPlus").setValue(true)
            plusActive = true
        }

        val paidAt = entryFeePaidAt ?: 0L
        if (paidAt > 0L) {
            val minimumExpiry = paidAt + monthInMillis
            val currentExpiry = loginPlusExpiry ?: 0L
            if (currentExpiry < minimumExpiry) {
                userRef.child("loginPlusExpiry").setValue(minimumExpiry)
            }
        }
    }

    val onPaidCallback by rememberUpdatedState(onPaid)
    LaunchedEffect(entryFeePaid, plusActive, hasNavigatedAway) {
        if (!hasNavigatedAway && (entryFeePaid || plusActive)) {
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
                val existingExpiry = loginPlusExpiry ?: 0L
                val desiredExpiry = purchaseTime + monthInMillis
                val finalExpiry = maxOf(existingExpiry, desiredExpiry)
                val updates = mutableMapOf<String, Any>(
                    "entryFeePaidAt" to ServerValue.TIMESTAMP,
                    "isEntryFeePaid" to true,
                    "isPlus" to true,
                    "entryFeePlusIntroSeen" to false,
                    "entryFeeOfferSeen" to true
                )
                updates["loginPlusExpiry"] = finalExpiry
                userRef.updateChildren(updates)
                    .addOnSuccessListener {
                        userRef.child("entryFeeOfferExpiry").removeValue()
                        val offer = BillingManager.products.value
                            .firstOrNull { it.productId == "entry_fee" }
                            ?.oneTimePurchaseOfferDetails
                        if (offer != null) {
                            try {
                                val amount = BigDecimal.valueOf(offer.priceAmountMicros, 6)
                                val currency = Currency.getInstance(offer.priceCurrencyCode)
                                AppEventsLogger.newLogger(ctx).logPurchase(amount, currency)
                            } catch (_: Exception) {
                                // Ignore analytics failures
                            }
                        }
                        entryFeePaid = true
                        plusActive = true
                        entryFeePaidAt = purchaseTime
                        loginPlusExpiry = finalExpiry
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

    val formattedEntryPrice = entryOffer?.formattedPrice

    val entryFeeMessage = when {
        formattedEntryPrice != null && isIndia -> stringResource(R.string.paywall_entry_fee_message_india, formattedEntryPrice)
        formattedEntryPrice != null && isMexico -> stringResource(R.string.paywall_entry_fee_message_mexico, formattedEntryPrice)
        formattedEntryPrice != null && isUnitedStates -> stringResource(R.string.paywall_entry_fee_message_us, formattedEntryPrice)
        formattedEntryPrice != null -> stringResource(R.string.paywall_entry_fee_message_generic, formattedEntryPrice)
        else -> stringResource(R.string.paywall_entry_fee_loading)
    }

    val buttonLabel = when {
        isProcessing -> stringResource(R.string.paywall_button_processing)
        entryProduct == null -> stringResource(R.string.paywall_button_loading)
        formattedEntryPrice != null && isIndia -> stringResource(R.string.paywall_button_pay_india, formattedEntryPrice)
        formattedEntryPrice != null && isMexico -> stringResource(R.string.paywall_button_pay_mexico, formattedEntryPrice)
        formattedEntryPrice != null && isUnitedStates -> stringResource(R.string.paywall_button_pay_us, formattedEntryPrice)
        formattedEntryPrice != null -> stringResource(R.string.paywall_button_pay_generic, formattedEntryPrice)
        else -> stringResource(R.string.paywall_button_pay_default)
    }
    val skipLabel = if (!hasUsedFreeTrial) {
        stringResource(R.string.paywall_start_trial_button)
    } else {
        stringResource(R.string.paywall_continue_button)
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
        Text(
            stringResource(R.string.paywall_title),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text(text = entryFeeMessage, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.paywall_opening_offer_message),
            color = Color.White,
            fontSize = 14.sp
        )
        trialStatusText?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Color.White, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val act = ctx as? Activity ?: return@Button
                entryProduct?.let {
                    BillingManager.launchBillingFlow(act, it, uid)
                }
            },
            enabled = entryProduct != null && !isProcessing
        ) {
            Text(buttonLabel)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = {
                    if (hasNavigatedAway || skipProcessing) return@TextButton

                    if (!hasUsedFreeTrial) {
                        skipProcessing = true
                        val now = System.currentTimeMillis()
                        val expiry = now + TimeUnit.DAYS.toMillis(30)
                        val updates = mutableMapOf<String, Any>(
                            "hasUsedFreeTrial" to true,
                            "freeTrialExpiry" to expiry,
                            "freeTrialStartedAt" to ServerValue.TIMESTAMP,
                            "freeTrialCompleted" to false,
                            "loginPlusExpiry" to expiry,
                            "isPlus" to true
                        )
                        userRef.updateChildren(updates)
                            .addOnSuccessListener {
                                hasUsedFreeTrial = true
                                freeTrialExpiry = expiry
                                loginPlusExpiry = expiry
                                plusActive = true
                                skipProcessing = false
                                if (!hasNavigatedAway) {
                                    hasNavigatedAway = true
                                    onPaidCallback()
                                }
                            }
                            .addOnFailureListener {
                                skipProcessing = false
                                Toast.makeText(ctx, R.string.paywall_trial_error, Toast.LENGTH_LONG).show()
                            }
                    } else {
                        hasNavigatedAway = true
                        onPaidCallback()
                    }
                },
                enabled = !skipProcessing
            ) {
                Text(skipLabel, color = KupidxOrange)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.paywall_skip_disclaimer),
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}