package de.badaboomi.gamesheetmanager.utils

import de.badaboomi.gamesheetmanager.data.Template
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports one or more templates as a ZIP archive.
 * The archive contains an export manifest and each template image.
 */
object TemplateExportUtils {

    fun createArchive(outputFile: File, templates: List<Template>): File? {
        if (templates.isEmpty()) return null

        outputFile.parentFile?.mkdirs()

        val manifestTemplates = templates.mapNotNull { template ->
            val imageFile = File(template.imagePath)
            if (!imageFile.exists()) return@mapNotNull null

            val fileName = sanitizeFileName(template.name, template.id)
            ExportEntry(
                template = template,
                imageFileName = "$fileName.jpg",
                imagePath = "images/$fileName.jpg"
            )
        }

        if (manifestTemplates.isEmpty()) return null

        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(buildManifest(manifestTemplates).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            manifestTemplates.forEach { entry ->
                val imageFile = File(entry.template.imagePath)
                zip.putNextEntry(ZipEntry(entry.imagePath))
                imageFile.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }

        return outputFile
    }

    private data class ExportEntry(
        val template: Template,
        val imageFileName: String,
        val imagePath: String
    )

    private fun buildManifest(templates: List<ExportEntry>): String {
        val builder = StringBuilder()
        builder.append("{\n")
        builder.append("  \"version\": 1,\n")
        builder.append("  \"templates\": [\n")

        templates.forEachIndexed { index, entry ->
            if (index > 0) {
                builder.append(",\n")
            }
            val template = entry.template
            builder.append("    {\n")
            builder.append("      \"id\": ${template.id},\n")
            builder.append("      \"name\": \"")
            builder.append(escapeJson(template.name))
            builder.append("\",\n")
            builder.append("      \"dateCreated\": ${template.dateCreated},\n")
            builder.append("      \"image\": \"")
            builder.append(escapeJson(entry.imageFileName))
            builder.append("\"\n")
            builder.append("    }")
        }

        builder.append("\n  ]\n")
        builder.append("}\n")
        return builder.toString()
    }

    private fun sanitizeFileName(name: String, templateId: Long): String {
        val sanitized = name.ifBlank { "template" }
            .replace("\\", "_")
            .replace("/", "_")
            .replace(":", "_")
            .replace("*", "_")
            .replace("?", "_")
            .replace("\"", "_")
            .replace("<", "_")
            .replace(">", "_")
            .replace("|", "_")
            .replace("\r", "_")
            .replace("\n", "_")
            .trim()
        return if (sanitized.isNotEmpty()) "${sanitized}_$templateId" else "template_$templateId"
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
