package com.kazmi.dev.my.secret.media.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.kazmi.dev.my.secret.media.billing.models.BillingEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BillingRepository {

    val products: StateFlow<List<ProductDetails>>
    val billingEvents: SharedFlow<BillingEvent>

    fun initBillingClient(context: Context)
    fun fetchProducts()
    fun restorePurchases()
    fun launchPurchase(activity: Activity, productId: String, offerId: String? = null)
    fun handlePurchase(purchase: Purchase, isRestoredPurchase: Boolean = false)
    fun clearResource()

}

