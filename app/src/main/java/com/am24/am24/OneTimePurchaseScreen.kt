package com.am24.am24.ui.purchase

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.razorpay.Checkout
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Defines the different one-time purchase categories, with per-unit price in paise.
 */
enum class PurchaseType(val displayName: String, val unitPricePaise: Int) {
    Swipes("Swipes", 100),
    Compliments("Compliments", 150),
    Boosts("Boosts", 200)
}

/**
 * UI state for the purchase flow: quantity and payment details.
 */
data class PurchaseUiState(
    val selectedQty: Int = 5,
    val orderId: String? = null,
    val keyId: String? = null,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel to call Firebase Cloud Functions and apply the purchase.
 */
class PurchaseViewModel : ViewModel() {
    private val functions = Firebase.functions("asia-south1")
    var uiState by mutableStateOf(PurchaseUiState())
        private set

    fun onQtySelected(qty: Int) {
        uiState = uiState.copy(selectedQty = qty)
    }

    /**
     * Calls `createOneTimeOrder` to generate a Razorpay order.
     */
    fun createOrder(type: PurchaseType) = viewModelScope.launch {
        uiState = uiState.copy(isLoading = true, error = null)
        try {
            val data = mapOf(
                "type" to type.name.lowercase(),
                "quantity" to uiState.selectedQty
            )
            val result = functions
                .getHttpsCallable("createOneTimeOrder")
                .call(data)
                .await().data as Map<*, *>

            uiState = uiState.copy(
                orderId = result["id"] as String,
                keyId   = result["key"] as String,
                isLoading = false
            )
        } catch (e: Exception) {
            uiState = uiState.copy(isLoading = false, error = e.message)
        }
    }

    /**
     * Verifies captured payment and updates the user's counters in RTDB.
     */
    fun verifyAndApplyPayment(type: PurchaseType, paymentId: String) = viewModelScope.launch {
        uiState = uiState.copy(isLoading = true, error = null)
        try {
            val verify = mapOf("paymentId" to paymentId)
            val captured = functions
                .getHttpsCallable("verifyPayment")
                .call(verify)
                .await().data as Boolean
            if (!captured) throw Exception("Payment not captured")

            val uid = FirebaseAuth.getInstance().currentUser?.uid
                ?: throw Exception("No authenticated user")
            val dbRef = FirebaseDatabase.getInstance().getReference("users/$uid")

            when (type) {
                PurchaseType.Swipes ->
                    dbRef.child("swipesInfo/remainingSwipes").get()
                        .addOnSuccessListener { snap ->
                            val current = snap.getValue(Int::class.java) ?: 0
                            dbRef.child("swipesInfo/remainingSwipes")
                                .setValue(current + uiState.selectedQty)
                        }

                PurchaseType.Compliments ->
                    dbRef.child("complimentsLeft").get()
                        .addOnSuccessListener { snap ->
                            val current = snap.getValue(Int::class.java) ?: 0
                            dbRef.child("complimentsLeft")
                                .setValue(current + uiState.selectedQty)
                        }

                PurchaseType.Boosts ->
                    dbRef.child("availableBoosts").get()
                        .addOnSuccessListener { snap ->
                            val current = snap.getValue(Int::class.java) ?: 0
                            dbRef.child("availableBoosts")
                                .setValue(current + uiState.selectedQty)
                        }
            }

            uiState = uiState.copy(isLoading = false, isSuccess = true)
        } catch (e: Exception) {
            uiState = uiState.copy(isLoading = false, error = e.message)
        }
    }
}

/**
 * Screen to run a single one-time purchase of [type].
 */
@Composable
fun OneTimePurchaseScreen(
    type: PurchaseType,
    onBack: () -> Unit,
    viewModel: PurchaseViewModel = viewModel()
) {
    val ui = viewModel.uiState
    val context = LocalContext.current
    val activity = context as? Activity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            backgroundColor = Color(0xFFFF6F00),
            contentColor = Color.White,
            title = { Text("Buy ${type.displayName}", color = Color.White) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Quantity:",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        listOf(5, 10, 20).forEach { qty ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = ui.selectedQty == qty,
                    onClick = { viewModel.onQtySelected(qty) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Color(0xFFFF6F00),
                        unselectedColor = Color.White
                    )
                )
                Text(qty.toString(), color = Color.White)
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.createOrder(type) },
            enabled = !ui.isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFFFF6F00),
                contentColor = Color.White
            )
        ) {
            if (ui.isLoading) CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White
            ) else Text("Create Order")
        }

        ui.orderId?.let { orderId ->
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val checkout = Checkout().apply {
                        setKeyID(ui.keyId!!)
                    }
                    val options = JSONObject().apply {
                        put("order_id", orderId)
                        put("name", "AM24 Bengal")
                        put("description", "${ui.selectedQty} ${type.displayName}")
                        put("currency", "INR")
                        put("amount", type.unitPricePaise * ui.selectedQty)
                    }
                    activity?.let { checkout.open(it, options) }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFFF6F00),
                    contentColor = Color.White
                )
            ) {
                Text("Pay Now")
            }
        }

        ui.isSuccess.takeIf { it }?.let {
            Spacer(Modifier.height(16.dp))
            Text("Purchase successful!", color = Color(0xFFFF6F00))
        }
        ui.error?.let { err ->
            Spacer(Modifier.height(16.dp))
            Text("Error: $err", color = Color.White)
        }
    }
}