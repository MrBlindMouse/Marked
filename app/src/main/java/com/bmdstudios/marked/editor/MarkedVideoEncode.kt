package com.bmdstudios.marked.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.bmdstudios.marked.model.Layer
import java.io.File

private const val TAG = "MarkedVideoEncode"
private const val ENCODE_MIME = "video/avc"
private const val CODEC_TIMEOUT_US = 10_000L

/**
 * Phase 2 video encode: decodes the picked video, composites the mark layers over every
 * frame and re-encodes the result into an MP4 via MediaCodec (decode -> encode) + MediaMuxer.
 *
 * Pipeline:
 *  - MediaExtractor pulls the source track samples -> MediaCodec decoder -> frames.
 *  - Each decoded frame is passed through the shared [renderMark] overlay pipeline.
 *  - The composite frame is handed to the MediaCodec encoder (buffer mode) and the encoded
 *    output is written into a MediaMuxer MP4.
 *
 * The MVP encodes at source resolution; marked frames keep the layer transforms applied in
 * the editor. Rotation is not applied (see DESIGN.md MVP scope).
 */
fun encodeMarkedVideo(
    context: Context,
    sourceUri: Uri,
    outputFile: File,
    layers: List<Layer>,
    onProgress: (Long) -> Unit = {},
): File? {
    Log.d(TAG, "Starting marked video encode: $sourceUri -> ${outputFile.absolutePath}")

    val extractor = MediaExtractor().apply { setDataSource(context, sourceUri, null) }
    if (extractor.trackCount < 1) {
        extractor.release()
        return null
    }
    extractor.selectTrack(0)
    val trackFormat = extractor.getTrackFormat(0)
    val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ENCODE_MIME
    val width = trackFormat.getInteger(MediaFormat.KEY_WIDTH, 0)
    val height = trackFormat.getInteger(MediaFormat.KEY_HEIGHT, 0)
    val bitrate = trackFormat.getInteger(MediaFormat.KEY_BIT_RATE, 0)
    val frameRate = trackFormat.getInteger(MediaFormat.KEY_FRAME_RATE, 30)

    val decoder = MediaCodec.createDecoderByType(mime)
    decoder.configure(trackFormat, null, null, 0)
    decoder.start()

    val encoder = MediaCodec.createEncoderByType(mime)
    val encodeFormat = MediaFormat.createVideoFormat(mime, width, height)
    encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
    encodeFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
    encodeFormat.setInteger(
        MediaFormat.KEY_COLOR_FORMAT,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
    )
    encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    encoder.start()

    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val trackIndex = muxer.addTrack(encodeFormat)
    muxer.start()

    val bufferInfo = MediaCodec.BufferInfo()
    var framesEncoded = 0L
    var lastPtsUs = 0L
    var sawDecoderEos = false

    while (!sawDecoderEos) {
        val decoderInputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (decoderInputIndex >= 0) {
            val decoderInputBuffer = decoder.getInputBuffer(decoderInputIndex) ?: break
            val sampleSize = extractor.getSampleSize().toInt()
            if (sampleSize > 0) {
                extractor.readSampleData(decoderInputBuffer, 0)
            }
            decoder.queueInputBuffer(
                decoderInputIndex,
                0,
                sampleSize,
                extractor.getSampleTime(),
                extractor.getSampleFlags(),
            )
            extractor.advance()
        }

        val decoderOutputIndex = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
        if (decoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
        if (decoderOutputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) continue
        if (decoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) continue
        if (decoderOutputIndex < 0) continue
        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            sawDecoderEos = true
        }

        val image = decoder.getOutputImage(decoderOutputIndex)
        val frameBitmap = image?.hardwareBuffer?.let { hardware ->
            Bitmap.wrapHardwareBuffer(hardware, ColorSpace.get(ColorSpace.Named.SRGB))
        }
        image?.close()
        decoder.releaseOutputBuffer(decoderOutputIndex, false)

        if (frameBitmap != null) {
            val composite = renderMark(frameBitmap, layers)
            lastPtsUs = bufferInfo.presentationTimeUs
            encodeFrame(
                encoder = encoder,
                muxer = muxer,
                trackIndex = trackIndex,
                composite = composite,
                ptsUs = bufferInfo.presentationTimeUs,
                width = width,
                height = height,
                bufferInfo = bufferInfo,
            )
            framesEncoded += 1
            onProgress(framesEncoded)
            composite.recycle()
        }
        frameBitmap?.recycle()
    }

    // Signal end-of-stream on the encoder and drain whatever is left in the output queue.
    val encoderInputIndex = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
    if (encoderInputIndex >= 0) {
        encoder.queueInputBuffer(
            encoderInputIndex,
            0,
            0,
            lastPtsUs,
            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
        )
    }
    var encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
    var sawEncoderEos = false
    while (!sawEncoderEos) {
        if (encoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            continue
        }
        if (encoderOutputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            continue
        }
        if (encoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            continue
        }
        if (encoderOutputIndex < 0) break
        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            sawEncoderEos = true
        }
        val outputBuffer = encoder.getOutputBuffer(encoderOutputIndex) ?: break
        outputBuffer.clear()
        outputBuffer.position(bufferInfo.offset)
        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
        encoder.releaseOutputBuffer(encoderOutputIndex, false)
        if (sawEncoderEos) break
        encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
    }

    runCatching { muxer.stop() }
    runCatching { muxer.release() }
    runCatching { encoder.stop() }
    runCatching { encoder.release() }
    runCatching { decoder.stop() }
    runCatching { decoder.release() }
    extractor.release()

    Log.d(TAG, "Marked video encode finished: $framesEncoded frames encoded")
    return outputFile
}

private fun encodeFrame(
    encoder: MediaCodec,
    muxer: MediaMuxer,
    trackIndex: Int,
    composite: Bitmap,
    ptsUs: Long,
    width: Int,
    height: Int,
    bufferInfo: MediaCodec.BufferInfo,
): Boolean {
    val inputIndex = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
    if (inputIndex >= 0) {
        val inputBuffer = encoder.getInputBuffer(inputIndex) ?: return false
        val yuv = composite.toYuv420(width, height)
        inputBuffer.clear()
        inputBuffer.put(yuv)
        val flags = if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
            MediaCodec.BUFFER_FLAG_KEY_FRAME
        } else {
            0
        }
        encoder.queueInputBuffer(inputIndex, 0, yuv.size, ptsUs, flags)
    }

    val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) return false
    if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) return false
    if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) return false
    if (outputIndex < 0) return false
    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return false

    val outputBuffer = encoder.getOutputBuffer(outputIndex) ?: return false
    outputBuffer.clear()
    outputBuffer.position(bufferInfo.offset)
    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
    muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
    encoder.releaseOutputBuffer(outputIndex, false)
    return true
}

/**
 * Converts a software ARGB_8888 bitmap into a planar YUV420 byte array
 * (Y plane followed by U and V chroma planes) ready for MediaCodec encoder input.
 */
internal fun Bitmap.toYuv420(width: Int, height: Int): ByteArray {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)

    val yPlane = ByteArray(width * height)
    val chromaWidth = (width + 1) / 2
    val chromaHeight = (height + 1) / 2
    val chromaPlaneSize = chromaWidth * chromaHeight
    val uPlane = ByteArray(chromaPlaneSize)
    val vPlane = ByteArray(chromaPlaneSize)

    for (i in pixels.indices) {
        val argb = pixels[i]
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val y = ((299 * r + 587 * g + 114 * b) / 1000).toInt().coerceIn(0, 255)
        val u = ((186 * r - 359 * g + 183 * b) / 1000 + 128).toInt().coerceIn(0, 255)
        val v = ((226 * r - 683 * g + 369 * b) / 1000 + 128).toInt().coerceIn(0, 255)
        yPlane[i] = y.toByte()

        val x = i % width
        val row = i / width
        if (x % 2 == 0 && row % 2 == 0) {
            val cx = x / 2
            val cy = row / 2
            uPlane[cy * chromaWidth + cx] = u.toByte()
            vPlane[cy * chromaWidth + cx] = v.toByte()
        }
    }

    val yuv = ByteArray(width * height + chromaPlaneSize * 2)
    System.arraycopy(yPlane, 0, yuv, 0, yPlane.size)
    System.arraycopy(uPlane, 0, yuv, width * height, chromaPlaneSize)
    System.arraycopy(vPlane, 0, yuv, width * height + chromaPlaneSize, chromaPlaneSize)
    return yuv
}
