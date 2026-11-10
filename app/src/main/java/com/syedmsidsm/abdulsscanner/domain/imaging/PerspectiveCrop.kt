package com.syedmsidsm.abdulsscanner.domain.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.hypot
import kotlin.math.roundToInt

data class Point2D(val x: Float, val y: Float)

data class Quad(
    val p0: Point2D, // Top-Left
    val p1: Point2D, // Top-Right
    val p2: Point2D, // Bottom-Right
    val p3: Point2D  // Bottom-Left
) {
    fun toNormalizedList(): List<Float> {
        return listOf(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
    }

    companion object {
        fun fromNormalizedList(coords: List<Float>): Quad {
            require(coords.size == 8) { "Quad requires 8 normalized coordinates" }
            return Quad(
                Point2D(coords[0], coords[1]),
                Point2D(coords[2], coords[3]),
                Point2D(coords[4], coords[5]),
                Point2D(coords[6], coords[7])
            )
        }

        fun fullQuad(): Quad {
            return Quad(
                Point2D(0f, 0f),
                Point2D(1f, 0f),
                Point2D(1f, 1f),
                Point2D(0f, 1f)
            )
        }
    }
}

object PerspectiveCrop {

    fun dist(a: Point2D, b: Point2D): Float = hypot(a.x - b.x, a.y - b.y)

    /**
     * Checks if segments AB and CD intersect.
     */
    private fun linesIntersect(a: Point2D, b: Point2D, c: Point2D, d: Point2D): Boolean {
        fun ccw(p1: Point2D, p2: Point2D, p3: Point2D): Float {
            return (p2.x - p1.x) * (p3.y - p1.y) - (p2.y - p1.y) * (p3.x - p1.x)
        }
        val d1 = ccw(a, b, c)
        val d2 = ccw(a, b, d)
        val d3 = ccw(c, d, a)
        val d4 = ccw(c, d, b)

        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    /**
     * Returns true if quad edges do not self-intersect and have positive orientation.
     */
    fun isValidQuad(quad: Quad): Boolean {
        // Check edge intersection: Top-Bottom (p0-p1 with p3-p2) and Left-Right (p0-p3 with p1-p2)
        if (linesIntersect(quad.p0, quad.p1, quad.p3, quad.p2)) return false
        if (linesIntersect(quad.p0, quad.p3, quad.p1, quad.p2)) return false

        // Check clockwise cross products
        fun cross(a: Point2D, b: Point2D, c: Point2D): Float {
            return (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }
        val c0 = cross(quad.p0, quad.p1, quad.p2)
        val c1 = cross(quad.p1, quad.p2, quad.p3)
        val c2 = cross(quad.p2, quad.p3, quad.p0)
        val c3 = cross(quad.p3, quad.p0, quad.p1)

        val allPositive = c0 > 0 && c1 > 0 && c2 > 0 && c3 > 0
        val allNegative = c0 < 0 && c1 < 0 && c2 < 0 && c3 < 0

        return allPositive || allNegative
    }

    fun calculateOutputSize(quad: Quad, imgWidth: Int, imgHeight: Int): Pair<Int, Int> {
        val p0 = Point2D(quad.p0.x * imgWidth, quad.p0.y * imgHeight)
        val p1 = Point2D(quad.p1.x * imgWidth, quad.p1.y * imgHeight)
        val p2 = Point2D(quad.p2.x * imgWidth, quad.p2.y * imgHeight)
        val p3 = Point2D(quad.p3.x * imgWidth, quad.p3.y * imgHeight)

        var outW = maxOf(dist(p0, p1), dist(p3, p2)).roundToInt().coerceAtLeast(1)
        var outH = maxOf(dist(p0, p3), dist(p1, p2)).roundToInt().coerceAtLeast(1)

        val maxSide = maxOf(outW, outH)
        if (maxSide > 4000) {
            val scale = 4000f / maxSide
            outW = (outW * scale).roundToInt().coerceAtLeast(1)
            outH = (outH * scale).roundToInt().coerceAtLeast(1)
        }

        return Pair(outW, outH)
    }

    fun crop(src: Bitmap, quad: Quad): Bitmap {
        val (outW, outH) = calculateOutputSize(quad, src.width, src.height)

        val srcPoints = floatArrayOf(
            quad.p0.x * src.width, quad.p0.y * src.height,
            quad.p1.x * src.width, quad.p1.y * src.height,
            quad.p2.x * src.width, quad.p2.y * src.height,
            quad.p3.x * src.width, quad.p3.y * src.height
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val outBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(src, matrix, paint)

        return outBitmap
    }
}
