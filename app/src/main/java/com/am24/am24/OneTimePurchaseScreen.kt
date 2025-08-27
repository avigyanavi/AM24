@file:OptIn(ExperimentalMaterial3Api::class)
package com.am24.am24.ui.purchase

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue.increment
import com.google.firebase.functions.FirebaseFunctions
import com.razorpay.Checkout
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import androidx.fragment.app.FragmentActivity
import com.am24.am24.CountryUtil
import com.am24.am24.billing.BillingViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.am24.am24.R

enum class PurchaseType(
    val apiType: String,
    val displayName: String,
    val unitPricePaise: Int,
    val unitPriceUsd: Double
) {
    Swipes      ("swipes",      "Swipes",       100, 0.29),
    Boosts      ("boosts",      "Boosts",       200, 1.99),
    Compliments ("compliments", "Compliments",  150, 0.29),
    AiMessages  ("aiMessages",  "AI messages",  200, 1.99);

    fun skuFor(qty: Int): String = "${apiType.lowercase()}_${qty}"
    fun skuPrefix(): String = "${apiType.lowercase()}_"
}

private data class UiState(val selectedQty: Int = 5, val isProcessing: Boolean = false)

@Composable
fun OneTimePurchaseScreen(
    type: PurchaseType,
    navController: NavController,
    onBack: () -> Unit
) {
    val ctx        = LocalContext.current
    val scope      = rememberCoroutineScope()
    val uid        = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRoot   = FirebaseDatabase.getInstance().getReference("users/$uid")
    val fx         = FirebaseFunctions.getInstance("asia-south1")
    val checkout   = remember { Checkout().apply { setKeyID("rzp_live_DsoxJLeiCw940M") } }
    val act        = ctx as FragmentActivity

    var userCountry by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uid) {
        userCountry = userRoot.child("country").get().await().getValue(String::class.java)
    }
    val isIndia = CountryUtil.useRazorpay(ctx, userCountry)
    var ui by remember { mutableStateOf(UiState()) }

    val billingViewModel: BillingViewModel = viewModel()
    val products by billingViewModel.products.collectAsState()
    val purchases by billingViewModel.purchases.collectAsState()

    fun launchRazorpay(orderId: String, keyId: String) {
        val opts = JSONObject().apply {
            put("order_id", orderId)
            put("key", keyId)
            put("name", "AM24")
            put("description", "${type.displayName} x${ui.selectedQty}")
        }
        checkout.open(act, opts)
    }

    DisposableEffect(Unit) {
        (act as? PaymentResultListenerHost)?.setPaymentCallbacks(
            onSuccess = { paymentId ->
                fx.getHttpsCallable("verifyPayment")
                    .call(mapOf("paymentId" to paymentId))
                    .addOnSuccessListener { resp ->
                        val ok = resp.data as Boolean
                        if (ok) {
                            val inc = ui.selectedQty.toLong()
                            userRoot.updateChildren(mapOf(qtyField(type) to increment(inc)))
                            Toast.makeText(ctx,"Added $inc ${type.displayName}", Toast.LENGTH_LONG).show()
                            navController.popBackStack()
                        } else {
                            Toast.makeText(ctx, "Verification failed", Toast.LENGTH_LONG).show()
                        }
                        ui = ui.copy(isProcessing = false)
                    }
                    .addOnFailureListener {
                        Toast.makeText(ctx, "Verify error", Toast.LENGTH_LONG).show()
                        ui = ui.copy(isProcessing = false)
                    }
            },
            onError = {
                Toast.makeText(ctx, "Payment failed: $it", Toast.LENGTH_LONG).show()
                ui = ui.copy(isProcessing = false)
            }
        )
        onDispose { (act as? PaymentResultListenerHost)?.setPaymentCallbacks({}, {}) }
    }

    val totalInrPaise = ui.selectedQty * type.unitPricePaise
    val totalUsd      = ui.selectedQty * type.unitPriceUsd

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Buy ${type.displayName}") },
                colors = centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                }
            )
        },
        containerColor = Color(0xFF121212)
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Text("Select quantity", fontSize = 18.sp, color = Color.White)
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(5, 10, 20).forEach { qty ->
                    FilterChip(
                        selected = ui.selectedQty == qty,
                        onClick = { ui = ui.copy(selectedQty = qty) },
                        label = { Text(qty.toString()) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
//            Text(
//                if (isIndia) "₹%.2f".format(totalInrPaise / 100.0)
//                else "$%.2f".format(totalUsd),
//                color = Color.White,
//                fontSize = 16.sp
//            )
//            Spacer(Modifier.height(40.dp))

            Button(
                onClick = {
                    if (isIndia) {
                        scope.launch {
                            ui = ui.copy(isProcessing = true)
                            try {
                                val res = fx.getHttpsCallable("createOneTimeOrder")
                                    .call(mapOf(
                                        "type"     to type.apiType,
                                        "quantity" to ui.selectedQty
                                    )).await().data as Map<*, *>
                                launchRazorpay(res["id"] as String, res["key"] as String)
                            } catch (e: Exception) {
                                ui = ui.copy(isProcessing = false)
                                Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        val sku = type.skuFor(ui.selectedQty) // e.g. swipes_10
                        val product = products.firstOrNull { it.productId == sku }
                        if (product != null) {
                            billingViewModel.purchase(act, product)
                            ui = ui.copy(isProcessing = true)
                        } else {
                            Toast.makeText(ctx, "Product not available ($sku)", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !ui.isProcessing,
                modifier = Modifier.fillMaxWidth().height(dimensionResource(id = R.dimen.btn_height))
            ) {
                if (ui.isProcessing) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Text(if (isIndia) "Pay with Razorpay" else "Pay with Google Play")
                }
            }
        }
    }

    // Grant by actual productId purchased (e.g., swipes_10 → +10)
    LaunchedEffect(purchases) {
        val prefix = type.skuPrefix()
        val p = purchases.firstOrNull { it.products.any { pid -> pid.startsWith(prefix) } }
        if (p != null) {
            val productId = p.products.first { it.startsWith(prefix) }
            val qty = productId.removePrefix(prefix).toIntOrNull() ?: 1
            userRoot.updateChildren(mapOf(qtyField(type) to increment(qty.toLong())))
            Toast.makeText(ctx, "Added $qty ${type.displayName}", Toast.LENGTH_LONG).show()
            navController.popBackStack()
            ui = ui.copy(isProcessing = false)
        }
    }
}

private fun qtyField(t: PurchaseType) = when (t) {
    PurchaseType.Swipes      -> "swipesInfo/remainingSwipes"
    PurchaseType.Boosts      -> "availableBoosts"
    PurchaseType.Compliments -> "availableCompliments"
    PurchaseType.AiMessages  -> "availableAiMessages"
}

interface PaymentResultListenerHost {
    fun setPaymentCallbacks(onSuccess: (String) -> Unit, onError: (String) -> Unit)
}