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
import com.google.firebase.database.*
import kotlinx.coroutines.flow.collect
import com.facebook.appevents.AppEventsLogger
import java.math.BigDecimal
import java.util.Currency
import com.android.billingclient.api.Purchase
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
    DisposableEffect(userRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userCountry = snapshot.child("country").getValue(String::class.java)
                entryFeePaid = snapshot.child("isEntryFeePaid").getValue(Boolean::class.java) == true
                plusActive = snapshot.child("isPlus").getValue(Boolean::class.java) == true
                loginPlusExpiry = snapshot.child("loginPlusExpiry").getValue(Long::class.java)
                entryFeePaidAt = snapshot.child("entryFeePaidAt").getValue(Long::class.java)
                entryFeeOfferSeen = snapshot.child("entryFeeOfferSeen").getValue(Boolean::class.java)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        userRef.addValueEventListener(listener)
        onDispose { userRef.removeEventListener(listener) }
    }

    LaunchedEffect(entryFeePaid, plusActive, loginPlusExpiry, entryFeePaidAt) {
        if (!entryFeePaid) return@LaunchedEffect

        val yearInMillis = TimeUnit.DAYS.toMillis(365)

        if (!plusActive) {
            userRef.child("isPlus").setValue(true)
            plusActive = true
        }

        val paidAt = entryFeePaidAt ?: 0L
        if (paidAt > 0L) {
            val minimumExpiry = paidAt + yearInMillis
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

    val products by BillingManager.products.collectAsState()
    val entryProduct = products.firstOrNull { it.productId == "entry_fee" }
    val entryOffer = entryProduct?.oneTimePurchaseOfferDetails
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        BillingManager.purchaseFlowFinished.collect {
            val purchase = BillingManager.purchases.value.firstOrNull { it.products.contains("entry_fee") }
            if (purchase != null && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                isProcessing = true
                val yearInMillis = TimeUnit.DAYS.toMillis(365)
                val purchaseTime = purchase.purchaseTime.takeIf { it > 0L } ?: System.currentTimeMillis()
                val existingExpiry = loginPlusExpiry ?: 0L
                val desiredExpiry = purchaseTime + yearInMillis
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

    val entryFeeMessage = when {
        isIndia -> stringResource(R.string.paywall_entry_fee_message_india)
        isMexico -> stringResource(R.string.paywall_entry_fee_message_mexico)
        isUnitedStates -> stringResource(R.string.paywall_entry_fee_message_us)
        entryOffer != null -> stringResource(
            R.string.paywall_entry_fee_message_generic,
            entryOffer.formattedPrice
        )
        else -> stringResource(R.string.paywall_entry_fee_loading)
    }

    val buttonLabel = when {
        isProcessing -> stringResource(R.string.paywall_button_processing)
        entryProduct == null -> stringResource(R.string.paywall_button_loading)
        isIndia -> stringResource(R.string.paywall_button_pay_india)
        isMexico -> stringResource(R.string.paywall_button_pay_mexico)
        isUnitedStates -> stringResource(R.string.paywall_button_pay_us)
        entryOffer != null -> stringResource(
            R.string.paywall_button_pay_generic,
            entryOffer.formattedPrice
        )
        else -> stringResource(R.string.paywall_button_pay_default)
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
                    if (!hasNavigatedAway) {
                        if (!entryFeePaid) {
                            val expiry = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24)
                            userRef.child("entryFeeOfferExpiry").setValue(expiry)
                            userRef.child("entryFeeOfferSeen").setValue(false)
                        } else if (entryFeeOfferSeen == null) {
                            userRef.child("entryFeeOfferSeen").setValue(true)
                        }
                        hasNavigatedAway = true
                        onPaidCallback()
                    }
                }
            ) {
                Text(stringResource(R.string.paywall_skip), color = KupidxOrange)
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