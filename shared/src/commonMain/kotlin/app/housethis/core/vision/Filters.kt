package app.housethis.core.vision

import kotlin.math.ceil
import kotlin.math.exp

class Gradient(val width: Int, val height: Int, val dx: FloatArray, val dy: FloatArray)

fun GrayImage.gaussianBlur(sigma: Float): GrayImage {
    if (sigma <= 0f) return GrayImage(width, height, pixels.copyOf())
    val kernel = gaussianKernel(sigma)
    val r = kernel.size / 2
    val horizontal = FloatArray(pixels.size)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            var acc = 0f
            for (k in -r..r) acc += kernel[k + r] * pixels[row + (x + k).coerceIn(0, width - 1)]
            horizontal[row + x] = acc
        }
    }
    val out = FloatArray(pixels.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var acc = 0f
            for (k in -r..r) acc += kernel[k + r] * horizontal[(y + k).coerceIn(0, height - 1) * width + x]
            out[y * width + x] = acc
        }
    }
    return GrayImage(width, height, out)
}

fun GrayImage.sobel(): Gradient {
    val dx = FloatArray(pixels.size)
    val dy = FloatArray(pixels.size)
    for (y in 0 until height) {
        val up = (y - 1).coerceAtLeast(0) * width
        val mid = y * width
        val down = (y + 1).coerceAtMost(height - 1) * width
        for (x in 0 until width) {
            val l = (x - 1).coerceAtLeast(0)
            val r = (x + 1).coerceAtMost(width - 1)
            dx[mid + x] = (pixels[up + r] + 2 * pixels[mid + r] + pixels[down + r]) -
                (pixels[up + l] + 2 * pixels[mid + l] + pixels[down + l])
            dy[mid + x] = (pixels[down + l] + 2 * pixels[down + x] + pixels[down + r]) -
                (pixels[up + l] + 2 * pixels[up + x] + pixels[up + r])
        }
    }
    return Gradient(width, height, dx, dy)
}

internal fun gaussianKernel(sigma: Float): FloatArray {
    val r = ceil(3 * sigma).toInt().coerceAtLeast(1)
    val kernel = FloatArray(2 * r + 1) { i ->
        val d = (i - r).toFloat()
        exp(-d * d / (2 * sigma * sigma))
    }
    val sum = kernel.sum()
    for (i in kernel.indices) kernel[i] /= sum
    return kernel
}
