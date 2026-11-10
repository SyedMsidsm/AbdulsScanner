package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.domain.imaging.Filters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {

    @Test
    fun testFindPercentilesMapping() {
        // Create an array with values from 0 to 255
        val pixels = IntArray(1000) { idx ->
            val v = (idx * 255 / 999)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        val (low, high) = Filters.findPercentiles(pixels, 0.02f, 0.98f)
        assertTrue("Low percentile should be near 5", low in 0..15)
        assertTrue("High percentile should be near 250", high in 240..255)
    }

    @Test
    fun testGrayscaleLuminanceFormula() {
        val width = 1
        val height = 1
        // Pure red: 0xFFFF0000 -> Y = 0.299 * 255 = 76.245
        val redPixel = intArrayOf(0xFFFF0000.toInt())
        val gray = Filters.applyGrayscale(redPixel, width, height)

        val r = (gray[0] shr 16) and 0xFF
        val g = (gray[0] shr 8) and 0xFF
        val b = gray[0] and 0xFF

        assertEquals(r, g)
        assertEquals(g, b)
    }

    @Test
    fun testBrightnessContrastAdjustment() {
        val original = intArrayOf(0xFF808080.toInt()) // 128 mid-gray
        val brightened = Filters.adjustBrightnessContrast(original, brightness = 10, contrast = 0)

        val r = (brightened[0] shr 16) and 0xFF
        assertTrue("Brightness should increase channel value", r > 128)
    }
}
