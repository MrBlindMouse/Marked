package com.bmdstudios.marked.model

import kotlinx.serialization.Serializable

/**
 * A placed mark on top of the media. Flat model: every layer carries its [transform] and
 * [style] plus the type-specific payload it needs to render (see DESIGN.md).
 */
sealed interface Layer {
    /** Stable layer identity used for selection, undo and list operations. */
    val id: String

    /** What kind of mark this layer renders as. */
    val type: LayerType

    /** Position / size of the mark inside the media. */
    val transform: LayerTransform

    /** Styling applied on top of the type payload. */
    val style: LayerStyle

    /**
     * Applies an in-progress pinch/pan gesture (zoom ratio delta + pan delta in media pixels)
     * to this layer and returns the updated layer.
     */
    fun applyGesture(zoomChange: Float, panDeltaX: Float, panDeltaY: Float): Layer = when (this) {
        is Layer.TextLayer -> copy(
            transform = transform.copy(
                scale = (transform.scale * zoomChange).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE),
                x = (transform.x + panDeltaX / transform.scale).coerceAtLeast(0f),
                y = (transform.y + panDeltaY / transform.scale).coerceAtLeast(0f),
            ),
        )

        is Layer.StickerLayer -> copy(
            transform = transform.copy(
                scale = (transform.scale * zoomChange).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE),
                x = (transform.x + panDeltaX / transform.scale).coerceAtLeast(0f),
                y = (transform.y + panDeltaY / transform.scale).coerceAtLeast(0f),
            ),
        )

        is Layer.TimestampLayer -> copy(
            transform = transform.copy(
                scale = (transform.scale * zoomChange).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE),
                x = (transform.x + panDeltaX / transform.scale).coerceAtLeast(0f),
                y = (transform.y + panDeltaY / transform.scale).coerceAtLeast(0f),
            ),
        )

        is Layer.AssetLayer -> copy(
            transform = transform.copy(
                scale = (transform.scale * zoomChange).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE),
                x = (transform.x + panDeltaX / transform.scale).coerceAtLeast(0f),
                y = (transform.y + panDeltaY / transform.scale).coerceAtLeast(0f),
            ),
        )

        is Layer.SvgLayer -> copy(
            transform = transform.copy(
                scale = (transform.scale * zoomChange).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE),
                x = (transform.x + panDeltaX / transform.scale).coerceAtLeast(0f),
                y = (transform.y + panDeltaY / transform.scale).coerceAtLeast(0f),
            ),
        )

        // Signature strokes are edited point-by-point, not via transform gestures.
        is Layer.SignatureLayer -> this
    }

    /**
     * Returns a copy of this layer with the given [style] overlaid. Editing-grade controls
     * mutate individual style fields, so they route through here instead of assuming a
     * concrete layer type.
     */
    fun withStyle(style: LayerStyle): Layer = when (this) {
        is Layer.TextLayer -> copy(style = style)
        is Layer.TimestampLayer -> copy(style = style)
        is Layer.StickerLayer -> copy(style = style)
        is Layer.SignatureLayer -> copy(style = style)
        is Layer.AssetLayer -> copy(style = style)
        is Layer.SvgLayer -> copy(style = style)
    }

    /** Text mark content. */
    data class TextLayer(
        override val id: String,
        override val type: LayerType,
        override val transform: LayerTransform,
        override val style: LayerStyle,
        val text: String,
        val fontSize: Float = 48f,
        val color: Int = -0x1, // opaque white (ARGB 0xFFFFFFFF)
        val bold: Boolean = false,
        val fontFamily: String? = null,
        val tracking: Float = 0f,
    ) : Layer

    /** Timestamp mark content. */
    data class TimestampLayer(
        override val id: String,
        override val type: LayerType,
        override val transform: LayerTransform,
        override val style: LayerStyle,
        /** Fixed time-of-day format preset. */
        val format: TimestampFormat = TimestampFormat.HH_MM_SS,
        /** Layer-local font size in authored units. */
        val fontSize: Float = 64f,
        /** Corner placement preset this timestamp mark was created with. */
        val position: TimestampPosition = TimestampPosition.TOP_LEFT,
    ) : Layer

    /** Sticker mark content (emoji glyph). */
    data class StickerLayer(
        override val id: String,
        override val type: LayerType,
        override val transform: LayerTransform,
        override val style: LayerStyle,
        val emoji: String = "😀",
        val fontSize: Float = 64f,
    ) : Layer

    /** Freehand signature stroke. */
    data class SignatureLayer(
        override val id: String,
        override val type: LayerType,
        override val transform: LayerTransform,
        override val style: LayerStyle,
        val color: Int = -16777216, // opaque black (ARGB 0xFF000000)
        val width: Float = 6f,
        val points: List<SignaturePoint> = emptyList(),
    ) : Layer

    /** PNG asset mark. */
    data class AssetLayer(
        override val id: String,
        override val type: LayerType,
        override val transform: LayerTransform,
        override val style: LayerStyle,
        val bitmap: android.graphics.Bitmap? = null,
    ) : Layer

    /** Vector SVG mark, rendered through the AndroidSVG renderer. */
    data class SvgLayer(
        override val id: String,
        override val type: LayerType,
        override val transform: LayerTransform,
        override val style: LayerStyle,
        /** Raw SVG markup; rehydrated into a bitmap at render time. */
        val svgSource: String,
    ) : Layer

    /** A single point on a signature stroke. */
    @Serializable
    data class SignaturePoint(
        val x: Float,
        val y: Float,
    )
}

/** Safe zoom bounds for editor layers. */
private const val MIN_LAYER_SCALE = 0.1f
private const val MAX_LAYER_SCALE = 8f
