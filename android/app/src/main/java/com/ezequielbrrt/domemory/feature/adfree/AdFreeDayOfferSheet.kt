package com.ezequielbrrt.domemory.feature.adfree

import android.text.format.DateFormat
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.feature.adfree.AdFreeDayOffer.Phase
import com.ezequielbrrt.domemory.services.ads.AdFreeDayService
import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import com.ezequielbrrt.domemory.services.haptics.HapticsService
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import java.util.Date

/**
 * The ad-free day sheet (spec 12.3), the port of iOS's `AdFreeDayOfferView`: Flippo, a short
 * pitch, a stepper for the two-ad chain, one primary button and "Not now". iOS's "Remove ads
 * forever" link is left out: Android has no Remove Ads purchase to link to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdFreeDayOfferSheet(source: String, service: AdFreeDayService, onDismiss: () -> Unit) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val offer = remember(source) { AdFreeDayOffer(source, service, AdMobAdFreeDayAds(activity), scope) }
    val state by offer.state.collectAsState()
    LaunchedEffect(offer) { offer.start() }

    val palette = LocalPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.appBackground,
    ) {
        AdFreeDayOfferContent(
            state = state,
            onPrimary = {
                HapticsService.fire(HapticIntent.TAP)
                if (state.phase == Phase.ACTIVE) onDismiss() else offer.primaryAction()
            },
            onNotNow = {
                HapticsService.fire(HapticIntent.TAP)
                onDismiss()
            },
        )
    }
}

@Composable
private fun AdFreeDayOfferContent(state: AdFreeDayOffer.State, onPrimary: () -> Unit, onNotNow: () -> Unit) {
    val palette = LocalPalette.current
    val isActive = state.phase == Phase.ACTIVE
    val isBusy = state.phase == Phase.LOADING || state.phase == Phase.PRESENTING

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 16.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 460.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(if (state.isIntro && !isActive) R.drawable.flippo_sorry else R.drawable.flippo_no_ads),
                contentDescription = null,
                modifier = Modifier.height(150.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(if (state.isIntro) R.string.ad_free_day_intro_title else R.string.ad_free_day_title),
                style = DoMemoryType.display(26),
                color = palette.primary,
                textAlign = TextAlign.Center,
            )
            if (!isActive) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        if (state.isIntro) R.string.ad_free_day_intro_message else R.string.ad_free_day_message,
                        state.requiredAds,
                    ),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            AdFreeDayStepper(
                adsWatched = state.adsWatched,
                required = state.requiredAds,
                isGranted = isActive,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
            )

            // A fixed minimum height so the sheet does not jump between states.
            Text(
                text = statusText(state),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (state.phase == Phase.NO_FILL) palette.secondary else palette.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.heightIn(min = 40.dp).padding(bottom = 8.dp),
            )

            Button(
                onClick = onPrimary,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) palette.primary else palette.hardAmber,
                    contentColor = Color.White,
                    disabledContainerColor = palette.hardAmber.copy(alpha = 0.6f),
                    disabledContentColor = Color.White,
                ),
            ) {
                Text(primaryTitle(state), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            if (!isActive) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onNotNow) {
                    Text(
                        stringResource(R.string.ad_free_day_not_now),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun statusText(state: AdFreeDayOffer.State): String = when (state.phase) {
    Phase.ACTIVE -> {
        val until = state.expiryMillis?.let { DateFormat.getTimeFormat(LocalContext.current).format(Date(it)) }.orEmpty()
        stringResource(R.string.ad_free_day_active_format, until)
    }
    Phase.NO_FILL -> stringResource(R.string.ad_free_day_no_fill)
    Phase.LOADING, Phase.READY, Phase.PRESENTING ->
        if (state.adsWatched == state.requiredAds - 1) stringResource(R.string.ad_free_day_halfway_hint) else ""
}

@Composable
private fun primaryTitle(state: AdFreeDayOffer.State): String = when (state.phase) {
    Phase.LOADING, Phase.PRESENTING -> stringResource(R.string.ads_loading)
    Phase.READY ->
        if (state.isIntro && state.adsWatched == 0) {
            stringResource(R.string.ad_free_day_intro_watch_format, state.nextAdNumber, state.requiredAds)
        } else {
            stringResource(R.string.ad_free_day_watch_format, state.nextAdNumber, state.requiredAds)
        }
    Phase.NO_FILL -> stringResource(R.string.ad_free_day_try_again)
    Phase.ACTIVE -> stringResource(R.string.ad_free_day_back)
}

/** `( 1 )──( 2 )──( ★ )` with labels: one node per ad, then the reward. */
@Composable
private fun AdFreeDayStepper(adsWatched: Int, required: Int, isGranted: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val description = stringResource(R.string.ad_free_day_pill_progress_format, if (isGranted) required else adsWatched, required)
    Row(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.Top,
    ) {
        for (index in 1..required) {
            val done = isGranted || adsWatched >= index
            StepperNode(done = done, label = stringResource(R.string.ad_free_day_step_ad_format, index), fill = palette.primary) {
                if (done) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                } else {
                    Text("$index", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = palette.textSecondary)
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .padding(top = (NODE_SIZE / 2 - 1.5f).dp)
                    .height(3.dp)
                    .background(if (done) palette.primary else palette.surfaceBorder),
            )
        }
        StepperNode(done = isGranted, label = stringResource(R.string.ad_free_day_step_reward), fill = palette.hardAmber) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = if (isGranted) Color.White else palette.hardAmber,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private const val NODE_SIZE = 36

@Composable
private fun StepperNode(done: Boolean, label: String, fill: Color, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(84.dp)) {
        Box(
            modifier = Modifier
                .size(NODE_SIZE.dp)
                .background(if (done) fill else palette.surfacePrimary, CircleShape)
                .border(BorderStroke(2.dp, if (done) fill else palette.surfaceBorder), CircleShape),
            contentAlignment = Alignment.Center,
        ) { content() }
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (done) palette.textPrimary else palette.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
