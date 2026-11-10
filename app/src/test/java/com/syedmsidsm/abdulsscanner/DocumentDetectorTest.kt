package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.domain.imaging.DocumentDetector
import com.syedmsidsm.abdulsscanner.domain.imaging.Point2D
import com.syedmsidsm.abdulsscanner.domain.imaging.Quad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencv.core.Point

class DocumentDetectorTest {

    @Test
    fun testOrderAndNormalizePoints_scrambledCorners() {
        val width = 640f
        val height = 480f

        // Defined in random order:
        // Bottom-Right: (500, 400)
        // Top-Left: (100, 50)
        // Bottom-Left: (80, 420)
        // Top-Right: (520, 60)
        val pts = arrayOf(
            Point(500.0, 400.0),
            Point(100.0, 50.0),
            Point(80.0, 420.0),
            Point(520.0, 60.0)
        )

        val quad = DocumentDetector.orderAndNormalizePoints(pts, width, height)

        // p0 must be Top-Left: (100, 50)
        assertEquals(100f / 640f, quad.p0.x, 0.001f)
        assertEquals(50f / 480f, quad.p0.y, 0.001f)

        // p1 must be Top-Right: (520, 60)
        assertEquals(520f / 640f, quad.p1.x, 0.001f)
        assertEquals(60f / 480f, quad.p1.y, 0.001f)

        // p2 must be Bottom-Right: (500, 400)
        assertEquals(500f / 640f, quad.p2.x, 0.001f)
        assertEquals(400f / 480f, quad.p2.y, 0.001f)

        // p3 must be Bottom-Left: (80, 420)
        assertEquals(80f / 640f, quad.p3.x, 0.001f)
        assertEquals(420f / 480f, quad.p3.y, 0.001f)
    }

    @Test
    fun testCalculateAverageQuad() {
        val q1 = Quad(Point2D(0.1f, 0.1f), Point2D(0.9f, 0.1f), Point2D(0.9f, 0.9f), Point2D(0.1f, 0.9f))
        val q2 = Quad(Point2D(0.2f, 0.2f), Point2D(0.8f, 0.2f), Point2D(0.8f, 0.8f), Point2D(0.2f, 0.8f))

        val avg = DocumentDetector.calculateAverageQuad(listOf(q1, q2))

        assertEquals(0.15f, avg.p0.x, 0.001f)
        assertEquals(0.15f, avg.p0.y, 0.001f)
        assertEquals(0.85f, avg.p1.x, 0.001f)
        assertEquals(0.15f, avg.p1.y, 0.001f)
        assertEquals(0.85f, avg.p2.x, 0.001f)
        assertEquals(0.85f, avg.p2.y, 0.001f)
        assertEquals(0.15f, avg.p3.x, 0.001f)
        assertEquals(0.85f, avg.p3.y, 0.001f)
    }

    @Test
    fun testMaxCornerDistance_stabilityThreshold() {
        val q1 = Quad(Point2D(0.10f, 0.10f), Point2D(0.90f, 0.10f), Point2D(0.90f, 0.90f), Point2D(0.10f, 0.90f))
        // q2 shifted slightly by 0.01 (< 0.02 threshold)
        val q2 = Quad(Point2D(0.11f, 0.10f), Point2D(0.91f, 0.10f), Point2D(0.89f, 0.90f), Point2D(0.10f, 0.91f))
        // q3 shifted substantially by 0.05 (> 0.02 threshold)
        val q3 = Quad(Point2D(0.15f, 0.10f), Point2D(0.90f, 0.10f), Point2D(0.90f, 0.90f), Point2D(0.10f, 0.90f))

        val distSmall = DocumentDetector.maxCornerDistance(q1, q2)
        val distLarge = DocumentDetector.maxCornerDistance(q1, q3)

        assertTrue("Distance $distSmall should be < 0.02 (stable)", distSmall < 0.02f)
        assertFalse("Distance $distLarge should be >= 0.02 (unstable)", distLarge < 0.02f)
    }
}
