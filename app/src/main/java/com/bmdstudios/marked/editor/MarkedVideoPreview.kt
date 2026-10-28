package com.bmdstudios.marked.editor

import android.content.Context
import android.net.Uri
import android.media.MediaPlayer
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Phase 2 video preview: plays the picked video through MediaPlayer into a SurfaceView.
 * Mark overlay on video frames is composited per-frame during the encode step (see MarkedVideoEncode).
 */
@Composable
fun MarkedVideoPreview(
    videoUri: Uri,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val surfaceView = remember { SurfaceView(context) }

    DisposableEffect(videoUri) {
        val created = MediaPlayer.create(context, videoUri, surfaceView.getHolder())
        if (created != null) {
            created.start()
        }
        onDispose {
            created?.release()
        }
    }

    AndroidView(
        factory = { ctx: Context -> surfaceView },
        modifier = modifier,
    )
}
