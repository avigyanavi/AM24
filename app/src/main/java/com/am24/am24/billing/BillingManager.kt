package com.am24.am24.billing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.android.billingclient.api.*
import com.am24.am24.BuildConfig
import com.am24.am24.FirebaseRefs
import com.am24.am24.MyApp
import com.facebook.appevents.AppEventsLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

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

    private val plusSubIds = setOf("plus", "plus-monthly", "plus-annual")
    private val premiumSubIds = setOf("premium", "premium-monthly", "premium-annual")

    private val plusOneTimeIds = setOf("kupidx_plus_one_month", "kupidx_plus_one_year")
    private val premiumOneTimeIds = setOf("kupidx_premium_one_month", "kupidx_premium_one_year")
    private val oneTimeDurations = mapOf(
        "kupidx_plus_one_month" to TimeUnit.DAYS.toMillis(30),
        "kupidx_plus_one_year" to TimeUnit.DAYS.toMillis(365),
        "kupidx_premium_one_month" to TimeUnit.DAYS.toMillis(30),
        "kupidx_premium_one_year" to TimeUnit.DAYS.toMillis(365),
        "entry_fee" to TimeUnit.DAYS.toMillis(30),
        )

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
        obfuscatedAccountId: String
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
        obfuscatedAccountId: String
    ) {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()

        val builder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))

        builder.setObfuscatedAccountId(obfuscatedAccountId)

        billingClient.launchBillingFlow(activity, builder.build())
    }

    /** SUBS (pick a reasonable offer/base plan). */
    fun launchSubsFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String? = null,
        basePlanId: String? = null,
        obfuscatedAccountId: String
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
            _purchaseFlowFinished.tryEmit(Unit)
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offer.offerToken)
            .build()

        val builder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))

        .setObfuscatedAccountId(obfuscatedAccountId)

        billingClient.launchBillingFlow(activity, builder.build())
    }

    fun openPlaySubscriptionManagement(
        context: Context,
        productId: String? = null
    ) {
        val uri = if (productId.isNullOrBlank()) {
            Uri.parse("https://play.google.com/store/account/subscriptions")
        } else {
            Uri.parse(
                "https://play.google.com/store/account/subscriptions" +
                        "?sku=${Uri.encode(productId)}&package=${Uri.encode(context.packageName)}"
            )
        }

        // Prefer the Play Store app; fall back to a browser if unavailable
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage("com.android.vending")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
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
            _purchaseFlowFinished.tryEmit(Unit)
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.w("BillingManager", "Purchase failed: ${result.responseCode}")
            _purchaseFlowFinished.tryEmit(Unit)
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
        if (valid.isEmpty()) {
            _purchaseFlowFinished.tryEmit(Unit)
            return
        }

        _purchases.value = valid

        valid.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!purchase.isAcknowledged) {
                    try {
                        val logger = AppEventsLogger.newLogger(MyApp.instance)
                        purchase.products.forEach { productId ->
                            when {
                                productId == "entry_fee" ||
                                        plusOneTimeIds.contains(productId) ||
                                        premiumOneTimeIds.contains(productId) -> {
                                    val params = Bundle().apply {
                                        putString("product_id", productId)
                                    }
                                    logger.logEvent("user_paid", params)
                                }
                                plusSubIds.contains(productId) || premiumSubIds.contains(productId) -> {
                                    val params = Bundle().apply {
                                        putString("product_id", productId)
                                    }
                                    logger.logEvent("user_paid_subscription", params)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("BillingManager", "Failed to log Facebook purchase event", e)
                    }
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

                // 🔸 Authoritative server flip for entry_fee (non-blocking)
                                if (purchase.products.contains("entry_fee")) {
                                        val data = hashMapOf(
                                                "purchaseToken" to purchase.purchaseToken,
                                                "productId" to "entry_fee",
                                                "packageName" to BuildConfig.APPLICATION_ID
                                                    )
                                        try {
                                                functions.getHttpsCallable("confirmEntryFee")
                                                    .call(data)
                                                    .addOnFailureListener {
                                                            Log.w("BillingManager", "confirmEntryFee failed", it)
                                                        }
                                            } catch (e: Exception) {
                                                Log.w("BillingManager", "confirmEntryFee call error", e)
                                            }
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
        val purchased = all.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }

        val activeSubProductIds = purchased
            .flatMap { it.products }
            .toSet()

        val isPlusSub = activeSubProductIds.any { it in plusSubIds }
        val isPremiumSub = activeSubProductIds.any { it in premiumSubIds }

        val oneTimeExpiries = purchased.mapNotNull { purchase ->
            val productId = purchase.products.firstOrNull { oneTimeDurations.containsKey(it) }
            val duration = productId?.let { oneTimeDurations[it] }
            if (productId == null || duration == null) return@mapNotNull null
            productId to (purchase.purchaseTime + duration)
        }

        val plusOneTimeExpiry = oneTimeExpiries
            .filter { plusOneTimeIds.contains(it.first) }
            .maxOfOrNull { it.second }

        val premiumOneTimeExpiry = oneTimeExpiries
            .filter { premiumOneTimeIds.contains(it.first) }
            .maxOfOrNull { it.second }

        val now = System.currentTimeMillis()
        val hasPlusOneTime = plusOneTimeExpiry != null && plusOneTimeExpiry > now
        val hasPremiumOneTime = premiumOneTimeExpiry != null && premiumOneTimeExpiry > now

        val hasEntryFeePurchase = purchased.any { it.products.contains("entry_fee") }
        val entryFeeExpiry = oneTimeExpiries
            .filter { it.first == "entry_fee" }
            .maxOfOrNull { it.second }

        val nextRenewal = listOfNotNull(
            plusOneTimeExpiry?.takeIf { it > now },
            premiumOneTimeExpiry?.takeIf { it > now },
            entryFeeExpiry?.takeIf { it > now },
            ).maxOrNull()

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = FirebaseRefs.db.reference.child("users/$uid")

        val premiumActiveFromPurchases = isPremiumSub || hasPremiumOneTime

        if (hasEntryFeePurchase) {
            applyTierEntitlements(
                userRef,
                plus = true,
                premium = premiumActiveFromPurchases,
                nextRenewal = nextRenewal,
            )
            return
        }
        userRef.get()
            .addOnSuccessListener { snapshot ->
                val entryFeePaid =
                    snapshot.child("isEntryFeePaid").getValue(Boolean::class.java) == true
                val entryFeePaidAt =
                    snapshot.child("entryFeePaidAt").getValue(Long::class.java) ?: 0L
                val rewardExpiry =
                    snapshot.child("loginPlusExpiry").getValue(Long::class.java) ?: 0L
                val rewardActive = rewardExpiry > now
                val entryFeeExpiry = if (entryFeePaidAt > 0L) {
                    entryFeePaidAt + TimeUnit.DAYS.toMillis(30)
                } else 0L
                val entryFeeActive = entryFeePaid && (rewardActive || entryFeeExpiry > now)
                if (entryFeePaid && !entryFeeActive && entryFeeExpiry > 0L && entryFeeExpiry <= now) {
                    userRef.child("isEntryFeePaid").setValue(false)
                }
                val finalPlus =
                    isPlusSub || rewardActive || entryFeeActive || hasPlusOneTime || hasPremiumOneTime
                val finalPremium = premiumActiveFromPurchases
                applyTierEntitlements(
                    userRef,
                    plus = finalPlus,
                    premium = finalPremium,
                    nextRenewal = nextRenewal,
                )
            }
            .addOnFailureListener {
                val finalPlus = isPlusSub || hasPlusOneTime || hasPremiumOneTime
                val finalPremium = premiumActiveFromPurchases
                applyTierEntitlements(
                    userRef,
                    plus = finalPlus,
                    premium = finalPremium,
                    nextRenewal = nextRenewal,
                )
            }
    }

    private fun applyTierEntitlements(
        userRef: DatabaseReference,
        plus: Boolean,
        premium: Boolean,
        nextRenewal: Long? = null,
    ) {
        val updates = mutableMapOf<String, Any>(
            "isPlus" to plus,
            "isPremium" to premium,
            "priority" to premium,
            )

        if (plus || premium) {
            updates["swipesInfo/remainingSwipes"] =
                if (premium) Int.MAX_VALUE else 50
            updates["availableCompliments"] = if (premium) 5 else 3
            if (premium) updates["availableAiMessages"] = 2
        }
        updates["nextRenewal"] = nextRenewal ?: 0L
        userRef.updateChildren(updates)
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