package com.am24.am24.billing

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.android.billingclient.api.ProductDetails
import com.google.firebase.auth.FirebaseAuth

class BillingViewModel : ViewModel() {
    val products = BillingManager.products
    val purchases = BillingManager.purchases

    fun purchase(activity: Activity, productDetails: ProductDetails) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        BillingManager.launchBillingFlow(activity, productDetails, obfuscatedAccountId = uid)
    }

    fun restore() {
        BillingManager.restorePurchases()
    }
}