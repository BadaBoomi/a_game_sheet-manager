package de.badaboomi.gamesheetmanager.ui.game

import de.badaboomi.gamesheetmanager.data.GameSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GameSheetFactoryTest {

    @Test
    fun `createRestartedSheet keeps template and resets drawing`() {
        val current = GameSheet(
            id = 42,
            templateId = 7,
            templateName = "Kniffel",
            templateImagePath = "/tmp/kniffel.jpg",
            drawingData = "[{\"x\":1}]",
            dateStarted = 1000,
            lastModified = 2000
        )

        val restarted = GameSheetFactory.createRestartedSheet(current)

        assertEquals(current.templateId, restarted.templateId)
        assertEquals(current.templateName, restarted.templateName)
        assertEquals(current.templateImagePath, restarted.templateImagePath)
        assertEquals("[]", restarted.drawingData)
        assertNotEquals(current.id, restarted.id)
    }

    @Test
    fun `hamburger menu rotation starts in game orientation and advances by 90 degrees`() {
        assertEquals(90f, GameSheetActivity.getInitialHamburgerMenuRotation(true), 0.01f)
        assertEquals(0f, GameSheetActivity.getInitialHamburgerMenuRotation(false), 0.01f)

        assertEquals(90f, GameSheetActivity.rotateHamburgerMenu(0f), 0.01f)
        assertEquals(180f, GameSheetActivity.rotateHamburgerMenu(90f), 0.01f)
        assertEquals(270f, GameSheetActivity.rotateHamburgerMenu(180f), 0.01f)
        assertEquals(0f, GameSheetActivity.rotateHamburgerMenu(270f), 0.01f)
    }
}
