#!/usr/bin/env python3
"""Make a realistic test photo (paper with uneven light and a hand shadow) and run every filter on it."""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from reference_filters import FILTERS

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "samples"); os.makedirs(OUT, exist_ok=True)
F = "/usr/share/fonts/truetype/dejavu/"
bold = ImageFont.truetype(F + "DejaVuSans-Bold.ttf", 34)
reg = ImageFont.truetype(F + "DejaVuSans.ttf", 21)
small = ImageFont.truetype(F + "DejaVuSans.ttf", 17)
serif = ImageFont.truetype(F + "DejaVuSerif-Italic.ttf", 22)

W, H = 900, 1200
page = Image.new("RGB", (W, H), (246, 244, 238))
d = ImageDraw.Draw(page)
# coloured logo
d.rounded_rectangle((60, 60, 150, 150), 16, fill=(19, 160, 111))
d.polygon([(80, 130), (105, 80), (130, 130)], fill=(255, 255, 255))
d.text((175, 68), "GULF STATIONERY W.L.L.", font=bold, fill=(25, 35, 70))
d.text((177, 112), "Manama, Kingdom of Bahrain  ·  Tel 1700 0000", font=small, fill=(90, 90, 90))
d.line((60, 180, 840, 180), fill=(200, 40, 40), width=4)
d.text((60, 205), "TAX INVOICE  No. 2026-0418", font=ImageFont.truetype(F + "DejaVuSans-Bold.ttf", 26), fill=(20, 20, 20))
d.text((60, 248), "Date: 10 November 2026", font=reg, fill=(40, 40, 40))
# table
y = 300
d.rectangle((60, y, 840, y + 44), fill=(225, 238, 250))
for x, t in [(75, "Item"), (470, "Qty"), (580, "Price"), (720, "Total")]:
    d.text((x, y + 10), t, font=ImageFont.truetype(F + "DejaVuSans-Bold.ttf", 20), fill=(20, 40, 80))
rows = [("A4 paper, 80 gsm (box)", "3", "2.600", "7.800"), ("Ballpoint pens, blue", "20", "0.150", "3.000"),
        ("Highlighters (pack of 4)", "2", "1.250", "2.500"), ("Stapler, heavy duty", "1", "4.900", "4.900"),
        ("Exercise books, 100 pages", "12", "0.450", "5.400")]
for i, r in enumerate(rows):
    yy = y + 60 + i * 46
    for x, t in zip([75, 470, 580, 720], r):
        d.text((x, yy), t, font=reg, fill=(30, 30, 30))
    d.line((60, yy + 36, 840, yy + 36), fill=(205, 205, 205), width=2)
yy = y + 60 + len(rows) * 46 + 20
d.rectangle((560, yy - 6, 840, yy + 34), fill=(255, 236, 120))  # highlighter
d.text((580, yy), "Total  BD 23.600", font=ImageFont.truetype(F + "DejaVuSans-Bold.ttf", 22), fill=(10, 10, 10))
para = ["Payment is due within 30 days of the invoice date.", "Please quote the invoice number when paying",
        "by bank transfer. Goods once sold are exchanged", "only with this original invoice."]
for i, t in enumerate(para):
    d.text((60, yy + 80 + i * 34), t, font=reg, fill=(50, 50, 50))
# blue pen signature
pts = [(560 + i * 6, 1050 + 22 * np.sin(i / 3.2) - (i % 9) * 1.5) for i in range(45)]
d.line(pts, fill=(25, 55, 160), width=4, joint="curve")
d.text((560, 1095), "Received by", font=serif, fill=(70, 70, 70))
d.text((60, 1095), "Thank you for your business!", font=serif, fill=(19, 120, 85))

a = np.asarray(page).astype(np.float32)
# lighting: warm cast, brighter top-left, darker bottom-right
yy_, xx_ = np.mgrid[0:H, 0:W]
light = 0.98 - 0.30 * ((xx_ / W) * 0.45 + (yy_ / H) * 0.55)
cast = np.array([1.0, 0.95, 0.84])
a = a * light[..., None] * cast
# soft hand/phone shadow
sh = Image.new("L", (W, H), 0)
ImageDraw.Draw(sh).ellipse((-250, 620, 520, 1450), fill=255)
ImageDraw.Draw(sh).rectangle((-50, 900, 380, 1300), fill=255)
sh = np.asarray(sh.filter(ImageFilter.GaussianBlur(70))).astype(np.float32) / 255.0
a = a * (1 - 0.48 * sh[..., None])
rng = np.random.default_rng(7)
a = a + rng.normal(0, 3.0, a.shape)
photo = np.clip(a, 0, 255).astype(np.uint8)
Image.fromarray(photo).save(os.path.join(OUT, "test-photo.jpg"), quality=92)
photo = np.asarray(Image.open(os.path.join(OUT, "test-photo.jpg")).convert("RGB"))  # filters run on the saved JPEG, like the app

results = []
for key, label, fn in FILTERS:
    img = fn(photo)
    Image.fromarray(img).save(os.path.join(OUT, f"filter-{key.lower()}.png"), optimize=True)
    results.append((label, img))

# comparison sheet
tw, th = 300, 400
lab = ImageFont.truetype(F + "DejaVuSans-Bold.ttf", 22)
sheet = Image.new("RGB", (4 * (tw + 24) + 24, 2 * (th + 70) + 90), (24, 24, 24))
sd = ImageDraw.Draw(sheet)
sd.text((24, 24), "Abdul's Scanner filters, applied to the same photo (shadow + uneven light)", font=lab, fill=(95, 211, 165))
for i, (label, img) in enumerate(results):
    x, y0 = 24 + (i % 4) * (tw + 24), 80 + (i // 4) * (th + 70)
    sheet.paste(Image.fromarray(img).resize((tw, th), Image.LANCZOS), (x, y0))
    sd.text((x, y0 + th + 12), label, font=lab, fill=(235, 235, 235))
x, y0 = 24 + 3 * (tw + 24), 80 + (th + 70)
sd.rectangle((x, y0, x + tw, y0 + th), outline=(70, 70, 70), width=2)
note = ["Original = the photo", "as the camera took it.", "", "Look at the bottom-left:", "the hand shadow is gone", "in No shadow, Magic Color,", "B&W and Eco."]
for i, t in enumerate(note):
    sd.text((x + 18, y0 + 30 + i * 32), t, font=ImageFont.truetype(F + "DejaVuSans.ttf", 19), fill=(200, 200, 200))
sheet.save(os.path.join(OUT, "filters-comparison.png"))
print("ok")
