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
        val subId = intent?.data?.getQueryParameter("subscriptionId") ?: return finish()
        val user  = FirebaseAuth.getInstance().currentUser ?: return finish()

        lifecycleScope.launch {
            val ok = FirebaseFunctions.getInstance()
                .getHttpsCallable("verifyPaypalSubscription")
                .call(mapOf("subscriptionId" to subId))
                .await()
                .data as? Boolean ?: false

            if (ok) {
                val expiry = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.time)
                FirebaseRefs.db.getReference("users")
                    .child(user.uid)
                    .child("premiumStatus")
                    .updateChildren(
                        mapOf(
                            "isPremium"      to true,
                            "subscriptionId" to subId,
                            "expiryDate"     to expiry
                        )
                    )
                Toast.makeText(this@PayPalReturnActivity, "Premium activated!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@PayPalReturnActivity, "Subscription verification failed", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }
}
