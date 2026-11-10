package com.syedmsidsm.abdulsscanner.domain.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.File
import kotlin.math.roundToInt

object LongImageStitcher {

    /**
     * Stacks the rendered page files vertically at the width of the narrowest page
     * (max 1600 px wide), 24 px white gap, JPEG quality 85.
     * If the total height would exceed 20,000 px, scales all pages down to fit.
     */
    fun createLongImage(pageFiles: List<File>, outputFile: File): Boolean {
        if (pageFiles.isEmpty()) return false

        val pageBitmaps = pageFiles.mapNotNull { file ->
            if (file.exists()) BitmapIo.decodeUpright(file, maxLongSide = 4000) else null
        }
        if (pageBitmaps.isEmpty()) return false

        try {
            val minWidth = pageBitmaps.minOf { it.width }
            val targetWidth = minOf(minWidth, 1600)
            val gap = 24

            val scaledHeights = pageBitmaps.map { bmp ->
                val scale = targetWidth.toFloat() / bmp.width.toFloat()
                (bmp.height * scale).roundToInt()
            }

            var totalHeight = scaledHeights.sum() + gap * (pageBitmaps.size - 1)
            var globalScale = 1.0f

            if (totalHeight > 20000) {
                globalScale = 20000f / totalHeight.toFloat()
                totalHeight = 20000
            }

            val finalWidth = (targetWidth * globalScale).roundToInt().coerceAtLeast(1)
            val finalHeight = totalHeight.coerceAtLeast(1)

            val longBmp = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(longBmp)
            canvas.drawColor(Color.WHITE)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            var currentY = 0

            for (i in pageBitmaps.indices) {
                val srcBmp = pageBitmaps[i]
                val h = (scaledHeights[i] * globalScale).roundToInt()
                val dstRect = Rect(0, currentY, finalWidth, currentY + h)
                canvas.drawBitmap(srcBmp, null, dstRect, paint)
                currentY += h + (gap * globalScale).roundToInt()
            }

            outputFile.parentFile?.mkdirs()
            outputFile.outputStream().use { out ->
                longBmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.flush()
            }
            longBmp.recycle()
            return true
        } finally {
            pageBitmaps.forEach { it.recycle() }
        }
    }
}
