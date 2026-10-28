package com.bmdstudios.marked.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LayerStyleTest {

    private fun value(matrix: FloatArray, row: Int, col: Int): Float = matrix[row * 5 + col]

    @Test
    fun neutralStyleYieldsIdentityColorMatrix() {
        val matrix = LayerStyle().basicColorMatrix()

        // Identity: RGB gains on the channel diagonal, neutral offsets, alpha untouched.
        assertEquals(1f, value(matrix, 0, 0), 0.0001f)
        assertEquals(0f, value(matrix, 0, 1), 0.0001f)
        assertEquals(0f, value(matrix, 0, 4), 0.0001f)
        assertEquals(1f, value(matrix, 1, 1), 0.0001f)
        assertEquals(0f, value(matrix, 1, 4), 0.0001f)
        assertEquals(1f, value(matrix, 2, 2), 0.0001f)
        assertEquals(0f, value(matrix, 2, 4), 0.0001f)
        assertEquals(1f, value(matrix, 3, 3), 0.0001f)
        assertEquals(0f, value(matrix, 3, 4), 0.0001f)
        assertEquals(1f, value(matrix, 4, 4), 0.0001f)
    }

    @Test
    fun contrastScalesPixelValuesAroundMidGray() {
        val matrix = LayerStyle(contrast = 2f).basicColorMatrix()

        // out = 2 * (in - 0.5) + 0.5 = 2 * in - 0.5 per channel; alpha is untouched.
        assertEquals(2f, value(matrix, 0, 0), 0.0001f) // R gain
        assertEquals(-0.5f, value(matrix, 0, 4), 0.0001f) // R offset
        assertEquals(2f, value(matrix, 1, 1), 0.0001f) // G gain
        assertEquals(-0.5f, value(matrix, 1, 4), 0.0001f) // G offset
        assertEquals(2f, value(matrix, 2, 2), 0.0001f) // B gain
        assertEquals(-0.5f, value(matrix, 2, 4), 0.0001f) // B offset
        assertEquals(1f, value(matrix, 3, 3), 0.0001f) // alpha gain stays 1
    }

    @Test
    fun exposureScalesAllColorChannels() {
        val matrix = LayerStyle(exposure = 2f).basicColorMatrix()

        // out = 2 * in; offsets stay neutral.
        assertEquals(2f, value(matrix, 0, 0), 0.0001f) // R gain
        assertEquals(2f, value(matrix, 1, 1), 0.0001f) // G gain
        assertEquals(2f, value(matrix, 2, 2), 0.0001f) // B gain
        assertEquals(0f, value(matrix, 0, 4), 0.0001f) // R offset
        assertEquals(0f, value(matrix, 1, 4), 0.0001f) // G offset
        assertEquals(0f, value(matrix, 2, 4), 0.0001f) // B offset
    }

    @Test
    fun saturationZeroGraysOutAllChannels() {
        val matrix = LayerStyle(saturation = 0f).basicColorMatrix()

        // Grayscale: out = luma = 0.299R + 0.587G + 0.114B.
        assertEquals(0.299f, value(matrix, 0, 0), 0.0001f)
        assertEquals(0.587f, value(matrix, 1, 1), 0.0001f)
        assertEquals(0.114f, value(matrix, 2, 2), 0.0001f)
        assertEquals(0f, value(matrix, 0, 4), 0.0001f)
    }

    @Test
    fun saturationOneKeepsContrastAndExposureGrade() {
        val graded = LayerStyle(contrast = 1.5f, exposure = 1.25f, saturation = 1f).basicColorMatrix()

        // With saturation at neutral the grade collapses back to contrast / exposure only.
        assertEquals(1.5f * 1.25f, value(graded, 0, 0), 0.0001f)
        assertEquals((1f - 1.5f) / 2f, value(graded, 0, 4), 0.0001f)
    }
}
