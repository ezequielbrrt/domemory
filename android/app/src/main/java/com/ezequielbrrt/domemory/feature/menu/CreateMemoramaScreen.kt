package com.ezequielbrrt.domemory.feature.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette

/**
 * Custom memorama creation (spec 13.3): a name, then 2+ emoji added one at a time.
 * [state]'s `canSave` already reflects the two-item floor (mirroring
 * `UserPreferences.addCustomMemorama`'s own rejection of a too-short board), so the
 * primary action is simply disabled until then; [state.rejected] covers the unlikely
 * case where a save is attempted and refused anyway.
 */
@Composable
fun CreateMemoramaScreen(
    state: CreateMemoramaUiState,
    onNameChange: (String) -> Unit,
    onEmojiInputChange: (String) -> Unit,
    onAddEmoji: () -> Unit,
    onRemoveEmoji: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Column(
        modifier
            .fillMaxSize()
            .background(palette.appBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.menu_create_title),
            style = DoMemoryType.display(24),
            color = palette.textPrimary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.menu_create_name_label),
                fontSize = 12.sp,
                color = palette.textSecondary,
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                placeholder = { Text(stringResource(R.string.menu_create_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.menu_create_emojis_label),
                fontSize = 12.sp,
                color = palette.textSecondary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.emojiInput,
                    onValueChange = onEmojiInputChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onAddEmoji,
                    enabled = state.emojiInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                ) {
                    Text(stringResource(R.string.menu_create_add))
                }
            }
            if (state.items.size < 2) {
                Text(
                    text = stringResource(R.string.menu_create_minimum_hint),
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                )
            }
            if (state.items.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(state.items) { index, item ->
                        EmojiChip(item = item, onRemove = { onRemoveEmoji(index) })
                    }
                }
            }
        }

        if (state.rejected) {
            Text(
                text = stringResource(R.string.menu_create_rejected),
                color = palette.secondary,
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.size(1.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onSave,
                enabled = state.canSave,
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
            ) {
                Text(stringResource(R.string.menu_create_save))
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.common_cancel), color = palette.textSecondary)
            }
        }
    }
}

@Composable
private fun EmojiChip(item: String, onRemove: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .background(palette.surfaceSecondary, RoundedCornerShape(999.dp))
            .border(1.dp, palette.surfaceBorder, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = item, fontSize = 18.sp)
        Text(
            text = "✕",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary,
            modifier = Modifier.clickable(onClick = onRemove),
        )
    }
}
