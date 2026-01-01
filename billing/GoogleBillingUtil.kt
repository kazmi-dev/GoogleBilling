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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object BillingUtil: PurchasesUpdatedListener {

    private const val BILLING_LOG = "billing_log_123428374852"
    private lateinit var billingClient: BillingClient
    private val billingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    //Always describe your billingProducts here
    //Always describe your billingProducts here
    private val billingProducts = listOf(
//        BillingProduct(productId = "android.test.purchased", productType = ProductType.IN_APP),
        BillingProduct(productId = "unlock_3d_view", productType = ProductType.IN_APP),
        BillingProduct(productId = "life_time_adfree", productType = ProductType.IN_APP),
        BillingProduct(productId = "weekly_subsciption", productType = ProductType.SUBS),
        BillingProduct(productId = "monthly_subscription", productType = ProductType.SUBS),
        BillingProduct(productId = "6_month_subscription", productType = ProductType.SUBS),
        BillingProduct(productId = "yearly_subscription", productType = ProductType.SUBS),
    )

    //Store BillingProduct details for later use
    private val billingProductDetailsList = mutableListOf<ProductDetails>()

    /************* STATE ***************/
    private val _products: MutableStateFlow<List<ProductDetails>> = MutableStateFlow(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products

    private val _purchaseEvents: MutableSharedFlow<BillingEvent> = MutableSharedFlow(extraBufferCapacity = 1)
    val purchaseEvents: SharedFlow<BillingEvent> = _purchaseEvents


    fun initBillingClient(context: Context){
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        startConnection()
    }

    private fun startConnection() {

        if(billingClient.isReady) {
            fetchProducts() /*fetch all purchases*/
            restorePurchases() /*fetch active Purchases*/
            Log.d("2839709234242342342", "onBillingSetupFinished: Already connected")
            return
        }

        billingClient.startConnection(
            object : BillingClientStateListener {

                //handle disconnection logic
                override fun onBillingServiceDisconnected() {
                    Log.d(BILLING_LOG, "onBillingServiceDisconnected: Disconnected.")
                }

                //connection success
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(BILLING_LOG, "onBillingSetupFinished: Success.")
                        fetchProducts() //fetch all purchases
                        restorePurchases() //fetch active Purchases
                    } else {
                        //something went wrong
                        Log.d(BILLING_LOG, "onBillingSetupFinished: Failed -> ${result.debugMessage}")
                    }
                }

            }
        )
    }

    private fun fetchProducts() {
       billingScope.launch {

           val inAppProducts = billingProducts.filter { it.productType.type == ProductType.IN_APP.type }
           val subsProducts = billingProducts.filter { it.productType.type == ProductType.SUBS.type }

           val queryInAppProducts = mutableListOf<QueryProductDetailsParams.Product>()
           val querySubsProducts = mutableListOf<QueryProductDetailsParams.Product>()

           val inAppQueryProductJob = async {
               Log.d("327847289340928402342", "fetchProducts: inApp job started")
               inAppProducts.forEach { product ->
                   queryInAppProducts.add(
                       QueryProductDetailsParams.Product.newBuilder()
                           .setProductId(product.productId)
                           .setProductType(product.productType.type)
                           .build()
                   )
               }
               queryProducts(queryInAppProducts)
           }

           val subsQueryProductJob = async {
               Log.d("327847289340928402342", "fetchProducts: subs job started")
               subsProducts.forEach { product ->
                   querySubsProducts.add(
                       QueryProductDetailsParams.Product.newBuilder()
                           .setProductId(product.productId)
                           .setProductType(product.productType.type)
                           .build()
                   )
               }
               queryProducts(querySubsProducts)
           }

           awaitAll(inAppQueryProductJob, subsQueryProductJob)
           Log.d("327847289340928402342", "fetchProducts: inApp job and subs job completed")

           _products.value = billingProductDetailsList

       }
    }

    private suspend fun queryProducts(queryProducts: List<QueryProductDetailsParams.Product>){
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(queryProducts)
            .build()

        val productBillingResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(params)
        }

        if (productBillingResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            productBillingResult.productDetailsList?.let { productsDetails ->
                Log.d(BILLING_LOG, "queryAvailablePurchases: Products-> $productsDetails")
                billingProductDetailsList.addAll(productsDetails)
            }
        } else {
            _purchaseEvents.tryEmit(BillingEvent.Error(productBillingResult.billingResult.debugMessage))
            Log.d(BILLING_LOG, "queryAvailablePurchases: Failed -> ${productBillingResult.billingResult.debugMessage}")
        }
        Log.d("327847289340928402342", "queryProducs: completed")
    }

    private fun restorePurchases() {
        val productTypes = listOf(ProductType.IN_APP.type, ProductType.SUBS.type)

        productTypes.forEach {
            queryPurchases(it)
        }
    }

    private fun queryPurchases(purchaseType: String) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(purchaseType)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
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

    fun launchPurchase(activity: Activity, productId: String, offerId: String? = null) {

        val productDetails = billingProductDetailsList.find { it.productId == productId }
        if (productDetails == null){
            Log.d(BILLING_LOG, "launchPurchase: No BillingProduct with this id founded.")
            return
        }

        val offerToken = getOfferToken(productDetails, offerId)

        productDetails.let { details ->

            val productDetailsParams = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .apply {
                        if (offerToken != null){
                            setOfferToken(offerToken)
                        }
                    }
                    .build()
            )

            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParams)

            billingClient.launchBillingFlow(activity, params.build())

        }

    }

    private fun getOfferToken(productDetails: ProductDetails, offerId: String?): String? {
        return if (productDetails.productType == ProductType.IN_APP.type){
            val offer = productDetails.oneTimePurchaseOfferDetailsList?.firstOrNull { it.offerId == offerId }
            offer?.offerToken
        }else{
            val offer = productDetails.subscriptionOfferDetails?.firstOrNull { it.offerId == offerId }
            offer?.offerToken
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
                _purchaseEvents.tryEmit(BillingEvent.PurchaseSuccess(purchase.products.first()))
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
    }

}
