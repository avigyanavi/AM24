package com.am24.am24.payments

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.am24.am24.FirebaseRefs
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class PayPalReturnActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink()
    }

    private fun handleDeepLink() {
        val dataUri = intent?.data ?: return finish()
                val orderId = dataUri.getQueryParameter("token")     // PayPal sends ?token=<orderId>
                    ?: dataUri.getQueryParameter("orderId")          // fallback if you ever append it yourself
                val subId   = dataUri.getQueryParameter("subscriptionId")

                val user = FirebaseAuth.getInstance().currentUser ?: return finish()

                when {
                        orderId != null -> handleOrderCapture(orderId, user.uid)
                        subId   != null -> handleSubscriptionVerify(subId, user.uid)
                        else -> finish()
                    }
            }

        private fun handleOrderCapture(orderId: String, uid: String) {
                lifecycleScope.launch {
                        try {
                                val fx = FirebaseFunctions.getInstance("asia-south1")
                                @Suppress("UNCHECKED_CAST")
                                val res = fx.getHttpsCallable("capturePaypalOrder")
                                    .call(mapOf("orderId" to orderId))
                                    .await()
                                    .data as? Map<String, Any?>

                                val status = res?.get("status") as? String ?: ""
                                val ok = (res?.get("ok") as? Boolean == true) || status == "COMPLETED"

                                if (ok) {
                                        // If your CF already updates DB flags/coins, just toast & finish.
                                        // Otherwise update here (example):
                                        // FirebaseRefs.db.getReference("users/$uid/purchases").push().setValue(...)
                                        Toast.makeText(this@PayPalReturnActivity,
                                                "Payment completed!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(this@PayPalReturnActivity,
                                               "Capture failed: $status", Toast.LENGTH_LONG).show()
                                    }
                            } catch (e: Exception) {
                                Toast.makeText(this@PayPalReturnActivity,
                                        "PayPal error: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                finish()
                            }
                    }
            }

        private fun handleSubscriptionVerify(subId: String, uid: String) {
                lifecycleScope.launch {
                        try {
                                val fx = FirebaseFunctions.getInstance("asia-south1")
                                @Suppress("UNCHECKED_CAST")
                                val result = fx.getHttpsCallable("verifyPaypalSubscription")
                                    .call(mapOf("subscriptionId" to subId))
                                    .await()
                                    .data as? Map<String, Any?>

                                val ok = result?.get("valid") as? Boolean ?: false
                                if (ok) {
                                       val expiry = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                            .format(Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.time)
                                       FirebaseRefs.db.getReference("users")
                                            .child(uid)
                                            .child("premiumStatus")
                                            .updateChildren(
                                                       mapOf(
                                                                "isPremium"      to true,
                                                                "subscriptionId" to subId,
                                                                "expiryDate"     to expiry
                                                                    )
                                                            )
                                        Toast.makeText(this@PayPalReturnActivity,
                                                "Premium activated!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(this@PayPalReturnActivity,
                                                "Subscription verification failed", Toast.LENGTH_LONG).show()
                                    }
                            } catch (e: Exception) {
                                Toast.makeText(this@PayPalReturnActivity,
                                      "Subscription check error: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                finish()
                            }
                    }
    }
}
