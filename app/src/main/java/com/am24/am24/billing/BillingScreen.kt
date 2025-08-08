package com.am24.am24.billing

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.billingclient.api.ProductDetails
import java.time.Period
import java.time.format.DateTimeParseException

@Composable
fun BillingScreen(viewModel: BillingViewModel = viewModel()) {
    val inapps     by viewModel.products.collectAsState()                 // INAPP ProductDetails
    val subs       by BillingManager.subsProducts.collectAsState()        // SUBS ProductDetails
    val purchases  by viewModel.purchases.collectAsState()
    val activity   = LocalContext.current as Activity

    Column(Modifier.fillMaxSize().padding(16.dp)) {

        Text("Subscriptions")
        Spacer(Modifier.height(8.dp))

        if (subs.isEmpty()) {
            Text("— none loaded —")
        } else {
            subs.forEach { pd ->
                val priceLabel = firstSubsPriceLabel(pd) ?: "(no offer)"
                Button(onClick = { BillingManager.launchSubsFlow(activity, pd) }) {
                    Text("${pd.productId} • $priceLabel")
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("INAPP Packs")
        Spacer(Modifier.height(8.dp))

        if (inapps.isEmpty()) {
            Text("— none loaded —")
        } else {
            inapps.forEach { pd ->
                val p = pd.oneTimePurchaseOfferDetails
                val label = p?.formattedPrice ?: "(price N/A)"
                Button(onClick = { viewModel.purchase(activity, pd) }) {
                    Text("${pd.productId} • $label")
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { viewModel.restore() }) { Text("Restore purchases") }

        Spacer(Modifier.height(12.dp))
        purchases.forEach { p -> Text("Owned: ${p.products.joinToString()}") }
    }
}

/** Human-ish label for first subscription offer, e.g. “₹99.00 / month”. */
private fun firstSubsPriceLabel(pd: ProductDetails): String? {
    val offer = pd.subscriptionOfferDetails?.firstOrNull() ?: return null
    val phase = offer.pricingPhases.pricingPhaseList.firstOrNull() ?: return null
    val periodStr = phase.billingPeriod?.let { prettyIsoPeriod(it) } ?: ""
    return if (periodStr.isNotEmpty()) "${phase.formattedPrice} / $periodStr" else phase.formattedPrice
}

/** Convert ISO-8601 periods like “P1M”, “P1Y”, “P1W” to “month”, “year”, “week”. */
private fun prettyIsoPeriod(iso: String): String {
    return try {
        val p = Period.parse(iso) // supports PnY, PnM, PnW, PnD (weeks via Period in 8.0.0 aren’t direct; fallback)
        when {
            p.years == 1 && p.months == 0 -> "year"
            p.years > 1  && p.months == 0 -> "${p.years} years"
            p.months == 1 && p.years == 0 -> "month"
            p.months > 1  && p.years == 0 -> "${p.months} months"
            else -> iso
        }
    } catch (_: DateTimeParseException) {
        // Billing can also return “P1W” which Period.parse in Java time pre-API 26 can stumble on;
        // quick manual fallbacks:
        when (iso) {
            "P1W" -> "week"
            "P2W" -> "2 weeks"
            else  -> iso
        }
    }
}
