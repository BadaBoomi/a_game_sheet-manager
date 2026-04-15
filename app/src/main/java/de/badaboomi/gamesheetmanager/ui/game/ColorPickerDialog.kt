package de.badaboomi.gamesheetmanager.ui.game

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import de.badaboomi.gamesheetmanager.R

/**
 * A simple color picker dialog for selecting pen color and stroke width.
 */
class ColorPickerDialog(
    context: Context,
    private val currentColor: Int,
    private val currentWidth: Float,
    private val onColorSelected: (color: Int, width: Float) -> Unit
) : Dialog(context, R.style.Theme_GameSheetManager_Dialog) {

    private val predefinedColors = listOf(
        Color.BLACK,
        Color.RED,
        Color.parseColor("#FF6600"),    // Orange
        Color.YELLOW,
        Color.GREEN,
        Color.parseColor("#00CC00"),    // Dark Green
        Color.BLUE,
        Color.parseColor("#8800AA"),    // Purple
        Color.parseColor("#FF69B4"),    // Pink
        Color.WHITE,
        Color.GRAY,
        Color.parseColor("#8B4513")     // Brown
    )

    private var selectedColor = currentColor
    private var selectedWidth = currentWidth

    init {
        buildUI()
    }

    private fun buildUI() {
        val ctx = context
        val dp = ctx.resources.displayMetrics.density

        val scrollView = ScrollView(ctx)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16 * dp).toInt(), (16 * dp).toInt(),
                (16 * dp).toInt(), (16 * dp).toInt()
            )
        }

        // Title
        val titleView = TextView(ctx).apply {
            text = ctx.getString(R.string.color_picker_title)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (12 * dp).toInt())
        }
        container.addView(titleView)

        // Color grid
        val colorLabel = TextView(ctx).apply {
            text = ctx.getString(R.string.label_pen_color)
            textSize = 14f
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
        container.addView(colorLabel)

        val colorGrid = GridLayout(ctx).apply {
            columnCount = 4
            rowCount = 3
        }

        for (color in predefinedColors) {
            val colorView = ImageView(ctx).apply {
                val size = (40 * dp).toInt()
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(
                        (4 * dp).toInt(), (4 * dp).toInt(),
                        (4 * dp).toInt(), (4 * dp).toInt()
                    )
                }
                val shape = GradientDrawable()
                shape.shape = GradientDrawable.OVAL
                shape.setColor(color)
                shape.setStroke((2 * dp).toInt(), Color.GRAY)
                background = shape

                setOnClickListener {
                    selectedColor = color
                    updateColorSelection(colorGrid, this)
                }
            }
            colorGrid.addView(colorView)
        }

        container.addView(colorGrid)

        // Stroke width section
        val widthLabel = TextView(ctx).apply {
            text = ctx.getString(R.string.label_pen_width)
            textSize = 14f
            setPadding(0, (16 * dp).toInt(), 0, (8 * dp).toInt())
        }
        container.addView(widthLabel)

        val widthPreview = ImageView(ctx).apply {
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            shape.cornerRadius = selectedWidth
            shape.setColor(selectedColor)
            background = shape
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (selectedWidth * dp).toInt().coerceAtLeast((8 * dp).toInt())
            ).apply {
                setMargins(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
            }
        }
        container.addView(widthPreview)

        val widthSeekBar = SeekBar(ctx).apply {
            max = 48
            progress = selectedWidth.toInt() - 2
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    selectedWidth = (progress + 2).toFloat()
                    widthPreview.layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (selectedWidth * dp).toInt().coerceAtLeast((8 * dp).toInt())
                    ).apply {
                        setMargins(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
                    }
                    widthPreview.requestLayout()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        container.addView(widthSeekBar)

        // Buttons
        val buttonRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (16 * dp).toInt(), 0, 0)
        }

        val cancelBtn = android.widget.Button(ctx).apply {
            text = ctx.getString(android.R.string.cancel)
            setOnClickListener { dismiss() }
        }

        val okBtn = android.widget.Button(ctx).apply {
            text = ctx.getString(android.R.string.ok)
            setOnClickListener {
                onColorSelected(selectedColor, selectedWidth)
                dismiss()
            }
        }

        buttonRow.addView(cancelBtn)
        buttonRow.addView(okBtn)
        container.addView(buttonRow)

        scrollView.addView(container)
        setContentView(scrollView)
        window?.setLayout(
            (320 * dp).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun updateColorSelection(grid: GridLayout, selected: ImageView) {
        val dp = context.resources.displayMetrics.density
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i) as? ImageView ?: continue
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.OVAL
            if (child == selected) {
                shape.setColor(selectedColor)
                shape.setStroke((3 * dp).toInt(), Color.BLACK)
            } else {
                val idx = i
                if (idx < predefinedColors.size) {
                    shape.setColor(predefinedColors[idx])
                }
                shape.setStroke((2 * dp).toInt(), Color.GRAY)
            }
            child.background = shape
        }
    }
}
