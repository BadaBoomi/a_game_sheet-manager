package de.badaboomi.gamesheetmanager.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite database helper for the GameSheetManager app.
 * Manages Templates, GameSheets, and HallOfFame entries.
 */
class AppDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "game_sheet_manager.db"
        const val DATABASE_VERSION = 1

        // Templates table
        const val TABLE_TEMPLATES = "templates"
        const val COL_TEMPLATE_ID = "_id"
        const val COL_TEMPLATE_NAME = "name"
        const val COL_TEMPLATE_IMAGE_PATH = "image_path"
        const val COL_TEMPLATE_DATE_CREATED = "date_created"

        // GameSheets table
        const val TABLE_GAME_SHEETS = "game_sheets"
        const val COL_SHEET_ID = "_id"
        const val COL_SHEET_TEMPLATE_ID = "template_id"
        const val COL_SHEET_TEMPLATE_NAME = "template_name"
        const val COL_SHEET_TEMPLATE_IMAGE_PATH = "template_image_path"
        const val COL_SHEET_DRAWING_DATA = "drawing_data"
        const val COL_SHEET_DATE_STARTED = "date_started"
        const val COL_SHEET_LAST_MODIFIED = "last_modified"
        const val COL_SHEET_STATUS = "status"

        // HallOfFame table
        const val TABLE_HALL_OF_FAME = "hall_of_fame"
        const val COL_HOF_ID = "_id"
        const val COL_HOF_GAME_NAME = "game_name"
        const val COL_HOF_TEMPLATE_ID = "template_id"
        const val COL_HOF_TEMPLATE_NAME = "template_name"
        const val COL_HOF_TEMPLATE_IMAGE_PATH = "template_image_path"
        const val COL_HOF_DRAWING_DATA = "drawing_data"
        const val COL_HOF_DATE_COMPLETED = "date_completed"

        private const val CREATE_TABLE_TEMPLATES = """
            CREATE TABLE $TABLE_TEMPLATES (
                $COL_TEMPLATE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TEMPLATE_NAME TEXT NOT NULL,
                $COL_TEMPLATE_IMAGE_PATH TEXT NOT NULL,
                $COL_TEMPLATE_DATE_CREATED INTEGER NOT NULL
            )
        """

        private const val CREATE_TABLE_GAME_SHEETS = """
            CREATE TABLE $TABLE_GAME_SHEETS (
                $COL_SHEET_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SHEET_TEMPLATE_ID INTEGER NOT NULL,
                $COL_SHEET_TEMPLATE_NAME TEXT NOT NULL,
                $COL_SHEET_TEMPLATE_IMAGE_PATH TEXT NOT NULL,
                $COL_SHEET_DRAWING_DATA TEXT NOT NULL DEFAULT '[]',
                $COL_SHEET_DATE_STARTED INTEGER NOT NULL,
                $COL_SHEET_LAST_MODIFIED INTEGER NOT NULL,
                $COL_SHEET_STATUS TEXT NOT NULL DEFAULT 'IN_PROGRESS'
            )
        """

        private const val CREATE_TABLE_HALL_OF_FAME = """
            CREATE TABLE $TABLE_HALL_OF_FAME (
                $COL_HOF_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_HOF_GAME_NAME TEXT NOT NULL,
                $COL_HOF_TEMPLATE_ID INTEGER NOT NULL,
                $COL_HOF_TEMPLATE_NAME TEXT NOT NULL,
                $COL_HOF_TEMPLATE_IMAGE_PATH TEXT NOT NULL,
                $COL_HOF_DRAWING_DATA TEXT NOT NULL,
                $COL_HOF_DATE_COMPLETED INTEGER NOT NULL
            )
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_TEMPLATES)
        db.execSQL(CREATE_TABLE_GAME_SHEETS)
        db.execSQL(CREATE_TABLE_HALL_OF_FAME)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TEMPLATES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_GAME_SHEETS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HALL_OF_FAME")
        onCreate(db)
    }
}
