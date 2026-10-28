package com.bmdstudios.marked.editor

import com.bmdstudios.marked.model.TimestampPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class TimestampAnchorTest {

    @Test
    fun topLeftTimestampAnchorsAtOrigin() {
        val transform = timestampAnchor(
            sourceWidth = 1080f,
            sourceHeight = 1920f,
            position = TimestampPosition.TOP_LEFT,
            textWidth = 100f,
            textHeight = 50f,
        )
        assertEquals(0f, transform.x, 0.0001f)
        assertEquals(0f, transform.y, 0.0001f)
        assertEquals(1f, transform.scale, 0.0001f)
    }

    @Test
    fun topRightTimestampAnchorsAtTopRightEdge() {
        val transform = timestampAnchor(
            sourceWidth = 1080f,
            sourceHeight = 1920f,
            position = TimestampPosition.TOP_RIGHT,
            textWidth = 200f,
            textHeight = 50f,
        )
        assertEquals(880f, transform.x, 0.0001f)
        assertEquals(0f, transform.y, 0.0001f)
    }

    @Test
    fun bottomLeftTimestampAnchorsAtBottomLeftEdge() {
        val transform = timestampAnchor(
            sourceWidth = 1080f,
            sourceHeight = 1920f,
            position = TimestampPosition.BOTTOM_LEFT,
            textWidth = 200f,
            textHeight = 50f,
        )
        assertEquals(0f, transform.x, 0.0001f)
        assertEquals(1870f, transform.y, 0.0001f)
    }

    @Test
    fun bottomRightTimestampAnchorsAtBottomRightEdge() {
        val transform = timestampAnchor(
            sourceWidth = 1080f,
            sourceHeight = 1920f,
            position = TimestampPosition.BOTTOM_RIGHT,
            textWidth = 300f,
            textHeight = 60f,
        )
        assertEquals(780f, transform.x, 0.0001f)
        assertEquals(1860f, transform.y, 0.0001f)
    }

    @Test
    fun anchorNeverPlacesTextOutsideSourceBounds() {
        val transform = timestampAnchor(
            sourceWidth = 500f,
            sourceHeight = 800f,
            position = TimestampPosition.BOTTOM_RIGHT,
            textWidth = 900f,
            textHeight = 400f,
        )
        assertEquals(0f, transform.x, 0.0001f)
        assertEquals(400f, transform.y, 0.0001f)
    }
}
