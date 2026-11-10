package com.syedmsidsm.abdulsscanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.syedmsidsm.abdulsscanner.domain.imaging.PageDetector
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot

@RunWith(AndroidJUnit4::class)
class PageDetectorGoldenTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpOpenCv() {
            assertTrue("OpenCV failed to initialize", OpenCVLoader.initLocal())
        }
    }

    @Test
    fun testAllSevenGoldenImages() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = testContext.assets

        val jsonString = assets.open("detect-tests/expected.json").bufferedReader().use { it.readText() }
        val root = JSONObject(jsonString)

        val tempDir = File(targetContext.cacheDir, "golden_tests").apply {
            if (!exists()) mkdirs()
        }
        var allPassed = true

        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = root.getJSONObject(key)
            val expectedArray = if (obj.isNull("expected")) null else obj.getJSONArray("expected")

            // Copy image asset to temp file so Imgcodecs can read it cleanly
            val imageFile = File(tempDir, "$key.jpg")
            assets.open("detect-tests/$key.jpg").use { input ->
                FileOutputStream(imageFile).use { output ->
                    input.copyTo(output)
                }
            }

            val bgr = Imgcodecs.imread(imageFile.absolutePath)
            assertNotNull("Failed to load image for $key", bgr)
            assertTrue("Loaded image is empty for $key", bgr.rows() > 0 && bgr.cols() > 0)

            val diag = hypot(bgr.cols().toDouble(), bgr.rows().toDouble())
            val actualCorners = PageDetector.detectPage(bgr)

            if (expectedArray == null) {
                // Scene 07: Expect no page found
                assertNull("Expected null detection for $key, but got $actualCorners", actualCorners)
                println("$key: PASS (no page correctly returned)")
            } else {
                assertNotNull("Expected page detected for $key, but got null", actualCorners)
                val expectedCorners = (0 until 4).map { i ->
                    val ptArr = expectedArray.getJSONArray(i)
                    ptArr.getDouble(0) to ptArr.getDouble(1)
                }

                var maxCornerError = 0.0
                for (i in 0 until 4) {
                    val exp = expectedCorners[i]
                    val act = actualCorners!![i]
                    val dist = hypot(act.x - exp.first, act.y - exp.second)
                    val errPercent = (dist / diag) * 100.0
                    if (errPercent > maxCornerError) {
                        maxCornerError = errPercent
                    }
                }

                println(String.format("%-30s PASS max corner err %.2f%%", key, maxCornerError))
                assertTrue(
                    "Corner error for $key was $maxCornerError% > 2.5% max allowed",
                    maxCornerError <= 2.5
                )
            }

            bgr.release()
            imageFile.delete()
        }

        tempDir.deleteRecursively()
        assertTrue("All golden detection tests must pass", allPassed)
    }
}
