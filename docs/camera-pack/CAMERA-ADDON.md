# Abdul's Scanner — Camera and filters add-on

**Date:** 2026-11-10 · Adds to `docs/SPEC.md`. Where this file and the spec disagree, **this file wins** for the topics it covers.

It adds the camera controls and filters the owner uses in OKEN:

- three new filters (**No shadow**, **Lighten**, **Eco**), and Magic Color done properly
- a **filter panel** on the camera screen
- **flash menu** with **Torch**
- **photo size** (resolution) menu
- **grid lines**
- a **camera settings panel**: Auto shutter, Auto crop images, Crop images after each scan, Mute mode

No new libraries or permissions are needed. Everything uses what the spec already lists (CameraX incl. `camera-camera2`, Android platform APIs).

---

## 1. What goes where

| Part | Section | Build in | Replaces / changes in SPEC.md |
| --- | --- | --- | --- |
| 7 filters with the new algorithms | 2 | **Phase 3** (page editor) | 11.4 filter table; filter strip in 10.3; `defaultFilter` options in 7 / 10.8 |
| Flash menu incl. Torch | 3.2 | Phase 8 | "flash auto/on/off" in 11.14 |
| Photo size menu | 3.3 | Phase 8 | "HD toggle" on the camera in 11.14 |
| Filter panel on camera | 3.4 | Phase 8 | (new) |
| Grid lines | 3.5 | Phase 8 | "grid overlay" in 11.14 |
| Camera settings panel + its 4 switches | 3.6 | Phase 8 | "Auto-capture" and "settings" in 11.14 |
| Crop-after-scan screen | 3.7 | Phase 8 | (new) |
| New settings rows | 4 | Phases 3 and 8 | Settings table (7) and Settings → Scanning (10.8) |

If the phase is already built when you receive this, do it now.

While the app uses **Google's scanner** (phases 1–7, or later when the owner picks it in Settings), Google's screen has its own flash and auto-capture that we can't change. The 7 filters still apply to every page in the page editor and as the default filter.

---

## 2. The seven filters

### 2.1 List, order and labels

Show filters in **this order** everywhere (page editor strip, camera filter panel, Default filter setting):

| # | Label | Stored value (`pages.filter`) | Notes |
| --- | --- | --- | --- |
| 1 | No shadow | `NO_SHADOW` | Small red **BETA** badge on its thumbnail (like OKEN) |
| 2 | Original | `ORIGINAL` | |
| 3 | Lighten | `LIGHTEN` | |
| 4 | Magic Color | `ENHANCED` | Same stored value as before, new label and algorithm |
| 5 | Grayscale | `GRAYSCALE` | Unchanged |
| 6 | B&W | `BW` | Updated algorithm |
| 7 | Eco | `ECO` | |

- `pages.filter` is a TEXT column, so **no Room migration** is needed. Add the new values to the Kotlin enum. Unknown values read from an old backup fall back to `ORIGINAL`.
- `defaultFilter` default stays `ENHANCED` (shown as "Magic Color").
- Brightness/contrast sliders still apply after the filter, as in spec 11.4.

### 2.2 Algorithms (exact)

`filters/reference_filters.py` is the **reference implementation**. Port each function 1:1 to Kotlin (`domain/imaging/Filters.kt`, pure functions on `IntArray` pixels plus a small `Bitmap` scaling helper). The parameters below are the ones in the reference file.

Shared building blocks:

- **Luminance** `Y = 0.299R + 0.587G + 0.114B`.
- **Percentile levels:** luminance percentiles measured on a downsample (long side ≤ 512 px); map `[low, high] → [0, 255]` on each channel, clamped.
- **Background estimate** (`estimateBackground`), per channel:
  1. scale down with area averaging so the long side is **256 px**;
  2. **max filter 7 × 7** (removes dark ink, keeps paper brightness);
  3. **box blur radius 6, twice**;
  4. scale back up to full size with bilinear filtering.
- **Remove shadows:** `out = c / max(background, 1) × 245`, per channel, clamped.
- **Saturation** like `ColorMatrix.setSaturation`; **contrast** `(c − pivot) × f + pivot`.

| Filter | Steps |
| --- | --- |
| No shadow | remove shadows → levels with low = 1st percentile, high = max(**90th** percentile, 200) (paper becomes pure white) → contrast 1.05 around 160 |
| Original | unchanged |
| Lighten | levels with low = ½ × 2nd percentile, high = 98th percentile → gamma 0.75 (`255 × (x/255)^0.75`) → saturation 1.05. Shadows are softened, not removed |
| Magic Color | remove shadows → levels (2nd, max(90th, 200)) → saturation **1.35** → contrast **1.15** around 150 |
| Grayscale | luminance → levels (2nd, 98th) |
| B&W | remove shadows → luminance → Bradley–Roth adaptive threshold (window = max(15, width/12) odd, **t = 0.20**) |
| Eco | remove shadows → luminance → levels (1st, 90th) → `y = 255 − (255 − y) × 0.55` (ink 45% lighter) → values above 225 become 255. Saves printer ink |

### 2.3 Speed and memory

- Work on a `IntArray` of the image capped at 4000 px on the long side (as in spec 11.2). The background estimate runs on a 256 px image, so it is fast; the full-size step is one pass per pixel.
- Process in horizontal bands (e.g. 256 rows at a time) when building the output, to avoid holding several full-size float arrays.
- Target: **under 1.5 s for a 12 MP page** on a mid-range phone. Thumbnails for the filter strip/panel are made from the 360 px thumbnail, never from the full image.

### 2.4 Golden tests (required)

`filters/samples/` contains `test-photo.jpg` (a receipt photo with uneven light and a hand shadow), the reference output of every filter as lossless PNG (`filter-no_shadow.png`, `filter-lighten.png`, …), and `filters-comparison.png`. The references were made by running `reference_filters.py` on `test-photo.jpg` exactly as stored.

Add a unit test (Robolectric or instrumented) that decodes `test-photo.jpg`, runs each Kotlin filter, and compares with the matching PNG:

- colour and grey filters: **mean absolute difference ≤ 3** and **99th-percentile difference ≤ 20** (0–255 scale);
- B&W: **at most 1% of pixels** differ.

A faithful port lands far inside these limits (a test with different but reasonable resize and blur methods stayed under MAD 0.6). Anything larger means the port is wrong.

---

## 3. Camera controls (phase 8, own CameraX camera)

### 3.1 Top bar

Order, left to right: **Flash** · **Photo size (HD)** · **Filter** · **Grid** · **Settings** (Material Symbols `flash_auto` / `flash_on` / `flash_off` / `flashlight_on`, `hd`, `bubble_chart`, `grid_on` / `grid_off`, `settings`). Black bar, white icons, 28 dp.

Popups (flash, photo size, filter, settings):
- Only one open at a time. Tapping its icon again, tapping outside, or Back closes it. While open, its icon is tinted `primary`.
- Style: panel `#3A3A3A` at 96% opacity, corner radius 6 dp, shadow; rows 46 dp high with a 1 dp divider (`#FFFFFF` at 8%); radio buttons on the right. The settings panel is full-width and black (see mockup).
- The live preview, edge detection and auto shutter **pause while a popup is open**.

### 3.2 Flash menu

| Option | Implementation |
| --- | --- |
| Flash Auto (default) | `ImageCapture.flashMode = FLASH_MODE_AUTO`, torch off |
| Flash On | `FLASH_MODE_ON`, torch off |
| Flash Off | `FLASH_MODE_OFF`, torch off |
| Torch | `FLASH_MODE_OFF` and `cameraControl.enableTorch(true)` (light stays on while aiming) |

- The top-bar icon shows the current mode. A small "Torch on" chip appears at the top-right of the preview while the torch is on.
- Remember the choice (`cameraFlash`). Turn the torch **off** whenever the camera screen closes or the app goes to the background; turn it back on when returning if Torch is still selected.
- Hide this menu if the phone has no flash (`cameraInfo.hasFlashUnit()`).
- QR mode keeps its own "Turn light on" button (torch), as in spec 11.14.

### 3.3 Photo size menu (the HD icon)

- Header row: "Photo size · 4:3 · pixels".
- List the back camera's JPEG output sizes: `Camera2CameraInfo.from(cameraInfo).getCameraCharacteristic(SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG)`. Keep **4:3** sizes (|w/h − 4/3| < 0.02) that are **at least 1600 × 1200**; sort by area, largest first; remove duplicates; max 8 entries.
- Label: `"{round(w×h/1 000 000)}M ({w}x{h})"`, e.g. `12M (4080x3060)`.
- Apply with `ResolutionSelector` (`AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY`, `ResolutionStrategy(size, FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)`). Default = largest. Remember (`cameraPhotoSize`, `"WxH"`). If the phone can't use the stored size, fall back and show the size actually used as selected.
- Photos from the built-in camera are kept at this size (still capped at 4000 px long side by spec 11.2). The **HD scans** setting now applies only to Google's scanner and to imports. Settings shows "HD scans" under a "Google scanner and imports" note.
- Hidden in QR mode.

### 3.4 Filter panel

- 4-column grid of the 7 filters (section 2.1 order), each a small thumbnail with its label under it; No shadow has the BETA badge; the selected one has a 2 dp `primary` border and green label.
- Thumbnails show the **current view**: when the panel opens, take the latest `ImageAnalysis` frame, crop to the detected page outline if there is one, scale to 160 px, run each filter on a background thread, and fill the tiles in as they finish (grey placeholder until then).
- The chosen filter is used for every page captured afterwards in this session and is saved as `defaultFilter`.
- Hidden in QR mode.

### 3.5 Grid lines

Toggles a 3 × 3 grid over the preview: 1 dp lines, black at 55% opacity (like OKEN). Icon `grid_on`/`grid_off`. Remember (`cameraGrid`, default off).

### 3.6 Settings panel (gear)

Full-width black panel sliding down from under the top bar, four switches, and a hint "Also in Settings → Scanning". Changes save immediately.

| Switch | Default | When ON | When OFF |
| --- | --- | --- | --- |
| **Auto shutter** | On | Takes the photo by itself when a page outline is steady (movement < 2% for 1 s, spec 11.14). Shows "Hold still… capturing". After a capture, wait at least 1.5 s **and** until the page outline moves by more than 10% or disappears before the next auto capture, so the same page isn't captured twice | Only the shutter button takes photos |
| **Auto crop images** | On | Saves the detected page outline as the crop (raw photo kept in `orig/`, spec 11.14) | Saves the whole photo; `cropQuad` = null. The owner can still crop later in the page editor |
| **Crop images after each scan** | On | After every capture, open the **Crop-after-scan** screen (3.7) | Captures go straight into the batch; a "Done (n)" chip with the last page's thumbnail appears above the shutter; tapping it finishes |
| **Mute mode** | Off | No sound | Plays `MediaActionSound.SHUTTER_CLICK` on capture (CameraX itself makes no sound). Some countries' phones force a shutter sound; we can't change that |

### 3.7 Crop-after-scan screen

Shown after each capture when "Crop images after each scan" is on.

- Title "Page {n} · adjust crop"; the photo with the 4 corner handles (detected outline, or the full photo if none), same handle behaviour as the page editor's Crop (spec 10.3).
- Buttons: **Retake** (discard this photo, back to camera), **Rotate** (90°), **Next page** (save with this crop, back to camera), and **Done (n pages)** (save and open the Document screen).
- The chosen filter is applied when the page is saved (rendering pipeline, spec 11.2).

---

## 4. New settings

Add to the settings table (spec section 7) and to Settings → **Scanning** (spec 10.8), in this order: Scanner, Default filter, Photo size, Flash, Grid lines, Auto shutter, Auto crop images, Crop images after each scan, Mute mode, Book page order, HD scans (Google scanner and imports).

| Key | Type | Default |
| --- | --- | --- |
| `defaultFilter` | 7 values (2.1) | `ENHANCED` |
| `cameraFlash` | `AUTO` / `ON` / `OFF` / `TORCH` | `AUTO` |
| `cameraPhotoSize` | `"WxH"` or null | null (= largest 4:3) |
| `cameraGrid` | bool | false |
| `autoShutter` | bool | true |
| `autoCrop` | bool | true |
| `cropAfterEachScan` | bool | true |
| `muteShutter` | bool | false |

Settings rows that only work with the built-in camera (Photo size, Flash, Grid, the four switches) show "Built-in camera only" as their subtitle while the Scanner setting is "Google scanner".

---

## 5. Mockups

`mockups/` (390 × 844 dp at 2×). Match their layout; where a mockup and this text disagree, the text wins.

| File | Shows |
| --- | --- |
| `00-camera-overview.png` | All screens below on one sheet |
| `cam-01-flash-menu.png` | Flash menu (Auto / On / Off / Torch) |
| `cam-02-photo-size.png` | Photo size menu |
| `cam-03-filter-panel.png` | Filter panel with live thumbnails |
| `cam-04-settings-panel.png` | Camera settings panel (grid lines on behind it) |
| `cam-05-scanning.png` | Auto shutter capturing, torch on, grid on |
| `cam-06-crop-after-scan.png` | Crop-after-scan screen |
| `edit-07-filter-strip.png` | Page editor with the 7 filters |
| `settings-08-scanning.png` | Settings → Scanning with the new rows |

---

## 6. Acceptance checks (add to the owner's final checklist)

1. **Filters (phase 3):** the page editor shows all 7 filters in the right order with live thumbnails; No shadow has the BETA badge; golden tests pass.
2. **(owner)** On a page photographed with his hand's shadow on it, No shadow and Magic Color remove the shadow.
3. Flash Auto/On/Off change the photo's flash; Torch keeps the light on while aiming and turns off when leaving the camera.
4. Photo size lists real sizes for the phone; the saved photo has the chosen size.
5. The filter panel shows the current view through each filter; the chosen filter is applied to the next pages.
6. Grid lines toggle on and off and are remembered.
7. Auto shutter captures a steady page once, not repeatedly; with it off, only the button captures.
8. Auto crop off saves the whole photo; Crop after each scan on shows the crop screen after every photo; off shows the "Done (n)" chip.
9. Mute mode on: no shutter sound; off: shutter sound.
10. All camera settings are remembered after closing the app, and match Settings → Scanning.
