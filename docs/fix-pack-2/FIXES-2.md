# Abul's Scanner — Fix pack 2: capture guidance, portrait lock, screenshots, crop & screens

**Date:** 2026-11-10 · Based on the owner testing the updated app and the OKEN reference screenshots. This file **overrides** `docs/SPEC.md`, `docs/camera-pack/CAMERA-ADDON.md` and `docs/fix-pack-1/FIXES-1.md` for everything it covers.

## 0. What the owner asked for

| # | Request |
| --- | --- |
| 1 | While aiming, the camera should guide distance ("move closer") and try to detect the page live, then after capture auto-crop or let me adjust the corners (OKEN crop screen) → review with filters → document screen → (check button) select-mode actions |
| 2 | Tilting the phone 90° must NOT rotate the app into a broken sideways layout |
| 3 | When a screenshot was just taken, offer it on the home screen (like OKEN's "New screenshot" card) |

---

## 1. Live capture guidance (phase 8 camera)

Match `mockups/f2-01-searching.png`, `f2-02-move-closer.png`, `f2-03-hold-still.png`.

While the Docs / Book / ID Card camera is live, run the page detector (fix pack 1 `detectPage`) on the analysis frames (~640 px, every 3rd frame, already wired for Auto shutter) and show **one** centred hint pill that reflects the state. Port `guidance/reference_guidance.py` 1:1 to `domain/scan/CaptureGuidance.kt`.

### 1.1 States and thresholds

`area` = detected page quad area ÷ frame area. Thresholds:

| State | Condition | Pill text |
| --- | --- | --- |
| SEARCHING | no page found for ≥ 4 frames | "Searching for a page…" |
| MOVE_CLOSER | page found, `area < 0.25` | "Move closer" |
| MOVE_FARTHER | `area > 0.92` (edges likely cut off) | "Move farther" |
| HOLD_STILL | `0.25 ≤ area ≤ 0.92`, still settling | "Hold still…" |
| CAPTURING | steady (corners moved < 2% of frame diagonal) for ≥ 1000 ms | "Capturing…" → auto-capture |

- After an auto-capture, a 1500 ms cooldown prevents capturing the same page twice (as in CAMERA-ADDON 3.6).
- The green page outline is drawn whenever a page is detected, in every state except SEARCHING.
- **Auto shutter OFF** (CAMERA-ADDON setting): still show the hints (they help framing), but never auto-capture; only the shutter button captures.
- The manual shutter always works, in any state.
- QR mode shows none of this (it has its own scanning box).

### 1.2 Required test

`guidance/reference_guidance.py` has the logic. Add a unit test for `CaptureGuidance` that feeds a sequence of quads (none → small → growing → large & steady) and asserts: SEARCHING while none, MOVE_CLOSER while small, exactly one capture after ~1 s steady, and no second capture during the cooldown. The Python reference passes this.

---

## 2. Portrait lock (fixes the tilt bug)

The owner's screenshots show the camera rotating into a stretched sideways layout when the phone is tilted. Match `mockups/f2-05-portrait-lock.png` (illustration of the fix).

- **Lock the whole app to portrait.** In `AndroidManifest.xml`, on the single activity:
  `android:screenOrientation="portrait"` and include `orientation` in `android:configChanges` (`android:configChanges="orientation|screenSize|keyboardHidden|uiMode"`) so a tilt never recreates or relayouts the activity.
- The camera preview must use the **device's natural (portrait) orientation** for its target rotation (`Preview.setTargetRotation(Surface.ROTATION_0)` / the display rotation), and `ImageCapture` likewise, so captured photos are upright regardless of how the phone is held.
- Keep reading the **accelerometer only** to decide the saved photo's orientation metadata if needed (so a sideways-held document still saves upright), but the UI itself never rotates.
- This matches OKEN, which is portrait-only.
- Check on the owner's phone: open the camera, tilt the phone fully left and right — the controls, mode slider and preview stay upright and correctly shaped; a captured page is upright.

---

## 3. Screenshot card on Home (new; needs photo permission)

Match `mockups/f2-06-screenshot-card.png`. The owner approved asking for photo access the first time, with an in-app explanation before Android's dialog.

### 3.1 Behaviour

- The newest image in the device's **Screenshots** folder, if taken in the **last 10 minutes** and not already dismissed or scanned, is offered as a small card at the bottom-right of Home: the thumbnail, "New screenshot", "Tap to scan it", and a ✕ to dismiss.
- **Tap** → start the scan flow at the Review & filter screen (fix pack 1 §6) with that screenshot as a single page (no camera, no crop needed, but Crop is available there). From there, Done saves it like any scan.
- **✕** → dismiss; remember its id so it is not offered again (`dismissedScreenshotIds`, keep the last 50).
- Only one card at a time, and only the single newest qualifying screenshot. It never covers the camera button (sits above it).

### 3.2 Permission (the only new permission in the whole app)

- Android 14+: `READ_MEDIA_VISUAL_USER_SELECTED` or `READ_MEDIA_IMAGES`; Android 13: `READ_MEDIA_IMAGES`; Android 8–12: `READ_EXTERNAL_STORAGE` (maxSdkVersion 32). Declare them with the right `maxSdkVersion` bounds.
- **Ask-once flow:** the first time Home is shown after this update, show a small one-time card (not a blocking dialog): "Show new screenshots here? Abul's Scanner can offer a screenshot to scan right after you take one. It only reads your newest screenshot, nothing else." with **Allow** and **No thanks**. Allow → Android's permission dialog. No thanks → never ask again (store `screenshotFeatureDeclined = true`); the feature stays off and can be turned on later in Settings.
- Add a **Settings → Scanning** row "Offer new screenshots" (switch). Off by default until granted; turning it on triggers the permission request.
- If permission is denied, the feature is simply off; never nag. No other part of the app uses this permission.
- Use the photo picker (`PickVisualMedia`) for the existing **Import** everywhere else, which still needs no permission — only this always-watching card needs it.

### 3.3 How it reads the newest screenshot

- Query `MediaStore.Images` with `RELATIVE_PATH LIKE '%Screenshots%'` (API 29+) or `DATA LIKE '%Screenshots%'` (older), ordered by `DATE_ADDED DESC`, limit 1. Check `DATE_ADDED` is within 10 minutes.
- Observe `MediaStore` with a `ContentObserver` while Home is resumed, so a screenshot taken with the app in the background appears when the user returns.
- Never copy or upload anything; the image is read only when the user taps the card.

---

## 4. Crop screen: OKEN-style refinements

Match `mockups/f2-04-crop-okenstyle.png`. This refines the crop screen from fix pack 1 §3 (all of that still applies: real magnifier, correct insets, no drawing under the status/gesture bars).

- Add **mid-edge handles** (one on the middle of each edge) besides the 4 corner handles. Dragging a mid-edge handle moves that whole edge parallel; dragging a corner moves just that corner. 8 handles total.
- Add an **"Auto Crop"** toggle at the bottom-left of the action row (a radio/checkbox, green when on, default on). When on, the detected page corners are used (and shown); the user can still drag. Turning it off resets the handles to the four image corners (whole photo) so the user can crop fully by hand.
- Action row (one line): **Auto Crop** toggle (left), then a spacer, **Retake** (text button) and **Continue** (filled primary) on the right. Use the word **Continue** (not "Next page") to match OKEN; its behaviour is still "save this page, go to the next step" per fix pack 1 §1.
- Helper line under the image: "Drag the dots to the page corners, or adjust later with the crop tool".
- Everything else (crash-safety, session, memory rules) stays exactly as fix pack 1.

---

## 5. Document screen

Match `mockups/f2-07-document.png`. This replaces the Document bottom bar in spec 10.2.

- Top bar: back, document name (tap or ✎ to rename), and a **check/select** icon (enters page-select mode, §6).
- Page grid (2 columns) with page-number badges; last tile "Tap 📷➕ to add new pages".
- **Bottom bar (5 actions):** **Add** (scan/import more pages), **Collage** (make a collage from this doc's pages, spec 11.10), **Share** (the share sheet from fix pack 1 §7), **Edit PDF**, **More**.
  - **Edit PDF** opens the **page editor** (crop, rotate, filter, signature, watermark, reorder) — i.e. our existing editing tools, not text-in-PDF editing (which stays out of scope, spec 18). Label it "Edit PDF" to match OKEN, but it is the page editor.
  - **More:** Save PDF to…, Save JPGs, Print, Password, Compress, Page size, Lock/Unlock, Book split, Move to recycle bin.

---

## 6. Page-select mode (inside a document)

Match `mockups/f2-08-select-actions.png` and `f2-09-select-more.png`.

- Entered from the check icon on the Document screen (or long-press a page). Top bar: back, "n selected", "Deselect all" / "Select all".
- Each page shows a checkmark; tap to toggle.
- **Bottom bar (5 actions):** **Share** (selected pages as PDF/JPG), **Save to Gallery** (selected pages → `Pictures/Abul's Scanner`), **Move** (to another document, or a new one), **Delete** (selected pages; undo snackbar), **More**.
  - **More → Copy** (duplicate the selected pages in this document), **Collage** (collage from the selected pages).
- This is the document-level select mode. The home-screen select mode (whole documents) stays as in spec 10.1.

---

## 7. Checks (add to the owner's final test checklist)

1. Aim at a page far away: "Move closer"; bring it closer: the green outline appears and it says "Hold still…", then auto-captures once.
2. With a messy background and no page, it says "Searching for a page…" and does not auto-capture.
3. Tilt the phone 90° left and right in the camera: the screen stays upright and correctly shaped (no sideways stretch); a captured page is upright.
4. Take a screenshot, open the app: the "New screenshot" card appears bottom-right; tapping it opens Review with that image; ✕ dismisses it and it doesn't come back.
5. First launch after the update shows the one-time "Show new screenshots here?" card; No thanks disables it; Settings → Scanning can turn it back on.
6. Crop screen has 8 handles; dragging a mid-edge moves the whole edge; Auto Crop toggle off gives the whole photo; Continue proceeds.
7. Document screen shows Add / Collage / Share / Edit PDF / More; Edit PDF opens the page editor.
8. Page-select mode shows Share / Save to Gallery / Move / Delete / More (Copy, Collage) and each works.
9. The guidance unit test (1.2) passes in CI.

---

## 8. Files in this pack

```
docs/fix-pack-2/
  FIXES-2.md                           this file
  mockups/00-fix2-overview.png         all screens on one sheet
  mockups/f2-01-searching.png ... f2-09-select-more.png
  owner-report/                        the owner's 10 screenshots (bugs + OKEN references)
  guidance/reference_guidance.py       capture-guidance state machine (port this)
  guidance/reference_detect.py         the page detector it uses (same as fix pack 1)
```

No new libraries. The only new **permission** is photo access for the screenshot card (§3.2), which the owner approved.
