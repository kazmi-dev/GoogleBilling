package com.ai.fusion.character.merge.video.generator.core.billing

import com.android.billingclient.api.Purchase

sealed class BillingPurchaseResult {

    data class PurchaseAcknowledged(val purchase: Purchase) : BillingPurchaseResult()
    data class PurchasePending(val purchase: Purchase) : BillingPurchaseResult()
    data class SubscriptionUpdated(val purchase: Purchase) : BillingPurchaseResult()
    data class PurchaseCancelled(val reason: String = "User cancelled") : BillingPurchaseResult()
    data class NoPurchasesFound(val message: String = "No purchases found") : BillingPurchaseResult()
    data class PurchaseError(
        val responseCode: Int,
        val debugMessage: String
    ) : BillingPurchaseResult()
}
