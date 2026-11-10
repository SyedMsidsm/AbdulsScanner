# Abul's Scanner — Fix pack 1: scanning flow, auto-crop, crop screen

**Date:** 2026-11-10 · Based on the owner testing the app on his phone, his screenshots, and the OKEN "photo to PDF" video. This file **overrides** `docs/SPEC.md` and `docs/camera-pack/CAMERA-ADDON.md` for everything it covers.

## 0. What the owner reported

| # | Problem (owner's words, cleaned up) | What the screenshots show |
| --- | --- | --- |
| 1 | After taking a photo it doesn't auto-crop | The crop corners sit at a fixed small inset from the photo edges on every page. Page detection is not running on the captured photo (or its result isn't used) |
| 2 | While cropping, the circle is just green; you can't see the view | The magnifier is a solid green circle with a "+", not a zoomed view of the photo |
| 3 | Placements are wrong | The title "Page 1 · adjust crop" is drawn inside the status bar; the Retake / Rotate / Next buttons are squeezed; the **Done** button has collapsed into a tall empty green bar on the right edge |
| 4 | Tapping **Next** doesn't go to the next page; it goes back or **closes the app** | Crash or wrong navigation (see section 4) |

The fixes below also bring the flow in line with OKEN: **Camera → (crop) → Continue → Review & filter → Done → Document**.

---

## 1. The scanning flow (state machine)

Every way of adding pages ends in the same **Review & filter** screen, then **Done** saves the document.

```
Home ─ camera button ─► CAMERA (Docs mode)
   capture photo ─► save JPEG to the session folder immediately
      ├─ "Crop images after each scan" ON  ─► CROP screen (page n)
      │        Retake  ─► delete photo, back to CAMERA
      │        Rotate  ─► rotate 90°
      │        Next page ─► add page to session, back to CAMERA (count = n)
      │        Done (n pages) ─► add page, go to REVIEW
      └─ OFF ─► add page to session (auto-crop applied), stay in CAMERA (count = n)
   CAMERA with n ≥ 1 pages: thumbnail + count + "Continue" (bottom right) ─► REVIEW
   Import (gallery) ─► selected images become pages (auto-crop applied) ─► REVIEW
   Google scanner (if chosen in Settings) ─► returned pages ─► REVIEW
REVIEW & FILTER ─ Done ─► save document (spec 3.2 pipeline) ─► DOCUMENT screen
```

Back button rules (never leave the app by accident):

| Where | Back does |
| --- | --- |
| CROP | Dialog "Discard this photo?" → Discard (back to CAMERA) / Keep editing |
| CAMERA with 0 pages | Close camera, back to Home |
| CAMERA with ≥ 1 page | Dialog "Save 3 pages?" → **Save** (go to REVIEW) / **Discard** (delete session, Home) / Cancel |
| REVIEW | Back to CAMERA with the same pages (nothing lost) |

### 1.1 The scan session (fixes the "Next closes the app" class of bugs)

- One `ScanSessionViewModel`, **scoped to a nested navigation graph** `scan_flow` that contains CAMERA, CROP and REVIEW (`navController.getBackStackEntry("scan_flow")`). All three screens share it.
- Each captured photo goes straight to disk with `ImageCapture.takePicture(OutputFileOptions)` into `cacheDir/scan-session/{sessionId}/{pageId}.jpg`. The session holds only **ids, file paths, crop corners, rotation and filter**: never `Bitmap`s.
- Save the session list (paths + corners + rotation + filter) in `SavedStateHandle` **and** as `session.json` in the session folder after every change.
- Navigation arguments are **ids only**. Never put a `Bitmap`, byte array or large list in a nav argument, `Bundle` or `SavedStateHandle` (that throws `TransactionTooLargeException` when the app goes to the background or a screen is recreated, which looks exactly like "the app closes").
- **Next page** = `session.addPage(...)` then `navController.popBackStack("camera", inclusive = false)`. Never call `finish()`, never `popBackStack()` twice, never start a new Activity for the crop screen.
- The camera must re-bind when CAMERA becomes visible again (bind in a `DisposableEffect` keyed on the lifecycle / `LaunchedEffect(lifecycleOwner)`, unbind on dispose). Returning from CROP must show a live preview within 1 second.
- After **Done** in REVIEW, move files into the document folder (spec 6) in one transaction, then delete the session folder.
- **Unfinished session:** if the app starts and a `scan-session/*/session.json` with ≥ 1 page exists, show the dialog in `fix-05-recover-session.png`: "Unfinished scan found · You have 3 scanned pages that weren't saved because the app was closed." → **Continue scanning** (open REVIEW with those pages) / **Discard**. Sessions older than 7 days are deleted silently.

### 1.2 Memory rules (crashes on 12 MP photos)

- Never decode a full-resolution photo for display. Decode with `inSampleSize` so the long side is ≤ 2048 px for the CROP/REVIEW screens, and use Coil for thumbnails.
- Full-resolution decoding happens only in the background save/render step, one page at a time (spec 11.2).
- All decoding, detection and filtering run off the main thread (`Dispatchers.Default`); the UI shows a small spinner over the image until ready.

---

## 2. Auto-crop (page edge detection)

### 2.1 When it runs

- **Every captured photo** (built-in camera) and **every imported image**: run `detectPage()` on the **saved photo**, upright (EXIF/rotationDegrees applied), downscaled to 1000 px long side. This result is authoritative.
- The live preview may also run it on analysis frames (≈ 640 px, every 3rd frame) to draw the green outline and drive Auto shutter, but the crop is **never** taken from the preview outline. (Preview and capture have different sizes, aspect ratios and rotations. Mapping between them is the usual cause of wrong or missing crops.)
- With **Auto crop images** ON: use the detected corners as the page's `cropQuad`. OFF: no crop.
- **No page found:** corners exactly at the 4 image corners (no inset), and a short message on the CROP screen: "Couldn't find the page edges. Drag the corners." Never use an arbitrary inset rectangle.

### 2.2 The algorithm (exact)

`detection/reference_detect.py` is the reference. Port it 1:1 to Kotlin with OpenCV for Android (`org.opencv:opencv`, already allowed for phase 8; use 4.x or 5.0.0, whichever builds; only basic `Imgproc`/`Core` calls are used). Summary:

1. Downscale to 1000 px long side (`INTER_AREA`).
2. Make 4 candidate masks:
   - **edges:** Gaussian blur 5×5 → Canny with thresholds 0.66× / 1.33× the median brightness → dilate 5×5 ×2
   - **edges_lo:** CLAHE (clip 3, 8×8 tiles) → blur 7×7 → Canny 20/60 → dilate 5×5 ×2. This catches light paper on a light table
   - **bright:** Otsu threshold → close 9×9 ×2
   - **paper:** "paper-like" pixels (Otsu-bright or V > 200, and saturation < 70) → close 9×9 ×3 → open 5×5
3. For the 8 largest contours of each mask, make a 4-corner shape: convex hull → `approxPolyDP` (1% of perimeter). If that isn't 4 points, take the **4 longest hull edges**, sort them around the centre and **intersect neighbouring edges**. This rebuilds a corner hidden by a shadow, finger or pen.
4. Reject a shape if it covers less than 15% of the photo, isn't convex, has a corner angle outside 45°–135°, hugs 3 or more photo borders (that's the whole frame, not a page), or if **less than 50% of its inside looks like paper** (bright, low saturation).
5. Score = `area fraction × fill² × paperness²` (fill = contour area ÷ shape area). Highest score wins. Corners ordered top-left, top-right, bottom-right, bottom-left, scaled back to full size.

### 2.3 Required tests (golden photos)

`detection/detect-tests/` has 7 test photos and `expected.json` with the true page corners:

| Photo | Situation | Expected |
| --- | --- | --- |
| 01-dark-desk-angled | Receipt on a dark desk, tilted | page found |
| 02-light-table-low-contrast | Receipt on a light beige table | page found |
| 03-hand-shadow-over-corner | Notebook page, hand shadow over a corner | page found |
| 04-busy-carpet | Receipt on a patterned carpet | page found |
| 05-pen-across-edge | Notebook page with a pen across the bottom edge | page found |
| 06-close-up-fills-frame | Page fills ~80% of the photo | page found |
| 07-room-no-page | The owner's own room/laptop photo | **no page** |

Add an instrumented (or Robolectric with OpenCV) test that runs the Kotlin `detectPage()` on each photo: every corner must be within **2.5% of the photo diagonal** of the expected corner, and `07` must return no page. `detection/run_tests.py` shows the check; the Python reference passes all 7. It also got 37 of 40 random new scenes right (the 3 misses had two shadows stacked on the page). Don't tune the numbers to pass; fix the port.

---

## 3. CROP screen (layout and magnifier)

Match `mockups/fix-01-crop-screen.png`.

### 3.1 Layout

- Use edge-to-edge with proper insets: the whole screen is wrapped in `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` (or `statusBarsPadding()` on the top bar and `navigationBarsPadding()` on the bottom bar). **Nothing may draw under the status bar or gesture bar.**
- **Top bar** (56 dp): ✕ (= Back rules above) on the left, centred title "Page {n} · adjust crop".
- **Image area:** `Box(Modifier.weight(1f).fillMaxWidth())`, image fitted inside with 16 dp padding, centred. Compute the fitted image rect (`scale = min(boxW/imgW, boxH/imgH)`, offsets centre it). All handle and outline coordinates are converted with this rect: `screen = rect.topLeft + imagePx × scale`; dragging converts back and **clamps to the image rect**.
- **Bottom bar** (two rows, `#161616` background):
  - Row 1, `Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = CenterVertically)`: **Retake** (tonal button, icon + text), 10 dp gap, **Rotate** (icon-only tonal button), `Spacer(Modifier.weight(1f))`, **Next page →** (filled primary button). Buttons size to their content (no fixed widths, no `weight` on buttons), height 48 dp, text `maxLines = 1`.
  - Row 2: centred text button **Done (n pages)**, where n includes the current page.
- A small pill above the bottom bar: "Page edges found automatically" (or the "Couldn't find…" message from 2.1). It fades out after 3 s.

### 3.2 Handles and magnifier

- 4 corner handles: 22 dp white circles with a 3 dp green (`#13A06F`) ring; touch target 48 dp. The handle being dragged turns green-filled and slightly larger. Optional mid-edge handles may come later; not required.
- Outline: 2.5 dp green line joining the corners, light green fill at 10%. Show it in red and disable Next/Done while the shape is invalid (crossed lines or not convex).
- **Magnifier (only while dragging):** a 116 dp circle with a 3 dp white border and shadow, showing the photo **zoomed 2.6×** around the finger's corner position, with a thin white crosshair at the centre and the two green edges leaving that corner. Draw it with a Compose `Canvas`: `clipPath(circle)` then `drawImage(image, srcOffset = corner − (radius / zoom), srcSize = diameter / zoom, dstSize = diameter)`. Use the same ≤ 2048 px display bitmap, never a solid fill.
- Position: top-left of the image area; if the finger is in the top-left quarter, move it to the top-right. It must never sit under the finger.

---

## 4. "Next" closes the app: required investigation

Before fixing, **find the actual cause** and write it in `CHANGELOG.md`:

1. Reproduce on an emulator: capture 2–3 pages with "Crop images after each scan" ON, tapping Next each time; also with OFF and Continue; also rotate the phone and press Home mid-flow.
2. Read `adb logcat` for `FATAL EXCEPTION`, `TransactionTooLargeException`, `OutOfMemoryError`, `IllegalStateException` (CameraX "use case not bound"), and navigation errors.
3. The usual causes in this kind of flow, all forbidden by section 1.1–1.2: bitmaps passed through navigation or saved state; full-size bitmaps decoded on the main thread; the crop screen as a separate Activity calling `finish()`; popping the back stack twice; CameraX not re-bound after returning; the session ViewModel scoped to the CROP screen so it's destroyed on Next.
4. Add an instrumented UI test that captures (or injects) 3 photos, taps Next twice and Done once, then checks REVIEW shows 3 pages and the app is still running.

### 4.1 Local crash log (so the owner can report problems)

Add `CrashLogger`: in `Application.onCreate`, install a `Thread.setDefaultUncaughtExceptionHandler` that writes the stack trace, app version, Android version and phone model to `filesDir/crash/last-crash.txt` (keep the last 3), then calls the previous handler. Settings → About shows **"Copy last crash report"** (copies the text to the clipboard) and **"Share last crash report"**. Nothing is ever sent anywhere automatically: this is local only and fits the spec's "no crash-reporting services" rule.

---

## 5. CAMERA screen after capturing

Match `mockups/fix-02-camera-continue.png`.

- When the session has ≥ 1 page, the bottom-right area shows the **last page's thumbnail** (46 × 58 dp, white 2 dp border) with a green **count badge**, and the label **Continue** under it. Tapping it opens REVIEW. The Import buttons are hidden while n ≥ 1 (REVIEW has **Add**).
- A pill "{n} pages · keep scanning or tap Continue" shows for 3 s after each capture.
- Auto shutter keeps working between pages (with the no-duplicate rule in CAMERA-ADDON 3.6).

---

## 6. REVIEW & FILTER screen (new; OKEN's "Continue → filter → Done")

Match `mockups/fix-03-review-filters.png`.

- **Top bar:** ← (back to CAMERA, pages kept), title "Review · {n} pages", filled **Done** button on the right.
- **Pager:** one page at a time, swipe left/right, "1 / 3" under it; the page is shown **cropped, rotated and filtered** (a ≤ 2048 px preview; full-size rendering happens on save).
- **Filter strip:** the 7 filters from CAMERA-ADDON 2.1 with live thumbnails of the current page; the selected filter applies to the current page. Switch **"Apply filter to all pages"** (default ON): when on, choosing a filter sets it on every page in the session.
- **Bottom actions:** Crop (opens the CROP screen for this page; its buttons become **Cancel / Apply**), Rotate, Retake (camera for this page only, then back here), Add (camera or gallery; new pages append), Delete (with undo snackbar; deleting the last page asks to discard the scan).
- **Done:** save as a new document (or append to the document the scan was started from, via Document → Add pages), then open the Document screen.

---

## 7. Share sheet

Match `mockups/fix-04-share-sheet.png`. This replaces the share sheet in spec 10.2.

| Row | Subtitle | Action |
| --- | --- | --- |
| Share PDF | `{name}.pdf · {size}` | As spec 11.6 |
| Share JPG | `{n} images · {total size}` | As spec 11.6 |
| **Share as long image** (new) | "All pages joined top to bottom · 1 image" | Stack the rendered pages vertically at the width of the narrowest page (max 1600 px wide), 24 px white gap, JPEG quality 85; if the total height would exceed 20 000 px, scale all pages down to fit |
| Share text | "Text read from the pages" | Disabled until OCR is done |
| Share with password… | "Locked PDF; this document stays as it is" | As spec |
| **Save to Gallery** (new) | "Saves the pages as images in Pictures/Abul's Scanner" | Same as Save JPGs to Pictures (spec 11.6) |

Sizes are calculated when the sheet opens (PDF size from the export file; JPG size = sum of rendered page files). No watermark, no sign-in prompt, no premium badges, ever.

---

## 8. Checks (add to the owner's final test checklist)

1. Photograph a sheet of paper on a table: the CROP screen opens with the corners **on the paper's corners**.
2. Photograph a room with no paper: the corners sit at the photo's corners and the "Couldn't find the page edges" message shows.
3. While dragging a corner, the circle shows a **zoomed view of the photo** with a crosshair, away from your finger.
4. Nothing on the CROP screen is under the status bar or the gesture bar; Retake, Rotate, Next page and Done (n pages) are all fully visible and readable.
5. Scan 3 pages tapping **Next page** each time: you go back to the camera every time; after the third, **Done** opens Review with 3 pages. The app never closes.
6. With "Crop images after each scan" OFF: photos stay in the camera, the thumbnail with the count appears, **Continue** opens Review.
7. In Review: swipe through pages, change filter (all pages change when "Apply filter to all pages" is on), crop, rotate, delete, add; **Done** opens the saved document.
8. Close the app (swipe it away) during a scan with 2 pages, reopen: "Unfinished scan found" offers to continue.
9. Share sheet shows sizes; "Share as long image" and "Save to Gallery" work.
10. Settings → About → "Copy last crash report" works (shows "No crashes recorded" if none).
11. The golden detection tests (2.3) and a UI test for the multi-page flow (4) pass in CI.

---

## 9. Files in this pack

```
docs/fix-pack-1/
  FIXES-1.md                        this file
  mockups/00-fix-overview.png       all screens on one sheet
  mockups/fix-01-crop-screen.png
  mockups/fix-02-camera-continue.png
  mockups/fix-03-review-filters.png
  mockups/fix-04-share-sheet.png
  mockups/fix-05-recover-session.png
  owner-report/                     the owner's 2 screenshots of the bugs
  detection/reference_detect.py     reference page detector (port this)
  detection/run_tests.py            how the golden test checks results
  detection/make_scenes.py          how the test photos were made
  detection/detect-tests/*.jpg      7 test photos
  detection/detect-tests/expected.json
```
