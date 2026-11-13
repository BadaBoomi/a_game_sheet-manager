package de.badaboomi.gamesheetmanager.ui.game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import com.google.android.material.button.MaterialButton
import de.badaboomi.gamesheetmanager.MainActivity
import de.badaboomi.gamesheetmanager.R
import de.badaboomi.gamesheetmanager.data.GameSheet
import de.badaboomi.gamesheetmanager.data.HallOfFameEntry
import de.badaboomi.gamesheetmanager.data.Template
import de.badaboomi.gamesheetmanager.databinding.ActivityGameSheetBinding
import de.badaboomi.gamesheetmanager.repository.GameSheetRepository
import de.badaboomi.gamesheetmanager.repository.HallOfFameRepository
import de.badaboomi.gamesheetmanager.repository.TemplateRepository
import java.io.File

/**
 * Activity for playing a game on a sheet.
 * Displays the template image with a drawing overlay.
 * Supports pen color/width selection, intermediate save, and saving to Hall of Fame.
 */
class GameSheetActivity : AppCompatActivity() {
    // Store the original pen width for zoom mode logic
    private var originalPenWidth: Float? = null

    companion object {
        const val EXTRA_SHEET_ID = "sheet_id"

        internal fun getInitialHamburgerMenuRotation(isLandscape: Boolean): Float =
            if (isLandscape) 90f else 0f

        internal fun rotateHamburgerMenu(currentRotation: Float): Float {
            val normalized = ((currentRotation % 360f) + 360f) % 360f
            return if (normalized >= 270f) 0f else normalized + 90f
        }

        internal fun normalizeRotationForTemplateStorage(rotation: Float): Float {
            val normalized = ((rotation % 360f) + 360f) % 360f
            return when {
                normalized < 45f -> 0f
                normalized < 135f -> 90f
                normalized < 225f -> 180f
                normalized < 315f -> 270f
                else -> 0f
            }
        }

        internal fun getCurrentMenuRotation(isLandscape: Boolean, manualRotation: Float): Float {
            val orientationRotation = getInitialHamburgerMenuRotation(isLandscape)
            val combinedRotation = orientationRotation + manualRotation
            val normalized = ((combinedRotation % 360f) + 360f) % 360f
            return normalized
        }
    }

    /** The three-state zoom cycle: NORMAL → ZOOM_PAN → DRAW_ZOOMED → NORMAL */
    private enum class ZoomState { NORMAL, ZOOM_PAN, DRAW_ZOOMED }

    private lateinit var binding: ActivityGameSheetBinding
    private lateinit var gameSheetRepository: GameSheetRepository
    private lateinit var hallOfFameRepository: HallOfFameRepository
    private lateinit var templateRepository: TemplateRepository
    private var currentSheet: GameSheet? = null
    private var currentTemplate: Template? = null
    private var sheetId: Long = -1
    private var isDraggingMenuButton = false
    private var dragTouchOffsetX = 0f
    private var dragTouchOffsetY = 0f

    private var zoomState = ZoomState.NORMAL
    private var templateBitmapCache: Bitmap? = null
    private var gameSheetRotationDegrees = 0f
    private var popupMenuRotationDegrees = 0f
    private var menuOrientationListener: OrientationEventListener? = null
    private var isLandscapeMenuLayout = false
    private var lastMenuLayoutSwitchAtMs: Long = 0L
    private var floatingMenuActionsLayout: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameSheetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gameSheetRepository = GameSheetRepository(this)
        hallOfFameRepository = HallOfFameRepository(this)
        templateRepository = TemplateRepository(this)

        sheetId = intent.getLongExtra(EXTRA_SHEET_ID, -1)
        if (sheetId == -1L) {
            finish()
            return
        }

        loadGameSheet()
        setupMenuOrientationTracking()
        setupFloatingMenuButton()
        setupZoomButton()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isLandscapeMenuLayout = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyFloatingControlsOrientation(isLandscapeMenuLayout)
    }

    override fun onResume() {
        super.onResume()
        menuOrientationListener?.enable()
    }

    private fun loadGameSheet() {
        val sheet = gameSheetRepository.getGameSheetById(sheetId) ?: run {
            finish()
            return
        }
        currentSheet = sheet
        currentTemplate = templateRepository.getTemplateById(sheet.templateId)

        // Load template image
        val imageFile = File(sheet.templateImagePath)
        if (imageFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(sheet.templateImagePath)
            binding.ivTemplateBackground.setImageBitmap(bitmap)
            templateBitmapCache = bitmap
        }

        // Load existing drawing strokes
        binding.drawingView.loadStrokes(sheet.drawingData)

        gameSheetRotationDegrees = normalizeRotationForTemplateStorage(
            currentTemplate?.sheetRotationDegrees ?: 0f
        )
        popupMenuRotationDegrees = normalizeRotationForTemplateStorage(
            currentTemplate?.menuRotationDegrees ?: 0f
        )
        applyGameSheetRotation()
    }

    // ── Zoom ─────────────────────────────────────────────────────────────────

    private fun setupZoomButton() {
        binding.btnZoom.setOnClickListener {
            when (zoomState) {
                ZoomState.NORMAL -> enterZoomPanMode()
                ZoomState.ZOOM_PAN -> enterDrawZoomedMode()
                ZoomState.DRAW_ZOOMED -> exitZoomMode()
            }
        }
    }

    /**
     * First tap on zoom: enter zoom/pan mode.
     * The user can zoom and pan but cannot draw.
     */
    private fun enterZoomPanMode() {
        zoomState = ZoomState.ZOOM_PAN
        binding.drawingView.zoomModeEnabled = true
        binding.drawingView.templateBitmap = templateBitmapCache
        binding.ivTemplateBackground.visibility = View.INVISIBLE
        updateControlStates()
    }

    /**
     * Second tap on zoom: keep the current zoom level and resume drawing.
     * Pen size is automatically scaled to the zoom factor.
     */
    private fun enterDrawZoomedMode() {
        zoomState = ZoomState.DRAW_ZOOMED
        binding.drawingView.zoomModeEnabled = false
        // Store the original pen width if not already stored
        if (originalPenWidth == null) {
            originalPenWidth = binding.drawingView.penWidth
        }
        // Scale pen width by current zoom factor
        binding.drawingView.penWidth = (originalPenWidth ?: binding.drawingView.penWidth) * binding.drawingView.currentZoomFactor
        // templateBitmap stays set; ivTemplateBackground stays hidden so the
        // zoomed template and strokes remain aligned.
        updateControlStates()
    }

    /**
     * Third tap on zoom: return to normal (no-zoom) mode.
     */
    private fun exitZoomMode() {
        zoomState = ZoomState.NORMAL
        binding.drawingView.zoomModeEnabled = false
        binding.drawingView.resetZoom()
        binding.drawingView.templateBitmap = null
        binding.ivTemplateBackground.visibility = View.VISIBLE
        // Restore original pen width if it was changed
        originalPenWidth?.let {
            binding.drawingView.penWidth = it
            originalPenWidth = null
        }
        updateControlStates()
    }

    /**
     * Enables/disables and visually indicates the controls for each zoom state.
     */
    private fun updateControlStates() {
        when (zoomState) {
            ZoomState.NORMAL -> {
                binding.btnQuickUndo.isEnabled = true
                binding.btnQuickUndo.alpha = 1f
                // Restore the zoom button to its default appearance.
                binding.btnZoom.clearColorFilter()
            }
            ZoomState.ZOOM_PAN -> {
                // Only menu drag/drop remains usable; undo is disabled.
                binding.btnQuickUndo.isEnabled = false
                binding.btnQuickUndo.alpha = 0.35f
                // Highlight zoom button in blue to signal active zoom/pan mode.
                binding.btnZoom.setColorFilter(
                    resources.getColor(R.color.zoom_pan_tint, theme)
                )
            }
            ZoomState.DRAW_ZOOMED -> {
                binding.btnQuickUndo.isEnabled = true
                binding.btnQuickUndo.alpha = 1f
                // Highlight zoom button in orange to signal draw-while-zoomed mode.
                binding.btnZoom.setColorFilter(
                    resources.getColor(R.color.zoom_draw_tint, theme)
                )
            }
        }
    }

    // ── Floating menu / drag ─────────────────────────────────────────────────

    private fun setupFloatingMenuButton() {
        val controls = binding.floatingControls
        val menuButton = binding.btnMenuHandle
        binding.btnQuickUndo.setOnClickListener {
            binding.drawingView.undoLastStroke()
        }
        binding.btnRotate.setOnClickListener {
            rotateGameSheetClockwise()
        }
        menuButton.setOnClickListener {
            showFloatingMenu()
        }

        val gestureDetector = GestureDetectorCompat(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onLongPress(e: MotionEvent) {
                    isDraggingMenuButton = true
                    val controlsLocation = IntArray(2)
                    controls.getLocationOnScreen(controlsLocation)
                    dragTouchOffsetX = e.rawX - controlsLocation[0]
                    dragTouchOffsetY = e.rawY - controlsLocation[1]
                    controls.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        )

        val dragTouchListener = View.OnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingMenuButton) {
                        repositionFloatingControls(event)
                        return@OnTouchListener true
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingMenuButton) {
                        isDraggingMenuButton = false
                        return@OnTouchListener true
                    }
                }
            }

            // Allow normal clicks when we are not actively dragging.
            isDraggingMenuButton
        }

        // Drag can start from any icon and from free area inside the 2x2 control block.
        controls.setOnTouchListener(dragTouchListener)
        binding.btnZoom.setOnTouchListener(dragTouchListener)
        binding.btnQuickUndo.setOnTouchListener(dragTouchListener)
        binding.btnRotate.setOnTouchListener(dragTouchListener)
        binding.btnMenuHandle.setOnTouchListener(dragTouchListener)
    }

    private fun setupMenuOrientationTracking() {
        isLandscapeMenuLayout = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyFloatingControlsOrientation(isLandscapeMenuLayout)

        menuOrientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                // Landscape when device is rotated ~90° or ~270°.
                val shouldUseLandscapeLayout =
                    orientation in 60..120 || orientation in 240..300

                val now = System.currentTimeMillis()
                if (shouldUseLandscapeLayout != isLandscapeMenuLayout && now - lastMenuLayoutSwitchAtMs >= 250L) {
                    isLandscapeMenuLayout = shouldUseLandscapeLayout
                    applyFloatingControlsOrientation(isLandscapeMenuLayout)
                    lastMenuLayoutSwitchAtMs = now
                }
            }
        }
    }

    private fun applyFloatingControlsOrientation(isLandscape: Boolean) {
        // Keep the 2x2 square and current position untouched.
        // Only rotate icon orientation in landscape.
        val iconRotation = getInitialHamburgerMenuRotation(isLandscape)
        binding.btnZoom.rotation = iconRotation
        binding.btnQuickUndo.rotation = iconRotation
        binding.btnRotate.rotation = iconRotation
        binding.btnMenuHandle.rotation = iconRotation

        floatingMenuActionsLayout?.rotation = getCurrentMenuRotation(isLandscape, popupMenuRotationDegrees)
    }

    private fun rotateGameSheetClockwise() {
        gameSheetRotationDegrees = rotateHamburgerMenu(gameSheetRotationDegrees)
        applyGameSheetRotation()
        persistTemplateRotations()
    }

    private fun applyGameSheetRotation() {
        binding.ivTemplateBackground.rotation = gameSheetRotationDegrees
        binding.drawingView.rotation = gameSheetRotationDegrees
    }

    private fun persistTemplateRotations() {
        val template = currentTemplate ?: return
        val normalizedSheetRotation = normalizeRotationForTemplateStorage(gameSheetRotationDegrees)
        val normalizedMenuRotation = normalizeRotationForTemplateStorage(popupMenuRotationDegrees)

        if (template.sheetRotationDegrees == normalizedSheetRotation &&
            template.menuRotationDegrees == normalizedMenuRotation
        ) {
            return
        }

        val updatedTemplate = template.copy(
            sheetRotationDegrees = normalizedSheetRotation,
            menuRotationDegrees = normalizedMenuRotation
        )
        templateRepository.updateTemplate(updatedTemplate)
        currentTemplate = updatedTemplate
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
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        }
        val dialog = AlertDialog.Builder(this).setView(layout).create()
        val actionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            rotation = getCurrentMenuRotation(isLandscapeMenuLayout, popupMenuRotationDegrees)
        }
        floatingMenuActionsLayout = actionsLayout
        val rotateButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_rotate_right)
            setBackgroundResource(R.drawable.bg_floating_menu)
            contentDescription = getString(R.string.action_rotate_menu)
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            imageTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(android.R.color.black, theme)
            )
            elevation = 8f * dp
            rotation = getCurrentMenuRotation(isLandscapeMenuLayout, popupMenuRotationDegrees)
            setOnClickListener {
                popupMenuRotationDegrees = rotateHamburgerMenu(popupMenuRotationDegrees)
                val newMenuRotation = getCurrentMenuRotation(isLandscapeMenuLayout, popupMenuRotationDegrees)
                actionsLayout.rotation = newMenuRotation
                rotateButton.rotation = newMenuRotation
                persistTemplateRotations()
            }
        }

        rotateButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.END
        }
        layout.addView(rotateButton)

        actionsLayout.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layout.addView(actionsLayout)

        fun addButton(label: String, enabled: Boolean = true, action: () -> Unit) {
            val button = MaterialButton(this).apply {
                text = label
                isEnabled = enabled
                alpha = if (enabled) 1f else 0.35f
                setOnClickListener { dialog.dismiss(); action() }
            }

            button.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, (4 * dp).toInt(), 0, (4 * dp).toInt()) }

            actionsLayout.addView(button)
        }

        addButton(getString(R.string.action_restart_game)) { restartGameFromTemplate() }
        addButton(getString(R.string.action_finish_and_save_hof)) { finishGame() }
        addButton(
            label = getString(R.string.action_settings),
            enabled = zoomState != ZoomState.ZOOM_PAN
        ) { showColorPicker() }
        addButton(getString(R.string.action_finish_without_save)) { saveAndFinish() }

        dialog.show()
        val horizontalMarginPx = (24 * dp).toInt()
        val maxDialogWidthPx = (360 * dp).toInt()
        val widthByScreenPx = resources.displayMetrics.widthPixels - (horizontalMarginPx * 2)
        val dialogWidthPx = minOf(maxDialogWidthPx, widthByScreenPx)
        if (dialogWidthPx > 0) {
            dialog.window?.setLayout(dialogWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
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
        }.show()
    }

    // ── Game actions ─────────────────────────────────────────────────────────

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
        val dialog = AlertDialog.Builder(this)
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
            .create()

        dialog.show()
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
        menuOrientationListener?.disable()
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
