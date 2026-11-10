package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.data.db.entity.PageFilter
import com.syedmsidsm.abdulsscanner.domain.imaging.Filters
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class FiltersGoldenTest {

    private fun findSamplesDir(): File {
        val candidates = listOf(
            File("docs/camera-pack/filters/samples"),
            File("../docs/camera-pack/filters/samples"),
            File("../../docs/camera-pack/filters/samples"),
            File(System.getProperty("user.dir"), "docs/camera-pack/filters/samples")
        )
        return candidates.firstOrNull { it.exists() && File(it, "test-photo.jpg").exists() }
            ?: throw IllegalStateException("Could not find docs/camera-pack/filters/samples directory. Tried: $candidates")
    }

    private data class DecodedImage(val width: Int, val height: Int, val pixels: IntArray)

    private fun decodeImageWithJdk(file: File): DecodedImage {
        val imageIOClass = Class.forName("javax.imageio.ImageIO")
        val readMethod = imageIOClass.getMethod("read", File::class.java)
        val bufferedImage = readMethod.invoke(null, file)
            ?: throw IllegalArgumentException("Could not decode image at ${file.absolutePath}")

        val getWidth = bufferedImage.javaClass.getMethod("getWidth")
        val getHeight = bufferedImage.javaClass.getMethod("getHeight")
        val width = getWidth.invoke(bufferedImage) as Int
        val height = getHeight.invoke(bufferedImage) as Int

        val getRGB = bufferedImage.javaClass.getMethod(
            "getRGB",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            IntArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )

        val pixels = IntArray(width * height)
        getRGB.invoke(bufferedImage, 0, 0, width, height, pixels, 0, width)
        return DecodedImage(width, height, pixels)
    }

    @Test
    fun testAllFiltersGoldenImages() {
        val samplesDir = findSamplesDir()
        val testPhotoFile = File(samplesDir, "test-photo.jpg")
        val input = decodeImageWithJdk(testPhotoFile)
        val width = input.width
        val height = input.height
        val srcPixels = input.pixels

        for (filter in PageFilter.entries) {
            val key = when (filter) {
                PageFilter.NO_SHADOW -> "no_shadow"
                PageFilter.ORIGINAL -> "original"
                PageFilter.LIGHTEN -> "lighten"
                PageFilter.ENHANCED -> "enhanced"
                PageFilter.GRAYSCALE -> "grayscale"
                PageFilter.BW -> "bw"
                PageFilter.ECO -> "eco"
            }

            val goldenFile = File(samplesDir, "filter-$key.png")
            assertTrue("Golden file must exist: ${goldenFile.path}", goldenFile.exists())

            val golden = decodeImageWithJdk(goldenFile)
            val goldenPixels = golden.pixels

            val outputPixels = Filters.applyFilter(srcPixels, width, height, filter)

            var diffSum = 0.0
            var maxDiff = 0
            val diffs = IntArray(width * height)
            var mismatchCount = 0

            for (i in 0 until width * height) {
                val cOut = outputPixels[i]
                val cGold = goldenPixels[i]

                val rOut = (cOut shr 16) and 0xFF
                val gOut = (cOut shr 8) and 0xFF
                val bOut = cOut and 0xFF

                val rGold = (cGold shr 16) and 0xFF
                val gGold = (cGold shr 8) and 0xFF
                val bGold = cGold and 0xFF

                val dR = abs(rOut - rGold)
                val dG = abs(gOut - gGold)
                val dB = abs(bOut - bGold)

                val pixDiff = maxOf(dR, dG, dB)
                diffs[i] = pixDiff
                diffSum += (dR + dG + dB) / 3.0
                if (pixDiff > maxDiff) maxDiff = pixDiff
                if (pixDiff > 0) mismatchCount++
            }

            val mad = diffSum / (width * height)
            diffs.sort()
            val p99Index = ((width * height - 1) * 0.99).toInt()
            val p99 = diffs[p99Index]

            println("Filter ${filter.name}: MAD = $mad, P99 = $p99, Max = $maxDiff, Mismatch = ${mismatchCount * 100.0 / (width * height)}%")

            if (filter == PageFilter.BW) {
                val mismatchPct = mismatchCount * 100.0 / (width * height)
                assertTrue("B&W mismatch % must be <= 1%, was $mismatchPct%", mismatchPct <= 1.0)
            } else {
                assertTrue("Filter ${filter.name} MAD must be <= 3, was $mad", mad <= 3.0)
                assertTrue("Filter ${filter.name} P99 must be <= 20, was $p99", p99 <= 20)
            }
        }
    }
}
