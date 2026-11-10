package com.syedmsidsm.abdulsscanner.domain.imaging

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class DetectionStatus {
    SEARCHING,
    DETECTED,
    HOLD_STILL,
    AUTO_CAPTURE_TRIGGERED
}

data class DetectionResult(
    val rawQuad: Quad?,
    val smoothedQuad: Quad?,
    val isStable: Boolean,
    val status: DetectionStatus
)

class DocumentDetector {

    private val recentQuads = ArrayDeque<Quad>()
    private var anchorQuad: Quad? = null
    private var stableStartTime: Long? = null
    private var autoCaptureFired = false

    fun reset() {
        recentQuads.clear()
        anchorQuad = null
        stableStartTime = null
        autoCaptureFired = false
    }

    /**
     * Analyzes an incoming camera frame bitmap (scaled to ~640px long side)
     * and performs edge detection, contour approximation, 5-frame smoothing,
     * and stability detection for auto-capture.
     */
    fun processFrame(bitmap: Bitmap): DetectionResult {
        val detected = detectRawQuad(bitmap)
        val currentTime = System.currentTimeMillis()

        if (detected != null) {
            recentQuads.addLast(detected)
            if (recentQuads.size > 5) {
                recentQuads.removeFirst()
            }
        } else {
            if (recentQuads.isNotEmpty()) {
                recentQuads.removeFirst()
            }
        }

        val smoothed = if (recentQuads.isNotEmpty()) {
            calculateAverageQuad(recentQuads)
        } else null

        if (smoothed == null) {
            anchorQuad = null
            stableStartTime = null
            return DetectionResult(
                rawQuad = null,
                smoothedQuad = null,
                isStable = false,
                status = DetectionStatus.SEARCHING
            )
        }

        // Check stability against anchor quad
        val currentAnchor = anchorQuad
        if (currentAnchor == null) {
            anchorQuad = smoothed
            stableStartTime = currentTime
        } else {
            val maxMovement = maxCornerDistance(currentAnchor, smoothed)
            if (maxMovement < 0.02f) { // movement < 2% of frame
                // Stable
            } else {
                // Moved beyond 2%: reset anchor
                anchorQuad = smoothed
                stableStartTime = currentTime
                autoCaptureFired = false
            }
        }

        val duration = currentTime - (stableStartTime ?: currentTime)
        val isStable = duration >= 1000L

        val status = when {
            isStable && !autoCaptureFired -> {
                autoCaptureFired = true
                DetectionStatus.AUTO_CAPTURE_TRIGGERED
            }
            isStable -> DetectionStatus.HOLD_STILL
            duration >= 300L -> DetectionStatus.HOLD_STILL
            else -> DetectionStatus.DETECTED
        }

        return DetectionResult(
            rawQuad = detected,
            smoothedQuad = smoothed,
            isStable = isStable,
            status = status
        )
    }

    /**
     * OpenCV edge detection pipeline:
     * Grayscale -> Gaussian blur (5x5) -> Canny (50/150) -> Dilate -> findContours
     * -> largest convex 4-point polygon by approxPolyDP (eps = 2% perimeter)
     * with area > 20% of frame.
     */
    fun detectRawQuad(bitmap: Bitmap): Quad? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        val mat = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val dilated = Mat()
        val hierarchy = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val contours = ArrayList<MatOfPoint>()

        try {
            Utils.bitmapToMat(bitmap, mat)
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 50.0, 150.0)
            Imgproc.dilate(edges, dilated, kernel)

            Imgproc.findContours(dilated, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val totalArea = (w * h).toDouble()
            val minArea = totalArea * 0.20 // > 20% of the frame

            var maxArea = 0.0
            var bestPoints: Array<Point>? = null

            for (contour in contours) {
                val contour2f = MatOfPoint2f(*contour.toArray())
                val peri = Imgproc.arcLength(contour2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)

                val points = approx.toArray()
                if (points.size == 4) {
                    val mop = MatOfPoint(*points)
                    if (Imgproc.isContourConvex(mop)) {
                        val area = Imgproc.contourArea(approx)
                        if (area > minArea && area > maxArea) {
                            maxArea = area
                            bestPoints = points
                        }
                    }
                    mop.release()
                }
                contour2f.release()
                approx.release()
            }

            if (bestPoints != null && bestPoints.size == 4) {
                return orderAndNormalizePoints(bestPoints, w.toFloat(), h.toFloat())
            }
        } catch (t: Throwable) {
            // Native error or uninitialized OpenCV in test environment
            return null
        } finally {
            mat.release()
            gray.release()
            blurred.release()
            edges.release()
            dilated.release()
            kernel.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
        return null
    }

    companion object {

        /**
         * Orders 4 points into Top-Left, Top-Right, Bottom-Right, Bottom-Left
         * and normalizes coordinates to 0.0f..1.0f.
         */
        fun orderAndNormalizePoints(pts: Array<Point>, width: Float, height: Float): Quad {
            require(pts.size == 4) { "Must provide exactly 4 points" }

            // Top-left: smallest (x + y)
            // Bottom-right: largest (x + y)
            // Top-right: smallest (y - x)
            // Bottom-left: largest (y - x)

            val p0 = pts.minByOrNull { it.x + it.y } ?: pts[0]
            val p2 = pts.maxByOrNull { it.x + it.y } ?: pts[2]
            val p1 = pts.minByOrNull { it.y - it.x } ?: pts[1]
            val p3 = pts.maxByOrNull { it.y - it.x } ?: pts[3]

            return Quad(
                p0 = Point2D((p0.x / width).toFloat().coerceIn(0f, 1f), (p0.y / height).toFloat().coerceIn(0f, 1f)),
                p1 = Point2D((p1.x / width).toFloat().coerceIn(0f, 1f), (p1.y / height).toFloat().coerceIn(0f, 1f)),
                p2 = Point2D((p2.x / width).toFloat().coerceIn(0f, 1f), (p2.y / height).toFloat().coerceIn(0f, 1f)),
                p3 = Point2D((p3.x / width).toFloat().coerceIn(0f, 1f), (p3.y / height).toFloat().coerceIn(0f, 1f))
            )
        }

        fun calculateAverageQuad(quads: Collection<Quad>): Quad {
            if (quads.isEmpty()) return Quad.fullQuad()
            val n = quads.size.toFloat()
            var p0x = 0f; var p0y = 0f
            var p1x = 0f; var p1y = 0f
            var p2x = 0f; var p2y = 0f
            var p3x = 0f; var p3y = 0f

            for (q in quads) {
                p0x += q.p0.x; p0y += q.p0.y
                p1x += q.p1.x; p1y += q.p1.y
                p2x += q.p2.x; p2y += q.p2.y
                p3x += q.p3.x; p3y += q.p3.y
            }

            return Quad(
                p0 = Point2D(p0x / n, p0y / n),
                p1 = Point2D(p1x / n, p1y / n),
                p2 = Point2D(p2x / n, p2y / n),
                p3 = Point2D(p3x / n, p3y / n)
            )
        }

        fun maxCornerDistance(a: Quad, b: Quad): Float {
            fun dist(pA: Point2D, pB: Point2D): Float = hypot(pA.x - pB.x, pA.y - pB.y)
            return max(
                max(dist(a.p0, b.p0), dist(a.p1, b.p1)),
                max(dist(a.p2, b.p2), dist(a.p3, b.p3))
            )
        }
    }
}
