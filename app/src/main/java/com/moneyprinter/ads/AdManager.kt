package com.moneyprinter.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdManager(context: Context) {
    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var lastAdShownTime = 0L
    private val minAdInterval = 3 * 60 * 1000L // 3 minutes
    private val appContext = context.applicationContext

    // Test IDs — replace with real IDs before production release
    private val rewardedAdId = "ca-app-pub-3940256099942544/5224354917"
    private val interstitialAdId = "ca-app-pub-3940256099942544/1033173712"

    init {
        MobileAds.initialize(appContext) {}
        loadRewardedAd()
        loadInterstitialAd()
    }

    private fun loadRewardedAd() {
        RewardedAd.load(
            appContext,
            rewardedAdId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    private fun loadInterstitialAd() {
        InterstitialAd.load(
            appContext,
            interstitialAdId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    fun showRewardedAd(
        activity: Activity,
        onRewarded: () -> Unit,
        onFailed: () -> Unit = {},
    ) {
        if (!canShowAd()) {
            onFailed()
            return
        }
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewardedAd()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    rewardedAd = null
                    loadRewardedAd()
                }
            }
            ad.show(activity) {
                lastAdShownTime = System.currentTimeMillis()
                onRewarded()
            }
        } else {
            onFailed()
            loadRewardedAd()
        }
    }

    fun showInterstitialAd(
        activity: Activity,
        onClosed: () -> Unit = {},
    ) {
        if (!canShowAd()) {
            onClosed()
            return
        }
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    lastAdShownTime = System.currentTimeMillis()
                    interstitialAd = null
                    loadInterstitialAd()
                    onClosed()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialAd = null
                    loadInterstitialAd()
                    onClosed()
                }
            }
            ad.show(activity)
        } else {
            onClosed()
            loadInterstitialAd()
        }
    }

    fun isRewardedAdReady() = rewardedAd != null && canShowAd()

    private fun canShowAd(): Boolean {
        return (System.currentTimeMillis() - lastAdShownTime) >= minAdInterval
    }
}
