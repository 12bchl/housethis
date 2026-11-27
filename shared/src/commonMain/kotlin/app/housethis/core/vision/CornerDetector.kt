package app.housethis.core.vision

import kotlin.math.floor
import kotlin.math.sqrt

data class CornerParams(
    val maxCorners: Int = 500,
    val qualityLevel: Float = 0.01f,
    val minDistance: Float = 10f,
    val preBlurSigma: Float = 1f,
    val windowSigma: Float = 1.5f,
    val borderMargin: Int = 4,
)

/** Shi-Tomasi corners: local maxima of the structure tensor's smaller eigenvalue. */
object CornerDetector {
    fun detect(image: GrayImage, params: CornerParams = CornerParams()): List<Corner> {
        val w = image.width
        val h = image.height
        val margin = params.borderMargin.coerceAtLeast(1)
        if (w <= 2 * margin || h <= 2 * margin) return emptyList()

        val g = image.gaussianBlur(params.preBlurSigma).sobel()
        val n = w * h
        val sxx = GrayImage(w, h, FloatArray(n) { g.dx[it] * g.dx[it] }).gaussianBlur(params.windowSigma).pixels
        val syy = GrayImage(w, h, FloatArray(n) { g.dy[it] * g.dy[it] }).gaussianBlur(params.windowSigma).pixels
        val sxy = GrayImage(w, h, FloatArray(n) { g.dx[it] * g.dy[it] }).gaussianBlur(params.windowSigma).pixels
        val response = FloatArray(n) { i ->
            val half = (sxx[i] - syy[i]) / 2
            (sxx[i] + syy[i]) / 2 - sqrt(half * half + sxy[i] * sxy[i])
        }

        var maxResponse = 0f
        for (y in margin until h - margin) for (x in margin until w - margin) {
            maxResponse = maxOf(maxResponse, response[y * w + x])
        }
        if (maxResponse <= 0f) return emptyList()
        val threshold = maxResponse * params.qualityLevel

        val candidates = ArrayList<Corner>()
        for (y in margin until h - margin) {
            for (x in margin until w - margin) {
                val i = y * w + x
                val r = response[i]
                if (r < threshold || !isLocalMax(response, w, i, r)) continue
                val ox = subpixelOffset(response[i - 1], r, response[i + 1])
                val oy = subpixelOffset(response[i - w], r, response[i + w])
                candidates += Corner(x + 0.5f + ox, y + 0.5f + oy, r)
            }
        }
        candidates.sortByDescending { it.score }
        return enforceMinDistance(candidates, params.minDistance, params.maxCorners)
    }

    private fun isLocalMax(response: FloatArray, w: Int, i: Int, r: Float): Boolean {
        // Strict on earlier neighbours, non-strict on later ones -> exactly one winner on plateaus.
        return r > response[i - w - 1] && r > response[i - w] && r > response[i - w + 1] && r > response[i - 1] &&
            r >= response[i + 1] && r >= response[i + w - 1] && r >= response[i + w] && r >= response[i + w + 1]
    }

    private fun subpixelOffset(prev: Float, center: Float, next: Float): Float {
        val denom = prev - 2 * center + next
        if (denom >= 0f) return 0f
        return (0.5f * (prev - next) / denom).coerceIn(-0.5f, 0.5f)
    }

    private fun enforceMinDistance(sorted: List<Corner>, minDistance: Float, maxCorners: Int): List<Corner> {
        if (minDistance <= 0f) return sorted.take(maxCorners)
        val cell = minDistance
        val grid = HashMap<Long, MutableList<Corner>>()
        val accepted = ArrayList<Corner>()
        val minDist2 = minDistance * minDistance
        for (c in sorted) {
            if (accepted.size >= maxCorners) break
            val cx = floor(c.x / cell).toInt()
            val cy = floor(c.y / cell).toInt()
            var clear = true
            loop@ for (gy in cy - 1..cy + 1) for (gx in cx - 1..cx + 1) {
                val bucket = grid[cellKey(gx, gy)] ?: continue
                for (o in bucket) {
                    val dx = o.x - c.x
                    val dy = o.y - c.y
                    if (dx * dx + dy * dy < minDist2) {
                        clear = false
                        break@loop
                    }
                }
            }
            if (!clear) continue
            accepted += c
            grid.getOrPut(cellKey(cx, cy)) { ArrayList() } += c
        }
        return accepted
    }

    private fun cellKey(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
}
