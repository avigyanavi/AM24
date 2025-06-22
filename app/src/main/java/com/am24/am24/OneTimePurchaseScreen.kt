@file:OptIn(ExperimentalMaterial3Api::class)
package com.am24.am24.ui.purchase

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue.increment
import com.google.firebase.functions.FirebaseFunctions
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

// ──────────────── 1. PurchaseType enum ────────────────
enum class PurchaseType(val apiType: String, val displayName: String) {
    Swipes("swipes", "Swipes"),
    Compliments("compliments", "Compliments"),
    Boosts("boosts", "Boosts"),
    AiMessages("aiMessages", "AI messages")
}

// ──────────────── 2. UI state ────────────────
private data class UiState(val selectedQty: Int = 5, val isProcessing: Boolean = false)

// ──────────────── 3. Screen ────────────────
@Composable
fun OneTimePurchaseScreen(
    type: PurchaseType,
    navController: NavController,
    onBack: () -> Unit
) {
    val ctx       = LocalContext.current
    val scope     = rememberCoroutineScope()
    val uid       = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRoot  = FirebaseDatabase.getInstance().getReference("users/$uid")
    val functions = FirebaseFunctions.getInstance("asia-south1")
    val checkout  = remember { Checkout().apply { setKeyID("rzp_live_DsoxJLeiCw940M") } }
    val activity  = ctx as Activity

    var ui by remember { mutableStateOf(UiState()) }

    // 1️⃣ Create order & launch Razorpay
    fun startPayment(orderId: String, keyId: String) {
        val opts = JSONObject().apply {
            put("order_id", orderId)
            put("key", keyId)
            put("name", "Kupidx ${type.displayName}")
            put("description", "${type.displayName} x${ui.selectedQty}")
            put("prefill", JSONObject().apply {
                put("email", FirebaseAuth.getInstance().currentUser?.email)
            })
        }
        checkout.open(activity, opts)
    }

    // 2️⃣ Handle payment callbacks via host activity
    DisposableEffect(Unit) {
        (activity as? PaymentResultListenerHost)?.setPaymentCallbacks(
            onSuccess = { paymentId ->
                // verify with cloud function
                functions.getHttpsCallable("verifyPayment")
                    .call(mapOf("paymentId" to paymentId))
                    .addOnSuccessListener { resp ->
                        val ok = resp.data as Boolean
                        if (ok) {
                            val incQty = ui.selectedQty.toLong()
                            userRoot.updateChildren(
                                mapOf(
                                    when(type) {
                                        PurchaseType.Swipes      -> "swipesInfo/remainingSwipes"
                                        PurchaseType.Compliments -> "availableCompliments"
                                        PurchaseType.Boosts      -> "availableBoosts"
                                        PurchaseType.AiMessages  -> "availableAiMessages"
                                    } to increment(incQty)
                                )
                            )
                            Toast.makeText(ctx, "Added $incQty ${type.displayName}", Toast.LENGTH_LONG).show()
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
            onError = { msg ->
                Toast.makeText(ctx, "Payment failed: $msg", Toast.LENGTH_LONG).show()
                ui = ui.copy(isProcessing = false)
            }
        )
        onDispose {
            (activity as? PaymentResultListenerHost)?.setPaymentCallbacks({}, {})
        }
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
            Text(
                "Select quantity",
                fontSize = 18.sp,
                color = Color.White
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(5, 10, 20).forEach { qty ->
                    FilterChip(
                        selected = ui.selectedQty == qty,
                        onClick  = { ui = ui.copy(selectedQty = qty) },
                        label    = { Text(qty.toString()) }
                    )
                }
            }
            Spacer(Modifier.height(40.dp))

            Button(
                onClick = {
                    scope.launch {
                        ui = ui.copy(isProcessing = true)
                        try {
                            val res = functions
                                .getHttpsCallable("createOneTimeOrder")
                                .call(mapOf("type" to type.apiType, "quantity" to ui.selectedQty))
                                .await().data as Map<*, *>
                            startPayment(res["id"] as String, res["key"] as String)
                        } catch(e: Exception) {
                            ui = ui.copy(isProcessing = false)
                            Toast.makeText(ctx, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !ui.isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (ui.isProcessing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text("Pay with Razorpay")
                }
            }
        }
    }
}

// Host activity interface to wire up callbacks
interface PaymentResultListenerHost {
    fun setPaymentCallbacks(onSuccess: (String)->Unit, onError: (String)->Unit)
}
