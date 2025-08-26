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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.collect
import com.facebook.appevents.AppEventsLogger
import java.math.BigDecimal
import java.util.Currency
import com.android.billingclient.api.Purchase

@Composable
fun PaywallScreen(onPaid: () -> Unit) {
    val ctx = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

    var userCountry by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uid) {
        userCountry = FirebaseDatabase.getInstance().getReference("users/$uid/country").get().await().getValue(String::class.java)
    }
    val isMexico = CountryUtil.isMexico(ctx, userCountry)

    val products by BillingManager.products.collectAsState()
    val entryProduct = products.firstOrNull { it.productId == "entry_fee" }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        BillingManager.purchaseFlowFinished.collect {
            val purchase = BillingManager.purchases.value.firstOrNull { it.products.contains("entry_fee") }
            if (purchase != null && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                isProcessing = true
                val expiry = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                val updates = mapOf<String, Any>(
                    "entryFeePaidAt" to ServerValue.TIMESTAMP,
                    "isEntryFeePaid" to true,
                    "isPlus" to true,
                    "loginPlusExpiry" to expiry
                )
                FirebaseDatabase.getInstance().reference.child("users/$uid").updateChildren(updates).addOnCompleteListener {
                    val price = if (isMexico) BigDecimal("4.99") else BigDecimal("0.50")
                    val currency = if (isMexico) Currency.getInstance("MXN") else Currency.getInstance("USD")
                    AppEventsLogger.newLogger(ctx).logPurchase(price, currency)
                    isProcessing = false
                    onPaid()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Confirma que no eres un bot y obtén un mes de KupidxPlus: mira a quién le gustas y más.", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isMexico) "Entrada: MXN$4.99 para mantener alejados a los robots." else "$0.50 entry fee - to keep the bots away",
            color = Color.White
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
            Text(if (isProcessing) "Processing..." else { if (isMexico) "Pagar y continuar\n" else "Pay and Continue"})
        }
    }
}