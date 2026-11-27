package app.housethis.core.capture

import app.housethis.core.vision.ImageFeatures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max

@Serializable
data class DeviceInfo(val manufacturer: String, val model: String, val os: String)

@Serializable
data class CameraIntrinsics(
    val focalLengthMm: Double? = null,
    val sensorWidthMm: Double? = null,
    val sensorHeightMm: Double? = null,
    val activeArrayWidthPx: Int? = null,
    val activeArrayHeightPx: Int? = null,
    val sensorOrientationDeg: Int? = null,
    /** Platform calibration [fx, fy, cx, cy, skew] in active-array pixels, if the device reports it. */
    val calibration: List<Double>? = null,
    val distortion: List<Double>? = null,
) {
    /** Focal length in pixels of an upright image of the given size; assumes it spans the sensor's long side. */
    fun focalLengthPx(imageWidth: Int, imageHeight: Int): Double? {
        val imageLong = max(imageWidth, imageHeight).toDouble()
        val arrayLong = max(activeArrayWidthPx ?: 0, activeArrayHeightPx ?: 0)
        calibration?.firstOrNull()?.takeIf { it > 0 && arrayLong > 0 }?.let { fx ->
            return fx * imageLong / arrayLong
        }
        val f = focalLengthMm ?: return null
        val sensorLong = max(sensorWidthMm ?: 0.0, sensorHeightMm ?: 0.0)
        if (sensorLong <= 0) return null
        return f * imageLong / sensorLong
    }

    fun horizontalFovDeg(imageWidth: Int, imageHeight: Int): Double? {
        val fpx = focalLengthPx(imageWidth, imageHeight) ?: return null
        return 2 * kotlin.math.atan(imageWidth / (2 * fpx)) * 180 / kotlin.math.PI
    }
}

@Serializable
data class CaptureRecord(
    val schemaVersion: Int = SCHEMA_VERSION,
    val id: String,
    val imageFile: String,
    val capturedAtEpochMs: Long,
    val device: DeviceInfo,
    /** Upright image size (after applying EXIF orientation). Feature coordinates use this frame. */
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int,
    val motion: MotionStamp?,
    val camera: CameraIntrinsics?,
    val focalLengthPx: Double? = camera?.focalLengthPx(imageWidth, imageHeight),
    val pitchDeg: Double? = motion?.let { Attitude(it.down, rotationDegrees).pitchDeg },
    val rollDeg: Double? = motion?.let { Attitude(it.down, rotationDegrees).rollDeg },
    val features: ImageFeatures? = null,
) {
    val attitude: Attitude? get() = motion?.let { Attitude(it.down, rotationDegrees) }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/** Compact subset written into the JPEG's EXIF UserComment so the image stays self-describing. */
@Serializable
data class ExifStamp(
    val app: String = "housethis",
    val schemaVersion: Int = CaptureRecord.SCHEMA_VERSION,
    val rotationDegrees: Int,
    val motion: MotionStamp?,
    val focalLengthPx: Double?,
)

val HouseThisJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

val HouseThisCompactJson = Json(HouseThisJson) { prettyPrint = false }
