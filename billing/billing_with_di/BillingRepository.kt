import android.app.Activity
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BillingRepository {

    val products: StateFlow<List<ProductDetails>>
    val billingEvents: SharedFlow<BillingEvent>

    fun initBillingClient()
    fun fetchProducts()
    fun restorePurchases()
    fun launchPurchase(activity: Activity, productId: String, offerId: String? = null)
    fun handlePurchase(purchase: Purchase, isRestoredPurchase: Boolean = false)


}
