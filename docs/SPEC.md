# Abdul's Scanner — Complete Build Specification

**Version:** 1.0 · **Date:** 2026-11-10 · **Owner:** Abdul (GitHub: SyedMsidsm) · **Repo:** https://github.com/SyedMsidsm/AbdulsScanner

A personal Android document-scanner app modelled on OKEN (CamScanner-style), with every OKEN feature and none of its ads, logins, premium upsells or data-loss risk. Everything runs on the phone.

This document is the single source of truth. It is written so that an AI coding model (or a developer) who has never seen the conversation that produced it can build the whole app from it, phase by phase.

---

## Table of contents

0. [Rules for whoever builds this](#0-rules-for-whoever-builds-this)
1. [Product summary and ground rules](#1-product-summary-and-ground-rules)
2. [Tech stack and versions](#2-tech-stack-and-versions)
3. [Architecture](#3-architecture)
4. [Project structure](#4-project-structure)
5. [Data model (Room)](#5-data-model-room)
6. [File storage layout](#6-file-storage-layout)
7. [Settings (DataStore)](#7-settings-datastore)
8. [Design system (look and feel)](#8-design-system-look-and-feel)
9. [Navigation map](#9-navigation-map)
10. [Screens, one by one (with wireframes)](#10-screens-one-by-one-with-wireframes)
11. [Feature specifications and algorithms](#11-feature-specifications-and-algorithms)
12. [Backup, restore and data safety](#12-backup-restore-and-data-safety)
13. [Security: PIN lock](#13-security-pin-lock)
14. [Permissions](#14-permissions)
15. [Build, signing and releases](#15-build-signing-and-releases)
16. [Build phases with acceptance criteria](#16-build-phases-with-acceptance-criteria)
17. [Testing](#17-testing)
18. [Out of scope](#18-out-of-scope)
19. [Reference projects and sources](#19-reference-projects-and-sources)
20. [Mockups (how the screens should look)](#20-mockups-how-the-screens-should-look)

---

## 0. Rules for whoever builds this

The owner has no real coding experience. You build the complete app; he then installs it from GitHub Releases, tests it on his phone, and asks for changes. Follow these rules without exception.

1. **Build the whole app in one go.** Build all eight phases in order (section 16) without stopping for the owner's approval in between. For each phase: implement it, verify its acceptance criteria yourself as far as you can (build, unit tests, instrumented tests, lint, an emulator if you have one), commit, tag, update `CHANGELOG.md`, then continue with the next phase. Stop early only for a blocker that only the owner can resolve; even then, keep building everything that doesn't depend on it, and list the blocker in the handover. The owner tests the finished app and then asks for changes.
2. **Never lose user data.**
   - Never use `fallbackToDestructiveMigration()` in Room. Every schema change gets a written `Migration` and a migration test.
   - Never change the `applicationId` (`com.syedmsidsm.abdulsscanner`).
   - Never change the release signing key. Never commit the keystore, its passwords, or `keystore.properties`.
   - Never delete a document's files except through the recycle-bin purge (section 12) or when the owner explicitly retakes or deletes a single page.
3. **No network** except the "Web page to PDF" tool. No analytics, no crash reporting services, no ads SDKs, no accounts, no cloud.
4. **No extra permissions or dependencies** beyond those listed in sections 2 and 14 without asking the owner first and explaining why in plain English.
5. **Never block the UI thread.** Image, PDF, OCR, zip and database work runs on `Dispatchers.IO` / `Dispatchers.Default` or in WorkManager.
6. **All user-visible text in `strings.xml`.** The UI is English. Arabic *content* (scanned text, file names) must display correctly right-to-left.
7. **Plain-English handover at the end.** When everything is built, write in `CHANGELOG.md` and in your reply to the owner: what the app can do, how to install it, one numbered test checklist (all phases' acceptance criteria combined, grouped by area, in plain words he can follow on his phone), what you could not verify yourself, and known limitations. Keep `CHANGELOG.md` updated per phase as you go.
8. **Versions.** Library versions in section 2 were the latest stable on 2026-11-10. If one fails to resolve or build, use the nearest working stable version and note it in `CHANGELOG.md`.
9. **Do not invent features.** If something is not in this spec, ask. If the spec is ambiguous, pick the simplest reading, note it in `CHANGELOG.md`, and mention it in the handover.
10. **Keep this file updated.** If a decision changes during the build, update `docs/SPEC.md` in the same commit.
11. **Follow the mockups.** `docs/mockups/*.png` (section 20) show how the main screens should look. Match their layout, colours and element placement. Where a mockup and this text disagree, this text wins.
12. **Keep `main` green.** Commit often with clear messages. Every commit on `main` must build.

---

## 1. Product summary and ground rules

### 1.1 What the app is

An Android app that turns the phone camera into a document scanner and PDF toolkit:

- Scan documents, books, ID cards and QR codes.
- Read text from scans (English and Arabic), offline.
- Organise scans with tags and full-text search.
- PDF tools: merge, split, compress, watermark, password, signature, reorder, PDF→JPG, import, Word/Excel/web page → PDF.
- Automatic local backups.

Single user (the owner). Not published on the Play Store. Installed by downloading the APK from GitHub Releases on the phone.

### 1.2 Ground rules (each fixes something OKEN gets wrong)

| Rule | Why |
| --- | --- |
| No ads, no premium tier, no pop-ups or upsell banners | OKEN shows "Get 1 Year Premium for FREE" on its home screen |
| No login or account of any kind | OKEN nags "Sign in to sync and keep files safe"; users report WhatsApp OTP logins |
| Files can never silently disappear | An OKEN reviewer lost about 20 PDFs |
| Works offline; everything runs on the phone; no PC or server | Owner's explicit decision |
| Only the permissions it needs (section 14) | OKEN reviewers complain about unnecessary permissions |

### 1.3 Decisions already made (do not reopen)

| Topic | Decision |
| --- | --- |
| Platform | Android only, native Kotlin + Jetpack Compose (same stack as the owner's other app, Ali Educational) |
| App name | Abul's Scanner (renamed from Abdul's Scanner on 2026-11-10; internal IDs unchanged) |
| Scanning camera | Phases 1–7: Google ML Kit Document Scanner (Google's ready-made scanning UI). Phase 8: own CameraX camera with OKEN-style mode slider |
| English OCR | Google ML Kit Text Recognition v2 (Latin), bundled model, offline |
| Arabic OCR | Must be supported, offline. Ship both candidates, Tesseract (`ara+eng`) and PaddleOCR (ONNX), behind a setting, with a Labs screen where the owner compares them on his own papers and picks one. Default: Tesseract. The other engine is removed later, when the owner asks |
| Office → PDF | On the phone. Word (.docx) and Excel (.xlsx) are converted to HTML inside the app and printed to PDF with Android's print dialog. PowerPoint and anything that converts badly opens in Google's Docs/Sheets/Slides app on the phone instead |
| Storage | App-private storage + a copy of every PDF in `Documents/Abdul's Scanner` on the phone |
| Backup | Automatic weekly backup `.zip` to a phone folder the owner picks (keep last 3) + manual backup + restore. Monthly reminder to copy a backup off the phone |
| Login / cloud | None |
| Build process | AI builds the complete app (all 8 phases) in the GitHub repo in one go; the owner then tests it and asks for changes |

---

## 2. Tech stack and versions

Latest stable versions checked on 2026-11-10 (Google Maven, Maven Central, JitPack, Gradle).

### 2.1 Toolchain

| Item | Version |
| --- | --- |
| Gradle wrapper | 9.9.0 |
| Android Gradle Plugin | 9.5.1 |
| Kotlin | 2.4.21 |
| KSP | 2.3.13 (use the release matching the Kotlin version if 2.3.13 fails) |
| JDK | 17 or 21 |
| compileSdk / targetSdk | 37 (Android 17). If the toolchain cannot use 37, use 36 |
| minSdk | 26 (Android 8.0) |

### 2.2 Libraries

| Purpose | Coordinate | Version |
| --- | --- | --- |
| Compose BOM | `androidx.compose:compose-bom` | 2026.09.00 |
| Material 3 | `androidx.compose.material3:material3` | from BOM |
| Material icons (extended) | `androidx.compose.material:material-icons-extended` | from BOM |
| Activity Compose | `androidx.activity:activity-compose` | 1.13.0 |
| Lifecycle (runtime-compose, viewmodel-compose) | `androidx.lifecycle:*` | 2.11.0 |
| Navigation Compose | `androidx.navigation:navigation-compose` | 2.10.2 |
| Room (runtime, ktx, compiler via KSP) | `androidx.room:*` | 2.8.5 |
| DataStore Preferences | `androidx.datastore:datastore-preferences` | 1.3.0 |
| WorkManager | `androidx.work:work-runtime-ktx` | 2.12.0 |
| Biometric | `androidx.biometric:biometric` | 1.1.0 |
| ExifInterface | `androidx.exifinterface:exifinterface` | 1.4.2 |
| DocumentFile (SAF folders) | `androidx.documentfile:documentfile` | latest stable 1.x |
| Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 |
| Coroutines for Google Tasks (`await()`) | `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` | 1.11.0 |
| JSON | `org.jetbrains.kotlinx:kotlinx-serialization-json` (+ serialization plugin) | 1.12.0 |
| Process lifecycle (lock session) | `androidx.lifecycle:lifecycle-process` | 2.11.0 |
| Image loading (thumbnails) | `io.coil-kt.coil3:coil-compose` | 3.6.3 |
| Drag-to-reorder lists/grids | `sh.calvin.reorderable:reorderable` | 3.1.0 |
| Document scanner | `com.google.android.gms:play-services-mlkit-document-scanner` | 16.0.0 |
| Text recognition (Latin, bundled) | `com.google.mlkit:text-recognition` | 16.0.1 |
| QR scanner UI | `com.google.android.gms:play-services-code-scanner` | 16.1.0 |
| Barcode from image (bundled) | `com.google.mlkit:barcode-scanning` | 17.3.0 |
| PDF read/write/merge/encrypt | `com.tom-roush:pdfbox-android` | 2.0.27.0 |
| Arabic OCR candidate A | `cz.adaptech.tesseract4android:tesseract4android` (JitPack) | 4.8.0 (4.9.0's JitPack build currently fails) |
| Arabic OCR candidate B | `com.microsoft.onnxruntime:onnxruntime-android` | 1.22.0 or newer |
| Phase 8 camera | `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` | 1.6.2 |
| Phase 8 edge detection | `org.opencv:opencv` (Maven Central) | latest 4.x |

Repositories: `google()`, `mavenCentral()`, `maven("https://jitpack.io")` (Tesseract only).

**Not used, on purpose:** Hilt/Dagger (manual dependency injection is simpler for this app), Firebase, any analytics or crash SDK, Apache POI (too heavy; we parse .docx/.xlsx XML ourselves), OpenCV before phase 8.

### 2.3 OCR data files (bundled in `assets/`)

| File | Source | Used by |
| --- | --- | --- |
| `tessdata/ara.traineddata` | tesseract-ocr/tessdata_best | Tesseract |
| `tessdata/eng.traineddata` | tesseract-ocr/tessdata_best | Tesseract (`ara+eng` mixed pages) |
| `paddle/det.onnx`, `paddle/rec_arabic.onnx`, `paddle/arabic_dict.txt` | PaddleOCR Arabic mobile models converted to ONNX (see MakeACopy, section 19, for a working Android setup) | PaddleOCR candidate |

Both engines ship until the owner picks one in Labs; he will then ask for the other to be removed (code, assets, dependency).

### 2.4 Device requirement

Google Play services must be present (ML Kit Document Scanner and Code Scanner are delivered through it). The owner's phone has it.

---

## 3. Architecture

- **Single activity** (`MainActivity`) hosting Jetpack Compose with Navigation Compose.
- **MVVM:** each screen has a `ViewModel` exposing `StateFlow<UiState>`; UI sends events by calling ViewModel functions.
- **Repositories** wrap Room, files and settings. ViewModels never touch Room or the file system directly.
- **Manual dependency injection:** `AppContainer` is created in `AbdulsScannerApp.onCreate()` and holds singletons (database, repositories, OCR service, file stores). ViewModels get dependencies through a `ViewModelProvider.Factory` built from the container.
- **WorkManager** for background jobs: OCR indexing, PDF rebuilds, auto-backup, recycle-bin purge.
- **Pure logic is separated** (geometry, layouts, filters on pixel arrays, backup DTOs, DOCX/XLSX parsing, PDF coordinate mapping) so it can be unit-tested without a device.

### 3.1 The core idea: a document is an ordered list of page images

Every document in the app, however it was created (scan, import, ID card, book, collage), is stored as:

- one `documents` row, and
- an ordered list of `pages`, each page having:
  - a **source image** (exactly what the scanner or import gave us; never modified), and
  - **edit instructions** (crop corners, rotation, filter, brightness, contrast, overlays such as a signature), and
  - a **rendered image** = source + edit instructions applied.

The document's PDF is always generated from the rendered images. All edits are therefore non-destructive relative to the source image: the owner can re-crop, change filter, or remove a signature at any time.

(Limitation until phase 8: Google's scanner already crops the photo, so the source image is the scanner's cropped output, not the raw camera photo. Phase 8's own camera stores the raw photo as the source.)

### 3.2 Data flow for a scan

```
Camera button
  → ML Kit Document Scanner (Google UI: capture, auto-crop, filters, cleanup)
  → returns N JPEG URIs
  → SaveScanUseCase:
        copy each JPEG to docs/{docId}/src/{pageId}.jpg   (apply HD setting)
        insert documents row + pages rows (one transaction)
        render each page (default filter) → docs/{docId}/pages/{pageId}.jpg
        make thumbnails → docs/{docId}/thumbs/{pageId}.jpg
        build PDF → docs/{docId}/export/{docId}.pdf
        mirror PDF → Documents/Abdul's Scanner/{name}.pdf
        enqueue OcrWorker(docId) for search indexing
  → open Document screen
```

---

## 4. Project structure

```
AbdulsScanner/
├── .github/workflows/
│   ├── build.yml                  # builds debug APK on every push (checks it compiles)
│   └── release.yml                # builds signed release APK on tag v*, attaches to GitHub Release
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   ├── schemas/                   # Room schema JSON (exportSchema = true), committed to git
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   ├── tessdata/      # ara/eng traineddata (if Tesseract wins)
│       │   │   └── paddle/        # ONNX models (if PaddleOCR wins)
│       │   ├── res/               # strings, icons, themes (XML theme only for splash/status bar)
│       │   └── java/com/syedmsidsm/abdulsscanner/
│       │       ├── AbdulsScannerApp.kt          # Application: AppContainer, PDFBox init, WorkManager schedules
│       │       ├── MainActivity.kt
│       │       ├── di/AppContainer.kt
│       │       ├── data/
│       │       │   ├── db/
│       │       │   │   ├── AppDatabase.kt
│       │       │   │   ├── Converters.kt
│       │       │   │   ├── Migrations.kt
│       │       │   │   ├── entity/  DocumentEntity.kt, PageEntity.kt, TagEntity.kt,
│       │       │   │   │            DocumentTagCrossRef.kt, DocumentSearchFts.kt,
│       │       │   │   │            QrHistoryEntity.kt, SignatureEntity.kt
│       │       │   │   └── dao/     DocumentDao.kt, PageDao.kt, TagDao.kt, SearchDao.kt,
│       │       │   │                QrDao.kt, SignatureDao.kt
│       │       │   ├── files/       FileStore.kt, MirrorStore.kt, SafFolderStore.kt
│       │       │   ├── settings/    SettingsRepository.kt
│       │       │   └── repo/        DocumentRepository.kt, TagRepository.kt,
│       │       │                    SearchRepository.kt, QrRepository.kt, SignatureRepository.kt
│       │       ├── domain/
│       │       │   ├── scan/        SaveScanUseCase.kt, ImportUseCase.kt
│       │       │   ├── imaging/     PageRenderer.kt, Filters.kt, AdaptiveThreshold.kt,
│       │       │   │                PerspectiveCrop.kt, BookSplit.kt, IdCardLayout.kt,
│       │       │   │                CollageLayout.kt, OverlayCompositor.kt, BitmapIo.kt
│       │       │   ├── pdf/         PdfBuilder.kt, PdfTools.kt, PdfRasterizer.kt,
│       │       │   │                PdfCompressor.kt, PdfCoordinates.kt
│       │       │   ├── ocr/         OcrEngine.kt, MlKitLatinEngine.kt, TesseractEngine.kt,
│       │       │   │                PaddleEngine.kt, OcrService.kt
│       │       │   ├── office/      DocxToHtml.kt, XlsxToHtml.kt, HtmlPrinter.kt
│       │       │   ├── qr/          QrParser.kt
│       │       │   ├── backup/      BackupDtos.kt, BackupWriter.kt, BackupReader.kt
│       │       │   └── security/    PinHasher.kt, LockManager.kt
│       │       ├── work/            OcrWorker.kt, PdfRebuildWorker.kt,
│       │       │                    AutoBackupWorker.kt, TrashPurgeWorker.kt
│       │       └── ui/
│       │           ├── theme/       Color.kt, Theme.kt, Type.kt, Shape.kt
│       │           ├── nav/         Routes.kt, AppNavHost.kt, BottomBar.kt
│       │           ├── components/  DocCard.kt, PageThumb.kt, TagChip.kt, ConfirmDialog.kt,
│       │           │                ProgressOverlay.kt, Banner.kt, EmptyState.kt, ...
│       │           └── screens/
│       │               ├── home/ document/ pageeditor/ ocr/ tags/ tools/
│       │               ├── idcard/ book/ collage/ qr/
│       │               ├── pdftools/ (merge, split, compress, watermark, password,
│       │               │              signature, reorder, pdftojpg, importfiles)
│       │               ├── office/ web/ settings/ backup/ trash/ lock/
│       │               └── labs/    # Arabic OCR comparison screen (Settings → Labs)
│       ├── test/                    # JVM unit tests
│       └── androidTest/             # Room migration tests, instrumented tests
├── gradle/libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── keystore.properties.example      # template; the real file is gitignored
├── .gitignore                       # includes keystore.properties, *.jks, *.keystore, local.properties
├── CHANGELOG.md
├── CLAUDE.md                        # short: "Read docs/SPEC.md section 0 before doing anything"
├── NOTICE                           # attributions for any adapted Apache-2.0 code
├── README.md                        # how to install, how to back up the signing key
└── docs/SPEC.md                     # this file
```

---

## 5. Data model (Room)

Database name: `abdulsscanner.db`. `exportSchema = true`, schemas in `app/schemas/`. Start at version 1. Foreign keys ON.

### 5.1 `documents`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | TEXT PK | UUID string |
| `name` | TEXT | Default `Scan yyyy-MM-dd HH:mm` (setting can change pattern) |
| `kind` | TEXT | `SCAN`, `ID_CARD`, `BOOK`, `COLLAGE`, `IMPORT_IMAGES`, `IMPORT_PDF` |
| `createdAt` | INTEGER | epoch millis |
| `updatedAt` | INTEGER | epoch millis; bump on any change to the doc or its pages |
| `pageCount` | INTEGER | denormalised for the list |
| `coverPageId` | TEXT? | first page's id; used for thumbnail |
| `pageSize` | TEXT | `AUTO`, `A4`, `LETTER` (copied from settings at creation, changeable per doc) |
| `locked` | INTEGER (bool) | PIN-protected |
| `deletedAt` | INTEGER? | non-null = in recycle bin |
| `pdfBuiltAt` | INTEGER? | when export PDF was last built; rebuild if `< updatedAt` |
| `mirrorUri` | TEXT? | MediaStore Uri of the copy in `Documents/Abdul's Scanner` |

Indexes: `deletedAt`, `updatedAt`, `name`.

### 5.2 `pages`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | TEXT PK | UUID |
| `documentId` | TEXT FK → documents.id | ON DELETE CASCADE; indexed |
| `position` | INTEGER | 0-based order within document |
| `sourcePath` | TEXT | relative path, e.g. `docs/{docId}/src/{id}.jpg`; never modified after creation |
| `originalPath` | TEXT? | raw camera photo before crop (phase 8 only) |
| `renderedPath` | TEXT | relative path of rendered image |
| `thumbPath` | TEXT | relative path |
| `widthPx`, `heightPx` | INTEGER | of rendered image |
| `cropQuad` | TEXT? | JSON `[x0,y0,x1,y1,x2,y2,x3,y3]`, normalised 0..1 on the source image, clockwise from top-left; null = no extra crop |
| `rotation` | INTEGER | 0, 90, 180, 270 (clockwise, applied after crop) |
| `filter` | TEXT | `ORIGINAL`, `ENHANCED`, `GRAYSCALE`, `BW` |
| `brightness` | INTEGER | -100..100, default 0 |
| `contrast` | INTEGER | -100..100, default 0 |
| `overlays` | TEXT | JSON array of overlays (section 11.12), default `[]` |
| `ocrText` | TEXT? | recognised text |
| `ocrLang` | TEXT? | `EN`, `AR`, `BOTH` used for `ocrText` |
| `ocrStatus` | TEXT | `PENDING`, `DONE`, `FAILED` |
| `createdAt` | INTEGER | |

Unique index on (`documentId`, `position`) is NOT used (reordering would conflict mid-transaction); keep a plain index.

### 5.3 `tags` and `document_tags`

`tags`: `id` INTEGER PK autoincrement, `name` TEXT UNIQUE (case-insensitive: store and compare with `COLLATE NOCASE`), `createdAt` INTEGER.

`document_tags`: (`documentId` TEXT FK cascade, `tagId` INTEGER FK cascade), composite PK.

On first launch insert starter tags: `Business Card`, `ID Card`, `Note`, `PPT`, `Whiteboard`. ID-card scans automatically get the `ID Card` tag.

### 5.4 `document_search` (FTS4)

Room `@Fts4` entity, maintained manually by `SearchRepository` (not `contentEntity`):

| Column | Notes |
| --- | --- |
| `docId` | `@ColumnInfo` with `notIndexed` |
| `name` | document name |
| `body` | all pages' `ocrText` joined with newlines |

Tokenizer: `unicode61` (handles Arabic letters). Update the row whenever the name or any page's OCR text changes; delete it when the document is purged.

### 5.5 `qr_history`

`id` INTEGER PK, `rawValue` TEXT, `format` TEXT, `valueType` TEXT (`URL`, `WIFI`, `TEXT`, `PHONE`, `EMAIL`, `SMS`, `CONTACT`, `GEO`, `OTHER`), `source` TEXT (`CAMERA`, `IMAGE`), `scannedAt` INTEGER.

### 5.6 `signatures`

`id` TEXT PK, `path` TEXT (transparent PNG in `signatures/`), `createdAt` INTEGER. Up to 5 saved signatures.

### 5.7 Key DAO queries

- Home list: documents where `deletedAt IS NULL`, optional tag filter (join `document_tags`), "Ungrouped" filter (no tags), ordered by setting (`updatedAt DESC`, `createdAt DESC`, `name COLLATE NOCASE ASC`). Return `Flow`.
- Tag counts: `SELECT t.*, COUNT(dt.documentId) ...` excluding deleted docs; plus totals for "All docs" and "Ungrouped".
- Search: `SELECT docId, snippet(document_search, '[', ']', '…', -1, 12) FROM document_search WHERE document_search MATCH ?` then join non-deleted documents. Exclude locked documents unless the lock session is open (filter in repository).
- Trash: documents where `deletedAt IS NOT NULL` ordered by `deletedAt DESC`.

---

## 6. File storage layout

All paths below are relative to `context.filesDir` and stored relative in the database (never absolute, so restore works on any phone).

```
filesDir/
├── docs/{docId}/
│   ├── src/{pageId}.jpg        # source image (never modified)
│   ├── orig/{pageId}.jpg       # phase 8: raw camera photo
│   ├── pages/{pageId}.jpg      # rendered image (JPEG quality 90)
│   ├── thumbs/{pageId}.jpg     # 360 px on the long side, quality 80
│   └── export/{docId}.pdf      # current PDF
├── signatures/{id}.png
├── tessdata/                   # copied from assets on first OCR use
└── paddle/                     # copied from assets on first OCR use
cacheDir/tmp/                   # scratch; cleared on app start
```

### 6.1 HD setting

- **HD on:** source image stored exactly as received.
- **HD off (default):** source image downscaled so the long side ≤ 2400 px, JPEG quality 88.
- Always respect EXIF orientation when decoding (use `ExifInterface`), and write upright pixels (no EXIF rotation) to `src/`.

### 6.2 The Documents-folder mirror (`MirrorStore`)

Purpose: every PDF also lives in a normal phone folder so it survives an uninstall.

- Folder: `Documents/Abdul's Scanner/` (create if missing).
- API 29+: insert via `MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)` with `RELATIVE_PATH = "Documents/Abdul's Scanner/"`, `DISPLAY_NAME = "{safe name}.pdf"`, `MIME_TYPE = "application/pdf"`. Save the returned Uri in `documents.mirrorUri`.
- API 26–28: `WRITE_EXTERNAL_STORAGE` (maxSdkVersion 28), write to `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS)/Abdul's Scanner/`. Store a `file://` Uri.
- **Update:** after each PDF rebuild, overwrite via `contentResolver.openOutputStream(uri, "wt")`. If that throws (e.g. the app was reinstalled and no longer owns the file, or the user deleted it), insert a new entry and update `mirrorUri`.
- **Rename** document → update `DISPLAY_NAME`.
- **Move to recycle bin** → delete the mirror file; **restore** → recreate it.
- **Locked document** → delete its mirror file and never mirror it while locked (a locked document must not sit readable in a public folder).
- Safe name: replace `/ \ : * ? " < > |` and control characters with `_`, trim, max 100 chars. MediaStore adds ` (1)` on collisions; that is acceptable.
- Settings has a switch "Keep a copy of PDFs in Documents" (default on).

---

## 7. Settings (DataStore)

| Key | Type | Default | Shown in Settings |
| --- | --- | --- | --- |
| `theme` | `DARK`/`LIGHT`/`SYSTEM` | `DARK` | Appearance |
| `defaultFilter` | filter enum | `ENHANCED` | Scanning |
| `hdMode` | bool | false | Scanning |
| `pageSize` | `AUTO`/`A4`/`LETTER` | `AUTO` | PDF |
| `namePattern` | string | `Scan yyyy-MM-dd HH:mm` | PDF ("File names") |
| `mirrorEnabled` | bool | true | Storage |
| `ocrSearchLanguage` | `EN`/`AR`/`BOTH` | `BOTH` | Text reading ("Language for search indexing") |
| `ocrLastLanguage` | `EN`/`AR`/`BOTH` | `EN` | (remembered by To Text) |
| `arabicEngine` | `TESSERACT`/`PADDLE` | `TESSERACT` | Labs |
| `bookDirection` | `LTR`/`RTL` | `LTR` | Scanning ("Book page order") |
| `sortOrder` | `UPDATED`/`CREATED`/`NAME` | `UPDATED` | (Home) |
| `viewMode` | `LIST`/`GRID` | `LIST` | (Home) |
| `autoBackupEnabled` | bool | false until folder chosen | Backup |
| `autoBackupTreeUri` | string? | null | Backup |
| `autoBackupIntervalDays` | int | 7 | Backup (1, 3, 7, 14) |
| `backupKeepCount` | int | 3 | Backup |
| `lastBackupAt` | long? | null | (shown) |
| `lastOffPhoneReminderAt` | long? | null | — |
| `pinHash`, `pinSalt` | string? | null | Security |
| `biometricEnabled` | bool | false | Security |
| `qrSaveHistory` | bool | true | QR |

---

## 8. Design system (look and feel)

The layout follows OKEN closely (the owner likes it) with all sales clutter removed. Dark theme by default.

### 8.1 Colours

| Token | Dark | Light |
| --- | --- | --- |
| `background` | `#121212` | `#F7F7F5` |
| `surface` (cards, sheets) | `#1E1E1E` | `#FFFFFF` |
| `surfaceVariant` (section headers, chips) | `#2A2A2A` | `#EFEFEC` |
| `primary` (OKEN-like green) | `#13A06F` | `#0F8A5F` |
| `onPrimary` | `#FFFFFF` | `#FFFFFF` |
| `textPrimary` | `#FFFFFF` | `#1A1A1A` |
| `textSecondary` | `#A0A0A0` | `#5F5F5F` |
| `outline` / dividers | `#333333` | `#DDDDDA` |
| `error` | `#E5484D` | `#D13438` |
| `warningBanner` background | `#3A2A12` | `#FFF4E0` |

Camera/scanner overlays (phase 8): black bars `#000000`, mode labels white, selected mode `primary`.

### 8.2 Type, shape, spacing

- Material 3 default type scale (Roboto). Arabic text uses the system Arabic font automatically.
- Text that may contain Arabic (doc names, OCR text, file names) uses `TextStyle(textDirection = TextDirection.Content)`.
- Corner radius: cards 12 dp, chips 8 dp, bottom sheets 16 dp top corners, camera button: circle 64 dp.
- Spacing grid: 4 dp; screen padding 16 dp; list item height ≥ 72 dp.
- Icons: Material Symbols (outlined) from `material-icons-extended`.
- App icon: green (`#13A06F`) rounded square, white stylised page with a folded top-right corner and a scan line across it. Provide adaptive icon (foreground + background) and monochrome layer.

### 8.3 Common components

- **Bottom bar** (Home and Tools only): left "Docs" (document icon + label), centre raised round green camera button (64 dp, white camera icon), right "Tools" (grid icon + label). Selected tab in `primary`.
- **Banner:** full-width rounded card under the top bar, icon + text + one action button; used only for backup reminders and errors. Never for promotions.
- **Progress overlay:** dim background, centred card with spinner, short text ("Building PDF…"), and Cancel where the job is cancellable.
- **Confirm dialog:** title, one sentence, two buttons; destructive action in `error` colour.
- **Snackbar** for undo after delete ("Moved to recycle bin · UNDO").

---

## 9. Navigation map

```
Home (Docs tab) ──┬── Document ──┬── Page viewer/editor
                  │              ├── To Text result
                  │              ├── Signature place
                  │              ├── Watermark
                  │              └── Share/Export sheet
                  ├── Search (inline on Home)
                  ├── Tags
                  ├── Settings ──┬── Backup & restore
                  │              ├── Recycle bin
                  │              ├── Security (PIN)
                  │              └── Labs (Arabic OCR comparison)
                  └── Camera button → ML Kit scanner → Document

Tools tab ──┬── Scan:     ID Card · Scan Docs · Book · QR Code · To Text
            ├── Process:  Import Files · Collage · Watermark · Merge · Compression ·
            │             Word/Excel → PDF · Web page → PDF
            └── PDF:      Signature · Password · Page Reorder · Split · PDF → JPG
QR Code ── QR result ── QR history
Lock screen (shown before any locked document opens)
```

Routes (`Routes.kt`): `home`, `document/{docId}`, `page/{docId}/{pageId}`, `ocr/{docId}?pages={ids}`, `tags`, `tools`, `idcard`, `book/{docId}`, `collage`, `qr`, `qr/result/{id}`, `qr/history`, `tool/merge`, `tool/split`, `tool/compress`, `tool/watermark?docId=`, `tool/password`, `tool/signature?docId=&pageId=`, `tool/reorder`, `tool/pdftojpg`, `tool/import`, `tool/office`, `tool/web`, `settings`, `settings/backup`, `settings/trash`, `settings/security`, `labs/arabic-ocr`.

---

## 10. Screens, one by one (with wireframes)

Wireframes are approximate; follow the element lists. Dark theme shown. Symbols in wireframes stand for icons: `=` settings, `Q` search, `!` warning, `s` sort, `G` grid/tools, `v` select, `#` lock, `T` tag, `/` edit, `X` delete, `^` share, `C` copy, `S` sign, `R` rotate, `+` crop, `*` brightness, `c` contrast, `O` camera, `D` docs, `.` more.

### 10.1 Home (Docs tab)

```
┌──────────────────────────────────────┐
│ =  Abdul's Scanner              Q    │  top bar: settings (left), search (right)
├──────────────────────────────────────┤
│ ! Last backup 9 days ago  [Back up]  │  banner only when needed
├──────────────────────────────────────┤
│ All docs (12) v        s   G   v     │  tag filter · sort · list/grid · select
├──────────────────────────────────────┤
│ ┌─────┐Electricity bill              │
│ │thumb│ 10 Nov 2026 · 2 pages        │
│ └─────┘[Note]                    :   │
│ ┌─────┐Passport copy           #     │
│ │blur │ 08 Nov 2026 · 1 page         │
│ └─────┘[ID Card]                 :   │
│ ...                                  │
├──────────────────────────────────────┤
│   D Docs        ( O )      G Tools   │
└──────────────────────────────────────┘
```

- **Top bar:** settings icon (left), app name, search icon (right). Search icon expands an inline search field; results replace the list (section 11.9) and show a text snippet with the match in bold.
- **Banner area:** backup reminders (section 12.5). Nothing else ever appears here.
- **Filter row:** "All docs (n) ▾" opens a dropdown: All docs, Ungrouped, each tag with count, "Manage tags…" (→ Tags screen). Then icons: sort (menu: Last modified, Date created, Name), list/grid toggle, select mode.
- **List item:** thumbnail (first page; blurred with lock icon if locked), name (1 line, ellipsis), date + page count, tag chips (max 2 + "+n"), overflow ⋮ (Rename, Tags, Share PDF, Lock/Unlock, Move to recycle bin).
- **Grid mode:** 2 columns of portrait thumbnails with name under each.
- **Tap** → Document screen (Lock screen first if locked). **Long-press** → enters select mode with that item selected.
- **Select mode:** top bar shows "n selected", actions: Tags, Share (all PDFs), Merge (≥2, → creates a new document), Lock, Delete. Back exits select mode.
- **Empty state:** illustration of a page, "You don't have any documents yet", outlined button "Scan new docs" (opens scanner).
- **Camera button** → ML Kit scanner in Docs mode (section 11.1).
- No premium banner, no sign-in bar, no promotional tiles.

### 10.2 Document

```
┌──────────────────────────────────────┐
│ ←  Electricity bill /        T  :    │
├──────────────────────────────────────┤
│  ┌──────┐  ┌──────┐  ┌──────┐        │
│  │  1   │  │  2   │  │  +  │         │  page grid (2–3 columns)
│  │      │  │      │  │ Add  │        │
│  └──────┘  └──────┘  └──────┘        │
│                                      │
├──────────────────────────────────────┤
│  ^ Share  T To Text  S Sign  . More  │
└──────────────────────────────────────┘
```

- **Top bar:** back, document name (tap or ✎ to rename; dialog with text field, Arabic-capable), tag icon (bottom sheet with checkboxes for tags + "New tag"), overflow.
- **Page grid:** thumbnails with page number badge. Tap → Page viewer. Long-press → reorder mode (drag to reorder, using `reorderable`; "Done" saves new positions in one transaction). Last tile "＋ Add" → bottom sheet: Scan more pages (ML Kit), Import from gallery.
- **Bottom actions:**
  - **Share** → sheet: Share PDF, Share as JPG (one image per page), Share text (TXT; disabled until OCR is done), Share with password… (asks password → PDF via section 11.13).
  - **To Text** → OCR screen for all pages (language picker first, section 11.7).
  - **Sign** → pick page → Signature place screen (section 11.12).
  - **More** → Export (Save PDF to…, Save JPGs to Pictures), Print, Watermark, Compress, Page size (Auto/A4/Letter), Lock/Unlock, Book split (if `kind = BOOK` or any single page), Move to recycle bin.
- After any change: set `updatedAt`, enqueue `PdfRebuildWorker(docId)`; show a small "Updating PDF…" indicator while pending. Sharing waits for the rebuild to finish.

### 10.3 Page viewer / editor

```
┌──────────────────────────────────────┐
│ ←  Page 2 of 5                 X     │
├──────────────────────────────────────┤
│                                      │
│          (zoomable page image)       │  swipe left/right = other pages
│                                      │
├──────────────────────────────────────┤
│ [Original][Enhanced][Gray][B&W]      │  filter strip (live thumbnails)
│ * ───────o───────   c ─────o─────    │  brightness · contrast (Adjust panel)
├──────────────────────────────────────┤
│ + Crop  R Rotate  * Adjust  O Retake │
└──────────────────────────────────────┘
```

- Pinch-zoom and pan; swipe between pages (HorizontalPager).
- **Crop:** shows the *source* image with 4 draggable corner handles (initialised from `cropQuad` or the image corners) and a magnifier loupe while dragging; "Reset" and "Apply". Saves `cropQuad`.
- **Rotate:** each tap +90°; saves `rotation`.
- **Filter strip:** 4 options with small live previews; selected outlined in `primary`.
- **Adjust:** brightness and contrast sliders −100..100; "Reset".
- **Retake:** launches ML Kit scanner with `pageLimit = 1`; replaces `sourcePath` (old source deleted only after the new one is saved), resets crop/rotation, keeps filter.
- **Delete page** (🗑): confirm; if it was the last page, the whole document moves to the recycle bin.
- Edits render in the background with a short debounce (300 ms) and save immediately; there is no separate Save button. Undo for the last change via snackbar.

### 10.4 To Text (OCR result)

```
┌──────────────────────────────────────┐
│ ←  Text                C Copy  ^     │
├──────────────────────────────────────┤
│ Language: (English) (Arabic) (Both)  │
├──────────────────────────────────────┤
│ --- Page 1 ---                       │
│ ELECTRICITY & WATER AUTHORITY        │
│ Account no. 1234 ...                 │  editable text field, RTL-aware
│                                      │
├──────────────────────────────────────┤
│  Save as TXT     Save to document    │
└──────────────────────────────────────┘
```

- Language chips; changing language re-runs OCR. Remembers last choice (`ocrLastLanguage`).
- Progress per page ("Reading page 2 of 5…") with Cancel.
- Text field is editable; "Save to document" writes the edited text back into each page's `ocrText` (split on the page separators) and updates search.
- Copy (all text to clipboard), Share (plain text), Save as TXT (SAF create document).

### 10.5 Tools tab

```
┌──────────────────────────────────────┐
│               Tools                  │
├──────────────────────────────────────┤
│ Scan                                 │
│ [ID Card] [Scan Docs] [Book]         │
│ [QR Code] [To Text]                  │
│ Process Files                        │
│ [Import Files] [Collage] [Watermark] │
│ [Merge] [Compression] [Word/Excel→]  │
│ [Web page→PDF]                       │
│ PDF Tools                            │
│ [Signature] [Password] [Page Reorder]│
│ [Split PDF] [PDF→JPG]                │
├──────────────────────────────────────┤
│   D Docs        ( O )      G Tools   │
└──────────────────────────────────────┘
```

- Three sections with headers. Tiles in a 3-column grid; each tile = rounded card with a simple illustration/icon and label (OKEN style, but no "Shortcuts" title and no promotional tiles).
- Each PDF tool first asks **what to work on** (section 11.11): "A document in the app" (document picker) or "A PDF file on the phone" (SAF picker).

### 10.6 Tags

Matches OKEN's Tags screen: list grouped alphabetically with letter headers; first rows "All docs (n)" and "Ungrouped (n)"; each tag row shows name and count. Pencil icon (top right) → edit mode: rename / delete tags (delete removes the tag from documents, not the documents). Green FAB "＋" → new tag dialog. Tap a tag → Home filtered by that tag.

### 10.7 QR Code

- Tool tile opens Google Code Scanner directly (section 11.8).
- **Result screen:** big icon for the type, the decoded value (selectable), and type-specific buttons: URL → Open, Copy, Share; Wi-Fi → shows network name, password (hidden with eye toggle), security type, buttons Connect, Copy password; Text → Copy, Share; Phone → Call, Copy; Email → Send email; SMS → Send message; Contact → Add to contacts; Geo → Open in maps. Always: Scan again.
- **History** (top-right icon on result screen and on the QR tile's long-press): list newest first with type icon, value preview, time; swipe to delete; "Clear all".
- **From image:** button "Scan QR from an image" on the result/history screen → photo picker → ML Kit barcode-scanning.

### 10.8 Settings

Sections (in order):

1. **Backup:** "Back up now", "Automatic backup" (switch + folder + interval), "Restore from backup", last backup time.
2. **Scanning:** Default filter, HD scans, Book page order (Left→Right / Right→Left).
3. **PDF:** Page size, File names pattern.
4. **Storage:** Keep a copy of PDFs in Documents (switch), Recycle bin (n items).
5. **Text reading:** Language for search indexing (English / Arabic / Both); "Re-read all pages" (re-queues OCR).
6. **Security:** Set/change/remove PIN, Use fingerprint.
7. **Appearance:** Theme (Dark / Light / System).
8. **QR:** Save scan history.
9. **About:** version, "Open-source licences" (list from NOTICE), link to repo.
10. **Labs:** Arabic OCR comparison and engine choice.

### 10.9 Recycle bin

List of deleted docs with "Deleted 3 days ago · removed in 27 days". Per item: Restore, Delete forever. Top bar: "Empty bin" (confirm).

### 10.10 Lock screen

PIN pad (4–6 digits, dots), fingerprint button if enabled, "Forgot PIN?" link (section 13). Shown before opening a locked document, toggling lock, or opening Security settings.

---

## 11. Feature specifications and algorithms

### 11.1 Scanning with ML Kit Document Scanner (phases 1–7)

```kotlin
val options = GmsDocumentScannerOptions.Builder()
    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL) // crop, filters, stain/finger cleanup
    .setGalleryImportAllowed(true)
    .setPageLimit(pageLimit)            // omit for Docs/Book; 1 for Retake; 2 for ID Card
    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG) // we build PDFs ourselves
    .build()

val scanner = GmsDocumentScanning.getClient(options)
val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        val res = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val uris = res?.pages?.map { it.imageUri }.orEmpty()
        viewModel.onScanned(uris, mode)
    }
}
scanner.getStartScanIntent(activity)
    .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
    .addOnFailureListener { viewModel.onScannerUnavailable(it) }
```

- If the scanner fails to start (no Play services, module still downloading), show: "The scanner is getting ready. Check your internet connection once and try again." with Retry.
- The returned URIs are temporary: copy them into app storage immediately (`SaveScanUseCase`, section 3.2).
- Saving happens in one Room transaction; files are written first to `cacheDir/tmp` then moved into `docs/{docId}/` so a crash never leaves half-saved rows pointing at missing files.

### 11.2 Page rendering pipeline (`PageRenderer`)

Order (always the same):

1. Decode `sourcePath` (respect EXIF; cap the long side at 4000 px).
2. **Perspective crop** if `cropQuad != null` (11.3).
3. **Rotate** by `rotation`.
4. **Filter** (11.4).
5. **Brightness / contrast** (11.4).
6. **Overlays** (11.12): signatures, watermarks.
7. Encode JPEG quality 90 → `renderedPath` (write to temp, then rename).
8. Thumbnail 360 px long side, quality 80 → `thumbPath`.
9. Update `widthPx/heightPx`, document `updatedAt`, enqueue `PdfRebuildWorker`.

Rendering runs on `Dispatchers.Default`, one page at a time (to limit memory). Recycle bitmaps after use.

### 11.3 Perspective crop (`PerspectiveCrop`)

Input: source bitmap, quad `p0..p3` (top-left, top-right, bottom-right, bottom-left) in pixels.

```
outW = max(dist(p0,p1), dist(p3,p2))
outH = max(dist(p0,p3), dist(p1,p2))
scale down so max(outW,outH) ≤ 4000
matrix.setPolyToPoly(
    floatArrayOf(p0.x,p0.y, p1.x,p1.y, p2.x,p2.y, p3.x,p3.y), 0,
    floatArrayOf(0f,0f, outW,0f, outW,outH, 0f,outH), 0, 4)
canvas(outBitmap).drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
```

Crop UI: handles must stay inside the image; reject self-intersecting quads (show the outline in `error` colour and disable Apply). Unit-test the size math.

### 11.4 Filters (`Filters`, `AdaptiveThreshold`)

All operate on ARGB pixels. Pure functions over `IntArray` where possible so they can be unit-tested.

| Filter | Algorithm |
| --- | --- |
| `ORIGINAL` | No change |
| `ENHANCED` | (1) Auto-levels: build a luminance histogram from a ≤512 px downsample; `low` = 2nd percentile, `high` = 98th percentile; map each channel linearly `[low, high] → [0, 255]` (clamp). (2) `ColorMatrix` contrast ×1.15 around 128 and saturation ×1.10. Result: whiter paper, darker ink, colours kept |
| `GRAYSCALE` | `Y = 0.299R + 0.587G + 0.114B`, then the same auto-levels on Y |
| `BW` | Grayscale → Bradley–Roth adaptive threshold using an integral image: window `s = max(15, width / 12)` (odd), threshold `t = 0.15`; pixel = black if `Y * s² ≤ sum * (1 − t)`, else white |

Brightness `b` (−100..100): add `b × 1.28` to R, G, B. Contrast `k` (−100..100): factor `f = (100 + k) / 100`, `c' = (c − 128) × f + 128`. Apply via one `ColorMatrix` after the filter. Clamp 0..255.

### 11.5 Building PDFs (`PdfBuilder`)

Use PdfBox-Android (call `PDFBoxResourceLoader.init(applicationContext)` once in `Application.onCreate`). Embed the rendered JPEGs directly (no re-encoding) with `JPEGFactory.createFromStream`.

```kotlin
PDDocument().use { pdf ->
    for (page in pages) {
        val img = FileInputStream(rendered(page)).use { JPEGFactory.createFromStream(pdf, it) }
        val box = pageBox(img.width, img.height, doc.pageSize) // PDRectangle in points
        val p = PDPage(box); pdf.addPage(p)
        PDPageContentStream(pdf, p).use { cs ->
            val r = fitCentered(img.width, img.height, box, margin = marginFor(doc.pageSize))
            cs.drawImage(img, r.x, r.y, r.w, r.h)
        }
    }
    pdf.save(tmpFile)
}
tmpFile.renameTo(exportFile) // atomic replace
```

Page sizes (points, 1 pt = 1/72 inch):

| Setting | Portrait image | Landscape image | Margin |
| --- | --- | --- | --- |
| `AUTO` | width 595.28, height = 595.28 × h / w | height 595.28, width = 595.28 × w / h | 0 |
| `A4` | 595.28 × 841.89 | 841.89 × 595.28 | 18 |
| `LETTER` | 612 × 792 | 792 × 612 | 18 |

Set PDF metadata: Title = document name, Producer = "Abdul's Scanner". `PdfRebuildWorker` (unique work `pdf-{docId}`, policy REPLACE) builds the PDF, sets `pdfBuiltAt`, then updates the mirror.

### 11.6 Sharing, exporting, printing

- **FileProvider** authority `${applicationId}.fileprovider`, paths: `files/docs/`, `cache/share/`.
- **Share PDF:** copy `export/{docId}.pdf` to `cache/share/{safe name}.pdf` (so the receiving app sees the real name), `ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION`. Multiple docs → `ACTION_SEND_MULTIPLE`.
- **Share as JPG:** copy rendered pages to `cache/share/{safe name} - p{n}.jpg`.
- **Save PDF to…:** SAF `CreateDocument("application/pdf")`.
- **Save JPGs to Pictures:** MediaStore `Pictures/Abdul's Scanner/{safe name}/`.
- **Print:** `PrintManager.print(name, PdfPrintAdapter(exportFile), null)` where `PdfPrintAdapter` streams the existing PDF in `onWrite`.
- Clear `cache/share/` on app start.

### 11.7 Text reading (OCR)

#### Engines

```kotlin
interface OcrEngine {
    val id: String
    suspend fun recognize(bitmap: Bitmap): String
}
```

- **`MlKitLatinEngine`** — `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` (bundled model). `process(InputImage.fromBitmap(bmp, 0)).await()` (add `org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0`). Join `textBlocks` sorted top-to-bottom, then left-to-right, blocks separated by blank lines.
- **`TesseractEngine`** (candidate A) — copy `assets/tessdata/*.traineddata` to `filesDir/tessdata/` on first use. `TessBaseAPI().init(filesDir.absolutePath, "ara+eng", TessBaseAPI.OEM_LSTM_ONLY)`, `pageSegMode = PSM_AUTO`. Preprocess: grayscale; if image height < 1800 px upscale ×1.5. One shared instance guarded by a `Mutex`; call `clear()` after each page; `recycle()` on app shutdown.
- **`PaddleEngine`** (candidate B) — ONNX Runtime with PaddleOCR text detection (DB) + Arabic recognition (CTC) models. Pipeline: resize to multiple of 32 (max side 960) → detection → boxes (unclip, min area) → crop each box (perspective, rotate tall boxes) → resize to height 48 → recognition → greedy CTC decode with `arabic_dict.txt`. Order lines top-to-bottom; within a line order boxes right-to-left when Arabic, left-to-right otherwise. Check on real samples whether recognised Arabic strings come out in visual (reversed) order and fix to logical order if so. MakeACopy (Apache-2.0, section 19) has a working Android PaddleOCR implementation that may be adapted with attribution in `NOTICE`.

#### Language routing (`OcrService`)

| Language chosen | Engine |
| --- | --- |
| English | `MlKitLatinEngine` |
| Arabic | Winning Arabic engine |
| Both | Winning Arabic engine (Tesseract runs `ara+eng`; Paddle: run Arabic engine, then also ML Kit on the same page and append Latin lines not already present) |

#### Arabic engine comparison (Settings → Labs)

1. Owner picks 5–10 real pages (from documents or gallery) containing Arabic, some mixed with English.
2. For each page run: ML Kit (reference for English), Tesseract `ara+eng`, Paddle. Record text and time in ms.
3. Show results side by side per page, scrollable, RTL-aware, with a 1–5 star rating per result ("how much of the text is correct").
4. Summary screen: average stars and average time per engine. Button "Use Tesseract" / "Use PaddleOCR" sets `arabicEngine`.
5. **Timebox:** if PaddleOCR cannot be made to work within a reasonable effort, ship Tesseract only, hide the Paddle option, and say so in the handover.
6. Both engines stay in the app until the owner picks one and asks for the other to be removed.

#### Search indexing (`OcrWorker`)

- Unique work `ocr-{docId}`, policy APPEND_OR_REPLACE, constraint `batteryNotLow`.
- Processes pages with `ocrStatus = PENDING` using `ocrSearchLanguage`. On success store `ocrText`, `ocrLang`, `DONE`; on failure `FAILED` (retry once later).
- After each page, rebuild the document's `document_search.body`.
- New or retaken pages are set to `PENDING`. Filter/crop changes do not re-trigger OCR (text doesn't change). "Re-read all pages" in Settings sets all to `PENDING`.

#### Search normalisation (apply identically to indexed text and queries)

- Lower-case (Latin).
- Arabic: remove tashkeel (U+064B–U+0652, U+0670) and tatweel (U+0640); `أ إ آ ٱ → ا`; `ى → ي`; `ة → ه`; `ؤ → و`; `ئ → ي`.
- Convert Arabic-Indic digits `٠–٩` and Persian digits `۰–۹` to `0–9`.
- Query: split on whitespace; strip `" * ( ) : ^ -`; each token → `"token"*`; join with spaces (AND). Empty → show normal list.

### 11.8 QR codes

- **Camera:** `GmsBarcodeScanning.getClient(context, GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).enableAutoZoom().build()).startScan()`. Needs no camera permission.
- **From image:** photo picker → `InputImage.fromFilePath` → `BarcodeScanning.getClient()` (bundled `barcode-scanning`). Multiple codes → let the owner pick one.
- **Parse** with `Barcode.valueType` into the types in section 5.5 (`QrParser`, unit-tested with sample raw strings such as `WIFI:T:WPA;S:Home;P:secret;;`).
- **Wi-Fi Connect:** API 30+: `Intent(Settings.ACTION_WIFI_ADD_NETWORKS)` with `EXTRA_WIFI_NETWORK_LIST` containing a `WifiNetworkSuggestion` (system dialog, no permission). API < 30: copy password to clipboard and open `Settings.ACTION_WIFI_SETTINGS`.
- Save to `qr_history` if `qrSaveHistory`.

### 11.9 Tags, sorting, select mode, rename

As described in screens 10.1, 10.2, 10.6. Default name uses `namePattern` with `SimpleDateFormat` (Locale.ENGLISH digits). Renaming re-mirrors and updates search.

### 11.10 Special scans

#### ID Card (`IdCardLayout`)

1. Tool tile → intro sheet: illustration, "Scan the front, then the back. Both sides go on one A4 page at real size." → Start.
2. ML Kit with `pageLimit = 2`. If 1 page returns: dialog "Only one side scanned." [Scan back side] (ML Kit, limit 1) / [Save front only].
3. Compose on a white A4 canvas at 300 DPI: **2480 × 3508 px**. Card size ISO/IEC 7810 ID-1 = 85.6 × 54 mm = **1011 × 638 px**. Front rect: x = (2480 − 1011)/2 = **734**, y = **600**. Back rect: x = 734, y = 600 + 638 + 240 = **1478**. Each side scaled to fit its rect keeping aspect ratio, centred; rotate a portrait side 90° first so it fills the landscape rect.
4. Save as a 1-page document: `kind = ID_CARD`, `pageSize = A4`, tag `ID Card`, filter `ORIGINAL`.
5. Unit-test the rectangle math.

#### Book (`BookSplit`)

1. Tool tile → hint: "Lay the book open and flat. Scan both pages together; you'll split them next."
2. ML Kit (no page limit): each captured image is one spread.
3. **Book split screen** per spread: image with a vertical green dashed line, draggable between 30% and 70% of width (starts at 50%), big translucent "A" and "B" letters on the two halves (A = first page in reading order), "Apply this line to all spreads" checkbox, Next / Done.
4. Split with a gutter trim of 1.5% of width on each side of the line. Output order per spread: `LTR` → left, right; `RTL` (Arabic books) → right, left. Setting `bookDirection`; also a toggle on this screen.
5. Save as `kind = BOOK`. Curved text near the spine is **not** flattened (known limitation).
6. Also available on any existing page: Document → More → Book split.

#### Collage (`CollageLayout`)

- Pick 2–4 images (photo picker or pages from a document).
- Layouts: 2 images → 2 rows / 2 columns; 3 images → 3 rows / 1 large + 2 small; 4 images → 2×2 grid / 4 rows.
- Canvas A4 300 DPI (2480 × 3508), white, outer margin 120 px, gap 60 px. Each image fit-centred in its cell.
- Live preview; Save → new 1-page document `kind = COLLAGE`, `pageSize = A4`.

### 11.11 PDF tools: inputs and outputs

```kotlin
sealed interface ToolInput {
    data class AppDoc(val docId: String) : ToolInput
    data class PdfFile(val uri: Uri, val displayName: String) : ToolInput
}
```

Every PDF tool starts with a source sheet: **A document in the app** (document picker with search) or **A PDF file on the phone** (`OpenDocument("application/pdf")`). Encrypted PDFs prompt for the password (PdfBox `InvalidPasswordException` → "Wrong password").

When a tool produces a **new file**, save it to `Documents/Abdul's Scanner/Tools/{name} ({suffix}).pdf` via MediaStore, then show a result sheet: Open (`ACTION_VIEW`), Share, Add to app (import as document), Done.

| Tool | On an app document | On a PDF file |
| --- | --- | --- |
| **Merge** (pick ≥ 2, reorder the list) | All inputs app docs → new app document with copies of all pages (edits kept) | Any input is a file → output file with `PDFMergerUtility` (app docs contribute their export PDF) |
| **Split** | "Extract pages": select pages → new document (copies); option "Remove them from the original" | Ranges like `1-3, 5, 8-10`, or "Every page separately" → one file per range |
| **Compression** | Choose Small / Medium / Large → output file; document unchanged | Re-encode embedded images (below) → output file |
| **Watermark** | Adds a watermark overlay to every page (removable later) | Stamp onto every page → output file |
| **Password** | Output protected PDF file; document unchanged | Add password, or remove password (needs current one) → output file |
| **Signature** | Adds signature overlay to the chosen page | Choose page (thumbnails via `PdfRenderer`), place signature, stamp → output file |
| **Page Reorder** | Opens the document's reorder mode | Thumbnail grid: drag to reorder, tap ✕ to delete → output file |
| **PDF → JPG** | Copies rendered pages to `Pictures/Abdul's Scanner/{name}/` | Renders pages with `PdfRenderer` at 200 DPI into the same folder |

**Compression presets:**

| Preset | Max long side | JPEG quality |
| --- | --- | --- |
| Small | 1240 px | 50 |
| Medium | 1754 px | 65 |
| Large | 2480 px | 80 |

For a PDF file: for each page, for each `PDImageXObject` in its resources (skip images with masks or transparency, skip if already smaller than the target), decode, downscale, re-encode with `JPEGFactory.createFromImage(doc, bitmap, quality/100f)`, and `resources.put(name, newImage)`. Text and vectors are untouched. Show "3.05 MB → 1.52 MB". If the result is not smaller, say "This PDF is already compact" and keep the original.

**Password:** `StandardProtectionPolicy(ownerPassword = random 32 chars, userPassword = input, AccessPermission())`, `encryptionKeyLength = 256`, `pdf.protect(policy)`. Remove: `PDDocument.load(file, password)`, `isAllSecurityToBeRemoved = true`, save. Password field with show/hide and confirm.

**Import Files** (Tools → Import Files, and Document → Add → Import):
- Images (multi-select photo picker or `OpenMultipleDocuments("image/*")`) → new document `IMPORT_IMAGES`, filter `ORIGINAL`.
- PDF → (password if needed) → render each page with `PdfRenderer` at 200 DPI (cap 4000 px) to JPEG → new document `IMPORT_PDF`. Tell the owner: "Pages become images; the app reads their text again for search." Limit: warn above 100 pages.

**PDF coordinate mapping (`PdfCoordinates`)** — overlays use normalised top-left coordinates (x, y, w, h in 0..1). For a page with media box W × H and `/Rotate` r:
- r = 0: `px = x·W`, `py = H − (y + h)·H`, size `w·W × h·H`.
- r = 90, 180, 270: transform the normalised rectangle into the unrotated page space first (rotate the unit square by −r), then apply the r = 0 formula.
Unit-test all four rotations.

### 11.12 Overlays (signatures and watermarks)

`pages.overlays` JSON (kotlinx.serialization, polymorphic by `type`):

```json
[
  {"type":"SIGNATURE","signatureId":"…","x":0.62,"y":0.81,"w":0.28,"rotation":0},
  {"type":"WATERMARK","text":"Confidential","sizePct":6,"opacity":25,"angle":45,"color":"#808080","tiled":false}
]
```

- Coordinates are normalised to the page image after crop/rotate/filter (before overlays). If the page is re-cropped later, overlays keep their normalised position (may shift slightly; acceptable).
- **Signature pad:** full-width canvas (aspect 2.5:1), finger drawing smoothed with quadratic Béziers between touch points, stroke 6 px, ink colour black or blue, Clear / Save. Save as transparent PNG trimmed to the ink bounding box + 16 px padding. Keep up to 5 signatures; manage (delete) from the pad screen.
- **Placing a signature:** page image with the signature as a draggable, pinch-resizable box (min width 5% of the page); Done adds the overlay.
- **Watermark options:** text (Arabic allowed), size S/M/L = 4/6/9% of page width, opacity 10–60% (default 25), angle 0/30/45 (default 45), colour grey/red/blue, single centred or tiled. Draw with Android `Canvas` + `StaticLayout` (so Arabic shapes correctly).
- **Managing overlays:** Page viewer → "Signatures & watermarks" lists overlays on the page with Remove. Document → More → Watermark → "Remove watermark from all pages".
- For **PDF files**, render the overlay to a transparent PNG with the same code, then stamp with PdfBox `LosslessFactory.createFromImage` + `PDPageContentStream(…, AppendMode.APPEND, true, true)`.

### 11.13 Word / Excel / web page → PDF (on the phone)

#### Flow

1. Tools → Word/Excel → PDF → pick a file (`OpenDocument` with `.docx`, `.xlsx` MIME types).
2. Convert to HTML in the app (below), load it in an off-screen `WebView`, and on `onPageFinished` call:
   ```kotlin
   printManager.print(jobName, webView.createPrintDocumentAdapter(jobName),
       PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4 /* landscape for wide sheets */).build())
   ```
   Keep a reference to the `WebView` until the print job finishes.
3. Android's print screen opens; the owner chooses **Save as PDF** and a location.
4. Back in the app, a sheet offers: "Add the saved PDF to Abdul's Scanner" (opens a PDF picker → Import) or Done.
5. Unsupported types (`.pptx`, `.doc`, `.xls`, `.ppt`, `.odt`, …), or if the owner taps "Doesn't look right?": show "Open it in Google Docs, Sheets or Slides on your phone, then use Share → Print → Save as PDF" with an **Open with…** button (`ACTION_VIEW` + `FLAG_GRANT_READ_URI_PERMISSION`).

#### `DocxToHtml`

Unzip in memory (`ZipInputStream`); parse `word/document.xml` with `XmlPullParser`:

| DOCX element | HTML |
| --- | --- |
| `w:p` | `<p dir="auto">` |
| `w:pStyle` = `Title`, `Heading1`–`Heading6` | `<h1>`–`<h6>` |
| `w:r` with `w:b`, `w:i`, `w:u`, `w:sz` (half-points) | `<b>`, `<i>`, `<u>`, `style="font-size:{sz/2}pt"` |
| `w:t`, `w:tab`, `w:br` | text (HTML-escaped), `&emsp;`, `<br>` |
| `w:bidi` / `w:rtl` | `dir="rtl"` |
| `w:numPr` (consecutive paragraphs) | `<ul><li>` (bullets) or `<ol>` if the numbering format is decimal |
| `w:tbl` / `w:tr` / `w:tc` | `<table>` / `<tr>` / `<td>` with 1 px borders |
| `w:drawing` → `a:blip r:embed` | resolve via `word/_rels/document.xml.rels` → `word/media/*` → `<img src="data:…;base64,…" style="max-width:100%">` |

Ignore headers, footers, footnotes, comments, text boxes. CSS: `@page { size: A4; margin: 20mm } body { font-family: sans-serif; font-size: 11pt }`.

#### `XlsxToHtml`

- `xl/workbook.xml` → sheet names and `r:id`; `xl/_rels/workbook.xml.rels` → sheet file paths.
- `xl/sharedStrings.xml` → string table (`si/t` and rich `si/r/t`).
- Each `xl/worksheets/sheetN.xml`: rows `row`, cells `c` with `r` (A1 ref), `t` (`s` shared, `str`, `inlineStr`, `b`, `n`), `v`. Place cells by column letter; fill gaps with empty cells.
- Numbers: print as stored, trimming a trailing `.0`. Dates stay as raw serial numbers (known limitation; noted in the UI).
- One `<h2>{sheet name}</h2><table>` per sheet; page break between sheets; landscape if any sheet has more than 6 used columns.

#### Web page → PDF

- Tools → Web page → PDF: URL field (prepend `https://` if no scheme) → Load in a visible `WebView` (JavaScript on, file access off, no geolocation) → Print → same print flow.
- This is the **only** feature that uses the internet (`INTERNET` permission).

### 11.14 Phase 8: own camera (CameraX + OpenCV)

- Full-screen camera matching OKEN's layout: top bar icons (flash auto/on/off, HD toggle, grid overlay, settings), bottom mode slider **Book · To Text · Docs · ID Card · QR Code** (swipe or tap; selected in `primary`), shutter, Cancel (✕), Import (gallery), Import Files.
- Mode overlays: Book → horizontal hint + green dashed binding line + "A/B"; ID Card → card-shaped green corner brackets + "Front page" / "Back page" pills; To Text → "OCR in batch" pill; QR → square brackets + moving scan line + "Turn light on"; Docs → "Searching doc…" pill until a document is found, then the live quad outline.
- Live edge detection on ~640 px analysis frames: grayscale → Gaussian blur 5×5 → Canny (50/150) → dilate → `findContours` → largest convex 4-point polygon by `approxPolyDP` (ε = 2% of perimeter) with area > 20% of the frame. Smooth the quad over the last 5 frames.
- Auto-capture when the quad is stable (movement < 2% for 1 second); manual shutter always available.
- Store the full-resolution raw photo in `orig/`, the auto-cropped result in `src/` with the detected quad saved so the owner can re-crop from the raw photo (fixes the phase 1–7 limitation).
- Setting "Scanner: Built-in camera / Google scanner" so the ML Kit flow remains available as a fallback.
- Adds the `CAMERA` permission (requested on first use).

---

## 12. Backup, restore and data safety

### 12.1 Backup file format (version 1)

File name: `AbdulsScanner-backup-yyyyMMdd-HHmm.zip`

```
manifest.json
data.json
files/docs/{docId}/src/{pageId}.jpg
files/docs/{docId}/orig/{pageId}.jpg        (phase 8, if present)
files/docs/{docId}/pages/{pageId}.jpg
files/signatures/{id}.png
```

`manifest.json`:

```json
{
  "format": "abdulsscanner-backup",
  "formatVersion": 1,
  "appVersionCode": 200,
  "createdAt": 1762770000000,
  "documentCount": 42,
  "pageCount": 131,
  "fileCount": 280
}
```

`data.json`: `{ "documents": [...], "pages": [...], "tags": [...], "documentTags": [...], "qrHistory": [...], "signatures": [...] }` using dedicated `@Serializable` DTOs in `BackupDtos.kt` (never serialise Room entities directly, so database changes don't break old backups). Thumbnails and export PDFs are not included (they are rebuilt). Settings, PIN and the Documents-folder mirror are not included.

### 12.2 Writing a backup (`BackupWriter`)

1. Snapshot the database inside a read transaction into DTOs.
2. Write the zip to `cacheDir/tmp/backup.zip` (store JPEG/PNG entries with `ZipEntry.STORED`, JSON with DEFLATED).
3. Verify: reopen the zip, check `manifest.json` parses and the entry count equals `fileCount + 2`.
4. Copy to the destination (SAF tree folder for automatic backups; `CreateDocument` location for manual), then verify the destination file size equals the temp file size.
5. Only after success: update `lastBackupAt`, and delete older automatic backups in that folder beyond `backupKeepCount` (only files matching the name pattern).
6. Show progress with page counts; backups of large libraries can take a while.

### 12.3 Restoring (`BackupReader`)

1. Owner picks a `.zip` (`OpenDocument`). Validate `format` and `formatVersion` (refuse newer versions with a clear message).
2. Show a summary: "42 documents, 131 pages, made 10 Nov 2026 13:25. Restore?"
3. Restore is **additive**: documents whose `id` already exists are skipped (reported as "already on this phone"); others are inserted with their files. Tags merge by name. QR history and signatures merge by id.
4. Write files first (to temp, then move), then insert rows in one transaction.
5. Enqueue `PdfRebuildWorker` for each restored document (rebuilds PDFs, thumbnails and mirror copies). OCR text comes from the backup; no re-reading needed.
6. Final message: "Restored 40 documents. 2 were already on this phone."

### 12.4 Automatic backup (`AutoBackupWorker`)

- Settings → Backup → Automatic backup: choose folder (`OpenDocumentTree`, `takePersistableUriPermission` read+write), interval (1/3/7/14 days, default 7), keep last 3.
- `PeriodicWorkRequest` (unique `auto-backup`, policy UPDATE), constraints: `storageNotLow`, `batteryNotLow`.
- Skip if nothing changed since `lastBackupAt` (compare max `updatedAt` and counts).
- If folder permission is lost or writing fails: post a notification (requires `POST_NOTIFICATIONS` on API 33+, asked when enabling automatic backup) and show the Home banner "Automatic backup failed — choose the folder again".
- Note in the UI: Google Drive folders usually cannot be chosen here (Drive doesn't expose folders to this picker); use a phone folder or SD card.

### 12.5 Reminders (Home banner)

| Condition | Banner | Action |
| --- | --- | --- |
| Documents exist and no backup ever, or last backup > 7 days ago | "Last backup 9 days ago" / "You haven't backed up yet" | Back up now |
| Automatic backup failed | "Automatic backup failed" | Fix |
| A backup exists and `lastOffPhoneReminderAt` > 30 days ago | "Copy your newest backup off this phone (USB stick or another device) so a lost phone doesn't mean lost scans." | Got it (sets `lastOffPhoneReminderAt`) |

At most one banner at a time, in the order above.

### 12.6 Recycle bin (`TrashPurgeWorker`)

- Delete = set `deletedAt`, delete mirror copy, snackbar with Undo.
- Daily periodic worker removes documents with `deletedAt` older than 30 days: delete files (`docs/{docId}/`), rows (cascade), FTS row.
- "Delete forever" / "Empty bin" do the same immediately after confirmation.

### 12.7 Other data-safety rules

- Room: `exportSchema = true`; migrations only; `MigrationTestHelper` test for every version step.
- File writes: temp file + rename everywhere.
- App updates never clear data. Never call `clearApplicationUserData`.
- Low storage: before saving a scan or backup, check free space (`StatFs`); if < 200 MB show a warning; if < 50 MB refuse with a clear message.
- `android:allowBackup="false"` (Android's own cloud backup would ignore our size and privacy rules; our own backup is the supported path). Also set `dataExtractionRules` to exclude everything.

---

## 13. Security: PIN lock

- **Set PIN:** 4–6 digits, entered twice. Store `PBKDF2WithHmacSHA256` hash (120 000 iterations, 16-byte random salt, 256-bit output), Base64, in DataStore. Never store the PIN itself.
- **Fingerprint (optional):** `BiometricPrompt` with `BIOMETRIC_STRONG`; on success counts as PIN entry.
- **Locking a document:** requires a PIN to exist (prompt to create one). Locked docs: blurred thumbnail with lock icon on Home; hidden from search results and snippets until unlocked; not mirrored to the Documents folder (existing mirror deleted on lock, recreated on unlock).
- **Session:** after a successful unlock, locked docs stay open until the app goes to the background (`ProcessLifecycleOwner` ON_STOP).
- **Forgot PIN:** confirm the phone's own screen lock (`BiometricPrompt` with `DEVICE_CREDENTIAL` on API 30+; `KeyguardManager.createConfirmDeviceCredentialIntent` on API 26–29), then set a new PIN. Locked documents stay locked under the new PIN. If the phone has no screen lock, the PIN cannot be reset (tell the owner this when he sets the PIN).
- Backups contain locked documents unencrypted (warn once when locking the first document: "Backup files are not password-protected. Keep them somewhere safe.").
- `FLAG_SECURE` on screens that show a locked document (prevents screenshots and the recent-apps preview).

---

## 14. Permissions

| Permission | Why | When |
| --- | --- | --- |
| `INTERNET` | Web page → PDF only | Install-time (normal permission) |
| `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="28"`) | Documents-folder mirror on Android 8–9 | Asked on first save on those versions |
| `POST_NOTIFICATIONS` | Automatic-backup failure notice (Android 13+) | Asked when enabling automatic backup |
| `USE_BIOMETRIC` | Fingerprint unlock | Normal permission |
| `CAMERA` | Phase 8 own camera only | Asked on first use in phase 8 |

Not needed: camera before phase 8 (ML Kit scanner and code scanner use Google Play services' camera), storage read (we use the photo picker and SAF), contacts, location, phone.

After each build, inspect the merged manifest (`app/build/intermediates/merged_manifest/`). Remove any extra permission added by a library with `tools:node="remove"`, and list the final permissions in the phase handover.

---

## 15. Build, signing and releases

The owner installs builds on his phone by downloading the APK from GitHub Releases. No computer is needed on his side.

### 15.1 Identity

- `applicationId` / `namespace`: `com.syedmsidsm.abdulsscanner` (never changes).
- App name: `Abdul's Scanner`.
- `versionCode` = phase × 100 + build number within the phase (phase 1 → 101, 102, …). `versionName` = `0.{phase}.{build}`; 1.0.0 after phase 8.

### 15.2 Signing key (do this first, at the very start)

1. Generate a release keystore once:
   `keytool -genkeypair -v -keystore abdulsscanner-release.jks -alias abdulsscanner -keyalg RSA -keysize 4096 -validity 36500`
2. Give the owner the `.jks` file and the passwords, and tell him in plain words to keep **two copies** in different places (for example a USB stick and a private cloud folder) plus the passwords in a password manager. Losing the key means future updates cannot install over the old app; the old app would have to be uninstalled, which deletes its data.
3. Add GitHub repository secrets (owner can do this in the GitHub website on his phone; give step-by-step instructions): `KEYSTORE_BASE64` (base64 of the .jks), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
4. `app/build.gradle.kts` reads signing config from environment variables (CI) or `keystore.properties` (local). If neither is present, **release builds fail** with a clear error. Never fall back to the debug key for release.
5. The keystore and passwords are never committed (`.gitignore`: `*.jks`, `*.keystore`, `keystore.properties`).
6. Do steps 1–3 **at the very start** and hand the key and instructions to the owner immediately, then continue building while he adds the secrets. If the secrets are still missing when you tag a release, the release job must skip signing gracefully (debug APK still uploaded as an artifact) and you list this in the handover.

### 15.3 GitHub Actions

- `build.yml`: on every push and pull request: JDK 17, Gradle cache, `./gradlew testDebugUnitTest lintDebug assembleDebug`, upload the debug APK as a workflow artifact.
- `release.yml`: on tag `v*`: if `KEYSTORE_BASE64` is empty, upload the debug APK and stop with a clear message; otherwise decode `KEYSTORE_BASE64` to a temp file, `./gradlew assembleRelease`, rename to `AbdulsScanner-{versionName}.apk`, create a GitHub Release with the APK attached and the phase's CHANGELOG section as notes.
- Release build: R8 minify on, resource shrinking on. Add keep rules for PdfBox-Android, Tesseract4Android / ONNX Runtime, kotlinx.serialization, and ML Kit as needed; test the **release** APK, not only debug.

### 15.4 Installing on the phone (put in README, plain English)

1. Open the repo's **Releases** page on the phone, tap the newest `AbdulsScanner-x.y.z.apk`.
2. First time only: allow your browser to "install unknown apps".
3. Tap **Update/Install**. Updating keeps all scans (same signing key).

---

## 16. Build phases with acceptance criteria

Build every phase in order **without waiting for the owner** (rule 1). Each phase ends with: green CI, a commit tagged `v0.{phase}.0`, and a `CHANGELOG.md` entry. Verify each acceptance criterion yourself wherever possible (tests, emulator). Criteria marked **(owner)** need his phone, papers or judgement: put them in the final checklist. After phase 8, tag `v1.0.0`, publish the release APK, and write the handover (rule 7).

### Phase 1 — Basic scanner (+ Arabic OCR comparison)

**Build:**
- Project setup exactly as sections 2–4; theme (section 8); `AppContainer`; Room v1 with all tables from section 5 (create them all now to avoid early migrations); DataStore settings; GitHub Actions; signing (15.2).
- Home (list mode, sorting by last modified, empty state, overflow: Rename, Share PDF, Move to recycle bin), bottom bar, Tools tab placeholder showing all tiles with "Coming in phase N" labels.
- Camera button → ML Kit scanner → save pipeline (3.2, 11.1, 11.2 with default filter, 11.5).
- Document screen: page grid, rename, Share PDF, Add pages (scan more), Move to recycle bin (soft delete; recycle bin screen comes in phase 2).
- Page viewer: view and swipe only (no editing yet).
- Documents-folder mirror (6.2).
- Settings → Labs → Arabic OCR comparison (11.7) with ML Kit, Tesseract and PaddleOCR.
- App icon.

**Acceptance:**
1. Scan a 3-page document; it appears on Home with thumbnail, name, date, "3 pages".
2. The PDF opens in another app with pages in the right order and correct orientation.
3. The same PDF appears in `Documents/Abdul's Scanner` in the phone's Files app.
4. Rename updates Home, the mirror file name, and the shared file name.
5. Add pages appends to the existing document.
6. Closing the app mid-save never leaves a broken document (verify by force-stopping during save).
7. **(owner)** The comparison runs on the owner's Arabic pages and his engine choice is saved and used.
8. Merged manifest contains only `INTERNET` (and `WRITE_EXTERNAL_STORAGE` max 28).
9. **(owner)** The release APK installs, and a later build updates over it without losing documents.

### Phase 2 — Backup and safety

**Build:** manual backup, restore, automatic backup + folder picker, reminders/banners (12), recycle bin screen + purge worker, low-storage checks, Room migration test setup, Settings screen (Backup, Storage sections; others greyed "coming soon").

**Acceptance:**
1. Back up, uninstall, reinstall, restore → every document, page, name and tag returns; PDFs rebuild.
2. Restoring the same backup twice adds nothing the second time.
3. Automatic backup writes a file to the chosen folder (test with interval forced to 15 minutes in a debug-only setting) and keeps only the last 3.
4. Deleted documents appear in the recycle bin, restore correctly (mirror recreated), and are purged after 30 days (test with a debug-only time override).
5. Backup reminder banner appears and disappears per 12.5.

### Phase 3 — Editing and organising

**Build:** page editor (crop with quad handles, rotate, filters, brightness/contrast, retake, delete), page reorder (drag) and delete in Document, tags (Tags screen, tag sheet, Home filter incl. Ungrouped, counts), sort options, list/grid toggle, select mode (tags, share, merge, lock, delete), PIN lock + fingerprint + forgot PIN (13), Settings: Scanning, PDF, Security, Appearance (light theme), export JPG, print, page size per document.

**Acceptance:**
1. Every edit is undoable by re-editing (e.g. change filter back to Original restores the look; reset crop restores the scanner's crop).
2. Reordering pages changes the PDF order.
3. Tags filter Home correctly; counts are correct; deleting a tag keeps the documents.
4. A locked document: blurred on Home, needs PIN/fingerprint, not in the Documents folder, not in search.
5. Forgot PIN works via the phone's screen lock.
6. Merging two documents from select mode creates a third with all pages.

### Phase 4 — Text and QR

**Build:** To Text screen (single, batch, language choice, edit, save back, copy/share/TXT), background OCR indexing, search with Arabic normalisation and snippets (11.7), Share text, QR scanner, result types, Wi-Fi connect, history, QR from image (11.8), Settings: Text reading, QR.

**Acceptance:**
1. English and Arabic pages produce readable text; Arabic displays right-to-left.
2. Searching a word printed on a receipt finds it; Arabic search finds words regardless of hamza/alef variants and tashkeel.
3. Batch OCR on 5 pages shows progress and can be cancelled.
4. QR codes for a URL, Wi-Fi and plain text each show the right actions; history saves and deletes.

### Phase 5 — Special scans

**Build:** ID Card (11.10), Book with split screen + RTL order, Collage, Document → More → Book split.

**Acceptance:**
1. ID card PDF printed at 100% scale measures 85.6 × 54 mm per side (owner can check with a ruler on a printout or by comparing with the real card on screen at A4 zoom).
2. An Arabic book spread with RTL order produces the right page first.
3. Collage layouts for 2, 3 and 4 images look correct.

### Phase 6 — PDF tools

**Build:** source picker (11.11), Merge, Split, Compression, Watermark, Password (add/remove), Signature (pad, saved signatures, placement, overlays list), Page Reorder for files, PDF → JPG, Import Files (images and PDF), Web page → PDF, Tools-folder output + result sheet.

**Acceptance:**
1. Each tool works on both an app document and an external PDF (where the table in 11.11 says so).
2. A password-protected PDF asks for the password in another PDF app; removing the password works.
3. Compression shows before/after size and the result opens correctly.
4. Signature and watermark can be removed later from app documents.
5. Arabic watermark text renders correctly (joined letters, right-to-left).

### Phase 7 — Word / Excel → PDF

**Build:** `DocxToHtml`, `XlsxToHtml`, `HtmlPrinter`, unsupported-type fallback (11.13).

**Acceptance:**
1. A simple Word letter (headings, bold, a bullet list, a table, an image) and an Arabic Word document convert with readable layout.
2. A two-sheet Excel file prints both sheets with headings.
3. A `.pptx` shows the "Open with Google Slides" fallback.

### Phase 8 — Own camera

**Build:** CameraX camera with mode slider and overlays, live edge detection, auto-capture, raw photo storage and re-crop from raw (11.14), scanner choice setting.

**Acceptance:**
1. All five modes reachable from the slider and behave like their tool tiles.
2. Re-crop can now expand beyond the original crop (uses the raw photo).
3. Google scanner still available via the setting.
4. Version becomes 1.0.0.

---

## 17. Testing

### 17.1 Unit tests (JVM, `src/test`)

- `PerspectiveCrop` output size math; quad validation (self-intersection).
- `Filters`: auto-levels percentile mapping on synthetic pixel arrays; `AdaptiveThreshold` on a synthetic gradient with text-like blocks.
- `IdCardLayout`, `CollageLayout`, `BookSplit` rectangles (incl. RTL order and gutter trim).
- `PdfCoordinates` for rotations 0/90/180/270.
- Search normalisation (Arabic variants, digits) and query building.
- `QrParser` for URL, Wi-Fi, text, phone, email, SMS, geo samples.
- `DocxToHtml` / `XlsxToHtml` against small fixture files in `src/test/resources` (including an Arabic `.docx`).
- Backup DTO round-trip (serialize → deserialize → equal) and manifest validation (reject newer `formatVersion`).
- `PinHasher` (same PIN + salt → same hash; different salt → different hash).

### 17.2 Instrumented tests (`src/androidTest`)

- Room `MigrationTestHelper` for every schema version.
- DAO queries: tag counts, Ungrouped, trash, FTS search.
- Backup write → wipe → restore on a test database and temp folder.

### 17.3 Manual checklist

Each phase's acceptance list (section 16) is the owner's checklist. Also test on the release APK: rotate the phone on every screen; switch dark/light theme; turn on airplane mode (everything except Web page → PDF must work); fill a document with 50+ pages to check speed and memory.

---

## 18. Out of scope

Do not build these (ask the owner if he requests them later):

- Editing the existing text inside a PDF.
- Cloud sync, accounts, sharing links, comment collection.
- PowerPoint conversion inside the app; legacy `.doc`, `.xls`, `.ppt` conversion.
- Searchable (text-layer) PDFs. (Possible later addition: invisible text layer for Latin text.)
- Flattening curved book pages.
- Handwriting recognition beyond what the OCR engines do; translation.
- iPhone, tablet-specific layouts, Wear OS.
- Ads, analytics, crash reporting, in-app purchases.
- An Arabic-language app interface (strings are ready for it, but only English is in scope).

---

## 19. Reference projects and sources

| Name | Use | Licence and rule |
| --- | --- | --- |
| [OKEN on Google Play](https://play.google.com/store/apps/details?id=com.cambyte.okenscan) | Feature list and layout inspiration (owner's screenshots of camera modes, Home, Tags, Tools) | Proprietary: copy no assets, icons or text |
| [MakeACopy](https://github.com/egdels/makeacopy) | Working offline OCR (Tesseract and PaddleOCR via ONNX), Room FTS search, OpenCV edge detection on Android | Apache-2.0: code may be adapted with attribution in `NOTICE` |
| [FairScan](https://github.com/pynicolas/FairScan) | Ideas for a simple, private scanning flow | GPLv3: read for ideas only; do **not** copy code |
| [ML Kit Document Scanner](https://developers.google.com/ml-kit/vision/doc-scanner) | Scanner API, modes, options | Google terms |
| [ML Kit Text Recognition v2](https://developers.google.com/ml-kit/vision/text-recognition/v2) | Latin OCR; note it does not support Arabic script | Google terms |
| [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android) | Tesseract on Android | Apache-2.0 |
| [tessdata_best](https://github.com/tesseract-ocr/tessdata_best) | `ara`, `eng` models | Apache-2.0 |
| [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) | PDF building and tools | Apache-2.0 |
| [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) | Arabic recognition models | Apache-2.0 |

---

## 20. Mockups (how the screens should look)

The folder `docs/mockups/` contains PNG mockups of the main screens (dark theme, 390 × 844 dp at 2× scale), plus `00-overview.png` showing them all together. They were drawn from this spec. Follow them for layout, colours, spacing and element placement (rule 11). They are illustrations: sample data (bill amounts, names, Arabic text) is made up, icons are Material Symbols, and anything not shown follows the text of this spec. Where a mockup and the text disagree, the text wins.

| File | Screen |
| --- | --- |
| `00-overview.png` | All screens on one sheet |
| `01-home.png` | Home (Docs tab) |
| `02-home-empty.png` | Home – empty state |
| `03-search.png` | Search inside documents |
| `04-select-mode.png` | Select mode |
| `05-document.png` | Document |
| `06-share-sheet.png` | Document – Share sheet |
| `07-page-editor.png` | Page viewer / editor |
| `08-crop.png` | Crop (4 corner handles) |
| `09-to-text.png` | To Text (OCR result) |
| `10-tools.png` | Tools tab |
| `11-tags.png` | Tags |
| `12-book-split.png` | Book split |
| `13-id-card.png` | ID Card result (one A4 page) |
| `14-qr-result.png` | QR result (Wi-Fi) |
| `15-compression.png` | PDF tool – Compression |
| `16-signature.png` | Signature placement |
| `17-settings.png` | Settings |
| `18-backup.png` | Backup & restore |
| `19-recycle-bin.png` | Recycle bin |
| `20-lock.png` | Lock screen (PIN) |
| `21-labs-arabic-ocr.png` | Labs – Arabic OCR comparison |
| `22-camera-phase8.png` | Own camera (phase 8) |
| `23-watermark.png` | Watermark |
| `24-collage.png` | Collage |

Screens without a mockup (follow sections 10 and 11): Document overflow menus, Tags sheet, ID Card intro, Import Files, Split, Password, Page Reorder for files, PDF → JPG, Word/Excel → PDF, Web page → PDF, QR history, Security settings, light theme.

---

*End of specification.*
