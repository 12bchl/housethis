package app.housethis.core.vision

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue

class CornerDetectorTest {
    @Test
    fun findsFourRectangleCorners() {
        val corners = CornerDetector.detect(rectangleImage(), CornerParams(maxCorners = 4))
        val expected = listOf(80f to 60f, 240f to 60f, 80f to 180f, 240f to 180f)
        for ((ex, ey) in expected) {
            val nearest = corners.minOf { hypot(it.x - ex, it.y - ey) }
            assertTrue(nearest < 3f, "no corner near ($ex, $ey); got $corners")
        }
    }

    @Test
    fun flatImageHasNoCorners() {
        assertTrue(CornerDetector.detect(GrayImage(64, 64)).isEmpty())
    }
}
