package com.bmdstudios.marked.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bmdstudios.marked.ui.theme.MarkedMicroLabel

/**
 * Assets screen: media the user picked to use as marks. Top "Import Asset" button launches the
 * system media picker; the scrollable asset list shows imported media with multi-select, and
 * "Add Mark" loads a selected asset into the mark editor. Back returns to the landing screen.
 */
@Composable
fun MarkedAssetsScreen(
    media: List<MarkedMedia>,
    selection: Set<String>,
    onToggleSelect: (String) -> Unit,
    onAddMark: (MarkedMedia) -> Unit,
    onImportAsset: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(text = "<-Back")
            }
            Text(
                text = "Assets",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onImportAsset,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Import Asset")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your assets",
            style = MarkedMicroLabel,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (media.isEmpty()) {
            Text(
                text = "No assets yet. Import media to use as marks.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        media.forEach { mediaItem ->
            MarkedMediaRow(
                media = mediaItem,
                selected = mediaItem.id in selection,
                onToggleSelect = { onToggleSelect(mediaItem.id) },
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = {
                media.firstOrNull { it.id in selection }?.let { onAddMark(it) }
            },
            enabled = selection.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Add Mark")
        }
    }
}
