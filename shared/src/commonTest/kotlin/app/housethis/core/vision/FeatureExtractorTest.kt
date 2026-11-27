package app.housethis.core.vision

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureExtractorTest {
    @Test
    fun rectangleCornersAppearAsLJunctions() {
        val features = FeatureExtractor.extract(rectangleImage())
        val expected = listOf(80f to 60f, 240f to 60f, 80f to 180f, 240f to 180f)
        for ((ex, ey) in expected) {
            val hit = features.junctions.any { it.kind == JunctionKind.L && hypot(it.x - ex, it.y - ey) < 3f }
            assertTrue(hit, "no L junction near ($ex, $ey); got ${features.junctions}")
        }
    }

    @Test
    fun scalingMapsCoordinatesToFullResolution() {
        val features = FeatureExtractor.extract(rectangleImage())
        val scaled = features.scaledTo(features.width * 4, features.height * 4)
        assertEquals(features.lines.first().x1 * 4, scaled.lines.first().x1)
        assertEquals(features.corners.first().y * 4, scaled.corners.first().y)
    }
}
