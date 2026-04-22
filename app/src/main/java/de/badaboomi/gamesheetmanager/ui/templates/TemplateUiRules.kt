package de.badaboomi.gamesheetmanager.ui.templates

object TemplateUiRules {
    fun showDirectStartButton(mode: String): Boolean {
        return mode == TemplateListActivity.MODE_MANAGE
    }
}
