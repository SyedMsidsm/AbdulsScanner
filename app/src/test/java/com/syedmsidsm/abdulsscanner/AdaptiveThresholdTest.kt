package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.domain.imaging.AdaptiveThreshold
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveThresholdTest {

    @Test
    fun testBinarizationProducesOnlyBlackAndWhite() {
        val width = 20
        val height = 20
        val gray = IntArray(width * height) { idx ->
            // Gradient with a dark spot in the middle
            val x = idx % width
            val y = idx / width
            if (x in 8..12 && y in 8..12) 30 else 220
        }

        val binary = AdaptiveThreshold.binarize(gray, width, height, t = 0.15f)
        assertEquals(width * height, binary.size)

        for (pixel in binary) {
            val isBlackOrWhite = (pixel == 0xFF000000.toInt() || pixel == 0xFFFFFFFF.toInt())
            assert(isBlackOrWhite) { "Pixel must be either black or white, but was $pixel" }
        }

        // Spot in the center should be black ink
        val centerPixel = binary[10 * width + 10]
        assertEquals(0xFF000000.toInt(), centerPixel)
    }
}
