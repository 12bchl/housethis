package app.housethis.capture

import android.os.Build
import android.os.SystemClock
import androidx.exifinterface.media.ExifInterface
import app.housethis.core.capture.CameraIntrinsics
import app.housethis.core.capture.CaptureRecord
import app.housethis.core.capture.DeviceInfo
import app.housethis.core.capture.ExifStamp
import app.housethis.core.capture.HouseThisCompactJson
import app.housethis.core.vision.FeatureExtractor
import app.housethis.core.vision.GrayImage
import app.housethis.motion.MotionRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/** Turns a saved JPEG into a stamped capture: EXIF motion stamp, JSON sidecar, then line/corner features. */
class CaptureProcessor(
    private val repository: CaptureRepository,
    private val motion: MotionRecorder,
    private val scope: CoroutineScope,
) {
    private val analysisLock = Mutex()
    private val _inFlight = MutableStateFlow(0)
    val inFlight: StateFlow<Int> = _inFlight.asStateFlow()

    private val _errors = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _errors.asStateFlow()

    fun submit(id: String, shutterNanos: Long, rotationDegrees: Int, camera: CameraIntrinsics?) = track {
        val record = stamp(id, shutterNanos, rotationDegrees, camera)
        analyze(record)
    }

    fun reanalyze(record: CaptureRecord) = track { analyze(record) }

    private fun track(block: suspend () -> Unit) {
        _errors.value = null
        _inFlight.update { it + 1 }
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                _errors.value = e.message ?: e.toString()
            } finally {
                _inFlight.update { it - 1 }
            }
        }
    }

    private suspend fun stamp(id: String, shutterNanos: Long, rotationDegrees: Int, camera: CameraIntrinsics?): CaptureRecord {
        val waitMs = (shutterNanos + MotionRecorder.WINDOW_AFTER_NANOS - SystemClock.elapsedRealtimeNanos()) / 1_000_000
        if (waitMs > 0) delay(waitMs)

        val file = repository.imageFile(id)
        val size = withContext(Dispatchers.IO) { Bitmaps.uprightSize(file) } ?: error("Unreadable image $file")
        val sinceShutterMs = (SystemClock.elapsedRealtimeNanos() - shutterNanos) / 1_000_000
        val record = CaptureRecord(
            id = id,
            imageFile = file.name,
            capturedAtEpochMs = System.currentTimeMillis() - sinceShutterMs,
            device = DeviceInfo(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                os = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            ),
            imageWidth = size.width,
            imageHeight = size.height,
            rotationDegrees = rotationDegrees,
            motion = motion.stamp(shutterNanos),
            camera = camera,
        )

        withContext(Dispatchers.IO) {
            val exifStamp = ExifStamp(
                rotationDegrees = record.rotationDegrees,
                motion = record.motion,
                focalLengthPx = record.focalLengthPx,
            )
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_USER_COMMENT, HouseThisCompactJson.encodeToString(exifStamp))
                setAttribute(ExifInterface.TAG_SOFTWARE, "housethis")
                saveAttributes()
            }
        }
        repository.save(record)
        return record
    }

    private suspend fun analyze(record: CaptureRecord) = analysisLock.withLock {
        val bitmap = withContext(Dispatchers.IO) {
            Bitmaps.decodeUpright(repository.imageFile(record), ANALYSIS_MAX_DIMENSION)
        } ?: error("Unreadable image ${record.imageFile}")
        val features = withContext(Dispatchers.Default) {
            val argb = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val gray = GrayImage.fromArgb(bitmap.width, bitmap.height, argb)
            bitmap.recycle()
            FeatureExtractor.extract(gray).scaledTo(record.imageWidth, record.imageHeight)
        }
        repository.save(record.copy(features = features))
    }

    companion object {
        const val ANALYSIS_MAX_DIMENSION = 1280
    }
}
