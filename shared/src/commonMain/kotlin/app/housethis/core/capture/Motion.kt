package app.housethis.core.capture

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

@Serializable
data class Vec3(val x: Double, val y: Double, val z: Double) {
    val norm: Double get() = sqrt(x * x + y * y + z * z)

    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun normalized(): Vec3 {
        val n = norm
        return if (n == 0.0) this else this * (1.0 / n)
    }

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
    }
}

@Serializable
data class Quaternion(val x: Double, val y: Double, val z: Double, val w: Double)

@Serializable
data class TimedVec3(val timestampNanos: Long, val value: Vec3)

@Serializable
data class Vec3Stats(val mean: Vec3, val stdDev: Vec3, val samples: Int) {
    companion object {
        fun of(values: List<Vec3>): Vec3Stats? {
            if (values.isEmpty()) return null
            val mean = values.fold(Vec3.ZERO) { acc, v -> acc + v } * (1.0 / values.size)
            val variance = values.fold(Vec3.ZERO) { acc, v ->
                val d = v - mean
                acc + Vec3(d.x * d.x, d.y * d.y, d.z * d.z)
            } * (1.0 / values.size)
            return Vec3Stats(mean, Vec3(sqrt(variance.x), sqrt(variance.y), sqrt(variance.z)), values.size)
        }
    }
}

/**
 * Sensor state at shutter time.
 *
 * Device frame (both platforms): x right, y up (top of device in natural portrait), z out of the screen.
 * Accelerometer values are specific force in m/s^2 (reads +9.81 on y when held upright, Android convention);
 * iOS adapters must convert CMAcceleration (g, opposite sign) before storing.
 */
@Serializable
data class MotionStamp(
    val shutterTimestampNanos: Long,
    val windowStartNanos: Long,
    val windowEndNanos: Long,
    val accelerometer: Vec3?,
    val accelerometerWindow: Vec3Stats?,
    val gravity: Vec3?,
    val gyroscopeWindow: Vec3Stats?,
    /** Device-to-world rotation; world = East, North, Up. */
    val rotation: Quaternion?,
    /** Unit vector toward the earth in the device frame. */
    val down: Vec3,
)
