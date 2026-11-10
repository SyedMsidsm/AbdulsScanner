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
- [ ] `PerspectiveCrop`: 4-point corner handles, magnifier loupe, self-intersection rejection
- [ ] `Filters` & `AdaptiveThreshold`: Original, Enhanced auto-levels, Grayscale, Bradley-Roth B&W
- [ ] Brightness (-100..100) and Contrast (-100..100) color adjustments
- [ ] Page viewer editing toolbar: Crop, Rotate (+90°), Adjust, Retake, Delete with undo snackbar
- [ ] Page reorder mode on Document screen (drag to reorder)
- [ ] Tags system: Tags management screen (alphabetical grouping, counts, rename, delete), tag bottom sheet on Document, Home tag filter dropdown (All, Ungrouped, per-tag)
- [ ] Home sort order (Updated, Created, Name)
- [ ] Home list/grid view toggle
- [ ] Home select mode (multi-select: tags, share, merge, lock, delete)
- [ ] Merge documents use case (combine pages into new document)
- [ ] PIN lock security: `PinHasher` (PBKDF2), PIN setup/change/verify, session timeout on app background, biometric prompt, forgot PIN via device lock, FLAG_SECURE on locked screens
- [ ] Settings: Scanning, PDF, Security, Appearance (Light/Dark/System theme)
- [ ] Document actions: Export JPG to Pictures, Print via PrintManager, Page size (Auto/A4/Letter)
- [ ] Build & tests for Phase 3
- [ ] Verification of Phase 3 acceptance criteria:
  - [ ] 1. Edits are non-destructive and reversible
  - [ ] 2. Reordering pages updates generated PDF order
  - [ ] 3. Tags filter Home correctly; deleting tag retains documents
  - [ ] 4. Locked document blurred on Home, requires auth, hidden from mirror/search
  - [ ] 5. Forgot PIN resets via device credential
  - [ ] 6. Multi-select merge produces combined document
- [ ] Commit and tag `v0.3.0`

---

## Phase 4 — Text and QR
- [ ] To Text screen: single & batch OCR, language picker (English, Arabic, Both), RTL-aware editable view, save to document, copy, share, save as TXT
- [ ] Background search indexing via `OcrWorker`
- [ ] `SearchRepository` and FTS4 search with Arabic text normalisation (strip diacritics, unify alef/yah/taa, normalize digits)
- [ ] Home search bar with inline query and snippet preview
- [ ] QR code scanning: Google Code Scanner integration (camera)
- [ ] QR from image using ML Kit BarcodeScanning
- [ ] `QrParser` for structured types (URL, Wi-Fi, Text, Phone, Email, SMS, Contact, Geo)
- [ ] QR result screen with tailored actions (open URL, Wi-Fi connect/copy password, phone call, etc.)
- [ ] QR history screen (history list, swipe delete, clear all)
- [ ] Settings: Text reading & QR preferences
- [ ] Build & tests for Phase 4
- [ ] Verification of Phase 4 acceptance criteria:
  - [ ] 1. English and Arabic OCR produce readable text with proper RTL display
  - [ ] 2. Search matches words with Arabic normalization and highlighted snippets
  - [ ] 3. Batch OCR displays progress and allows cancellation
  - [ ] 4. QR actions perform correctly per type; history tracks entries
- [ ] Commit and tag `v0.4.0`

---

## Phase 5 — Special scans
- [ ] ID Card scan flow (`IdCardLayout`): front and back capture, auto-composition onto 2480×3508 A4 300 DPI canvas at real 85.6×54 mm size
- [ ] Book scan flow (`BookSplit`): spread capture, split line adjustment (30%-70%), A/B page labeling, LTR/RTL reading order, gutter trim
- [ ] Document -> More -> Book split for existing pages
- [ ] Collage tool (`CollageLayout`): 2-4 image selection, layout templates (rows/columns/grids), compose on A4 300 DPI
- [ ] Build & tests for Phase 5
- [ ] Verification of Phase 5 acceptance criteria:
  - [ ] 1. ID card prints at exact 1:1 physical size
  - [ ] 2. Book spread splitting correctly assigns first page based on RTL/LTR
  - [ ] 3. Collage arrangements render cleanly on A4
- [ ] Commit and tag `v0.5.0`

---

## Phase 6 — PDF tools
- [ ] Universal tool input picker (App document vs external PDF file via SAF)
- [ ] Output saving to `Documents/Abdul's Scanner/Tools/` with result action sheet
- [ ] PDF Merge tool (app docs & external PDF files via `PDFMergerUtility`)
- [ ] PDF Split tool (page extraction to app doc, or range extraction to external files)
- [ ] PDF Compression tool: Small, Medium, Large image re-encoding presets with size comparison
- [ ] PDF Watermark tool: custom text, Arabic RTL support, size, opacity, angle, color, single or tiled
- [ ] PDF Password tool: 256-bit AES encryption protection & password removal
- [ ] Signature feature: signature drawing pad, smoothing Bézier curves, save up to 5 signatures, draggable/resizable placement, overlay removal
- [ ] PDF Page Reorder tool for external PDF files
- [ ] PDF -> JPG tool: rasterize PDF pages to JPEG in Pictures
- [ ] Import Files tool: import external images or rasterize external PDF pages into new documents
- [ ] Web page -> PDF tool: URL input, WebView rendering, Android print adapter
- [ ] Build & tests for Phase 6
- [ ] Verification of Phase 6 acceptance criteria:
  - [ ] 1. Tools support both app documents and external PDFs
  - [ ] 2. Password encryption locks PDF in third-party viewers; password removal unlocks
  - [ ] 3. Compression reports accurate before/after size and output is readable
  - [ ] 4. Signatures and watermarks can be placed and subsequently removed
  - [ ] 5. Arabic watermark renders with correct ligature shaping and RTL order
- [ ] Commit and tag `v0.6.0`

---

## Phase 7 — Word / Excel -> PDF
- [ ] `DocxToHtml`: memory unzipping, XML parsing of paragraphs, headings, formatting, tables, images, and RTL text
- [ ] `XlsxToHtml`: workbook, shared strings, worksheet tables, multi-sheet rendering, landscape orientation for wide sheets
- [ ] `HtmlPrinter`: invisible WebView print document adapter integration
- [ ] Fallback dialog for unsupported office formats with "Open with Google Docs/Sheets"
- [ ] Build & tests for Phase 7
- [ ] Verification of Phase 7 acceptance criteria:
  - [ ] 1. Word DOCX (including Arabic) converts to printable HTML/PDF
  - [ ] 2. Multi-sheet Excel XLSX converts cleanly
  - [ ] 3. Unsupported PPTX/DOC prompts external office app fallback
- [ ] Commit and tag `v0.7.0`

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
