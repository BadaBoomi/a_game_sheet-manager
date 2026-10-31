package de.badaboomi.gamesheetmanager.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingMenuRotationTest {
    @Test
    fun `initial orientation follows screen orientation`() {
        assertEquals(0f, FloatingMenuRotation.rotationForOrientation(false), 0.01f)
        assertEquals(90f, FloatingMenuRotation.rotationForOrientation(true), 0.01f)
    }

    @Test
    fun `manual rotation advances in 90 degree steps`() {
        assertEquals(90f, FloatingMenuRotation.nextRotation(0f), 0.01f)
        assertEquals(180f, FloatingMenuRotation.nextRotation(90f), 0.01f)
        assertEquals(270f, FloatingMenuRotation.nextRotation(180f), 0.01f)
        assertEquals(0f, FloatingMenuRotation.nextRotation(270f), 0.01f)
    }
}
