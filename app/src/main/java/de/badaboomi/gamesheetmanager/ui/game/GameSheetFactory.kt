package de.badaboomi.gamesheetmanager.ui.game

import de.badaboomi.gamesheetmanager.data.GameSheet

object GameSheetFactory {
    fun createRestartedSheet(currentSheet: GameSheet): GameSheet {
        return GameSheet(
            templateId = currentSheet.templateId,
            templateName = currentSheet.templateName,
            templateImagePath = currentSheet.templateImagePath
        )
    }
}
