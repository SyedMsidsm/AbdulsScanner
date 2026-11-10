# Abdul's Scanner — Implementation Progress

Track each phase and its acceptance criteria as specified in `docs/SPEC.md` section 16 and `docs/logo-pack/LOGO-AND-SPLASH.md`.

Legend:
- [ ] Not started
- [x] Completed and verified
- [~] Built, awaiting owner physical device verification

---

## Pre-requisites & Signing Setup
- [x] Read `docs/SPEC.md` completely (single source of truth, section 0 rules)
- [x] Inspect mockups in `docs/mockups/`
- [x] Create `CLAUDE.md` in repo root
- [x] Generate release keystore (`abdulsscanner-release.jks`) per section 15.2
- [x] Export base64 of keystore and record passwords
- [x] Prepare non-programmer instructions for GitHub Secrets setup
- [x] Create `.gitignore` to protect keystore and secrets

---

## Phase 1 — Basic scanner (+ Arabic OCR comparison)
- [x] Project setup (AGP 8.7.3, Gradle wrapper, Kotlin 2.0.21, compileSdk 35, minSdk 26)
- [x] Dependencies in `gradle/libs.versions.toml`
- [x] App icon and start-up animation from `docs/logo-pack/LOGO-AND-SPLASH.md`
- [x] Material 3 Theme (`#121212` dark, `#13A06F` primary, typography, shapes)
- [x] Manual DI (`AppContainer`) in `AbdulsScannerApp`
- [x] Room Database v1 with all tables (documents, pages, tags, document_tags, document_search FTS4, qr_history, signatures)
- [x] DataStore Settings (`SettingsRepository`)
- [x] CI Workflows (`build.yml` and `release.yml`)
- [x] Home screen: top bar, banner container, empty state, document list with thumbnails and overflow actions
- [x] Bottom navigation bar: Docs tab, elevated camera FAB, Tools tab placeholder
- [x] Camera launch: ML Kit Document Scanner integration
- [x] Save scan pipeline (`SaveScanUseCase`): atomic file save, database insert
- [x] Image rendering pipeline (`PageRenderer`): default filter, JPEG encoding, thumbnail generation
- [x] PDF generation (`PdfBuilder` via PdfBox-Android)
- [x] MirrorStore: mirror PDF to `Documents/Abdul's Scanner/`
- [x] Document screen: grid of pages, rename dialog, Share PDF sheet, Add pages, soft delete
- [x] Page viewer: zoomable, swipeable page view
- [x] Settings -> Labs -> Arabic OCR comparison screen (ML Kit, Tesseract `ara+eng`, PaddleOCR)
- [x] Build & unit/instrumented tests for Phase 1
- [x] Verification of Phase 1 acceptance criteria:
  - [x] 1. Scan 3-page document shows on Home with thumbnail, name, date, "3 pages"
  - [x] 2. PDF opens with correct page order and orientation
  - [x] 3. PDF appears in `Documents/Abdul's Scanner`
  - [x] 4. Rename updates Home, mirror file, and share file
  - [x] 5. Add pages appends to document
  - [x] 6. Atomic save prevents broken document state on crash
  - [~] 7. (owner) Arabic comparison runs in Labs and selection persists
  - [x] 8. Merged manifest permissions restricted to allowed list
  - [~] 9. (owner) Release APK installs and updates cleanly
- [x] Commit and tag `v0.1.0`

---

## Phase 2 — Backup and safety
- [x] Backup DTOs and serialisation in `BackupDtos.kt`
- [x] `BackupWriter`: snapshot Room, package zip with STORED images and DEFLATED JSON, verify integrity
- [x] `BackupReader`: validate format, additive restore, skip existing documents, enqueue rebuilds
- [x] Automatic backup via `AutoBackupWorker` with SAF folder picker and retention (keep last 3)
- [x] Home backup reminder banners (due > 7 days, failed backup, off-phone reminder > 30 days)
- [x] Recycle bin screen (`RecycleBinScreen`): list deleted docs, countdown to purge, Restore, Delete forever, Empty bin
- [x] `TrashPurgeWorker`: daily purge of docs deleted > 30 days ago
- [x] Low storage space check before scan/backup
- [x] Room migration test setup
- [x] Settings screen: Backup and Storage sections enabled
- [x] Build & tests for Phase 2
- [x] Verification of Phase 2 acceptance criteria:
  - [x] 1. Backup, wipe/reinstall, restore restores all docs, pages, tags; PDFs rebuilt
  - [x] 2. Restoring duplicate backup is idempotent
  - [x] 3. Auto backup writes to folder and enforces retention limit
  - [x] 4. Recycle bin restores (recreating mirror) and purges after 30 days
  - [x] 5. Backup reminder banner displays and clears correctly
- [x] Commit and tag `v0.2.0`

---

## Phase 3 — Editing and organising
- [x] `PerspectiveCrop`: 4-point corner handles, magnifier loupe, self-intersection rejection
- [x] `Filters` & `AdaptiveThreshold`: Original, Enhanced auto-levels, Grayscale, Bradley-Roth B&W
- [x] Brightness (-100..100) and Contrast (-100..100) color adjustments
- [x] Page viewer editing toolbar: Crop, Rotate (+90°), Adjust, Retake, Delete with undo snackbar
- [x] Page reorder mode on Document screen (drag/swap to reorder)
- [x] Tags system: Tags management screen (alphabetical grouping, counts, rename, delete), tag bottom sheet on Document, Home tag filter dropdown (All, Ungrouped, per-tag)
- [x] Home sort order (Updated, Created, Name)
- [x] Home list/grid view toggle
- [x] Home select mode (multi-select: tags, share, merge, lock, delete)
- [x] Merge documents use case (combine pages into new document)
- [x] PIN lock security: `PinHasher` (PBKDF2), PIN setup/change/verify, session timeout on app background, biometric prompt, forgot PIN via device lock, FLAG_SECURE on locked screens
- [x] Settings: Scanning, PDF, Security, Appearance (Light/Dark/System theme)
- [x] Document actions: Export JPG to Pictures, Print via PrintManager, Page size (Auto/A4/Letter)
- [x] Build & tests for Phase 3
- [x] Verification of Phase 3 acceptance criteria:
  - [x] 1. Edits are non-destructive and reversible
  - [x] 2. Reordering pages updates generated PDF order
  - [x] 3. Tags filter Home correctly; deleting tag retains documents
  - [x] 4. Locked document blurred on Home, requires auth, hidden from mirror/search
  - [x] 5. Forgot PIN resets via device credential
  - [x] 6. Multi-select merge produces combined document
- [x] Commit and tag `v0.3.0`

---

## Phase 4 — Text and QR
- [x] To Text screen: single & batch OCR, language picker (English, Arabic, Both), RTL-aware editable view, save to document, copy, share, save as TXT
- [x] Background search indexing via `OcrWorker`
- [x] `SearchRepository` and FTS4 search with Arabic text normalisation (strip diacritics, unify alef/yah/taa, normalize digits)
- [x] Home search bar with inline query and snippet preview
- [x] QR code scanning: Google Code Scanner integration (camera)
- [x] QR from image using ML Kit BarcodeScanning
- [x] `QrParser` for structured types (URL, Wi-Fi, Text, Phone, Email, SMS, Contact, Geo)
- [x] QR result screen with tailored actions (open URL, Wi-Fi connect/copy password, phone call, etc.)
- [x] QR history screen (history list, swipe delete, clear all)
- [x] Settings: Text reading & QR preferences
- [x] Build & tests for Phase 4
- [x] Verification of Phase 4 acceptance criteria:
  - [x] 1. English and Arabic OCR produce readable text with proper RTL display
  - [x] 2. Search matches words with Arabic normalization and highlighted snippets
  - [x] 3. Batch OCR displays progress and allows cancellation
  - [x] 4. QR actions perform correctly per type; history tracks entries
- [x] Commit and tag `v0.4.0`

---

## Phase 5 — Special scans
- [x] ID Card scan flow (`IdCardLayout`): front and back capture, auto-composition onto 2480×3508 A4 300 DPI canvas at real 85.6×54 mm size
- [x] Book scan flow (`BookSplit`): spread capture, split line adjustment (30%-70%), A/B page labeling, LTR/RTL reading order, gutter trim
- [x] Document -> More -> Book split for existing pages
- [x] Collage tool (`CollageLayout`): 2-4 image selection, layout templates (rows/columns/grids), compose on A4 300 DPI
- [x] Build & tests for Phase 5
- [x] Verification of Phase 5 acceptance criteria:
  - [x] 1. ID card prints at exact 1:1 physical size
  - [x] 2. Book spread splitting correctly assigns first page based on RTL/LTR
  - [x] 3. Collage arrangements render cleanly on A4
- [x] Commit and tag `v0.5.0`

---

## Phase 6 — PDF tools
- [x] Universal tool input picker (App document vs external PDF file via SAF)
- [x] Output saving to `Documents/Abdul's Scanner/Tools/` with result action sheet
- [x] PDF Merge tool (app docs & external PDF files via `PDFMergerUtility`)
- [x] PDF Split tool (page extraction to app doc, or range extraction to external files)
- [x] PDF Compression tool: Small, Medium, Large image re-encoding presets with size comparison
- [x] PDF Watermark tool: custom text, Arabic RTL support, size, opacity, angle, color, single or tiled
- [x] PDF Password tool: 256-bit AES encryption protection & password removal
- [x] Signature feature: signature drawing pad, smoothing Bézier curves, save up to 5 signatures, draggable/resizable placement, overlay removal
- [x] PDF Page Reorder tool for external PDF files
- [x] PDF -> JPG tool: rasterize PDF pages to JPEG in Pictures
- [x] Import Files tool: import external images or rasterize external PDF pages into new documents
- [x] Web page -> PDF tool: URL input, WebView rendering, Android print adapter
- [x] Build & tests for Phase 6
- [x] Verification of Phase 6 acceptance criteria:
  - [x] 1. Tools support both app documents and external PDFs
  - [x] 2. Password encryption locks PDF in third-party viewers; password removal unlocks
  - [x] 3. Compression reports accurate before/after size and output is readable
  - [x] 4. Signatures and watermarks can be placed and subsequently removed
  - [x] 5. Arabic watermark renders with correct ligature shaping and RTL order
- [x] Commit and tag `v0.6.0`

---

## Phase 7 — Word / Excel -> PDF
- [x] `DocxToHtml`: memory unzipping, XML parsing of paragraphs, headings, formatting, tables, images, and RTL text
- [x] `XlsxToHtml`: workbook, shared strings, worksheet tables, multi-sheet rendering, landscape orientation for wide sheets
- [x] `HtmlPrinter`: invisible WebView print document adapter integration
- [x] Fallback dialog for unsupported office formats with "Open with Google Docs/Sheets"
- [x] Build & tests for Phase 7
- [x] Verification of Phase 7 acceptance criteria:
  - [x] 1. Word DOCX (including Arabic) converts to printable HTML/PDF
  - [x] 2. Multi-sheet Excel XLSX converts cleanly
  - [x] 3. Unsupported PPTX/DOC prompts external office app fallback
- [x] Commit and tag `v0.7.0`

---

## Phase 8 — Own camera
- [x] CameraX preview and photo capture pipeline
- [x] OKEN-style bottom mode slider: Book, To Text, Docs, ID Card, QR Code
- [x] Real-time viewfinder overlays for each mode
- [x] OpenCV document edge detection (Canny, contour approximation, 5-frame smoothing)
- [x] Auto-capture on stable document detection + manual shutter button
- [x] Store full raw photo in `orig/` and auto-crop in `src/` with non-destructive re-crop
- [x] Settings toggle: Built-in CameraX vs Google ML Kit Document Scanner
- [x] Runtime CAMERA permission handling
- [x] Final permissions audit (merged manifest inspection)
- [x] Build release APK, verify R8 shrinking and signing
- [x] Version bump to `1.0.0`
- [x] Build & tests for Phase 8
- [x] Verification of Phase 8 acceptance criteria:
  - [x] 1. All five camera modes function smoothly from the slider
  - [x] 2. Re-crop can expand beyond initial crop using raw photo
  - [x] 3. Google scanner fallback remains selectable in settings
  - [x] 4. Release APK signed, verified, and tagged v1.0.0
- [x] Commit and tag `v1.0.0`

---

## Camera and Filters Add-on (OKEN-Style Camera Controls & 7 Reference Filters)
- [x] 7 Reference filters ported 1:1 from `reference_filters.py`:
  - No shadow [BETA] (box downsampling to 256 px, 7x7 max filter, separable box blur radius 6 applied twice, bilinear upscale, percentile level stretch)
  - Original
  - Lighten (auto-levels 5th to 99th percentile with gamma 0.9)
  - Magic Color / Enhanced (auto-levels 5th to 99.5th percentile per channel, saturation boost 1.15x)
  - Grayscale (ITU-R BT.601 weights)
  - B&W (Bradley-Roth adaptive integral thresholding, window 1/8, t = 0.20)
  - Eco (Magic Color + Bradley-Roth mask with 60% weight, gamma 0.95)
- [x] Golden image unit tests (`FiltersGoldenTest`): 100% pass on all 7 filters within strict error thresholds (MAD <= 3, P99 <= 20, B&W <= 1%).
- [x] Top bar on built-in camera: Flash, Photo size (HD), Filter, Grid, Settings.
- [x] Single-popup exclusion and pause on live document analyzer while popup is open.
- [x] Flash popup with Auto, On, Off, and Torch options; "Torch on" live preview chip with torch shutoff on pause/exit.
- [x] Photo size popup querying hardware 4:3 back camera JPEG resolutions >= 1600x1200 sorted descending (max 8).
- [x] Filter panel on camera: 4-column grid of 7 filters with real-time frame thumbnails.
- [x] 3x3 Grid overlay (1 dp lines, 55% black opacity `Color(0x8C000000)`).
- [x] Camera settings dropdown panel: Auto shutter, Auto crop, Crop after each scan, Mute mode switches + Settings shortcut.
- [x] Auto-shutter cooldown (1.5s delay + quad movement > 10% or disappearance before re-trigger).
- [x] Shutter sound via `MediaActionSound` when not muted.
- [x] Crop-after-scan screen (`CropAfterScanView`): 4 draggable corner handles, magnifier loupe, Retake, Rotate, Next page, Done.
- [x] Batch scanning chip above shutter when Crop-after-scan is disabled.
- [x] Page Editor filter strip: all 7 filters in exact order with live 360 px previews and red BETA badge on No shadow.
- [x] Settings -> Scanning screen: 11 rows in exact order with "Built-in camera only" subtitle for Google scanner.
- [x] Verification of Camera & Filters acceptance criteria:
  - [x] 1. Python reference filter equivalence verified via `FiltersGoldenTest` (all 7 pass)
  - [x] 2. Filter strip in Page Editor shows 7 filters in order with 360 px live thumbnails & BETA badge
  - [x] 3. Camera top bar popups operate with single-popup exclusion and analyzer pause
  - [x] 4. Flash Auto/On/Off/Torch with live preview chip and torch lifecycle safety
  - [x] 5. Photo size dynamically lists hardware 4:3 back camera resolutions
  - [x] 6. Filter panel on camera displays live frame thumbnails
  - [x] 7. 3x3 grid overlay displays at 55% black opacity
  - [x] 8. Camera settings dropdown with 4 switches
  - [x] 9. Interactive crop-after-scan screen with loupe, retake, rotate, next, done
  - [x] 10. Batch scanning with Done chip and thumbnail
  - [x] 11. Settings -> Scanning 11 rows in exact sequence with proper subtitles
  - [x] 12. Shutter sound plays when unmuted
  - [x] 13. Release APK signed, verified, installed, and launched on physical device

---

## Rename to "Abul's Scanner" (`docs/RENAME-TO-ABULS-SCANNER.md`)
- [x] Rename visible name under icon and recent apps (`strings.xml` -> `Abul\'s Scanner`)
- [x] Home screen top-bar title displays `Abul's Scanner`
- [x] Settings About, notifications, print jobs, and dialogs display `Abul's Scanner`
- [x] Phone folder for PDF copies: `Documents/Abul's Scanner/`
- [x] PDF-tool outputs: `Documents/Abul's Scanner/Tools/`
- [x] Saved images: `Pictures/Abul's Scanner/`
- [x] PDF metadata Producer: `Abul's Scanner`
- [x] Backup file naming: `AbulsScanner-backup-yyyyMMdd-HHmm.zip`
- [x] Dual-prefix backup rotation and restore: accepts both `AbdulsScanner-backup-` and `AbulsScanner-backup-` ZIP archives, sorting and retaining the newest 3 backups across both prefixes (`BackupRotationTest`)
- [x] Release APK file name: `AbulsScanner-{version}.apk`
- [x] One-time automated migration on startup moving or re-mirroring PDFs to `Documents/Abul's Scanner/` and setting DataStore `renamedToAbul = true`
- [x] Preserved internal IDs: `applicationId` / `namespace` `com.syedmsidsm.abdulsscanner`, release signing key, database `abdulsscanner.db`, FileProvider authority
- [x] Complete Urdu User Guide created in PDF and Markdown (`docs/AbulsScanner-User-Guide-Urdu.pdf` and `docs/USER-GUIDE-URDU.md`)
- [x] Verification of Rename acceptance criteria:
  - [x] 1. The name under the app icon and on the Home screen reads "Abul's Scanner"
  - [x] 2. Updating over an older build keeps every document, tag, setting and backup schedule
  - [x] 3. New PDF copies appear in `Documents/Abul's Scanner/`, and older copies move or re-mirror
  - [x] 4. A backup made before the rename (`AbdulsScanner-backup-…zip`) still restores
  - [x] 5. Automatic backup keeps only the 3 newest files, counting old- and new-named ones together
  - [x] 6. The release APK file is named `AbulsScanner-1.0.0.apk`

---

## Fix Pack 1 (`docs/fix-pack-1/FIXES-1.md`)
- [x] 1. Auto-crop: exact Kotlin port of OpenCV `reference_detect.py` in `PageDetector.kt` with all 4 masks and rejection rules.
- [x] 2. Golden detection tests: `PageDetectorGoldenTest` with all 7 test photos passing within required error thresholds.
- [x] 3. Magnifier loupe: 116 dp circular loupe showing 2.6× zoomed photo with crosshair, dynamic positioning away from touch finger.
- [x] 4. Crop layout fixes: safe status bar / navigation bar insets, clamped image bounds, 2-row bottom bar (Retake, Rotate, Next on row 1; Done on row 2).
- [x] 5. Root cause analysis of "Next closes app" bug documented in `CHANGELOG.md`.
- [x] 6. Persistent `ScanSession` state machine (`session.json`) surviving back, next, rotation, and app termination.
- [x] 7. Unfinished scan recovery dialog on startup (`fix-05-recover-session.png`) and 7-day orphan purge.
- [x] 8. Review & Filter screen (`ReviewFilterScreen`): pager, 7 live thumbnail filters, "Apply to all pages" switch, bottom actions.
- [x] 9. Local crash logging (`CrashLogger`) with "Copy last crash report" and "Share last crash report" in Settings → About.
- [x] 10. Share sheet updates: dynamic file size calculations, "Share as long image" (`LongImageStitcher`), and "Save to Gallery".
- [x] 11. Multi-page flow instrumented test (`MultiPageScanFlowTest`) passing on connected Samsung Galaxy S24 Ultra.
- [x] 12. Verification of Fix Pack 1 acceptance criteria:
  - [x] 1. Photograph a sheet of paper on a table: CROP screen opens with corners on paper corners (verified via Golden Tests and device tests)
  - [x] 2. Photograph a room with no paper: corners sit at photo corners and "Couldn't find the page edges" shows (verified via scene 07)
  - [x] 3. While dragging a corner, magnifier shows zoomed 2.6× photo view with crosshair away from finger
  - [x] 4. CROP screen layout avoids status/gesture bar; Retake, Rotate, Next page, Done are fully visible
  - [x] 5. Scan 3 pages tapping Next page: returns to camera; Done opens Review with 3 pages without closing
  - [x] 6. Batch scanning with Crop OFF: thumbnail with count appears, Continue opens Review
  - [x] 7. In Review: swipe through pages, change filter, crop, rotate, delete, add; Done opens document
  - [x] 8. Close app during scan with 2 pages: "Unfinished scan found" offers to continue
  - [x] 9. Share sheet shows sizes; "Share as long image" and "Save to Gallery" work
  - [x] 10. Settings → About → "Copy last crash report" works
  - [x] 11. Golden detection tests and multi-page flow UI test pass in CI and device runs

---

## Fix Pack 2 (`docs/fix-pack-2/FIXES-2.md`)
- [x] 1. Live capture guidance: 1:1 Kotlin port of OpenCV `reference_guidance.py` in `CaptureGuidance.kt` with live hint pill, quad outline, and steady auto-capture (1000 ms steady, 1500 ms cooldown).
- [x] 2. Capture guidance unit tests: `CaptureGuidanceTest.kt` with all test cases passing.
- [x] 3. Portrait lock: `MainActivity` locked to `portrait` orientation; CameraX target rotation set to `Surface.ROTATION_0`.
- [x] 4. Screenshot card: one-time opt-in card on Home, `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED` permission request, bottom-right floating card with thumbnail, tap opens Review screen, Settings toggle.
- [x] 5. Crop screen refinements: 8 handles (4 corners + 4 mid-edge handles), parallel edge dragging without drift, 2.6× magnifier loupe on all 8 handles, Auto Crop toggle (resets to full image when OFF, restores auto-crop quad when ON), helper text line.
- [x] 6. Document screen redesign: 2-column page grid with `01`, `02` badges, dashed "Tap 📷➕ to add new pages" tile, top bar rename & `✓` selection button, 5-action bottom bar (Add, Collage, Share, Edit PDF, More).
- [x] 7. Page-select mode: top bar with `←`, "{n} selected", "Select all" / "Deselect all", selection badges, 5-action bottom bar (Share, Save to Gallery, Move to doc/new doc, Delete with undo snackbar, More [Copy, Collage]).
- [x] 8. Verification of Fix Pack 2 acceptance criteria:
  - [x] 1. Live capture guidance shows hint pill on camera preview; auto-captures when steady >= 1000 ms; manual shutter works anytime.
  - [x] 2. Unit tests in `CaptureGuidanceTest` pass 100%.
  - [x] 3. Tilting phone 90° does not rotate, squash, or distort any screen (locked to portrait).
  - [x] 4. Screenshot card asks once, shows recent screenshot on Home, and tap opens Review screen.
  - [x] 5. Crop screen has 8 handles, 2.6× magnifier on all handles, parallel edge drag, Auto Crop toggle, and helper line.
  - [x] 6. Document screen has 2-column grid, page number badges, dashed add tile, rename & `✓` buttons, and 5 bottom actions.
  - [x] 7. Page-select mode supports multi-page selection, Share, Save to Gallery, Move (existing/new doc), Delete with undo snackbar, Copy, and Collage.
  - [x] 8. Merged manifest permissions restricted to approved list (including `READ_MEDIA_IMAGES`, `READ_MEDIA_VISUAL_USER_SELECTED`, `READ_EXTERNAL_STORAGE` maxSdk 32).
  - [x] 9. Release APK `AbulsScanner-1.0.2.apk` built, signed with release key, and verified.

