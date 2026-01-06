package com.kazmi.dev.my.secret.media.billing.billing_di

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.kazmi.dev.my.secret.media.billing.models.BillingEvent
import com.kazmi.dev.my.secret.media.billing.models.BillingProduct
import com.kazmi.dev.my.secret.media.billing.BillingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BillingImpl @Inject constructor(
    private val appContext: Context,
    private val billingProducts: List<BillingProduct>
) : BillingRepository, PurchasesUpdatedListener {

    companion object {
        private const val TAG = "billing_log_123428374852"
    }

    private lateinit var billingClient: BillingClient
    private val billingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Products Management */
    private var billingProductsDetailList: MutableList<ProductDetails> = emptyList()
    private val _products: MutableStateFlow<List<ProductDetails>> =
        MutableStateFlow<List<ProductDetails>>(emptyList())
    override val products: StateFlow<List<ProductDetails>>
        get() = _products
    private val _billingEvents: MutableSharedFlow<BillingEvent> =
        MutableSharedFlow(extraBufferCapacity = 1)
    override val billingEvents: SharedFlow<BillingEvent>
        get() = _billingEvents


    /** Billing Client */
    init {
        initBillingClient(appContext)
    }

    /** Billing Connection */
    override fun initBillingClient(context: Context) {
        val pendingPurchaseParams = PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enableAutoServiceReconnection()
            .enablePendingPurchases(pendingPurchaseParams)
            .build()

        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingServiceDisconnected() {
                    Log.d(TAG, "onBillingServiceDisconnected: Billing Disconnected")
                }

                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    Log.d(TAG, "onBillingSetupFinished: Billing Connected")
                    fetchProducts()
                    restorePurchases()
                }
            }
        )
    }


    /** Product Fetching */
    override fun fetchProducts() {
        billingScope.launch {
            val inAppProducts = billingProducts.filter { it.productType == BillingClient.ProductType.INAPP }
            val subsProducts = billingProducts.filter { it.productType == BillingClient.ProductType.SUBS }

            val inAppQueryJob = async {
                if (inAppProducts.isEmpty()) {
                    Log.d(TAG, "fetchProducts: No In-App Products")
                    return@async emptyList()
                }
                queryProducts(inAppProducts)
            }

            val subsQueryJob = async {
                if (subsProducts.isEmpty()) {
                    Log.d(TAG, "fetchProducts: No Subs Products")
                    return@async emptyList()
                }
                queryProducts(subsProducts)
            }

            val productList = inAppQueryJob.await() + subsQueryJob.await()
            Log.d(TAG, "fetchProducts: job completed: productList -> $productList")
            billingProductsDetailList.addAll(productList)
            _products.value = productList
        }
    }
    private suspend fun queryProducts(queryProducts: List<BillingProduct>): List<ProductDetails> {
        val productsList = mutableListOf<QueryProductDetailsParams.Product>()
        queryProducts.forEach { product ->
            productsList.add(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(product.productId)
                    .setProductType(product.productType)
                    .build()
            )
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productsList)
            .build()

        // billingClient.queryProductDetails is already safe for coroutines in Billing v5+
        val productBillingResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(params)
        }

        return if (productBillingResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "queryProducts: Success")
            productBillingResult.productDetailsList ?: emptyList()
        } else {
            val debugMessage = productBillingResult.billingResult.debugMessage
            Log.d(TAG, "queryProducts: Failed -> $debugMessage")
            _billingEvents.tryEmit(BillingEvent.Error(debugMessage))
            emptyList()
        }
    }


    /** Restore Purchases */
    override fun restorePurchases() {
        billingScope.launch {
            val fetchInAppPurchasesJob = async {
                Log.d(TAG, "restorePurchases: fetch inApp purchases job started")
                queryPurchases(BillingClient.ProductType.INAPP)
            }
            val fetchSubsPurchasesJob = async {
                Log.d(TAG, "restorePurchases: fetch subs purchases job started")
                queryPurchases(BillingClient.ProductType.SUBS)
            }

            val purchases = fetchInAppPurchasesJob.await() + fetchSubsPurchasesJob.await()
            Log.d(TAG, "restorePurchases: purchases job complete -> $purchases")
            purchases.forEach { purchase ->
                handlePurchase(purchase, isRestoredPurchase = true)
            }
        }
    }
    private suspend fun queryPurchases(productType: String): List<Purchase> {
        val queryPurchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(productType)
            .build()

        val result = billingClient.queryPurchasesAsync(queryPurchasesParams)

        return if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "queryPurchases: Success")
            result.purchasesList
        } else {
            Log.d(TAG, "queryPurchases: Failed -> ${result.billingResult.debugMessage}")
            emptyList()
        }
    }


    /** Purchase Flow */
    override fun launchPurchase(activity: Activity, productId: String, offerId: String?) {

        val productDetails = billingProductsDetailList.find { it.productId == productId }
        if (productDetails == null) {
            Log.d(TAG, "launchPurchase: No BillingProduct with this id founded.")
            _billingEvents.tryEmit(BillingEvent.Error("No BillingProduct with this id founded."))
            return
        }

        val offerToken = getOfferToken(productDetails, offerId)

        val productDetailsParams = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .apply {
                    if (offerToken != null)
                        setOfferToken(offerToken)
                }
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParams)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)

    }
    private fun getOfferToken(productDetails: ProductDetails, offerId: String?): String? {
        return if (offerId != null) {
            getOfferTokenByOfferId(productDetails, offerId)
        } else {
            getOfferTokenByMinPrice(productDetails)
        }
    }
    private fun getOfferTokenByOfferId(productDetails: ProductDetails, offerId: String): String? {
        return when (productDetails.productType) {
            BillingClient.ProductType.INAPP -> {
                productDetails.oneTimePurchaseOfferDetailsList?.find { it.offerId == offerId }?.offerToken
            }

            else -> {
                productDetails.subscriptionOfferDetails?.find { it.offerId == offerId }?.offerToken
            }
        }
    }
    private fun getOfferTokenByMinPrice(productDetails: ProductDetails): String? {
        return when (productDetails.productType) {
            BillingClient.ProductType.INAPP -> {
                productDetails.oneTimePurchaseOfferDetailsList?.minByOrNull { it.priceAmountMicros }?.offerToken
            }

            else -> {
                //if not inapp than must be sub -_-
                productDetails.subscriptionOfferDetails?.minByOrNull { offer -> offer.pricingPhases.pricingPhaseList.minOf { phase -> phase.priceAmountMicros } }?.offerToken
            }
        }
    }


    /** Handle Purchase */
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase?>?
    ) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach { purchase ->
                if (purchase == null) return
                //handle Purchase
                handlePurchase(purchase)
            }?: run {
                    Log.d(TAG, "onPurchasesUpdated: Purchases is null")
                    _billingEvents.tryEmit(BillingEvent.Error("Purchases is null"))
                }
        } else {
            Log.d(TAG, "onPurchasesUpdated: ${billingResult.debugMessage}")
            _billingEvents.tryEmit(BillingEvent.Error(billingResult.debugMessage))
        }
    }
    override fun handlePurchase(purchase: Purchase, isRestoredPurchase: Boolean) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                Log.d(TAG, "handlePurchase: Item Purchased.")
                //acknowledgePurchase
                acknowledgePurchase(purchase, isRestoredPurchase)
            }

            Purchase.PurchaseState.PENDING -> {
                Log.d(TAG, "handlePurchase: Pending State.")
                _billingEvents.tryEmit(BillingEvent.PurchasePending(purchase.products.firstOrNull()?: "null"))
            }

            Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                Log.d(TAG, "handlePurchase: Unspecified State.")
            }
        }
    }
    private fun acknowledgePurchase(purchase: Purchase, isRestoredPurchase: Boolean) {
        if (purchase.isAcknowledged) return
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "handlePurchase: Acknowledge Success")
                val event = if (isRestoredPurchase)
                    BillingEvent.PurchaseRestore(purchase.products.first())
                else
                    BillingEvent.PurchasePending(purchase.products.first())
                _billingEvents.tryEmit(event)
            } else {
                Log.d(TAG, "handlePurchase: Acknowledge Failed -> ${billingResult.debugMessage}")
                _billingEvents.tryEmit(BillingEvent.Error(billingResult.debugMessage))
            }
        }
    }


    /** Clear Resource */
    override fun clearResource() {
        billingScope.cancel()
        billingClient.endConnection()
    }

}

