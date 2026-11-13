package de.badaboomi.gamesheetmanager.utils

import de.badaboomi.gamesheetmanager.data.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class TemplateExportUtilsTest {

    @Test
    fun `readArchive reads manifest entries and images`() {
        val tempDir = createTempDir(prefix = "template_import_")
        val firstImage = File(tempDir, "first.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val secondImage = File(tempDir, "second.jpg").apply { writeBytes(byteArrayOf(9, 8, 7, 6)) }

        val templates = listOf(
            Template(id = 1L, name = "Alpha", imagePath = firstImage.absolutePath, dateCreated = 1000L),
            Template(id = 2L, name = "Beta", imagePath = secondImage.absolutePath, dateCreated = 2000L)
        )
        val archive = File(tempDir, "archive.zip")
        TemplateExportUtils.createArchive(archive, templates)

        val imported = TemplateExportUtils.readArchive(archive)

        assertNotNull(imported)
        assertEquals(listOf("Alpha", "Beta"), imported!!.templates.map { it.name })
        assertEquals(byteArrayOf(1, 2, 3, 4).toList(), imported.templates[0].imageBytes.toList())
        assertEquals(byteArrayOf(9, 8, 7, 6).toList(), imported.templates[1].imageBytes.toList())
    }

    @Test
    fun `findDuplicateTemplateNames detects names already in the database`() {
        val imported = listOf(
            TemplateExportUtils.ImportedTemplate("Alpha", "Alpha_1.jpg", byteArrayOf(1)),
            TemplateExportUtils.ImportedTemplate("Beta", "Beta_2.jpg", byteArrayOf(2)),
            TemplateExportUtils.ImportedTemplate("Alpha", "Alpha_3.jpg", byteArrayOf(3))
        )

        val duplicates = TemplateExportUtils.findDuplicateTemplateNames(listOf("Alpha", "Gamma"), imported)

        assertEquals(listOf("Alpha"), duplicates)
    }

    @Test
    fun `createArchive exports manifest and template images`() {
        val tempDir = createTempDir(prefix = "template_export_")
        val firstImage = File(tempDir, "first.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val secondImage = File(tempDir, "second.jpg").apply { writeBytes(byteArrayOf(9, 8, 7, 6)) }

        val templates = listOf(
            Template(id = 1L, name = "Alpha", imagePath = firstImage.absolutePath, dateCreated = 1000L),
            Template(id = 2L, name = "Beta", imagePath = secondImage.absolutePath, dateCreated = 2000L)
        )
        val archive = File(tempDir, "templates.zip")

        val exported = TemplateExportUtils.createArchive(archive, templates)

        assertNotNull(exported)
        assertTrue(archive.exists())

        ZipFile(archive).use { zip ->
            assertNotNull(zip.getEntry("manifest.json"))
            assertNotNull(zip.getEntry("images/Alpha_1.jpg"))
            assertNotNull(zip.getEntry("images/Beta_2.jpg"))
            assertEquals(byteArrayOf(1, 2, 3, 4).toList(), zip.getInputStream(zip.getEntry("images/Alpha_1.jpg")).readBytes().toList())
            assertEquals(byteArrayOf(9, 8, 7, 6).toList(), zip.getInputStream(zip.getEntry("images/Beta_2.jpg")).readBytes().toList())

            val manifest = zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().use { it.readText() }
            assertTrue(manifest.contains("\"name\": \"Alpha\""))
            assertTrue(manifest.contains("\"name\": \"Beta\""))
        }
    }

    @Test
    fun `createArchive returns null for empty input`() {
        val tempDir = createTempDir(prefix = "template_export_empty_")
        val archive = File(tempDir, "empty.zip")

        assertNull(TemplateExportUtils.createArchive(archive, emptyList()))
    }
}
