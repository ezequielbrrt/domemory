package com.ezequielbrrt.domemory.feature.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.services.analytics.AnalyticsEvent
import com.ezequielbrrt.domemory.services.analytics.AnalyticsService
import com.ezequielbrrt.domemory.services.share.PlayStoreLinks
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Android port of `ios/.../Modules/Memorize/MemorizeView/Modals/WinModal/ShareResultCard
 * .swift`: a Wordle-style spoiler-free grid (green square per matched pair, an amber row
 * only on a mistake) shared as a card image plus a caption, through the OS share sheet.
 */
data class ResultShareData(
    val pairs: Int,
    val timeRemaining: Int,
    val failedTries: Int,
    val difficulty: Difficulty,
    val isDailyChallenge: Boolean,
    val streak: Int,
)

/**
 * Spoiler-free Wordle-style grid: one square per pair (green, capped at 12), plus a row of
 * amber squares for mistakes (also capped at 12, present only when [failedTries] > 0).
 * Reveals nothing about the actual board content — pure, no Compose/Android dependency, so
 * it is unit-testable on its own (line-for-line port of iOS's `resultGridString`).
 */
fun resultGridString(pairs: Int, failedTries: Int): String {
    val greens = "🟩".repeat(pairs.coerceIn(0, 12))
    val ambers = if (failedTries > 0) "\n" + "🟧".repeat(failedTries.coerceAtMost(12)) else ""
    return greens + ambers
}

/** Port of iOS's `ResultShare.caption` — its final line is `InviteLink.appStoreURL
 * .absoluteString`; Android has no App Store link, so this reuses [PlayStoreLinks], the same
 * helper the "Rate DoMemory" Settings row builds its `market://` fallback from. */
fun resultShareCaption(context: Context, data: ResultShareData): String {
    val headline = context.getString(R.string.share_result_caption, data.pairs)
    val grid = resultGridString(data.pairs, data.failedTries)
    return "$headline\n$grid\n${PlayStoreLinks.listingUrl(context)}"
}

/** The rendered card, shared as an image. Brand-color background with white text
 * unconditionally (mirrors iOS's literal `.white`/`.white.opacity` — the card is meant to
 * look the same regardless of the viewer's device theme, unlike the rest of the app's
 * adaptive [LocalPalette] tokens). */
@Composable
fun ShareResultCardView(data: ResultShareData, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Column(
        modifier
            .width(320.dp)
            .height(440.dp)
            .background(palette.primary)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.common_app_name), color = Color.White, style = DoMemoryType.display(26))
        Text("😎", fontSize = 60.sp)
        Text(
            text = stringResource(if (data.isDailyChallenge) R.string.daily_challenge_title else R.string.game_win_title),
            color = Color.White,
            style = DoMemoryType.display(22),
            textAlign = TextAlign.Center,
        )
        Text(
            text = resultGridString(data.pairs, data.failedTries),
            color = Color.White,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ShareRow(stringResource(R.string.menu_difficulty_label), stringResource(data.difficulty.labelRes()))
            ShareRow(stringResource(R.string.game_pairs_label), "${data.pairs}")
            ShareRow(stringResource(R.string.game_remaining_label), "${data.timeRemaining}s")
            ShareRow(stringResource(R.string.game_errors_label), "${data.failedTries}")
            if (data.isDailyChallenge && data.streak > 0) {
                ShareRow("🔥", "${data.streak}")
            }
        }
    }
}

@Composable
private fun ShareRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
    }
}

/** Small local mapper, duplicated deliberately — `feature/settings/SettingsScreen.kt` and
 * `feature/menu/MenuScreen.kt` each already carry their own copy of this exact mapping
 * rather than a shared helper (see `ANDROID_PLAN.md`'s 2026-09-14 emulator-verification
 * note, which fixed a bug from a *third*, inconsistent copy) — this follows that existing
 * per-file precedent instead of introducing a new shared one. */
private fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MEDIUM -> R.string.difficulty_medium
    Difficulty.HARD -> R.string.difficulty_hard
    Difficulty.VERY_HARD -> R.string.difficulty_very_hard
}

/**
 * Renders [graphicsLayer]'s already-recorded content to a PNG in the app's cache dir, then
 * launches an `ACTION_SEND` chooser with the image (via [FileProvider]) and the caption.
 * Compose has no direct SwiftUI-`ImageRenderer` analogue; this is the current idiomatic
 * approach (`GraphicsLayer.toImageBitmap()`, Compose UI graphics) rather than standing up an
 * offscreen `ComposeView`/window, and needs nothing beyond what a win-screen button tap can
 * reasonably do — no extra Activity lifecycle handling. [graphicsLayer] must already have
 * [ShareResultCardView] recorded into it (see `GameScreen.kt`'s `OutcomeOverlay`, which hosts
 * an invisible, alpha-0 instance of the card purely so there's real content to capture).
 */
suspend fun shareResultCard(context: Context, graphicsLayer: GraphicsLayer, data: ResultShareData) {
    // Mirrors iOS's WinModal: source is "daily_challenge" for the Daily Challenge, "win"
    // for every other mode's share button.
    AnalyticsService.log(AnalyticsEvent.ResultShared(source = if (data.isDailyChallenge) "daily_challenge" else "win"))
    val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
    val file = writeShareBitmap(context, bitmap) ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, resultShareCaption(context, data))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private suspend fun writeShareBitmap(context: Context, bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "result_share.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        file
    }.getOrNull()
}
