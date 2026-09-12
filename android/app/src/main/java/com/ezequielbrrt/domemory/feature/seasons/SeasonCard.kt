package com.ezequielbrrt.domemory.feature.seasons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.services.seasons.Season
import com.ezequielbrrt.domemory.services.seasons.SeasonLocaleResolver
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import java.util.Locale

/**
 * The menu's season entry point (spec 9.1). Shares a row with [com.ezequielbrrt.domemory.feature.daily.DailyChallengeCard]
 * when a season is active — [com.ezequielbrrt.domemory.feature.menu.MenuScreen] gives each
 * card equal `weight(1f)` in that row, though neither card's internal layout restyles for
 * the narrower width yet (spec 9.1's "shrinks to a compact layout" is not implemented —
 * cosmetic only, see `ANDROID_PLAN.md` §7).
 *
 * The card is the season's accent colour with white text (spec 9.7), overlaid with
 * [Season.cardImageURL] when present. Entering this card is also the trigger to warm the
 * disk cache with the season's background artwork, so [SeasonLevelsScreen] does not flash
 * flat colour on first open — mirrors iOS's "prefetch when the active season changes"
 * guard by keying on the season id.
 */
@Composable
fun SeasonCard(season: Season, todayKey: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val languageTag = remember { Locale.getDefault().toLanguageTag() }
    val resolved = remember(season, languageTag) { SeasonLocaleResolver.resolveText(season, languageTag) }
    val title = resolved?.first ?: stringResource(R.string.season_fallback_title)
    val fallbackAccent = LocalPalette.current.primary
    val accentColor = remember(season.accentColor, fallbackAccent) { season.parsedAccentColor() ?: fallbackAccent }
    val daysRemaining = remember(season, todayKey) { season.daysRemaining(todayKey) }

    LaunchedEffect(season.id) {
        val loader = SingletonImageLoader.get(context)
        season.artworkUrls().forEach { url ->
            loader.enqueue(ImageRequest.Builder(context).data(url).build())
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(accentColor)
            .clickable(onClick = onClick)
            .wrapContentHeight(),
    ) {
        season.cardImageURL?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(season.icon, fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = DoMemoryType.display(16))
                daysRemaining?.let { days ->
                    Text(text = seasonCountdownLabel(days), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
            }
        }
    }
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
