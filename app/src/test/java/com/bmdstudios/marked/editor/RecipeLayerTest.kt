package com.bmdstudios.marked.editor

import com.bmdstudios.marked.model.Layer
import com.bmdstudios.marked.model.LayerStyle
import com.bmdstudios.marked.model.LayerTransform
import com.bmdstudios.marked.model.LayerType
import com.bmdstudios.marked.model.TimestampFormat
import com.bmdstudios.marked.model.TimestampPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecipeLayerTest {

    @Test
    fun textRecipeLayerRehydratesTextPayload() {
        val layer = RecipeLayer(
            type = LayerType.TEXT.name,
            transform = LayerTransform(x = 10f, y = 20f, scale = 2f),
            style = LayerStyle(opacity = 0.9f),
            payload = RecipePayload.Text(
                text = "Hello Marked",
                fontSize = 64f,
                color = -0x1,
                bold = true,
                fontFamily = "sans",
                tracking = 4f,
            ),
        ).toLayer(0)

        assertEquals(LayerType.TEXT, layer.type)
        assertEquals("recipe-layer-0", layer.id)
        assertEquals(10f, layer.transform.x, 0.0001f)
        assertEquals(20f, layer.transform.y, 0.0001f)
        assertEquals(2f, layer.transform.scale, 0.0001f)
        assertEquals(0.9f, layer.style.opacity, 0.0001f)
        val textLayer = layer as Layer.TextLayer
        assertEquals("Hello Marked", textLayer.text)
        assertEquals(64f, textLayer.fontSize, 0.0001f)
        assertEquals(true, textLayer.bold)
        assertEquals("sans", textLayer.fontFamily)
        assertEquals(4f, textLayer.tracking, 0.0001f)
    }

    @Test
    fun timestampRecipeLayerRehydratesTimestampPayload() {
        val layer = RecipeLayer(
            type = LayerType.TIMESTAMP.name,
            transform = LayerTransform(x = 0f, y = 0f),
            style = LayerStyle.Solid,
            payload = RecipePayload.Timestamp(
                format = TimestampFormat.HH_MM_SS,
                fontSize = 72f,
                position = TimestampPosition.BOTTOM_RIGHT,
            ),
        ).toLayer(3)

        assertEquals(LayerType.TIMESTAMP, layer.type)
        assertEquals("recipe-layer-3", layer.id)
        assertEquals(72f, (layer as Layer.TimestampLayer).fontSize, 0.0001f)
        assertEquals(TimestampPosition.BOTTOM_RIGHT, (layer as Layer.TimestampLayer).position)
    }

    @Test
    fun signatureRecipeLayerRehydratesSignaturePayloadWithPoints() {
        val layer = RecipeLayer(
            type = LayerType.SIGNATURE.name,
            transform = LayerTransform(x = 5f, y = 6f),
            style = LayerStyle.Solid,
            payload = RecipePayload.Signature(
                color = -16777216,
                width = 8f,
                points = listOf(Layer.SignaturePoint(x = 1f, y = 2f)),
            ),
        ).toLayer(1)

        assertEquals(LayerType.SIGNATURE, layer.type)
        assertEquals("recipe-layer-1", layer.id)
        val signatureLayer = layer as Layer.SignatureLayer
        assertEquals(-16777216, signatureLayer.color)
        assertEquals(8f, signatureLayer.width, 0.0001f)
        assertEquals(1, signatureLayer.points.size)
    }

    @Test
    fun stickerRecipeLayerRehydratesStickerPayload() {
        val layer = RecipeLayer(
            type = LayerType.STICKER.name,
            transform = LayerTransform(x = 100f, y = 100f),
            style = LayerStyle(),
            payload = RecipePayload.Sticker(emoji = "🎉", fontSize = 96f),
        ).toLayer(2)

        assertEquals(LayerType.STICKER, layer.type)
        assertEquals("recipe-layer-2", layer.id)
        val stickerLayer = layer as Layer.StickerLayer
        assertEquals("🎉", stickerLayer.emoji)
        assertEquals(96f, stickerLayer.fontSize, 0.0001f)
    }

    @Test
    fun svgRecipeLayerRehydratesSvgPayload() {
        val layer = RecipeLayer(
            type = LayerType.SVG.name,
            transform = LayerTransform(x = 12f, y = 34f, scale = 2f),
            style = LayerStyle(opacity = 0.7f, contrast = 1.2f),
            payload = RecipePayload.Svg(svgSource = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"/>"),
        ).toLayer(4)

        assertEquals(LayerType.SVG, layer.type)
        assertEquals("recipe-layer-4", layer.id)
        assertEquals(12f, layer.transform.x, 0.0001f)
        assertEquals(34f, layer.transform.y, 0.0001f)
        assertEquals(2f, layer.transform.scale, 0.0001f)
        assertEquals(0.7f, layer.style.opacity, 0.0001f)
        assertEquals(1.2f, layer.style.contrast, 0.0001f)
        val svgLayer = layer as Layer.SvgLayer
        assertEquals("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"/>", svgLayer.svgSource)
    }

    @Test
    fun unsupportedRecipeLayerTypeThrows() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            RecipeLayer(
                type = LayerType.ASSET.name,
                transform = LayerTransform(),
                style = LayerStyle(),
                payload = null,
            ).toLayer(0)
        }
        assertEquals("Unsupported recipe layer type: ASSET", error.message)
    }
}
