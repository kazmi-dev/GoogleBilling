import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleBilling@Inject constructor(
    @ApplicationContext private val context: Context
): PurchasesUpdatedListener {

    companion object{
        private const val BILLING_LOG = "GoogleBilling_46712349827394"
    }
    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()
    private var activityRef: WeakReference<Activity>? = null
    private var billingPurchaseResult: ((BillingPurchaseResult) -> Unit)? = null

    fun setBillingPurchaseResult(callback: (BillingPurchaseResult)-> Unit){
        billingPurchaseResult = callback
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when{
            billingResult.responseCode == BillingResponseCode.OK ->{
                purchases?.forEach {purchase->
                    when(purchase.purchaseState){
                        PurchaseState.PURCHASED ->{
                            if (!purchase.isAcknowledged){
                                acknowledgePurchase(purchase){
                                    Log.d(BILLING_LOG, "onPurchasesUpdated: Purchase Acknowledged")
                                    //handle further actions
                                    billingPurchaseResult?.invoke(BillingPurchaseResult.PurchaseAcknowledged(purchase))
                                }
                            }else{
                                Log.d(BILLING_LOG, "onPurchasesUpdated: Purchase Already Acknowledged")
                                billingPurchaseResult?.invoke(BillingPurchaseResult.PurchaseAcknowledged(purchase))
                            }
                        }
                        PurchaseState.PENDING->{
                            Log.d(BILLING_LOG, "onPurchasesUpdated: Purchase Pending")
                            billingPurchaseResult?.invoke(BillingPurchaseResult.PurchasePending(purchase))
                        }
                        PurchaseState.UNSPECIFIED_STATE->{
                            Log.d(BILLING_LOG, "onPurchasesUpdated: Purchase Unspecified State")
                            billingPurchaseResult?.invoke(BillingPurchaseResult.NoPurchasesFound())
                        }
                        else ->{
                            Log.d(BILLING_LOG, "onPurchasesUpdated: Purchase Error")
                            billingPurchaseResult?.invoke(BillingPurchaseResult.NoPurchasesFound())
                        }
                    }
                }?: kotlin.run {
                    Log.d(BILLING_LOG, "onPurchasesUpdated: No Purchases Found")
                    billingPurchaseResult?.invoke(BillingPurchaseResult.NoPurchasesFound())
                }
            }
            else ->{
                Log.d(BILLING_LOG, "onPurchasesUpdated: Error: code: ${billingResult.responseCode}, message: ${billingResult.debugMessage}")
                billingPurchaseResult?.invoke(BillingPurchaseResult.PurchaseError(billingResult.responseCode, billingResult.debugMessage))
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase, onAcknowledged: ()-> Unit) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams){billingResult->
            when{
                billingResult.responseCode == BillingResponseCode.OK -> onAcknowledged()
                else-> {
                    Log.d(BILLING_LOG, "onPurchaseAcknowledge: Error acknowledging: code: ${billingResult.responseCode}, message: ${billingResult.debugMessage}")
                    billingPurchaseResult?.invoke(BillingPurchaseResult.PurchaseError(billingResult.responseCode, billingResult.debugMessage))
                }
            }
        }

    }

    fun startConnection(){
        billingClient.startConnection(object: BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                Log.d(BILLING_LOG, "onBillingServiceDisconnected: Billing Client Disconnected")
            }
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                when{
                    billingResult.responseCode == BillingResponseCode.OK->{
                        Log.d(BILLING_LOG, "onBillingSetupFinished: Billing Client Connected")
                        //handle further actions
                    }
                    else-> Log.d(BILLING_LOG, "onBillingSetupFinished: Error connecting to Billing Client: code: ${billingResult.responseCode}, message: ${billingResult.debugMessage}")
                }
            }
        })
    }

    suspend fun queryPurchases(products: List<Product>): List<ProductDetails> {

        //create products
        val productList : MutableList<QueryProductDetailsParams.Product> = mutableListOf()
        products.forEach {product->
            val productBuild = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(product.productId)
                .setProductType(product.productType.type)
                .build()
            productList.add(productBuild)
        }

        //query products params
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)

        //query products
        val result = withContext(Dispatchers.IO){
            billingClient.queryProductDetails(params.build())
        }

        when{
            result.billingResult.responseCode == BillingResponseCode.OK -> {
                return if(result.productDetailsList != null){
                    Log.d(BILLING_LOG, "queryPurchases: products found: ${result.productDetailsList!!}")
                    result.productDetailsList!!
                }else{
                    Log.d(BILLING_LOG, "queryPurchases: No products found")
                    emptyList()
                }
            }
            else->{
                Log.d(BILLING_LOG, "queryPurchases: Error querying products: code: ${result.billingResult.responseCode}, message: ${result.billingResult.debugMessage}")
                return emptyList()
            }
        }

    }

    fun queryPurchases(productType: ProductType){
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(productType.type)
            .build()

        billingClient.queryPurchasesAsync(params){result, purchases->
            when{
                result.responseCode == BillingResponseCode.OK->{
                    purchases.forEach {purchase->
                        if (purchase.isAcknowledged){
                            Log.d(BILLING_LOG, "onPurchasesUpdated: Purchase Already Acknowledged")
                            billingPurchaseResult?.invoke(BillingPurchaseResult.PurchaseAcknowledged(purchase))
                        }else{
                            acknowledgePurchase(purchase){
                                Log.d(BILLING_LOG, "onPurchasesUpdated: Purchase Acknowledged")
                                //handle further actions
                                billingPurchaseResult?.invoke(BillingPurchaseResult.PurchaseAcknowledged(purchase))
                            }
                        }
                    }
                }
                else->{
                    Log.d(BILLING_LOG, "onPurchasesUpdated: Error querying purchases: code: ${result.responseCode}, message: ${result.debugMessage}")
                    billingPurchaseResult?.invoke(BillingPurchaseResult.PurchaseError(result.responseCode, result.debugMessage))
                }
            }
        }
    }

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails){
        activityRef = WeakReference(activity)

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .apply {
                val offerToken = when(productDetails.productType){
                    BillingClient.ProductType.INAPP -> productDetails.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
                    else -> productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
                }

                if (offerToken != null){
                    Log.d(BILLING_LOG, "launchBillingFlow: Offer Token found: $offerToken")
                    this.setOfferToken(offerToken)
                }
            }
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        activityRef?.get()?.let {activityNew->
            billingClient.launchBillingFlow(activityNew, billingFlowParams)
        }
    }

    fun releaseBilling(){
        activityRef?.clear()
        activityRef = null
        billingClient.endConnection()
    }

}
