package de.badaboomi.gamesheetmanager.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
            val optimizedBitmap = decodeAndOptimizeForScreen(context, sourceUri)

            if (optimizedBitmap != null) {
                FileOutputStream(destFile).use { out ->
                    optimizedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                optimizedBitmap.recycle()
            } else {
                // Fallback to plain copy if decoding fails for an unsupported source.
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeAndOptimizeForScreen(context: Context, sourceUri: Uri): Bitmap? {
        val targetWidth = context.resources.displayMetrics.widthPixels
        val targetHeight = context.resources.displayMetrics.heightPixels

        if (targetWidth <= 0 || targetHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return null

        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                srcWidth = options.outWidth,
                srcHeight = options.outHeight,
                reqWidth = targetWidth,
                reqHeight = targetHeight
            )
        }

        val sampledBitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: return null

        val srcWidth = sampledBitmap.width
        val srcHeight = sampledBitmap.height

        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()

        val cropWidth: Int
        val cropHeight: Int
        val cropX: Int
        val cropY: Int

        if (srcRatio > targetRatio) {
            cropHeight = srcHeight
            cropWidth = (srcHeight * targetRatio).toInt().coerceAtMost(srcWidth)
            cropX = ((srcWidth - cropWidth) / 2).coerceAtLeast(0)
            cropY = 0
        } else {
            cropWidth = srcWidth
            cropHeight = (srcWidth / targetRatio).toInt().coerceAtMost(srcHeight)
            cropX = 0
            cropY = ((srcHeight - cropHeight) / 2).coerceAtLeast(0)
        }

        val croppedBitmap = Bitmap.createBitmap(sampledBitmap, cropX, cropY, cropWidth, cropHeight)
        if (croppedBitmap !== sampledBitmap) {
            sampledBitmap.recycle()
        }

        return if (croppedBitmap.width == targetWidth && croppedBitmap.height == targetHeight) {
            croppedBitmap
        } else {
            val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, targetWidth, targetHeight, true)
            if (scaledBitmap !== croppedBitmap) {
                croppedBitmap.recycle()
            }
            scaledBitmap
        }
    }

    private fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1

        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            var halfHeight = srcHeight / 2
            var halfWidth = srcWidth / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
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
