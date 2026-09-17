package com.ezequielbrrt.domemory.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.painter.Painter
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import com.ezequielbrrt.domemory.services.haptics.HapticsService
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import com.ezequielbrrt.domemory.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * One page of [IntroCarousel]. Ports iOS's `IntroSlide` (`IntroCarouselView.swift`).
 *
 * A slide shows either [illustration] or, when that is null, the tinted [icon] in a
 * circle. Both forms exist on both platforms: the first-launch intro is illustrated,
 * the Levels intro is symbolic.
 */
data class IntroSlide(
    val icon: ImageVector,
    val color: (Palette) -> Color,
    val title: String,
    val subtitle: String,
    /**
     * A light/dark pair of drawables for a full-width illustration.
     *
     * Artwork contract, shared with iOS: 2:3 portrait, scene within the top ~48%, the
     * rest a flat fill in the app background colour that the title and subtitle overlap.
     */
    val illustration: IntroIllustration? = null,
)

/** The light and dark drawables for one slide's artwork. */
data class IntroIllustration(
    @DrawableRes val light: Int,
    @DrawableRes val dark: Int,
)

/**
 * The paged intro carousel, shared by the first-launch feature intro and the Levels
 * intro — the Android counterpart of iOS's `IntroCarouselView.swift`, which the two
 * intros share for the same reason: so a change to the chrome cannot reach one intro
 * and not the other.
 *
 * Owners supply the slides and handle their own persistence and analytics; this knows
 * only how to page through them.
 *
 * @param finishTitle label for the button on the last page — "Get Started" on first
 *   launch, "Got It" when the carousel is a reference the player opened deliberately.
 */
@Composable
fun IntroCarousel(
    slides: List<IntroSlide>,
    finishTitle: String,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == slides.size - 1

    Box(modifier.fillMaxSize().background(palette.appBackground)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        HapticsService.fire(HapticIntent.TAP)
                        onSkip()
                    },
                ) {
                    Text(
                        stringResource(R.string.intro_skip),
                        color = palette.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                IntroSlideView(slides[page])
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(slides.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (selected) 8.dp else 6.dp)
                            .background(if (selected) palette.primary else palette.surfaceBorder, CircleShape),
                    )
                }
            }

            Button(
                onClick = {
                    if (isLastPage) {
                        HapticsService.fire(HapticIntent.TAP)
                        onFinish()
                    } else {
                        HapticsService.fire(HapticIntent.SELECT)
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
            ) {
                Text(
                    if (isLastPage) finishTitle else stringResource(R.string.intro_next),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun IntroSlideView(slide: IntroSlide) {
    val palette = LocalPalette.current
    val illustration = slide.illustration
    if (illustration == null) {
        SymbolSlide(slide)
    } else {
        IllustratedSlide(
            slide = slide,
            artwork = painterResource(if (palette.isDark) illustration.dark else illustration.light),
        )
    }
}

@Composable
private fun SymbolSlide(slide: IntroSlide) {
    val palette = LocalPalette.current
    val color = slide.color(palette)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(140.dp).background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(slide.icon, contentDescription = null, tint = color, modifier = Modifier.size(60.dp))
        }
        Spacer(Modifier.height(24.dp))
        IntroSlideText(slide)
    }
}

/**
 * The illustrated slide, ported from iOS's `IllustratedSlideView`.
 *
 * Only the *scene* takes up layout space, so the scene and the text centre together the
 * way a symbol slide does; the artwork's empty lower part hangs behind the text.
 */
@Composable
private fun IllustratedSlide(slide: IntroSlide, artwork: Painter) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val aspect = ARTWORK_ASPECT
        // Cap the width so the scene never eats the room the text needs on a short
        // screen or a tablet — the same two constants iOS uses.
        val width = minOf(
            maxWidth,
            maxHeight * MAX_SCENE_SHARE_OF_PAGE / (SCENE_FRACTION * aspect),
        )
        val height = width * aspect
        val isInset = width < maxWidth

        // Centred, not iOS's one-spacer-above / two-below distribution. That weighting
        // seats the group above centre on iOS because its page area is short enough that
        // the spacers nearly collapse; Android's pager is taller, so the same weights
        // pushed the artwork up under the Skip button. Centring lands where iOS lands.
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.fillMaxWidth().height(height * SCENE_FRACTION),
                contentAlignment = Alignment.TopCenter,
            ) {
                Image(
                    painter = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        // `required*`, not `width`/`height`: this Box is deliberately
                        // shorter than the artwork (only the scene takes layout space),
                        // and Compose clamps an ordinary size modifier to the incoming
                        // constraints — which measured the image at the box's height and
                        // let ContentScale.Fit shrink it to about half width. SwiftUI's
                        // `.frame` inside an overlay overflows instead, which is the
                        // behaviour iOS's `IllustratedSlideView` relies on.
                        .requiredWidth(width)
                        .requiredHeight(height)
                        // The fade has to composite against the artwork alone, not the
                        // screen, so the layer is rasterised before the mask is drawn.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawEdgeFade(isInset)
                        },
                )
            }
            Spacer(Modifier.height(16.dp))
            IntroSlideText(slide, horizontalPadding = 32.dp)
        }
    }
}

/**
 * Fades the artwork into the background: a short fade at the top, a long one at the
 * bottom (hiding any mismatch with the app background), and side fades only when the
 * image is narrower than the page, as on a tablet.
 *
 * Drawn as `DstIn` alpha rather than a shader mask so it matches iOS's `.mask`.
 */
private fun DrawScope.drawEdgeFade(isInset: Boolean) {
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.04f to Color.Black,
            0.8f to Color.Black,
            1f to Color.Transparent,
            startY = 0f,
            endY = size.height,
        ),
        blendMode = BlendMode.DstIn,
    )
    if (isInset) {
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.1f to Color.Black,
                0.9f to Color.Black,
                1f to Color.Transparent,
                startX = 0f,
                endX = size.width,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

@Composable
private fun IntroSlideText(slide: IntroSlide, horizontalPadding: Dp = 0.dp) {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            slide.title,
            style = DoMemoryType.display(26),
            color = palette.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            slide.subtitle,
            color = palette.textSecondary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
    }
}

/** Height ÷ width of every intro illustration: the 2:3 portrait the contract pins. */
private const val ARTWORK_ASPECT = 1536f / 1024f

/** Fraction of the artwork's height the scene occupies, card shadows included. */
private const val SCENE_FRACTION = 0.48f

/** Most of the page the scene may take, so short screens keep room for the text. */
private const val MAX_SCENE_SHARE_OF_PAGE = 0.55f
