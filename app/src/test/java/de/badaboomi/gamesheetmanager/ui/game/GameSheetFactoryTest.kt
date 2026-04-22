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
}
