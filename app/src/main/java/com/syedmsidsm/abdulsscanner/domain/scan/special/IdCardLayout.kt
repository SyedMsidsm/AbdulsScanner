package com.syedmsidsm.abdulsscanner.domain.scan.special

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint

object IdCardLayout {
    const val CANVAS_WIDTH = 2480
    const val CANVAS_HEIGHT = 3508
    const val CARD_WIDTH = 1011
    const val CARD_HEIGHT = 638
    const val FRONT_X = 734
    const val FRONT_Y = 600
    const val BACK_X = 734
    const val BACK_Y = 1478

    data class Rect(val left: Int, val top: Int, val width: Int, val height: Int) {
        val right: Int get() = left + width
        val bottom: Int get() = top + height
    }

    val frontRect = Rect(FRONT_X, FRONT_Y, CARD_WIDTH, CARD_HEIGHT)
    val backRect = Rect(BACK_X, BACK_Y, CARD_WIDTH, CARD_HEIGHT)

    /**
     * Calculates the fitted destination rectangle inside the target bounding box.
     * If the source image is portrait (width < height), it is rotated 90 degrees to landscape.
     */
    fun calculateFitRect(sourceWidth: Int, sourceHeight: Int, target: Rect): Rect {
        val isPortrait = sourceWidth < sourceHeight
        val (w, h) = if (isPortrait) Pair(sourceHeight, sourceWidth) else Pair(sourceWidth, sourceHeight)
        val scale = minOf(target.width.toFloat() / w, target.height.toFloat() / h)
        val scaledW = (w * scale).toInt()
        val scaledH = (h * scale).toInt()
        val left = target.left + (target.width - scaledW) / 2
        val top = target.top + (target.height - scaledH) / 2
        return Rect(left, top, scaledW, scaledH)
    }

    fun composeIdCard(frontBitmap: Bitmap, backBitmap: Bitmap?): Bitmap {
        val canvasBitmap = Bitmap.createBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        drawCardSide(canvas, frontBitmap, frontRect, paint)
        if (backBitmap != null) {
            drawCardSide(canvas, backBitmap, backRect, paint)
        }
        return canvasBitmap
    }

    private fun drawCardSide(canvas: Canvas, bitmap: Bitmap, targetRect: Rect, paint: Paint) {
        var bmp = bitmap
        if (bmp.width < bmp.height) {
            val matrix = Matrix().apply { postRotate(90f) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }
        val scale = minOf(targetRect.width.toFloat() / bmp.width, targetRect.height.toFloat() / bmp.height)
        val scaledW = (bmp.width * scale).toInt()
        val scaledH = (bmp.height * scale).toInt()
        val left = targetRect.left + (targetRect.width - scaledW) / 2
        val top = targetRect.top + (targetRect.height - scaledH) / 2

        val dstRect = android.graphics.Rect(left, top, left + scaledW, top + scaledH)
        val srcRect = android.graphics.Rect(0, 0, bmp.width, bmp.height)
        canvas.drawBitmap(bmp, srcRect, dstRect, paint)
    }
}
