package com.syedmsidsm.abdulsscanner.domain.scan

import com.syedmsidsm.abdulsscanner.domain.imaging.Point2D
import com.syedmsidsm.abdulsscanner.domain.imaging.Quad
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Live capture-guidance state machine (Abul's Scanner, phase 8 camera).
 * 1:1 Kotlin port of reference_guidance.py.
 *
 * Runs on each analysis frame (~640 px) while the camera is live.
 * Decides the on-screen guidance hint and whether to auto-capture.
 */
enum class GuidanceHint(val text: String) {
    SEARCHING("Searching for a page…"),
    MOVE_CLOSER("Move closer"),
    MOVE_FARTHER("Move farther"),
    HOLD_STILL("Hold still…"),
    CAPTURING("Capturing…")
}

data class GuidanceState(
    var hint: GuidanceHint = GuidanceHint.SEARCHING,
    var quad: Quad? = null,
    var steadySinceMs: Long? = null,
    var lastCaptureMs: Long = -1000000000L,
    var lostFrames: Int = 0,
    var prevQuad: Quad? = null
)

class CaptureGuidance(
    val tooFar: Float = TOO_FAR,
    val tooClose: Float = TOO_CLOSE,
    val steadyMs: Long = STEADY_MS,
    val moveTol: Float = MOVE_TOL,
    val cooldownMs: Long = COOLDOWN_MS,
    val lostFramesThreshold: Int = LOST_FRAMES
) {
    companion object {
        const val TOO_FAR = 0.25f       // below this -> "Move closer"
        const val TOO_CLOSE = 0.92f     // above this -> "Move farther" (edges likely cut off)
        const val STEADY_MS = 1000L     // must stay steady this long before auto-capture
        const val MOVE_TOL = 0.02f      // max corner movement (fraction of frame diagonal) to count as steady
        const val COOLDOWN_MS = 1500L   // after a capture, ignore the same page this long
        const val LOST_FRAMES = 4       // consecutive frames with no page before going back to "Searching"

        fun polygonArea(quad: Quad): Float {
            val p0 = quad.p0
            val p1 = quad.p1
            val p2 = quad.p2
            val p3 = quad.p3
            val cross = (p0.x * p1.y - p0.y * p1.x) +
                    (p1.x * p2.y - p1.y * p2.x) +
                    (p2.x * p3.y - p2.y * p3.x) +
                    (p3.x * p0.y - p3.y * p0.x)
            return 0.5f * abs(cross)
        }

        fun cornerMove(a: Quad?, b: Quad?, diag: Float): Float {
            if (a == null || b == null || diag <= 0f) return 1e9f
            val d0 = hypot(a.p0.x - b.p0.x, a.p0.y - b.p0.y)
            val d1 = hypot(a.p1.x - b.p1.x, a.p1.y - b.p1.y)
            val d2 = hypot(a.p2.x - b.p2.x, a.p2.y - b.p2.y)
            val d3 = hypot(a.p3.x - b.p3.x, a.p3.y - b.p3.y)
            val maxDist = max(max(d0, d1), max(d2, d3))
            return maxDist / diag
        }
    }

    /**
     * Advance the state machine one frame.
     * @param state Mutable guidance state
     * @param quad Detected quad in frame pixel coordinates (or null if no page found)
     * @param frameW Frame width in pixels
     * @param frameH Frame height in pixels
     * @param nowMs Current timestamp in milliseconds
     * @param autoShutterEnabled Whether auto-shutter capture is enabled
     * @return Pair of updated state and boolean shouldCapture (true exactly on trigger)
     */
    fun update(
        state: GuidanceState,
        quad: Quad?,
        frameW: Float,
        frameH: Float,
        nowMs: Long,
        autoShutterEnabled: Boolean = true
    ): Pair<GuidanceState, Boolean> {
        val diag = hypot(frameW, frameH)
        val frameArea = frameW * frameH

        // In cooldown right after a capture: show nothing actionable
        if (nowMs - state.lastCaptureMs < cooldownMs) {
            state.hint = if (quad != null) GuidanceHint.HOLD_STILL else GuidanceHint.SEARCHING
            state.prevQuad = quad
            return Pair(state, false)
        }

        if (quad == null) {
            state.lostFrames += 1
            if (state.lostFrames >= lostFramesThreshold) {
                state.hint = GuidanceHint.SEARCHING
                state.quad = null
                state.steadySinceMs = null
            }
            state.prevQuad = null
            return Pair(state, false)
        }

        state.lostFrames = 0
        state.quad = quad
        val areaFrac = if (frameArea > 0f) polygonArea(quad) / frameArea else 0f

        if (areaFrac < tooFar) {
            state.hint = GuidanceHint.MOVE_CLOSER
            state.steadySinceMs = null
            state.prevQuad = quad
            return Pair(state, false)
        }

        if (areaFrac > tooClose) {
            state.hint = GuidanceHint.MOVE_FARTHER
            state.steadySinceMs = null
            state.prevQuad = quad
            return Pair(state, false)
        }

        // Good size -> check steadiness
        val moved = cornerMove(quad, state.prevQuad, diag)
        state.prevQuad = quad

        if (moved > moveTol) {
            state.hint = GuidanceHint.HOLD_STILL
            state.steadySinceMs = nowMs
            return Pair(state, false)
        }

        if (state.steadySinceMs == null) {
            state.steadySinceMs = nowMs
        }
        state.hint = GuidanceHint.HOLD_STILL

        if (autoShutterEnabled && (nowMs - state.steadySinceMs!! >= steadyMs)) {
            state.hint = GuidanceHint.CAPTURING
            state.lastCaptureMs = nowMs
            state.steadySinceMs = null
            return Pair(state, true)
        }

        return Pair(state, false)
    }
}
