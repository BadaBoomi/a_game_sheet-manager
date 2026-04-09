package de.badaboomi.gamesheetmanager.ui.halloffame

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import de.badaboomi.gamesheetmanager.R
import de.badaboomi.gamesheetmanager.data.HallOfFameEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for displaying Hall of Fame entries in a ListView.
 */
class HallOfFameAdapter(
    private val entries: List<HallOfFameEntry>,
    private val onItemClick: (HallOfFameEntry) -> Unit,
    private val onDeleteClick: (HallOfFameEntry) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = entries.size
    override fun getItem(position: Int): HallOfFameEntry = entries[position]
    override fun getItemId(position: Int): Long = entries[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hall_of_fame, parent, false)

        val entry = entries[position]
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        view.findViewById<TextView>(R.id.tvGameName).text = entry.gameName
        view.findViewById<TextView>(R.id.tvTemplateName).text = entry.templateName
        view.findViewById<TextView>(R.id.tvCompletedDate).text =
            dateFormat.format(Date(entry.dateCompleted))

        val ivThumbnail = view.findViewById<ImageView>(R.id.ivEntryThumbnail)
        val file = File(entry.templateImagePath)
        if (file.exists()) {
            val bitmap = BitmapFactory.decodeFile(entry.templateImagePath)
            ivThumbnail.setImageBitmap(bitmap)
        } else {
            ivThumbnail.setImageResource(R.drawable.ic_template_placeholder)
        }

        view.findViewById<ImageButton>(R.id.btnDeleteEntry).setOnClickListener {
            onDeleteClick(entry)
        }

        view.setOnClickListener { onItemClick(entry) }

        return view
    }
}
