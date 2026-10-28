package com.bmdstudios.marked.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LayerGestureTest {

    private fun textLayer() = Layer.TextLayer(
        id = "text-1",
        type = LayerType.TEXT,
        transform = LayerTransform(x = 100f, y = 200f, scale = 1f),
        style = LayerStyle.Solid,
        text = "Hello Marked",
    )

    @Test
    fun applyGesture_scalesLayerUp() {
        val updated = textLayer().applyGesture(zoomChange = 2f, panDeltaX = 0f, panDeltaY = 0f)
        assertEquals(2f, updated.transform.scale, 0.0001f)
    }

    @Test
    fun applyGesture_scalesLayerDown() {
        val updated = textLayer().applyGesture(zoomChange = 0.5f, panDeltaX = 0f, panDeltaY = 0f)
        assertEquals(0.5f, updated.transform.scale, 0.0001f)
    }

    @Test
    fun applyGesture_clampsZoomToSafeBounds() {
        val zoomedIn = textLayer().applyGesture(zoomChange = 50f, panDeltaX = 0f, panDeltaY = 0f)
        assertEquals(8f, zoomedIn.transform.scale, 0.0001f)

        val zoomedOut = textLayer().applyGesture(zoomChange = 0f, panDeltaX = 0f, panDeltaY = 0f)
        // scale 1 * 0 = 0 -> clamped to the minimum zoom bound.
        assertEquals(0.1f, zoomedOut.transform.scale, 0.0001f)
    }

    @Test
    fun applyGesture_movesLayerByPanDeltaInMediaPixels() {
        val updated = textLayer().applyGesture(zoomChange = 1f, panDeltaX = 25f, panDeltaY = 50f)
        assertEquals(125f, updated.transform.x, 0.0001f)
        assertEquals(250f, updated.transform.y, 0.0001f)
    }

    @Test
    fun applyGesture_clampsNegativePanToOrigin() {
        val updated = textLayer().applyGesture(zoomChange = 1f, panDeltaX = -500f, panDeltaY = -500f)
        assertEquals(0f, updated.transform.x, 0.0001f)
        assertEquals(0f, updated.transform.y, 0.0001f)
    }

    private fun svgLayer() = Layer.SvgLayer(
        id = "svg-1",
        type = LayerType.SVG,
        transform = LayerTransform(x = 100f, y = 200f, scale = 1f),
        style = LayerStyle.Solid,
        svgSource = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"/>",
    )

    @Test
    fun applyGesture_scalesSvgLayerUp() {
        val updated = svgLayer().applyGesture(zoomChange = 2f, panDeltaX = 0f, panDeltaY = 0f)
        assertEquals(2f, (updated as Layer.SvgLayer).transform.scale, 0.0001f)
    }

    @Test
    fun applyGesture_movesSvgLayerByPanDeltaInMediaPixels() {
        val updated = svgLayer().applyGesture(zoomChange = 1f, panDeltaX = 25f, panDeltaY = 50f)
        assertEquals(125f, (updated as Layer.SvgLayer).transform.x, 0.0001f)
        assertEquals(250f, (updated as Layer.SvgLayer).transform.y, 0.0001f)
    }

    @Test
    fun withStyle_overlaysGradeOnSvgLayer() {
        val updated = svgLayer().withStyle(LayerStyle(contrast = 1.5f, exposure = 0.8f, saturation = 0.9f))
        val svgLayer = updated as Layer.SvgLayer
        assertEquals(1.5f, svgLayer.style.contrast, 0.0001f)
        assertEquals(0.8f, svgLayer.style.exposure, 0.0001f)
        assertEquals(0.9f, svgLayer.style.saturation, 0.0001f)
    }

    @Test
    fun applyGesture_signatureLayerIsUnchanged() {
        val signature = Layer.SignatureLayer(
            id = "sig-1",
            type = LayerType.SIGNATURE,
            transform = LayerTransform(x = 10f, y = 20f, scale = 1f),
            style = LayerStyle.Solid,
            color = -16777216,
            width = 4f,
            points = listOf(Layer.SignaturePoint(x = 1f, y = 2f), Layer.SignaturePoint(x = 3f, y = 4f)),
        )
        assertSame(signature, signature.applyGesture(zoomChange = 2f, panDeltaX = 5f, panDeltaY = 6f))
    }
}
