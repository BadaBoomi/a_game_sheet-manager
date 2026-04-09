package de.badaboomi.gamesheetmanager.repository

import android.content.ContentValues
import android.content.Context
import de.badaboomi.gamesheetmanager.data.AppDatabase
import de.badaboomi.gamesheetmanager.data.HallOfFameEntry

/**
 * Repository for managing Hall of Fame entries in the database.
 * Hall of Fame entries are read-only once saved.
 */
class HallOfFameRepository(context: Context) {

    private val db = AppDatabase(context)

    fun getAllEntries(): List<HallOfFameEntry> {
        val entries = mutableListOf<HallOfFameEntry>()
        val cursor = db.readableDatabase.query(
            AppDatabase.TABLE_HALL_OF_FAME,
            null, null, null, null, null,
            "${AppDatabase.COL_HOF_DATE_COMPLETED} DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                entries.add(cursorToEntry(it))
            }
        }
        return entries
    }

    fun getEntryById(id: Long): HallOfFameEntry? {
        val cursor = db.readableDatabase.query(
            AppDatabase.TABLE_HALL_OF_FAME,
            null,
            "${AppDatabase.COL_HOF_ID} = ?",
            arrayOf(id.toString()),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) cursorToEntry(it) else null
        }
    }

    fun insertEntry(entry: HallOfFameEntry): Long {
        val values = ContentValues().apply {
            put(AppDatabase.COL_HOF_GAME_NAME, entry.gameName)
            put(AppDatabase.COL_HOF_TEMPLATE_ID, entry.templateId)
            put(AppDatabase.COL_HOF_TEMPLATE_NAME, entry.templateName)
            put(AppDatabase.COL_HOF_TEMPLATE_IMAGE_PATH, entry.templateImagePath)
            put(AppDatabase.COL_HOF_DRAWING_DATA, entry.drawingData)
            put(AppDatabase.COL_HOF_DATE_COMPLETED, entry.dateCompleted)
        }
        return db.writableDatabase.insert(AppDatabase.TABLE_HALL_OF_FAME, null, values)
    }

    fun deleteEntry(id: Long): Int {
        return db.writableDatabase.delete(
            AppDatabase.TABLE_HALL_OF_FAME,
            "${AppDatabase.COL_HOF_ID} = ?",
            arrayOf(id.toString())
        )
    }

    private fun cursorToEntry(cursor: android.database.Cursor): HallOfFameEntry {
        return HallOfFameEntry(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(AppDatabase.COL_HOF_ID)),
            gameName = cursor.getString(cursor.getColumnIndexOrThrow(AppDatabase.COL_HOF_GAME_NAME)),
            templateId = cursor.getLong(cursor.getColumnIndexOrThrow(AppDatabase.COL_HOF_TEMPLATE_ID)),
            templateName = cursor.getString(cursor.getColumnIndexOrThrow(AppDatabase.COL_HOF_TEMPLATE_NAME)),
            templateImagePath = cursor.getString(cursor.getColumnIndexOrThrow(AppDatabase.COL_HOF_TEMPLATE_IMAGE_PATH)),
            drawingData = cursor.getString(cursor.getColumnIndexOrThrow(AppDatabase.COL_HOF_DRAWING_DATA)),
            dateCompleted = cursor.getLong(cursor.getColumnIndexOrThrow(AppDatabase.COL_HOF_DATE_COMPLETED))
        )
    }
}
