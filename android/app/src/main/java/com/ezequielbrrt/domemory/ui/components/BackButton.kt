package com.ezequielbrrt.domemory.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.ui.theme.LocalPalette

/**
 * The one back affordance for screens that draw their own header: a 28dp arrow inside the
 * 48dp minimum touch target. It replaces bare "‹" text glyphs, which rendered about 12dp tall
 * with a tap target to match. The arrow mirrors under RTL.
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalPalette.current.primary,
) {
    val label = stringResource(R.string.common_back)
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp).semantics { contentDescription = label },
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
    }
}
