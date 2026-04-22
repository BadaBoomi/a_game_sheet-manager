package de.badaboomi.gamesheetmanager.ui.templates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateUiRulesTest {

    @Test
    fun `showDirectStartButton true in manage mode`() {
        assertTrue(TemplateUiRules.showDirectStartButton(TemplateListActivity.MODE_MANAGE))
    }

    @Test
    fun `showDirectStartButton false in select mode`() {
        assertFalse(TemplateUiRules.showDirectStartButton(TemplateListActivity.MODE_SELECT))
    }
}
