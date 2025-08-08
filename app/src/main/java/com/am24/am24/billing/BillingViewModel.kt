package com.am24.am24.billing

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.android.billingclient.api.ProductDetails

class BillingViewModel : ViewModel() {
    val products = BillingManager.products
    val purchases = BillingManager.purchases

    fun purchase(activity: Activity, productDetails: ProductDetails) {
        BillingManager.launchBillingFlow(activity, productDetails)
    }

    fun restore() {
        BillingManager.restorePurchases()
    }
}