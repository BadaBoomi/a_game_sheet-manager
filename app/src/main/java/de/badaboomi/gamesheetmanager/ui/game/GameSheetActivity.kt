package de.badaboomi.gamesheetmanager.ui.game

import android.graphics.BitmapFactory
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import com.google.android.material.button.MaterialButton
import de.badaboomi.gamesheetmanager.MainActivity
import de.badaboomi.gamesheetmanager.R
import de.badaboomi.gamesheetmanager.data.GameSheet
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
        private const val STATE_FLOATING_CONTROLS_ORIENTATION = "state_floating_controls_orientation"
    }

    private lateinit var binding: ActivityGameSheetBinding
    private lateinit var gameSheetRepository: GameSheetRepository
    private lateinit var hallOfFameRepository: HallOfFameRepository
    private var currentSheet: GameSheet? = null
    private var sheetId: Long = -1
    private var isDraggingMenuButton = false
    private var dragTouchOffsetX = 0f
    private var dragTouchOffsetY = 0f
    private var floatingControlsOrientation = LinearLayout.HORIZONTAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameSheetBinding.inflate(layoutInflater)
        setContentView(binding.root)
        floatingControlsOrientation = savedInstanceState?.getInt(
            STATE_FLOATING_CONTROLS_ORIENTATION,
            binding.floatingControls.orientation
        ) ?: binding.floatingControls.orientation

        gameSheetRepository = GameSheetRepository(this)
        hallOfFameRepository = HallOfFameRepository(this)

        sheetId = intent.getLongExtra(EXTRA_SHEET_ID, -1)
        if (sheetId == -1L) {
            finish()
            return
        }

        loadGameSheet()
        setupFloatingMenuButton()
    }

    private fun loadGameSheet() {
        val sheet = gameSheetRepository.getGameSheetById(sheetId) ?: run {
            finish()
            return
        }
        currentSheet = sheet

        // Load template image
        val imageFile = File(sheet.templateImagePath)
        if (imageFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(sheet.templateImagePath)
            binding.ivTemplateBackground.setImageBitmap(bitmap)
        }

        // Load existing drawing strokes
        binding.drawingView.loadStrokes(sheet.drawingData)
    }

    private fun setupFloatingMenuButton() {
        val menuButton = binding.btnMenuHandle
        applyFloatingControlsOrientation(floatingControlsOrientation)
        binding.btnPenStyle.setColorFilter(binding.drawingView.penColor)
        binding.btnPenStyle.setOnClickListener {
            showColorPicker()
        }
        binding.btnQuickUndo.setOnClickListener {
            binding.drawingView.undoLastStroke()
        }

        val gestureDetector = GestureDetectorCompat(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    showFloatingMenu()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    isDraggingMenuButton = true
                    dragTouchOffsetX = e.x
                    dragTouchOffsetY = e.y
                    menuButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        )

        menuButton.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingMenuButton) {
                        repositionFloatingControls(event)
                        return@setOnTouchListener true
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingMenuButton) {
                        isDraggingMenuButton = false
                        return@setOnTouchListener true
                    }
                }
            }

            true
        }
    }

    private fun repositionFloatingControls(event: MotionEvent) {
        val controls = binding.floatingControls
        val parentView = controls.parent as? View ?: return
        val parentLocation = IntArray(2)
        parentView.getLocationOnScreen(parentLocation)

        val targetX = event.rawX - parentLocation[0] - dragTouchOffsetX
        val targetY = event.rawY - parentLocation[1] - dragTouchOffsetY

        controls.x = targetX.coerceIn(0f, (parentView.width - controls.width).toFloat())
        controls.y = targetY.coerceIn(0f, (parentView.height - controls.height).toFloat())
    }

    private fun showFloatingMenu() {
        val dp = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = floatingControlsOrientation
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        }
        val dialog = AlertDialog.Builder(this).setView(layout).create()

        fun addButton(label: String, action: () -> Unit) {
            layout.addView(MaterialButton(this).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(
                    if (floatingControlsOrientation == LinearLayout.VERTICAL) {
                        ViewGroup.LayoutParams.MATCH_PARENT
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    },
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (floatingControlsOrientation == LinearLayout.VERTICAL) {
                        setMargins(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
                    } else {
                        setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                    }
                }
                setOnClickListener { dialog.dismiss(); action() }
            })
        }

        addButton(getString(R.string.action_toggle_control_orientation)) { toggleFloatingControlsOrientation() }
        addButton(getString(R.string.action_restart_game)) { restartGameFromTemplate() }
        addButton(getString(R.string.action_save)) { saveCurrentState() }
        addButton(getString(R.string.action_finish_and_save_hof)) { finishGame() }
        addButton(getString(R.string.action_save_and_finish)) { saveAndFinish() }
        addButton(getString(R.string.action_finish_without_save)) { finishWithoutSaving() }

        dialog.show()
    }

    private fun toggleFloatingControlsOrientation() {
        val newOrientation = if (floatingControlsOrientation == LinearLayout.HORIZONTAL) {
            LinearLayout.VERTICAL
        } else {
            LinearLayout.HORIZONTAL
        }
        applyFloatingControlsOrientation(newOrientation)
    }

    private fun applyFloatingControlsOrientation(orientation: Int) {
        floatingControlsOrientation = orientation
        val controls = binding.floatingControls
        controls.orientation = orientation

        val spacing = (8 * resources.displayMetrics.density).toInt()
        for (index in 0 until controls.childCount) {
            val child = controls.getChildAt(index)
            val params = child.layoutParams as? LinearLayout.LayoutParams ?: continue
            val leadingSpacing = if (index == 0) 0 else spacing
            val startMargin = if (orientation == LinearLayout.HORIZONTAL) leadingSpacing else 0
            val topMargin = if (orientation == LinearLayout.VERTICAL) leadingSpacing else 0
            params.setMargins(startMargin, topMargin, 0, 0)
            child.layoutParams = params
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
            binding.btnPenStyle.setColorFilter(color)
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
                navigateToStartPage()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun finishWithoutSaving() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_finish_without_save_title)
            .setMessage(R.string.dialog_finish_without_save_message)
            .setPositiveButton(R.string.action_finish_without_save) { _, _ ->
                currentSheet?.let { sheet ->
                    gameSheetRepository.deleteGameSheet(sheet.id)
                    currentSheet = null
                }
                navigateToStartPage()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveAndFinish() {
        saveCurrentState()
        navigateToStartPage()
    }

    private fun restartGameFromTemplate() {
        val sheet = currentSheet ?: return
        val restartedSheet = GameSheetFactory.createRestartedSheet(sheet)
        val newSheetId = gameSheetRepository.insertGameSheet(restartedSheet)
        gameSheetRepository.deleteGameSheet(sheet.id)
        currentSheet = null

        val intent = Intent(this, GameSheetActivity::class.java).apply {
            putExtra(EXTRA_SHEET_ID, newSheetId)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToStartPage() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_FLOATING_CONTROLS_ORIENTATION, floatingControlsOrientation)
    }
}
