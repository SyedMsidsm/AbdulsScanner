package com.syedmsidsm.abdulsscanner.domain.scan

import com.syedmsidsm.abdulsscanner.domain.imaging.Point2D
import com.syedmsidsm.abdulsscanner.domain.imaging.Quad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureGuidanceTest {

    private fun makeQuad(x: Float, y: Float, w: Float, h: Float): Quad {
        return Quad(
            Point2D(x, y),
            Point2D(x + w, y),
            Point2D(x + w, y + h),
            Point2D(x, y + h)
        )
    }

    @Test
    fun testCaptureGuidanceSequence() {
        val guidance = CaptureGuidance()
        val state = GuidanceState()
        val frameW = 640f
        val frameH = 480f

        var timeMs = 1000L

        // 1. None: feed null quad for >= 4 frames -> SEARCHING
        for (i in 0 until 5) {
            val (updatedState, capture) = guidance.update(state, null, frameW, frameH, timeMs)
            assertFalse("Should not capture when no page found", capture)
            assertEquals(GuidanceHint.SEARCHING, updatedState.hint)
            timeMs += 100L
        }

        // 2. Small: quad covering ~10% of frame area (< 0.25) -> MOVE_CLOSER
        // Frame area = 640 * 480 = 307,200. 10% is ~30,720 (e.g. 180 x 170)
        val smallQuad = makeQuad(100f, 100f, 180f, 170f)
        for (i in 0 until 5) {
            val (updatedState, capture) = guidance.update(state, smallQuad, frameW, frameH, timeMs)
            assertFalse("Should not capture when too small", capture)
            assertEquals(GuidanceHint.MOVE_CLOSER, updatedState.hint)
            timeMs += 100L
        }

        // 3. Too large: quad covering 95% of frame area (> 0.92) -> MOVE_FARTHER
        val hugeQuad = makeQuad(10f, 10f, 620f, 460f)
        val (hugeState, hugeCapture) = guidance.update(state, hugeQuad, frameW, frameH, timeMs)
        assertFalse("Should not capture when too close/large", hugeCapture)
        assertEquals(GuidanceHint.MOVE_FARTHER, hugeState.hint)
        timeMs += 100L

        // 4. Good size: quad covering ~50% of frame area (e.g. 450 x 340 = 153,000)
        // First feed with jitter -> HOLD_STILL, steady timer resets
        val baseQuad = makeQuad(100f, 70f, 450f, 340f)
        val jitterQuad = makeQuad(125f, 95f, 450f, 340f) // moved > 2% diagonal

        val (stateJitter, capJitter) = guidance.update(state, jitterQuad, frameW, frameH, timeMs)
        assertFalse(capJitter)
        assertEquals(GuidanceHint.HOLD_STILL, stateJitter.hint)
        timeMs += 100L

        // Now hold steady: feed exact baseQuad repeatedly for 1 second (10 x 100ms)
        var captureOccurred = false
        var captureCount = 0

        for (i in 0 until 12) {
            val (updatedState, capture) = guidance.update(state, baseQuad, frameW, frameH, timeMs)
            if (capture) {
                captureOccurred = true
                captureCount++
                assertEquals(GuidanceHint.CAPTURING, updatedState.hint)
            }
            timeMs += 100L
        }

        assertTrue("Auto capture must trigger after 1000ms steady", captureOccurred)
        assertEquals("Exactly one capture must trigger", 1, captureCount)

        // 5. Cooldown: feed baseQuad during next 1400 ms (< 1500 ms cooldown)
        for (i in 0 until 10) {
            val (updatedState, capture) = guidance.update(state, baseQuad, frameW, frameH, timeMs)
            assertFalse("No second capture allowed during cooldown", capture)
            assertEquals(GuidanceHint.HOLD_STILL, updatedState.hint)
            timeMs += 100L
        }

        // 6. When auto-shutter is disabled, hints still show but capture never triggers
        timeMs += 2000L // pass cooldown
        val manualGuidanceState = GuidanceState()
        for (i in 0 until 15) {
            val (updatedState, capture) = guidance.update(
                manualGuidanceState,
                baseQuad,
                frameW,
                frameH,
                timeMs,
                autoShutterEnabled = false
            )
            assertFalse("Capture must never trigger when auto shutter is disabled", capture)
            assertEquals(GuidanceHint.HOLD_STILL, updatedState.hint)
            timeMs += 100L
        }
    }
}
