package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.domain.pdf.PdfLayoutMath
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfLayoutMathTest {

    @Test
    fun testAutoPageSizeDimensions() {
        // Portrait 1000 x 2000 -> w = 595.28, h = 595.28 * 2 = 1190.56
        val (box, margin) = PdfLayoutMath.calculatePageBox(1000, 2000, "AUTO")
        assertEquals(0f, margin, 0.01f)
        assertEquals(595.28f, box.width, 0.01f)
        assertEquals(1190.56f, box.height, 0.01f)
    }

    @Test
    fun testA4PortraitPageSizeDimensions() {
        val (box, margin) = PdfLayoutMath.calculatePageBox(1200, 1800, "A4")
        assertEquals(18f, margin, 0.01f)
        assertEquals(595.28f, box.width, 0.01f)
        assertEquals(841.89f, box.height, 0.01f)
    }

    @Test
    fun testFitCenteredCalculatesExactCentering() {
        val box = PDRectangle(600f, 800f)
        val placement = PdfLayoutMath.fitCentered(300, 400, box, margin = 20f)
        // Available: 560 x 760. Scale = min(560/300, 760/400) = min(1.866, 1.9) = 1.8666f
        // width = 300 * 1.8666 = 560. x = 20 + 0 = 20
        assertEquals(20f, placement.x, 0.01f)
        assertEquals(560f, placement.w, 0.01f)
    }
}
