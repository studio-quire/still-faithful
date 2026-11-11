"""Draws the Pure Faithful icon candidates and the comparison page that shows them.

    python tools/icon_candidates.py [outdir]     (default prism/release/icons)

Every candidate is pixel art drawn here at a native 32 px (one at 16 px), written at native size; the page scales them
crisply. Some have an Old Faithful companion (`-old.png`), for the pair idea. No game textures or logos. Deterministic.
The release's own icon is tools/make_icon.py's; this script never touches it.
"""
import colorsys
import html
import importlib.util
import math
import shutil
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent

OUT = (20, 30, 52)
NAVY = (24, 38, 72)
DEEP = (34, 72, 132)
BLUE = (56, 122, 196)
CLEAR = (112, 184, 232)
PALE = (188, 226, 248)
WHITE = (248, 252, 255)
PEARL = (232, 236, 243)
SILVER = (196, 204, 217)
STEEL = (140, 152, 172)
SLATE = (94, 106, 128)
TEAL = (44, 148, 164)


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def ramp(colors, t):
    t = max(0.0, min(1.0, t)) * (len(colors) - 1)
    i = min(int(t), len(colors) - 2)
    return lerp(colors[i], colors[i + 1], t - i)


class Canvas:
    def __init__(self, n=32):
        self.n = n
        self.p = [[None] * n for _ in range(n)]

    def set(self, x, y, c):
        if 0 <= x < self.n and 0 <= y < self.n:
            self.p[y][x] = c

    def get(self, x, y):
        if 0 <= x < self.n and 0 <= y < self.n:
            return self.p[y][x]
        return None

    def paint(self, fn):
        """fn(x, y) -> colour or None, called with pixel centres; None leaves the pixel as it is."""
        for y in range(self.n):
            for x in range(self.n):
                c = fn(x + 0.5, y + 0.5)
                if c is not None:
                    self.p[y][x] = c

    def clip(self, inside):
        for y in range(self.n):
            for x in range(self.n):
                if not inside(x + 0.5, y + 0.5):
                    self.p[y][x] = None

    def outline(self, color, diagonal=False):
        nb = [(1, 0), (-1, 0), (0, 1), (0, -1)]
        if diagonal:
            nb += [(1, 1), (1, -1), (-1, 1), (-1, -1)]
        add = [(x, y) for y in range(self.n) for x in range(self.n)
               if self.p[y][x] is None and any(self.get(x + dx, y + dy) is not None for dx, dy in nb)]
        for x, y in add:
            self.p[y][x] = color

    def bitmap(self, rows, x0, y0, color_fn):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch != ".":
                    c = color_fn(x0 + i, y0 + j, ch)
                    if c is not None:
                        self.set(x0 + i, y0 + j, c)

    def image(self):
        img = Image.new("RGBA", (self.n, self.n), (0, 0, 0, 0))
        for y in range(self.n):
            for x in range(self.n):
                c = self.p[y][x]
                if c is not None:
                    img.putpixel((x, y), c if len(c) == 4 else c + (255,))
        return img


def rounded(n, r, inset=0):
    lo, hi = inset + r, n - inset - r

    def inside(x, y):
        dx = max(lo - x, 0, x - hi)
        dy = max(lo - y, 0, y - hi)
        return dx * dx + dy * dy <= r * r and inset <= x <= n - inset and inset <= y <= n - inset
    return inside


def disc(cx, cy, r):
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def vgrad(top, bottom, y0=0, y1=32):
    return lambda x, y: lerp(top, bottom, (y - y0) / (y1 - y0))


# ---------------------------------------------------------------- shared motifs

def plume(c, cx, base, top, w_base, w_top, lit=WHITE, shade=PALE, edge=CLEAR):
    """A water column narrow at the vent, widening upward, lit from the left."""
    for y in range(top, base + 1):
        t = (base - y) / max(1, base - top)
        half = w_base + (w_top - w_base) * t
        for x in range(c.n):
            dx = x + 0.5 - cx
            if abs(dx) <= half:
                if dx > half - 0.9:
                    c.set(x, y, edge)
                elif dx > half - 2.0:
                    c.set(x, y, shade)
                else:
                    c.set(x, y, lit)


def noise(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) % 1009 / 1009


def geyser(c, cx, base, top, w_base=1.2, w_top=3.0, lit=WHITE, shade=PALE, edge=CLEAR, wind=1, spray=None,
           seed=0, **_):
    """An eruption: one shape widening steadily from the vent into a billowing top, lit from the left.

    No narrow stalk under a wider cap: that reads as a mushroom cloud or a tap. The top leans a little downwind."""
    h = base - top
    w_top = w_top * 1.5
    lean = wind * w_top * 0.25
    blobs = [(cx + lean - w_top * 0.45, top + w_top * 0.75, w_top * 0.62),
             (cx + lean + w_top * 0.35, top + w_top * 0.6, w_top * 0.7),
             (cx + lean - w_top * 0.05, top + w_top * 0.42, w_top * 0.58)]

    def half_at(y):
        t = max(0.0, (base - y) / h)
        return w_base + (w_top - w_base) * t ** 1.7

    def centre_at(y):
        t = max(0.0, (base - y) / h)
        return cx + lean * t ** 2

    for y in range(top - 1, base + 1):
        for x in range(c.n):
            px, py = x + 0.5, y + 0.5
            ccx = centre_at(py)
            half = half_at(py) + (noise(x // 2, y, seed) - 0.5) * 0.9 * ((base - py) / h > 0.45)
            in_col = py >= top + w_top * 0.6 and abs(px - ccx) <= half
            in_blob = None
            for bx, by, r in blobs:
                if (px - bx) ** 2 + (py - by) ** 2 <= r * r:
                    in_blob = (bx, by, r)
            if not (in_col or in_blob):
                continue
            if in_blob and not in_col:
                bx, by, r = in_blob
                u = ((px - bx) * 0.8 + (py - by) * 0.6) / r
            else:
                u = (px - ccx) / max(half, 0.6)
            c.set(x, y, lit if u < 0.25 else (shade if u < 0.75 else edge))
    for x, y in spray or []:
        c.set(round(cx + x * h / 17), round(base - y * h / 17), shade)


def puffs(c, blobs, lit=WHITE, shade=PALE):
    for cx, cy, r in blobs:
        for y in range(c.n):
            for x in range(c.n):
                dy = y + 0.5 - cy
                if (x + 0.5 - cx) ** 2 + dy * dy <= r * r:
                    c.set(x, y, shade if dy > r * 0.35 else lit)


def mound(c, cx, top, half_top, slope, lit, shade, edge, pool=None):
    for y in range(top, c.n):
        half = half_top + (y - top) * slope
        for x in range(c.n):
            dx = x + 0.5 - cx
            if abs(dx) > half:
                continue
            if pool and y == top and abs(dx) < half_top - 0.5:
                c.set(x, y, pool)
            elif abs(dx) > half - 1.2:
                c.set(x, y, edge)
            elif dx > 0 and dx > half * 0.45:
                c.set(x, y, shade)
            else:
                c.set(x, y, lit)


SPRAY = [(-6, 12), (6, 11), (-8, 7), (8, 6), (-5, 4), (5, 3)]


def geyser_scene(c, cx=15, ground=24):
    geyser(c, cx, ground, 4, 1.3, 3.6, spray=SPRAY)
    mound(c, cx, ground, 3, 2.4, (214, 222, 230), (184, 194, 208), (140, 152, 172), pool=TEAL)
    # Terraces on the mound.
    for y, x0, x1 in [(27, 6, 11), (29, 18, 25), (30, 3, 9)]:
        for x in range(x0, x1):
            if c.get(x, y) is not None:
                c.set(x, y, (164, 176, 194))


# ---------------------------------------------------------------- the candidates

def c01_geyser():
    c = Canvas()
    c.paint(vgrad((44, 92, 170), (156, 204, 240)))
    geyser_scene(c)
    c.clip(rounded(32, 5))
    return c


def pool_badge(erupting):
    c = Canvas()
    horizon = 15
    c.paint(lambda x, y: lerp((62, 120, 200), (168, 212, 244), y / horizon) if y < horizon else
            lerp((206, 214, 226), (236, 240, 246), (y - horizon) / 16))
    for x in range(32):  # far hills along the horizon
        hy = horizon - 1 - round(1 + math.sin(x * 0.45) * 0.9 + math.sin(x * 0.17 + 1) * 0.8)
        for y in range(hy, horizon):
            c.set(x, y, (120, 146, 182))
    cx, cy = 16, 22.5
    # The pool sunk in the sinter: its far wall shows above the water.
    c.paint(lambda x, y: (150, 162, 182) if ((x - cx) / 12) ** 2 + ((y - cy) / 4.8) ** 2 <= 1 else None)

    def water(x, y):
        d = math.hypot((x - cx) / 11.3, (y - cy - 0.8) / 4.0)
        if d > 1:
            return None
        return ramp([NAVY, DEEP, BLUE, TEAL, (120, 206, 206)], d ** 1.2)
    c.paint(water)
    if erupting:
        geyser(c, cx, round(cy), 2, 1.3, 3.0, spray=[(-7, 8), (7, 7), (-9, 3), (9, 2)], seed=2)
    else:
        for x, y in [(11, 21), (12, 21), (19, 24)]:
            c.set(x, y, PALE)
        # Two wisps of steam lifting off the still water, fading as they rise.
        for base_x, phase, y0, y1 in ((11.5, 0.0, 20, 5), (19.5, 1.4, 21, 8)):
            for y in range(y0, y1, -1):
                t = (y0 - y) / (y0 - y1)
                x = base_x + math.sin(t * 3.0 + phase) * 1.4 + t * 2.0
                for px in {math.floor(x), math.floor(x + (0.5 if t < 0.4 else 0))}:
                    old = c.get(px, y)
                    if old is not None:
                        c.set(px, y, lerp(old, WHITE, 0.7 - t * 0.55))
    c.clip(disc(16, 16, 15.5))
    return c


def cube(c, cx, top, hw, h):
    """An isometric block of clear water: top rhombus hw wide each side (2:1), sides h tall."""
    ty = top + hw / 2  # middle of the top face

    def top_face(x, y):
        return abs(x - cx) / 2 + abs(y - ty) <= hw / 2

    def left(x, y):
        if not (cx - hw <= x < cx):
            return False
        yt = ty + (x - (cx - hw)) / 2
        return yt <= y <= yt + h

    def right(x, y):
        if not (cx <= x <= cx + hw):
            return False
        yt = ty + hw / 2 - (x - cx) / 2
        return yt <= y <= yt + h

    def side(x, y):
        if left(x, y):
            # A diagonal glint across the left face.
            k = (x - (cx - hw)) - (y - ty) * 0.9
            if -3.5 < k < -1.5:
                return PALE
            return CLEAR if y < ty + h * 0.75 else lerp(CLEAR, BLUE, 0.5)
        if right(x, y):
            return BLUE if y < ty + h * 0.75 else lerp(BLUE, DEEP, 0.6)
        return None
    c.paint(side)

    def face(x, y):
        if not top_face(x, y):
            return None
        d = abs(x - cx) / 2 + abs(y - ty)
        if d > hw / 2 - 0.9 and y > ty:
            return WHITE  # the lit front edges
        ripple = math.hypot((x - cx) / 2, y - ty)
        if abs(ripple - hw * 0.22) < 0.5 or abs(ripple - hw * 0.42) < 0.5:
            return WHITE
        return PALE
    c.paint(face)


def c03_cube(jet):
    c = Canvas()
    if jet:
        cube(c, 16, 12, 11, 10)
        geyser(c, 16, 17, 2, 1.1, 3.0, seed=3)
    else:
        cube(c, 16, 2, 14, 13)
    c.outline(OUT)
    return c


def c04_drop():
    c = Canvas(16)
    cx, cy, r, tip = 8, 10.2, 5.2, 1.2
    d = cy - tip
    tan_t = r / math.sqrt(d * d - r * r)
    y_tan = cy - r * r / d

    def half(y):
        hc = math.sqrt(max(0.0, r * r - (y - cy) ** 2))
        hcone = (y - tip) * tan_t if tip <= y <= y_tan else 0
        return max(hc, hcone)

    def color(x, y):
        w = half(y)
        if w <= 0 or abs(x - cx) > w:
            return None
        dx = (x - cx) / w
        lum = 0.62 - dx * 0.38 + (cy - y) * 0.03
        return ramp([DEEP, BLUE, CLEAR, PALE], lum)
    c.paint(color)
    for x, y in [(6, 7), (5, 9), (5, 10), (6, 13)]:
        c.set(x, y, WHITE)
    c.outline(OUT)
    return c


def c05_ripple():
    c = Canvas()
    cx, cy = 16, 21

    def water(x, y):
        base = ramp([(150, 206, 236), CLEAR, BLUE, DEEP], y / 32)
        for i, rr in enumerate((3.5, 7.5, 12)):
            d = math.hypot(x - cx, (y - cy) / 0.42)
            fade = i * 0.25
            if abs(d - rr) < 0.8:
                return lerp(WHITE if y < cy else PALE, base, fade)  # the crest, its far side catching the sky
            if 0.8 <= d - rr < 2.0 and y > cy:
                return lerp(base, NAVY, 0.3 - fade * 0.5)  # the trough behind it
        return base
    c.paint(water)
    # The drop, just about to land again from the splash.
    for x, y, col in [(16, 6, PALE), (15, 7, WHITE), (16, 7, PALE), (15, 8, WHITE), (16, 8, CLEAR),
                      (16, 13, WHITE), (16, 14, WHITE), (14, 16, WHITE), (18, 16, PALE), (16, 17, WHITE),
                      (15, 18, WHITE), (16, 18, WHITE), (17, 18, PALE)]:
        c.set(x, y, col)
    c.clip(rounded(32, 5))
    return c


GLYPHS = {
    "P": ["########..",
          "#########.",
          "###...####",
          "###....###",
          "###....###",
          "###...####",
          "#########.",
          "########..",
          "###.......",
          "###.......",
          "###.......",
          "###.......",
          "###.......",
          "###......."],
    "F": ["##########",
          "##########",
          "###.......",
          "###.......",
          "###.......",
          "#######...",
          "#######...",
          "###.......",
          "###.......",
          "###.......",
          "###.......",
          "###.......",
          "###.......",
          "###......."],
    "O": ["..######..",
          ".########.",
          "####..####",
          "###....###",
          "###....###",
          "###....###",
          "###....###",
          "###....###",
          "###....###",
          "###....###",
          "###....###",
          "####..####",
          ".########.",
          "..######.."],
}


def c06_letters(first):
    c = Canvas()
    c.paint(vgrad((40, 84, 150), NAVY))
    for i, ch in enumerate((first, "F")):
        x0 = 5 + i * 12
        c.bitmap(GLYPHS[ch], x0 + 1, 7, lambda x, y, _: DEEP)  # the shadow
        c.bitmap(GLYPHS[ch], x0, 6, lambda x, y, _: lerp(WHITE, SILVER, (y - 6) / 14))
    # A wave beneath, the water the names share.
    for x in range(5, 27):
        c.set(x, 25 + round(math.sin(x * 0.9) * 0.9), CLEAR)
    c.clip(rounded(32, 5))
    return c


BETA = ["...######....",
        "..########...",
        ".###....###..",
        ".###.....###.",
        ".###.....###.",
        ".###....###..",
        ".###..####...",
        ".###..#####..",
        ".###.....###.",
        ".###......###",
        ".###......###",
        ".###......###",
        ".###......###",
        ".###.....###.",
        ".##########..",
        ".###.#####...",
        ".###.........",
        ".###.........",
        ".###.........",
        ".###........."]


def luminance(c):
    def lin(v):
        v /= 255
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = map(lin, c)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def matched(hue, sat, like, light=1.0):
    """The colour of this hue and HLS saturation with the same luminance as `like`, so a recoloured seal keeps
    the original's light and dark; `light` scales that luminance, for a hue that needs to sit deeper."""
    target, lo, hi = luminance(like) * light, 0.0, 1.0
    for _ in range(40):
        mid = (lo + hi) / 2
        c = tuple(round(v * 255) for v in colorsys.hls_to_rgb(hue / 360, mid, sat))
        lo, hi = (mid, hi) if luminance(c) < target else (lo, mid)
    return c


BETA_PURE = ((86, 150, 222), (34, 72, 140), PALE)
BETA_TEAL = ((30, 112, 120), (16, 60, 72), (150, 210, 214))


def beta_green(hue, sat=0.5):
    top, bottom, ring = BETA_PURE
    return matched(hue, sat, top), matched(hue, sat * 1.05, bottom), matched(hue, sat * 1.1, ring)


# Old Faithful's seal, from a clear step greener than teal to a properly green one; each keeps Pure's luminance.
BETA_GREENS = [("a", 168, "Sea green"), ("b", 155, "Jade"), ("c", 142, "Spring green"), ("d", 128, "Leaf green")]


def c07_beta(colors=BETA_PURE, glyph_tint=None, shadow=OUT):
    top, bottom, ring = colors
    c = Canvas()
    c.paint(lambda x, y: lerp(top, bottom, y / 32) if disc(16, 16, 15.5)(x, y) else None)
    c.paint(lambda x, y: ring if 14.4 <= math.hypot(x - 16, y - 16) <= 15.5 else None)
    tint = glyph_tint or (ring if colors is not BETA_TEAL else PALE)
    c.bitmap(BETA, 9 + 1, 6 + 1, lambda x, y, _: shadow)
    c.bitmap(BETA, 9, 6, lambda x, y, _: lerp(WHITE, tint, (y - 6) / 20))
    return c


def c08_pick():
    c = Canvas()
    # The water the pick stands in.
    c.paint(lambda x, y: ramp([PALE, CLEAR, BLUE], math.hypot((x - 16) / 13, (y - 26) / 4.2) ** 1.5)
            if ((x - 16) / 13) ** 2 + ((y - 26) / 4.2) ** 2 <= 1 else None)
    c.paint(lambda x, y: WHITE if abs(math.hypot((x - 16) / 7, (y - 26) / 2.2) - 1) < 0.16 else None)
    # The handle, pale ash, into the water.
    for y in range(6, 27):
        c.set(15, y, (226, 220, 204) if y < 25 else lerp((226, 220, 204), CLEAR, 0.6))
        c.set(16, y, (178, 170, 152) if y < 25 else lerp((178, 170, 152), BLUE, 0.6))
    # The head: a shallow arc, thick in the middle, drawn to points.
    for x in range(3, 29):
        dx = (x + 0.5 - 16) / 12.5
        if abs(dx) > 1:
            continue
        yc = 7.2 + dx * dx * 4.2
        t = 0.9 + 2.6 * (1 - abs(dx)) ** 0.7
        for y in range(0, 16):
            dy = y + 0.5 - yc
            if abs(dy) <= t / 2:
                c.set(x, y, PEARL if dy < -t / 6 else (SILVER if dy < t / 6 else STEEL))
    for y in (6, 7, 8, 9):
        c.set(15, y, SLATE)
        c.set(16, y, SLATE)
    c.set(15, 7, STEEL)
    c.outline(OUT)
    return c


def c09_night():
    c = Canvas()
    c.paint(vgrad((14, 18, 44), (54, 70, 128)))
    for x, y in [(4, 4), (9, 2), (25, 3), (28, 9), (3, 12), (22, 6), (27, 15)]:
        c.set(x, y, (200, 210, 240))
    # A thin crescent moon.
    c.paint(lambda x, y: (232, 236, 250) if disc(7, 8, 3.2)(x, y) and not disc(8.4, 7.2, 2.8)(x, y) else None)
    geyser(c, 17, 25, 7, 1.1, 2.8, (226, 234, 250), (172, 190, 228), (120, 140, 196),
           steam=((176, 190, 230), (120, 138, 194)), spray=SPRAY, seed=5)
    mound(c, 17, 25, 3, 2.6, (30, 38, 74), (24, 30, 60), (20, 26, 52), pool=(90, 120, 190))
    c.clip(rounded(32, 5))
    return c


def c10_cliff():
    c = Canvas()
    c.paint(vgrad((70, 128, 206), (176, 216, 246), 0, 26))
    for x in range(32):  # far hills
        for y in range(18 - round(1.5 + math.sin(x * 0.4) * 1.2 + math.sin(x * 0.15 + 2) * 1.0), 26):
            c.set(x, y, (124, 156, 190))
    # The cliff, stepped in 4 px blocks of slightly different stone.
    heights = [4, 4, 8, 8, 8]
    for bx, top in enumerate(heights):
        for y in range(top, 32):
            for x in range(bx * 4, bx * 4 + 4):
                k = noise(bx, (y - top) // 4, 11)
                col = lerp((134, 144, 162), (108, 118, 138), k)
                if y == top:
                    col = (184, 194, 208)
                elif x == 19:
                    col = (78, 88, 108)  # its face in shadow
                c.set(x, y, col)
    for y in range(4, 8):  # the step's shaded riser
        c.set(7, y, (96, 106, 126))
    # The stream along the top and over the lip.
    for x in range(10, 20):
        c.set(x, 8, CLEAR if x % 3 else PALE)
    for y in range(8, 26):
        c.set(20, y, WHITE)
        c.set(21, y, PALE if y > 9 else WHITE)
        c.set(22, y, CLEAR if y > 11 else None)
    c.set(19, 8, WHITE)
    # The pool, with foam where the fall lands.
    for x in range(32):
        for y in range(25, 32):
            c.set(x, y, lerp(CLEAR, DEEP, (y - 25) / 7))
    for x, y in [(18, 25), (19, 24), (20, 24), (21, 24), (22, 24), (23, 25), (24, 25), (17, 25), (19, 26),
                 (22, 26), (23, 24), (18, 23), (24, 23), (27, 27), (28, 27), (6, 28), (7, 28), (12, 27)]:
        c.set(x, y, WHITE)
    c.clip(rounded(32, 5))
    return c


def shield(charge):
    c = Canvas()

    def half(y):
        if y < 3:
            return -1
        if y <= 17:
            return 12.5
        t = (y - 17) / 13.2
        return 12.5 * (1 - t ** 1.7) if t < 1 else -1

    def inside(x, y, inset=0.0):
        return abs(x - 16) <= half(y) - inset and y >= 3 + inset

    def body(x, y):
        if not inside(x, y):
            return None
        if not inside(x, y, 2.0):
            return PEARL if x < 16 else SILVER
        return lerp(DEEP, NAVY, (y - 3) / 28)
    c.paint(body)
    if charge == "fountain":
        def fountain(x, y):
            d = math.hypot(x - 16, y - 15)
            if d > 7.6:
                return None
            if d > 6.8:
                return WHITE
            band = int((y - 7 + math.sin(x * 0.95) * 1.1) // 3.1)
            return WHITE if band % 2 == 0 else CLEAR
        c.paint(fountain)
    else:
        geyser(c, 16, 22, 6, 1.1, 3.2, seed=7)
        for y in range(22, 26):
            for x in range(c.n):
                if abs(x + 0.5 - 16) <= 2 + (y - 22) * 1.6 and inside(x + 0.5, y + 0.5, 2.0):
                    c.set(x, y, SILVER if x >= 16 else PEARL)
    c.outline(OUT)
    return c


def c12_horizon():
    c = Canvas()
    c.paint(vgrad((92, 146, 226), (184, 216, 246), 0, 20))
    for x0, y0, w in [(3, 5, 9), (19, 3, 8), (25, 8, 6)]:
        for x in range(x0, x0 + w):
            c.set(x, y0, WHITE)
            c.set(x, y0 + 1, (224, 234, 246))
    # A small geyser on the far shore.
    geyser(c, 22, 17, 10, 0.6, 1.1, WHITE, WHITE, PALE, seed=4)
    far = [17, 17, 16, 16, 15, 15, 16, 17, 17, 18, 18, 17, 17, 16, 16, 17]
    near = [20, 20, 19, 19, 20, 21, 21, 22, 22, 22, 21, 21, 20, 20, 21, 21]
    for x in range(32):
        for y in range(far[x // 2], 32):
            c.set(x, y, (118, 158, 170) if y < far[x // 2] + 1 else (102, 140, 156))
        for y in range(near[x // 2], 32):
            c.set(x, y, (104, 176, 108) if y == near[x // 2] else (74, 140, 84))
    for x in range(32):
        for y in range(26, 32):
            c.set(x, y, lerp(CLEAR, DEEP, (y - 26) / 6))
    for x, y in [(4, 27), (5, 27), (13, 28), (21, 27), (22, 27), (27, 29)]:
        c.set(x, y, WHITE)
    c.clip(rounded(32, 5))
    return c


CANDIDATES = [
    # number, slug, name, idea, draw, companion (Old Faithful) or None
    ("01", "geyser", "Geyser, cleaner",
     "The current draft simplified: a bolder plume and a cool pale mound, fewer specks, so it holds at 32 px.",
     c01_geyser, None),
    ("02", "pool", "Still pool / eruption",
     "Pure is the hot spring at rest, Old Faithful the same pool erupting: one scene in two moments.",
     lambda: pool_badge(False), lambda: pool_badge(True)),
    ("03", "source", "Source block",
     "A block of clear water, the purest thing in a blocky world; Old Faithful's block throws up a jet.",
     lambda: c03_cube(False), lambda: c03_cube(True)),
    ("04", "drop", "Drop (16 px)",
     "One drop of clear water at beta's own 16 px item scale, chunky on purpose.",
     c04_drop, None),
    ("05", "ripple", "Ripple",
     "A drop landing in still water, seen from above: calm, and nothing else.",
     c05_ripple, None),
    ("06", "pf", "PF lettermark",
     "Plain letters over a wave; Old Faithful's is OF. Readable before it's pretty.",
     lambda: c06_letters("P"), lambda: c06_letters("O")),
    ("07", "beta", "Beta",
     "The β of beta in clear water on a round seal; the two editions differ by the seal's water.",
     c07_beta, lambda: c07_beta(BETA_TEAL)),
    ("08", "pick", "Pick in the spring",
     "A pick of our own drawing, silver head and ash handle, standing in clear water: the game, kept clean.",
     c08_pick, None),
    ("09", "night", "Blue hour geyser",
     "The geyser at night under a crescent moon: quieter, and the plume pops on any background.",
     c09_night, None),
    ("10", "cliff", "Cliff spring",
     "A spring falling from a stepped cliff into a pool: beta's terrain feel without its textures.",
     c10_cliff, None),
    ("11", "fountain", "Heraldic fountain",
     "A silver shield bearing a heraldic fountain (the old sign for a spring); Old Faithful's bears the geyser.",
     lambda: shield("fountain"), lambda: shield("geyser")),
    ("12", "horizon", "Beta horizon",
     "Flat clouds, stepped hills and a lake, with a small geyser on the far shore.",
     c12_horizon, None),
]


# The user's note on 07-old-c (2026-11-02): keep the green, but the mint ring and the β's gradient toward it read
# teal, so the icon feels blue; a silver rim and β instead. Each: (key, name, silver, shadow).
SILVERS = [("s1", "Neutral silver", (206, 208, 208), OUT),
           ("s2", "Green-tinged silver", (200, 212, 203), OUT),
           ("s3", "Neutral silver, green shadow", (206, 208, 208), (14, 44, 30)),
           ("s4", "Warm silver", (212, 210, 202), OUT)]


def silver_seal(silver, shadow):
    top, bottom, _ = beta_green(142)
    return c07_beta((top, bottom, silver), glyph_tint=silver, shadow=shadow)


def silvers_section():
    def cells(src):
        return "".join(f'<div class="cell {bg}"><img src="{src}" width="{n}" height="{n}" alt=""></div>'
                       for bg, n in (("dark", 32), ("light", 32), ("dark m", 64), ("light m", 64), ("modrinth", 96)))
    rows = [f'<div class="green ref"><span class="tag">07-old-c &middot; Spring green, as now</span>'
            f'<div class="row">{cells("07-old-c.png")}</div></div>']
    for key, name, _, _ in SILVERS:
        rows.append(f'<div class="green"><span class="tag">07-old-c-{key} &middot; {html.escape(name)}</span>'
                    f'<div class="row">{cells(f"07-old-c-{key}.png")}</div></div>')
    return ('<section class="card chosen" id="c07-silver"><h2><span class="num">07</span>Old Faithful: silver rim and β'
            '</h2><p>The spring green kept; the rim and the β in silver instead of mint, so the seal stops leaning '
            f'blue.</p>{"".join(rows)}</section>')


# Still Faithful's seal (2026-11-02): the family's β seal with Old Faithful's silver rim and β, in a third colour.
# Each: (key, name, top hue, bottom hue, why). Same luminance as Pure's, so the three sit together.
STILLS = [("a", "Dusk violet", 266, 266, "Chosen (2026-11-02). Violet, the hour smooth dusk is about; clearly apart from Pure's blue and Old's green."),
          ("b", "Twilight", 335, 250, "The seal's water as the sky after sunset: rose at the top, falling to indigo. Dusk, literally."),
          ("c", "Rose", 340, 340, "A muted rose: warm and soft, as it might have been."),
          ("d", "Amber", 36, 36, "The placeholder, for comparison: warm gold, but orange is our house colour."),
          ("e", "Midnight", 232, 232, "A deep night blue, darker than Pure's; quiet, but close to Pure at 32 px.")]
STILL_SILVER = (206, 208, 208)


def still_seal(top_hue, bottom_hue, sat=0.5, light=1.0):
    top, bottom, _ = BETA_PURE
    t, b = matched(top_hue, sat, top, light), matched(bottom_hue, sat * 1.05, bottom, light)
    if top_hue == 232:
        t, b = matched(232, 0.45, (52, 96, 170)), matched(232, 0.5, (20, 34, 78))
    shadow = tuple(round(v * 255) for v in colorsys.hls_to_rgb(bottom_hue / 360, 0.11, 0.5))
    return c07_beta((t, b, STILL_SILVER), glyph_tint=STILL_SILVER, shadow=shadow)


# More Faithful's seal (the user's pick, 2026-11-10, of Claude Fable 5.2's rounds): crimson, with the silver rim and beta
# of Old and Still Faithful. The first warm seal, where the ladder goes from beta itself to beta with more in it. It sits
# a little deeper than the first three: a red as light as Pure's blue is salmon.
def more_seal():
    return still_seal(355, 355, 0.6, 0.68)


def stills_section():
    def trio(src, size, bg):
        return (f'<div class="cell {bg}"><div class="duo">'
                f'<img src="07-beta.png" width="{size}" height="{size}" alt="">'
                f'<img src="07-old-c-s3.png" width="{size}" height="{size}" alt="">'
                f'<img src="{src}" width="{size}" height="{size}" alt=""></div></div>')
    rows = []
    for key, name, _, _, why in STILLS:
        src = f"07-still-{key}.png"
        mark = " pick" if key == "a" else (" ref" if key == "d" else "")
        rows.append(f'<div class="green{mark}"><span class="tag">07-still-{key} &middot; {html.escape(name)}: '
                    f'{html.escape(why)}</span><div class="row">{trio(src, 32, "dark")}{trio(src, 32, "light")}'
                    f'<div class="cell dark m"><img src="{src}" width="64" height="64" alt=""></div>'
                    f'<div class="cell modrinth"><img class="mr" src="{src}" width="96" height="96" alt=""></div>'
                    f'</div></div>')
    return ('<section class="card chosen" id="c07-still"><h2><span class="num">07</span>Still Faithful</h2>'
            '<p>The family seal in a third colour, with Old Faithful&#x27;s silver rim and β. Each row shows Pure, Old and '
            f'Still side by side at Prism&#x27;s 32 px, then Still alone at 64 and Modrinth&#x27;s 96.</p>{"".join(rows)}</section>')


GREEN_PICK = "b"
GREEN_WHY = ("Recommended: b, Jade. It reads as green at 32 px on either background, yet stays cool enough to sit "
             "beside Pure's blue as its sibling; c and d drift toward a generic green.")


def greens_section():
    def pair(old, size, bg):
        return (f'<div class="cell {bg}"><div class="duo">'
                f'<img src="07-beta.png" width="{size}" height="{size}" alt="">'
                f'<img src="{old}" width="{size}" height="{size}" alt=""></div></div>')

    rows = []
    for key, _, name in BETA_GREENS + [("teal", None, "Teal (the first draft)")]:
        src = "07-beta-old.png" if key == "teal" else f"07-old-{key}.png"
        label = name if key == "teal" else f"07-old-{key} &middot; {html.escape(name)}"
        mark = " pick" if key == GREEN_PICK else (" ref" if key == "teal" else "")
        rows.append(f'<div class="green{mark}"><span class="tag">{label}</span><div class="row">'
                    f'{pair(src, 32, "dark")}{pair(src, 32, "light")}{pair(src, 96, "modrinth")}</div></div>')
    return ('<section class="card chosen" id="c07-green"><h2><span class="num">07</span>Old Faithful in green</h2>'
            f'<p>Pure (left of each pair) as chosen; Old Faithful greener than teal, in four steps. '
            f'{html.escape(GREEN_WHY)}</p>{"".join(rows)}</section>')


def rounds(out):
    """Candidate rounds kept beside the page as `<digits>*.py`, part of the workshop and not of the public script:
    each has draw(ic, out), which writes its pictures, and sections(ic), its cards for the page, newest first."""
    found = []
    for path in sorted(out.glob("[0-9]*.py")):
        spec = importlib.util.spec_from_file_location("round_" + path.stem, path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        found.append(module)
    return found


def page(entries, has_current, extra=()):
    def imgs(src, big=True):
        cells = []
        for bg in ("dark", "light"):
            cells.append(f'<div class="cell {bg}"><img src="{src}" width="32" height="32" alt=""></div>')
        if big:
            for bg in ("dark", "light"):
                cells.append(f'<div class="cell {bg} m"><img src="{src}" width="64" height="64" alt=""></div>')
        cells.append(f'<div class="cell modrinth"><img class="mr" src="{src}" width="96" height="96" alt=""></div>')
        return "".join(cells)

    cards = []
    for num, slug, name, idea, has_old in entries:
        old = ""
        if has_old:
            old = (f'<div class="pair"><span class="tag">Old Faithful</span>'
                   f'<div class="row">{imgs(f"{num}-{slug}-old.png", big=False)}</div></div>')
        cards.append(
            f'<section class="card" id="c{num}"><h2><span class="num">{num}</span>{html.escape(name)}</h2>'
            f'<p>{html.escape(idea)}</p><div class="row">{imgs(f"{num}-{slug}.png")}</div>{old}</section>')
    if has_current:
        cards.insert(0, '<section class="card ref" id="c00"><h2><span class="num">00</span>Current draft</h2>'
                        '<p>The geyser in the release now, for comparison.</p>'
                        f'<div class="row">{imgs("00-current.png")}</div></section>')
    cards.insert(0, greens_section())
    cards.insert(0, silvers_section())
    cards.insert(0, stills_section())
    cards[:0] = extra
    grid = "".join(f'<figure><img src="{num}-{slug}.png" width="32" height="32" alt=""><figcaption>{num}</figcaption></figure>'
                   for num, slug, *_ in entries)
    return f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Faithful icons</title>
<style>
:root {{ --ink:#2a3346; --soft:#6b7489; --line:#dfe4ec; }}
* {{ box-sizing:border-box; }}
body {{ margin:0; font:15px/1.45 system-ui, -apple-system, "Segoe UI", sans-serif; color:var(--ink);
  background:linear-gradient(180deg,#f6f8fb,#eceff4); }}
main {{ max-width:880px; margin:0 auto; padding:28px 18px 60px; }}
h1 {{ font-size:22px; font-weight:600; margin:0 0 4px; }}
.sub {{ color:var(--soft); margin:0 0 22px; }}
.card {{ background:rgba(255,255,255,.75); border:1px solid var(--line); border-radius:14px; padding:14px 16px 16px;
  margin:0 0 14px; box-shadow:0 1px 2px rgba(30,40,60,.05); }}
.card.ref {{ opacity:.8; }}
h2 {{ font-size:16px; font-weight:600; margin:0 0 2px; display:flex; gap:10px; align-items:baseline; }}
.num {{ font:600 13px ui-monospace, Consolas, monospace; color:#3d6fb0; background:#e7eef8; padding:1px 7px;
  border-radius:6px; }}
p {{ margin:0 0 10px; color:var(--soft); }}
.row {{ display:flex; flex-wrap:wrap; gap:8px; align-items:center; }}
.cell {{ display:grid; place-items:center; border-radius:8px; padding:10px; min-width:52px; min-height:52px; }}
.cell.m {{ min-width:84px; min-height:84px; }}
.dark {{ background:#2b2d31; }}
.light {{ background:#f2f2f2; border:1px solid #e2e2e2; }}
.modrinth {{ background:#16181c; padding:14px; }}
img {{ image-rendering:pixelated; image-rendering:crisp-edges; display:block; }}
img.mr {{ border-radius:16px; }}
.pair {{ margin-top:10px; padding-top:10px; border-top:1px dashed var(--line); }}
.tag {{ display:block; font-size:12px; color:var(--soft); margin-bottom:6px; }}
.duo {{ display:flex; gap:8px; }}
.green {{ margin-top:10px; }}
.green.pick .tag {{ color:#2c7a55; font-weight:600; }}
.green.ref {{ opacity:.7; }}
.card.chosen {{ border-color:#bcd3ea; }}
.legend {{ display:flex; gap:8px; font-size:12px; color:var(--soft); margin:0 0 14px; flex-wrap:wrap; }}
.legend span {{ padding:2px 8px; border-radius:6px; background:#fff; border:1px solid var(--line); }}
.grid {{ display:flex; flex-wrap:wrap; gap:14px; background:#2b2d31; border-radius:12px; padding:16px; margin-bottom:22px; }}
figure {{ margin:0; display:grid; justify-items:center; gap:4px; }}
figcaption {{ font:12px ui-monospace, Consolas, monospace; color:#b9bfca; }}
</style></head>
<body><main>
<h1>Faithful icons</h1>
<p class="sub">Twelve candidates, each with a number to name it by.</p>
<div class="grid">{grid}</div>
<div class="legend"><span>Prism 32 px, dark and light</span><span>64 px, dark and light</span><span>Modrinth 96 px</span></div>
{"".join(cards)}
</main></body></html>
"""


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    out = Path(argv[0]) if argv else ROOT / "prism" / "release" / "icons"
    out.mkdir(parents=True, exist_ok=True)
    entries = []
    for num, slug, name, idea, draw, old in CANDIDATES:
        draw().image().save(out / f"{num}-{slug}.png", optimize=True)
        if old:
            old().image().save(out / f"{num}-{slug}-old.png", optimize=True)
        entries.append((num, slug, name, idea, old is not None))
    for key, hue, _ in BETA_GREENS:
        c07_beta(beta_green(hue)).image().save(out / f"07-old-{key}.png", optimize=True)
    for key, _, top_hue, bottom_hue, _ in STILLS:
        still_seal(top_hue, bottom_hue).image().save(out / f"07-still-{key}.png", optimize=True)
    for key, _, silver, shadow in SILVERS:
        silver_seal(silver, shadow).image().save(out / f"07-old-c-{key}.png", optimize=True)
    # The chosen Pure Faithful icon at the release's sizes: Prism's instance icon (128, as purefaithful.png), the
    # mods' icons (128), Modrinth (512; 96 and up), and the native 32.
    final = out / "final"
    final.mkdir(exist_ok=True)
    pure = c07_beta().image()
    for size in (32, 64, 128, 256, 512):
        pure.resize((size, size), Image.NEAREST).save(final / f"purefaithful-{size}.png", optimize=True)
    pure.resize((128, 128), Image.NEAREST).save(final / "purefaithful.png", optimize=True)
    # Old Faithful's: the spring green the user picked (07-old-c) with a silver rim and β, s3 (the silvers looked alike
    # to them at icon sizes; s3's green shadow is the one that differs, 2026-11-02).
    old = silver_seal(*next((silver, shadow) for key, _, silver, shadow in SILVERS if key == "s3")).image()
    for size in (32, 64, 128, 256, 512):
        old.resize((size, size), Image.NEAREST).save(final / f"oldfaithful-{size}.png", optimize=True)
    # Still Faithful's: dusk violet (07-still-a), the user's pick (2026-11-02).
    still = still_seal(*next((t, b) for key, _, t, b, _ in STILLS if key == "a")).image()
    for size in (32, 64, 128, 256, 512):
        still.resize((size, size), Image.NEAREST).save(final / f"stillfaithful-{size}.png", optimize=True)
    # More Faithful's: crimson (above).
    more = more_seal().image()
    for size in (32, 64, 128, 256, 512):
        more.resize((size, size), Image.NEAREST).save(final / f"morefaithful-{size}.png", optimize=True)
    current = next((p for p in (ROOT / "prism" / "release" / n for n in ("purefaithful.png", "oldfaithful.png"))
                    if p.exists()), None)
    if current:
        img = Image.open(current)
        img.resize((32, 32), Image.NEAREST).save(out / "00-current.png", optimize=True)
    ic, extra = sys.modules[__name__], []
    for module in rounds(out):
        module.draw(ic, out)
        extra += module.sections(ic)
    (out / "index.html").write_text(page(entries, current is not None, extra), encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
