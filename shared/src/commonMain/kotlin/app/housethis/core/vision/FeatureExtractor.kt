package app.housethis.core.vision

import kotlin.time.TimeSource

data class FeatureParams(
    val lines: LineParams = LineParams(),
    val corners: CornerParams = CornerParams(),
    val junctions: JunctionParams = JunctionParams(),
)

object FeatureExtractor {
    fun extract(image: GrayImage, params: FeatureParams = FeatureParams()): ImageFeatures {
        val start = TimeSource.Monotonic.markNow()
        val lines = LineSegmentDetector.detect(image, params.lines).sortedByDescending { it.length }
        val corners = CornerDetector.detect(image, params.corners)
        val junctions = JunctionDetector.detect(lines, image.width, image.height, params.junctions)
        return ImageFeatures(
            width = image.width,
            height = image.height,
            lines = lines,
            corners = corners,
            junctions = junctions,
            elapsedMs = start.elapsedNow().inWholeMilliseconds,
        )
    }
}
