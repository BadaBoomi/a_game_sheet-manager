package de.badaboomi.gamesheetmanager.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility functions for file operations.
 */
object FileUtils {

    private const val TEMPLATES_DIR = "templates"

    /**
     * Returns the directory used for storing template images.
     */
    fun getTemplatesDir(context: Context): File {
        val dir = File(context.filesDir, TEMPLATES_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Creates a new unique file for a template image.
     */
    fun createTemplateImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "TEMPLATE_${timeStamp}.jpg"
        return File(getTemplatesDir(context), fileName)
    }

    /**
     * Copies an image from a URI to the templates directory.
     * Returns the destination file path, or null on failure.
     */
    fun copyImageToTemplatesDir(context: Context, sourceUri: Uri): String? {
        return try {
            val destFile = createTemplateImageFile(context)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves a bitmap to a file and returns the file path, or null on failure.
     */
    fun saveBitmapToFile(context: Context, bitmap: Bitmap): String? {
        return try {
            val destFile = createTemplateImageFile(context)
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Deletes a file at the given path.
     */
    fun deleteFile(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }
}
