package com.ezequielbrrt.domemory.services.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/** Compose lifecycle wrapper for the two persistent banner placements. */
@Composable
fun AdMobBanner(placement: AdPlacement, modifier: Modifier = Modifier) {
    require(placement.type == AdPlacement.Type.BANNER) { "$placement is not a banner placement" }
    val context = LocalContext.current
    val adView = remember(placement) {
        AdView(context).apply {
            adUnitId = AdUnitConfiguration.unitId(placement)
            setAdSize(AdSize.BANNER)
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) { onDispose { adView.destroy() } }
    AndroidView(factory = { adView }, modifier = modifier)
}
