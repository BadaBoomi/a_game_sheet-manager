package de.badaboomi.gamesheetmanager.ui.game

import android.graphics.BitmapFactory
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
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
        const val MENU_ITEM_STYLE = 1
        const val MENU_ITEM_SAVE = 2
        const val MENU_ITEM_FINISH_AND_SAVE = 3
        const val MENU_ITEM_SAVE_AND_FINISH = 4
        const val MENU_ITEM_FINISH_WITHOUT_SAVE = 5
    }

    private lateinit var binding: ActivityGameSheetBinding
    private lateinit var gameSheetRepository: GameSheetRepository
    private lateinit var hallOfFameRepository: HallOfFameRepository
    private var currentSheet: GameSheet? = null
    private var sheetId: Long = -1
    private var isDraggingMenuButton = false
    private var dragTouchOffsetX = 0f
    private var dragTouchOffsetY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameSheetBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        val menuButton = binding.btnShowControls
        menuButton.setColorFilter(binding.drawingView.penColor)
        val gestureDetector = GestureDetectorCompat(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onDoubleTap(e: MotionEvent): Boolean {
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

        menuButton.setOnTouchListener { view, event ->
            gestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingMenuButton) {
                        repositionMenuButton(view, event)
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

    private fun repositionMenuButton(view: View, event: MotionEvent) {
        val parentView = view.parent as? View ?: return
        val parentLocation = IntArray(2)
        parentView.getLocationOnScreen(parentLocation)

        val targetX = event.rawX - parentLocation[0] - dragTouchOffsetX
        val targetY = event.rawY - parentLocation[1] - dragTouchOffsetY

        view.x = targetX.coerceIn(0f, (parentView.width - view.width).toFloat())
        view.y = targetY.coerceIn(0f, (parentView.height - view.height).toFloat())
    }

    private fun showFloatingMenu() {
        PopupMenu(this, binding.btnShowControls).apply {
            menu.add(0, MENU_ITEM_STYLE, 0, getString(R.string.action_change_pen_style))
            menu.add(0, MENU_ITEM_SAVE, 1, getString(R.string.action_save))
            menu.add(0, MENU_ITEM_FINISH_AND_SAVE, 2, getString(R.string.action_finish_and_save_hof))
            menu.add(0, MENU_ITEM_SAVE_AND_FINISH, 3, getString(R.string.action_save_and_finish))
            menu.add(0, MENU_ITEM_FINISH_WITHOUT_SAVE, 4, getString(R.string.action_finish_without_save))

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_ITEM_STYLE -> {
                        showColorPicker()
                        true
                    }

                    MENU_ITEM_SAVE -> {
                        saveCurrentState()
                        true
                    }

                    MENU_ITEM_FINISH_AND_SAVE -> {
                        finishGame()
                        true
                    }

                    MENU_ITEM_SAVE_AND_FINISH -> {
                        saveAndFinish()
                        true
                    }

                    MENU_ITEM_FINISH_WITHOUT_SAVE -> {
                        finishWithoutSaving()
                        true
                    }

                    else -> false
                }
            }
        }.show()
    }

    private fun showColorPicker() {
        ColorPickerDialog(
            context = this,
            currentColor = binding.drawingView.penColor,
            currentWidth = binding.drawingView.penWidth
        ) { color, width ->
            binding.drawingView.penColor = color
            binding.drawingView.penWidth = width
            binding.btnShowControls.setColorFilter(color)
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
}
