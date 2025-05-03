package com.am24.am24

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.functions.FirebaseFunctions

@Composable
fun SubscriptionScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val currentUser = FirebaseAuth.getInstance().currentUser ?: return
    val userRef = FirebaseRefs.db.getReference("users").child(currentUser.uid)
    val scope = rememberCoroutineScope()

    var isPaymentInitiated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Checkout.preload(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Upgrade to Premium",
            color = Color(0xFF00bf63),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Unlock exclusive features with a Premium subscription!",
            color = Color.White,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (!isPaymentInitiated) {
                    isPaymentInitiated = true
                    scope.launch {
                        initiateRazorpayPayment(
                            context = context,
                            activity = activity,
                            amount = 99900, // ₹999.00 in paise
                            onSuccess = { paymentId ->
                                Log.d("SubscriptionScreen", "Payment success callback received with paymentId: $paymentId")
                                // Verify payment before updating Firebase
                                scope.launch {
                                    try {
                                        val isPaymentValid = verifyPayment(paymentId)
                                        if (isPaymentValid) {
                                            Log.d("SubscriptionScreen", "Payment verified successfully for paymentId: $paymentId")
                                            val expiryDate = calculateExpiryDate()
                                            userRef.child("premiumStatus").updateChildren(
                                                mapOf(
                                                    "isPremium" to true,
                                                    "expiryDate" to expiryDate,
                                                    "paymentId" to paymentId
                                                )
                                            ).addOnSuccessListener {
                                                Log.d("SubscriptionScreen", "Firebase updated successfully for paymentId: $paymentId")
                                                Toast.makeText(context, "Premium upgraded successfully!", Toast.LENGTH_LONG).show()
                                                navController.previousBackStackEntry?.savedStateHandle?.set("premiumUpdated", true)
                                                navController.popBackStack()
                                            }.addOnFailureListener { e ->
                                                Log.e("SubscriptionScreen", "Failed to update Firebase: ${e.message}")
                                                Toast.makeText(context, "Failed to update premium status.", Toast.LENGTH_LONG).show()
                                                isPaymentInitiated = false
                                            }
                                        } else {
                                            Log.e("SubscriptionScreen", "Payment verification failed for paymentId: $paymentId")
                                            Toast.makeText(context, "Payment verification failed.", Toast.LENGTH_LONG).show()
                                            isPaymentInitiated = false
                                        }
                                    } catch (e: Exception) {
                                        Log.e("SubscriptionScreen", "Payment verification error: ${e.message}")
                                        Toast.makeText(context, "Payment verification error: ${e.message}", Toast.LENGTH_LONG).show()
                                        isPaymentInitiated = false
                                    }
                                }
                            },
                            onError = { errorMessage ->
                                Log.e("SubscriptionScreen", "Payment error: $errorMessage")
                                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                                isPaymentInitiated = false
                            }
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00)),
            enabled = !isPaymentInitiated
        ) {
            Text(
                text = if (isPaymentInitiated) "Processing..." else "Upgrade for ₹999",
                color = Color.White,
                fontSize = 16.sp
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = {
                navController.popBackStack()
            }
        ) {
            Text("Cancel", color = Color(0xFF00bf63), fontSize = 16.sp)
        }
    }
}

suspend fun verifyPayment(paymentId: String): Boolean {
    return try {
        val functions = FirebaseFunctions.getInstance()
        val data = mapOf("paymentId" to paymentId)
        val result = functions
            .getHttpsCallable("verifyPayment")
            .call(data)
            .await()
        val isValid = result.data as? Boolean ?: false
        Log.d("SubscriptionScreen", "Cloud Function verifyPayment result for paymentId $paymentId: $isValid")
        isValid
    } catch (e: Exception) {
        Log.e("SubscriptionScreen", "Error calling Cloud Function: ${e.message}")
        throw e
    }
}

fun initiateRazorpayPayment(
    context: Context,
    activity: Activity,
    amount: Int,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val checkout = Checkout()
    checkout.setKeyID("rzp_test_PEBgJvcT9jIT7O")

    try {
        val options = JSONObject().apply {
            put("name", "AM24 Premium")
            put("description", "Premium Subscription")
            put("currency", "INR")
            put("amount", amount)
            put("prefill", JSONObject().apply {
                put("email", FirebaseAuth.getInstance().currentUser?.email ?: "")
                put("contact", "")
            })
            put("theme", JSONObject().apply {
                put("color", "#FF6F00")
            })
            // Add timeout for UPI payments (in seconds)
            put("timeout", 60) // 60 seconds timeout
            // Configure UPI-specific handling
            put("method", JSONObject().apply {
                put("upi", JSONObject().apply {
                    put("flow", "intent") // Use UPI intent flow
                    put("cancel_behaviour", "dismiss") // Dismiss checkout on cancellation
                })
            })
        }

        // Set callbacks in KupidXAppActivity
        if (activity is KupidXAppActivity) {
            activity.setPaymentCallbacks(onSuccess, onError)
        }

        checkout.open(activity, options)
    } catch (e: Exception) {
        onError("Error initiating payment: ${e.message}")
    }
}

// Calculate expiry date (e.g., 1 year from now)
fun calculateExpiryDate(): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.YEAR, 1) // 1-year subscription
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(calendar.time)
}