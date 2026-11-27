package app.housethis.core.vision

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class JunctionParams(
    val minSegmentLength: Float = 20f,
    val minAngleDeg: Float = 25f,
    val maxGapFraction: Float = 0.01f,
    val minGap: Float = 6f,
    val maxSegments: Int = 400,
    val includeCrossings: Boolean = false,
)

/** Structural corners: places where two non-parallel segments meet (L), abut (T) or cross (X). */
object JunctionDetector {
    fun detect(lines: List<LineSegment>, imageWidth: Int, imageHeight: Int, params: JunctionParams = JunctionParams()): List<Junction> {
        val gap = max(params.minGap, params.maxGapFraction * max(imageWidth, imageHeight))
        val candidates = lines.withIndex()
            .filter { it.value.length >= params.minSegmentLength }
            .sortedByDescending { it.value.length }
            .take(params.maxSegments)
        val out = ArrayList<Junction>()
        for (a in candidates.indices) {
            for (b in a + 1 until candidates.size) {
                val (ia, la) = candidates[a]
                val (ib, lb) = candidates[b]
                junction(la, lb, gap, params)?.let { (x, y, kind, angle) ->
                    if (x >= 0 && y >= 0 && x <= imageWidth && y <= imageHeight) {
                        out += Junction(x, y, min(ia, ib), max(ia, ib), kind, angle)
                    }
                }
            }
        }
        return out
    }

    private data class Hit(val x: Float, val y: Float, val kind: JunctionKind, val angleDeg: Float)

    private fun junction(a: LineSegment, b: LineSegment, gap: Float, params: JunctionParams): Hit? {
        val angle = acuteAngleDeg(a, b)
        if (angle < params.minAngleDeg) return null

        val adx = a.x2 - a.x1
        val ady = a.y2 - a.y1
        val bdx = b.x2 - b.x1
        val bdy = b.y2 - b.y1
        val denom = adx * bdy - ady * bdx
        if (abs(denom) < 1e-6f) return null
        val ta = ((b.x1 - a.x1) * bdy - (b.y1 - a.y1) * bdx) / denom
        val tb = ((b.x1 - a.x1) * ady - (b.y1 - a.y1) * adx) / denom

        val lenA = a.length
        val lenB = b.length
        val outsideA = outsideDistance(ta * lenA, lenA)
        val outsideB = outsideDistance(tb * lenB, lenB)
        if (outsideA > gap || outsideB > gap) return null

        val atEndA = endDistance(ta * lenA, lenA) <= gap
        val atEndB = endDistance(tb * lenB, lenB) <= gap
        val kind = when {
            atEndA && atEndB -> JunctionKind.L
            atEndA || atEndB -> JunctionKind.T
            else -> JunctionKind.X
        }
        if (kind == JunctionKind.X && !params.includeCrossings) return null
        return Hit(a.x1 + ta * adx, a.y1 + ta * ady, kind, angle)
    }

    private fun outsideDistance(along: Float, length: Float) = when {
        along < 0 -> -along
        along > length -> along - length
        else -> 0f
    }

    private fun endDistance(along: Float, length: Float) = min(abs(along), abs(length - along))

    internal fun acuteAngleDeg(a: LineSegment, b: LineSegment): Float {
        var d = abs(a.angleRad - b.angleRad) % PI.toFloat()
        if (d > PI.toFloat() / 2) d = PI.toFloat() - d
        return d * 180f / PI.toFloat()
    }
}
