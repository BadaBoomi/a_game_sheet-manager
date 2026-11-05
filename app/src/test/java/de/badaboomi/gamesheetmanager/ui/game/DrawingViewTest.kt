package de.badaboomi.gamesheetmanager.ui.game

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrawingViewTest {

    @Test
    fun `system gesture exclusion covers the full drawing area`() {
        val rect = DrawingView.createSystemGestureExclusionRect(200, 300)

        assertEquals(Rect(0, 0, 200, 300), rect)
    }

    @Test
    fun `system gesture exclusion ignores empty dimensions`() {
        assertNull(DrawingView.createSystemGestureExclusionRect(0, 300))
        assertNull(DrawingView.createSystemGestureExclusionRect(200, 0))
    }
}
