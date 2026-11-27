package app.housethis

import android.app.Application
import app.housethis.capture.CaptureProcessor
import app.housethis.capture.CaptureRepository
import app.housethis.motion.MotionRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HouseThisApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repository: CaptureRepository
        private set
    lateinit var motion: MotionRecorder
        private set
    lateinit var processor: CaptureProcessor
        private set

    override fun onCreate() {
        super.onCreate()
        repository = CaptureRepository(this)
        motion = MotionRecorder(this)
        processor = CaptureProcessor(repository, motion, appScope)
        appScope.launch { repository.refresh() }
    }
}
