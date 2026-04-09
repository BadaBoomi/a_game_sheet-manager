package de.badaboomi.gamesheetmanager.utils

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a single point in a drawing path.
 */
data class DrawPoint(val x: Float, val y: Float)

/**
 * Represents a complete drawing stroke (a series of points with paint settings).
 */
data class DrawStroke(
    val points: List<DrawPoint>,
    val color: Int = Color.BLACK,
    val strokeWidth: Float = 8f
)

/**
 * Handles serialization and deserialization of drawing data to/from JSON.
 */
object DrawingSerializer {

    /**
     * Serializes a list of draw strokes to a JSON string.
     */
    fun serialize(strokes: List<DrawStroke>): String {
        val array = JSONArray()
        for (stroke in strokes) {
            val strokeObj = JSONObject()
            strokeObj.put("color", stroke.color)
            strokeObj.put("strokeWidth", stroke.strokeWidth.toDouble())
            val pointsArray = JSONArray()
            for (point in stroke.points) {
                val pointObj = JSONObject()
                pointObj.put("x", point.x.toDouble())
                pointObj.put("y", point.y.toDouble())
                pointsArray.put(pointObj)
            }
            strokeObj.put("points", pointsArray)
            array.put(strokeObj)
        }
        return array.toString()
    }

    /**
     * Deserializes a JSON string to a list of draw strokes.
     */
    fun deserialize(json: String): List<DrawStroke> {
        val strokes = mutableListOf<DrawStroke>()
        return try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val strokeObj = array.getJSONObject(i)
                val color = strokeObj.getInt("color")
                val strokeWidth = strokeObj.getDouble("strokeWidth").toFloat()
                val points = mutableListOf<DrawPoint>()
                val pointsArray = strokeObj.getJSONArray("points")
                for (j in 0 until pointsArray.length()) {
                    val pointObj = pointsArray.getJSONObject(j)
                    points.add(
                        DrawPoint(
                            x = pointObj.getDouble("x").toFloat(),
                            y = pointObj.getDouble("y").toFloat()
                        )
                    )
                }
                strokes.add(DrawStroke(points, color, strokeWidth))
            }
            strokes
        } catch (e: Exception) {
            emptyList()
        }
    }
}
