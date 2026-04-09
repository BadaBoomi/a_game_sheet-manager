package de.badaboomi.gamesheetmanager.ui.halloffame

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import de.badaboomi.gamesheetmanager.R
import de.badaboomi.gamesheetmanager.data.HallOfFameEntry
import de.badaboomi.gamesheetmanager.databinding.ActivityHallOfFameBinding
import de.badaboomi.gamesheetmanager.repository.HallOfFameRepository

/**
 * Activity showing all completed game sheets saved in the Hall of Fame.
 * These entries are read-only.
 */
class HallOfFameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHallOfFameBinding
    private lateinit var repository: HallOfFameRepository
    private lateinit var adapter: HallOfFameAdapter
    private val entries = mutableListOf<HallOfFameEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHallOfFameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = HallOfFameRepository(this)
        setupList()
        loadEntries()
    }

    override fun onResume() {
        super.onResume()
        loadEntries()
    }

    private fun setupList() {
        adapter = HallOfFameAdapter(
            entries = entries,
            onItemClick = { entry -> openEntry(entry) },
            onDeleteClick = { entry -> confirmDelete(entry) }
        )
        binding.listHallOfFame.adapter = adapter
    }

    private fun loadEntries() {
        entries.clear()
        entries.addAll(repository.getAllEntries())
        adapter.notifyDataSetChanged()

        binding.emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        binding.listHallOfFame.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openEntry(entry: HallOfFameEntry) {
        val intent = Intent(this, HallOfFameViewActivity::class.java)
        intent.putExtra(HallOfFameViewActivity.EXTRA_ENTRY_ID, entry.id)
        startActivity(intent)
    }

    private fun confirmDelete(entry: HallOfFameEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_hof_title)
            .setMessage(getString(R.string.dialog_delete_hof_message, entry.gameName))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                repository.deleteEntry(entry.id)
                loadEntries()
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
