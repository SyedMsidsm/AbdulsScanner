package com.syedmsidsm.abdulsscanner.domain.imaging

object AdaptiveThreshold {

    /**
     * Bradley-Roth adaptive thresholding using an integral image.
     * @param grayPixels Grayscale luminance values (0..255) for each pixel.
     * @param width Image width.
     * @param height Image height.
     * @param t Percentage threshold, typically 0.15f (15%).
     * @return ARGB IntArray where black is 0xFF000000 and white is 0xFFFFFFFF.
     */
    fun binarize(grayPixels: IntArray, width: Int, height: Int, t: Float = 0.15f): IntArray {
        val count = width * height
        val output = IntArray(count)

        // Calculate window size: odd number, max(15, width / 12)
        var s = maxOf(15, width / 12)
        if (s % 2 == 0) s += 1
        val s2 = s / 2

        // Build integral image (LongArray to avoid overflow for large images)
        val integral = LongArray(width * height)
        for (y in 0 until height) {
            var sum = 0L
            val rowOffset = y * width
            for (x in 0 until width) {
                sum += (grayPixels[rowOffset + x] and 0xFF)
                integral[rowOffset + x] = if (y == 0) sum else integral[(y - 1) * width + x] + sum
            }
        }

        // Apply threshold
        val factor = 1.0f - t
        for (y in 0 until height) {
            val y1 = maxOf(0, y - s2)
            val y2 = minOf(height - 1, y + s2)
            val rowOffset = y * width

            for (x in 0 until width) {
                val x1 = maxOf(0, x - s2)
                val x2 = minOf(width - 1, x + s2)
                val area = (x2 - x1 + 1) * (y2 - y1 + 1)

                val sum = integral[y2 * width + x2] -
                        (if (x1 > 0) integral[y2 * width + x1 - 1] else 0L) -
                        (if (y1 > 0) integral[(y1 - 1) * width + x2] else 0L) +
                        (if (x1 > 0 && y1 > 0) integral[(y1 - 1) * width + x1 - 1] else 0L)

                val pixelValue = grayPixels[rowOffset + x] and 0xFF
                if (pixelValue * area <= sum * factor) {
                    output[rowOffset + x] = 0xFF000000.toInt() // Black ink
                } else {
                    output[rowOffset + x] = 0xFFFFFFFF.toInt() // White paper
                }
            }
        }

        return output
    }
}
