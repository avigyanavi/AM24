package com.am24.am24.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Billing manager that:
 *  - Queries both INAPP (managed) and SUBS (subscriptions)
 *  - Launches purchase flows for either type
 *  - Restores purchases for both types
 *  - Verifies signatures client-side (keep your public key up to date)
 */
object BillingManager : PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient

    private var inappIds: List<String> = emptyList()
    private var subsIds:  List<String> = emptyList()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())       // INAPP
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _subsProducts = MutableStateFlow<List<ProductDetails>>(emptyList())   // SUBS
    val subsProducts: StateFlow<List<ProductDetails>> = _subsProducts.asStateFlow()

    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())            // all
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()

    /** Back-compat init (INAPP only). */
    fun startConnection(context: Context, ids: List<String>) {
        startConnection(context, inappIds = ids, subsIds = emptyList())
    }

    /** Full init: INAPP + SUBS. */
    fun startConnection(context: Context, inappIds: List<String>, subsIds: List<String>) {
        this.inappIds = inappIds.distinct()
        this.subsIds  = subsIds.distinct()

        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    restorePurchases()
                } else {
                    Log.w("BillingManager", "Billing setup failed: ${result.responseCode}")
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w("BillingManager", "Billing service disconnected")
            }
        })
    }

    private fun queryProducts() {
        // INAPP packs
        if (inappIds.isNotEmpty()) {
            val inappList = inappIds.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }
            val inappParams = QueryProductDetailsParams.newBuilder()
                .setProductList(inappList)
                .build()
            billingClient.queryProductDetailsAsync(inappParams) { result, list ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _products.value = list
                } else {
                    Log.w("BillingManager", "Query INAPP failed: ${result.responseCode}")
                }
            }
        } else {
            _products.value = emptyList()
        }

        // SUBS
        if (subsIds.isNotEmpty()) {
            val subsList = subsIds.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
            val subsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(subsList)
                .build()
            billingClient.queryProductDetailsAsync(subsParams) { result, list ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _subsProducts.value = list
                } else {
                    Log.w("BillingManager", "Query SUBS failed: ${result.responseCode}")
                }
            }
        } else {
            _subsProducts.value = emptyList()
        }
    }

    /** Generic entrypoint. Detects INAPP vs SUBS automatically. */
    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails) {
        val isInapp = productDetails.oneTimePurchaseOfferDetails != null
        val isSubs  = productDetails.subscriptionOfferDetails != null

        when {
            isInapp -> launchInappFlow(activity, productDetails)
            isSubs  -> launchSubsFlow(activity, productDetails)
            else    -> Log.w("BillingManager", "Unknown product type for ${productDetails.productId}")
        }
    }

    /** INAPP (consumable) flow. */
    fun launchInappFlow(activity: Activity, productDetails: ProductDetails) {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    /** SUBS flow (pick first available offer). */
    fun launchSubsFlow(activity: Activity, productDetails: ProductDetails) {
        val offer = productDetails.subscriptionOfferDetails?.firstOrNull()
        if (offer == null) {
            Log.w("BillingManager", "No subscription offers for ${productDetails.productId}")
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offer.offerToken)
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.w("BillingManager", "Purchase failed: ${result.responseCode}")
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val valid = purchases.filter { p ->
            val ok = Security.verifyPurchase(
                Security.PLAY_BILLING_PUBLIC_KEY,
                p.originalJson,
                p.signature
            )
            if (!ok) Log.w("BillingManager", "Invalid signature: ${p.purchaseToken}")
            ok
        }
        if (valid.isEmpty()) return

        _purchases.value = valid

        valid.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!purchase.isAcknowledged) {
                    val ack = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(ack) { /* no-op */ }
                }
                // Only INAPP should be consumed so users can rebuy packs
                val isInappPurchase = purchase.products.any { inappIds.contains(it) }
                if (isInappPurchase) {
                    val consume = ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.consumeAsync(consume) { _, _ -> }
                }
            }
        }
    }

    fun restorePurchases() {
        val inappParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(inappParams) { _, inappPurchases ->
            handlePurchases(inappPurchases)
        }

        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(subsParams) { _, subsPurchases ->
            handlePurchases(subsPurchases)
        }
    }
}