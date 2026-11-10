#!/usr/bin/env python3
"""Reference implementation of Abdul's Scanner page filters (NumPy + Pillow).

The Android app must implement the SAME steps in Kotlin (see CAMERA-ADDON.md, section 3).
Port these functions 1:1 and unit-test the Kotlin version against images produced here.

All functions take and return a uint8 RGB array of shape (H, W, 3).
"""
import numpy as np
from PIL import Image, ImageFilter

# ---------- shared helpers ----------

def luminance(rgb):
    """Y = 0.299 R + 0.587 G + 0.114 B, float32."""
    rgb = rgb.astype(np.float32)
    return 0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]

def percentile_levels(y, lo_pct=2.0, hi_pct=98.0, sample_long_side=512):
    """Return (low, high) luminance percentiles measured on a downsample."""
    h, w = y.shape
    step = max(1, int(max(h, w) / sample_long_side))
    s = y[::step, ::step]
    return float(np.percentile(s, lo_pct)), float(np.percentile(s, hi_pct))

def apply_levels(rgb, low, high):
    """Map [low, high] -> [0, 255] on every channel, clamped."""
    rgb = rgb.astype(np.float32)
    scale = 255.0 / max(1.0, high - low)
    return np.clip((rgb - low) * scale, 0, 255)

def saturation(rgb, factor):
    """Scale saturation around per-pixel luminance (same as Android ColorMatrix.setSaturation)."""
    rgb = rgb.astype(np.float32)
    y = luminance(rgb)[..., None]
    return np.clip(y + (rgb - y) * factor, 0, 255)

def contrast(rgb, factor, pivot=128.0):
    return np.clip((rgb.astype(np.float32) - pivot) * factor + pivot, 0, 255)

def estimate_background(rgb, work_long_side=256, max_size=7, blur_radius=6):
    """Paper/lighting estimate per channel.

    1. Downscale (area average) so the long side is `work_long_side` px.
    2. Max filter (`max_size` x `max_size`) per channel: removes dark ink, keeps paper brightness.
    3. Blur (box blur of radius `blur_radius`, applied twice ~ Gaussian) to smooth it.
    4. Upscale back to full size with bilinear filtering.
    """
    h, w = rgb.shape[:2]
    k = work_long_side / max(h, w)
    sw, sh = max(1, round(w * k)), max(1, round(h * k))
    small = Image.fromarray(rgb.astype(np.uint8)).resize((sw, sh), Image.BOX)
    small = small.filter(ImageFilter.MaxFilter(max_size))
    small = small.filter(ImageFilter.BoxBlur(blur_radius)).filter(ImageFilter.BoxBlur(blur_radius))
    big = small.resize((w, h), Image.BILINEAR)
    return np.asarray(big).astype(np.float32)

def remove_shadows(rgb, target=245.0):
    """Divide each channel by the background estimate: shadows and uneven light disappear."""
    bg = estimate_background(rgb)
    out = rgb.astype(np.float32) / np.maximum(bg, 1.0) * target
    return np.clip(out, 0, 255)

def to_u8(x):
    return np.clip(np.rint(x), 0, 255).astype(np.uint8)

# ---------- the seven filters ----------

def f_original(rgb):
    return rgb.copy()

def f_no_shadow(rgb):
    """No shadow: flat white paper, original ink colours."""
    out = remove_shadows(rgb)
    lo, hi = percentile_levels(luminance(out), 1.0, 90.0)     # 90th pct = paper -> pure white
    out = apply_levels(out, lo, max(hi, 200.0))
    return to_u8(contrast(out, 1.05, 160))

def f_lighten(rgb):
    """Lighten: brighter mid-tones, colours kept, shadows softened (not removed)."""
    lo, hi = percentile_levels(luminance(rgb))
    out = apply_levels(rgb, lo * 0.5, hi)                       # gentle levels
    out = 255.0 * np.power(out / 255.0, 0.75)                   # gamma lift
    return to_u8(saturation(out, 1.05))

def f_magic_color(rgb):
    """Magic Color (stored as ENHANCED): shadow-free white paper + punchy colours and dark ink."""
    out = remove_shadows(rgb)
    lo, hi = percentile_levels(luminance(out), 2.0, 90.0)     # paper -> pure white
    out = apply_levels(out, lo, max(hi, 200.0))
    out = saturation(out, 1.35)
    out = contrast(out, 1.15, 150)
    return to_u8(out)

def f_grayscale(rgb):
    y = luminance(rgb)
    lo, hi = percentile_levels(y)
    y = np.clip((y - lo) * 255.0 / max(1.0, hi - lo), 0, 255)
    return to_u8(np.repeat(y[..., None], 3, axis=2))

def f_bw(rgb, t=0.20):
    """B&W: shadow removal, then Bradley-Roth adaptive threshold on luminance."""
    y = luminance(remove_shadows(rgb))
    h, w = y.shape
    s = max(15, (w // 12) | 1)
    ii = np.pad(y, ((1, 0), (1, 0))).cumsum(0).cumsum(1)
    r = s // 2
    ys, xs = np.arange(h), np.arange(w)
    y0, y1 = np.clip(ys - r, 0, h), np.clip(ys + r + 1, 0, h)
    x0, x1 = np.clip(xs - r, 0, w), np.clip(xs + r + 1, 0, w)
    area = (y1 - y0)[:, None] * (x1 - x0)[None, :]
    total = ii[y1][:, x1] - ii[y0][:, x1] - ii[y1][:, x0] + ii[y0][:, x0]
    black = y * area <= total * (1 - t)
    out = np.where(black, 0, 255).astype(np.uint8)
    return np.repeat(out[..., None], 3, axis=2)

def f_eco(rgb):
    """Eco: ink-saving print version. White paper, text kept readable but printed as mid-grey."""
    y = luminance(remove_shadows(rgb))
    lo, hi = percentile_levels(y, 1.0, 90.0)
    y = np.clip((y - lo) * 255.0 / max(1.0, hi - lo), 0, 255)
    y = 255.0 - (255.0 - y) * 0.55                             # lighten all ink by 45%
    y = np.where(y > 225, 255.0, y)                            # pure white paper
    return to_u8(np.repeat(y[..., None], 3, axis=2))

FILTERS = [
    ("NO_SHADOW", "No shadow", f_no_shadow),
    ("ORIGINAL", "Original", f_original),
    ("LIGHTEN", "Lighten", f_lighten),
    ("ENHANCED", "Magic Color", f_magic_color),
    ("GRAYSCALE", "Grayscale", f_grayscale),
    ("BW", "B&W", f_bw),
    ("ECO", "Eco", f_eco),
]
