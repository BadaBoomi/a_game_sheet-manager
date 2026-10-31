package de.badaboomi.gamesheetmanager.ui.game

import kotlin.math.roundToInt

object FloatingMenuRotation {
    fun rotationForOrientation(isLandscape: Boolean): Float = if (isLandscape) 90f else 0f

    fun nextRotation(currentRotationDegrees: Float): Float {
        val normalized = ((currentRotationDegrees % 360f) + 360f) % 360f
        val quarterTurns = (normalized / 90f).roundToInt() % 4
        val nextQuarterTurn = (quarterTurns + 1) % 4
        return nextQuarterTurn * 90f
    }

    fun effectiveRotationDegrees(isLandscape: Boolean, manualRotationOffsetDegrees: Float): Float =
        rotationForOrientation(isLandscape) + manualRotationOffsetDegrees
}
