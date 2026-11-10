# Changelog

All notable changes to **Abdul's Scanner** will be documented in this file.

## [Unreleased]

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
