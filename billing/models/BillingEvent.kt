package com.kazmi.dev.my.secret.media.billing.models

sealed class BillingEvent {
    data class PurchaseSuccess(val productId: String) : BillingEvent()
    data class PurchaseRestore(val productId: String) : BillingEvent()
    data class PurchasePending(val productId: String) : BillingEvent()
    data class Error(val message: String) : BillingEvent()

}
