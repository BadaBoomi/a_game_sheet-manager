package de.badaboomi.gamesheetmanager.data

/**
 * Status of a game sheet.
 */
enum class GameSheetStatus {
    IN_PROGRESS,
    FINISHED
}

/**
 * Represents an active or finished game sheet based on a template.
 * The drawing data is stored as a serialized JSON string.
 */
data class GameSheet(
    val id: Long = 0,
    val templateId: Long,
    val templateName: String,
    val templateImagePath: String,
    val drawingData: String = "[]",
    val dateStarted: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val status: GameSheetStatus = GameSheetStatus.IN_PROGRESS
)
