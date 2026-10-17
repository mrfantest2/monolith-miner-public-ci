package com.fantest.monolithminer

import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Non-invasive monetization: banner below the scrollable UI and a player-initiated
 * rewarded ore cache. The preview/debug builds use Google's sample inventory only.
 * Release builds never request ads until Monolith-specific production IDs are provided.
 */
class GameAds(
    private val activity: MainActivity,
    private val bannerHost: FrameLayout,
    private val onReadyChanged: () -> Unit
) {
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private var adView: AdView? = null
    private var rewarded: RewardedAd? = null
    private var sdkStarted = false
    private var rewardLoading = false
    private var rewardLoadFailed = false
    private var showingReward = false
    private var disposed = false

    val isRewardReady: Boolean get() = !disposed && rewarded != null && !showingReward
    val canRetryReward: Boolean get() = !disposed && sdkStarted && rewardLoadFailed && !rewardLoading
    fun retryReward() { if (canRetryReward) loadReward() }
    val privacyOptionsRequired: Boolean get() =
        BuildConfig.ADS_ENABLED && consent.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun start() {
        bannerHost.visibility = View.GONE
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
                    setupBanner()
                    loadReward()
                }
            }
        }.start()
    }

    private fun setupBanner() {
        if (adView != null || disposed) return
        // A compact anchored banner sits outside the tappable mining field and tabs.
        val widthDp = (bannerHost.resources.displayMetrics.widthPixels /
            bannerHost.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val view = AdView(activity)
        view.adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
        view.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
        view.adListener = object : AdListener() {
            override fun onAdLoaded() {
                if (!disposed) bannerHost.visibility = View.VISIBLE
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                bannerHost.visibility = View.GONE
            }
        }
        adView = view
        bannerHost.removeAllViews()
        bannerHost.addView(view)
        view.loadAd(AdRequest.Builder().build())
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

    fun onPause() { adView?.pause() }
    fun onResume() { adView?.resume() }
    fun destroy() {
        disposed = true
        rewarded = null
        adView?.destroy()
        adView = null
        bannerHost.removeAllViews()
    }
}
