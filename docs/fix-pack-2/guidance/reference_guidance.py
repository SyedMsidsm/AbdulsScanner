#!/usr/bin/env python3
"""Reference for the live capture-guidance state machine (Abul's Scanner, phase 8 camera).

Runs on each analysis frame while the Docs/Book/ID camera is live. Decides the on-screen hint and
whether to auto-capture. Port 1:1 to Kotlin (domain/scan/CaptureGuidance.kt), driven by the page
detector from fix pack 1 (detect_page on the ~640 px analysis frame).

Design goal: match OKEN's behaviour the owner showed -- "Searching doc..." when no page is seen,
"Please move closer" when the page is too small/far, then auto-capture when it's big and steady.
"""
from dataclasses import dataclass, field
import numpy as np

# Thresholds are fractions of the FRAME area that the detected page quad covers.
TOO_FAR   = 0.25     # below this -> "Move closer"
TOO_CLOSE = 0.92     # above this -> "Move farther" (edges likely cut off)
STEADY_MS = 1000     # must stay steady this long before auto-capture
MOVE_TOL  = 0.02     # max corner movement (fraction of frame diagonal) to count as "steady"
COOLDOWN_MS = 1500   # after a capture, ignore the same page this long
LOST_FRAMES = 4      # consecutive frames with no page before going back to "Searching"


@dataclass
class GuidanceState:
    hint: str = "SEARCHING"          # SEARCHING | MOVE_CLOSER | MOVE_FARTHER | HOLD_STILL | CAPTURING
    quad: object = None              # last detected quad (4x2) or None
    steady_since_ms: float = None
    last_capture_ms: float = -1e9
    _lost: int = 0
    _prev_quad: object = None

    HINT_TEXT = {
        "SEARCHING": "Searching for a page…",
        "MOVE_CLOSER": "Move closer",
        "MOVE_FARTHER": "Move farther",
        "HOLD_STILL": "Hold still…",
        "CAPTURING": "Capturing…",
    }


def _corner_move(a, b, diag):
    if a is None or b is None:
        return 1e9
    a = np.asarray(a, float); b = np.asarray(b, float)
    return float(np.linalg.norm(a - b, axis=1).max()) / diag


def update(state: GuidanceState, quad, frame_w, frame_h, now_ms,
           auto_shutter_enabled=True):
    """Advance the state machine one frame. Returns (state, should_capture: bool)."""
    diag = float(np.hypot(frame_w, frame_h))
    frame_area = float(frame_w * frame_h)

    # in cooldown right after a capture: show nothing actionable
    if now_ms - state.last_capture_ms < COOLDOWN_MS:
        state.hint = "HOLD_STILL" if quad is not None else "SEARCHING"
        state._prev_quad = quad
        return state, False

    if quad is None:
        state._lost += 1
        if state._lost >= LOST_FRAMES:
            state.hint = "SEARCHING"
            state.quad = None
            state.steady_since_ms = None
        state._prev_quad = None
        return state, False

    state._lost = 0
    state.quad = quad
    import cv2
    area_frac = abs(cv2.contourArea(np.asarray(quad, np.float32))) / frame_area

    if area_frac < TOO_FAR:
        state.hint = "MOVE_CLOSER"; state.steady_since_ms = None
        state._prev_quad = quad
        return state, False
    if area_frac > TOO_CLOSE:
        state.hint = "MOVE_FARTHER"; state.steady_since_ms = None
        state._prev_quad = quad
        return state, False

    # good size -> check steadiness
    moved = _corner_move(quad, state._prev_quad, diag)
    state._prev_quad = quad
    if moved > MOVE_TOL:
        state.hint = "HOLD_STILL"; state.steady_since_ms = now_ms
        return state, False
    if state.steady_since_ms is None:
        state.steady_since_ms = now_ms
    state.hint = "HOLD_STILL"
    if auto_shutter_enabled and now_ms - state.steady_since_ms >= STEADY_MS:
        state.hint = "CAPTURING"
        state.last_capture_ms = now_ms
        state.steady_since_ms = None
        return state, True
    return state, False
