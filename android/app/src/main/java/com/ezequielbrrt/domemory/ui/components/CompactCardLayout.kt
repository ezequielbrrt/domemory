package com.ezequielbrrt.domemory.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * The half-width layout used when the Daily Challenge and Season cards share a row (spec
 * 9.1: "the Daily card shrinks to a compact layout"). Ports iOS's `CompactCardLayout`
 * (`MenuView.swift:514-583`) literally: a vertical stack of icon circle → title → badge
 * pill, rather than the full-width card's horizontal one, since squeezing the horizontal
 * layout's 48dp icon + two lines of text + trailing badge into a half-width column is what
 * that iOS type exists to avoid.
 *
 * Artwork comes from one of two places. [artworkUrl] is Firebase-supplied, for a Season,
 * whose art is authored per season after the build and so cannot ship in the APK.
 * [artworkRes] is a bundled drawable, for the Daily Challenge, whose art is fixed. The two
 * are never both set.
 *
 * Either way a leading→trailing gradient of [background] sits between the artwork and the
 * text so the white icon/title/badge stay legible over it, exactly like iOS's own
 * `LinearGradient(colors: [background.opacity(0.85), background.opacity(0.25)])`. Without
 * this the artwork-only build (`SeasonCard.kt`'s pre-Phase-4 version) had no such gradient
 * and risked illegible text over busy art.
 */
@Composable
fun CompactCardLayout(
    icon: @Composable () -> Unit,
    title: String,
    background: Color,
    badge: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    artworkUrl: String? = null,
    @DrawableRes artworkRes: Int? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background),
    ) {
        if (artworkUrl != null || artworkRes != null) {
            // `matchParentSize` takes the card's measured size without influencing it, so
            // a filled image crops to the card instead of stretching it — the Compose
            // counterpart of the `Color.clear` overlay iOS uses for the same reason.
            if (artworkUrl != null) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else if (artworkRes != null) {
                Image(
                    painter = painterResource(artworkRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(background.copy(alpha = 0.85f), background.copy(alpha = 0.25f)),
                        ),
                    ),
            )
        }

        Column(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
            )
            Box(
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                badge()
            }
        }
    }
}
