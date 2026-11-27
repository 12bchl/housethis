package app.housethis.core.vision

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LineSegmentDetectorTest {
    @Test
    fun findsAllFourRectangleEdges() {
        val lines = LineSegmentDetector.detect(rectangleImage())
        assertTrue(lines.any { isHorizontal(it, 60f, 80f, 240f) }, "top edge missing: $lines")
        assertTrue(lines.any { isHorizontal(it, 180f, 80f, 240f) }, "bottom edge missing: $lines")
        assertTrue(lines.any { isVertical(it, 80f, 60f, 180f) }, "left edge missing: $lines")
        assertTrue(lines.any { isVertical(it, 240f, 60f, 180f) }, "right edge missing: $lines")
    }

    @Test
    fun noiseOnlyImageYieldsNoLines() {
        val lines = LineSegmentDetector.detect(rectangleImage(left = 0, top = 0, right = 0, bottom = 0, noise = 20f))
        assertEquals(emptyList(), lines)
    }

    @Test
    fun nfaRewardsMoreAlignedPoints() {
        val weak = LineSegmentDetector.logNfa(100, 20, 0.125, 10.0)
        val strong = LineSegmentDetector.logNfa(100, 80, 0.125, 10.0)
        assertTrue(weak < 0, "20/100 aligned should not be meaningful, got $weak")
        assertTrue(strong > 0, "80/100 aligned should be meaningful, got $strong")
    }

    private fun isHorizontal(l: LineSegment, y: Float, x0: Float, x1: Float) =
        abs(l.y1 - y) < 1.5f && abs(l.y2 - y) < 1.5f &&
            min(l.x1, l.x2) < x0 + 10 && max(l.x1, l.x2) > x1 - 10

    private fun isVertical(l: LineSegment, x: Float, y0: Float, y1: Float) =
        abs(l.x1 - x) < 1.5f && abs(l.x2 - x) < 1.5f &&
            min(l.y1, l.y2) < y0 + 10 && max(l.y1, l.y2) > y1 - 10
}
