package com.bmdstudios.marked.editor

import com.bmdstudios.marked.model.Layer
import com.bmdstudios.marked.model.LayerStyle
import com.bmdstudios.marked.model.LayerTransform
import com.bmdstudios.marked.model.LayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureAuthoredPointTest {

    private fun signatureLayer(
        transform: LayerTransform,
        points: List<Layer.SignaturePoint> = emptyList(),
    ): Layer.SignatureLayer = Layer.SignatureLayer(
        id = "sig-1",
        type = LayerType.SIGNATURE,
        transform = transform,
        style = LayerStyle.Solid,
        color = -16777216,
        width = 6f,
        points = points,
    )

    @Test
    fun authoredPointDividesDisplayDeltaByScale() {
        val layer = signatureLayer(LayerTransform(x = 100f, y = 50f, scale = 2f))
        val point = signatureAuthoredPoint(layer = layer, displayX = 210f, displayY = 110f)
        assertEquals(5f, point.x, 0.0001f)
        assertEquals(5f, point.y, 0.0001f)
    }

    @Test
    fun authoredPointSubtractsLayerAnchor() {
        val layer = signatureLayer(LayerTransform(x = 40f, y = 20f, scale = 1f))
        val point = signatureAuthoredPoint(layer = layer, displayX = 55f, displayY = 27f)
        assertEquals(15f, point.x, 0.0001f)
        assertEquals(7f, point.y, 0.0001f)
    }

    @Test
    fun authoredPointStartsAtOriginWhenAnchorIsZero() {
        val layer = signatureLayer(LayerTransform(x = 0f, y = 0f, scale = 1f))
        val point = signatureAuthoredPoint(layer = layer, displayX = 12f, displayY = 34f)
        assertEquals(12f, point.x, 0.0001f)
        assertEquals(34f, point.y, 0.0001f)
    }

    @Test
    fun shortStrokesAreStillCapturableButNotRenderable() {
        val layer = signatureLayer(
            LayerTransform(x = 0f, y = 0f, scale = 1f),
            points = listOf(Layer.SignaturePoint(x = 1f, y = 2f)),
        )
        assertEquals(1, layer.points.size)
        assertTrue(layer.points.size < 2)
    }
}
