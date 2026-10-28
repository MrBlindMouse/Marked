package com.bmdstudios.marked.model

import kotlinx.serialization.Serializable

/**
 * Position and size of a layer within the media it is placed on.
 *
 * @property x Horizontal anchor in media pixels (layer-local origin). Text/timestamp anchors
 * top-left; assets and stickers anchor the center.
 * @property y Vertical anchor in media pixels (layer-local origin). Text/timestamp anchors
 * top-left; assets and stickers anchor the center.
 * @property scale Size multiplier. 1f is the layer's authored size.
 */
@Serializable
data class LayerTransform(
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
) {
}
