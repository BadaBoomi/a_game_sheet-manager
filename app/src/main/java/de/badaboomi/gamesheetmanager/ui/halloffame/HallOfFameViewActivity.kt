package de.badaboomi.gamesheetmanager.ui.halloffame

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import de.badaboomi.gamesheetmanager.databinding.ActivityHallOfFameViewBinding
import de.badaboomi.gamesheetmanager.repository.HallOfFameRepository
import java.io.File

/**
 * Read-only activity for viewing a completed game sheet from the Hall of Fame.
 */
class HallOfFameViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
    }

    private lateinit var binding: ActivityHallOfFameViewBinding
    private lateinit var repository: HallOfFameRepository
    private var isDragging = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHallOfFameViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackButton()

        repository = HallOfFameRepository(this)

        val entryId = intent.getLongExtra(EXTRA_ENTRY_ID, -1)
        if (entryId == -1L) {
            finish()
            return
        }

        loadEntry(entryId)
    }

    private fun loadEntry(entryId: Long) {
        val entry = repository.getEntryById(entryId) ?: run {
            finish()
            return
        }

        // Load template image as background
        val imageFile = File(entry.templateImagePath)
        if (imageFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(entry.templateImagePath)
            binding.ivTemplateBackground.setImageBitmap(bitmap)
        }

        // Load the drawing (read-only: touch is disabled)
        binding.drawingView.loadStrokes(entry.drawingData)
        binding.drawingView.isEnabled = false
    }

    private fun setupBackButton() {
        val btn = binding.btnBack
        val gestureDetector = GestureDetectorCompat(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    finish()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    isDragging = true
                    dragOffsetX = e.x
                    dragOffsetY = e.y
                    btn.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        )

        btn.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val parent = btn.parent as? View ?: return@setOnTouchListener true
                        btn.x = (event.rawX - dragOffsetX)
                            .coerceIn(0f, (parent.width - btn.width).toFloat())
                        btn.y = (event.rawY - dragOffsetY)
                            .coerceIn(0f, (parent.height - btn.height).toFloat())
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        isDragging = false
                        return@setOnTouchListener true
                    }
                }
            }
            true
        }
    }
}
