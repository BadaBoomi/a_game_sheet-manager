package de.badaboomi.gamesheetmanager.ui.halloffame

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHallOfFameViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }

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
}
