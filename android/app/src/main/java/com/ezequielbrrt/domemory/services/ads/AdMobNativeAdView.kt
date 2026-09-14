package com.ezequielbrrt.domemory.services.ads

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ezequielbrrt.domemory.R
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Compose lifecycle wrapper for the multiplayer-finished native placement — a manually-built
 * [NativeAdView] hierarchy, the same reason iOS's `AdMobNativeAdView` builds its native ad
 * view in plain UIKit rather than SwiftUI: neither toolkit has first-class native-ad support.
 * A placement with no configured ad unit id renders nothing (see the caller's own
 * [AdsService.isNativeConfigured] gate — this view does not re-check it, matching how
 * [AdMobBanner] owns its own gate rather than trusting every call site to remember one).
 */
@Composable
fun AdMobNativeAdView(placement: AdPlacement, modifier: Modifier = Modifier) {
    require(placement.type == AdPlacement.Type.NATIVE) { "$placement is not a native placement" }
    val context = LocalContext.current
    var nativeAd by remember(placement) { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(placement) {
        val adLoader = AdLoader.Builder(context, AdUnitConfiguration.unitId(placement))
            .forNativeAd { ad ->
                nativeAd?.destroy()
                nativeAd = ad
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeAd = null
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
        onDispose { nativeAd?.destroy() }
    }

    val ad = nativeAd ?: return
    AndroidView(
        modifier = modifier,
        factory = { ctx -> buildNativeAdView(ctx) },
        update = { view -> bindNativeAd(view, ad) },
    )
}

private class NativeAdViewHolder(
    val headline: TextView,
    val body: TextView,
    val cta: Button,
    val media: MediaView,
)

/** Media + badge + headline + body + a non-interactive call-to-action label, stacked
 * vertically — mirrors the layout iOS's `AdMobNativeAdView` builds by hand. */
private fun buildNativeAdView(context: Context): NativeAdView {
    val nativeAdView = NativeAdView(context)
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()

    val stack = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    nativeAdView.addView(stack)

    val media = MediaView(context)
    stack.addView(media, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    nativeAdView.setMediaView(media)

    val badge = TextView(context).apply {
        text = context.getString(R.string.ads_label)
        setTypeface(typeface, Typeface.BOLD)
        textSize = 11f
        setPadding(dp(14), dp(8), dp(14), 0)
    }
    stack.addView(badge)
    nativeAdView.setAdvertiserView(badge)

    val headline = TextView(context).apply {
        setTypeface(typeface, Typeface.BOLD)
        textSize = 16f
        maxLines = 2
        setPadding(dp(14), dp(4), dp(14), 0)
    }
    stack.addView(headline)
    nativeAdView.setHeadlineView(headline)

    val body = TextView(context).apply {
        textSize = 13f
        maxLines = 2
        setPadding(dp(14), dp(4), dp(14), 0)
    }
    stack.addView(body)
    nativeAdView.setBodyView(body)

    val cta = Button(context).apply {
        // Display-only: per AdMob's contract, taps on native ad content are handled by the
        // SDK's own overlay once `setNativeAd` is called, not by this button's own listener.
        isClickable = false
        isFocusable = false
    }
    val ctaParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        gravity = Gravity.START
        setMargins(dp(14), dp(8), dp(14), dp(14))
    }
    stack.addView(cta, ctaParams)
    nativeAdView.setCallToActionView(cta)

    nativeAdView.tag = NativeAdViewHolder(headline, body, cta, media)
    return nativeAdView
}

private fun bindNativeAd(view: NativeAdView, ad: NativeAd) {
    val holder = view.tag as? NativeAdViewHolder ?: return
    holder.headline.text = ad.headline
    holder.body.text = ad.body
    holder.body.visibility = if (ad.body == null) View.GONE else View.VISIBLE
    holder.cta.text = ad.callToAction
    holder.cta.visibility = if (ad.callToAction == null) View.GONE else View.VISIBLE
    holder.media.mediaContent = ad.mediaContent
    view.setNativeAd(ad)
}
