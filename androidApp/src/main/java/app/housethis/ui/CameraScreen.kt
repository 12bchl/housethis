package app.housethis.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.housethis.capture.CameraIntrinsicsReader
import app.housethis.capture.CaptureProcessor
import app.housethis.capture.CaptureRepository
import app.housethis.core.capture.Attitude
import app.housethis.core.capture.CameraIntrinsics
import app.housethis.core.capture.Vec3
import app.housethis.motion.MotionRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

@Composable
fun CameraScreen(
    repository: CaptureRepository,
    motion: MotionRecorder,
    processor: CaptureProcessor,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (granted) {
        CameraContent(repository, motion, processor, onOpenGallery, modifier)
    } else {
        Column(
            modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Camera permission is needed to capture photos.")
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, Modifier.padding(top = 16.dp)) {
                Text("Grant camera access")
            }
            TextButton(onClick = onOpenGallery) { Text("Open captures") }
        }
    }
}

@Composable
private fun CameraContent(
    repository: CaptureRepository,
    motion: MotionRecorder,
    processor: CaptureProcessor,
    onOpenGallery: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    var intrinsics by remember { mutableStateOf<CameraIntrinsics?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lifecycleOwner) {
        var provider: ProcessCameraProvider? = null
        try {
            provider = awaitCameraProvider(context)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            intrinsics = CameraIntrinsicsReader.read(camera.cameraInfo)
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = "Camera unavailable: ${e.message}"
        } finally {
            provider?.unbindAll()
        }
    }

    val rotationDegrees by rememberDeviceRotation()
    LaunchedEffect(rotationDegrees) { imageCapture.targetRotation = surfaceRotation(rotationDegrees) }

    val down by motion.down.collectAsStateWithLifecycle()
    val inFlight by processor.inFlight.collectAsStateWithLifecycle()
    val lastError by processor.lastError.collectAsStateWithLifecycle()

    fun capture() {
        val id = repository.newCaptureId()
        val shutterNanos = SystemClock.elapsedRealtimeNanos()
        val rotation = rotationDegrees
        val camera = intrinsics
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(repository.imageFile(id)).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    status = "Saved $id"
                    processor.submit(id, shutterNanos, rotation, camera)
                }

                override fun onError(exception: ImageCaptureException) {
                    status = "Capture failed: ${exception.message}"
                }
            },
        )
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        LevelOverlay(down, rotationDegrees, screenRotationDegrees(), Modifier.fillMaxSize())
        AttitudeReadout(down, rotationDegrees, Modifier.align(Alignment.TopCenter).padding(12.dp))

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000)).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val message = lastError?.let { "Error: $it" } ?: status
            if (message != null) Text(message, style = MaterialTheme.typography.bodySmall, color = Color.White)
            if (inFlight > 0) Text("Analyzing $inFlight...", style = MaterialTheme.typography.bodySmall, color = Color.White)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenGallery) { Text("Captures") }
                Box(
                    Modifier.size(72.dp).clickable(onClick = ::capture).border(4.dp, Color.White, CircleShape).padding(8.dp)
                        .background(Color.White, CircleShape),
                )
                Text("rot ${rotationDegrees}", color = Color.White, modifier = Modifier.padding(end = 12.dp))
            }
        }
    }
}

@Composable
private fun AttitudeReadout(down: Vec3?, rotationDegrees: Int, modifier: Modifier) {
    val text = down?.let {
        val a = Attitude(it, rotationDegrees)
        "pitch %+.1f   roll %+.1f".format(a.pitchDeg, a.rollDeg)
    } ?: "no motion data"
    Text(text, color = Color.White, modifier = modifier.background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp))
}

/** Horizon line in screen space; green when the photo's roll is within 1 degree of level. */
@Composable
private fun LevelOverlay(down: Vec3?, rotationDegrees: Int, screenRotationDegrees: Int, modifier: Modifier) {
    Canvas(modifier) {
        val d = down ?: return@Canvas
        val screenDown = Attitude(d, screenRotationDegrees).downInImage
        if (screenDown.length < 0.3f) return@Canvas
        val nx = screenDown.x / screenDown.length
        val ny = screenDown.y / screenDown.length
        val level = abs(Attitude(d, rotationDegrees).rollDeg) < 1.0
        val half = size.minDimension * 0.35f
        val c = center
        drawLine(
            color = if (level) Color.Green else Color.White,
            start = Offset(c.x - ny * half, c.y + nx * half),
            end = Offset(c.x + ny * half, c.y - nx * half),
            strokeWidth = 3.dp.toPx(),
        )
        drawCircle(Color.White, radius = 4.dp.toPx(), center = c)
    }
}

/** Clockwise rotation of the UI from the device's natural orientation; non-zero only where the portrait lock is ignored. */
@Composable
private fun screenRotationDegrees(): Int {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    return remember(configuration) { (360 - (view.display?.rotation ?: Surface.ROTATION_0) * 90) % 360 }
}

@Composable
private fun rememberDeviceRotation(): State<Int> {
    val context = LocalContext.current
    val state = remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation != ORIENTATION_UNKNOWN) state.intValue = Attitude.quantizeRotation(orientation)
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
    return state
}

private fun surfaceRotation(clockwiseDegrees: Int) = when (clockwiseDegrees) {
    90 -> Surface.ROTATION_270
    180 -> Surface.ROTATION_180
    270 -> Surface.ROTATION_90
    else -> Surface.ROTATION_0
}

private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener(
        {
            try {
                cont.resume(future.get())
            } catch (e: Exception) {
                cont.resumeWithException(e.cause ?: e)
            }
        },
        ContextCompat.getMainExecutor(context),
    )
}
