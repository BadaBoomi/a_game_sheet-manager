package de.badaboomi.gamesheetmanager.repository

import android.content.ContentValues
import android.content.Context
import de.badaboomi.gamesheetmanager.data.AppDatabase
import de.badaboomi.gamesheetmanager.data.Template

/**
 * Repository for managing paper templates in the database.
 */
class TemplateRepository(context: Context) {

    private val db = AppDatabase(context)

    fun getAllTemplates(): List<Template> {
        val templates = mutableListOf<Template>()
        val cursor = db.readableDatabase.query(
            AppDatabase.TABLE_TEMPLATES,
            null, null, null, null, null,
            "${AppDatabase.COL_TEMPLATE_DATE_CREATED} DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                templates.add(
                    Template(
                        id = it.getLong(it.getColumnIndexOrThrow(AppDatabase.COL_TEMPLATE_ID)),
                        name = it.getString(it.getColumnIndexOrThrow(AppDatabase.COL_TEMPLATE_NAME)),
                        imagePath = it.getString(it.getColumnIndexOrThrow(AppDatabase.COL_TEMPLATE_IMAGE_PATH)),
                        dateCreated = it.getLong(it.getColumnIndexOrThrow(AppDatabase.COL_TEMPLATE_DATE_CREATED))
                    )
                )
            }
        }
        return templates
    }

    fun getTemplateById(id: Long): Template? {
        val cursor = db.readableDatabase.query(
            AppDatabase.TABLE_TEMPLATES,
            null,
            "${AppDatabase.COL_TEMPLATE_ID} = ?",
            arrayOf(id.toString()),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                Template(
                    id = it.getLong(it.getColumnIndexOrThrow(AppDatabase.COL_TEMPLATE_ID)),
                    name = it.getString(it.getColumnIndexOrThrow(AppDatabase.COL_TEMPLATE_NAME)),
                    imagePath = it.getString(it.getColumnIndexOrThrow(AppDatabase.COL_TEMPLATE_IMAGE_PATH)),
                    dateCreated = it.getLong(it.getColumnIndexOrThrow(AppDatabase.COL_TEMPLATE_DATE_CREATED))
                )
            } else null
        }
    }

    fun insertTemplate(template: Template): Long {
        val values = ContentValues().apply {
            put(AppDatabase.COL_TEMPLATE_NAME, template.name)
            put(AppDatabase.COL_TEMPLATE_IMAGE_PATH, template.imagePath)
            put(AppDatabase.COL_TEMPLATE_DATE_CREATED, template.dateCreated)
        }
        return db.writableDatabase.insert(AppDatabase.TABLE_TEMPLATES, null, values)
    }

    fun deleteTemplate(id: Long): Int {
        return db.writableDatabase.delete(
            AppDatabase.TABLE_TEMPLATES,
            "${AppDatabase.COL_TEMPLATE_ID} = ?",
            arrayOf(id.toString())
        )
    }
}
