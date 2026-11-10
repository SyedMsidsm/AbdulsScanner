package com.syedmsidsm.abdulsscanner.domain.imaging

import kotlin.math.roundToInt

object Filters {

    /**
     * Extracts luminance histogram and finds low and high percentile thresholds.
     */
    fun findPercentiles(pixels: IntArray, lowPercentile: Float = 0.02f, highPercentile: Float = 0.98f): Pair<Int, Int> {
        val hist = IntArray(256)
        val step = maxOf(1, pixels.size / 50000) // Sample up to 50k pixels
        var sampled = 0

        var i = 0
        while (i < pixels.size) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val y = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt().coerceIn(0, 255)
            hist[y]++
            sampled++
            i += step
        }

        val lowCount = (sampled * lowPercentile).toInt()
        val highCount = (sampled * highPercentile).toInt()

        var sum = 0
        var low = 0
        for (v in 0..255) {
            sum += hist[v]
            if (sum >= lowCount) {
                low = v
                break
            }
        }

        sum = 0
        var high = 255
        for (v in 0..255) {
            sum += hist[v]
            if (sum >= highCount) {
                high = v
                break
            }
        }

        if (high <= low) {
            low = 0
            high = 255
        }

        return Pair(low, high)
    }

    /**
     * Applies auto-levels mapping [low, high] -> [0, 255] and contrast boost.
     */
    fun applyEnhanced(pixels: IntArray, width: Int, height: Int): IntArray {
        val (low, high) = findPercentiles(pixels, 0.02f, 0.98f)
        val range = (high - low).toFloat()
        val out = IntArray(pixels.size)

        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c shr 24) and 0xFF
            var r = (c shr 16) and 0xFF
            var g = (c shr 8) and 0xFF
            var b = c and 0xFF

            // Auto levels stretch
            r = (((r - low) / range) * 255f).roundToInt().coerceIn(0, 255)
            g = (((g - low) / range) * 255f).roundToInt().coerceIn(0, 255)
            b = (((b - low) / range) * 255f).roundToInt().coerceIn(0, 255)

            // Contrast factor 1.15 around 128
            r = ((r - 128) * 1.15f + 128).roundToInt().coerceIn(0, 255)
            g = ((g - 128) * 1.15f + 128).roundToInt().coerceIn(0, 255)
            b = ((b - 128) * 1.15f + 128).roundToInt().coerceIn(0, 255)

            // Saturation 1.10
            val gray = (0.299f * r + 0.587f * g + 0.114f * b)
            r = (gray + (r - gray) * 1.10f).roundToInt().coerceIn(0, 255)
            g = (gray + (g - gray) * 1.10f).roundToInt().coerceIn(0, 255)
            b = (gray + (b - gray) * 1.10f).roundToInt().coerceIn(0, 255)

            out[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    /**
     * Converts to Grayscale with auto-levels.
     */
    fun applyGrayscale(pixels: IntArray, width: Int, height: Int): IntArray {
        val (low, high) = findPercentiles(pixels, 0.02f, 0.98f)
        val range = (high - low).toFloat()
        val out = IntArray(pixels.size)

        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c shr 24) and 0xFF
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            var y = (0.299f * r + 0.587f * g + 0.114f * b)
            y = (((y - low) / range) * 255f).coerceIn(0f, 255f)
            val yInt = y.roundToInt()

            out[i] = (a shl 24) or (yInt shl 16) or (yInt shl 8) or yInt
        }
        return out
    }

    /**
     * B&W filter using Bradley-Roth adaptive threshold.
     */
    fun applyBw(pixels: IntArray, width: Int, height: Int): IntArray {
        val gray = IntArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt()
        }
        return AdaptiveThreshold.binarize(gray, width, height, t = 0.15f)
    }

    /**
     * Adjusts brightness (-100..100) and contrast (-100..100).
     */
    fun adjustBrightnessContrast(pixels: IntArray, brightness: Int, contrast: Int): IntArray {
        if (brightness == 0 && contrast == 0) return pixels

        val bOffset = brightness * 1.28f
        val factor = (100f + contrast) / 100f
        val out = IntArray(pixels.size)

        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c shr 24) and 0xFF
            var r = (c shr 16) and 0xFF
            var g = (c shr 8) and 0xFF
            var b = c and 0xFF

            // Apply contrast around 128
            r = ((r - 128) * factor + 128 + bOffset).roundToInt().coerceIn(0, 255)
            g = ((g - 128) * factor + 128 + bOffset).roundToInt().coerceIn(0, 255)
            b = ((b - 128) * factor + 128 + bOffset).roundToInt().coerceIn(0, 255)

            out[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }
}
