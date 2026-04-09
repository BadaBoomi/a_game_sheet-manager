package de.badaboomi.gamesheetmanager

import de.badaboomi.gamesheetmanager.utils.DrawPoint
import de.badaboomi.gamesheetmanager.utils.DrawStroke
import de.badaboomi.gamesheetmanager.utils.DrawingSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the DrawingSerializer utility.
 */
class DrawingSerializerTest {

    @Test
    fun `serialize empty list returns empty JSON array`() {
        val result = DrawingSerializer.serialize(emptyList())
        assertEquals("[]", result)
    }

    @Test
    fun `deserialize empty JSON array returns empty list`() {
        val result = DrawingSerializer.deserialize("[]")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `serialize and deserialize roundtrip preserves data`() {
        val strokes = listOf(
            DrawStroke(
                points = listOf(
                    DrawPoint(10f, 20f),
                    DrawPoint(30f, 40f),
                    DrawPoint(50f, 60f)
                ),
                color = -16777216, // Color.BLACK
                strokeWidth = 8f
            ),
            DrawStroke(
                points = listOf(
                    DrawPoint(100f, 200f),
                    DrawPoint(150f, 250f)
                ),
                color = -65536, // Color.RED
                strokeWidth = 12f
            )
        )

        val serialized = DrawingSerializer.serialize(strokes)
        val deserialized = DrawingSerializer.deserialize(serialized)

        assertEquals(strokes.size, deserialized.size)
        assertEquals(strokes[0].points.size, deserialized[0].points.size)
        assertEquals(strokes[0].color, deserialized[0].color)
        assertEquals(strokes[0].strokeWidth, deserialized[0].strokeWidth, 0.01f)
        assertEquals(strokes[0].points[0].x, deserialized[0].points[0].x, 0.01f)
        assertEquals(strokes[0].points[0].y, deserialized[0].points[0].y, 0.01f)
        assertEquals(strokes[1].color, deserialized[1].color)
        assertEquals(strokes[1].strokeWidth, deserialized[1].strokeWidth, 0.01f)
    }

    @Test
    fun `deserialize invalid JSON returns empty list`() {
        val result = DrawingSerializer.deserialize("not valid json")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deserialize malformed JSON returns empty list`() {
        val result = DrawingSerializer.deserialize("{}")
        assertTrue(result.isEmpty())
    }
}
