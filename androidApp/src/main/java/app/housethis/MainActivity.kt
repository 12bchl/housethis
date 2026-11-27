package app.housethis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import app.housethis.ui.HouseThisApp

class MainActivity : ComponentActivity() {
    private val app get() = application as HouseThisApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    HouseThisApp(
                        repository = app.repository,
                        motion = app.motion,
                        processor = app.processor,
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.motion.start()
    }

    override fun onPause() {
        app.motion.stop()
        super.onPause()
    }
}
