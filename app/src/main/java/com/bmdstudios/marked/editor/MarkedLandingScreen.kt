package com.bmdstudios.marked.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.bmdstudios.marked.ui.theme.MarkedMicroLabel

/**
 * Phase 1 landing screen: app name + options menu entry, scrollable multi-select media view and
 * the "Add Mark" call to action. Media is picked via the system picker; long-pressing a media row
 * toggles it into the mark selection. "Add Mark" stays disabled until at least one media item is
 * selected.
 */
@Composable
fun MarkedLandingScreen(
    media: List<MarkedMedia>,
    selection: Set<String>,
    onToggleSelect: (String) -> Unit,
    onAddMark: (MarkedMedia) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAssets: () -> Unit,
    onOpenRecipes: () -> Unit,
    onPickMedia: () -> Unit,
) {
    var optionsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(text = "<-Back")
            }
            Text(
                text = "Marked",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { optionsExpanded = true }) {
                    Text(
                        text = "⋮",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(
                    expanded = optionsExpanded,
                    onDismissRequest = { optionsExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "Settings") },
                        onClick = { optionsExpanded = false; onOpenSettings() },
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Assets") },
                        onClick = { optionsExpanded = false; onOpenAssets() },
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Recipes") },
                        onClick = { optionsExpanded = false; onOpenRecipes() },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = "Your media",
            style = MarkedMicroLabel,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (media.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No photos or videos yet.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onPickMedia) {
                        Text(text = "Add media")
                    }
                }
            }
        } else {
            media.forEach { mediaItem ->
                MarkedMediaRow(
                    media = mediaItem,
                    selected = mediaItem.id in selection,
                    onToggleSelect = { onToggleSelect(mediaItem.id) },
                )
            }
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

/**
 * One scrollable media row: thumbnail, label and a long-press toggle for multi-select. Selected
 * rows are tinted with the ink colour so the E-Ink paper/ink reading carries through to the media
 * list.
 */
@Composable
fun MarkedMediaRow(
    media: MarkedMedia,
    selected: Boolean,
    onToggleSelect: () -> Unit,
) {
    val inkColor = MaterialTheme.colorScheme.primary
    val onInkColor = MaterialTheme.colorScheme.onPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) inkColor else MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(8.dp),
            )
            .pointerInput(media.id) {
                detectTapGestures(onLongPress = { onToggleSelect() })
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (selected) onInkColor else MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(4.dp),
                ),
        ) {
            if (media.bitmap != null) {
                Image(
                    painter = BitmapPainter(media.bitmap.asImageBitmap()),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Text(
                    text = "▸",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (selected) inkColor else MaterialTheme.colorScheme.surface,
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column {
            Text(
                text = if (media.videoUri != null) "Video" else "Photo",
                color = if (selected) onInkColor else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = media.id.take(12),
                color = if (selected) onInkColor else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    color = if (selected) onInkColor else MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                ),
        )
    }
}
