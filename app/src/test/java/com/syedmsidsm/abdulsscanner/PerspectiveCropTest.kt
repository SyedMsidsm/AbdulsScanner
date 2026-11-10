package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.domain.imaging.PerspectiveCrop
import com.syedmsidsm.abdulsscanner.domain.imaging.Point2D
import com.syedmsidsm.abdulsscanner.domain.imaging.Quad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerspectiveCropTest {

    @Test
    fun testValidConvexQuad() {
        val quad = Quad(
            Point2D(0.1f, 0.1f),
            Point2D(0.9f, 0.15f),
            Point2D(0.85f, 0.85f),
            Point2D(0.12f, 0.9f)
        )
        assertTrue(PerspectiveCrop.isValidQuad(quad))
    }

    @Test
    fun testSelfIntersectingBowtieQuad() {
        // Crossed edges: p0 connects to p1, but p1 crosses to p3 instead of p2
        val bowtie = Quad(
            Point2D(0.1f, 0.1f),
            Point2D(0.9f, 0.9f),
            Point2D(0.1f, 0.9f),
            Point2D(0.9f, 0.1f)
        )
        assertFalse(PerspectiveCrop.isValidQuad(bowtie))
    }

    @Test
    fun testOutputDimensionsCalculation() {
        val quad = Quad.fullQuad()
        val (w, h) = PerspectiveCrop.calculateOutputSize(quad, 1000, 1500)
        assertEquals(1000, w)
        assertEquals(1500, h)
    }

    @Test
    fun testOutputDimensionsCappedAt4000() {
        val quad = Quad.fullQuad()
        val (w, h) = PerspectiveCrop.calculateOutputSize(quad, 8000, 6000)
        assertEquals(4000, w)
        assertEquals(3000, h)
    }
}
