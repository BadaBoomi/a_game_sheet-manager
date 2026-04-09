package de.badaboomi.gamesheetmanager.ui.templates

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import de.badaboomi.gamesheetmanager.R
import de.badaboomi.gamesheetmanager.data.Template
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for displaying templates in a ListView.
 */
class TemplateAdapter(
    private val templates: List<Template>,
    private val onItemClick: (Template) -> Unit,
    private val onDeleteClick: (Template) -> Unit,
    private val showDeleteButton: Boolean
) : BaseAdapter() {

    override fun getCount(): Int = templates.size
    override fun getItem(position: Int): Template = templates[position]
    override fun getItemId(position: Int): Long = templates[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_template, parent, false)

        val template = templates[position]
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

        view.findViewById<TextView>(R.id.tvTemplateName).text = template.name
        view.findViewById<TextView>(R.id.tvTemplateDate).text =
            dateFormat.format(Date(template.dateCreated))

        val ivThumbnail = view.findViewById<ImageView>(R.id.ivTemplateThumbnail)
        val file = File(template.imagePath)
        if (file.exists()) {
            val bitmap = BitmapFactory.decodeFile(template.imagePath)
            ivThumbnail.setImageBitmap(bitmap)
        } else {
            ivThumbnail.setImageResource(R.drawable.ic_template_placeholder)
        }

        val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteTemplate)
        btnDelete.visibility = if (showDeleteButton) View.VISIBLE else View.GONE
        btnDelete.setOnClickListener { onDeleteClick(template) }

        view.setOnClickListener { onItemClick(template) }

        return view
    }
}
