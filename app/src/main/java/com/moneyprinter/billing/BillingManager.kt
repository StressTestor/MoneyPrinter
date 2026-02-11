package com.moneyprinter.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class IAPProduct(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val priceLabel: String,
    val isConsumable: Boolean,
)

val IAP_PRODUCTS = listOf(
    IAPProduct("starter_pack", "Starter Pack", "\uD83C\uDF81", "500 gems + 2x boost (24h) + \$1M cash", "$0.99", true),
    IAPProduct("remove_ads", "Remove Ads", "\uD83D\uDEAB", "No more ads forever", "$9.99", false),
    IAPProduct("gems_small", "100 Gems", "\uD83D\uDC8E", "A handful of gems", "$0.99", true),
    IAPProduct("gems_medium", "300 Gems", "\uD83D\uDC8E\uD83D\uDC8E", "A nice stash of gems", "$2.99", true),
    IAPProduct("gems_large", "1000 Gems", "\uD83D\uDC8E\uD83D\uDC8E\uD83D\uDC8E", "A mountain of gems", "$7.99", true),
)

class BillingManager(
    context: Context,
    private val onPurchaseComplete: (String) -> Unit,
) : PurchasesUpdatedListener {

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private var isConnected = false

    init {
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    isConnected = true
                    restorePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnected = false
            }
        })
    }

    fun purchaseProduct(activity: Activity, productId: String) {
        if (!isConnected) {
            startConnection()
            return
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && detailsList.isNotEmpty()) {
                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(detailsList[0])
                                .build()
                        )
                    )
                    .build()
                billingClient.launchBillingFlow(activity, flowParams)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        purchase.products.forEach { productId ->
            onPurchaseComplete(productId)
        }

        val product = IAP_PRODUCTS.find { it.id in purchase.products }
        if (product?.isConsumable == true) {
            CoroutineScope(Dispatchers.IO).launch {
                billingClient.consumePurchase(
                    ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                )
            }
        } else if (!purchase.isAcknowledged) {
            CoroutineScope(Dispatchers.IO).launch {
                billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                )
            }
        }
    }

    private fun restorePurchases() {
        if (!isConnected) return
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it) }
            }
        }
    }

    fun destroy() {
        billingClient.endConnection()
    }
}
