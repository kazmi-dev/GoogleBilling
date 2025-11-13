import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleBillingUtil @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    companion object {

        //For logging purpose
        private const val BILLING_LOG = "billing_log_123428374532"

        //Always describe your products here
        private val products = listOf(
            Product(productId = "android.test.purchased", productType = ProductType.IN_APP.type),
        )
    }

    //Store Product details for later use
    private val productDetailsList = mutableListOf<ProductDetails>()

    private var reconnectionCount = 0

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val billingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var onGetProducts: ((List<ProductDetails>) -> Unit)? = null
    private var onProductPurchased: ((purchaseId: String) -> Unit)? = null

    fun setOnGetProductsListener(onGetProducts: (List<ProductDetails>) -> Unit) {
        this.onGetProducts = onGetProducts
    }
    fun setOnProductPurchasedListener(onProductPurchased: (purchaseId: String) -> Unit) {
        this.onProductPurchased = onProductPurchased
    }

    init {
        startConnection()
    }

    fun startConnection() {

        if(billingClient.isReady) {
            billingScope.launch(Dispatchers.Main) {
                //fetch all purchases
                queryAvailableProducts()
            }
            //fetch active Purchases
            queryActivePurchases()
            Log.d("2839709234242342342", "onBillingSetupFinished: $onGetProducts")
            return
        }

        billingClient.startConnection(
            object : BillingClientStateListener {

                //handle disconnection logic
                override fun onBillingServiceDisconnected() {
                    Log.d(BILLING_LOG, "onBillingServiceDisconnected: Disconnected.")
                    //apply reconnection logic
                    reconnectToBillingService()
                }

                //connection success
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(BILLING_LOG, "onBillingSetupFinished: Success.")

                        billingScope.launch(Dispatchers.Main) {
                            //fetch all purchases
                            queryAvailableProducts()
                        }
                        //fetch active Purchases
                        queryActivePurchases()
                        Log.d("2839709234242342342", "onBillingSetupFinished: $onGetProducts")

                    } else {
                        //something went wrong
                        Log.d(BILLING_LOG, "onBillingSetupFinished: Failed -> ${result.debugMessage}")
                    }
                }

            }
        )
    }

    private fun reconnectToBillingService() {

        if (reconnectionCount >= 3){
            return
        }

        reconnectionCount++

        Handler(Looper.getMainLooper()).postDelayed({
            startConnection()
        }, 3000)
    }

    private suspend fun queryAvailableProducts() {

        val queryProducts = mutableListOf<QueryProductDetailsParams.Product>()
        products.forEach { product ->
            queryProducts.add(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(product.productId)
                    .setProductType(product.productType)
                    .build()
            )
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(queryProducts)
            .build()

        val productBillingResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(params)
        }

        if (productBillingResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            productBillingResult.productDetailsList?.let { productsDetails ->
                Log.d(BILLING_LOG, "queryAvailablePurchases: Products-> $productsDetails")
                productDetailsList.clear()
                productDetailsList.addAll(productsDetails)
                onGetProducts?.invoke(productDetailsList)
            }
        } else {
            Log.d(BILLING_LOG, "queryAvailablePurchases: Failed -> ${productBillingResult.billingResult.debugMessage}")
        }

    }

    private fun queryActivePurchases() {
        val productTypes = listOf(ProductType.IN_APP.type, ProductType.SUBS.type)

        productTypes.forEach {
            queryPurchases(it)
        }
    }

    private fun queryPurchases(purchaseType: String) {
        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(purchaseType)
            .build()

        billingClient.queryPurchasesAsync(subsParams) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(BILLING_LOG, "queryPurchases: Success: $purchaseType")

                if (purchases.isEmpty()) {
                    Log.d(BILLING_LOG, "queryPurchases: No Products purchased.")
                    return@queryPurchasesAsync
                }

                purchases.forEach { purchase ->
                    handlePurchase(purchase)
                }
            } else {
                Log.d(BILLING_LOG, "queryPurchases: Failed -> ${result.debugMessage}")
            }
        }
    }

    fun launchPurchase(activity: Activity, productId: String) {

        val productDetails = productDetailsList.find { it.productId == productId }

        if (productDetails == null) {
            Log.d(BILLING_LOG, "launchPurchase: No Product with this id founded.")
            return
        }

        productDetails.let { details ->

            val productDetailsParams = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .apply {
                        if (details.productType == ProductType.SUBS.type) {
                            setOfferToken(details.subscriptionOfferDetails?.first()?.offerToken ?: "")
                        } else {
                            setOfferToken(details.oneTimePurchaseOfferDetails?.offerToken ?: "")
                        }
                    }
                    .build()
            )

            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParams)

            billingClient.launchBillingFlow(activity, params.build())

        }

    }

    private fun handlePurchase(purchase: Purchase?) {
        if (purchase == null) {
            Log.d(BILLING_LOG, "handlePurchase: Purchase is null")
            return
        }

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                Log.d(BILLING_LOG, "handlePurchase: Item Purchased.")
                //AcknowledgePurchase
                acknowledgePurchase(purchase)
            }

            Purchase.PurchaseState.PENDING -> {
                //handle pending purchase
                Log.d(BILLING_LOG, "handlePurchase: Pending State.")
            }

            Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                //handle pending purchase
                Log.d(BILLING_LOG, "handlePurchase: Unspecified State.")

            }

            else -> {
                //handle pending purchase
                Log.d(BILLING_LOG, "handlePurchase: Unspecified State. else one")
            }
        }

    }

    private fun acknowledgePurchase(purchase: Purchase) {

        if (purchase.isAcknowledged) {
            Log.d(BILLING_LOG, "handlePurchase: Purchase is already acknowledged.")
            return
        }

        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(BILLING_LOG, "handlePurchase: Acknowledge Success")
                //unlock required features
                onProductPurchased?.invoke(purchase.products.first())
            } else {
                Log.d(BILLING_LOG, "handlePurchase: Acknowledge Failed -> ${result.debugMessage}")
            }
        }
    }

    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: List<Purchase?>?
    ) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(BILLING_LOG, "onPurchasesUpdated: User Canceled.")
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.d(BILLING_LOG, "onPurchasesUpdated: Item already owned.")
            }

            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
                Log.d(BILLING_LOG, "onPurchasesUpdated: Item unavailable.")
            }
        }
    }

    fun releaseResources(){
        billingScope.cancel()
        billingClient.endConnection()
        onGetProducts = null
        onProductPurchased = null
    }

}
