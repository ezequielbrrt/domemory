package com.ezequielbrrt.domemory.feature.seasons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.services.seasons.Season
import com.ezequielbrrt.domemory.services.seasons.SeasonLevelProgressStore
import com.ezequielbrrt.domemory.services.seasons.SeasonLocaleResolver
import com.ezequielbrrt.domemory.ui.components.CompactCardLayout
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import java.util.Locale

/**
 * The menu's season entry point (spec 9.1). Always the compact vertical layout — unlike
 * the Daily Challenge card, iOS's `SeasonCard` (`MenuView.swift:614-655`) has no separate
 * full-width variant, since a season is only ever shown while it shares the row with the
 * Daily card.
 *
 * The badge shows the same metric iOS's `SeasonCard.badgeText` does (`MenuView.swift:
 * 629-638`): `cleared / total` progress, or the "Complete" badge once the season is
 * finished — **not** a season-cumulative star total, which iOS's header never displays
 * either (see `SeasonLevelsScreen.kt`'s own doc comment on that same point).
 *
 * Entering this card is also the trigger to warm the disk cache with the season's
 * background artwork, so [SeasonLevelsScreen] does not flash flat colour on first open —
 * mirrors iOS's "prefetch when the active season changes" guard by keying on the season id.
 */
@Composable
fun SeasonCard(
    season: Season,
    store: SeasonLevelProgressStore,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val languageTag = remember { Locale.getDefault().toLanguageTag() }
    val resolved = remember(season, languageTag) { SeasonLocaleResolver.resolveText(season, languageTag) }
    val title = resolved?.first ?: stringResource(R.string.season_fallback_title)
    val fallbackAccent = LocalPalette.current.primary
    val accentColor = remember(season.accentColor, fallbackAccent) { season.parsedAccentColor() ?: fallbackAccent }

    // Recomposes the badge when a level is cleared elsewhere (e.g. the season map) while
    // this card is still on screen behind it.
    val revision by store.progress.revision.collectAsState()
    val isComplete = store.progress.isComplete(season.levelCount)
    val clearedCount = store.progress.clearedLevelCount(season.levelCount)
    val badgeText = if (isComplete) {
        stringResource(R.string.season_complete_badge)
    } else {
        stringResource(R.string.season_progress_format, clearedCount, season.levelCount)
    }

    LaunchedEffect(season.id) {
        val loader = SingletonImageLoader.get(context)
        season.artworkUrls().forEach { url ->
            loader.enqueue(ImageRequest.Builder(context).data(url).build())
        }
    }

    CompactCardLayout(
        icon = { Text(season.icon, fontSize = 18.sp) },
        title = title,
        background = accentColor,
        badge = {
            Text(
                badgeText,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                maxLines = 1,
            )
        },
        artworkUrl = season.cardImageURL,
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
internal fun seasonCountdownLabel(daysRemaining: Int): String = when {
    daysRemaining <= 0 -> stringResource(R.string.season_last_day)
    daysRemaining == 1 -> stringResource(R.string.season_one_day_left)
    else -> stringResource(R.string.season_days_left_format, daysRemaining)
}

/** `#RRGGBB` (already validated at decode time) to a Compose [Color], falling back to primary at the call site. */
internal fun Season.parsedAccentColor(): Color? =
    accentColor?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() }
