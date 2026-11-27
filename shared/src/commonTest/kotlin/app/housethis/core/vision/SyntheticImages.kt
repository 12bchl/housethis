package app.housethis.core.vision

import kotlin.random.Random

internal fun rectangleImage(
    width: Int = 320,
    height: Int = 240,
    left: Int = 80,
    top: Int = 60,
    right: Int = 240,
    bottom: Int = 180,
    noise: Float = 4f,
    seed: Int = 1,
): GrayImage {
    val rnd = Random(seed)
    val img = GrayImage(width, height)
    for (y in 0 until height) for (x in 0 until width) {
        val inside = x in left until right && y in top until bottom
        img[x, y] = (if (inside) 200f else 50f) + (rnd.nextFloat() - 0.5f) * 2 * noise
    }
    return img
}
