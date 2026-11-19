package com.zyphite.coma.billing

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Coma Plus: one auto-renewing Google Play subscription, 99 baht a month, that lifts the
 * one-host limit of the free plan.
 *
 * The price is set in Play Console on the product below; [price] shows what Play reports for the
 * buyer's account and falls back to the Thai list price until Play has answered.
 */
class BillingManager(app: Application, private val prefs: SharedPreferences) : PurchasesUpdatedListener {

    /** True while the account holds an active Plus subscription. Cached so it survives an offline start. */
    var isPlus by mutableStateOf(prefs.getBoolean(KEY_PLUS, false))
        private set

    var price by mutableStateOf(LIST_PRICE)
        private set

    /** A sentence to show under the upgrade button when a purchase could not start or finish. */
    var message by mutableStateOf<String?>(null)

    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /** Connects to Play and refreshes the product and what the account owns. Safe to call repeatedly. */
    fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun refresh() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build(),
        ) { result, list ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            details = list.firstOrNull()
            details?.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                ?.let { price = it }
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) apply(purchases)
        }
    }

    /** Opens Google Play's purchase sheet for Plus. */
    fun purchase(activity: Activity) {
        message = null
        val product = details
        val offer = product?.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (!client.isReady || product == null || offer == null) {
            message = "Google Play isn't offering Plus on this device yet. Try again in a moment."
            connect()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offer)
                        .build(),
                ),
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            message = "Google Play couldn't start the purchase."
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> if (purchases != null) apply(purchases, merge = true)
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> message = "The purchase didn't go through. You were not charged."
        }
    }

    /**
     * [merge] is for the purchase callback, which lists only the purchases that just changed, so it
     * can grant Plus but must not take it away.
     */
    private fun apply(purchases: List<Purchase>, merge: Boolean = false) {
        val owned = purchases.filter {
            PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        if (owned.isNotEmpty() || !merge) {
            isPlus = owned.isNotEmpty()
            prefs.edit().putBoolean(KEY_PLUS, isPlus).apply()
        }
        // Play refunds a subscription that is not acknowledged within three days.
        owned.filterNot { it.isAcknowledged }.forEach { purchase ->
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            ) { }
        }
    }

    companion object {
        /** The subscription's product ID in Play Console. Its base plan is monthly, priced at THB 99. */
        const val PRODUCT_ID = "coma_plus_monthly"
        const val LIST_PRICE = "฿99"
        const val MANAGE_URL = "https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=com.zyphite.coma"
        private const val KEY_PLUS = "plus"
    }
}
