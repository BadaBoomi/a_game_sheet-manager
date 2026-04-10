package de.badaboomi.gamesheetmanager.ui.templates

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import de.badaboomi.gamesheetmanager.R
import de.badaboomi.gamesheetmanager.data.GameSheet
import de.badaboomi.gamesheetmanager.data.Template
import de.badaboomi.gamesheetmanager.databinding.ActivityTemplateListBinding
import de.badaboomi.gamesheetmanager.repository.GameSheetRepository
import de.badaboomi.gamesheetmanager.repository.TemplateRepository
import de.badaboomi.gamesheetmanager.ui.game.GameSheetActivity
import de.badaboomi.gamesheetmanager.utils.FileUtils
import java.io.File

/**
 * Activity for listing and managing paper templates.
 * Can be used in MANAGE mode (add/delete templates) or SELECT mode (pick a template to start a game).
 */
class TemplateListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_MANAGE = "manage"
        const val MODE_SELECT = "select"
        private const val SAVED_CAMERA_FILE_PATH = "camera_file_path"
    }

    private lateinit var binding: ActivityTemplateListBinding
    private lateinit var templateRepository: TemplateRepository
    private lateinit var gameSheetRepository: GameSheetRepository
    private lateinit var adapter: TemplateAdapter
    private val templates = mutableListOf<Template>()
    private var mode = MODE_MANAGE
    private var cameraImageFile: File? = null

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleImageSelected(it) }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageFile?.let { file ->
                if (file.exists()) {
                    handleImageSelected(Uri.fromFile(file))
                } else {
                    Toast.makeText(this, R.string.msg_photo_failed, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, R.string.msg_photo_cancelled, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemplateListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Restore camera file path if activity was recreated
        if (savedInstanceState != null) {
            val savedPath = savedInstanceState.getString(SAVED_CAMERA_FILE_PATH)
            if (savedPath != null) {
                val file = File(savedPath)
                if (file.exists()) {
                    cameraImageFile = file
                }
            }
        }

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_MANAGE
        templateRepository = TemplateRepository(this)
        gameSheetRepository = GameSheetRepository(this)

        val titleRes = if (mode == MODE_SELECT) R.string.title_select_template else R.string.title_templates
        supportActionBar?.title = getString(titleRes)

        setupList()
        loadTemplates()

        if (mode == MODE_MANAGE) {
            binding.fabAddTemplate.visibility = View.VISIBLE
            binding.fabAddTemplate.setOnClickListener { showAddTemplateDialog() }
        } else {
            binding.fabAddTemplate.visibility = View.GONE
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save camera file path in case the activity is recreated
        cameraImageFile?.let {
            outState.putString(SAVED_CAMERA_FILE_PATH, it.absolutePath)
        }
    }

    override fun onResume() {
        super.onResume()
        loadTemplates()
    }

    private fun setupList() {
        adapter = TemplateAdapter(
            templates = templates,
            onItemClick = { template -> onTemplateClicked(template) },
            onDeleteClick = { template -> confirmDeleteTemplate(template) },
            showDeleteButton = mode == MODE_MANAGE
        )
        binding.listTemplates.adapter = adapter
    }

    private fun loadTemplates() {
        templates.clear()
        templates.addAll(templateRepository.getAllTemplates())
        adapter.notifyDataSetChanged()

        binding.emptyView.visibility = if (templates.isEmpty()) View.VISIBLE else View.GONE
        binding.listTemplates.visibility = if (templates.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun onTemplateClicked(template: Template) {
        if (mode == MODE_SELECT) {
            val existingSheet = gameSheetRepository.getActiveSheetForTemplate(template.id)
            if (existingSheet != null) {
                // Ask: continue or start new
                AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_existing_sheet_title)
                    .setMessage(R.string.dialog_existing_sheet_message)
                    .setPositiveButton(R.string.btn_continue_sheet) { _, _ ->
                        openGameSheet(existingSheet.id)
                    }
                    .setNegativeButton(R.string.btn_new_sheet) { _, _ ->
                        gameSheetRepository.deleteGameSheet(existingSheet.id)
                        startNewGameSheet(template)
                    }
                    .show()
            } else {
                startNewGameSheet(template)
            }
        }
    }

    private fun startNewGameSheet(template: Template) {
        val newSheet = GameSheet(
            templateId = template.id,
            templateName = template.name,
            templateImagePath = template.imagePath
        )
        val id = gameSheetRepository.insertGameSheet(newSheet)
        openGameSheet(id)
    }

    private fun openGameSheet(sheetId: Long) {
        val intent = Intent(this, GameSheetActivity::class.java)
        intent.putExtra(GameSheetActivity.EXTRA_SHEET_ID, sheetId)
        startActivity(intent)
    }

    private fun showAddTemplateDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_add_template_title)
            .setItems(
                arrayOf(
                    getString(R.string.option_take_photo),
                    getString(R.string.option_from_gallery)
                )
            ) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> pickFromGallery()
                }
            }
            .show()
    }

    private fun takePhoto() {
        cameraImageFile = FileUtils.createTemplateImageFile(this)
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            cameraImageFile!!
        )
        cameraLauncher.launch(uri)
    }

    private fun pickFromGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun handleImageSelected(uri: Uri) {
        val editText = android.widget.EditText(this).apply {
            hint = getString(R.string.hint_template_name)
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_template_name_title)
            .setView(editText)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val name = editText.text.toString().trim().ifEmpty { getString(R.string.default_template_name) }
                saveTemplate(uri, name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveTemplate(uri: Uri, name: String) {
        val imagePath = FileUtils.copyImageToTemplatesDir(this, uri)
        if (imagePath != null) {
            val template = Template(name = name, imagePath = imagePath)
            templateRepository.insertTemplate(template)
            loadTemplates()
            Toast.makeText(this, R.string.msg_template_saved, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.msg_template_save_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteTemplate(template: Template) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_template_title)
            .setMessage(getString(R.string.dialog_delete_template_message, template.name))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                FileUtils.deleteFile(template.imagePath)
                templateRepository.deleteTemplate(template.id)
                loadTemplates()
                Toast.makeText(this, R.string.msg_template_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
