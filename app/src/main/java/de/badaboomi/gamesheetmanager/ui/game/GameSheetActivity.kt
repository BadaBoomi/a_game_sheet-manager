package de.badaboomi.gamesheetmanager.ui.game

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import de.badaboomi.gamesheetmanager.R
import de.badaboomi.gamesheetmanager.data.HallOfFameEntry
import de.badaboomi.gamesheetmanager.databinding.ActivityGameSheetBinding
import de.badaboomi.gamesheetmanager.repository.GameSheetRepository
import de.badaboomi.gamesheetmanager.repository.HallOfFameRepository
import java.io.File

/**
 * Activity for playing a game on a sheet.
 * Displays the template image with a drawing overlay.
 * Supports pen color/width selection, intermediate save, and saving to Hall of Fame.
 */
class GameSheetActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SHEET_ID = "sheet_id"
    }

    private lateinit var binding: ActivityGameSheetBinding
    private lateinit var gameSheetRepository: GameSheetRepository
    private lateinit var hallOfFameRepository: HallOfFameRepository
    private var currentSheet: GameSheet? = null
    private var sheetId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameSheetBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        gameSheetRepository = GameSheetRepository(this)
        hallOfFameRepository = HallOfFameRepository(this)

        sheetId = intent.getLongExtra(EXTRA_SHEET_ID, -1)
        if (sheetId == -1L) {
            finish()
            return
        }

        loadGameSheet()
        setupToolbarButtons()
    }

    private fun loadGameSheet() {
        val sheet = gameSheetRepository.getGameSheetById(sheetId) ?: run {
            finish()
            return
        }
        currentSheet = sheet
        supportActionBar?.title = sheet.templateName

        // Load template image
        val imageFile = File(sheet.templateImagePath)
        if (imageFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(sheet.templateImagePath)
            binding.ivTemplateBackground.setImageBitmap(bitmap)
        }

        // Load existing drawing strokes
        binding.drawingView.loadStrokes(sheet.drawingData)
    }

    private fun setupToolbarButtons() {
        // Color picker button
        binding.btnPickColor.setOnClickListener {
            showColorPicker()
        }

        // Stroke width seekbar
        binding.seekBarWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.drawingView.penWidth = (progress + 2).toFloat()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        binding.seekBarWidth.progress = (binding.drawingView.penWidth - 2).toInt()

        // Undo button
        binding.btnUndo.setOnClickListener {
            binding.drawingView.undoLastStroke()
        }

        // Clear button
        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_clear_title)
                .setMessage(R.string.dialog_clear_message)
                .setPositiveButton(R.string.btn_clear) { _, _ ->
                    binding.drawingView.clearDrawing()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showColorPicker() {
        ColorPickerDialog(
            context = this,
            currentColor = binding.drawingView.penColor,
            currentWidth = binding.drawingView.penWidth
        ) { color, width ->
            binding.drawingView.penColor = color
            binding.drawingView.penWidth = width
            binding.btnPickColor.setColorFilter(color)
            binding.seekBarWidth.progress = (width - 2).toInt()
        }.show()
    }

    /**
     * Saves the current state of the drawing.
     */
    private fun saveCurrentState() {
        val sheet = currentSheet ?: return
        val updatedSheet = sheet.copy(
            drawingData = binding.drawingView.getDrawingData(),
            lastModified = System.currentTimeMillis()
        )
        gameSheetRepository.updateGameSheet(updatedSheet)
        currentSheet = updatedSheet
        Toast.makeText(this, R.string.msg_sheet_saved, Toast.LENGTH_SHORT).show()
    }

    /**
     * Finishes the game and saves the sheet to the Hall of Fame.
     */
    private fun finishGame() {
        val sheet = currentSheet ?: return
        val nameInput = android.widget.EditText(this).apply {
            hint = getString(R.string.hint_game_name)
            setText(sheet.templateName)
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_finish_game_title)
            .setMessage(R.string.dialog_finish_game_message)
            .setView(nameInput)
            .setPositiveButton(R.string.btn_save_to_hof) { _, _ ->
                val gameName = nameInput.text.toString().trim().ifEmpty { sheet.templateName }

                val drawingData = binding.drawingView.getDrawingData()

                val hofEntry = HallOfFameEntry(
                    gameName = gameName,
                    templateId = sheet.templateId,
                    templateName = sheet.templateName,
                    templateImagePath = sheet.templateImagePath,
                    drawingData = drawingData
                )
                hallOfFameRepository.insertEntry(hofEntry)

                // Remove the active sheet
                gameSheetRepository.deleteGameSheet(sheet.id)
                currentSheet = null

                Toast.makeText(this, R.string.msg_saved_to_hof, Toast.LENGTH_LONG).show()
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_game_sheet, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_save -> {
                saveCurrentState()
                true
            }
            R.id.action_finish_game -> {
                finishGame()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        super.onPause()
        // Auto-save on pause
        currentSheet?.let {
            val updatedSheet = it.copy(
                drawingData = binding.drawingView.getDrawingData(),
                lastModified = System.currentTimeMillis()
            )
            gameSheetRepository.updateGameSheet(updatedSheet)
            currentSheet = updatedSheet
        }
    }
}
