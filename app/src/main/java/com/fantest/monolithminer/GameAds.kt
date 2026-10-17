package com.fantest.monolithminer

import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Non-invasive monetization: player-initiated rewards only. No banners or
 * interstitials. The preview/debug builds use Google test inventory only.
 * Release builds never request ads until Monolith-specific production IDs are provided.
 */
class GameAds(
    private val activity: MainActivity,
    private val onReadyChanged: () -> Unit
) {
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private var rewarded: RewardedAd? = null
    private var sdkStarted = false
    private var rewardLoading = false
    private var rewardLoadFailed = false
    private var showingReward = false
    private var rewardedAdReturnPending = false
    private var disposed = false

    val isRewardReady: Boolean get() = !disposed && rewarded != null && !showingReward
    val canRetryReward: Boolean get() = !disposed && sdkStarted && rewardLoadFailed && !rewardLoading
    fun retryReward() { if (canRetryReward) loadReward() }

    /** Consume the pending ad-to-game transition once, without suppressing earned idle ore. */
    fun consumeRewardedAdReturn(): Boolean {
        val pending = rewardedAdReturnPending
        rewardedAdReturnPending = false
        return pending
    }
    val privacyOptionsRequired: Boolean get() =
        BuildConfig.ADS_ENABLED && consent.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun start() {
        if (!BuildConfig.ADS_ENABLED) return
        // Check at every application launch; an existing consent can allow ads immediately.
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    requestAdsWhenPermitted()
                    onReadyChanged()
                }
                requestAdsWhenPermitted()
            },
            {
                // A previous consent decision may still be valid after a network error.
                requestAdsWhenPermitted()
                onReadyChanged()
            }
        )
    }

    private fun requestAdsWhenPermitted() {
        if (disposed || sdkStarted || !consent.canRequestAds()) return
        sdkStarted = true
        Thread {
            MobileAds.initialize(activity) {
                activity.runOnUiThread {
                    if (disposed) return@runOnUiThread
                    loadReward()
                }
            }
        }.start()
    }

    private fun loadReward() {
        if (disposed || !sdkStarted || rewardLoading || rewarded != null || showingReward) return
        rewardLoading = true
        RewardedAd.load(
            activity, BuildConfig.ADMOB_REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardLoading = false
                    rewardLoadFailed = false
                    if (disposed) return
                    rewarded = ad
                    onReadyChanged()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardLoading = false
                    rewardLoadFailed = true
                    rewarded = null
                    onReadyChanged()
                }
            }
        )
    }

    /** Only call after an explicit user tap; grant solely on Google's earned callback. */
    fun showReward(onEarned: () -> Unit): Boolean {
        if (disposed || showingReward) return false
        val ad = rewarded ?: return false
        rewarded = null
        showingReward = true
        rewardedAdReturnPending = true
        onReadyChanged()
        var delivered = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showingReward = false
                if (!disposed) {
                    loadReward()
                    onReadyChanged()
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showingReward = false
                if (!disposed) {
                    loadReward()
                    onReadyChanged()
                }
            }
        }
        ad.show(activity) {
            if (!disposed && !delivered) {
                delivered = true
                onEarned()
            }
        }
        return true
    }

    fun showPrivacyOptions() {
        if (!disposed && privacyOptionsRequired) {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) {
                onReadyChanged()
            }
        }
    }

    fun destroy() {
        disposed = true
        rewarded = null
    }
}
