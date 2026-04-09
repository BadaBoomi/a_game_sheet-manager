package de.badaboomi.gamesheetmanager.data

/**
 * Represents a completed game sheet saved in the Hall of Fame.
 * Hall of Fame entries are read-only and cannot be modified.
 */
data class HallOfFameEntry(
    val id: Long = 0,
    val gameName: String,
    val templateId: Long,
    val templateName: String,
    val templateImagePath: String,
    val drawingData: String,
    val dateCompleted: Long = System.currentTimeMillis()
)
