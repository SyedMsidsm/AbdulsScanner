package com.syedmsidsm.abdulsscanner.domain.imaging

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 1:1 Kotlin port of docs/fix-pack-1/detection/reference_detect.py.
 *
 * Authoritative document page detector for captured and imported photos.
 */
object PageDetector {

    const val WORK_LONG_SIDE = 1000.0
    const val MIN_AREA_FRACTION = 0.15
    const val MIN_ANGLE = 45.0
    const val MAX_ANGLE = 135.0
    const val MIN_PAPERNESS = 0.50

    data class Candidate(
        val maskName: String,
        val contour: MatOfPoint,
        val quad: List<Point>
    )

    fun orderCorners(pts: List<Point>): List<Point> {
        require(pts.size == 4) { "orderCorners requires exactly 4 points, got ${pts.size}" }
        // s = x + y, d = y - x
        val tl = pts.minByOrNull { it.x + it.y }!!
        val br = pts.maxByOrNull { it.x + it.y }!!
        val tr = pts.minByOrNull { it.y - it.x }!!
        val bl = pts.maxByOrNull { it.y - it.x }!!
        return listOf(tl, tr, br, bl)
    }

    fun cornerAngles(q: List<Point>): List<Double> {
        val angs = ArrayList<Double>(4)
        for (i in 0 until 4) {
            val p0 = q[(i + 3) % 4]
            val p1 = q[i]
            val p2 = q[(i + 1) % 4]
            val v1x = p0.x - p1.x
            val v1y = p0.y - p1.y
            val v2x = p2.x - p1.x
            val v2y = p2.y - p1.y
            val dot = v1x * v2x + v1y * v2y
            val norm1 = hypot(v1x, v1y)
            val norm2 = hypot(v2x, v2y)
            val c = (dot / (norm1 * norm2 + 1e-6)).coerceIn(-1.0, 1.0)
            angs.add(Math.toDegrees(acos(c)))
        }
        return angs
    }

    fun lineIntersection(p1: Point, p2: Point, p3: Point, p4: Point): Point? {
        val x1 = p1.x; val y1 = p1.y
        val x2 = p2.x; val y2 = p2.y
        val x3 = p3.x; val y3 = p3.y
        val x4 = p4.x; val y4 = p4.y
        val den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(den) < 1e-6) return null
        val a = x1 * y2 - y1 * x2
        val b = x3 * y4 - y3 * x4
        val px = (a * (x3 - x4) - (x1 - x2) * b) / den
        val py = (a * (y3 - y4) - (y1 - y2) * b) / den
        return Point(px, py)
    }

    private data class Edge(val p1: Point, val p2: Point, val length: Double)

    fun toQuad(contour: MatOfPoint, w: Double, h: Double): List<Point> {
        val hullIndices = MatOfInt()
        Imgproc.convexHull(contour, hullIndices)
        val contourList = contour.toList()
        val hullPoints = hullIndices.toList().map { contourList[it] }
        hullIndices.release()

        val hullMat2f = MatOfPoint2f(*hullPoints.toTypedArray())
        val peri = Imgproc.arcLength(hullMat2f, true)
        val poly2f = MatOfPoint2f()
        Imgproc.approxPolyDP(hullMat2f, poly2f, 0.01 * peri, true)
        val poly = poly2f.toList()
        poly2f.release()

        fun fallbackMinAreaRect(): List<Point> {
            val rect = Imgproc.minAreaRect(hullMat2f)
            hullMat2f.release()
            val box = Array(4) { Point() }
            rect.points(box)
            return box.toList()
        }

        if (poly.size == 4) {
            hullMat2f.release()
            return poly
        }
        if (poly.size < 4) {
            return fallbackMinAreaRect()
        }

        val n = poly.size
        val edges = (0 until n).map { i ->
            val p1 = poly[i]
            val p2 = poly[(i + 1) % n]
            Edge(p1, p2, hypot(p2.x - p1.x, p2.y - p1.y))
        }
        val top4 = edges.sortedByDescending { it.length }.take(4)

        val cX = poly.map { it.x }.average()
        val cY = poly.map { it.y }.average()

        val sortedEdges = top4.sortedBy { e ->
            val midX = (e.p1.x + e.p2.x) / 2.0
            val midY = (e.p1.y + e.p2.y) / 2.0
            atan2(midY - cY, midX - cX)
        }

        val pts = ArrayList<Point>(4)
        for (i in 0 until 4) {
            val p = lineIntersection(
                sortedEdges[i].p1, sortedEdges[i].p2,
                sortedEdges[(i + 1) % 4].p1, sortedEdges[(i + 1) % 4].p2
            )
            if (p == null || p.x < -0.1 * w || p.x > 1.1 * w || p.y < -0.1 * h || p.y > 1.1 * h) {
                return fallbackMinAreaRect()
            }
            pts.add(p)
        }
        hullMat2f.release()
        return pts
    }

    fun paperness(q: List<Point>, paperMask: Mat): Double {
        val mask = Mat.zeros(paperMask.size(), CvType.CV_8UC1)
        val qInt = MatOfPoint(*q.map { Point(it.x, it.y) }.toTypedArray())
        Imgproc.fillConvexPoly(mask, qInt, Scalar(255.0))
        val insideCount = Core.countNonZero(mask)
        qInt.release()
        if (insideCount == 0) {
            mask.release()
            return 0.0
        }
        val paperInside = Mat()
        Core.bitwise_and(paperMask, mask, paperInside)
        val paperCount = Core.countNonZero(paperInside)
        mask.release()
        paperInside.release()
        return paperCount.toDouble() / insideCount.toDouble()
    }

    fun score(
        qRaw: List<Point>,
        contour: MatOfPoint,
        w: Double,
        h: Double,
        paperMask: Mat
    ): Pair<Double, List<Point>> {
        val ordered = orderCorners(qRaw)
        val q = ordered.map {
            Point(it.x.coerceIn(0.0, w - 1.0), it.y.coerceIn(0.0, h - 1.0))
        }
        val qMat2f = MatOfPoint2f(*q.toTypedArray())
        val area = Imgproc.contourArea(qMat2f)
        qMat2f.release()

        val frac = area / (w * h)
        val qMatOfPoint = MatOfPoint(*q.toTypedArray())
        val isConvex = Imgproc.isContourConvex(qMatOfPoint)
        qMatOfPoint.release()

        if (frac < MIN_AREA_FRACTION || !isConvex) {
            return -1.0 to q
        }

        val angs = cornerAngles(q)
        if (angs.minOrNull()!! < MIN_ANGLE || angs.maxOrNull()!! > MAX_ANGLE) {
            return -1.0 to q
        }

        val m = 0.01 * maxOf(w, h)
        var borderHits = 0
        if (q.minOf { it.x } < m) borderHits++
        if (q.minOf { it.y } < m) borderHits++
        if (q.maxOf { it.x } > w - m) borderHits++
        if (q.maxOf { it.y } > h - m) borderHits++
        if (borderHits >= 3) {
            return -1.0 to q
        }

        val pn = paperness(q, paperMask)
        if (pn < MIN_PAPERNESS) {
            return -1.0 to q
        }

        val contourArea = Imgproc.contourArea(contour)
        val fill = (contourArea / maxOf(area, 1.0)).coerceAtMost(1.0)
        val s = frac * fill * fill * pn * pn
        return s to q
    }

    fun paperMaskOf(small: Mat): Mat {
        val blur = Mat()
        Imgproc.GaussianBlur(small, blur, Size(5.0, 5.0), 0.0)
        val hsv = Mat()
        Imgproc.cvtColor(blur, hsv, Imgproc.COLOR_BGR2HSV)
        val channels = ArrayList<Mat>(3)
        Core.split(hsv, channels)

        val v = channels[2]
        val s = channels[1]

        val vmask = Mat()
        Imgproc.threshold(v, vmask, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

        val v200 = Mat()
        Imgproc.threshold(v, v200, 200.0, 255.0, Imgproc.THRESH_BINARY)
        Core.bitwise_or(vmask, v200, vmask)

        val smask = Mat()
        Imgproc.threshold(s, smask, 70.0, 255.0, Imgproc.THRESH_BINARY_INV)

        val result = Mat()
        Core.bitwise_and(vmask, smask, result)

        blur.release()
        hsv.release()
        channels.forEach { it.release() }
        vmask.release()
        v200.release()
        smask.release()

        return result
    }

    fun candidates(small: Mat): Pair<List<Candidate>, Mat> {
        val h = small.rows().toDouble()
        val w = small.cols().toDouble()
        val gray = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_BGR2GRAY)
        val blur = Mat()
        Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 0.0)
        val k5 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val k9 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))

        // Compute median brightness of blur via 256-bin histogram
        val hist = Mat()
        Imgproc.calcHist(listOf(blur), MatOfInt(0), Mat(), hist, MatOfInt(256), MatOfFloat(0f, 256f))
        val total = (small.rows() * small.cols()) / 2.0
        var cum = 0.0
        var med = 128.0
        for (i in 0 until 256) {
            cum += hist.get(i, 0)[0]
            if (cum >= total) {
                med = i.toDouble()
                break
            }
        }
        hist.release()

        // Mask A: Canny edges
        val cannyEdges = Mat()
        Imgproc.Canny(blur, cannyEdges, maxOf(0.0, 0.66 * med), minOf(255.0, 1.33 * med))
        val edgesMask = Mat()
        Imgproc.dilate(cannyEdges, edgesMask, k5, Point(-1.0, -1.0), 2)
        cannyEdges.release()

        // Mask A2: Low-contrast edges (CLAHE + low thresholds)
        val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
        val eq = Mat()
        clahe.apply(blur, eq)
        val eqBlur = Mat()
        Imgproc.GaussianBlur(eq, eqBlur, Size(7.0, 7.0), 0.0)
        val cannyLo = Mat()
        Imgproc.Canny(eqBlur, cannyLo, 20.0, 60.0)
        val edgesLoMask = Mat()
        Imgproc.dilate(cannyLo, edgesLoMask, k5, Point(-1.0, -1.0), 2)
        eq.release()
        eqBlur.release()
        cannyLo.release()

        // Mask B: Otsu threshold on brightness
        val otsu = Mat()
        Imgproc.threshold(blur, otsu, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        val brightMask = Mat()
        Imgproc.morphologyEx(otsu, brightMask, Imgproc.MORPH_CLOSE, k9, Point(-1.0, -1.0), 2)
        otsu.release()

        // Mask C: paper-like pixels
        val paper = paperMaskOf(small)
        val pm = Mat()
        Imgproc.morphologyEx(paper, pm, Imgproc.MORPH_CLOSE, k9, Point(-1.0, -1.0), 3)
        val paperMask = Mat()
        Imgproc.morphologyEx(pm, paperMask, Imgproc.MORPH_OPEN, k5, Point(-1.0, -1.0), 1)
        pm.release()

        val masks = listOf(
            "edges" to edgesMask,
            "edges_lo" to edgesLoMask,
            "bright" to brightMask,
            "paper" to paperMask
        )

        val out = ArrayList<Candidate>()
        for ((name, m) in masks) {
            val mode = if (name.startsWith("edges")) Imgproc.RETR_LIST else Imgproc.RETR_EXTERNAL
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(m, contours, hierarchy, mode, Imgproc.CHAIN_APPROX_SIMPLE)
            hierarchy.release()
            m.release()

            val kept = HashSet<MatOfPoint>()
            val sorted = contours.sortedByDescending { Imgproc.contourArea(it) }.take(8)
            for (c in sorted) {
                if (Imgproc.contourArea(c) < MIN_AREA_FRACTION * w * h * 0.5) {
                    continue
                }
                val q = toQuad(c, w, h)
                out.add(Candidate(name, c, q))
                kept.add(c)
            }
            for (c in contours) {
                if (c !in kept) {
                    c.release()
                }
            }
        }

        gray.release()
        blur.release()
        k5.release()
        k9.release()

        return out to paper
    }

    /**
     * Authoritative page detector matching reference_detect.py.
     * Takes BGR Mat and returns 4 corners in original pixel coordinates:
     * top-left, top-right, bottom-right, bottom-left.
     * Returns null if no page was found.
     */
    fun detectPage(bgr: Mat): List<Point>? {
        val H = bgr.rows().toDouble()
        val W = bgr.cols().toDouble()
        val k = WORK_LONG_SIDE / maxOf(H, W)
        val small = if (k < 1.0) {
            val resized = Mat()
            Imgproc.resize(
                bgr,
                resized,
                Size((W * k).roundToInt().toDouble(), (H * k).roundToInt().toDouble()),
                0.0,
                0.0,
                Imgproc.INTER_AREA
            )
            resized
        } else {
            bgr.clone()
        }
        val scaleFactor = if (k >= 1.0) 1.0 else k
        val h = small.rows().toDouble()
        val w = small.cols().toDouble()

        var bestScore = 0.0
        var bestQ: List<Point>? = null

        val (cands, paper) = candidates(small)
        try {
            for ((name, c, q) in cands) {
                val (s, oq) = score(q, c, w, h, paper)
                if (s > bestScore) {
                    bestScore = s
                    bestQ = oq
                }
            }
        } finally {
            cands.forEach { it.contour.release() }
            paper.release()
            if (small !== bgr) {
                small.release()
            }
        }

        if (bestQ == null) {
            return null
        }

        val scaledQ = bestQ.map { Point(it.x / scaleFactor, it.y / scaleFactor) }
        return orderCorners(scaledQ)
    }

    /**
     * Smart page detector for real-world photos (open notebooks, indoor shadows, objects near edges).
     * First runs the exact reference detector [detectPage]; if it returns null, runs a multi-mask
     * fallback pass that separates adjacent notebook pages along spine creases and relaxes
     * lighting/border constraints.
     */
    fun detectPageSmart(bgr: Mat): List<Point>? {
        val primary = detectPage(bgr)
        if (primary != null) return primary
        return detectPageFallback(bgr, forceBestEffort = false)
    }

    /**
     * Best-effort page detector that always returns a non-full [Quad] for the "Auto Crop" toggle.
     */
    fun detectPageBestEffortQuad(bgr: Mat): Quad {
        val H = bgr.rows().toFloat().coerceAtLeast(1f)
        val W = bgr.cols().toFloat().coerceAtLeast(1f)
        val pts = detectPageSmart(bgr) ?: detectPageFallback(bgr, forceBestEffort = true)
        if (pts != null) {
            val q = pointsToNormalizedQuad(pts, W, H)
            if (PerspectiveCrop.isValidQuad(q)) {
                return q
            }
        }
        return Quad(
            p0 = Point2D(0.08f, 0.08f),
            p1 = Point2D(0.92f, 0.08f),
            p2 = Point2D(0.92f, 0.92f),
            p3 = Point2D(0.08f, 0.92f)
        )
    }

    private fun relaxedPaperMaskOf(small: Mat): Mat {
        val blur = Mat()
        Imgproc.GaussianBlur(small, blur, Size(5.0, 5.0), 0.0)
        val hsv = Mat()
        Imgproc.cvtColor(blur, hsv, Imgproc.COLOR_BGR2HSV)
        val channels = ArrayList<Mat>(3)
        Core.split(hsv, channels)

        val v = channels[2]
        val s = channels[1]

        val vmask = Mat()
        Imgproc.threshold(v, vmask, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

        val v145 = Mat()
        Imgproc.threshold(v, v145, 145.0, 255.0, Imgproc.THRESH_BINARY)
        Core.bitwise_or(vmask, v145, vmask)

        val smask = Mat()
        Imgproc.threshold(s, smask, 98.0, 255.0, Imgproc.THRESH_BINARY_INV)

        val result = Mat()
        Core.bitwise_and(vmask, smask, result)

        blur.release()
        hsv.release()
        channels.forEach { it.release() }
        vmask.release()
        v145.release()
        smask.release()

        return result
    }

    private fun scoreFallback(
        qRaw: List<Point>,
        contour: MatOfPoint,
        w: Double,
        h: Double,
        paperMask: Mat,
        forceBestEffort: Boolean
    ): Pair<Double, List<Point>> {
        val ordered = orderCorners(qRaw)
        val q = ordered.map {
            Point(it.x.coerceIn(0.0, w - 1.0), it.y.coerceIn(0.0, h - 1.0))
        }
        val qMat2f = MatOfPoint2f(*q.toTypedArray())
        val area = Imgproc.contourArea(qMat2f)
        qMat2f.release()

        val frac = area / (w * h)
        val qMatOfPoint = MatOfPoint(*q.toTypedArray())
        val isConvex = Imgproc.isContourConvex(qMatOfPoint)
        qMatOfPoint.release()

        val minArea = if (forceBestEffort) 0.05 else 0.07
        if (frac < minArea || frac > 0.96 || !isConvex) {
            return -1.0 to q
        }

        val angs = cornerAngles(q)
        val minAng = if (forceBestEffort) 22.0 else 28.0
        val maxAng = if (forceBestEffort) 158.0 else 152.0
        if (angs.minOrNull()!! < minAng || angs.maxOrNull()!! > maxAng) {
            return -1.0 to q
        }

        val m = 0.01 * maxOf(w, h)
        var borderHits = 0
        if (q.minOf { it.x } < m) borderHits++
        if (q.minOf { it.y } < m) borderHits++
        if (q.maxOf { it.x } > w - m) borderHits++
        if (q.maxOf { it.y } > h - m) borderHits++
        if (borderHits >= 4) {
            return -1.0 to q
        }

        val pn = paperness(q, paperMask)
        val minPn = if (forceBestEffort) 0.15 else 0.35
        if (pn < minPn) {
            return -1.0 to q
        }

        val contourArea = Imgproc.contourArea(contour)
        val fill = (contourArea / maxOf(area, 1.0)).coerceIn(0.0, 1.0)
        if (!forceBestEffort && fill < 0.52) {
            return -1.0 to q
        }

        val cx = q.map { it.x }.average()
        val cy = q.map { it.y }.average()
        val distCenter = hypot((cx / w) - 0.5, (cy / h) - 0.5)
        val centerBonus = (1.15 - 0.55 * distCenter).coerceIn(0.65, 1.15)
        val borderWeight = (1.0 - 0.12 * borderHits).coerceAtLeast(0.55)

        val s = frac * fill * fill * maxOf(pn, 0.35) * maxOf(pn, 0.35) * borderWeight * centerBonus
        return s to q
    }

    private fun detectPageFallback(bgr: Mat, forceBestEffort: Boolean): List<Point>? {
        val H = bgr.rows().toDouble()
        val W = bgr.cols().toDouble()
        if (H <= 10.0 || W <= 10.0) return null

        val k = WORK_LONG_SIDE / maxOf(H, W)
        val small = if (k < 1.0) {
            val resized = Mat()
            Imgproc.resize(
                bgr,
                resized,
                Size((W * k).roundToInt().toDouble(), (H * k).roundToInt().toDouble()),
                0.0,
                0.0,
                Imgproc.INTER_AREA
            )
            resized
        } else {
            bgr.clone()
        }
        val scaleFactor = if (k >= 1.0) 1.0 else k
        val h = small.rows().toDouble()
        val w = small.cols().toDouble()

        val gray = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_BGR2GRAY)
        val blur = Mat()
        Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 0.0)

        val k3 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val k5 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val k7 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))

        // Strong edges to cut apart adjacent pages along notebook spine / touching objects
        val canny = Mat()
        Imgproc.Canny(blur, canny, 35.0, 110.0)
        val thinEdges = Mat()
        Imgproc.dilate(canny, thinEdges, k3, Point(-1.0, -1.0), 1)
        canny.release()

        // 1. Otsu split by edges
        val otsu = Mat()
        Imgproc.threshold(blur, otsu, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        val otsuClosed = Mat()
        Imgproc.morphologyEx(otsu, otsuClosed, Imgproc.MORPH_CLOSE, k3, Point(-1.0, -1.0), 1)
        val otsuSplit = Mat()
        Core.subtract(otsuClosed, thinEdges, otsuSplit)
        Imgproc.morphologyEx(otsuSplit, otsuSplit, Imgproc.MORPH_OPEN, k5, Point(-1.0, -1.0), 1)
        otsu.release()
        otsuClosed.release()

        // 2. Relaxed paper mask split by edges
        val paperRelaxed = relaxedPaperMaskOf(small)
        val paperClosed = Mat()
        Imgproc.morphologyEx(paperRelaxed, paperClosed, Imgproc.MORPH_CLOSE, k5, Point(-1.0, -1.0), 1)
        val paperSplit = Mat()
        Core.subtract(paperClosed, thinEdges, paperSplit)
        Imgproc.morphologyEx(paperSplit, paperSplit, Imgproc.MORPH_OPEN, k5, Point(-1.0, -1.0), 1)

        // 3. Paper eroded (breaks narrow bridges like spiral bindings or overlapping pens)
        val paperEroded = Mat()
        Imgproc.erode(paperClosed, paperEroded, k7, Point(-1.0, -1.0), 2)
        Imgproc.dilate(paperEroded, paperEroded, k7, Point(-1.0, -1.0), 2)
        paperClosed.release()

        // 4. Adaptive threshold mask
        val adap = Mat()
        Imgproc.adaptiveThreshold(blur, adap, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 31, 7.0)
        val adapMask = Mat()
        Imgproc.dilate(adap, adapMask, k5, Point(-1.0, -1.0), 1)
        adap.release()

        val masks = listOf(
            "otsu_split" to otsuSplit,
            "paper_split" to paperSplit,
            "paper_eroded" to paperEroded,
            "adap" to adapMask
        )

        val fallbackCandidates = ArrayList<Candidate>()
        for ((name, m) in masks) {
            val mode = if (name == "adap") Imgproc.RETR_LIST else Imgproc.RETR_EXTERNAL
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(m, contours, hierarchy, mode, Imgproc.CHAIN_APPROX_SIMPLE)
            hierarchy.release()
            m.release()

            val kept = HashSet<MatOfPoint>()
            val sorted = contours.sortedByDescending { Imgproc.contourArea(it) }.take(10)
            for (c in sorted) {
                if (Imgproc.contourArea(c) < 0.05 * w * h) {
                    continue
                }
                val q = toQuad(c, w, h)
                fallbackCandidates.add(Candidate(name, c, q))
                kept.add(c)
            }
            for (c in contours) {
                if (c !in kept) c.release()
            }
        }

        // Also include the standard candidates scored with relaxed rules
        val (stdCands, stdPaper) = candidates(small)
        stdPaper.release()
        fallbackCandidates.addAll(stdCands)

        var bestScore = 0.0
        var bestQ: List<Point>? = null

        try {
            for ((_, c, q) in fallbackCandidates) {
                val (s, oq) = scoreFallback(q, c, w, h, paperRelaxed, forceBestEffort)
                if (s > bestScore) {
                    bestScore = s
                    bestQ = oq
                }
            }
        } finally {
            fallbackCandidates.forEach { it.contour.release() }
            paperRelaxed.release()
            thinEdges.release()
            gray.release()
            blur.release()
            k3.release()
            k5.release()
            k7.release()
            if (small !== bgr) {
                small.release()
            }
        }

        if (bestQ == null) return null
        val scaledQ = bestQ.map { Point(it.x / scaleFactor, it.y / scaleFactor) }
        return orderCorners(scaledQ)
    }

    /**
     * Maps corners in pixel space to normalized [Quad] coordinates (0..1).
     */
    fun pointsToNormalizedQuad(pts: List<Point>, imgW: Float, imgH: Float): Quad {
        val ordered = orderCorners(pts)
        return Quad(
            p0 = Point2D((ordered[0].x / imgW).toFloat().coerceIn(0f, 1f), (ordered[0].y / imgH).toFloat().coerceIn(0f, 1f)),
            p1 = Point2D((ordered[1].x / imgW).toFloat().coerceIn(0f, 1f), (ordered[1].y / imgH).toFloat().coerceIn(0f, 1f)),
            p2 = Point2D((ordered[2].x / imgW).toFloat().coerceIn(0f, 1f), (ordered[2].y / imgH).toFloat().coerceIn(0f, 1f)),
            p3 = Point2D((ordered[3].x / imgW).toFloat().coerceIn(0f, 1f), (ordered[3].y / imgH).toFloat().coerceIn(0f, 1f))
        )
    }
}
