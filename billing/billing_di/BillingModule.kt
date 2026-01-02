package com.kazmi.dev.my.secret.media.billing.billing_di

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.kazmi.dev.my.secret.media.billing.models.BillingProduct
import com.kazmi.dev.my.secret.media.billing.BillingRepository
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
    fun provideBillingRepository(@ApplicationContext context: Context): BillingRepository {

        val billingProducts = listOf(
            BillingProduct(
                productId = "android.test.purchased",
                productType = BillingClient.ProductType.INAPP
            ),
        )

        return BillingImpl(
            context,
            billingProducts = billingProducts
        )
    }


}