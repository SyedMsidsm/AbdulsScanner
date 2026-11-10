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
- [ ] CameraX preview and photo capture pipeline
- [ ] OKEN-style bottom mode slider: Book, To Text, Docs, ID Card, QR Code
- [ ] Real-time viewfinder overlays for each mode
- [ ] OpenCV document edge detection (Canny, contour approximation, 5-frame smoothing)
- [ ] Auto-capture on stable document detection + manual shutter button
- [ ] Store full raw photo in `orig/` and auto-crop in `src/` with non-destructive re-crop
- [ ] Settings toggle: Built-in CameraX vs Google ML Kit Document Scanner
- [ ] Runtime CAMERA permission handling
- [ ] Final permissions audit (merged manifest inspection)
- [ ] Build release APK, verify R8 shrinking and signing
- [ ] Version bump to `1.0.0`
- [ ] Build & tests for Phase 8
- [ ] Verification of Phase 8 acceptance criteria:
  - [ ] 1. All five camera modes function smoothly from the slider
  - [ ] 2. Re-crop can expand beyond initial crop using raw photo
  - [ ] 3. Google scanner fallback remains selectable in settings
  - [ ] 4. Release APK signed, verified, and tagged v1.0.0
- [ ] Commit and tag `v1.0.0`
