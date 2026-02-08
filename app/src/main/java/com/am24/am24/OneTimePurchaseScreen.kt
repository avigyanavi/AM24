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
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.launch
import androidx.fragment.app.FragmentActivity
import com.am24.am24.billing.BillingViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.am24.am24.FirebaseRefs
import com.am24.am24.R
import com.am24.am24.safePopBackStack

enum class PurchaseType(
    val apiType: String,
    val displayName: String
) {
    Swipes      ("swipes",      "Swipes"),
    Boosts      ("boosts",      "Boosts"),
    Compliments ("compliments", "Compliments"),
    AiMessages  ("aiMessages",  "AI messages");

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
    val uid        = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userDoc    = FirebaseRefs.userProfiles.document(uid)
    val act        = ctx as FragmentActivity

    var ui by remember { mutableStateOf(UiState()) }

    val billingViewModel: BillingViewModel = viewModel()
    val products by billingViewModel.products.collectAsState()
    val purchases by billingViewModel.purchases.collectAsState()
    val selectedSku = remember(type, ui.selectedQty) { type.skuFor(ui.selectedQty) }
    val playPrice = remember(products, selectedSku) {
        products.firstOrNull { it.productId == selectedSku }
            ?.oneTimePurchaseOfferDetails
            ?.formattedPrice
    }

    val selectedProduct = products.firstOrNull { it.productId == selectedSku }
    val displayPrice = when {
        playPrice != null -> playPrice
        else -> ""
    }

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
            Text(
                displayPrice,
                color = Color.White,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(40.dp))

            Button(
                onClick = {
                    if (selectedProduct != null) {
                        billingViewModel.purchase(act, selectedProduct)
                        ui = ui.copy(isProcessing = true)
                    } else {
                        Toast.makeText(ctx, "Product not available ($selectedSku)", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !ui.isProcessing,
                modifier = Modifier.fillMaxWidth().height(dimensionResource(id = R.dimen.btn_height))
            ) {
                if (ui.isProcessing) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Text("Pay with Google Play")
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
            userDoc.update(mapOf(qtyField(type) to FieldValue.increment(qty.toLong())))
            Toast.makeText(ctx, "Added $qty ${type.displayName}", Toast.LENGTH_LONG).show()
            navController.safePopBackStack()
            ui = ui.copy(isProcessing = false)
        }
    }
}

private fun qtyField(t: PurchaseType) = when (t) {
    PurchaseType.Swipes      -> "swipesInfo.remainingSwipes"
    PurchaseType.Boosts      -> "availableBoosts"
    PurchaseType.Compliments -> "availableCompliments"
    PurchaseType.AiMessages  -> "availableAiMessages"
}