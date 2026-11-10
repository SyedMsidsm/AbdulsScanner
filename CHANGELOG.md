# Changelog

All notable changes to **Abdul's Scanner** will be documented in this file.

## [Unreleased]

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
