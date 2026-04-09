package de.badaboomi.gamesheetmanager.ui.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import de.badaboomi.gamesheetmanager.utils.DrawPoint
import de.badaboomi.gamesheetmanager.utils.DrawStroke
import de.badaboomi.gamesheetmanager.utils.DrawingSerializer

/**
 * Custom view for drawing on top of a template image.
 * Supports multi-stroke drawing with configurable pen color and stroke width.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val completedStrokes = mutableListOf<DrawStroke>()
    private val currentPoints = mutableListOf<DrawPoint>()
    private var currentPath = Path()

    var penColor: Int = Color.BLACK
        set(value) {
            field = value
            updateCurrentPaint()
        }

    var penWidth: Float = 8f
        set(value) {
            field = value
            updateCurrentPaint()
        }

    private val currentPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = penColor
        strokeWidth = penWidth
    }

    private fun updateCurrentPaint() {
        currentPaint.color = penColor
        currentPaint.strokeWidth = penWidth
    }

    /**
     * Loads previously saved drawing strokes.
     */
    fun loadStrokes(json: String) {
        completedStrokes.clear()
        completedStrokes.addAll(DrawingSerializer.deserialize(json))
        invalidate()
    }

    /**
     * Serializes the current drawing to a JSON string for saving.
     */
    fun getDrawingData(): String {
        return DrawingSerializer.serialize(completedStrokes)
    }

    /**
     * Clears all drawing strokes.
     */
    fun clearDrawing() {
        completedStrokes.clear()
        currentPoints.clear()
        currentPath.reset()
        invalidate()
    }

    /**
     * Undoes the last stroke.
     */
    fun undoLastStroke() {
        if (completedStrokes.isNotEmpty()) {
            completedStrokes.removeAt(completedStrokes.size - 1)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw completed strokes
        for (stroke in completedStrokes) {
            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                color = stroke.color
                strokeWidth = stroke.strokeWidth
            }
            val path = buildPath(stroke.points)
            canvas.drawPath(path, paint)
        }

        // Draw the current (in-progress) stroke
        if (currentPoints.isNotEmpty()) {
            canvas.drawPath(currentPath, currentPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath.reset()
                currentPath.moveTo(x, y)
                currentPoints.clear()
                currentPoints.add(DrawPoint(x, y))
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                currentPoints.add(DrawPoint(x, y))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(x, y)
                currentPoints.add(DrawPoint(x, y))
                if (currentPoints.size > 1) {
                    completedStrokes.add(
                        DrawStroke(
                            points = currentPoints.toList(),
                            color = penColor,
                            strokeWidth = penWidth
                        )
                    )
                }
                currentPath.reset()
                currentPoints.clear()
                invalidate()
                return true
            }
        }
        return false
    }

    private fun buildPath(points: List<DrawPoint>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        return path
    }

    /**
     * Renders the current drawing to a Bitmap (for sharing or saving as image).
     */
    fun renderToBitmap(templateBitmap: Bitmap?): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (templateBitmap != null) {
            canvas.drawBitmap(
                Bitmap.createScaledBitmap(templateBitmap, width, height, true),
                0f, 0f, null
            )
        } else {
            canvas.drawColor(Color.WHITE)
        }

        for (stroke in completedStrokes) {
            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                color = stroke.color
                strokeWidth = stroke.strokeWidth
            }
            canvas.drawPath(buildPath(stroke.points), paint)
        }

        return bitmap
    }
}
