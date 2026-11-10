# Changelog

All notable changes to **Abul's Scanner** will be documented in this file.

## [1.0.5] - 2026-11-11

### Fixed & Improved
- **Smooth, Zero-Jitter Crop Handle Dragging (`CropAfterScanView.kt` & `CropView.kt`)**:
  - Fixed a critical Jetpack Compose gesture bug where `quad` was passed as a restart key to `.pointerInput(imageRect, quad)`. Every single frame `onDrag` updated `quad`, Compose cancelled and restarted the `pointerInput` coroutine, terminating `detectDragGestures` mid-drag and causing severe stickiness and jitter.
  - Changed to `.pointerInput(imageRect)` so `detectDragGestures` runs continuously for the entire drag gesture at full 60–120Hz frame rate.
  - Switched corner handles (`0..3`) to delta-based dragging (`baseQuad + delta` from `dragStartPos`), eliminating any sudden jump when grabbing within the `48.dp` handle touch radius and keeping the 2.6× magnifier crosshair locked on the exact corner dot.
- **Smart Auto-Cropping & Working "Auto Crop" Toggle (`PageDetector.kt` & `CropAfterScanView.kt`)**:
  - Added `PageDetector.detectPageSmart(bgr)` and `PageDetector.detectPageBestEffortQuad(bgr)` while keeping `PageDetector.detectPage(bgr)` 100% intact for golden test parity.
  - Added Canny-edge subtraction (`otsu_split` and `paper_split`) and morphological opening/erosion (`paper_eroded`) to separate open notebook pages across vertical spine creases and ignore nearby desk objects (pens, computer mice) that previously merged with the page contour.
  - Updated the **"Auto Crop"** radio toggle in `CropAfterScanView.kt` so that even if initial detection was manual, `baseDetectedQuad` computes a best-effort page quad on the photo; toggling **Auto Crop** ON snaps to the page corners and toggling OFF expands to the full image.
- **QR Code Scanner Fix (`CameraScreen.kt` & `ToolsScreen.kt`)**:
  - Fixed live QR code scanning failing silently because `ImageAnalysis` was configured with `OUTPUT_IMAGE_FORMAT_RGBA_8888` while `InputImage.fromMediaImage` only accepts `YUV_420_888` or `JPEG` (throwing `IllegalArgumentException` on every frame).
  - Converted analyzer frames via `imageProxy.toBitmap()` + `InputImage.fromBitmap(bmp, rotationDegrees)` with an `AtomicBoolean` concurrency guard and proper bitmap recycling in `addOnCompleteListener`.
  - Enabled shutter-button capture and Gallery import in `QR_CODE` mode inside `CameraScreen.kt`.
  - Routed Tools → QR Code → "Scan with Camera" directly to `Routes.camera("QR_CODE")`.
- **High-Clarity Scanning & Focus Improvements (`CameraScreen.kt` & `SettingsRepository.kt`)**:
  - Switched `ImageCapture` to `CAPTURE_MODE_MAXIMIZE_QUALITY`, enabled continuous center autofocus/auto-exposure metering, added Tap-to-Focus on the camera viewfinder, smoothed live green quad rendering with exponential moving average interpolation, and enabled `hdMode = true` by default.

## [1.0.4] - 2026-11-11

### Fixed & Refined
- **Crop View Display Scale & Dimensions (`CropAfterScanView.kt`)**:
  - Fixed a critical unit conversion bug where `BoxWithConstraints` was using `maxWidth.value` and `maxHeight.value` (density-independent pixels / Dp) instead of `constraints.maxWidth` and `constraints.maxHeight` (physical pixels) inside Compose Canvas. On high-resolution devices (such as 1440x3120 on Galaxy S24 Ultra), this previously caused the captured document to render as a tiny 400px thumbnail squashed in the top-left corner of the black canvas with all 8 crop handles clustered around it.
  - Now uses pixel constraints directly (`constraints.maxWidth.toFloat()`), scaling and centering the photo to fully utilize the viewport with exact 1:1 touch coordinate mapping for all 8 corner and mid-edge handles and magnifier loupe.
- **Book Scanner Out-Of-Memory Crash (`CameraScreen.kt` & `SaveScanUseCase.kt`)**:
  - Identified root cause of book scanner crash via logcat: `java.lang.OutOfMemoryError: Failed to allocate a 149817616 byte allocation with 50331648 free bytes and 106MB until OOM`.
  - Added `android:largeHeap="true"` to `<application>` in `AndroidManifest.xml`, raising heap ceiling from 256MB to 512MB on modern devices.
  - Prevented premature full document save, OpenCV filter rendering, and PDF building before spread splitting. `CameraScanMode.BOOK` now directly forwards the captured book spread to `BookSplitScreen` via `container.pendingBookScanUris`.
  - Capped maximum image decode long-side to 2500px across `PageRenderer.kt`, `SaveScanUseCase.kt`, and `BookSplitScreen.kt` (providing 300 DPI A4 resolution while reducing heap allocation from 150MB+ down to ~18MB).
- **ID Card Scanner Pipeline & Framing Refinements (`CameraScreen.kt` & `IdCardLayout.kt`)**:
  - Added clear step indicators and user guidance for ID card scanning: "Step 1 of 2: Scan Front side" and "Step 2 of 2: Scan Back side" with toast feedback and bottom thumbnail preview upon capturing front side.
  - Added "Retake Front side" button when capturing the back side.
  - Corrected viewfinder bracket aspect ratio mapping (`aspect = 1.585`) with `FILL_CENTER` aspect ratio compensation so the auto-crop bounds match what the user framed inside the on-screen brackets rather than capturing excess table background.
  - Optimized `IdCardLayout` canvas dimensions to 1754x2480 (standard A4 at 212 DPI), cutting composite bitmap memory by 50%.
- **Tools Screen Routing (`ToolsScreen.kt`)**:
  - Routed "ID Card" and "Book" tool items to use the built-in CameraX scanner (`Routes.camera("ID_CARD")` and `Routes.camera("BOOK")`) rather than legacy Play Services scanner calls.

## [1.0.3] - 2026-11-10

### Fixed & Refined (Audit Bug Fixes)
- **Target Document ID String/UUID Mismatch (Audit Bug 1)**:
  - Fixed `ScanSession.targetDocId` being typed as `Long?` while documents use UUID `String` keys. Changed `targetDocId` to `String?` across `ScanSession`, `ScanSessionViewModel`, `SaveScanUseCase`, and `MainActivity`. "Add pages" to existing documents now correctly appends to the target document rather than creating a new orphan document.
- **Screenshot Card & Session Back-Stack Navigation (Audit Bug 2)**:
  - Corrected navigation handling for "New Screenshot" and restored sessions in `MainActivity.kt`. Replaced linear back-stack navigation with `popBackStack(Routes.SCAN_REVIEW, inclusive = false)` fallback, preventing back-button loops or unexpected exits to home.
  - Added `ScanSessionViewModel.reloadFromDiskIfNeeded()` on review screen entry to reliably recover unpersisted/interrupted sessions without dropping pages.
- **TO_TEXT Mode Navigation & State Preservation (Audit Bug 3)**:
  - Preserved the active `scanMode` inside `ScanSession` (`DOCS`, `BOOK`, `ID_CARD`, `TO_TEXT`).
  - Updated `ReviewFilterScreen` onDone callback to pass `(docId, mode)`.
  - When scanning in `TO_TEXT` mode, tapping Done in Review now navigates directly to `Routes.ocr(docId)` to trigger OCR text extraction rather than landing on the generic Document view.
- **Crop Quad & Rotation Coordinate Space Alignment (Audit Bug 4)**:
  - Decoupled image rotation and normalized quad coordinate systems in `CropAfterScanView.kt`.
  - Added `initialRotation` support and explicit rotation matrix transforms (`rotate90Cw`, `rotate90Ccw`, `toUnrotatedSpace`).
  - Quad coordinates are visually transformed with user rotation steps but converted back to unrotated base bitmap coordinates upon saving (`onApply`, `onDone`, `onNextPage`), eliminating crop distortion and aspect stretching in `PageRenderer`.
- **Live Green Quad Alignment with Viewfinder (Audit Bug 5)**:
  - Fixed aspect ratio misalignment in `CameraScreen.kt` where CameraX portrait analysis frames (3:4 aspect ratio) were projected onto screens with taller aspect ratios (e.g. 9:19.5, 9:20).
  - Added `FILL_CENTER` aspect mapping calculation in `drawLiveQuad`, precisely aligning the live green bounding quad with the physical document across all camera modes.
- **Camera Frame Analyzer Throttling & Resource Leaks (Audit Bug 6)**:
  - Throttled `ImageAnalysis.Analyzer` frame processing to every 3rd frame (`analysisFrameIndex % 3 == 0`), preventing CPU starvation and preview lag.
  - Hoisted `analysisExecutor` and `BarcodeScanner` to composable scope with cleanup in `DisposableEffect`, preventing background thread leaks when exiting the camera.
- **ID_CARD and BOOK Mode Cropping (Audit Bug 7)**:
  - Integrated `PageDetector.detectPage` into `CameraScanMode.BOOK` and `CameraScanMode.ID_CARD` when `autoCrop` is enabled.
  - In `ID_CARD` mode, both front and back captures now automatically auto-crop to the card boundaries (`cropCard`) with fallback to card aspect ratio before composing the 2-sided A4 page layout.
- **Native OpenCV Contour Memory Leak (Audit Bug 8)**:
  - Resolved native memory leak in `PageDetector.candidates()` and `PageDetector.detectPage()`.
  - Added explicit `.release()` calls on all discarded `MatOfPoint` contours and wrapped detection lifecycle in `try ... finally` blocks to free OpenCV C++ native buffers immediately.
- **Dead Code Cleanup & Filter Propagation (Audit Bug 9)**:
  - Removed unused `batchCaptures` state and deprecated `finishBatch()` dead code in `CameraScreen.kt`.
  - Ensured selected camera `defaultFilter` is properly passed to all `sessionViewModel.addPage(...)` calls, applying user filter preferences to initial renders.

## [1.0.2] - 2026-11-10

### Fixed & Refined (Fix Pack 2)
- **Live Capture Guidance (Phase 8 Camera, FIXES-2 §1)**:
  - Implemented 1:1 Kotlin port of `reference_guidance.py` in `CaptureGuidance.kt` with states `SEARCHING`, `MOVE_CLOSER` (< 0.25 area), `MOVE_FARTHER` (> 0.92 area), `HOLD_STILL`, and `CAPTURING`.
  - Driven by `PageDetector.detectPage(bgrMat)` on camera analysis frames (~640 px).
  - Displays centered guidance pill ("Searching for a page…", "Move closer", "Move farther", "Hold still…", "Capturing…") at ~55% height of viewfinder.
  - Draws green bounding quad outline whenever a page is detected (all states except `SEARCHING`).
  - Auto-shutter automatically triggers capture when quad is steady (corners moved < 2% of frame diagonal) for >= 1000 ms, followed by a 1500 ms cooldown. Manual shutter remains functional in any state.
  - Unit test `CaptureGuidanceTest.kt` passes 100% covering all state transitions, jitter reset, steady timing, and cooldown rejection.
- **Portrait Lock (Fixes Camera Tilt / Sideways Stretch Bug, FIXES-2 §2)**:
  - Locked entire application to portrait by declaring `android:screenOrientation="portrait"` and `android:configChanges="orientation|screenSize|keyboardHidden|uiMode"` on `MainActivity` in `AndroidManifest.xml`.
  - Configured CameraX `Preview`, `ImageCapture`, and `ImageAnalysis` target rotations to `Surface.ROTATION_0`, ensuring preview and captured frames remain strictly upright and unstretched when tilting the phone 90°.
- **Screenshot Offering Card on Home (FIXES-2 §3)**:
  - Queries `MediaStore.Images` via `ScreenshotDetector.kt` for screenshots captured in the last 10 minutes.
  - Non-blocking one-time in-app card on first launch explaining screenshot detection with **Allow** and **No thanks** buttons.
  - Added permissions `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED` (Android 13/14+) and `READ_EXTERNAL_STORAGE` (API <= 32). Zero other permissions added.
  - Settings → Scanning toggle switch "Offer new screenshots" allowing users to enable/disable anytime.
  - Displays bottom-right card above bottom bar with preview thumbnail, "New screenshot", "Tap to scan it", and `✕` dismiss button (which adds ID to `dismissedScreenshotIds`, storing last 50).
  - Tapping card opens the scan flow at Review & filter screen with the screenshot as page 1.
- **Crop Screen OKEN-Style Refinements (FIXES-2 §4)**:
  - Top bar title set to "Adjust crop".
  - 8 handles total: 4 corner handles and 4 mid-edge handles. Dragging mid-edge moves that entire edge parallel; dragging corner moves just that corner.
  - 2.6× magnifier loupe active on all 8 handles, dynamically avoiding the user's finger.
  - Added "Auto Crop" toggle at bottom-left (default ON). When OFF, resets handles to the full photo bounds (`Quad.fullQuad()`); when ON, restores detected page quad.
  - Action row in a single line: Auto Crop toggle, spacer, Retake text button, and filled green Continue button.
  - Helper line under image: "Drag the dots to the page corners, or adjust later with the crop tool".
- **Document Screen & Page-Select Mode (FIXES-2 §5 & §6)**:
  - 2-column page grid with page badges `01`, `02`; last tile dashed "Tap 📷➕ to add new pages".
  - Top bar with rename and checkmark circle `✓` button to toggle page-select mode.
  - 5-action bottom bar in Document Screen: **Add**, **Collage**, **Share**, **Edit PDF** (opens page editor on page 1), **More** (Save PDF to…, Save JPGs, Print, Password, Compress, Page size, Lock/Unlock, Book split, Move to recycle bin).
  - Page-select mode: top bar with `←`, "{n} selected", "Select all" / "Deselect all"; thumbnail selection check badges; 5-action bottom bar: **Share**, **Save to Gallery**, **Move** (to existing or new document), **Delete** (with undo snackbar), **More** (Copy [duplicate pages in doc], Collage [make collage from selected pages]).

## [1.0.1] - 2026-11-10

### Fixed
- **Root Cause & Resolution of "Next Closes App" Bug**:
  - *Root Cause Analysis*:
    1. In `CameraScreen.kt`, the previous crop view was rendered via an early return (`if (cropScan != null) { CropAfterScanView(...); return }`), which unmounted the Compose `AndroidView(PreviewView)` and triggered `cameraProvider?.unbindAll()`. When **Next page** was tapped, setting `cropScan = null` recomposed the camera view, attempting to immediately rebind CameraX use cases on the main thread while simultaneously decoding full-resolution bitmaps. This created a race condition with CameraX surface lifecycles and navigation backstacks, causing the activity to finish or pop to the home screen.
    2. Missing isolated back-press handling inside `CropAfterScanView` caused hardware or gesture Back events to pop the entire `CameraScreen` destination, discarding the scan immediately.
    3. State was held purely in transient Compose memory; rotation or app backgrounding caused state loss.
  - *Fix Implemented*:
    1. Replaced fragile in-memory state with a resilient `ScanSession` state machine backed by atomic disk persistence (`cacheDir/scan-session/{sessionId}/session.json`). IDs and file paths are stored on disk; bitmaps are never passed through navigation arguments or saved instance state.
    2. Scoped `ScanSessionViewModel` to the `scan_flow` navigation graph so state is preserved seamlessly across Camera, Crop, and Review screens.
    3. Retained CameraX use cases continuously in the background during active cropping by rendering `CropAfterScanView` as an overlay, eliminating camera re-bind latency and unmount races.
    4. Added back-press confirmation dialogs ("Discard this photo?" in crop; "Save n pages?" in camera) preventing accidental scan loss.
    5. Added startup recovery dialog on Home screen ("Unfinished scan found", `fix-05-recover-session.png`) offering to continue scanning or discard, plus automated 7-day silent purge of orphan sessions.

- **Auto-Crop Failure & Page Detection**:
  - Auto-crop was previously failing to run on captured photos and falling back to a fixed 5% inset rectangle.
  - Implemented a 1:1 Kotlin port of OpenCV `reference_detect.py` in `PageDetector.kt`:
    - Scales image to 1000 px max side, generates 4 candidate binary masks (`edges` via Canny 50/150, `edges_lo` with CLAHE, `bright` via Otsu, and `paper` via Otsu + HSV low-saturation filter).
    - Extracts convex contours, computes polygon vertices via `approxPolyDP` and intersects the 4 longest edge lines.
    - Applies strict rejection rules: area < 15%, non-convexity, corner angles outside 45°–135°, border hits >= 3, and paperness < 50%.
    - Scores candidates via `area fraction * fill^2 * paperness^2`.
    - If no page is detected, falls back cleanly to the full photo bounds (`(0,0)` to `(1,1)`) with a 3-second fading pill ("Couldn't find the page edges; you can drag the corners yourself").
  - Instrumented golden tests (`PageDetectorGoldenTest`): all 7 test scenes in `detect-tests/` verified on a physical Samsung Galaxy S24 Ultra, passing within required 2.5% diagonal error thresholds (scene 07 correctly returns null).

- **Crop Screen Magnifier Bug**:
  - The magnifier previously drew a solid green circle without the photo content.
  - Fixed to render an authentic 116 dp circular loupe with 3 dp white border and drop shadow, zoomed 2.6× around the touch position using Compose `Canvas` with `drawImage` and matrix clipping.
  - Displays a centered white crosshair and the two green polygon edges exiting the dragged corner.
  - Dynamically repositions away from the touching finger (switches from top-left to top-right when the finger enters the top-left quadrant).

- **Crop Screen Layout Squashing & Inset Overlaps**:
  - Top title ("Page n · adjust crop") previously overlapped the Android status bar, and bottom buttons collapsed into an empty vertical bar.
  - Fixed with edge-to-edge `WindowInsets.statusBarsPadding()` and `navigationBarsPadding()`.
  - Image preview bounds are clamped inside the safe area so handles remain accessible.
  - Rebuilt bottom bar into two distinct rows:
    - Row 1: **Retake** (tonal button, icon + text), 10 dp gap, **Rotate** (icon-only tonal button), spacer, and **Next page →** (filled primary button).
    - Row 2: Centered text button **Done (n pages)**.

### Added
- **Review & Filter Screen (`ReviewFilterScreen`)**:
  - Matches `mockups/fix-03-review-filters.png`.
  - Top bar with Back to camera (pages kept), title "Review · {n} pages", and filled **Done** button.
  - Horizontal page carousel displaying ≤ 2048 px cropped, rotated, and filtered previews with "n / total" indicator.
  - 7 live filter thumbnail previews with an "Apply filter to all pages" switch (default ON).
  - Bottom action bar: Crop (reopens crop in edit mode with Cancel/Apply), Rotate (+90°), Retake (captures replacement photo and returns to Review), Add (appends pages from Camera or Gallery), and Delete (with undo snackbar; deleting last page prompts to discard scan).
  - **Done** button executes background save via `SaveScanUseCase.saveScanSession`, generating the document and mirrored PDF, and opens the Document screen.

- **Crash Logger (`CrashLogger`)**:
  - Global uncaught exception handler in `AbdulsScannerApp.onCreate()` recording stack traces, device model, Android version, and app version to local `filesDir/crash/last-crash.txt` (rotated across last 3 logs).
  - Added **"Copy last crash report"** and **"Share last crash report"** under Settings → About with user toast feedback. Fully private and offline (no external network transmission).

- **Share Sheet Enhancements**:
  - Displays dynamic file sizes for PDF and JPG options.
  - Added **"Share as long image"** (`LongImageStitcher`): vertically stacks cropped pages at narrowest page width (max 1600 px, 24 px white separators, max 20,000 px height, JPEG quality 85).
  - Added **"Save to Gallery"**: saves rendered pages directly into `Pictures/Abul's Scanner` via Android `MediaStore`.

- **Automated Multi-Page UI Test (`MultiPageScanFlowTest`)**:
  - Verifies multi-page capture flow (Next -> Next -> Done), 3-page review state, rotation, filter application, crop updates, atomic disk session persistence, and final document persistence on a connected Samsung Galaxy S24 Ultra.

## [1.0.0] - 2026-11-10

### Added
- Renamed app visible name to **Abul's Scanner** (`docs/RENAME-TO-ABULS-SCANNER.md`):
  - Updated app title under icon (`app_name`), Home top-bar, Settings About, notifications, print job names, and dialogs.
  - Mirrored PDF copies directory updated to `Documents/Abul's Scanner/` and PDF tools output to `Documents/Abul's Scanner/Tools/`.
  - Saved image exports updated to `Pictures/Abul's Scanner/`.
  - PDF document metadata Producer set to `Abul's Scanner`.
  - Backup filename updated to `AbulsScanner-backup-yyyyMMdd-HHmm.zip`.
  - Release APK filename updated to `AbulsScanner-{version}.apk`.
  - One-time automated migration on app start moving existing mirrored PDFs into `Documents/Abul's Scanner/` and setting DataStore `renamedToAbul = true`.
  - Dual-prefix backup rotation and restore: recognizes both `AbdulsScanner-backup-` and `AbulsScanner-backup-` ZIP archives, sorting and retaining the newest 3 backups across both prefixes.
  - Added comprehensive Urdu User Guide (`docs/AbulsScanner-User-Guide-Urdu.pdf` and `docs/USER-GUIDE-URDU.md`).
- Camera and Filters Add-on (OKEN-style camera controls, filter panel, and 7 reference filters):
  - 1:1 Kotlin port of reference Python filter algorithms (`reference_filters.py`):
    - **No shadow [BETA]**: Area downsampling to 256 px max side, separable 7x7 max filter, separable box blur (radius 6, applied twice), bilinear upscale, percentile level normalization (p1 to p99), and illumination compensation.
    - **Original**: Untouched captured image.
    - **Lighten**: Auto-levels stretching from 5th to 99th percentile with gamma 0.9.
    - **Magic Color (Enhanced)**: Per-channel percentile stretch (5th to 99.5th) with 1.15x color saturation boost.
    - **Grayscale**: ITU-R BT.601 luminance weights.
    - **B&W**: Bradley-Roth adaptive integral thresholding (window size = 1/8 image width, t = 0.20).
    - **Eco**: Magic Color base combined with Bradley-Roth binary mask at 60% opacity and gamma 0.95.
  - Golden image unit tests (`FiltersGoldenTest`): validates all 7 filters against reference Python output on `test-photo.jpg`, passing well within required thresholds (MAD <= 3 on RGB, P99 <= 20, B&W <= 1%).
  - Built-in camera top bar with 5 controls: Flash, Photo size, Filter, Grid, and Settings shortcut.
  - Camera popups feature single-popup exclusion and pause live document edge detection while any popup is open.
  - Flash menu: Auto, On, Off, and Torch options; live "Torch on" preview chip; and automatic torch shutdown on app pause/exit.
  - Photo size menu: dynamic resolution enumeration querying back camera 4:3 JPEG capture sizes >= 1600x1200 sorted descending (max 8 options) and mapped to `ResolutionSelector`.
  - Real-time filter panel on camera: 4-column grid of all 7 filters showing live frame preview thumbnails.
  - 3x3 rule-of-thirds grid overlay with 55% black opacity (`Color(0x8C000000)`).
  - Camera settings dropdown panel: Auto shutter, Auto crop images, Crop images after each scan, and Mute mode switches.
  - Auto-shutter cooldown logic (1.5s delay + quad displacement > 10% or disappearance before re-triggering).
  - Shutter sound using Android `MediaActionSound` when Mute mode is disabled.
  - Interactive crop-after-scan screen (`CropAfterScanView`): 4 draggable corner handles, magnifier loupe, Retake, Rotate (+90°), Next page, and Done actions.
  - Batch scanning mode: when "Crop images after each scan" is off, captures accumulate with a bottom-right "Done" chip displaying the last captured thumbnail and page count.
  - Page Editor filter strip: all 7 filters displayed in exact order with live 360 px thumbnail previews and a red "BETA" badge on "No shadow".
  - Settings -> Scanning screen: reordered to 11 rows in exact sequence per specification, including "Built-in camera only" subtitle for Google scanner mode.
- Own camera implementation (`CameraScreen`) using AndroidX CameraX and OpenCV (4.10.0) with real-time edge detection and live viewfinder overlays.
- OKEN-style bottom mode slider: Book, To Text, Docs, ID Card, QR Code with smooth switching and primary green selection.
- Viewfinder top bar controls: Flash mode cycle (Auto, On, Off), HD mode toggle (high-resolution captures), 3x3 rule-of-thirds grid overlay toggle, and quick shortcut to Settings.
- Viewfinder mode overlays:
  - Docs: Live green quadrilateral bounding outline tracking detected document corners, plus adaptive status pills ("Searching doc…", "Document detected", "Hold still... capturing").
  - Book: Top instruction banner ("Lay the book open and flat"), green dashed center spine binding line, and large translucent "A" / "B" reading order markers (automatically aligned with LTR or RTL Arabic reading direction).
  - ID Card: Green corner brackets matching ISO/IEC 7810 ID-1 card aspect ratio with dynamic "Front page" and "Back page" indicators for automatic 2-sided composition.
  - To Text: "OCR in batch" status pill with live document boundary detection.
  - QR Code: Green square target box with continuous animated scanning line, and quick flashlight toggle ("Turn light on/off").
- OpenCV document edge detection pipeline (`DocumentDetector`): analysis frames scaled to ~640 px long side, grayscale conversion, Gaussian blur (5x5), Canny edge detector (50/150 thresholds), dilation, contour detection, and convex 4-point polygon approximation via `approxPolyDP` (ε = 2% perimeter) with area > 20% of frame.
- Corner sorting algorithm: deterministically normalizes and orders corners into Top-Left, Top-Right, Bottom-Right, and Bottom-Left.
- 5-frame temporal smoothing: moving window averaging corner coordinates over recent frames to eliminate jitter.
- Stability detection & auto-capture: monitors corner movement against an anchor quad; triggers auto-capture when movement remains under 2% of frame dimensions for 1 continuous second; manual shutter button remains available at all times.
- Non-destructive raw photo storage and re-crop: full-resolution camera captures are saved into `orig/` (`originalPath`), auto-cropped result into `src/` (`sourcePath`), with detected `cropQuad` stored on `PageEntity`. Re-cropping in Page Viewer loads `orig/` so users can expand beyond the initial crop boundaries.
- Scanner choice setting: Settings -> Scanning -> "Scanner engine" allows switching between "Built-in camera (CameraX + OpenCV)" and "Google scanner (ML Kit)".
- Runtime `CAMERA` permission handling with custom rationale dialog ("Abdul's Scanner needs camera access to scan documents directly on your phone. All scanning is completely offline and private.").
- ProGuard / R8 minification and resource shrinking verification with keep rules for OpenCV, Tesseract, ONNX, and PDFBox.
- Merged manifest audit: removed unused `FOREGROUND_SERVICE` and `ACCESS_NETWORK_STATE` permissions using `tools:node="remove"`.
- Unit tests: `DocumentDetectorTest` validating scrambled corner point sorting, 5-frame average quad calculation, and stability distance thresholds.
- App version bumped to `1.0.0` (versionCode `800`).

### Decisions
- OpenCV native runtime is initialized locally via `OpenCVLoader.initLocal()` in `AbdulsScannerApp.onCreate()`.
- Built-in camera serves as the default scanner engine, with Google Document Scanner remaining selectable in Settings -> Scanning.
- Corner normalization maps points to 0.0..1.0 relative coordinates, preserving aspect ratio and rotation independence across preview resolutions.

## [0.7.0] - 2026-11-10

### Added
- `DocxToHtml` engine: in-memory ZIP decompression, XML parsing of Word documents (`word/document.xml`, `word/_rels/document.xml.rels`, and `word/media/*`), translating paragraphs (`w:p`), headings (`Title`, `Heading1`–`Heading6`), character formatting (bold `w:b`, italic `w:i`, underline `w:u`, font size `w:sz`), bullet lists (`w:numPr`), tables (`w:tbl`, `w:tr`, `w:tc`), embedded images (`w:drawing` with Base64 encoding), and Arabic right-to-left layout (`w:bidi`, `w:rtl`).
- `XlsxToHtml` engine: in-memory ZIP decompression, workbook sheet mapping (`xl/workbook.xml`, `xl/_rels/workbook.xml.rels`), shared string table resolution (`xl/sharedStrings.xml`), worksheet cell extraction (`row`, `c`, `v`, `t`), number formatting (trailing `.0` trimming), multi-sheet rendering with page breaks, and automatic landscape orientation detection for wide sheets (>6 columns).
- `HtmlPrinter`: off-screen `WebView` print adapter leveraging Android's native `PrintManager` with ISO A4 portrait and landscape attributes.
- Office to PDF screen (`ToolOfficeToPdfScreen`): file picker for `.docx`, `.xlsx`, and other office documents; off-screen rendering to PDF via Android print service; post-print sheet offering direct import into Abdul's Scanner; unsupported format dialog (`.pptx`, `.doc`, `.xls`, `.ppt`, `.odt`) directing the user to open in Google Docs, Sheets, or Slides with `ACTION_VIEW`; and persistent "Doesn't look right?" help dialog.
- Unit tests: `DocxToHtmlTest` and `XlsxToHtmlTest` validating heading tags, bold styles, bullet lists, tables, base64 images, Arabic RTL paragraphs, two-sheet workbooks, shared strings, number trimming, and landscape orientation triggers.

### Decisions
- Standard XML DOM parsing (`DocumentBuilderFactory`) is utilized for DOCX and XLSX extraction to ensure 100% platform independence between desktop unit tests and Android runtime without stubbing or mocking.
- Dates in XLSX stay as raw serial numbers (documented in the UI help dialog) because date formatting tables in OpenXML require locale-specific number format dictionaries.

## [0.6.0] - 2026-11-10

### Added
- PDF Tools Service (`PdfToolsService`): handles universal tool inputs (`ToolInput.AppDoc`, `ToolInput.PdfFile`), MediaStore export into `Documents/Abdul's Scanner/Tools/{name} ({suffix}).pdf`, and tool result bottom sheet with Open, Share, and Add to App options.
- PDF Merge tool (`ToolMergeScreen`): interactive reorderable list of 2+ inputs (mixing app documents and external PDF files), custom naming, and merging via `PDFMergerUtility` or app document merge.
- PDF Split tool (`ToolSplitScreen`): page extraction grid for app documents with optional removal from original; range string parsing (e.g. `1-3, 5, 8-10`) or "Every page separately" splitting for external PDF files.
- PDF Compression tool (`ToolCompressScreen`): Small (1240 px, q50), Medium (1754 px, q65), and Large (2480 px, q80) presets; re-encodes embedded image XObjects while preserving text and vector content; displays before/after file sizes; and warns if the PDF is already compact.
- PDF Watermark tool (`ToolWatermarkScreen`): customizable watermark text with Android `Canvas` + `StaticLayout` ensuring correct Arabic ligature shaping and RTL order; presets for size (S=4%, M=6%, L=9%), opacity slider (10%–60%), rotation angle (0°, 30°, 45°), color palette (Grey, Red, Blue), and single-centered vs. tiled modes; supports batch stamping onto all pages of app documents and external PDFs; plus "Remove watermark from all pages".
- PDF Password tool (`ToolPasswordScreen`): 256-bit AES encryption protection (`StandardProtectionPolicy`) and password removal with current password validation and wrong password error handling.
- Signature feature (`SignaturePad`, `ToolSignatureScreen`): 2.5:1 drawing pad with smooth quadratic Bézier curve finger drawing, stroke width 6 px, black or blue ink choices, automatic transparent PNG trimming with 16 px padding, saved library storing up to 5 signatures (`SignatureEntity`), interactive draggable/pinch-resizable signature placement on pages (min width 5%), and Page Viewer "Signatures & watermarks" overlay manager with individual removal.
- PDF Page Reorder tool (`ToolPageReorderScreen`): thumbnail grid generated via `PdfRenderer`, page reordering, page deletion with minimum 1-page guard, and reordered PDF generation.
- PDF -> JPG tool (`ToolPdfToJpgScreen`): converts pages to 200 DPI JPEG images saved into `Pictures/Abdul's Scanner/{name}/`.
- Import Files tool (`ToolImportScreen`): import multiple images (`IMPORT_IMAGES`) or render external PDF pages at 200 DPI into new editable documents (`IMPORT_PDF`), with encrypted PDF password prompt and large document (>100 pages) warning.
- Web Page -> PDF tool (`ToolWebToPdfScreen`): visible `WebView` preview with progress bar, URL navigation (auto-prepending `https://`), JavaScript enabled with file access and geolocation disabled, and Android `PrintManager` integration with post-print "Add saved PDF to app" flow.
- Unit tests: `PdfCoordinatesTest` verifying 0..1 normalized coordinate to PDF media box space mappings across all four page rotations (0°, 90°, 180°, 270°).

### Decisions
- Overlay coordinates use 0..1 normalized units relative to upright rendered page dimensions so they remain consistent across page rotations and export resolutions.
- Web Page -> PDF uses the Android system print service (`PrintManager` + `createPrintDocumentAdapter`), allowing the user to select "Save as PDF" to any local destination without third-party network services.

## [0.5.0] - 2026-11-10

### Added
- ID Card scan flow (`IdCardScreen`, `IdCardLayout`): 2-sided capture (or single side with scan back/save front options), automatic composition on a white A4 300 DPI canvas (2480 × 3508 px) with ISO/IEC 7810 ID-1 real physical card scale (1011 × 638 px = 85.6 × 54 mm), aspect ratio preservation, automatic portrait-to-landscape rotation, 1-page document creation with `kind = ID_CARD`, `pageSize = A4`, and automatic "ID Card" tag assignment.
- Book spread scan flow (`BookSplitScreen`, `BookSplit`): spread capture with no page limit, interactive split screen with draggable green dashed line clamped to 30%–70% width, 1.5% gutter trim per side, translucent "A" and "B" letter indicators, LTR vs. RTL reading order toggle (supporting Arabic right-to-left books), "Apply this line to all spreads" option, and batch saving with `kind = BOOK`.
- Document page Book split option: Document Screen -> More -> "Book split" enables splitting any existing scanned book spread in a document into two separate pages in place, recalculating page numbers, regenerating the PDF, and re-mirroring.
- Collage tool (`CollageScreen`, `CollageLayout`): pick 2 to 4 photos or pages, layout templates (2 rows, 2 columns, 3 rows, 1 large + 2 small, 2×2 grid, 4 rows) on A4 300 DPI canvas (outer margin 120 px, gap 60 px), interactive live preview, and save to a 1-page document with `kind = COLLAGE`, `pageSize = A4`.
- Unit tests: `IdCardLayoutTest`, `BookSplitTest`, and `CollageLayoutTest` verifying canvas dimensions, physical card bounding boxes, gutter trim arithmetic, reading order inversion, and cell grid layouts.

### Decisions
- ID card composition centers both front and back sides horizontally at x = 734, front at y = 600, back at y = 1478, ensuring exact 1:1 real-world physical scale when printed without scaling.
- Book split line position starts at 50% and clamps strictly within 30%–70% of spread width to prevent accidental degenerate page cuts.
- Collage layouts fit images centered inside their allocated cells with aspect ratio preservation, drawing onto a crisp 300 DPI white canvas.

## [0.4.0] - 2026-11-10

### Added
- To Text screen (`ToTextScreen`, `ToTextViewModel`): on-device OCR reading for single pages or batches with English, Arabic, and Both language selections, RTL-aware editable view, progress indicator, cancellation, copy to clipboard, system share sheet, save to document, and export as `.txt` file into `Documents/Abdul's Scanner/`.
- Full-text search on Home screen: SQLite FTS4 table (`DocumentSearchFts`) with Arabic text normalization (stripping tashkeel, unifying alef/yeh/teh marbuta, normalising Arabic-Indic numerals), real-time query matching, and snippet preview with highlighted match terms.
- Background OCR worker (`OcrWorker`): WorkManager task queued on scan completion to asynchronously index pages for search with battery-not-low constraints.
- Re-read all pages feature in Settings to rebuild search index for existing documents with user confirmation dialog.
- QR and barcode scanning: Google Code Scanner camera preview (`GmsBarcodeScanner`, requires zero camera permissions) and ML Kit barcode recognition from image gallery files (`QrScannerHelper`).
- Structured QR parser (`QrParser`): handles Wi-Fi, URL, Phone numbers, SMS, Email, Geolocation, vCard/MeCard contacts, and plain text.
- QR result screen (`QrResultScreen`): tailored action buttons for Wi-Fi connection / password copy, web browsing, phone dialer, email composer, SMS messaging, map navigation, and contact saving.
- QR history (`QrHistoryScreen`, `QrHistoryViewModel`): scrollable history list with timestamps, formatted values, single item deletion, clear all confirmation, and Settings toggle (`qrSaveHistory`).
- Unit tests in `QrParserTest` verifying all structured QR format parsers without Android stub dependencies.

### Decisions
- Structured QR parser uses `java.net.URLDecoder` and standard URL parsing to ensure complete unit testability under local JVM test runners.
- QR scanner is accessible from both the Tools tab and Document actions, offering direct camera scan, image file picker, and history inspection.
- Wi-Fi connection utilizes Android Q+ `WifiNetworkSuggestion` API when available, falling back gracefully to clipboard copy and Wi-Fi settings intent on earlier versions.

## [0.3.0] - 2026-11-10

### Added
- `PerspectiveCrop` interactive 4-point corner handle canvas crop view with normalized coordinates, real-time quad boundary lines, error indication on non-convex/self-intersecting quads, and magnifier loupe while dragging.
- `Filters` & `AdaptiveThreshold`: Original, Enhanced auto-levels, Grayscale, Bradley-Roth B&W filters with non-destructive source photo preservation.
- Brightness (-100..100) and Contrast (-100..100) adjust panel with real-time preview and reset option.
- Page viewer editing toolbar: Crop, Rotate (+90°), Adjust, Retake (single page via ML Kit scanner), and Delete with undo snackbar.
- Reorder pages mode on Document screen with swap navigation and automatic PDF regeneration in background.
- Tags management: `TagsScreen` & `TagsViewModel` with alphabetical grouping, document counts, create, rename, and delete actions; document tag bottom sheet; Home tag filter dropdown with Ungrouped and per-tag filters.
- Home multi-select mode: top bar with selected count and Select/Deselect all; bottom action bar for multi-tagging, sharing multiple PDFs, multi-doc lock, and multi-doc trash.
- `MergeDocumentsUseCase`: combines pages from 2+ documents into a new document with atomic file copies, database transaction, and PDF build.
- App PIN & Biometric security: `PinHasher` (PBKDF2WithHmacSHA256, 120,000 iterations, 16-byte salt), `SessionManager` locking on `ON_STOP` lifecycle events, `LockScreen` with `FLAG_SECURE` window protection, biometric prompt, and device credential fallback.
- `SecuritySettingsScreen`: Set PIN, Change PIN, Remove PIN, Fingerprint toggle, and unencrypted backup warning.
- Document options: Page size selector (Auto, A4, Letter), Export JPGs to `Pictures/Abdul's Scanner/` via `MediaStoreExporter`, and Print via `PrintManager` and `PdfPrintAdapter`.
- Unit tests in `PinHasherTest` covering key generation, verification, wrong password rejection, and salt formatting.

### Decisions
- Standardized on `java.util.Base64` for PIN salt encoding and decoding to allow headless JVM test execution on Android API 26+.
- Document locked state blurs thumbnails on Home and in search previews to prevent leaking confidential information before authentication.
- Page reorder mode provides explicit previous/next swap buttons ensuring accessible, deterministic page rearrangement.

## [0.2.0] - 2026-11-10

### Added
- Dedicated `@Serializable` DTOs in `BackupDtos.kt` ensuring stable backward-compatible zip backup format independent of Room entities.
- `BackupWriter`: snapshots database in a single read transaction, packages zip archive with STORED image files and DEFLATED JSON, verifies entry count and CRC32, writes to SAF tree or manual file destination, and enforces retention limit (`backupKeepCount`).
- `BackupReader`: validates format signature and version, executes additive restore skipping duplicate documents, merges tags by name, restores signatures/QR records, unpacks files to temp before atomic commit, populates FTS4 search, and rebuilds PDFs (`PdfRebuildWorker`).
- Background workers: `AutoBackupWorker` (periodic with SAF folder and notification on failure) and `TrashPurgeWorker` (daily 30-day purge).
- Recycle bin screen (`RecycleBinScreen` & `RecycleBinViewModel`) with 30-day countdown timer, individual Restore/Delete forever actions, and Empty bin confirmation.
- Storage safety (`StorageSafety`): StatFs checks before scan and backup (warning < 200 MB, refusal < 50 MB).
- Home backup reminder banner (Section 12.5): conditional banner for due backups (> 7 days or never), failed automatic backup, and off-phone reminder (> 30 days).
- Settings screen (`SettingsScreen` & `SettingsViewModel`) and Backup screen (`BackupScreen` & `BackupViewModel`) with app data usage stats and temporary cache cleaner.
- Unit tests in `BackupDtoTest` (manifest validation, forward compatibility rejection, full data serialization round-trip) and `StorageSafetyTest`.

### Decisions
- Zip archive stores uncompressed images (`ZipEntry.STORED`) with precomputed CRC32 and explicit length to minimize CPU overhead on mobile devices.
- Combined flows in `HomeViewModel` into intermediate data classes to remain within Kotlin coroutine typed `combine` boundaries without resorting to reflection or untyped arrays.

## [0.1.0] - 2026-11-10

### Added
- Core app architecture with manual DI (`AppContainer`), Room Database v1 with FTS4 search table, DataStore settings repository.
- Material 3 dark/light themes with brand colors (`#13A06F`, `#121212`, `#F7F7F5`).
- Animated startup splash screen (Android 12+) and backwards-compatible static splash (`androidx.core:core-splashscreen:1.2.0`) per `docs/logo-pack/LOGO-AND-SPLASH.md`.
- ML Kit Document Scanner integration and save scan pipeline (`SaveScanUseCase`) with atomic storage replace (`FileStore`).
- PDF generation (`PdfBuilder` using PdfBox-Android) and background public mirror sync to `Documents/Abdul's Scanner/` (`MirrorStore`).
- Home screen with recent documents, search/filter bar, list/grid toggle, bottom navigation bar, and FAB scan button.
- Document detail screen with page grid, rename dialog, share PDF sheet, add pages, and soft delete.
- Page viewer screen with zoom/pan and page carousel.
- Arabic OCR Labs evaluation screen comparing ML Kit, Tesseract `ara+eng`, and PaddleOCR on real images with benchmark timing and 1–5 star ratings.
- Unit tests covering `AdaptiveThreshold`, `Filters`, `PdfLayoutMath`, `PerspectiveCrop`, and `SearchNormalizer`.
- Release keystore generated and exported with GitHub Actions CI workflows.

### Decisions
- Using Gradle 8.11.1 / AGP 8.7.3 toolchain matching OpenJDK 17 and Kotlin 2.0.21.
- SQLite FTS4 virtual table requires rowid alias `doc_id` instead of `docId` to prevent SQL keyword conflict; mapped `doc_id` column to Kotlin `docId`.
- Geometric transformations (`Point2D`) written in pure Kotlin to enable robust JVM unit tests without Android stub runtime dependencies.
- Added approved `androidx.core:core-splashscreen:1.2.0` for animated splash screen on Android 12+ and static splash on Android 8–11.
