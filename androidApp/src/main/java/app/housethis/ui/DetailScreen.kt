package app.housethis.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.housethis.capture.Bitmaps
import app.housethis.capture.CaptureProcessor
import app.housethis.capture.CaptureRepository
import app.housethis.core.capture.CaptureRecord
import app.housethis.core.capture.LineOrientation
import app.housethis.core.vision.JunctionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private val PlumbColor = Color(0xFF00E5FF)
private val LevelColor = Color(0xFFFFEB3B)
private val OtherLineColor = Color(0xFFFF4081)
private val CornerColor = Color(0xFF76FF03)
private val LJunctionColor = Color(0xFFFF1744)
private val TJunctionColor = Color(0xFFFF9100)

private data class OverlayToggles(val lines: Boolean = true, val corners: Boolean = false, val junctions: Boolean = true)

@Composable
fun DetailScreen(
    id: String,
    repository: CaptureRepository,
    processor: CaptureProcessor,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val records by repository.records.collectAsStateWithLifecycle()
    val record = records.firstOrNull { it.id == id }
    val scope = rememberCoroutineScope()
    var toggles by remember { mutableStateOf(OverlayToggles()) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(id, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (record != null) {
                TextButton(onClick = { scope.launch { repository.delete(record); onBack() } }) { Text("Delete") }
            }
        }
        if (record == null) {
            Text("Capture not found.", Modifier.padding(16.dp))
            return@Column
        }

        val file = repository.imageFile(record)
        val bitmap by produceState<ImageBitmap?>(null, file) {
            value = withContext(Dispatchers.IO) { Bitmaps.decodeUpright(file, 2048)?.asImageBitmap() }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
            val image = bitmap
            if (image == null) CircularProgressIndicator() else FeatureOverlayImage(image, record, toggles)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(toggles.lines, { toggles = toggles.copy(lines = !toggles.lines) }, { Text("Lines") })
            FilterChip(toggles.junctions, { toggles = toggles.copy(junctions = !toggles.junctions) }, { Text("Junctions") })
            FilterChip(toggles.corners, { toggles = toggles.copy(corners = !toggles.corners) }, { Text("Corners") })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { processor.reanalyze(record) }) { Text("Re-analyze") }
        }
        Text(
            "lines: cyan plumb, yellow level, pink other | junctions: red L, orange T | corners: green",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
            if (record.features == null) {
                Button(onClick = { processor.reanalyze(record) }) { Text("Analyze") }
            }
            Text(describe(record), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FeatureOverlayImage(bitmap: ImageBitmap, record: CaptureRecord, toggles: OverlayToggles) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val attitude = record.attitude

    Canvas(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    zoom = (zoom * zoomChange).coerceIn(1f, 10f)
                    pan = if (zoom == 1f) Offset.Zero else pan + panChange
                }
            }
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                translationX = pan.x
                translationY = pan.y
            },
    ) {
        val scale = min(size.width / record.imageWidth, size.height / record.imageHeight)
        val ox = (size.width - record.imageWidth * scale) / 2
        val oy = (size.height - record.imageHeight * scale) / 2
        drawImage(
            bitmap,
            dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
            dstSize = IntSize((record.imageWidth * scale).roundToInt(), (record.imageHeight * scale).roundToInt()),
        )
        val features = record.features ?: return@Canvas
        fun at(x: Float, y: Float) = Offset(ox + x * scale, oy + y * scale)
        val stroke = 2.dp.toPx() / zoom

        if (toggles.lines) {
            for (line in features.lines) {
                val color = when (attitude?.classify(line)) {
                    LineOrientation.PLUMB -> PlumbColor
                    LineOrientation.LEVEL -> LevelColor
                    else -> OtherLineColor
                }
                drawLine(color, at(line.x1, line.y1), at(line.x2, line.y2), strokeWidth = stroke)
            }
        }
        if (toggles.corners) {
            for (c in features.corners) drawCircle(CornerColor, radius = 2.5f * stroke, center = at(c.x, c.y))
        }
        if (toggles.junctions) {
            for (j in features.junctions) {
                val color = if (j.kind == JunctionKind.L) LJunctionColor else TJunctionColor
                drawCircle(color, radius = 5f * stroke, center = at(j.x, j.y), style = Stroke(stroke))
            }
        }
    }
}

private fun describe(r: CaptureRecord): String = buildString {
    appendLine("image ${r.imageWidth}x${r.imageHeight}, rotation ${r.rotationDegrees} deg")
    r.pitchDeg?.let { appendLine("pitch %+.2f deg, roll %+.2f deg".format(it, r.rollDeg ?: 0.0)) }
    r.motion?.let { m ->
        appendLine("down (device) %.3f, %.3f, %.3f".format(m.down.x, m.down.y, m.down.z))
        m.accelerometerWindow?.let {
            appendLine("accel mean %.2f, %.2f, %.2f m/s2 (n=%d)".format(it.mean.x, it.mean.y, it.mean.z, it.samples))
            appendLine("accel std %.3f, %.3f, %.3f".format(it.stdDev.x, it.stdDev.y, it.stdDev.z))
        }
        m.gyroscopeWindow?.let {
            appendLine("gyro std %.4f, %.4f, %.4f rad/s".format(it.stdDev.x, it.stdDev.y, it.stdDev.z))
        }
    } ?: appendLine("no motion stamp")
    r.focalLengthPx?.let { f ->
        appendLine("focal %.0f px, hfov %.1f deg".format(f, r.camera?.horizontalFovDeg(r.imageWidth, r.imageHeight) ?: 0.0))
    }
    r.features?.let { f ->
        appendLine("${f.lines.size} lines, ${f.junctions.size} junctions, ${f.corners.size} corners (${f.elapsedMs} ms)")
    }
    append("${r.device.manufacturer} ${r.device.model}, ${r.device.os}")
}
