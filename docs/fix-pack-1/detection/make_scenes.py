#!/usr/bin/env python3
"""Make test photos with known page corners (ground truth) for the page detector."""
import os, json
import numpy as np
import cv2
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "detect-tests"); os.makedirs(OUT, exist_ok=True)
W, H = 1200, 1600
rng = np.random.default_rng(11)
PAPER = cv2.imread("/home/claude/camera/samples/test-photo.jpg")            # receipt (900x1200)

def notebook():
    im = Image.new("RGB", (900, 1200), (250, 250, 246)); d = ImageDraw.Draw(im)
    for y in range(140, 1180, 42): d.line((40, y, 860, y), fill=(170, 200, 230), width=2)
    d.line((110, 0, 110, 1200), fill=(230, 120, 120), width=2)
    for i, y in enumerate(range(175, 900, 42)):
        xs = np.cumsum(rng.integers(8, 22, 40)) + 130
        pts = [(int(x), int(y - 8 + 6 * np.sin(x / 9.0 + i))) for x in xs if x < 820]
        d.line(pts, fill=(30, 40, 110), width=3)
    return cv2.cvtColor(np.asarray(im), cv2.COLOR_RGB2BGR)

def texture(base, noise=10, streaks=0, scale=1.0):
    a = np.ones((H, W, 3), np.float32) * np.array(base, np.float32)[::-1]
    a += rng.normal(0, noise, (H, W, 1))
    if streaks:
        s = cv2.GaussianBlur(rng.normal(0, streaks, (H, 1)).astype(np.float32), (1, 31), 0)
        a += np.repeat(s, W, axis=1)[..., None]
    yy, xx = np.mgrid[0:H, 0:W]
    a *= (1.08 - 0.25 * (yy / H) * scale)[..., None]
    return np.clip(a, 0, 255).astype(np.uint8)

def carpet():
    a = texture((150, 140, 130), noise=8)
    for x in range(-H, W, 60):
        cv2.line(a, (x, 0), (x + H, H), (95, 90, 85), 3)
        cv2.line(a, (x + H, 0), (x, H), (95, 90, 85), 3)
    return cv2.GaussianBlur(a, (3, 3), 0)

def place(bg, paper, quad, light=1.0, shadow=None):
    ph, pw = paper.shape[:2]
    M = cv2.getPerspectiveTransform(np.float32([[0, 0], [pw, 0], [pw, ph], [0, ph]]), np.float32(quad))
    warped = cv2.warpPerspective(paper, M, (W, H), flags=cv2.INTER_CUBIC)
    mask = cv2.warpPerspective(np.full((ph, pw), 255, np.uint8), M, (W, H))
    soft = cv2.GaussianBlur(mask, (5, 5), 0).astype(np.float32)[..., None] / 255
    # drop shadow under the paper
    sh = cv2.GaussianBlur(cv2.warpPerspective(np.full((ph, pw), 255, np.uint8),
                          M @ np.float32([[1, 0, 0], [0, 1, 0], [0, 0, 1]]), (W, H)), (41, 41), 0)
    sh = np.roll(np.roll(sh, 10, 0), 8, 1).astype(np.float32)[..., None] / 255
    out = bg.astype(np.float32) * (1 - 0.35 * sh)
    out = out * (1 - soft) + warped.astype(np.float32) * light * soft
    if shadow is not None:
        out *= (1 - 0.45 * shadow[..., None])
    out += rng.normal(0, 2.5, out.shape)
    return np.clip(out, 0, 255).astype(np.uint8)

def hand_shadow(box):
    m = Image.new("L", (W, H), 0); ImageDraw.Draw(m).ellipse(box, fill=255)
    return np.asarray(m.filter(ImageFilter.GaussianBlur(60))).astype(np.float32) / 255

scenes = {}
def add(name, img, quad, note):
    cv2.imwrite(os.path.join(OUT, f"{name}.jpg"), img, [cv2.IMWRITE_JPEG_QUALITY, 90])
    scenes[name] = {"expected": quad, "note": note}

q = [[240, 260], [960, 330], [930, 1330], [190, 1270]]
add("01-dark-desk-angled", place(texture((70, 58, 46), 9, 10), PAPER, q), q, "Receipt on a dark desk, tilted")
q = [[170, 220], [1010, 200], [1050, 1360], [150, 1400]]
add("02-light-table-low-contrast", place(texture((206, 198, 182), 5), PAPER, q, light=0.97), q, "Receipt on a light beige table (low contrast)")
q = [[260, 200], [990, 290], [940, 1350], [200, 1300]]
add("03-hand-shadow-over-corner", place(texture((120, 118, 115), 7), notebook(), q, shadow=hand_shadow((-300, 900, 700, 1900))), q,
    "Notebook page, hand shadow over the bottom-left corner")
q = [[300, 330], [930, 300], [960, 1250], [280, 1300]]
add("04-busy-carpet", place(carpet(), PAPER, q), q, "Receipt on a patterned carpet")
q = [[220, 240], [980, 260], [990, 1310], [200, 1330]]
img = place(texture((60, 62, 70), 8), notebook(), q)
cv2.line(img, (120, 1290), (900, 1380), (40, 40, 200), 22); cv2.line(img, (120, 1290), (900, 1380), (90, 90, 230), 8)
add("05-pen-across-edge", img, q, "Notebook page with a red pen lying across the bottom edge")
q = [[70, 90], [1130, 110], [1120, 1500], [80, 1520]]
add("06-close-up-fills-frame", place(texture((80, 70, 60), 8), PAPER, q), q, "Close-up: page fills ~80% of the photo")
room = cv2.imread("/mnt/user-data/uploads/ed894634-image.jpg")[207:1087, 30:690]
room = cv2.resize(room, (W, int(W * room.shape[0] / room.shape[1])))
add("07-room-no-page", room, None, "The owner's room photo (laptop, no paper): must return NO page")

json.dump(scenes, open(os.path.join(OUT, "expected.json"), "w"), indent=1)
print(len(scenes), "scenes")
