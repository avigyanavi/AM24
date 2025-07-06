@file:OptIn(ExperimentalMaterial3Api::class)
package com.am24.am24.ui.purchase

/* Android / Compose */
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Locale
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService

/* ─────── 1 · Purchase types ─────── */
enum class PurchaseType(val apiType: String,
                        val displayName: String,
                        val unitPricePaise: Int,   // ₹ in paise
                        val unitPriceUsd: Double)  // $ in dollars
{
    Swipes      ("swipes",      "Swipes",       100, 0.99),
    Compliments ("compliments", "Compliments",  150, 1.49),
    Boosts      ("boosts",      "Boosts",       200, 1.99),
    AiMessages  ("aiMessages",  "AI messages",  200, 1.99)
}

/* ─────── 2 · UI state ─────── */
private data class UiState(val selectedQty: Int = 5, val isProcessing: Boolean = false)

/* ─────── 3 · Screen ─────── */
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
    val act        = ctx as Activity
    val isIndia    = remember { isProbablyInIndia(ctx) }

    var ui by remember { mutableStateOf(UiState()) }

    /* ════════════════════ Razorpay helpers (₹) ════════════════════ */
    fun launchRazorpay(orderId: String, keyId: String) {
        val opts = JSONObject().apply {
            put("order_id", orderId)
            put("key", keyId)
            put("name", "Kupidx ${type.displayName}")
            put("description", "${type.displayName} x${ui.selectedQty}")
            put("prefill", JSONObject().apply {
                put("email", FirebaseAuth.getInstance().currentUser?.email)
            })
        }
        checkout.open(act, opts)
    }

    /* ════════════════════ Pay-Pal helpers ($) ════════════════════ */
    /* 1️⃣ — Result-handler: always clear the spinner first */
    val paypalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // whatever happened → we're back in the app
        ui = ui.copy(isProcessing = false)

        val uri = result.data?.data          // null when user just presses “back”
        if (uri == null) {                   // = plain cancel → nothing to do
            Toast.makeText(ctx, "Payment cancelled", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        if (uri.getQueryParameter("oneTime") != "true") return@rememberLauncherForActivityResult
        val orderId = uri.getQueryParameter("orderId") ?: return@rememberLauncherForActivityResult

        /* capture & credit … */
        scope.launch {
            try {
                fx.getHttpsCallable("capturePaypalOrder")
                    .call(mapOf("orderId" to orderId))
                    .await()
                Toast.makeText(ctx,
                    "Added ${ui.selectedQty} ${type.displayName}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Pay-Pal capture failed", Toast.LENGTH_LONG).show()
            } finally {
                navController.popBackStack()
            }
        }
    }

    /* 2️⃣ — Only switch the spinner on **after** we have an approval link */
    fun startPaypalFlow() = scope.launch {
        try {
            val amount = ui.selectedQty * type.unitPriceUsd
            val label  = "${type.apiType}_${ui.selectedQty}"

            val res = fx.getHttpsCallable("createPaypalOrder")
                .call(mapOf("amountUsd" to amount, "label" to label))
                .await().data as Map<*, *>

            val approveUrl = (res["approve"] as? String).orEmpty()
            if (approveUrl.isBlank()) {
                Toast.makeText(ctx, "Pay-Pal order failed, try again", Toast.LENGTH_LONG).show()
                return@launch
            }

            ui = ui.copy(isProcessing = true)                 // <-- move **here**
            val intent = CustomTabsIntent.Builder().build().intent.apply {
                data = Uri.parse(approveUrl)
            }
            paypalLauncher.launch(intent)

        } catch (e: Exception) {
            Toast.makeText(ctx, "Pay-Pal error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /* ════════════════════ common Razorpay callbacks ════════════════════ */
    DisposableEffect(Unit) {
        (act as? PaymentResultListenerHost)?.setPaymentCallbacks(
            onSuccess = { paymentId ->
                fx.getHttpsCallable("verifyPayment")
                    .call(mapOf("paymentId" to paymentId))
                    .addOnSuccessListener { resp ->
                        val ok = resp.data as Boolean
                        if (ok) {
                            val inc = ui.selectedQty.toLong()
                            userRoot.updateChildren(
                                mapOf(qtyField(type) to increment(inc))
                            )
                            Toast.makeText(ctx,
                                "Added $inc ${type.displayName}", Toast.LENGTH_LONG).show()
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

    /* ════════════════════ UI ════════════════════ */
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
            Text(
                if (isIndia)
                    "₹%.2f".format(totalInrPaise / 100.0)
                else
                    "$%.2f".format(totalUsd),
                color = Color.White,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(40.dp))

            Button(
                onClick = {
                    if (isIndia) {          /* Razorpay path */
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
                    } else {                /* Pay-Pal path */
                        startPaypalFlow()
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
                    Text(if (isIndia) "Pay with Razorpay" else "Pay with Pay-Pal")
                }
            }
        }
    }
}

/* ─────── helper: which RTDB field to increment ─────── */
private fun qtyField(t: PurchaseType) = when (t) {
    PurchaseType.Swipes      -> "swipesInfo/remainingSwipes"
    PurchaseType.Compliments -> "availableCompliments"
    PurchaseType.Boosts      -> "availableBoosts"
    PurchaseType.AiMessages  -> "availableAiMessages"
}

/* ─────── helper: quick geo gate ─────── */
fun isProbablyInIndia(ctx: android.content.Context): Boolean {
    val tel       = ctx.getSystemService<TelephonyManager>()
    val simIso    = tel?.simCountryIso ?: ""
    val netIso    = tel?.networkCountryIso ?: ""
    val localeIso = Locale.getDefault().country
    return listOf(simIso, netIso, localeIso).any { it.equals("IN", true) }
}

/* Host-activity contract remains unchanged */
interface PaymentResultListenerHost {
    fun setPaymentCallbacks(onSuccess: (String) -> Unit, onError: (String) -> Unit)
}
