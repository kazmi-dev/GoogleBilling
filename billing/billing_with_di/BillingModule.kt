import android.content.Context
import com.android.billingclient.api.BillingClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    @Provides
    @Singleton
    fun provideBillingRepository(@ApplicationContext context: Context): BillingRepository{
        return BillingImpl(
            context,
            billingProducts =  listOf(
                BillingProduct(productId = "android.test.purchased", productType = BillingClient.ProductType.INAPP),
            )
        )
    }


}
