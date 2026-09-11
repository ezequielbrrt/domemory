package com.ezequielbrrt.domemory.feature.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.core.model.Card
import com.ezequielbrrt.domemory.ui.theme.LocalPalette

/**
 * One card. Flip is a Y-rotation through 90°, at which point the face swaps — the same
 * "Cardify" trick the iOS app uses, so the back never shows mirrored content.
 */
@Composable
fun CardView(
    card: Card,
    isHidden: Boolean,
    showsPie: Boolean,
    pieFraction: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val rotation by animateFloatAsState(
        targetValue = if (card.isFaceUp) 0f else 180f,
        animationSpec = tween(durationMillis = 500),
        label = "cardFlip",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isHidden) 0f else 1f,
        animationSpec = tween(durationMillis = 350),
        label = "cardHide",
    )
    val showingFront = rotation < 90f
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
                this.alpha = alpha
            }
            .background(
                color = if (showingFront) palette.surfacePrimary else palette.primary,
                shape = RoundedCornerShape(14.dp),
            )
            .border(
                width = 1.dp,
                color = if (showingFront) palette.surfaceBorder else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !isHidden,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (showingFront) {
            if (showsPie && !card.isMatched && pieFraction > 0f) {
                PieOverlay(fraction = pieFraction, color = palette.hardAmber)
            }
            Text(
                text = card.content,
                fontSize = 34.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(4.dp),
            )
        } else {
            Text(
                text = "?",
                fontSize = 26.sp,
                color = palette.surfacePrimary,
                modifier = Modifier.graphicsLayer { rotationY = 180f },
            )
        }
    }
}

/**
 * The shrinking wedge (spec 3.4). Purely "you're taking too long on this card" — it is
 * worth no points and never ends the game.
 */
@Composable
private fun PieOverlay(fraction: Float, color: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(8.dp)
            .drawBehind {
                val diameter = minOf(size.width, size.height)
                drawArc(
                    color = color.copy(alpha = 0.25f),
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = true,
                    size = Size(diameter, diameter),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        (size.width - diameter) / 2f,
                        (size.height - diameter) / 2f,
                    ),
                    style = Fill,
                )
            },
    )
}
