package com.syedmsidsm.abdulsscanner.domain.imaging

import com.syedmsidsm.abdulsscanner.data.db.entity.PageFilter
import java.util.Arrays
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 1:1 Kotlin port of docs/camera-pack/filters/reference_filters.py.
 * Pure functions operating on ARGB IntArray pixels.
 */
object Filters {

    /**
     * Dispatches to the corresponding filter implementation.
     */
    fun applyFilter(pixels: IntArray, width: Int, height: Int, filter: PageFilter): IntArray {
        return when (filter) {
            PageFilter.NO_SHADOW -> applyNoShadow(pixels, width, height)
            PageFilter.ORIGINAL -> applyOriginal(pixels, width, height)
            PageFilter.LIGHTEN -> applyLighten(pixels, width, height)
            PageFilter.ENHANCED -> applyMagicColor(pixels, width, height)
            PageFilter.GRAYSCALE -> applyGrayscale(pixels, width, height)
            PageFilter.BW -> applyBw(pixels, width, height)
            PageFilter.ECO -> applyEco(pixels, width, height)
        }
    }

    // ---------------- Filter Implementations ----------------

    fun applyOriginal(pixels: IntArray, width: Int, height: Int): IntArray {
        return pixels.clone()
    }

    /**
     * No shadow: flat white paper, original ink colours.
     */
    fun applyNoShadow(pixels: IntArray, width: Int, height: Int): IntArray {
        val shadowRemoved = removeShadows(pixels, width, height, target = 245.0f)
        val y = computeLuminance(shadowRemoved, width, height)
        val (lo, hi) = percentileLevels(y, width, height, loPct = 1.0f, hiPct = 90.0f)
        val leveled = applyLevels(shadowRemoved, width, height, lo, max(hi, 200.0f))
        return applyContrast(leveled, width, height, factor = 1.05f, pivot = 160.0f)
    }

    /**
     * Lighten: brighter mid-tones, colours kept, shadows softened (not removed).
     */
    fun applyLighten(pixels: IntArray, width: Int, height: Int): IntArray {
        val y = computeLuminance(pixels, width, height)
        val (lo, hi) = percentileLevels(y, width, height, loPct = 2.0f, hiPct = 98.0f)
        val leveled = applyLevels(pixels, width, height, lo * 0.5f, hi)

        // Gamma lift: 255.0 * (out / 255.0)^0.75
        val gammaLut = FloatArray(256) { i ->
            (255.0 * (i / 255.0).pow(0.75)).toFloat().coerceIn(0f, 255f)
        }
        val gammaRgb = FloatArray(pixels.size * 3)
        for (i in pixels.indices) {
            val r = (leveled[i] shr 16) and 0xFF
            val g = (leveled[i] shr 8) and 0xFF
            val b = leveled[i] and 0xFF
            gammaRgb[i * 3] = gammaLut[r]
            gammaRgb[i * 3 + 1] = gammaLut[g]
            gammaRgb[i * 3 + 2] = gammaLut[b]
        }

        return applySaturation(gammaRgb, width, height, factor = 1.05f)
    }

    /**
     * Magic Color (stored as ENHANCED): shadow-free white paper + punchy colours and dark ink.
     */
    fun applyMagicColor(pixels: IntArray, width: Int, height: Int): IntArray {
        val shadowRemoved = removeShadows(pixels, width, height, target = 245.0f)
        val y = computeLuminance(shadowRemoved, width, height)
        val (lo, hi) = percentileLevels(y, width, height, loPct = 2.0f, hiPct = 90.0f)
        val leveled = applyLevels(shadowRemoved, width, height, lo, max(hi, 200.0f))
        val saturated = applySaturationToFloat(leveled, width, height, factor = 1.35f)
        return applyContrast(saturated, width, height, factor = 1.15f, pivot = 150.0f)
    }

    fun applyEnhanced(pixels: IntArray, width: Int, height: Int): IntArray {
        return applyMagicColor(pixels, width, height)
    }

    /**
     * Grayscale: luminance with auto-levels.
     */
    fun applyGrayscale(pixels: IntArray, width: Int, height: Int): IntArray {
        val y = computeLuminance(pixels, width, height)
        val (lo, hi) = percentileLevels(y, width, height, loPct = 2.0f, hiPct = 98.0f)
        val scale = 255.0f / max(1.0f, hi - lo)
        val out = IntArray(pixels.size)

        for (i in pixels.indices) {
            val a = (pixels[i] shr 24) and 0xFF
            val gray = ((y[i] - lo) * scale).roundToInt().coerceIn(0, 255)
            out[i] = (a shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        return out
    }

    /**
     * B&W: shadow removal, then Bradley-Roth adaptive threshold on luminance (t = 0.20).
     */
    fun applyBw(pixels: IntArray, width: Int, height: Int, t: Float = 0.20f): IntArray {
        val shadowRemoved = removeShadows(pixels, width, height, target = 245.0f)
        val y = computeLuminance(shadowRemoved, width, height)

        val s = max(15, (width / 12) or 1)
        val r = s / 2

        // Integral image of y
        val integral = DoubleArray((width + 1) * (height + 1))
        val stride = width + 1

        for (row in 0 until height) {
            var rowSum = 0.0
            val srcRowOffset = row * width
            val dstRowOffset = (row + 1) * stride
            val prevRowOffset = row * stride
            for (col in 0 until width) {
                rowSum += y[srcRowOffset + col]
                integral[dstRowOffset + (col + 1)] = integral[prevRowOffset + (col + 1)] + rowSum
            }
        }

        val out = IntArray(pixels.size)
        val factor = 1.0 - t

        for (row in 0 until height) {
            val y0 = max(0, row - r)
            val y1 = min(height, row + r + 1)
            val rHeight = y1 - y0

            val y0Stride = y0 * stride
            val y1Stride = y1 * stride
            val rowOffset = row * width

            for (col in 0 until width) {
                val x0 = max(0, col - r)
                val x1 = min(width, col + r + 1)
                val area = rHeight * (x1 - x0)

                val total = integral[y1Stride + x1] - integral[y0Stride + x1] -
                        integral[y1Stride + x0] + integral[y0Stride + x0]

                val pixelVal = y[rowOffset + col]
                val isBlack = (pixelVal * area) <= (total * factor)
                val color = if (isBlack) 0 else 255
                val a = (pixels[rowOffset + col] shr 24) and 0xFF
                out[rowOffset + col] = (a shl 24) or (color shl 16) or (color shl 8) or color
            }
        }
        return out
    }

    /**
     * Eco: ink-saving print version. White paper, text lightened by 45%.
     */
    fun applyEco(pixels: IntArray, width: Int, height: Int): IntArray {
        val shadowRemoved = removeShadows(pixels, width, height, target = 245.0f)
        val y = computeLuminance(shadowRemoved, width, height)
        val (lo, hi) = percentileLevels(y, width, height, loPct = 1.0f, hiPct = 90.0f)
        val scale = 255.0f / max(1.0f, hi - lo)
        val out = IntArray(pixels.size)

        for (i in pixels.indices) {
            val a = (pixels[i] shr 24) and 0xFF
            var yNorm = ((y[i] - lo) * scale).coerceIn(0f, 255f)
            yNorm = 255.0f - (255.0f - yNorm) * 0.55f
            if (yNorm > 225.0f) {
                yNorm = 255.0f
            }
            val gray = yNorm.roundToInt().coerceIn(0, 255)
            out[i] = (a shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        return out
    }

    /**
     * Adjusts brightness (-100..100) and contrast (-100..100).
     */
    fun adjustBrightnessContrast(pixels: IntArray, brightness: Int, contrast: Int): IntArray {
        if (brightness == 0 && contrast == 0) return pixels

        val bOffset = brightness * 1.28f
        val factor = (100f + contrast) / 100f
        val out = IntArray(pixels.size)

        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c shr 24) and 0xFF
            var r = (c shr 16) and 0xFF
            var g = (c shr 8) and 0xFF
            var b = c and 0xFF

            r = ((r - 128) * factor + 128 + bOffset).roundToInt().coerceIn(0, 255)
            g = ((g - 128) * factor + 128 + bOffset).roundToInt().coerceIn(0, 255)
            b = ((b - 128) * factor + 128 + bOffset).roundToInt().coerceIn(0, 255)

            out[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    fun findPercentiles(pixels: IntArray, lowPercentile: Float = 0.02f, highPercentile: Float = 0.98f): Pair<Int, Int> {
        val y = FloatArray(pixels.size) { i ->
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            0.299f * r + 0.587f * g + 0.114f * b
        }
        val (lo, hi) = percentileLevels(y, pixels.size, 1, lowPercentile * 100f, highPercentile * 100f)
        return Pair(lo.roundToInt(), hi.roundToInt())
    }

    // ---------------- Mathematical Helpers ----------------

    private fun computeLuminance(pixels: IntArray, width: Int, height: Int): FloatArray {
        val y = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            y[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        return y
    }

    fun percentileLevels(
        y: FloatArray,
        width: Int,
        height: Int,
        loPct: Float = 2.0f,
        hiPct: Float = 98.0f,
        sampleLongSide: Int = 512
    ): Pair<Float, Float> {
        val step = max(1, max(width, height) / sampleLongSide)
        val sampled = ArrayList<Float>((width / step + 1) * (height / step + 1))
        for (r in 0 until height step step) {
            val rowOffset = r * width
            for (c in 0 until width step step) {
                sampled.add(y[rowOffset + c])
            }
        }
        val arr = sampled.toFloatArray()
        Arrays.sort(arr)
        val n = arr.size
        if (n == 0) return Pair(0f, 255f)

        fun calc(p: Float): Float {
            val idx = (n - 1) * (p / 100.0f)
            val i = idx.toInt()
            val frac = idx - i
            return if (i + 1 < n) {
                arr[i] + frac * (arr[i + 1] - arr[i])
            } else {
                arr[i]
            }
        }

        return Pair(calc(loPct), calc(hiPct))
    }

    private fun applyLevels(
        pixels: IntArray,
        width: Int,
        height: Int,
        low: Float,
        high: Float
    ): IntArray {
        val scale = 255.0f / max(1.0f, high - low)
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c shr 24) and 0xFF
            val r = (((c shr 16 and 0xFF) - low) * scale).roundToInt().coerceIn(0, 255)
            val g = (((c shr 8 and 0xFF) - low) * scale).roundToInt().coerceIn(0, 255)
            val b = (((c and 0xFF) - low) * scale).roundToInt().coerceIn(0, 255)
            out[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    private fun applySaturation(
        rgbFloat: FloatArray,
        width: Int,
        height: Int,
        factor: Float
    ): IntArray {
        val out = IntArray(width * height)
        for (i in 0 until width * height) {
            val r = rgbFloat[i * 3]
            val g = rgbFloat[i * 3 + 1]
            val b = rgbFloat[i * 3 + 2]
            val y = 0.299f * r + 0.587f * g + 0.114f * b

            val rOut = (y + (r - y) * factor).roundToInt().coerceIn(0, 255)
            val gOut = (y + (g - y) * factor).roundToInt().coerceIn(0, 255)
            val bOut = (y + (b - y) * factor).roundToInt().coerceIn(0, 255)

            out[i] = (0xFF shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
        }
        return out
    }

    private fun applySaturationToFloat(
        pixels: IntArray,
        width: Int,
        height: Int,
        factor: Float
    ): FloatArray {
        val out = FloatArray(pixels.size * 3)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16 and 0xFF).toFloat()
            val g = (c shr 8 and 0xFF).toFloat()
            val b = (c and 0xFF).toFloat()
            val y = 0.299f * r + 0.587f * g + 0.114f * b

            out[i * 3] = (y + (r - y) * factor).coerceIn(0f, 255f)
            out[i * 3 + 1] = (y + (g - y) * factor).coerceIn(0f, 255f)
            out[i * 3 + 2] = (y + (b - y) * factor).coerceIn(0f, 255f)
        }
        return out
    }

    private fun applyContrast(
        pixels: IntArray,
        width: Int,
        height: Int,
        factor: Float,
        pivot: Float
    ): IntArray {
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c shr 24) and 0xFF
            val r = (((c shr 16 and 0xFF) - pivot) * factor + pivot).roundToInt().coerceIn(0, 255)
            val g = (((c shr 8 and 0xFF) - pivot) * factor + pivot).roundToInt().coerceIn(0, 255)
            val b = (((c and 0xFF) - pivot) * factor + pivot).roundToInt().coerceIn(0, 255)
            out[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    private fun applyContrast(
        rgbFloat: FloatArray,
        width: Int,
        height: Int,
        factor: Float,
        pivot: Float
    ): IntArray {
        val out = IntArray(width * height)
        for (i in 0 until width * height) {
            val r = ((rgbFloat[i * 3] - pivot) * factor + pivot).roundToInt().coerceIn(0, 255)
            val g = ((rgbFloat[i * 3 + 1] - pivot) * factor + pivot).roundToInt().coerceIn(0, 255)
            val b = ((rgbFloat[i * 3 + 2] - pivot) * factor + pivot).roundToInt().coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    // ---------------- Background & Shadow Removal ----------------

    /**
     * Removes shadows by dividing by background estimate: out = c / max(bg, 1) * 245.
     */
    fun removeShadows(
        pixels: IntArray,
        width: Int,
        height: Int,
        target: Float = 245.0f
    ): IntArray {
        val k = 256.0f / max(width, height)
        val sw = max(1, (width * k).roundToInt())
        val sh = max(1, (height * k).roundToInt())

        // 1. Downscale with area average
        val small = downsampleBox(pixels, width, height, sw, sh)

        // 2. MaxFilter 7x7 (separable radius 3)
        val maxed = maxFilter7(small, sw, sh)

        // 3. BoxBlur radius 6 twice (separable)
        val blurred = boxBlurTwice(maxed, sw, sh, radius = 6)

        // 4. Bilinear upscale & remove shadows in a single memory-efficient pass
        return removeShadowsBilinear(pixels, width, height, blurred, sw, sh, target)
    }

    private fun downsampleBox(
        src: IntArray,
        w: Int,
        h: Int,
        sw: Int,
        sh: Int
    ): FloatArray {
        val out = FloatArray(sh * sw * 3)
        val xScale = w.toFloat() / sw
        val yScale = h.toFloat() / sh

        for (y in 0 until sh) {
            val y0 = y * yScale
            val y1 = (y + 1) * yScale
            val iy0 = y0.toInt()
            val iy1 = min(h - 1, y1.toInt())

            val rowOffset = y * sw * 3

            for (x in 0 until sw) {
                val x0 = x * xScale
                val x1 = (x + 1) * xScale
                val ix0 = x0.toInt()
                val ix1 = min(w - 1, x1.toInt())

                var totalW = 0.0f
                var rSum = 0.0f
                var gSum = 0.0f
                var bSum = 0.0f

                for (py in iy0..iy1) {
                    val wy = min((py + 1).toFloat(), y1) - max(py.toFloat(), y0)
                    if (wy <= 0f) continue
                    val pyOffset = py * w
                    for (px in ix0..ix1) {
                        val wx = min((px + 1).toFloat(), x1) - max(px.toFloat(), x0)
                        if (wx <= 0f) continue
                        val wxy = wx * wy
                        val c = src[pyOffset + px]
                        rSum += ((c shr 16) and 0xFF) * wxy
                        gSum += ((c shr 8) and 0xFF) * wxy
                        bSum += (c and 0xFF) * wxy
                        totalW += wxy
                    }
                }

                val invW = if (totalW > 0f) 1.0f / totalW else 1.0f
                val idx = rowOffset + x * 3
                out[idx] = rSum * invW
                out[idx + 1] = gSum * invW
                out[idx + 2] = bSum * invW
            }
        }
        return out
    }

    private fun maxFilter7(arr: FloatArray, sw: Int, sh: Int): FloatArray {
        val hOut = FloatArray(arr.size)
        val r = 3

        // Horizontal pass
        for (y in 0 until sh) {
            val rowOffset = y * sw * 3
            for (x in 0 until sw) {
                val x0 = max(0, x - r)
                val x1 = min(sw - 1, x + r)

                var maxR = 0f
                var maxG = 0f
                var maxB = 0f

                for (ix in x0..x1) {
                    val idx = rowOffset + ix * 3
                    if (arr[idx] > maxR) maxR = arr[idx]
                    if (arr[idx + 1] > maxG) maxG = arr[idx + 1]
                    if (arr[idx + 2] > maxB) maxB = arr[idx + 2]
                }

                val outIdx = rowOffset + x * 3
                hOut[outIdx] = maxR
                hOut[outIdx + 1] = maxG
                hOut[outIdx + 2] = maxB
            }
        }

        val vOut = FloatArray(arr.size)
        // Vertical pass
        for (y in 0 until sh) {
            val y0 = max(0, y - r)
            val y1 = min(sh - 1, y + r)
            val rowOffset = y * sw * 3

            for (x in 0 until sw) {
                var maxR = 0f
                var maxG = 0f
                var maxB = 0f

                for (iy in y0..y1) {
                    val idx = iy * sw * 3 + x * 3
                    if (hOut[idx] > maxR) maxR = hOut[idx]
                    if (hOut[idx + 1] > maxG) maxG = hOut[idx + 1]
                    if (hOut[idx + 2] > maxB) maxB = hOut[idx + 2]
                }

                val outIdx = rowOffset + x * 3
                vOut[outIdx] = maxR
                vOut[outIdx + 1] = maxG
                vOut[outIdx + 2] = maxB
            }
        }
        return vOut
    }

    private fun boxBlurTwice(arr: FloatArray, sw: Int, sh: Int, radius: Int = 6): FloatArray {
        var curr = arr
        val winSize = 2 * radius + 1
        val invWin = 1.0f / winSize

        for (pass in 0 until 2) {
            // Horizontal pass with edge clamp-extension
            val hOut = FloatArray(curr.size)
            val paddedLen = sw + 2 * radius
            val cumsumR = FloatArray(paddedLen + 1)
            val cumsumG = FloatArray(paddedLen + 1)
            val cumsumB = FloatArray(paddedLen + 1)

            for (y in 0 until sh) {
                val rowOffset = y * sw * 3

                // Left clamp: indices 0 until radius
                val leftR = curr[rowOffset]
                val leftG = curr[rowOffset + 1]
                val leftB = curr[rowOffset + 2]

                // Right clamp: indices (sw + radius) until paddedLen
                val rightR = curr[rowOffset + (sw - 1) * 3]
                val rightG = curr[rowOffset + (sw - 1) * 3 + 1]
                val rightB = curr[rowOffset + (sw - 1) * 3 + 2]

                // Compute cumsum
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f

                var pIdx = 0
                for (i in 0 until radius) {
                    sumR += leftR; sumG += leftG; sumB += leftB
                    cumsumR[pIdx + 1] = sumR
                    cumsumG[pIdx + 1] = sumG
                    cumsumB[pIdx + 1] = sumB
                    pIdx++
                }
                for (x in 0 until sw) {
                    val idx = rowOffset + x * 3
                    sumR += curr[idx]; sumG += curr[idx + 1]; sumB += curr[idx + 2]
                    cumsumR[pIdx + 1] = sumR
                    cumsumG[pIdx + 1] = sumG
                    cumsumB[pIdx + 1] = sumB
                    pIdx++
                }
                for (i in 0 until radius) {
                    sumR += rightR; sumG += rightG; sumB += rightB
                    cumsumR[pIdx + 1] = sumR
                    cumsumG[pIdx + 1] = sumG
                    cumsumB[pIdx + 1] = sumB
                    pIdx++
                }

                for (x in 0 until sw) {
                    val outIdx = rowOffset + x * 3
                    hOut[outIdx] = (cumsumR[x + winSize] - cumsumR[x]) * invWin
                    hOut[outIdx + 1] = (cumsumG[x + winSize] - cumsumG[x]) * invWin
                    hOut[outIdx + 2] = (cumsumB[x + winSize] - cumsumB[x]) * invWin
                }
            }

            // Vertical pass with edge clamp-extension
            val vOut = FloatArray(curr.size)
            val vPaddedLen = sh + 2 * radius
            val vCumsumR = FloatArray(vPaddedLen + 1)
            val vCumsumG = FloatArray(vPaddedLen + 1)
            val vCumsumB = FloatArray(vPaddedLen + 1)

            for (x in 0 until sw) {
                val topR = hOut[x * 3]
                val topG = hOut[x * 3 + 1]
                val topB = hOut[x * 3 + 2]

                val bottomR = hOut[(sh - 1) * sw * 3 + x * 3]
                val bottomG = hOut[(sh - 1) * sw * 3 + x * 3 + 1]
                val bottomB = hOut[(sh - 1) * sw * 3 + x * 3 + 2]

                var sumR = 0f
                var sumG = 0f
                var sumB = 0f

                var pIdx = 0
                for (i in 0 until radius) {
                    sumR += topR; sumG += topG; sumB += topB
                    vCumsumR[pIdx + 1] = sumR
                    vCumsumG[pIdx + 1] = sumG
                    vCumsumB[pIdx + 1] = sumB
                    pIdx++
                }
                for (y in 0 until sh) {
                    val idx = y * sw * 3 + x * 3
                    sumR += hOut[idx]; sumG += hOut[idx + 1]; sumB += hOut[idx + 2]
                    vCumsumR[pIdx + 1] = sumR
                    vCumsumG[pIdx + 1] = sumG
                    vCumsumB[pIdx + 1] = sumB
                    pIdx++
                }
                for (i in 0 until radius) {
                    sumR += bottomR; sumG += bottomG; sumB += bottomB
                    vCumsumR[pIdx + 1] = sumR
                    vCumsumG[pIdx + 1] = sumG
                    vCumsumB[pIdx + 1] = sumB
                    pIdx++
                }

                for (y in 0 until sh) {
                    val outIdx = y * sw * 3 + x * 3
                    vOut[outIdx] = (vCumsumR[y + winSize] - vCumsumR[y]) * invWin
                    vOut[outIdx + 1] = (vCumsumG[y + winSize] - vCumsumG[y]) * invWin
                    vOut[outIdx + 2] = (vCumsumB[y + winSize] - vCumsumB[y]) * invWin
                }
            }
            curr = vOut
        }
        return curr
    }

    private fun removeShadowsBilinear(
        src: IntArray,
        w: Int,
        h: Int,
        bgSmall: FloatArray,
        sw: Int,
        sh: Int,
        target: Float
    ): IntArray {
        // Precalculate X mapping
        val x0Arr = IntArray(w)
        val x1Arr = IntArray(w)
        val fxArr = FloatArray(w)
        val invFxArr = FloatArray(w)

        for (x in 0 until w) {
            val u = (x + 0.5f) * (sw.toFloat() / w) - 0.5f
            val x0 = floor(u).toInt()
            val fx = u - x0
            x0Arr[x] = x0.coerceIn(0, sw - 1)
            x1Arr[x] = (x0 + 1).coerceIn(0, sw - 1)
            fxArr[x] = fx
            invFxArr[x] = 1.0f - fx
        }

        val out = IntArray(w * h)

        for (y in 0 until h) {
            val v = (y + 0.5f) * (sh.toFloat() / h) - 0.5f
            val y0 = floor(v).toInt()
            val fy = v - y0
            val invFy = 1.0f - fy
            val y0C = y0.coerceIn(0, sh - 1)
            val y1C = (y0 + 1).coerceIn(0, sh - 1)

            val r0Offset = y0C * sw * 3
            val r1Offset = y1C * sw * 3
            val srcRowOffset = y * w

            for (x in 0 until w) {
                val x0C = x0Arr[x] * 3
                val x1C = x1Arr[x] * 3
                val fx = fxArr[x]
                val invFx = invFxArr[x]

                val c0R = bgSmall[r0Offset + x0C] * invFx + bgSmall[r0Offset + x1C] * fx
                val c0G = bgSmall[r0Offset + x0C + 1] * invFx + bgSmall[r0Offset + x1C + 1] * fx
                val c0B = bgSmall[r0Offset + x0C + 2] * invFx + bgSmall[r0Offset + x1C + 2] * fx

                val c1R = bgSmall[r1Offset + x0C] * invFx + bgSmall[r1Offset + x1C] * fx
                val c1G = bgSmall[r1Offset + x0C + 1] * invFx + bgSmall[r1Offset + x1C + 1] * fx
                val c1B = bgSmall[r1Offset + x0C + 2] * invFx + bgSmall[r1Offset + x1C + 2] * fx

                val bgR = max(1.0f, c0R * invFy + c1R * fy)
                val bgG = max(1.0f, c0G * invFy + c1G * fy)
                val bgB = max(1.0f, c0B * invFy + c1B * fy)

                val c = src[srcRowOffset + x]
                val a = (c shr 24) and 0xFF
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val outR = ((r / bgR) * target).roundToInt().coerceIn(0, 255)
                val outG = ((g / bgG) * target).roundToInt().coerceIn(0, 255)
                val outB = ((b / bgB) * target).roundToInt().coerceIn(0, 255)

                out[srcRowOffset + x] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }
        return out
    }
}
