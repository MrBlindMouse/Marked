package com.bmdstudios.marked.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.RectF
import android.util.Log
import com.larvalabs.svgandroid.SVGBuilder
import com.bmdstudios.marked.model.Layer
import com.bmdstudios.marked.model.LayerStyle
import com.bmdstudios.marked.model.LayerTransform
import com.bmdstudios.marked.model.basicColorMatrix

/**
 * Renders the mark layers over a source bitmap and returns the composite image.
 *
 * MVP rendering pipeline: source photo -> layers in list order (later = on top).
 * Resolution stays at source-image resolution; layer scale is applied per layer (see DESIGN.md).
 */
/** E-Ink Phase 1 mark ink (graphite) drawn over the paper-toned media surface. */
const val MARK_INK_COLOR = 0xFF1B1A16.toInt()

fun renderMark(source: Bitmap, layers: List<Layer>): Bitmap {
    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawBitmap(source, 0f, 0f, null)
    layers.forEach { layer -> drawLayer(canvas, layer, MARK_INK_COLOR) }
    return output
}

private fun drawLayer(canvas: Canvas, layer: Layer, markColor: Int) {
    when (layer) {
        is Layer.TextLayer -> drawText(
            canvas = canvas,
            text = layer.text,
            fontSize = layer.fontSize,
            color = layer.color,
            bold = layer.bold,
            fontFamily = layer.fontFamily,
            tracking = layer.tracking,
            transform = layer.transform,
            style = layer.style,
        )

        is Layer.TimestampLayer -> drawText(
            canvas = canvas,
            text = layer.format.format(java.time.LocalTime.now()),
            fontSize = layer.fontSize,
            color = markColor,
            bold = false,
            fontFamily = null,
            tracking = 0f,
            transform = layer.transform,
            style = layer.style,
        )

        is Layer.StickerLayer -> drawText(
            canvas = canvas,
            text = layer.emoji,
            fontSize = layer.fontSize,
            color = markColor,
            bold = false,
            fontFamily = null,
            tracking = 0f,
            transform = layer.transform,
            style = layer.style,
        )

        is Layer.SignatureLayer -> drawSignature(canvas, layer)

        is Layer.AssetLayer -> drawAsset(canvas, layer)

        is Layer.SvgLayer -> drawSvg(canvas, layer)
    }
}

private const val DEFAULT_ASSET_OUTLINE_COLOR = 0xFF000000.toInt()
private const val TAG = "MarkedRenderer"

private fun drawText(
    canvas: Canvas,
    text: String,
    fontSize: Float,
    color: Int,
    bold: Boolean,
    fontFamily: String?,
    tracking: Float,
    transform: LayerTransform,
    style: LayerStyle,
) {
    val scale = transform.scale
    val anchorX = transform.x
    val anchorY = transform.y

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = fontSize * scale
        letterSpacing = tracking * scale
        val baseTypeface = when (fontFamily?.lowercase()) {
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }
        typeface = baseTypeface
        if (bold) {
            typeface = Typeface.create(baseTypeface, Typeface.BOLD)
        }
        setStyle(Paint.Style.FILL)
        style.shadow?.let { shadow ->
            setShadowLayer(shadow.offsetX, shadow.offsetY, shadow.radius, shadow.color)
        }
        alpha = (style.opacity * 255f).toInt()
        style.basicColorMatrix().let { matrix ->
            colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        }
    }

    if (style.outline) {
        textPaint.colorFilter = null
        textPaint.color = style.outlineColor ?: color
        textPaint.setStyle(Paint.Style.STROKE)
        textPaint.strokeWidth = style.outlineWidth * scale
        canvas.save()
        canvas.translate(anchorX, anchorY)
        canvas.scale(scale, scale)
        canvas.drawText(text, anchorX, anchorY, textPaint)
        canvas.restore()
    }

    textPaint.colorFilter = style.basicColorMatrix().let { ColorMatrixColorFilter(ColorMatrix(it)) }
    textPaint.color = color
    textPaint.setStyle(Paint.Style.FILL)
    canvas.save()
    canvas.translate(anchorX, anchorY)
    canvas.scale(scale, scale)
    canvas.drawText(text, anchorX, anchorY, textPaint)

    if (style.pill) {
        val textWidth = textPaint.measureText(text)
        val fontMetrics = textPaint.fontMetrics
        val textHeight =
            (fontMetrics.descent - fontMetrics.ascent).coerceAtLeast(fontSize * scale * 0.8f)
        val radius = minOf(textWidth, textHeight) / 2f
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(style.background)
            setAlpha((style.opacity * 255f).toInt())
        }
        val pillRect = RectF(anchorX, anchorY, anchorX + textWidth, anchorY + textHeight)
        canvas.drawRoundRect(pillRect, radius, radius, backgroundPaint)
    }
}

private fun drawSignature(canvas: Canvas, layer: Layer.SignatureLayer) {
    if (layer.points.size < 2) return
    val path = Path()
    path.moveTo(layer.points[0].x, layer.points[0].y)
    layer.points.drop(1).forEach { point -> path.lineTo(point.x, point.y) }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = layer.color
        strokeWidth = layer.width
        style = Paint.Style.STROKE
        alpha = (layer.style.opacity * 255f).toInt()
        layer.style.basicColorMatrix().let { matrix ->
            colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        }
    }
    canvas.save()
    canvas.translate(layer.transform.x, layer.transform.y)
    canvas.scale(layer.transform.scale, layer.transform.scale)
    canvas.drawPath(path, paint)
    canvas.restore()
}

private fun drawAsset(canvas: Canvas, layer: Layer.AssetLayer) {
    val bitmap = layer.bitmap ?: return
    drawPlacedContent(canvas, bitmap, layer.transform, layer.style)
}

private fun drawSvg(canvas: Canvas, layer: Layer.SvgLayer) {
    val bitmap = renderSvg(layer.svgSource) ?: return
    drawPlacedContent(canvas, bitmap, layer.transform, layer.style)
}

/**
 * Rasterizes raw SVG markup through the AndroidSVG (svg-android) renderer into a software ARGB
 * bitmap, then draws it through the shared layer placement pipeline (transform / opacity / grade /
 * outline / pill / shadow).
 */
private fun renderSvg(svgSource: String): Bitmap? {
    return try {
        val svg = SVGBuilder().readFromString(svgSource).build()
        val picture = svg.getPicture()
        val width = picture.width.coerceAtLeast(1)
        val height = picture.height.coerceAtLeast(1)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawPicture(picture)
        }
    } catch (error: Exception) {
        Log.e(TAG, "Failed to render SVG mark: ${error.message}", error)
        null
    }
}

/**
 * Draws a placed bitmap: optional pill backing, optional outline stroke, then the bitmap itself
 * with layer opacity / basic grade / shadow applied.
 */
private fun drawPlacedContent(
    canvas: Canvas,
    bitmap: Bitmap,
    transform: LayerTransform,
    style: LayerStyle,
) {
    val cornerRadius = minOf(bitmap.width, bitmap.height) / 2f

    if (style.pill) {
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(style.background)
            setAlpha((style.opacity * 255f).toInt())
        }
        canvas.save()
        canvas.translate(transform.x, transform.y)
        canvas.scale(transform.scale, transform.scale)
        canvas.drawRoundRect(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), cornerRadius, cornerRadius, backgroundPaint)
        canvas.restore()
    }

    if (style.outline) {
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(style.outlineColor ?: DEFAULT_ASSET_OUTLINE_COLOR)
            setStrokeWidth(style.outlineWidth * transform.scale)
            setStyle(Paint.Style.STROKE)
        }
        canvas.save()
        canvas.translate(transform.x, transform.y)
        canvas.scale(transform.scale, transform.scale)
        canvas.drawRoundRect(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), cornerRadius, cornerRadius, outlinePaint)
        canvas.restore()
    }

    val paint = Paint().apply {
        alpha = (style.opacity * 255f).toInt()
        style.basicColorMatrix().let { matrix ->
            colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        }
        style.shadow?.let { shadow ->
            setShadowLayer(shadow.offsetX, shadow.offsetY, shadow.radius, shadow.color)
        }
    }
    canvas.save()
    canvas.translate(transform.x, transform.y)
    canvas.scale(transform.scale, transform.scale)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    canvas.restore()
}
