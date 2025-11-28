package com.am24.am24

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.am24.am24.billing.BillingManager
import com.android.billingclient.api.Purchase
import com.facebook.appevents.AppEventsLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.util.Currency
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryFeePlusScreen(navController: NavController) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity ?: return
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRef = remember(uid) { FirebaseRefs.db.getReference("users/$uid") }
    var isProcessing by remember { mutableStateOf(false) }
    var offerExpiry by remember { mutableStateOf<Long?>(null) }
    val products by BillingManager.products.collectAsState()
    val entryProduct = products.firstOrNull { it.productId == "entry_fee" }
    val entryOffer = entryProduct?.oneTimePurchaseOfferDetails
    val arrowBack = Icons.Filled.ArrowBack

    LaunchedEffect(uid) {
        runCatching {
            val snap = userRef.get().await()
            offerExpiry = snap.child("entryFeeOfferExpiry").getValue(Long::class.java)
        }
        userRef.child("entryFeeOfferSeen").setValue(true)
    }

    LaunchedEffect(Unit) {
        BillingManager.purchaseFlowFinished.collect {
            val purchase = BillingManager.purchases.value.firstOrNull { it.products.contains("entry_fee") }
            if (purchase != null && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                isProcessing = true
                val monthMillis = TimeUnit.DAYS.toMillis(30)
                val purchaseTime = purchase.purchaseTime.takeIf { it > 0L } ?: System.currentTimeMillis()
                val renewalTime = purchaseTime + monthMillis
                val updates = mutableMapOf<String, Any>(
                    "entryFeePaidAt" to ServerValue.TIMESTAMP,
                    "isEntryFeePaid" to true,
                    "isPlus" to true,
                    "entryFeePlusIntroSeen" to false,
                    "entryFeeOfferSeen" to true,
                    "loginPlusExpiry" to renewalTime,
                    "entryFeeOfferExpiry" to renewalTime
                )
                userRef.updateChildren(updates)
                    .addOnSuccessListener {
                        BillingManager.creditAiMessagesOnce(userRef, 25, renewalTime)
                        val offerDetails = BillingManager.products.value
                            .firstOrNull { it.productId == "entry_fee" }
                            ?.oneTimePurchaseOfferDetails
                        if (offerDetails != null) {
                            try {
                                val amount = BigDecimal.valueOf(offerDetails.priceAmountMicros, 6)
                                val currency = Currency.getInstance(offerDetails.priceCurrencyCode)
                                AppEventsLogger.newLogger(ctx).logPurchase(amount, currency)
                            } catch (_: Exception) {
                            }
                        }
                        isProcessing = false
                        Toast.makeText(ctx, ctx.getString(R.string.entry_fee_plus_success_toast), Toast.LENGTH_LONG).show()
                        navController.popBackStack()
                    }
                    .addOnFailureListener {
                        isProcessing = false
                    }
            }
        }
    }

    val offerHoursLeft = offerExpiry?.let { expiry ->
        val remaining = expiry - System.currentTimeMillis()
        if (remaining > 0) max(1, ceil(remaining / 3600000.0).toInt()) else null
    }

    val buttonEnabled = entryProduct != null && !isProcessing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.entry_fee_plus_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = arrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.entry_fee_plus_header),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = entryOffer?.let {
                        stringResource(R.string.entry_fee_plus_description_price, it.formattedPrice)
                    } ?: stringResource(R.string.entry_fee_plus_description_generic),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center
                )
                offerHoursLeft?.let { hours ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.entry_fee_plus_offer_timer, hours),
                        style = MaterialTheme.typography.bodySmall,
                        color = KupidxOrange,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Button(
                onClick = {
                    entryProduct?.let { product ->
                        BillingManager.launchBillingFlow(activity, product, uid)
                    }
                },
                enabled = buttonEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    val buttonLabel = when {
                        entryOffer != null -> stringResource(R.string.entry_fee_plus_button, entryOffer.formattedPrice)
                        entryProduct == null -> stringResource(R.string.paywall_button_loading)
                        else -> stringResource(R.string.entry_fee_plus_button_generic)
                    }
                    Text(text = buttonLabel, color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp)
                }
            }
        }
    }
}