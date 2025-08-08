package com.am24.am24.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple helper around [BillingClient] that handles connecting to Google Play,
 * querying products and processing purchase callbacks. The manager is started
 * from [MyApp] so it is ready when UI needs it.
 */
object BillingManager : PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient
    private var productIds: List<String> = emptyList()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()

    /** Start the billing connection and query available products. */
    fun startConnection(context: Context, ids: List<String>) {
        productIds = ids
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    restorePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // retry is omitted for brevity
            }
        })
    }

    private fun queryProducts() {
        if (productIds.isEmpty()) return
        val productList = productIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _products.value = productDetailsList
            }
        }
    }

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails) {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        _purchases.value = purchases
        purchases.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!purchase.isAcknowledged) {
                    val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(acknowledgeParams) {}
                }
                val consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.consumeAsync(consumeParams) { _, _ -> }
            }
        }
    }

    fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { _, purchases ->
            handlePurchases(purchases)
        }
    }
}