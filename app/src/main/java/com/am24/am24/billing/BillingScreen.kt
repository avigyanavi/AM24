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

/**
 * Simple UI that displays available products and allows the user to purchase
 * or restore them. It relies on [BillingViewModel] for state.
 */
@Composable
fun BillingScreen(viewModel: BillingViewModel = viewModel()) {
    val products by viewModel.products.collectAsState()
    val purchases by viewModel.purchases.collectAsState()
    val activity = LocalContext.current as Activity

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        products.forEach { product ->
            Button(onClick = { viewModel.purchase(activity, product) }) {
                Text(product.productId)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(onClick = { viewModel.restore() }, modifier = Modifier.padding(top = 16.dp)) {
            Text("Restore purchases")
        }

        purchases.forEach { purchase ->
            Text(text = "Owned: ${purchase.products.joinToString()}")
        }
    }
}