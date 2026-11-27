package app.housethis.core.vision

import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.sqrt

// Coordinates: pixel (i, j) covers [i, i+1) x [j, j+1); y grows downward.

@Serializable
data class Point2(val x: Float, val y: Float) {
    val length: Float get() = sqrt(x * x + y * y)
}

@Serializable
data class LineSegment(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val width: Float,
    val logNfa: Float,
) {
    val length: Float get() = sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
    val angleRad: Float get() = atan2(y2 - y1, x2 - x1)

    fun scaled(factor: Float) = copy(
        x1 = x1 * factor, y1 = y1 * factor, x2 = x2 * factor, y2 = y2 * factor, width = width * factor,
    )
}

@Serializable
data class Corner(val x: Float, val y: Float, val score: Float) {
    fun scaled(factor: Float) = copy(x = x * factor, y = y * factor)
}

enum class JunctionKind { L, T, X }

@Serializable
data class Junction(
    val x: Float,
    val y: Float,
    val lineA: Int,
    val lineB: Int,
    val kind: JunctionKind,
    val angleDeg: Float,
) {
    fun scaled(factor: Float) = copy(x = x * factor, y = y * factor)
}

@Serializable
data class ImageFeatures(
    val width: Int,
    val height: Int,
    val lines: List<LineSegment>,
    val corners: List<Corner>,
    val junctions: List<Junction>,
    val elapsedMs: Long = 0,
) {
    fun scaledTo(targetWidth: Int, targetHeight: Int): ImageFeatures {
        val factor = targetWidth.toFloat() / width
        return copy(
            width = targetWidth,
            height = targetHeight,
            lines = lines.map { it.scaled(factor) },
            corners = corners.map { it.scaled(factor) },
            junctions = junctions.map { it.scaled(factor) },
        )
    }
}
