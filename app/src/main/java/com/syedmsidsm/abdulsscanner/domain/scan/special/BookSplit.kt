package com.syedmsidsm.abdulsscanner.domain.scan.special

import android.graphics.Bitmap

object BookSplit {
    const val GUTTER_TRIM_RATIO = 0.015f
    const val MIN_SPLIT_FRACTION = 0.30f
    const val MAX_SPLIT_FRACTION = 0.70f

    enum class ReadingOrder { LTR, RTL }

    data class IntRect(val x: Int, val y: Int, val width: Int, val height: Int)

    data class SplitRects(
        val leftRect: IntRect,
        val rightRect: IntRect
    )

    fun calculateSplitRects(width: Int, height: Int, splitFraction: Float): SplitRects {
        val clampedFraction = splitFraction.coerceIn(MIN_SPLIT_FRACTION, MAX_SPLIT_FRACTION)
        val splitX = (clampedFraction * width).toInt()
        val trimPx = (GUTTER_TRIM_RATIO * width).toInt()

        val leftWidth = (splitX - trimPx).coerceIn(1, width)
        val rightX = (splitX + trimPx).coerceIn(0, width - 1)
        val rightWidth = (width - rightX).coerceIn(1, width)

        return SplitRects(
            leftRect = IntRect(0, 0, leftWidth, height),
            rightRect = IntRect(rightX, 0, rightWidth, height)
        )
    }

    fun getOrderedPages(splitRects: SplitRects, order: ReadingOrder): Pair<IntRect, IntRect> {
        return when (order) {
            ReadingOrder.LTR -> Pair(splitRects.leftRect, splitRects.rightRect)
            ReadingOrder.RTL -> Pair(splitRects.rightRect, splitRects.leftRect)
        }
    }

    fun splitBitmap(bitmap: Bitmap, splitFraction: Float, order: ReadingOrder): Pair<Bitmap, Bitmap> {
        val rects = calculateSplitRects(bitmap.width, bitmap.height, splitFraction)
        val left = Bitmap.createBitmap(bitmap, rects.leftRect.x, rects.leftRect.y, rects.leftRect.width, rects.leftRect.height)
        val right = Bitmap.createBitmap(bitmap, rects.rightRect.x, rects.rightRect.y, rects.rightRect.width, rects.rightRect.height)
        return when (order) {
            ReadingOrder.LTR -> Pair(left, right)
            ReadingOrder.RTL -> Pair(right, left)
        }
    }
}
