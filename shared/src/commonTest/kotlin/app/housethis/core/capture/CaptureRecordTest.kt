package app.housethis.core.capture

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CaptureRecordTest {
    @Test
    fun roundTripsThroughJson() {
        val record = CaptureRecord(
            id = "IMG_1",
            imageFile = "IMG_1.jpg",
            capturedAtEpochMs = 1,
            device = DeviceInfo("acme", "phone", "android 15"),
            imageWidth = 4000,
            imageHeight = 3000,
            rotationDegrees = 90,
            motion = MotionStamp(
                shutterTimestampNanos = 10, windowStartNanos = 0, windowEndNanos = 20,
                accelerometer = Vec3(9.8, 0.0, 0.0), accelerometerWindow = null, gravity = null,
                gyroscopeWindow = null, rotation = null, down = Vec3(-1.0, 0.0, 0.0),
            ),
            camera = CameraIntrinsics(focalLengthMm = 4.0, sensorWidthMm = 6.4, sensorHeightMm = 4.8),
        )
        val decoded = HouseThisJson.decodeFromString<CaptureRecord>(HouseThisJson.encodeToString(record))
        assertEquals(record, decoded)
        assertEquals(2500.0, assertNotNull(decoded.focalLengthPx), 1e-9)
    }

    @Test
    fun calibrationTakesPrecedenceOverPhysicalFocalLength() {
        val cam = CameraIntrinsics(
            focalLengthMm = 4.0, sensorWidthMm = 6.4, sensorHeightMm = 4.8,
            activeArrayWidthPx = 4000, activeArrayHeightPx = 3000,
            calibration = listOf(3000.0, 3000.0, 2000.0, 1500.0, 0.0),
        )
        assertEquals(1500.0, assertNotNull(cam.focalLengthPx(2000, 1500)), 1e-9)
    }

    @Test
    fun windowStatsComputeMeanAndStdDev() {
        val stats = assertNotNull(Vec3Stats.of(listOf(Vec3(1.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0))))
        assertEquals(Vec3(2.0, 0.0, 0.0), stats.mean)
        assertEquals(1.0, stats.stdDev.x, 1e-12)
    }
}
