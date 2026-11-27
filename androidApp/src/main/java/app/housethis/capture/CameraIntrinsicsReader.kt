package app.housethis.capture

import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import app.housethis.core.capture.CameraIntrinsics

object CameraIntrinsicsReader {
    // Deprecation points to a CameraInfo.cameraCharacteristics extension that camera-camera2 1.7.0 does not ship.
    @Suppress("DEPRECATION")
    @OptIn(markerClass = [ExperimentalCamera2Interop::class])
    fun read(cameraInfo: CameraInfo): CameraIntrinsics {
        val info = Camera2CameraInfo.from(cameraInfo)
        val physical = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val active = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val distortion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.getCameraCharacteristic(CameraCharacteristics.LENS_DISTORTION)
        } else {
            null
        }
        return CameraIntrinsics(
            focalLengthMm = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull()?.toDouble(),
            sensorWidthMm = physical?.width?.toDouble(),
            sensorHeightMm = physical?.height?.toDouble(),
            activeArrayWidthPx = active?.width(),
            activeArrayHeightPx = active?.height(),
            sensorOrientationDeg = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_ORIENTATION),
            calibration = info.getCameraCharacteristic(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
                ?.map { it.toDouble() },
            distortion = distortion?.map { it.toDouble() },
        )
    }
}
