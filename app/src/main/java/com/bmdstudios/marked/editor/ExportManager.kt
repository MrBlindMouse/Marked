package com.bmdstudios.marked.editor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.bmdstudios.marked.settings.MarkedSettings
import java.io.File
import java.io.FileInputStream

private const val TAG = "MarkedExport"

/**
 * Saves a composite mark image into the device gallery (MediaStore) and returns its content URI.
 * The file name follows the export file prefix/suffix from [MarkedSettings]; the destination
 * folder follows the configured default export folder.
 */
fun exportMarkToGallery(context: Context, bitmap: Bitmap, settings: MarkedSettings): Uri? {
    Log.d(TAG, "Exporting mark to gallery: ${bitmap.width}x${bitmap.height}")
    val uri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${settings.exportFilePrefix}${System.currentTimeMillis()}${settings.exportFileSuffix}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            val relativePath = settings.defaultExportFolder
                .takeIf { it.isNotBlank() }
                ?.let { "Pictures/$it" }
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
        },
    ) ?: return null
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    }
    context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
    )
    return uri
}

/** Shares the exported mark through the system share sheet. */
fun shareMark(context: Context, bitmap: Bitmap, settings: MarkedSettings) {
    val uri = exportMarkToGallery(context, bitmap, settings) ?: return
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Marked"))
}

/**
 * Saves an encoded marked video into the device gallery (MediaStore) and returns its content URI.
 */
fun exportVideoToGallery(context: Context, video: File, settings: MarkedSettings): Uri? {
    Log.d(TAG, "Exporting marked video to gallery: ${video.absolutePath}")
    val uri = context.contentResolver.insert(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "${settings.exportFilePrefix}${System.currentTimeMillis()}${settings.exportFileSuffix}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/${settings.defaultExportFolder}")
        },
    ) ?: return null
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        FileInputStream(video).use { input -> input.copyTo(stream) }
    }
    context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
    )
    return uri
}

/** Shares the exported marked video through the system share sheet. */
fun shareVideo(context: Context, uri: Uri, settings: MarkedSettings) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Marked"))
}
