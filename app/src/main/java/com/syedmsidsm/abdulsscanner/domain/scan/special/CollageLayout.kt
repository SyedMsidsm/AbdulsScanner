package com.syedmsidsm.abdulsscanner.domain.scan.special

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object CollageLayout {
    const val CANVAS_WIDTH = 2480
    const val CANVAS_HEIGHT = 3508
    const val MARGIN = 120
    const val GAP = 60

    enum class CollageTemplate(val count: Int, val title: String) {
        TWO_ROWS(2, "2 Rows"),
        TWO_COLUMNS(2, "2 Columns"),
        THREE_ROWS(3, "3 Rows"),
        ONE_LARGE_TWO_SMALL(3, "1 Large, 2 Small"),
        GRID_2X2(4, "2×2 Grid"),
        FOUR_ROWS(4, "4 Rows")
    }

    data class CellRect(val left: Int, val top: Int, val width: Int, val height: Int) {
        val right: Int get() = left + width
        val bottom: Int get() = top + height
    }

    fun calculateCells(template: CollageTemplate): List<CellRect> {
        val usableW = CANVAS_WIDTH - (MARGIN * 2)
        val usableH = CANVAS_HEIGHT - (MARGIN * 2)

        return when (template) {
            CollageTemplate.TWO_ROWS -> {
                val cellH = (usableH - GAP) / 2
                listOf(
                    CellRect(MARGIN, MARGIN, usableW, cellH),
                    CellRect(MARGIN, MARGIN + cellH + GAP, usableW, cellH)
                )
            }
            CollageTemplate.TWO_COLUMNS -> {
                val cellW = (usableW - GAP) / 2
                listOf(
                    CellRect(MARGIN, MARGIN, cellW, usableH),
                    CellRect(MARGIN + cellW + GAP, MARGIN, cellW, usableH)
                )
            }
            CollageTemplate.THREE_ROWS -> {
                val cellH = (usableH - (GAP * 2)) / 3
                listOf(
                    CellRect(MARGIN, MARGIN, usableW, cellH),
                    CellRect(MARGIN, MARGIN + cellH + GAP, usableW, cellH),
                    CellRect(MARGIN, MARGIN + (cellH + GAP) * 2, usableW, cellH)
                )
            }
            CollageTemplate.ONE_LARGE_TWO_SMALL -> {
                val rowH = (usableH - GAP) / 2
                val smallW = (usableW - GAP) / 2
                listOf(
                    CellRect(MARGIN, MARGIN, usableW, rowH),
                    CellRect(MARGIN, MARGIN + rowH + GAP, smallW, rowH),
                    CellRect(MARGIN + smallW + GAP, MARGIN + rowH + GAP, smallW, rowH)
                )
            }
            CollageTemplate.GRID_2X2 -> {
                val cellW = (usableW - GAP) / 2
                val cellH = (usableH - GAP) / 2
                listOf(
                    CellRect(MARGIN, MARGIN, cellW, cellH),
                    CellRect(MARGIN + cellW + GAP, MARGIN, cellW, cellH),
                    CellRect(MARGIN, MARGIN + cellH + GAP, cellW, cellH),
                    CellRect(MARGIN + cellW + GAP, MARGIN + cellH + GAP, cellW, cellH)
                )
            }
            CollageTemplate.FOUR_ROWS -> {
                val cellH = (usableH - (GAP * 3)) / 4
                listOf(
                    CellRect(MARGIN, MARGIN, usableW, cellH),
                    CellRect(MARGIN, MARGIN + cellH + GAP, usableW, cellH),
                    CellRect(MARGIN, MARGIN + (cellH + GAP) * 2, usableW, cellH),
                    CellRect(MARGIN, MARGIN + (cellH + GAP) * 3, usableW, cellH)
                )
            }
        }
    }

    fun calculateFitRect(sourceWidth: Int, sourceHeight: Int, cell: CellRect): CellRect {
        val scale = minOf(cell.width.toFloat() / sourceWidth, cell.height.toFloat() / sourceHeight)
        val scaledW = (sourceWidth * scale).toInt()
        val scaledH = (sourceHeight * scale).toInt()
        val left = cell.left + (cell.width - scaledW) / 2
        val top = cell.top + (cell.height - scaledH) / 2
        return CellRect(left, top, scaledW, scaledH)
    }

    fun composeCollage(bitmaps: List<Bitmap>, template: CollageTemplate): Bitmap {
        val cells = calculateCells(template)
        val result = Bitmap.createBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        for (i in 0 until minOf(bitmaps.size, cells.size)) {
            val bmp = bitmaps[i]
            val cell = cells[i]
            val fit = calculateFitRect(bmp.width, bmp.height, cell)
            val srcRect = android.graphics.Rect(0, 0, bmp.width, bmp.height)
            val dstRect = android.graphics.Rect(fit.left, fit.top, fit.left + fit.width, fit.top + fit.height)
            canvas.drawBitmap(bmp, srcRect, dstRect, paint)
        }
        return result
    }
}
