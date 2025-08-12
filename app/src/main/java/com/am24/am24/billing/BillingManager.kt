package com.am24.am24.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.am24.am24.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Billing manager (BillingClient 8.0.0):
 *  • Queries INAPP packs and SUBS
 *  • Launches purchase flows for either type
 *  • Restores purchases (INAPP + SUBS)
 *  • Client-side signature verification
 *  • Consumes INAPP so packs can be repurchased
 */
object BillingManager : PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient

    private var inappIds: List<String> = emptyList()
    private var subsIds:  List<String> = emptyList()

    private val plusIds    = setOf("plus", "plus-monthly", "plus-annual")
    private val premiumIds = setOf("premium", "premium-monthly", "premium-annual")

    private val functions = FirebaseFunctions.getInstance("asia-south1")

    // INAPP product details
    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    // SUBS product details
    private val _subsProducts = MutableStateFlow<List<ProductDetails>>(emptyList())
    val subsProducts: StateFlow<List<ProductDetails>> = _subsProducts.asStateFlow()

    // All purchases (INAPP + SUBS)
    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()

    // Signal when a purchase flow finishes (success or cancel)
    private val _purchaseFlowFinished = MutableSharedFlow<Unit>()
    val purchaseFlowFinished: SharedFlow<Unit> = _purchaseFlowFinished.asSharedFlow()

    /** Back-compat init (INAPP only). */
    fun startConnection(context: Context, ids: List<String>) {
        startConnection(context, inappIds = ids, subsIds = emptyList())
    }

    /** Full init: INAPP + SUBS. */
    fun startConnection(context: Context, inappIds: List<String>, subsIds: List<String>) {
        this.inappIds = inappIds.distinct()
        this.subsIds  = subsIds.distinct()

        val pendingParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts() // INAPP pending flows
            .build()

        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(pendingParams)
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    restorePurchases()
                    _purchaseFlowFinished.tryEmit(Unit)
                } else {
                    if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                        Log.w("BillingManager", "Purchase failed: ${result.responseCode}")
                    }
                    _purchaseFlowFinished.tryEmit(Unit)
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w("BillingManager", "Billing service disconnected")
            }
        })
    }

    private fun queryProducts() {
        // INAPP
        if (inappIds.isNotEmpty()) {
            val list = inappIds.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(list)
                .build()

            // Billing 8.0.0 signature: (BillingResult, QueryProductDetailsResult)
            billingClient.queryProductDetailsAsync(params) { br, res ->
                if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                    _products.value = res.productDetailsList
                } else {
                    Log.w("BillingManager", "Query INAPP failed: ${br.responseCode}")
                }
            }
        } else _products.value = emptyList()

        // SUBS
        if (subsIds.isNotEmpty()) {
            val list = subsIds.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(list)
                .build()

            // Billing 8.0.0 signature: (BillingResult, QueryProductDetailsResult)
            billingClient.queryProductDetailsAsync(params) { br, res ->
                if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                    _subsProducts.value = res.productDetailsList
                } else {
                    Log.w("BillingManager", "Query SUBS failed: ${br.responseCode}")
                }
            }
        } else _subsProducts.value = emptyList()
    }

    /** Generic entrypoint. Detects INAPP vs SUBS automatically. */
    fun launchBillingFlow(
        activity: Activity,
        productDetails: ProductDetails,
        obfuscatedAccountId: String? = null
    ) {
        val isInapp = productDetails.oneTimePurchaseOfferDetails != null
        val isSubs  = productDetails.subscriptionOfferDetails != null

        when {
            isInapp -> launchInappFlow(activity, productDetails, obfuscatedAccountId)
            isSubs  -> launchSubsFlow(activity, productDetails, obfuscatedAccountId = obfuscatedAccountId)
            else    -> Log.w("BillingManager", "Unknown product type for ${productDetails.productId}")
        }
    }

    /** INAPP (consumable) – we use separate SKUs for quantities. */
    fun launchInappFlow(
        activity: Activity,
        productDetails: ProductDetails,
        obfuscatedAccountId: String? = null
    ) {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()

        val builder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))

        if (!obfuscatedAccountId.isNullOrBlank()) {
            builder.setObfuscatedAccountId(obfuscatedAccountId)
        }

        billingClient.launchBillingFlow(activity, builder.build())
    }

    /** SUBS (pick a reasonable offer/base plan). */
    fun launchSubsFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String? = null,
        basePlanId: String? = null,
        obfuscatedAccountId: String? = null
    ) {
        val offer = when {
            !offerToken.isNullOrBlank() ->
                productDetails.subscriptionOfferDetails?.find { it.offerToken == offerToken }
            !basePlanId.isNullOrBlank() ->
                productDetails.subscriptionOfferDetails?.find { it.basePlanId == basePlanId }
            else -> pickBestOffer(productDetails)
        }
        if (offer == null) {
            Log.w("BillingManager", "No matching subscription offer for ${productDetails.productId}")
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offer.offerToken)
            .build()

        val builder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))

        if (!obfuscatedAccountId.isNullOrBlank()) {
            builder.setObfuscatedAccountId(obfuscatedAccountId)
        }

        billingClient.launchBillingFlow(activity, builder.build())
    }

    /** Choose an offer; here we pick the lowest current price phase. */
    private fun pickBestOffer(pd: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
        val offers = pd.subscriptionOfferDetails ?: return null
        return offers.minByOrNull { offer ->
            val phases = offer.pricingPhases.pricingPhaseList
            val phase = phases.lastOrNull() ?: phases.first()
            phase.priceAmountMicros
        }
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
                // Consume INAPP so packs can be rebought
                val isInappPurchase = purchase.products.any { inappIds.contains(it) }
                if (isInappPurchase) {
                    val consume = ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.consumeAsync(consume) { _, _ -> }
                }
            }
            verifyPurchaseOnServer(purchase)
        }
        applyEntitlementsFrom(valid)
    }

    fun restorePurchases() {
        val inappParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        // SUBS
        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(inappParams) { br1, list1 ->
            val ownedInapp =
                if (br1.responseCode == BillingClient.BillingResponseCode.OK) list1 else emptyList()
            if (br1.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w("BillingManager", "Restore INAPP failed: ${br1.responseCode}")
            }

            billingClient.queryPurchasesAsync(subsParams) { br2, list2 ->
                val ownedSubs =
                    if (br2.responseCode == BillingClient.BillingResponseCode.OK) list2 else emptyList()
                if (br2.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w("BillingManager", "Restore SUBS failed: ${br2.responseCode}")
                }

                handlePurchases(ownedInapp + ownedSubs)
            }
        }
    }


    private fun applyEntitlementsFrom(all: List<Purchase>) {
        val activeSubProductIds = all
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .flatMap { it.products }
            .toSet()

        val isPlus = activeSubProductIds.any { it in plusIds }
        val isPremium = activeSubProductIds.any { it in premiumIds }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseDatabase.getInstance().reference
                .child("users/$uid")
                .updateChildren(mapOf("isPlus" to isPlus, "isPremium" to isPremium))
        }
    }

    private fun verifyPurchaseOnServer(purchase: Purchase) {
        val productId = purchase.products.firstOrNull() ?: return
        val type = when {
            inappIds.contains(productId) -> "inapp"
            subsIds.contains(productId) -> "subs"
            else -> return
        }
        val data = hashMapOf(
            "purchaseToken" to purchase.purchaseToken,
            "productId" to productId,
            "packageName" to BuildConfig.APPLICATION_ID,
            "productType" to type,
        )
        functions.getHttpsCallable("verifyPlayPurchase").call(data)
            .addOnFailureListener { e ->
                Log.w("BillingManager", "Server verify failed", e)
            }

        if (type == "subs") {
            syncSubscriptionOnServer(purchase)
        }
    }

    private fun syncSubscriptionOnServer(purchase: Purchase) {
        val productId = purchase.products.firstOrNull() ?: return
        if (!subsIds.contains(productId)) return
        val data = hashMapOf(
            "purchaseToken" to purchase.purchaseToken,
            "productId" to productId,
            "packageName" to BuildConfig.APPLICATION_ID,
        )
        functions.getHttpsCallable("syncPlaySubscription").call(data)
            .addOnFailureListener { e ->
                Log.w("BillingManager", "Subscription sync failed", e)
            }
    }
}