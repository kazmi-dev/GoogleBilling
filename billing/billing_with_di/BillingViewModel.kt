import android.app.Activity
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BillingViewModel@Inject constructor(
    private val billingRepository: BillingRepository
): ViewModel() {


    val products = billingRepository.products
    val billingEvents = billingRepository.billingEvents


    fun launchBillingFlow(activity: Activity, productId: String, offerId: String? = null) {
        billingRepository.launchPurchase(activity, productId, offerId)
    }



}
