package de.badaboomi.gamesheetmanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import de.badaboomi.gamesheetmanager.databinding.ActivityMainBinding
import de.badaboomi.gamesheetmanager.ui.halloffame.HallOfFameActivity
import de.badaboomi.gamesheetmanager.ui.templates.TemplateListActivity

/**
 * Main entry point of the GameSheetManager app.
 * Shows navigation options: Manage Templates, Start Game, Hall of Fame.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.btnManageTemplates.setOnClickListener {
            startActivity(Intent(this, TemplateListActivity::class.java))
        }

        binding.btnStartGame.setOnClickListener {
            // Open template list in "select" mode to start a game
            val intent = Intent(this, TemplateListActivity::class.java)
            intent.putExtra(TemplateListActivity.EXTRA_MODE, TemplateListActivity.MODE_SELECT)
            startActivity(intent)
        }

        binding.btnHallOfFame.setOnClickListener {
            startActivity(Intent(this, HallOfFameActivity::class.java))
        }

        binding.btnUsageGuide.setOnClickListener {
            showUsageGuide()
        }

        binding.tvContactEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${getString(R.string.support_email)}"))
            startActivity(Intent.createChooser(intent, null))
        }

        // Display version info
        binding.tvVersion.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)
    }

    private fun showUsageGuide() {
        AlertDialog.Builder(this)
            .setTitle(R.string.title_usage_guide)
            .setMessage(getString(R.string.message_usage_guide))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

}
