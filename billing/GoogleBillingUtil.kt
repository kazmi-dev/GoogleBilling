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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleBillingUtil @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "GoogleBilling"
    }

    private val productsConfig = listOf(
        BillingProduct("android.test.purchased", BillingClient.ProductType.INAPP)
    )

    private val billingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val billingClient: BillingClient =
        BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    /* -------------------- STATE -------------------- */

    private val _products =
        MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products

    private val _billingEvents =
        MutableSharedFlow<BillingEvent>(extraBufferCapacity = 1)
    val billingEvents = _billingEvents

    init {
        startConnection()
    }

    /* -------------------- CONNECTION -------------------- */

    private fun startConnection() {
        if (billingClient.isReady) {
            fetchProducts()
            restorePurchases()
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    fetchProducts()
                    restorePurchases()
                } else {
                    _billingEvents.tryEmit(
                        BillingEvent.Error(result.debugMessage)
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "Billing disconnected")
            }
        })
    }

    /* -------------------- PRODUCTS -------------------- */

    private fun fetchProducts() {
        billingScope.launch {
            val queryList = productsConfig.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it.productId)
                    .setProductType(it.productType)
                    .build()
            }

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(queryList)
                .build()

            val result = billingClient.queryProductDetails(params)

            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _products.value = result.productDetailsList ?: emptyList()
            } else {
                _billingEvents.tryEmit(
                    BillingEvent.Error(result.billingResult.debugMessage)
                )
            }
        }
    }

    /* -------------------- PURCHASE FLOW -------------------- */

    fun launchPurchase(activity: Activity, productId: String, offerToken: String? = null) {
        val details = _products.value.find {
            it.productId == productId
        } ?: return

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply {
                if (offerToken!= null){
                    setOfferToken(offerToken)
                }
            }
            .build()

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(productDetailsParams)
            )
            .build()

        billingClient.launchBillingFlow(activity, params)
    }

    /* -------------------- PURCHASE HANDLING -------------------- */

    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: List<Purchase>?
    ) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach { handlePurchase(it) }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _billingEvents.tryEmit(BillingEvent.Error("User cancelled purchase"))
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!purchase.isAcknowledged) {
                    acknowledge(purchase)
                }
            }

            Purchase.PurchaseState.PENDING -> {
                _billingEvents.tryEmit(
                    BillingEvent.PurchasePending(
                        purchase.products.first()
                    )
                )
            }

            else -> Unit
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _billingEvents.tryEmit(
                    BillingEvent.PurchaseSuccess(
                        purchase.products.first()
                    )
                )
            }
        }
    }

    /* -------------------- RESTORE -------------------- */

    private fun restorePurchases() {
        val types = listOf(
            BillingClient.ProductType.INAPP,
            BillingClient.ProductType.SUBS
        )

        types.forEach { type ->
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(type)
                    .build()
            ) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.forEach { handlePurchase(it) }
                }
            }
        }
    }

    /* -------------------- CLEANUP -------------------- */

    fun release() {
        billingScope.cancel()
        billingClient.endConnection()
    }
}
