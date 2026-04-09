package de.badaboomi.gamesheetmanager.data

/**
 * Represents a paper template (scanned game sheet template).
 */
data class Template(
    val id: Long = 0,
    val name: String,
    val imagePath: String,
    val dateCreated: Long = System.currentTimeMillis()
)
