package com.am24.am24

import android.app.Activity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    var hasNavigatedAway by remember(uid) { mutableStateOf(false) }
    DisposableEffect(userRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userCountry = snapshot.child("country").getValue(String::class.java)
                entryFeePaid = snapshot.child("isEntryFeePaid").getValue(Boolean::class.java) == true
                plusActive = snapshot.child("isPlus").getValue(Boolean::class.java) == true
                loginPlusExpiry = snapshot.child("loginPlusExpiry").getValue(Long::class.java)
                entryFeePaidAt = snapshot.child("entryFeePaidAt").getValue(Long::class.java)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        userRef.addValueEventListener(listener)
        onDispose { userRef.removeEventListener(listener) }
    }

    LaunchedEffect(entryFeePaid, plusActive, loginPlusExpiry, entryFeePaidAt) {

        if (entryFeePaid) {
            if (!plusActive) {
                userRef.child("isPlus").setValue(true)
            }
            val paidAt = entryFeePaidAt ?: 0L
            if (paidAt > 0L) {
                val expectedExpiry = paidAt + TimeUnit.DAYS.toMillis(365)
                if ((loginPlusExpiry ?: 0L) != expectedExpiry) {
                    userRef.child("loginPlusExpiry").setValue(expectedExpiry)
                }
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
                val purchaseTime = purchase.purchaseTime
                val updates = mutableMapOf<String, Any>(
                    "entryFeePaidAt" to ServerValue.TIMESTAMP,
                    "isEntryFeePaid" to true,
                    "isPlus" to true
                )
                if (purchaseTime > 0L) {
                    updates["loginPlusExpiry"] = purchaseTime + yearInMillis
                }
                userRef.updateChildren(updates)
                    .addOnSuccessListener {
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
        isIndia -> "Rs. 20 entry fee - to keep the bots away"
        isMexico -> "Entrada: MX$ 4.99 para mantener alejados a los robots."
        isUnitedStates -> "US 0.50$ entry fee - to keep the bots away"
        entryOffer != null -> "${entryOffer.formattedPrice} entry fee - to keep the bots away"
        else -> "Entry fee - price loading..."
    }

    val buttonLabel = when {
        isProcessing -> "Processing..."
        entryProduct == null -> "Loading..."
        isIndia -> "Pay Rs. 20 and Continue"
        isMexico -> "Pagar MX$ 4.99 y continuar"
        isUnitedStates -> "Pay US 0.50$ and Continue"
        entryOffer != null -> "Pay ${entryOffer.formattedPrice} and Continue"
        else -> "Pay and Continue"
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Confirma que no eres un bot y obtén un año de KupidxPlus (oferta de lanzamiento): mira a quién le gustas y más.",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text(text = entryFeeMessage, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            "Oferta de lanzamiento: acceso por 1 año desde tu pago.",
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
                        hasNavigatedAway = true
                        onPaidCallback()
                    }
                }
            ) {
                Text("Skip")
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Watch 1 ad on app open everytime",
                color = Color.White,
                fontSize = 12.sp
            )        }
    }
}