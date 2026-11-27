package app.housethis.core.vision

class GrayImage(val width: Int, val height: Int, val pixels: FloatArray = FloatArray(width * height)) {
    init {
        require(width > 0 && height > 0) { "Image must be non-empty, got ${width}x$height" }
        require(pixels.size == width * height) { "Pixel count ${pixels.size} != ${width}x$height" }
    }

    operator fun get(x: Int, y: Int): Float = pixels[y * width + x]

    operator fun set(x: Int, y: Int, value: Float) {
        pixels[y * width + x] = value
    }

    companion object {
        fun fromArgb(width: Int, height: Int, argb: IntArray): GrayImage {
            require(argb.size == width * height) { "Pixel count ${argb.size} != ${width}x$height" }
            val luma = FloatArray(argb.size) { i ->
                val c = argb[i]
                0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
            }
            return GrayImage(width, height, luma)
        }
    }
}
