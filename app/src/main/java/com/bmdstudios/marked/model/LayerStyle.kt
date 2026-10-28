package com.bmdstudios.marked.model

import kotlinx.serialization.Serializable

/**
 * Styling applied to a layer. Mirrors DESIGN.md: opacity, shadow, outline and pill are
 * layer-level concerns; the pill is a styled backing rect owned by the layer itself.
 *
 * @property opacity Layer opacity multiplier in the range 0f..1f.
 * @property background Backing color used when [pill] is set, ARGB int. Transparent by default.
 * @property shadow Optional shadow drawn behind the layer content.
 * @property outline Draw an outline around the layer content.
 * @property outlineWidth Outline stroke width in layer-local units.
 * @property outlineColor Optional outline stroke color; falls back to the layer content
 * color (text color / white for bitmaps) when null.
 * @property pill Draw a rounded backing rect behind the layer content.
 * @property contrast Basic editing grade: contrast multiplier around mid-gray (1f = neutral).
 * @property exposure Basic editing grade: brightness multiplier (1f = neutral).
 * @property saturation Basic editing grade: color saturation multiplier (1f = neutral).
 */
@Serializable
data class LayerStyle(
    val opacity: Float = 1f,
    val background: Int = 0x00000000,
    val shadow: Shadow? = null,
    val outline: Boolean = false,
    val outlineWidth: Float = 2f,
    val outlineColor: Int? = null,
    val pill: Boolean = false,
    val contrast: Float = 1f,
    val exposure: Float = 1f,
    val saturation: Float = 1f,
) {
    companion object {
        val Solid: LayerStyle = LayerStyle(opacity = 1f)
    }
}

/**
 * Builds the 5x5 (row-major, RGBA) color matrix for the layer's basic editing grade
 * (contrast / exposure / saturation). All multipliers are around neutral (1f / 1f / 1f), so a
 * neutral style yields the identity matrix.
 *
 * Contrast maps `out = contrast * (in - 0.5) + 0.5`; exposure scales the input first; saturation
 * mixes the channel towards the luma-weighted mean of the pixel.
 */
internal fun LayerStyle.basicColorMatrix(): FloatArray {
    // f(in) = contrast * (exposure * in - 0.5) + 0.5 = g * in + o per channel,
    // with g = contrast * exposure and o = (1 - contrast) / 2. The final grade combines
    // saturation on top: out = saturation * f(in) + (1 - saturation) * luma(f(in)).
    val g = contrast * exposure
    val o = (1f - contrast) / 2f
    val lumaWeights = floatArrayOf(0.299f, 0.587f, 0.114f)

    val matrix = FloatArray(25)
    for (row in 0..4) {
        for (col in 0..4) {
            matrix[row * 5 + col] = when {
                row < 3 && col < 3 -> g * (saturation * (if (row == col) 1f else 0f) + (1f - saturation) * lumaWeights[col])
                row < 3 && col == 4 -> o
                row == 3 && col == 3 -> 1f
                row == 3 && col == 4 -> 0f
                row == 4 && col == 4 -> 1f
                else -> 0f
            }
        }
    }
    return matrix
}

/** Shadow behind a layer. */
@Serializable
data class Shadow(
    val color: Int,
    val radius: Float = 8f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)
