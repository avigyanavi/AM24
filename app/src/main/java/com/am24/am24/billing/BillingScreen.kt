package com.am24.am24.billing

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun BillingScreen(viewModel: BillingViewModel = viewModel()) {
    val inapps by viewModel.products.collectAsState()
    val subs by BillingManager.subsProducts.collectAsState()
    val purchases by viewModel.purchases.collectAsState()
    val activity = LocalContext.current as Activity

    Column(Modifier.fillMaxSize().padding(16.dp)) {

        Text("Subscriptions")
        Spacer(Modifier.height(8.dp))
        subs.forEach { pd ->
            Button(onClick = { BillingManager.launchSubsFlow(activity, pd) }) {
                Text(pd.productId)
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))
        Text("INAPP Packs")
        Spacer(Modifier.height(8.dp))
        inapps.forEach { pd ->
            Button(onClick = { viewModel.purchase(activity, pd) }) {
                Text(pd.productId)
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { viewModel.restore() }) { Text("Restore purchases") }

        purchases.forEach { p -> Text("Owned: ${p.products.joinToString()}") }
    }
}
