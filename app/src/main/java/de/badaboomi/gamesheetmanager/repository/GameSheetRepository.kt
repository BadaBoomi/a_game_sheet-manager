package de.badaboomi.gamesheetmanager.repository

import android.content.ContentValues
import android.content.Context
import de.badaboomi.gamesheetmanager.data.AppDatabase
import de.badaboomi.gamesheetmanager.data.GameSheet
import de.badaboomi.gamesheetmanager.data.GameSheetStatus

/**
 * Repository for managing active game sheets in the database.
 */
class GameSheetRepository(context: Context) {

    private val db = AppDatabase(context)

    fun getAllGameSheets(): List<GameSheet> {
        val sheets = mutableListOf<GameSheet>()
        val cursor = db.readableDatabase.query(
            AppDatabase.TABLE_GAME_SHEETS,
            null, null, null, null, null,
            "${AppDatabase.COL_SHEET_LAST_MODIFIED} DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                sheets.add(cursorToGameSheet(it))
            }
        }
        return sheets
    }

    fun getGameSheetById(id: Long): GameSheet? {
        val cursor = db.readableDatabase.query(
            AppDatabase.TABLE_GAME_SHEETS,
            null,
            "${AppDatabase.COL_SHEET_ID} = ?",
            arrayOf(id.toString()),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) cursorToGameSheet(it) else null
        }
    }

    /**
     * Returns the active (IN_PROGRESS) game sheet for the given template, if any.
     */
    fun getActiveSheetForTemplate(templateId: Long): GameSheet? {
        val cursor = db.readableDatabase.query(
            AppDatabase.TABLE_GAME_SHEETS,
            null,
            "${AppDatabase.COL_SHEET_TEMPLATE_ID} = ? AND ${AppDatabase.COL_SHEET_STATUS} = ?",
            arrayOf(templateId.toString(), GameSheetStatus.IN_PROGRESS.name),
            null, null,
            "${AppDatabase.COL_SHEET_LAST_MODIFIED} DESC",
            "1"
        )
        return cursor.use {
            if (it.moveToFirst()) cursorToGameSheet(it) else null
        }
    }

    fun insertGameSheet(sheet: GameSheet): Long {
        val values = gameSheetToContentValues(sheet)
        return db.writableDatabase.insert(AppDatabase.TABLE_GAME_SHEETS, null, values)
    }

    fun updateGameSheet(sheet: GameSheet): Int {
        val values = gameSheetToContentValues(sheet)
        return db.writableDatabase.update(
            AppDatabase.TABLE_GAME_SHEETS,
            values,
            "${AppDatabase.COL_SHEET_ID} = ?",
            arrayOf(sheet.id.toString())
        )
    }

    fun deleteGameSheet(id: Long): Int {
        return db.writableDatabase.delete(
            AppDatabase.TABLE_GAME_SHEETS,
            "${AppDatabase.COL_SHEET_ID} = ?",
            arrayOf(id.toString())
        )
    }

    private fun cursorToGameSheet(cursor: android.database.Cursor): GameSheet {
        return GameSheet(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(AppDatabase.COL_SHEET_ID)),
            templateId = cursor.getLong(cursor.getColumnIndexOrThrow(AppDatabase.COL_SHEET_TEMPLATE_ID)),
            templateName = cursor.getString(cursor.getColumnIndexOrThrow(AppDatabase.COL_SHEET_TEMPLATE_NAME)),
            templateImagePath = cursor.getString(cursor.getColumnIndexOrThrow(AppDatabase.COL_SHEET_TEMPLATE_IMAGE_PATH)),
            drawingData = cursor.getString(cursor.getColumnIndexOrThrow(AppDatabase.COL_SHEET_DRAWING_DATA)),
            dateStarted = cursor.getLong(cursor.getColumnIndexOrThrow(AppDatabase.COL_SHEET_DATE_STARTED)),
            lastModified = cursor.getLong(cursor.getColumnIndexOrThrow(AppDatabase.COL_SHEET_LAST_MODIFIED)),
            status = GameSheetStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(AppDatabase.COL_SHEET_STATUS)))
        )
    }

    private fun gameSheetToContentValues(sheet: GameSheet): ContentValues {
        return ContentValues().apply {
            put(AppDatabase.COL_SHEET_TEMPLATE_ID, sheet.templateId)
            put(AppDatabase.COL_SHEET_TEMPLATE_NAME, sheet.templateName)
            put(AppDatabase.COL_SHEET_TEMPLATE_IMAGE_PATH, sheet.templateImagePath)
            put(AppDatabase.COL_SHEET_DRAWING_DATA, sheet.drawingData)
            put(AppDatabase.COL_SHEET_DATE_STARTED, sheet.dateStarted)
            put(AppDatabase.COL_SHEET_LAST_MODIFIED, sheet.lastModified)
            put(AppDatabase.COL_SHEET_STATUS, sheet.status.name)
        }
    }
}
