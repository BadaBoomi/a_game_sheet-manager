package de.badaboomi.gamesheetmanager.utils

import de.badaboomi.gamesheetmanager.data.Template
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Exports one or more templates as a ZIP archive.
 * The archive contains an export manifest and each template image.
 */
object TemplateExportUtils {

    data class ImportedTemplate(
        val name: String,
        val imageFileName: String,
        val imageBytes: ByteArray
    )

    data class ArchiveImportResult(
        val templates: List<ImportedTemplate>
    ) {
        val conflictingNames: List<String>
            get() = templates
                .groupBy { it.name }
                .filterValues { it.size > 1 }
                .keys
                .toList()
    }

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

    fun readArchive(archiveFile: File): ArchiveImportResult? {
        if (!archiveFile.exists()) return null

        return try {
            ZipFile(archiveFile).use { zip ->
                val manifestEntry = zip.getEntry("manifest.json") ?: return null
                val manifestText = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val imported = parseManifestTemplates(manifestText, zip)
                if (imported.isEmpty()) null else ArchiveImportResult(imported)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun findDuplicateTemplateNames(
        existingNames: Collection<String>,
        importedTemplates: List<ImportedTemplate>
    ): List<String> {
        val normalizedExisting = existingNames.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return importedTemplates
            .map { it.name.trim() }
            .filter { it.isNotEmpty() && normalizedExisting.contains(it) }
            .distinct()
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

    private fun parseManifestTemplates(manifestText: String, zip: ZipFile): List<ImportedTemplate> {
        return try {
            val manifest = JSONObject(manifestText)
            val allTemplates = manifest.optJSONArray("templates") ?: return emptyList()
            val entries = mutableListOf<ImportedTemplate>()

            for (i in 0 until allTemplates.length()) {
                val item = allTemplates.getJSONObject(i)
                val name = item.optString("name").trim()
                val imageName = item.optString("image").trim()
                if (name.isBlank() || imageName.isBlank()) continue
                val entry = zip.getEntry("images/$imageName")
                    ?: zip.entries().asSequence().firstOrNull { it.name.endsWith("/$imageName") || it.name == imageName }
                    ?: continue
                val imageBytes = zip.getInputStream(entry).readBytes()
                if (imageBytes.isNotEmpty()) {
                    entries.add(ImportedTemplate(name, imageName, imageBytes))
                }
            }
            entries
        } catch (_: Exception) {
            emptyList()
        }
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

    private fun unescapeJson(value: String): String {
        return value
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
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
