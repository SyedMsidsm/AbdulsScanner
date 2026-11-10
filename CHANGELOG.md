# Changelog

All notable changes to **Abdul's Scanner** will be documented in this file.

## [1.0.0] - 2026-11-10

### Added
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
