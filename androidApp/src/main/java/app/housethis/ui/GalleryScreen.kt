package app.housethis.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.housethis.capture.Bitmaps
import app.housethis.capture.CaptureRepository
import app.housethis.core.capture.CaptureRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun GalleryScreen(
    repository: CaptureRepository,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val records by repository.records.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Camera") }
            Text("${records.size} captures", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            repository.directory.absolutePath,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(records, key = { it.id }) { record ->
                Thumbnail(repository.imageFile(record), record, onClick = { onOpen(record.id) })
            }
        }
    }
}

@Composable
private fun Thumbnail(file: File, record: CaptureRecord, onClick: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { Bitmaps.decodeUpright(file, 320)?.asImageBitmap() }
    }
    Box(Modifier.aspectRatio(1f).background(Color.DarkGray).clickable(onClick = onClick)) {
        bitmap?.let { Image(it, contentDescription = record.id, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        val f = record.features
        Text(
            if (f == null) "analyzing" else "${f.lines.size} lines / ${f.junctions.size} jn",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).background(Color(0x99000000)).padding(4.dp),
        )
    }
}
