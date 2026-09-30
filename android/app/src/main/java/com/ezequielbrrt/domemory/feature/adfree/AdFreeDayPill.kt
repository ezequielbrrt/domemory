package com.ezequielbrrt.domemory.feature.adfree

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.services.ads.AdFreeDayService
import com.ezequielbrrt.domemory.services.ads.AdPlacement
import com.ezequielbrrt.domemory.services.ads.AdsService
import com.ezequielbrrt.domemory.services.analytics.AnalyticsEvent
import com.ezequielbrrt.domemory.services.analytics.AnalyticsService
import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import com.ezequielbrrt.domemory.services.haptics.HapticsService
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The app's one [AdFreeDayService], provided once in `NavGraph` so the pill and the Settings
 * row can reach it without every screen threading it through. Null in previews, where the
 * entry point renders nothing.
 */
val LocalAdFreeDay = staticCompositionLocalOf<AdFreeDayService?> { null }

/**
 * The floating "No Ads" pill and the sheet it opens, placed bottom-end on the three menu tabs,
 * the season map and the multiplayer lobby — every surface that shows involuntary ads and is
 * not itself a game (spec 12.3). Port of iOS's `adFreeDayEntryPoint(source:)`. [source] names
 * the screen for analytics. Hidden when no rewarded unit is configured, unless a grant is
 * already running.
 */
@Composable
fun AdFreeDayEntryPoint(source: String, modifier: Modifier = Modifier) {
    val service = LocalAdFreeDay.current ?: return
    val expiry by service.expiryMillis.collectAsState()
    var showOffer by rememberSaveable { mutableStateOf(false) }
    var adsWatched by remember { mutableIntStateOf(0) }
    // Re-read whenever the sheet closes, so a chain advanced inside it shows on the pill at once.
    LaunchedEffect(showOffer) { if (!showOffer) adsWatched = service.adsWatched() }

    val isActive = service.isGrantActive()
    val visible = isActive || AdsService.isRewardedConfigured(AdPlacement.AD_FREE_DAY_REWARDED)
    if (visible) {
        AdFreeDayPill(
            adsWatched = adsWatched,
            expiryMillis = expiry,
            onClick = {
                HapticsService.fire(HapticIntent.TAP)
                logAdFreeDayEntryTapped(source, isActive = service.isGrantActive(), adsWatched = adsWatched)
                showOffer = true
            },
            modifier = modifier.padding(end = 16.dp, bottom = 12.dp),
        )
    }
    if (showOffer) {
        AdFreeDayOfferSheet(source = source, service = service, onDismiss = { showOffer = false })
    }
}

internal fun logAdFreeDayEntryTapped(source: String, isActive: Boolean, adsWatched: Int) {
    AnalyticsService.log(
        AnalyticsEvent.AdFreeDayEntryTapped(
            source = source,
            state = adFreeDayEntryState(isActive, adsWatched),
            adsWatched = adsWatched,
        ),
    )
}

/** "No Ads" idle, "No Ads 1/2" mid-chain, "18h left" while a grant runs. */
@Composable
fun AdFreeDayPill(adsWatched: Int, expiryMillis: Long?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    // The countdown only needs minute resolution.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiryMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000)
        }
    }
    val isActive = (expiryMillis ?: 0L) > now
    val title = when {
        isActive -> stringResource(R.string.ad_free_day_pill_active_format, remainingShort(expiryMillis!! - now))
        adsWatched > 0 -> stringResource(R.string.ad_free_day_pill_progress_format, adsWatched, AdFreeDayService.REQUIRED_ADS)
        else -> stringResource(R.string.ad_free_day_pill_title)
    }
    Surface(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (isActive) palette.primary else palette.surfacePrimary,
        border = BorderStroke(1.dp, palette.surfaceBorder),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (isActive) Color.White else palette.primary
            Icon(
                imageVector = if (isActive) Icons.Filled.CheckCircle else Icons.Filled.Block,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
            Text(title, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** "2d", "18h" or "45m": one unit is enough on a pill, as on iOS. */
internal fun remainingShort(millis: Long, locale: Locale = Locale.getDefault()): String {
    val minutes = (millis / 60_000).coerceAtLeast(1)
    val measure = when {
        minutes >= 24 * 60 -> Measure(minutes / (24 * 60), MeasureUnit.DAY)
        minutes >= 60 -> Measure(minutes / 60, MeasureUnit.HOUR)
        else -> Measure(minutes, MeasureUnit.MINUTE)
    }
    return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.NARROW).format(measure)
}
