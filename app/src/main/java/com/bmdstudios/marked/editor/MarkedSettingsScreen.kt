package com.bmdstudios.marked.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bmdstudios.marked.settings.MarkedSettings

/**
 * Settings screen: export defaults (folder / prefix / suffix) for marked media. Back returns to
 * the landing screen.
 */
@Composable
fun MarkedSettingsScreen(
    settings: MarkedSettings,
    onSave: (MarkedSettings) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }

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
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Export",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.defaultExportFolder,
            onValueChange = { draft = draft.copy(defaultExportFolder = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Default export folder") },
        )
        OutlinedTextField(
            value = draft.exportFilePrefix,
            onValueChange = { draft = draft.copy(exportFilePrefix = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Export file prefix") },
        )
        OutlinedTextField(
            value = draft.exportFileSuffix,
            onValueChange = { draft = draft.copy(exportFileSuffix = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Export file suffix") },
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = { onSave(draft) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Save")
        }
    }
}
