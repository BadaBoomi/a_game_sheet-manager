package de.badaboomi.gamesheetmanager.ui.templates

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import de.badaboomi.gamesheetmanager.R
import de.badaboomi.gamesheetmanager.bluetooth.BluetoothDeviceListActivity
import de.badaboomi.gamesheetmanager.bluetooth.TemplateReceiveService
import de.badaboomi.gamesheetmanager.data.GameSheet
import de.badaboomi.gamesheetmanager.data.Template
import de.badaboomi.gamesheetmanager.databinding.ActivityTemplateListBinding
import de.badaboomi.gamesheetmanager.repository.GameSheetRepository
import de.badaboomi.gamesheetmanager.repository.TemplateRepository
import de.badaboomi.gamesheetmanager.ui.game.GameSheetActivity
import de.badaboomi.gamesheetmanager.utils.FileUtils
import com.yalantis.ucrop.UCrop
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
        private const val SAVED_EDITING_TEMPLATE_ID = "editing_template_id"
    }

    private lateinit var binding: ActivityTemplateListBinding
    private lateinit var templateRepository: TemplateRepository
    private lateinit var gameSheetRepository: GameSheetRepository
    private lateinit var adapter: TemplateAdapter
    private val templates = mutableListOf<Template>()
    private var mode = MODE_MANAGE
    private var cameraImageFile: File? = null
    private var pendingTemplateImageUri: Uri? = null

    /** Non-null when the image-change flow was started for an existing template. */
    private var editingTemplate: Template? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(
                this,
                R.string.msg_camera_permission_denied,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { launchCropFlow(it) }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageFile != null) {
            val file = cameraImageFile!!
            if (file.exists() && file.length() > 0) {
                // Foto wurde erfolgreich gespeichert, verwende den FileProvider-URI
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                launchCropFlow(uri)
            } else {
                Toast.makeText(this, R.string.msg_photo_failed, Toast.LENGTH_SHORT).show()
                file.delete()
            }
        } else {
            Toast.makeText(this, R.string.msg_photo_cancelled, Toast.LENGTH_SHORT).show()
        }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val outputUri = result.data?.let { UCrop.getOutput(it) }
            if (outputUri != null) {
                val editing = editingTemplate
                if (editing != null) {
                    updateTemplateImage(editing, outputUri)
                } else {
                    handleImageSelected(outputUri)
                }
            } else {
                Toast.makeText(this, R.string.msg_crop_failed, Toast.LENGTH_SHORT).show()
            }
        } else if (result.resultCode == RESULT_CANCELED) {
            Toast.makeText(this, R.string.msg_crop_cancelled, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.msg_crop_failed, Toast.LENGTH_SHORT).show()
        }
        pendingTemplateImageUri = null
        editingTemplate = null
    }

    private val btPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startBluetoothReceive()
        } else {
            Toast.makeText(this, R.string.msg_bt_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    private val templateReceivedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadTemplates()
            receivingDialog?.setMessage(getString(R.string.msg_bt_waiting_next))
        }
    }
    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val percent = intent?.getIntExtra(TemplateReceiveService.EXTRA_PROGRESS_PERCENT, 0) ?: 0
            val secondsLeft = intent?.getIntExtra(TemplateReceiveService.EXTRA_SECONDS_LEFT, -1) ?: -1
            val msg = if (secondsLeft >= 0) {
                getString(R.string.msg_bt_receiving_progress, percent, secondsLeft)
            } else {
                getString(R.string.msg_bt_receiving_pct, percent)
            }
            receivingDialog?.setMessage(msg)
        }
    }
    private var receiverRegistered = false
    private var progressReceiverRegistered = false
    private var receivingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemplateListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_MANAGE
        templateRepository = TemplateRepository(this)
        gameSheetRepository = GameSheetRepository(this)

        // Restore camera file path if activity was recreated
        if (savedInstanceState != null) {
            val savedPath = savedInstanceState.getString(SAVED_CAMERA_FILE_PATH)
            if (savedPath != null) {
                val file = File(savedPath)
                if (file.exists()) {
                    cameraImageFile = file
                }
            }
            val editingId = savedInstanceState.getLong(SAVED_EDITING_TEMPLATE_ID, -1L)
            if (editingId >= 0) {
                editingTemplate = templateRepository.getTemplateById(editingId)
            }
        }

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
        editingTemplate?.let {
            outState.putLong(SAVED_EDITING_TEMPLATE_ID, it.id)
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
            onStartClick = { template -> onTemplateStartClicked(template) },
            onDeleteClick = { template -> confirmDeleteTemplate(template) },
            onEditClick = { template -> showEditTemplateDialog(template) },
            onSendClick = { template -> sendTemplateViaBluetooth(template) },
            showDeleteButton = mode == MODE_MANAGE,
            showStartButton = TemplateUiRules.showDirectStartButton(mode)
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

    private fun onTemplateStartClicked(template: Template) {
        val existingSheet = gameSheetRepository.getActiveSheetForTemplate(template.id)
        if (existingSheet != null) {
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
                    getString(R.string.option_from_gallery),
                    getString(R.string.option_receive_bluetooth)
                )
            ) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> pickFromGallery()
                    2 -> checkBtPermissionsAndReceive()
                }
            }
            .show()
    }

    // -------------------------------------------------------------------------
    // Edit template
    // -------------------------------------------------------------------------

    private fun showEditTemplateDialog(template: Template) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_edit_template_title)
            .setItems(
                arrayOf(
                    getString(R.string.option_change_name),
                    getString(R.string.option_change_image)
                )
            ) { _, which ->
                when (which) {
                    0 -> showEditNameDialog(template)
                    1 -> startEditImageFlow(template)
                }
            }
            .show()
    }

    private fun showEditNameDialog(template: Template) {
        val editText = android.widget.EditText(this).apply {
            hint = getString(R.string.hint_template_name)
            setText(template.name)
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_template_name_title)
            .setView(editText)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val newName = editText.text.toString().trim()
                    .ifEmpty { getString(R.string.default_template_name) }
                val updated = template.copy(name = newName)
                templateRepository.updateTemplate(updated)
                loadTemplates()
                Toast.makeText(this, R.string.msg_template_updated, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startEditImageFlow(template: Template) {
        editingTemplate = template
        showAddTemplateDialog()
    }

    private fun updateTemplateImage(template: Template, newImageUri: Uri) {
        val oldImagePath = template.imagePath
        val newImagePath = FileUtils.copyImageToTemplatesDir(this, newImageUri)
        if (newImagePath != null) {
            val updated = template.copy(imagePath = newImagePath)
            templateRepository.updateTemplate(updated)
            FileUtils.deleteFile(oldImagePath)
            loadTemplates()
            Toast.makeText(this, R.string.msg_template_updated, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.msg_template_save_error, Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------------------------------------------------------
    // Bluetooth send
    // -------------------------------------------------------------------------

    private fun sendTemplateViaBluetooth(template: Template) {
        val intent = Intent(this, BluetoothDeviceListActivity::class.java).apply {
            putExtra(BluetoothDeviceListActivity.EXTRA_TEMPLATE_ID, template.id)
            putExtra(BluetoothDeviceListActivity.EXTRA_TEMPLATE_NAME, template.name)
            putExtra(BluetoothDeviceListActivity.EXTRA_TEMPLATE_IMAGE_PATH, template.imagePath)
        }
        startActivity(intent)
    }

    // -------------------------------------------------------------------------
    // Camera / gallery / crop helpers
    // -------------------------------------------------------------------------

    private fun takePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
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

    private fun launchCropFlow(sourceUri: Uri) {
        pendingTemplateImageUri = sourceUri
        val destinationFile = File(cacheDir, "crop_${System.currentTimeMillis()}.jpg")
        val destinationUri = Uri.fromFile(destinationFile)

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(95)
            setFreeStyleCropEnabled(true)
            setToolbarTitle(getString(R.string.title_crop_template))
        }

        val cropIntent = UCrop.of(sourceUri, destinationUri)
            .withOptions(options)
            .getIntent(this)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        try {
            cropLauncher.launch(cropIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.msg_crop_failed, Toast.LENGTH_SHORT).show()
            fallbackFromCrop(sourceUri)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.msg_crop_failed, Toast.LENGTH_SHORT).show()
            fallbackFromCrop(sourceUri)
        }
    }

    /**
     * Handles the case where the crop flow fails or is unavailable.
     * Routes to either the template-update path or the new-template path.
     */
    private fun fallbackFromCrop(sourceUri: Uri) {
        val editing = editingTemplate
        if (editing != null) {
            editingTemplate = null
            updateTemplateImage(editing, sourceUri)
        } else {
            handleImageSelected(sourceUri)
        }
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
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // -------------------------------------------------------------------------
    // Bluetooth receive
    // -------------------------------------------------------------------------

    private fun checkBtPermissionsAndReceive() {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                required.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (required.isEmpty()) {
            startBluetoothReceive()
        } else {
            btPermissionLauncher.launch(required.toTypedArray())
        }
    }

    private fun startBluetoothReceive() {
        if (receiverRegistered || progressReceiverRegistered) {
            stopBluetoothReceive()
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothManager?.adapter == null) {
            Toast.makeText(this, R.string.msg_bt_not_supported, Toast.LENGTH_LONG).show()
            return
        }
        if (bluetoothManager.adapter?.isEnabled != true) {
            Toast.makeText(this, R.string.msg_bt_not_enabled, Toast.LENGTH_LONG).show()
            return
        }

        val filter = IntentFilter(TemplateReceiveService.ACTION_TEMPLATE_RECEIVED)
        ContextCompat.registerReceiver(
            this, templateReceivedReceiver, filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true

        val progressFilter = IntentFilter(TemplateReceiveService.ACTION_RECEIVE_PROGRESS)
        ContextCompat.registerReceiver(
            this, progressReceiver, progressFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        progressReceiverRegistered = true

        TemplateReceiveService.start(this)

        receivingDialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_bt_listening_title)
            .setMessage(R.string.dialog_bt_listening_message)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                stopBluetoothReceive()
                receivingDialog = null
            }
            .show()
    }

    private fun stopBluetoothReceive() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(templateReceivedReceiver)
            } catch (_: IllegalArgumentException) {
            }
            receiverRegistered = false
        }
        if (progressReceiverRegistered) {
            try {
                unregisterReceiver(progressReceiver)
            } catch (_: IllegalArgumentException) {
            }
            progressReceiverRegistered = false
        }
        TemplateReceiveService.stop(this)
    }

    override fun onDestroy() {
        stopBluetoothReceive()
        super.onDestroy()
    }
}
