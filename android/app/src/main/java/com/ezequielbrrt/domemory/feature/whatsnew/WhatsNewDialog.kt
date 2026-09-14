package com.ezequielbrrt.domemory.feature.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.ui.theme.LocalPalette

/** Current release notes, intentionally reused for automatic and Settings presentation. */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new_title), color = palette.primary) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                WhatsNewItem(R.string.whats_new_seasons_title, R.string.whats_new_seasons_description)
                WhatsNewItem(R.string.whats_new_season_artwork_title, R.string.whats_new_season_artwork_description)
                WhatsNewItem(R.string.whats_new_tap_to_play_title, R.string.whats_new_tap_to_play_description)
                WhatsNewItem(R.string.whats_new_season_progress_title, R.string.whats_new_season_progress_description)
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.whats_new_button)) } },
    )
}

@Composable
private fun WhatsNewItem(title: Int, description: Int) {
    val palette = LocalPalette.current
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(stringResource(title), fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Text(stringResource(description), color = palette.textSecondary)
    }
}
