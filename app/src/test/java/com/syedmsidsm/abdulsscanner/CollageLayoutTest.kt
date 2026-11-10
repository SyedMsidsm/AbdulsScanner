package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.domain.scan.special.CollageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageLayoutTest {

    @Test
    fun testTwoRowsCellLayout() {
        val cells = CollageLayout.calculateCells(CollageLayout.CollageTemplate.TWO_ROWS)
        assertEquals(2, cells.size)

        // Margin is 120, gap is 60
        // Canvas width is 2480, height is 3508
        // Usable width = 2480 - 240 = 2240
        // Usable height = 3508 - 240 = 3268
        // Cell height = (3268 - 60) / 2 = 1604
        val top = cells[0]
        val bottom = cells[1]

        assertEquals(120, top.left)
        assertEquals(120, top.top)
        assertEquals(2240, top.width)
        assertEquals(1604, top.height)

        assertEquals(120, bottom.left)
        assertEquals(120 + 1604 + 60, bottom.top)
        assertEquals(2240, bottom.width)
        assertEquals(1604, bottom.height)
    }

    @Test
    fun testTwoColumnsCellLayout() {
        val cells = CollageLayout.calculateCells(CollageLayout.CollageTemplate.TWO_COLUMNS)
        assertEquals(2, cells.size)

        val left = cells[0]
        val right = cells[1]

        // Usable width = 2240, cell width = (2240 - 60) / 2 = 1090
        assertEquals(120, left.left)
        assertEquals(120, left.top)
        assertEquals(1090, left.width)
        assertEquals(3268, left.height)

        assertEquals(120 + 1090 + 60, right.left)
        assertEquals(120, right.top)
        assertEquals(1090, right.width)
        assertEquals(3268, right.height)
    }

    @Test
    fun testGrid2x2CellLayout() {
        val cells = CollageLayout.calculateCells(CollageLayout.CollageTemplate.GRID_2X2)
        assertEquals(4, cells.size)

        for (cell in cells) {
            assertEquals(1090, cell.width)
            assertEquals(1604, cell.height)
            assertTrue(cell.left >= CollageLayout.MARGIN)
            assertTrue(cell.top >= CollageLayout.MARGIN)
            assertTrue(cell.right <= CollageLayout.CANVAS_WIDTH - CollageLayout.MARGIN)
            assertTrue(cell.bottom <= CollageLayout.CANVAS_HEIGHT - CollageLayout.MARGIN)
        }
    }

    @Test
    fun testFitRectMaintainsAspectRatio() {
        val cell = CollageLayout.CellRect(100, 100, 1000, 1000)
        // A 4:3 photo (1600 x 1200) inside square 1000x1000
        val fit = CollageLayout.calculateFitRect(1600, 1200, cell)
        assertEquals(1000, fit.width)
        assertEquals(750, fit.height)
        assertEquals(100, fit.left)
        assertEquals(100 + (1000 - 750) / 2, fit.top)
    }
}
