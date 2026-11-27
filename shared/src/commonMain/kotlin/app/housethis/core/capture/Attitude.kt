package app.housethis.core.capture

import app.housethis.core.vision.LineSegment
import app.housethis.core.vision.Point2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class LineOrientation { PLUMB, LEVEL, OTHER }

/**
 * Maps device-frame gravity into an upright image.
 *
 * [rotationDegrees] = clockwise rotation of the device from natural portrait at capture (0, 90, 180, 270),
 * the same value used to make the saved image upright.
 */
class Attitude(down: Vec3, val rotationDegrees: Int) {
    val down: Vec3 = down.normalized()

    /** Projection of [down] onto the image plane, image coords (y grows downward). Length = sin(angle to optical axis). */
    val downInImage: Point2 = run {
        val theta = rotationDegrees * PI / 180.0
        val viewX = this.down.x * cos(theta) + this.down.y * sin(theta)
        val viewUp = -this.down.x * sin(theta) + this.down.y * cos(theta)
        Point2(viewX.toFloat(), (-viewUp).toFloat())
    }

    /** Elevation of the optical axis (camera looks along device -z); positive = looking up. */
    val pitchDeg: Double get() = asin(this.down.z.coerceIn(-1.0, 1.0)) * 180.0 / PI

    /** Angle of gravity from image-down; positive = gravity leans toward image +x. */
    val rollDeg: Double get() = atan2(downInImage.x.toDouble(), downInImage.y.toDouble()) * 180.0 / PI

    fun classify(line: LineSegment, toleranceDeg: Float = 8f, minProjection: Float = 0.3f): LineOrientation {
        if (downInImage.length < minProjection) return LineOrientation.OTHER
        val gravityAngle = atan2(downInImage.y, downInImage.x)
        var d = abs(line.angleRad - gravityAngle) % PI.toFloat()
        if (d > PI.toFloat() / 2) d = PI.toFloat() - d
        val deg = d * 180f / PI.toFloat()
        return when {
            deg <= toleranceDeg -> LineOrientation.PLUMB
            deg >= 90f - toleranceDeg -> LineOrientation.LEVEL
            else -> LineOrientation.OTHER
        }
    }

    companion object {
        fun quantizeRotation(clockwiseDegrees: Int): Int = ((clockwiseDegrees + 45) / 90 % 4) * 90
    }
}
