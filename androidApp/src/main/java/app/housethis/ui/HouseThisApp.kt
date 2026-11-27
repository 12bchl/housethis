package app.housethis.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.housethis.capture.CaptureProcessor
import app.housethis.capture.CaptureRepository
import app.housethis.motion.MotionRecorder

private sealed interface Screen {
    data object Camera : Screen
    data object Gallery : Screen
    data class Detail(val id: String) : Screen
}

@Composable
fun HouseThisApp(
    repository: CaptureRepository,
    motion: MotionRecorder,
    processor: CaptureProcessor,
    modifier: Modifier = Modifier,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Camera) }
    BackHandler(enabled = screen != Screen.Camera) {
        screen = if (screen is Screen.Detail) Screen.Gallery else Screen.Camera
    }
    when (val s = screen) {
        Screen.Camera -> CameraScreen(
            repository = repository,
            motion = motion,
            processor = processor,
            onOpenGallery = { screen = Screen.Gallery },
            modifier = modifier,
        )
        Screen.Gallery -> GalleryScreen(
            repository = repository,
            onOpen = { screen = Screen.Detail(it) },
            onBack = { screen = Screen.Camera },
            modifier = modifier,
        )
        is Screen.Detail -> DetailScreen(
            id = s.id,
            repository = repository,
            processor = processor,
            onBack = { screen = Screen.Gallery },
            modifier = modifier,
        )
    }
}
