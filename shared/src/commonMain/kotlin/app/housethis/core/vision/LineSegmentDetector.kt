package app.housethis.core.vision

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LineParams(
    val blurSigma: Float = 0.6f,
    val quantizationError: Float = 2f,
    val angleToleranceDeg: Float = 22.5f,
    val logEpsilon: Double = 0.0,
    val densityThreshold: Float = 0.7f,
    val minLengthFraction: Float = 0.02f,
)

/**
 * Line Segment Detector after von Gioi et al., "LSD: a Line Segment Detector", IPOL 2012.
 * Simplified: no rectangle-improvement step; low-density regions are refined by radius reduction only.
 */
object LineSegmentDetector {
    private const val NOT_DEFINED = -1024f
    private const val ORDER_BINS = 1024
    // LSD tests 11 precision values when improving rectangles; kept so NFA thresholds match the paper.
    private const val TESTED_PRECISIONS = 11.0

    fun detect(image: GrayImage, params: LineParams = LineParams()): List<LineSegment> =
        Run(image.gaussianBlur(params.blurSigma), params).detect()

    private class Rect(
        val x1: Double, val y1: Double, val x2: Double, val y2: Double,
        val width: Double, val theta: Double,
    ) {
        val dx = cos(theta)
        val dy = sin(theta)
        val length = sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
    }

    private class Run(private val img: GrayImage, private val params: LineParams) {
        private val w = img.width
        private val h = img.height
        private val prec = PI * params.angleToleranceDeg / 180.0
        private val p = params.angleToleranceDeg / 180.0
        private val angles = FloatArray(w * h) { NOT_DEFINED }
        private val magnitudes = FloatArray(w * h)
        private val used = BooleanArray(w * h)
        private val logNT = 5.0 * (log10(w.toDouble()) + log10(h.toDouble())) / 2.0 + log10(TESTED_PRECISIONS)
        private val minRegionSize = (-logNT / log10(p)).toInt()
        private val minLength = params.minLengthFraction * max(w, h)

        fun detect(): List<LineSegment> {
            if (w < 2 || h < 2) return emptyList()
            computeGradient()
            val out = ArrayList<LineSegment>()
            val region = IntList()
            for (seed in pseudoOrderedPixels()) {
                if (used[seed] || angles[seed] == NOT_DEFINED) continue
                val regionAngle = growRegion(seed, region)
                if (region.size < minRegionSize) continue
                val rect = refine(region, regionAngle) ?: continue
                val logNfa = rectLogNfa(rect)
                if (logNfa <= params.logEpsilon || rect.length < minLength) continue
                out += LineSegment(
                    (rect.x1 + 1).toFloat(), (rect.y1 + 1).toFloat(),
                    (rect.x2 + 1).toFloat(), (rect.y2 + 1).toFloat(),
                    rect.width.toFloat(), logNfa.toFloat(),
                )
            }
            return out
        }

        // 2x2 gradient at (x, y) is centred on the shared corner of pixels (x..x+1, y..y+1), i.e. (x+1, y+1) in image coords.
        private fun computeGradient() {
            val px = img.pixels
            val threshold = params.quantizationError / sin(prec)
            for (y in 0 until h - 1) {
                for (x in 0 until w - 1) {
                    val i = y * w + x
                    val com1 = px[i + w + 1] - px[i]
                    val com2 = px[i + 1] - px[i + w]
                    val gx = com1 + com2
                    val gy = com1 - com2
                    val mag = sqrt((gx * gx + gy * gy) / 4f)
                    magnitudes[i] = mag
                    if (mag > threshold) angles[i] = atan2(gx, -gy)
                }
            }
        }

        private fun pseudoOrderedPixels(): IntArray {
            var maxMag = 0f
            var count = 0
            for (i in angles.indices) if (angles[i] != NOT_DEFINED) {
                count++
                maxMag = max(maxMag, magnitudes[i])
            }
            if (count == 0) return IntArray(0)
            val binOf = { i: Int -> ((magnitudes[i] / maxMag) * (ORDER_BINS - 1)).toInt() }
            val starts = IntArray(ORDER_BINS + 1)
            for (i in angles.indices) if (angles[i] != NOT_DEFINED) starts[ORDER_BINS - 1 - binOf(i) + 1]++
            for (b in 1..ORDER_BINS) starts[b] += starts[b - 1]
            val order = IntArray(count)
            for (i in angles.indices) if (angles[i] != NOT_DEFINED) order[starts[ORDER_BINS - 1 - binOf(i)]++] = i
            return order
        }

        private fun growRegion(seed: Int, region: IntList): Double {
            region.clear()
            region.add(seed)
            used[seed] = true
            var regionAngle = angles[seed].toDouble()
            var sumCos = cos(regionAngle)
            var sumSin = sin(regionAngle)
            var k = 0
            while (k < region.size) {
                val idx = region[k++]
                val cx = idx % w
                val cy = idx / w
                for (ny in max(cy - 1, 0)..min(cy + 1, h - 1)) {
                    for (nx in max(cx - 1, 0)..min(cx + 1, w - 1)) {
                        val n = ny * w + nx
                        if (used[n] || !isAligned(angles[n], regionAngle)) continue
                        used[n] = true
                        region.add(n)
                        sumCos += cos(angles[n].toDouble())
                        sumSin += sin(angles[n].toDouble())
                        regionAngle = atan2(sumSin, sumCos)
                    }
                }
            }
            return regionAngle
        }

        private fun isAligned(angle: Float, theta: Double): Boolean {
            if (angle == NOT_DEFINED) return false
            var d = abs(theta - angle)
            if (d > PI) d = 2 * PI - d
            return d <= prec
        }

        private fun regionToRect(region: IntList, regionAngle: Double): Rect {
            var sum = 0.0
            var cx = 0.0
            var cy = 0.0
            for (k in 0 until region.size) {
                val i = region[k]
                val m = magnitudes[i].toDouble()
                cx += (i % w) * m
                cy += (i / w) * m
                sum += m
            }
            cx /= sum
            cy /= sum

            var ixx = 0.0
            var iyy = 0.0
            var ixy = 0.0
            for (k in 0 until region.size) {
                val i = region[k]
                val m = magnitudes[i].toDouble()
                val dx = (i % w) - cx
                val dy = (i / w) - cy
                ixx += m * dy * dy
                iyy += m * dx * dx
                ixy -= m * dx * dy
            }
            val lambda = 0.5 * (ixx + iyy - sqrt((ixx - iyy) * (ixx - iyy) + 4.0 * ixy * ixy))
            var theta = if (abs(ixx) > abs(iyy)) atan2(lambda - ixx, ixy) else atan2(ixy, lambda - iyy)
            if (angleDistance(theta, regionAngle) > prec) theta += PI

            val dx = cos(theta)
            val dy = sin(theta)
            var lMin = 0.0
            var lMax = 0.0
            var wMin = 0.0
            var wMax = 0.0
            for (k in 0 until region.size) {
                val i = region[k]
                val ox = (i % w) - cx
                val oy = (i / w) - cy
                val l = ox * dx + oy * dy
                val t = -ox * dy + oy * dx
                lMin = min(lMin, l); lMax = max(lMax, l)
                wMin = min(wMin, t); wMax = max(wMax, t)
            }
            return Rect(
                cx + lMin * dx, cy + lMin * dy,
                cx + lMax * dx, cy + lMax * dy,
                max(wMax - wMin, 1.0), theta,
            )
        }

        private fun refine(region: IntList, regionAngle: Double): Rect? {
            var rect = regionToRect(region, regionAngle)
            if (density(region, rect) >= params.densityThreshold) return rect

            val seed = region[0]
            val sx = (seed % w).toDouble()
            val sy = (seed / w).toDouble()
            var radius = max(hypot(sx - rect.x1, sy - rect.y1), hypot(sx - rect.x2, sy - rect.y2))
            while (density(region, rect) < params.densityThreshold) {
                radius *= 0.75
                region.retainAll { i ->
                    val keep = hypot((i % w) - sx, (i / w) - sy) <= radius
                    if (!keep) used[i] = false
                    keep
                }
                if (region.size < 2) return null
                rect = regionToRect(region, regionAngle)
            }
            return rect
        }

        private fun density(region: IntList, rect: Rect): Double = region.size / (max(rect.length, 1.0) * rect.width)

        private fun rectLogNfa(rect: Rect): Double {
            val half = rect.width / 2
            val x0 = max(floor(min(rect.x1, rect.x2) - half).toInt(), 0)
            val x1 = min(ceil(max(rect.x1, rect.x2) + half).toInt(), w - 1)
            val y0 = max(floor(min(rect.y1, rect.y2) - half).toInt(), 0)
            val y1 = min(ceil(max(rect.y1, rect.y2) + half).toInt(), h - 1)
            var n = 0
            var k = 0
            for (y in y0..y1) {
                for (x in x0..x1) {
                    val vx = x - rect.x1
                    val vy = y - rect.y1
                    val along = vx * rect.dx + vy * rect.dy
                    val across = -vx * rect.dy + vy * rect.dx
                    if (along < -0.5 || along > rect.length + 0.5 || abs(across) > half) continue
                    n++
                    if (isAligned(angles[y * w + x], rect.theta)) k++
                }
            }
            return logNfa(n, k, p, logNT)
        }
    }

    private fun angleDistance(a: Double, b: Double): Double {
        var d = abs(a - b) % (2 * PI)
        if (d > PI) d = 2 * PI - d
        return d
    }

    private fun hypot(x: Double, y: Double) = sqrt(x * x + y * y)

    /** -log10(NFA) for k aligned points out of n with alignment probability p; > 0 means meaningful. */
    internal fun logNfa(n: Int, k: Int, p: Double, logNT: Double): Double {
        if (n == 0 || k == 0) return -logNT
        if (n == k) return -logNT - n * log10(p)
        if (k <= n * p) return -logNT
        val logFirstTerm = lnGamma(n + 1.0) - lnGamma(k + 1.0) - lnGamma(n - k + 1.0) +
            k * ln(p) + (n - k) * ln(1 - p)
        val ratio = p / (1 - p)
        var term = 1.0
        var sum = 1.0
        for (i in k until n) {
            term *= (n - i).toDouble() / (i + 1) * ratio
            sum += term
            if (term < sum * 1e-12) break
        }
        val log10Tail = (logFirstTerm + ln(sum)) / ln(10.0)
        return -logNT - log10Tail
    }

    private val lanczos = doubleArrayOf(
        75122.6331530, 80916.6278952, 36308.2951477, 8687.24529705,
        1168.92649479, 83.8676043424, 2.50662827511,
    )

    internal fun lnGamma(x: Double): Double {
        var a = (x + 0.5) * ln(x + 5.5) - (x + 5.5)
        var b = 0.0
        for (n in lanczos.indices) {
            a -= ln(x + n)
            b += lanczos[n] * x.pow(n)
        }
        return a + ln(b)
    }
}

internal class IntList {
    private var data = IntArray(64)
    var size = 0
        private set

    operator fun get(i: Int) = data[i]

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    fun clear() {
        size = 0
    }

    fun retainAll(predicate: (Int) -> Boolean) {
        var out = 0
        for (i in 0 until size) if (predicate(data[i])) data[out++] = data[i]
        size = out
    }
}
