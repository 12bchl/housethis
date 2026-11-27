package app.housethis.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.housethis.core.capture.MotionStamp
import app.housethis.core.capture.Quaternion
import app.housethis.core.capture.Vec3
import app.housethis.core.capture.Vec3Stats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps a short history of motion sensors so a capture can be stamped with readings around the shutter.
 * Timestamps are SensorEvent.timestamp, which shares the SystemClock.elapsedRealtimeNanos() base on API 26+ devices.
 */
class MotionRecorder(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val accel = SampleHistory()
    private val gravity = SampleHistory()
    private val gyro = SampleHistory()
    private val rotation = SampleHistory()

    private var smoothedAccel: FloatArray? = null
    private val _down = MutableStateFlow<Vec3?>(null)

    /** Live unit vector toward the earth in the device frame, for UI. */
    val down: StateFlow<Vec3?> = _down.asStateFlow()

    fun start() {
        listOfNotNull(accelerometer, gravitySensor, gyroscope, rotationSensor).forEach {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val t = event.timestamp
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val v = event.values.copyOf(3)
                accel.add(t, v)
                if (gravitySensor == null) publishDown(lowPass(v))
            }
            Sensor.TYPE_GRAVITY -> {
                val v = event.values.copyOf(3)
                gravity.add(t, v)
                publishDown(v)
            }
            Sensor.TYPE_GYROSCOPE -> gyro.add(t, event.values.copyOf(3))
            Sensor.TYPE_ROTATION_VECTOR -> {
                val wxyz = FloatArray(4)
                SensorManager.getQuaternionFromVector(wxyz, event.values)
                rotation.add(t, wxyz)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    fun stamp(shutterNanos: Long): MotionStamp? {
        val start = shutterNanos - WINDOW_BEFORE_NANOS
        val end = shutterNanos + WINDOW_AFTER_NANOS
        val accelWindow = Vec3Stats.of(accel.between(start, end).map { it.toVec3() })
        val accelNow = accel.nearest(shutterNanos)?.toVec3()
        val gravityNow = gravity.nearest(shutterNanos)?.toVec3()
        val specificForce = gravityNow ?: accelWindow?.mean ?: accelNow ?: return null
        return MotionStamp(
            shutterTimestampNanos = shutterNanos,
            windowStartNanos = start,
            windowEndNanos = end,
            accelerometer = accelNow,
            accelerometerWindow = accelWindow,
            gravity = gravityNow,
            gyroscopeWindow = Vec3Stats.of(gyro.between(start, end).map { it.toVec3() }),
            rotation = rotation.nearest(shutterNanos)?.let { Quaternion(it[1].toDouble(), it[2].toDouble(), it[3].toDouble(), it[0].toDouble()) },
            down = (-specificForce).normalized(),
        )
    }

    private fun lowPass(v: FloatArray): FloatArray {
        val prev = smoothedAccel ?: v.copyOf()
        for (i in 0..2) prev[i] += LOW_PASS_ALPHA * (v[i] - prev[i])
        smoothedAccel = prev
        return prev
    }

    private fun publishDown(specificForce: FloatArray) {
        _down.value = (-specificForce.toVec3()).normalized()
    }

    private fun FloatArray.toVec3() = Vec3(this[0].toDouble(), this[1].toDouble(), this[2].toDouble())

    companion object {
        const val WINDOW_BEFORE_NANOS = 200_000_000L
        const val WINDOW_AFTER_NANOS = 100_000_000L
        private const val LOW_PASS_ALPHA = 0.1f
    }
}

private class SampleHistory(private val keepNanos: Long = 3_000_000_000L) {
    private val times = ArrayDeque<Long>()
    private val values = ArrayDeque<FloatArray>()

    @Synchronized
    fun add(t: Long, v: FloatArray) {
        times.addLast(t)
        values.addLast(v)
        while (times.first() < t - keepNanos) {
            times.removeFirst()
            values.removeFirst()
        }
    }

    @Synchronized
    fun nearest(t: Long): FloatArray? {
        var best = -1
        var bestDistance = Long.MAX_VALUE
        for (i in times.indices) {
            val d = kotlin.math.abs(times[i] - t)
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return if (best >= 0) values[best] else null
    }

    @Synchronized
    fun between(start: Long, end: Long): List<FloatArray> =
        times.indices.filter { times[it] in start..end }.map { values[it] }
}
