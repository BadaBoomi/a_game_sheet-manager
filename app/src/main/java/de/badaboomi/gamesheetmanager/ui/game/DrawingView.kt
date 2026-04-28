package de.badaboomi.gamesheetmanager.ui.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import de.badaboomi.gamesheetmanager.utils.DrawPoint
import de.badaboomi.gamesheetmanager.utils.DrawStroke
import de.badaboomi.gamesheetmanager.utils.DrawingSerializer

/**
 * Custom view for drawing on top of a template image.
 * Supports multi-stroke drawing with configurable pen color and stroke width.
 * Also supports zoom/pan mode for inspecting the sheet without drawing.
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
        // Divide penWidth by the current zoom level so that the in-progress stroke
        // looks the same number of pixels wide on screen regardless of zoom factor.
        // When the stroke is committed it is stored with the same adjusted width, so
        // strokes drawn while zoomed appear proportionally thinner once the view is
        // zoomed back out – just as if drawn with a finer pen at that detail level.
        currentPaint.strokeWidth = penWidth / scaleFactor
    }

    // ── Zoom / pan state ────────────────────────────────────────────────────

    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f

    /** When true, touch events pan/zoom the view instead of drawing. */
    var zoomModeEnabled: Boolean = false

    /** The template bitmap to render inside this view (used while zoomed). */
    var templateBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Current zoom level (1 = no zoom). */
    val currentZoomFactor: Float get() = scaleFactor

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val ds = detector.scaleFactor
                val fx = detector.focusX
                val fy = detector.focusY
                val newScale = (scaleFactor * ds).coerceIn(1f, 8f)
                val actualDs = newScale / scaleFactor
                translateX = fx - (fx - translateX) * actualDs
                translateY = fy - (fy - translateY) * actualDs
                scaleFactor = newScale
                constrainTranslation()
                updateCurrentPaint()
                invalidate()
                return true
            }
        }
    )

    private var activePanPointerId = MotionEvent.INVALID_POINTER_ID
    private var lastPanX = 0f
    private var lastPanY = 0f

    /** Resets zoom and pan to the default (no zoom) state. */
    fun resetZoom() {
        scaleFactor = 1f
        translateX = 0f
        translateY = 0f
        updateCurrentPaint()
        invalidate()
    }

    private fun constrainTranslation() {
        if (width == 0 || height == 0) return
        translateX = translateX.coerceIn(-(scaleFactor - 1f) * width, 0f)
        translateY = translateY.coerceIn(-(scaleFactor - 1f) * height, 0f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        constrainTranslation()
    }

    // ── Stroke management ───────────────────────────────────────────────────

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

    // ── Drawing ─────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        // Draw the template bitmap when it has been provided (zoom/draw-zoomed modes)
        templateBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, computeFitCenterMatrix(bmp.width, bmp.height), null)
        }

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

        canvas.restore()
    }

    /** Builds a fitCenter matrix so the bitmap is centred inside the view. */
    private fun computeFitCenterMatrix(bitmapWidth: Int, bitmapHeight: Int): Matrix {
        val scale = minOf(width.toFloat() / bitmapWidth, height.toFloat() / bitmapHeight)
        val dx = (width - bitmapWidth * scale) / 2f
        val dy = (height - bitmapHeight * scale) / 2f
        return Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
    }

    // ── Touch handling ──────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (zoomModeEnabled) {
            return handleZoomPanTouch(event)
        }

        if (!isEnabled) return false

        // Convert screen coordinates to document (view) coordinates
        val x = (event.x - translateX) / scaleFactor
        val y = (event.y - translateY) / scaleFactor

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                updateCurrentPaint()
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
                            // Divide by scaleFactor so strokes appear visually consistent
                            // regardless of the current zoom level.
                            strokeWidth = penWidth / scaleFactor
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

    private fun handleZoomPanTouch(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePanPointerId = event.getPointerId(0)
                lastPanX = event.x
                lastPanY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second finger down – stop single-finger panning
                activePanPointerId = MotionEvent.INVALID_POINTER_ID
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleGestureDetector.isInProgress &&
                    activePanPointerId != MotionEvent.INVALID_POINTER_ID
                ) {
                    val pointerIndex = event.findPointerIndex(activePanPointerId)
                    if (pointerIndex >= 0) {
                        val dx = event.getX(pointerIndex) - lastPanX
                        val dy = event.getY(pointerIndex) - lastPanY
                        translateX += dx
                        translateY += dy
                        constrainTranslation()
                        invalidate()
                        lastPanX = event.getX(pointerIndex)
                        lastPanY = event.getY(pointerIndex)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val upPointerId = event.getPointerId(event.actionIndex)
                if (upPointerId == activePanPointerId) {
                    // Active finger lifted – resume panning with the remaining finger
                    val newIndex = if (event.actionIndex == 0) 1 else 0
                    if (newIndex < event.pointerCount) {
                        activePanPointerId = event.getPointerId(newIndex)
                        lastPanX = event.getX(newIndex)
                        lastPanY = event.getY(newIndex)
                    } else {
                        activePanPointerId = MotionEvent.INVALID_POINTER_ID
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePanPointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
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

