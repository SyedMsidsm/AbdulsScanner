package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.domain.pdf.PdfCoordinates
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfCoordinatesTest {

    private val pageWidth = 600f
    private val pageHeight = 800f
    private val delta = 0.001f

    @Test
    fun testRotation0Degrees() {
        // Overlay at top-left: x=0, y=0, w=0.5, h=0.25
        val rect = PdfCoordinates.mapToPdfSpace(
            normX = 0f,
            normY = 0f,
            normW = 0.5f,
            normH = 0.25f,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            rotation = 0
        )

        // px = 0, py = 800 - (0 + 0.25)*800 = 600
        // pw = 300, ph = 200
        assertEquals(0f, rect.x, delta)
        assertEquals(600f, rect.y, delta)
        assertEquals(300f, rect.width, delta)
        assertEquals(200f, rect.height, delta)
    }

    @Test
    fun testRotation90Degrees() {
        // At 90 degrees clockwise rotation
        val rect = PdfCoordinates.mapToPdfSpace(
            normX = 0f,
            normY = 0f,
            normW = 0.5f,
            normH = 0.25f,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            rotation = 90
        )

        // normX=0, normY=0, normW=0.5, normH=0.25
        // unrotX = 1 - (0 + 0.25) = 0.75
        // unrotY = 0
        // unrotW = 0.25
        // unrotH = 0.5
        // px = 0.75 * 600 = 450
        // py = 800 - (0 + 0.5)*800 = 400
        // pw = 0.25 * 600 = 150
        // ph = 0.5 * 800 = 400
        assertEquals(450f, rect.x, delta)
        assertEquals(400f, rect.y, delta)
        assertEquals(150f, rect.width, delta)
        assertEquals(400f, rect.height, delta)
    }

    @Test
    fun testRotation180Degrees() {
        val rect = PdfCoordinates.mapToPdfSpace(
            normX = 0f,
            normY = 0f,
            normW = 0.5f,
            normH = 0.25f,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            rotation = 180
        )

        // unrotX = 1 - (0 + 0.5) = 0.5
        // unrotY = 1 - (0 + 0.25) = 0.75
        // unrotW = 0.5, unrotH = 0.25
        // px = 0.5 * 600 = 300
        // py = 800 - (0.75 + 0.25)*800 = 0
        // pw = 300, ph = 200
        assertEquals(300f, rect.x, delta)
        assertEquals(0f, rect.y, delta)
        assertEquals(300f, rect.width, delta)
        assertEquals(200f, rect.height, delta)
    }

    @Test
    fun testRotation270Degrees() {
        val rect = PdfCoordinates.mapToPdfSpace(
            normX = 0f,
            normY = 0f,
            normW = 0.5f,
            normH = 0.25f,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            rotation = 270
        )

        // unrotX = 0
        // unrotY = 1 - (0 + 0.5) = 0.5
        // unrotW = 0.25, unrotH = 0.5
        // px = 0
        // py = 800 - (0.5 + 0.5)*800 = 0
        // pw = 0.25 * 600 = 150
        // ph = 0.5 * 800 = 400
        assertEquals(0f, rect.x, delta)
        assertEquals(0f, rect.y, delta)
        assertEquals(150f, rect.width, delta)
        assertEquals(400f, rect.height, delta)
    }
}
