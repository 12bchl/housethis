package app.housethis.capture

import android.content.Context
import app.housethis.core.capture.CaptureRecord
import app.housethis.core.capture.HouseThisJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Captures live as `<id>.jpg` + `<id>.json` sidecar in app-specific external storage (pull with adb). */
class CaptureRepository(context: Context) {
    val directory: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "captures").apply { mkdirs() }

    private val _records = MutableStateFlow<List<CaptureRecord>>(emptyList())
    val records: StateFlow<List<CaptureRecord>> = _records.asStateFlow()

    fun newCaptureId(): String = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    fun imageFile(id: String) = File(directory, "$id.jpg")

    fun imageFile(record: CaptureRecord) = File(directory, record.imageFile)

    private fun sidecarFile(id: String) = File(directory, "$id.json")

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val loaded = directory.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { file ->
            runCatching { HouseThisJson.decodeFromString<CaptureRecord>(file.readText()) }.getOrNull()
        }
        _records.value = loaded.sortedByDescending { it.capturedAtEpochMs }
    }

    suspend fun save(record: CaptureRecord) = withContext(Dispatchers.IO) {
        val target = sidecarFile(record.id)
        val tmp = File(directory, "${record.id}.json.tmp")
        tmp.writeText(HouseThisJson.encodeToString(record))
        if (!tmp.renameTo(target)) error("Could not write $target")
        _records.update { list -> (list.filterNot { it.id == record.id } + record).sortedByDescending { it.capturedAtEpochMs } }
    }

    suspend fun delete(record: CaptureRecord) = withContext(Dispatchers.IO) {
        imageFile(record).delete()
        sidecarFile(record.id).delete()
        _records.update { list -> list.filterNot { it.id == record.id } }
    }
}
