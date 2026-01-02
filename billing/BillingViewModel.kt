package com.kazmi.dev.my.secret.media.billing

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillingViewModel@Inject constructor(
    private val billingRepository: BillingRepository
): ViewModel() {

    val startUI: MutableStateFlow<Boolean> = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            startUI.value = false
        }
    }

    val products = billingRepository.products
    val billingEvents = billingRepository.billingEvents


    fun launchBillingFlow(activity: Activity, productId: String, offerId: String? = null) {
        billingRepository.launchPurchase(activity, productId, offerId)
    }

     override fun onCleared() {
        super.onCleared()
        billingRepository.clearResource()
    }

}

