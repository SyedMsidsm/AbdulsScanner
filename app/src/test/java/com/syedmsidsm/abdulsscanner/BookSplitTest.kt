package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.domain.scan.special.BookSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSplitTest {

    @Test
    fun testDefaultSplitAndGutterTrim() {
        val width = 2000
        val height = 1500
        val fraction = 0.50f

        val rects = BookSplit.calculateSplitRects(width, height, fraction)

        // 1.5% trim of 2000 is 30 px
        // splitX = 1000
        // left: 0 .. (1000 - 30) = 0 .. 970 (width 970)
        // right: (1000 + 30) .. 2000 = 1030 .. 2000 (width 970)
        assertEquals(0, rects.leftRect.x)
        assertEquals(970, rects.leftRect.width)
        assertEquals(1500, rects.leftRect.height)

        assertEquals(1030, rects.rightRect.x)
        assertEquals(970, rects.rightRect.width)
        assertEquals(1500, rects.rightRect.height)

        // Gutter area removed = 60 px (3% of width)
        assertEquals(60, width - (rects.leftRect.width + rects.rightRect.width))
    }

    @Test
    fun testClampedFractions() {
        val width = 1000
        val height = 800

        // Less than 30% should be clamped to 30%
        val rectsLow = BookSplit.calculateSplitRects(width, height, 0.10f)
        // 30% of 1000 is 300, trim is 15 -> left width = 285
        assertEquals(285, rectsLow.leftRect.width)

        // More than 70% should be clamped to 70%
        val rectsHigh = BookSplit.calculateSplitRects(width, height, 0.90f)
        // 70% of 1000 is 700, trim is 15 -> left width = 685
        assertEquals(685, rectsHigh.leftRect.width)
    }

    @Test
    fun testReadingOrderLtrVsRtl() {
        val width = 2000
        val height = 1500
        val rects = BookSplit.calculateSplitRects(width, height, 0.50f)

        // LTR: First page is left, second page is right
        val (firstLtr, secondLtr) = BookSplit.getOrderedPages(rects, BookSplit.ReadingOrder.LTR)
        assertEquals(rects.leftRect, firstLtr)
        assertEquals(rects.rightRect, secondLtr)

        // RTL: First page is right, second page is left (for Arabic books)
        val (firstRtl, secondRtl) = BookSplit.getOrderedPages(rects, BookSplit.ReadingOrder.RTL)
        assertEquals(rects.rightRect, firstRtl)
        assertEquals(rects.leftRect, secondRtl)
    }
}
