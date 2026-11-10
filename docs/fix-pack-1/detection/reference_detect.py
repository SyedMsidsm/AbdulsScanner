#!/usr/bin/env python3
"""Reference page-edge detector for Abul's Scanner (OpenCV, Python).

Port this 1:1 to Kotlin with OpenCV for Android (org.opencv:opencv, Imgproc/Core calls have the
same names). The same function is used for the live preview (on small analysis frames) and,
authoritatively, on every captured photo.

detect_page(bgr) -> 4x2 float array of corners in ORIGINAL image pixels, ordered
top-left, top-right, bottom-right, bottom-left, or None if no page was found.
"""
import cv2
import numpy as np

WORK_LONG_SIDE = 1000        # detection runs on a copy this size
MIN_AREA_FRACTION = 0.15     # page must cover >= 15% of the image
MIN_ANGLE, MAX_ANGLE = 45.0, 135.0
MIN_PAPERNESS = 0.50       # >= 50% of the quad must look like paper


def order_corners(pts):
    pts = np.asarray(pts, dtype=np.float32).reshape(4, 2)
    s = pts.sum(axis=1); d = np.diff(pts, axis=1).ravel()   # y - x
    tl = pts[np.argmin(s)]; br = pts[np.argmax(s)]
    tr = pts[np.argmin(d)]; bl = pts[np.argmax(d)]
    return np.array([tl, tr, br, bl], dtype=np.float32)


def corner_angles(q):
    angs = []
    for i in range(4):
        p0, p1, p2 = q[i - 1], q[i], q[(i + 1) % 4]
        v1, v2 = p0 - p1, p2 - p1
        c = np.dot(v1, v2) / (np.linalg.norm(v1) * np.linalg.norm(v2) + 1e-6)
        angs.append(np.degrees(np.arccos(np.clip(c, -1, 1))))
    return angs


def line_intersection(p1, p2, p3, p4):
    x1, y1 = p1; x2, y2 = p2; x3, y3 = p3; x4, y4 = p4
    den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
    if abs(den) < 1e-6:
        return None
    a = x1 * y2 - y1 * x2; b = x3 * y4 - y3 * x4
    return np.array([(a * (x3 - x4) - (x1 - x2) * b) / den, (a * (y3 - y4) - (y1 - y2) * b) / den], np.float32)


def to_quad(contour, w, h):
    """Reduce a contour to 4 corners.

    1. Convex hull, simplified with approxPolyDP (epsilon 1% of perimeter).
    2. If that already has 4 points, use them.
    3. Otherwise take the 4 LONGEST hull edges, sort them around the centre, and intersect
       neighbouring edges. This rebuilds corners hidden by a shadow, finger or pen.
    """
    hull = cv2.convexHull(contour)
    peri = cv2.arcLength(hull, True)
    poly = cv2.approxPolyDP(hull, 0.01 * peri, True).reshape(-1, 2).astype(np.float32)
    if len(poly) == 4:
        return poly
    if len(poly) < 4:
        return cv2.boxPoints(cv2.minAreaRect(hull)).astype(np.float32)
    n = len(poly)
    edges = [(poly[i], poly[(i + 1) % n]) for i in range(n)]
    edges = sorted(edges, key=lambda e: -np.linalg.norm(e[1] - e[0]))[:4]
    c = poly.mean(axis=0)
    edges = sorted(edges, key=lambda e: np.arctan2(*(((e[0] + e[1]) / 2 - c)[::-1])))
    pts = []
    for i in range(4):
        p = line_intersection(*edges[i], *edges[(i + 1) % 4])
        if p is None or not (-0.1 * w <= p[0] <= 1.1 * w and -0.1 * h <= p[1] <= 1.1 * h):
            return cv2.boxPoints(cv2.minAreaRect(hull)).astype(np.float32)
        pts.append(p)
    return np.array(pts, np.float32)


def paperness(q, paper_mask):
    """Share of pixels inside the quad that look like paper (bright, low saturation)."""
    m = np.zeros(paper_mask.shape, np.uint8)
    cv2.fillConvexPoly(m, q.astype(np.int32), 255)
    inside = m > 0
    return float((paper_mask[inside] > 0).mean()) if inside.any() else 0.0


def score(q, contour, w, h, paper_mask):
    q = order_corners(q)
    q[:, 0] = np.clip(q[:, 0], 0, w - 1); q[:, 1] = np.clip(q[:, 1], 0, h - 1)
    area = cv2.contourArea(q)
    frac = area / (w * h)
    if frac < MIN_AREA_FRACTION or not cv2.isContourConvex(q.reshape(-1, 1, 2).astype(np.int32)):
        return -1, q
    angs = corner_angles(q)
    if min(angs) < MIN_ANGLE or max(angs) > MAX_ANGLE:
        return -1, q
    # the whole frame is not a page: reject quads hugging 3+ image borders
    m = 0.01 * max(w, h)
    border_hits = sum([q[:, 0].min() < m, q[:, 1].min() < m, q[:, 0].max() > w - m, q[:, 1].max() > h - m])
    if border_hits >= 3:
        return -1, q
    pn = paperness(q, paper_mask)
    if pn < MIN_PAPERNESS:
        return -1, q
    fill = min(cv2.contourArea(contour) / max(area, 1.0), 1.0)   # how well the contour fills the quad
    return frac * fill ** 2 * pn ** 2, q


def paper_mask_of(small):
    """Bright AND low-saturation pixels, relative to this photo (Otsu on brightness)."""
    hsv = cv2.cvtColor(cv2.GaussianBlur(small, (5, 5), 0), cv2.COLOR_BGR2HSV)
    _, vmask = cv2.threshold(hsv[..., 2], 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    vmask = cv2.bitwise_or(vmask, (hsv[..., 2] > 200).astype(np.uint8) * 255)
    smask = (hsv[..., 1] < 70).astype(np.uint8) * 255
    return cv2.bitwise_and(vmask, smask)


def candidates(small):
    h, w = small.shape[:2]
    gray = cv2.cvtColor(small, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    k5 = cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5))
    k9 = cv2.getStructuringElement(cv2.MORPH_RECT, (9, 9))
    masks = []
    # A: Canny edges, thresholds from the median brightness
    med = float(np.median(blur))
    masks.append(("edges", cv2.dilate(cv2.Canny(blur, int(max(0, 0.66 * med)), int(min(255, 1.33 * med))), k5, iterations=2)))
    # A2: low-contrast edges (light page on light table): CLAHE + low fixed thresholds
    eq = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8, 8)).apply(blur)
    masks.append(("edges_lo", cv2.dilate(cv2.Canny(cv2.GaussianBlur(eq, (7, 7), 0), 20, 60), k5, iterations=2)))
    # B: Otsu threshold on brightness (paper is usually brighter than the background)
    _, otsu = cv2.threshold(blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    masks.append(("bright", cv2.morphologyEx(otsu, cv2.MORPH_CLOSE, k9, iterations=2)))
    # C: "paper-like" = bright AND low saturation
    paper = paper_mask_of(small)
    pm = cv2.morphologyEx(paper, cv2.MORPH_CLOSE, k9, iterations=3)
    masks.append(("paper", cv2.morphologyEx(pm, cv2.MORPH_OPEN, k5, iterations=1)))
    out = []
    for name, m in masks:
        mode = cv2.RETR_LIST if name.startswith("edges") else cv2.RETR_EXTERNAL
        contours, _ = cv2.findContours(m, mode, cv2.CHAIN_APPROX_SIMPLE)
        contours = sorted(contours, key=cv2.contourArea, reverse=True)[:8]
        for c in contours:
            if cv2.contourArea(c) < MIN_AREA_FRACTION * w * h * 0.5:
                continue
            out.append((name, c, to_quad(c, w, h)))
    return out, paper


def detect_page(bgr):
    H, W = bgr.shape[:2]
    k = WORK_LONG_SIDE / max(H, W)
    small = cv2.resize(bgr, (round(W * k), round(H * k)), interpolation=cv2.INTER_AREA) if k < 1 else bgr.copy()
    if k >= 1:
        k = 1.0
    h, w = small.shape[:2]
    best, best_q = 0.0, None
    cands, paper = candidates(small)
    for name, c, q in cands:
        s, oq = score(q, c, w, h, paper)
        if s > best:
            best, best_q = s, oq
    if best_q is None:
        return None
    return order_corners(best_q / k)


if __name__ == "__main__":
    import sys, json
    img = cv2.imread(sys.argv[1])
    q = detect_page(img)
    print(json.dumps(None if q is None else q.round(1).tolist()))
