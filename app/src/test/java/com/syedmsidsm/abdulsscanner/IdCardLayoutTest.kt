package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.domain.scan.special.IdCardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdCardLayoutTest {

    @Test
    fun testCanvasAndCardDimensions() {
        // A4 at 300 DPI is 2480 x 3508
        assertEquals(2480, IdCardLayout.CANVAS_WIDTH)
        assertEquals(3508, IdCardLayout.CANVAS_HEIGHT)

        // ID-1 card size 85.6 x 54 mm = 1011 x 638 px
        assertEquals(1011, IdCardLayout.CARD_WIDTH)
        assertEquals(638, IdCardLayout.CARD_HEIGHT)

        // Centered horizontally: (2480 - 1011) / 2 = 734
        assertEquals(734, IdCardLayout.FRONT_X)
        assertEquals(734, IdCardLayout.BACK_X)

        assertEquals(600, IdCardLayout.FRONT_Y)
        // Back Y = 600 + 638 + 240 = 1478
        assertEquals(1478, IdCardLayout.BACK_Y)
    }

    @Test
    fun testFitLandscapeImageInCardRect() {
        val target = IdCardLayout.frontRect
        // Landscape image with exact aspect ratio
        val fit = IdCardLayout.calculateFitRect(1011, 638, target)
        assertEquals(target.left, fit.left)
        assertEquals(target.top, fit.top)
        assertEquals(target.width, fit.width)
        assertEquals(target.height, fit.height)
    }

    @Test
    fun testFitPortraitImageRotatesToLandscape() {
        val target = IdCardLayout.frontRect
        // A portrait card image (e.g. 638 wide x 1011 high) should be treated as 1011 x 638 rotated
        val fit = IdCardLayout.calculateFitRect(638, 1011, target)
        assertEquals(target.left, fit.left)
        assertEquals(target.top, fit.top)
        assertEquals(target.width, fit.width)
        assertEquals(target.height, fit.height)
    }

    @Test
    fun testFitRectStaysWithinTargetBounds() {
        val target = IdCardLayout.backRect
        val fit = IdCardLayout.calculateFitRect(1920, 1080, target)
        assertTrue(fit.left >= target.left)
        assertTrue(fit.top >= target.top)
        assertTrue(fit.right <= target.right)
        assertTrue(fit.bottom <= target.bottom)
    }
}
