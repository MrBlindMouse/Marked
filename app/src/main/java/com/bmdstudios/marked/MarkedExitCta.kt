package com.bmdstudios.marked

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Donation destination: the project's GitHub page. */
const val DONATE_GITHUB_URL = "https://github.com/bmdstudios/Marked"

/**
 * Donation CTA shown while the user is exiting the app.
 *
 * Back handling lives in [com.bmdstudios.marked.editor.MarkedEditorScreen] so the exit flow owns a
 * single back dispatcher: first press shows the dialog, second closes it and exits the app.
 */
@Composable
fun MarkedExitCta(
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    onExit: () -> Unit,
) {
    if (visible) {
        val context = LocalContext.current
        Dialog(
            onDismissRequest = { onVisibleChange(false) },
            properties = DialogProperties(dismissOnBackPress = false),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(text = "Enjoy Marked?", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Marked is free and stays ad-free. If it helped you, consider supporting development.\n\n" +
                        "If you've enjoyed using the app, please consider leaving a review.\n\n" +
                        "And if you want to support the further development, please consider donating.\n\n" +
                        "Press back again to exit.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.play_store_url))),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Leave a review on Google Play")
                    }
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_GITHUB_URL)),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Donate on GitHub")
                    }
                    Button(
                        onClick = {
                            val address = context.getString(R.string.donate_bitcoin_address)
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("bitcoin:$address")),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Donate via crypto")
                    }
                }
                TextButton(onClick = { onVisibleChange(false) }) {
                    Text(text = "Not now")
                }
            }
        }
    }
}
