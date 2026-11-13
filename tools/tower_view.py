#!/usr/bin/env python3
"""Pictures of a Buried Tower, from the file library/buried-towers' PlanDump or the test driver writes.

  tower_view.py <dump.bin> <out dir> [--route]   a floor plan per floor, a side section each way, and a sheet of all of it
  tower_view.py --sheet <out.png> <tower dir>...  one contact sheet of several towers' sections

Textures come from terrain.png in the game's own client jar (local/cache/minecraft), read where it lies: nothing of the
game's goes in this repository, and the pictures stay in local/ or the output folder.
"""
import json
import struct
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
JAR = ROOT / "local/cache/minecraft/b1.7.3-client.jar"
TILE = 16

# Block id -> (top tile, side tile), by the tile's place in terrain.png (row * 16 + column). A meta list where it matters.
TILES = {
    1: (1, 1), 2: (0, 3), 3: (2, 2), 4: (16, 16), 5: (4, 4), 6: (15, 15), 7: (17, 17), 8: (205, 205), 9: (205, 205),
    10: (237, 237), 11: (237, 237), 12: (18, 18), 13: (19, 19), 14: (32, 32), 15: (33, 33), 16: (34, 34), 17: (21, 20),
    18: (52, 52), 19: (48, 48), 20: (49, 49), 21: (160, 160), 22: (144, 144), 23: (62, 46), 24: (176, 192), 25: (74, 74),
    26: (134, 150), 27: (163, 163), 28: (195, 195), 29: (106, 108), 30: (11, 11), 31: (39, 39), 32: (55, 55), 33: (107, 108),
    35: (64, 64), 37: (13, 13), 38: (12, 12), 39: (29, 29), 40: (28, 28), 41: (23, 23), 42: (22, 22), 43: (6, 5), 44: (6, 5),
    45: (7, 7), 46: (9, 8), 47: (4, 35), 48: (36, 36), 49: (37, 37), 50: (80, 80), 51: (31, 31), 52: (65, 65), 53: (4, 4),
    54: (25, 26), 55: (84, 84), 56: (50, 50), 57: (24, 24), 58: (43, 59), 59: (95, 95), 60: (87, 2), 61: (62, 45), 62: (62, 61),
    63: (4, 4), 64: (81, 97), 65: (83, 83), 66: (128, 128), 67: (16, 16), 68: (4, 4), 69: (96, 16), 70: (1, 1), 71: (82, 98),
    72: (4, 4), 73: (51, 51), 76: (99, 99), 77: (1, 1), 78: (66, 68), 79: (67, 67), 80: (66, 66), 81: (69, 70), 82: (72, 72),
    83: (73, 73), 84: (75, 74), 85: (4, 4), 86: (102, 118), 87: (103, 103), 88: (104, 104), 89: (105, 105), 90: (14, 14),
    91: (102, 120), 92: (121, 122), 93: (131, 131), 96: (84, 84),
}
WOOL = [64, 210, 194, 178, 162, 146, 130, 114, 225, 209, 193, 177, 161, 145, 129, 113]
SLABS = [(6, 5), (176, 192), (4, 4), (16, 16)]
LOGS = [(21, 20), (21, 116), (21, 117)]
# What a picture should show as a small shape rather than a whole tile.
THIN = {50: "torch", 69: "lever", 76: "torch", 70: "plate", 72: "plate", 77: "plate", 78: "plate", 85: "post", 64: "door", 71: "door"}
TINT = {2: (120, 180, 80), 18: (90, 150, 70), 31: (120, 180, 80)}


def load_tiles():
    with zipfile.ZipFile(JAR) as jar:
        with jar.open("terrain.png") as f:
            sheet = Image.open(f).convert("RGBA")
            sheet.load()
    return sheet


class Dump:
    def __init__(self, path):
        raw = Path(path).read_bytes()
        self.ox, self.oz, self.size, self.cx, self.cz, self.radius = struct.unpack(">6i", raw[:24])
        at = 24
        self.ids = raw[at:]
        self.step = 256
        text = at + self.size * self.size * self.step
        length = struct.unpack(">i", raw[text:text + 4])[0]
        self.info = json.loads(raw[text + 4:text + 4 + length].decode("utf-8"))
        self.fixtures = self.info.get("fixtures", {})

    def id(self, x, y, z):
        if not (self.ox <= x < self.ox + self.size and self.oz <= z < self.oz + self.size and 0 <= y < 128):
            return 0
        return self.ids[((x - self.ox) * self.size + z - self.oz) * self.step + y]

    def meta(self, x, y, z):
        if not (self.ox <= x < self.ox + self.size and self.oz <= z < self.oz + self.size and 0 <= y < 128):
            return 0
        return self.ids[((x - self.ox) * self.size + z - self.oz) * self.step + 128 + y]


class Painter:
    def __init__(self):
        self.sheet = load_tiles()
        self.cache = {}

    def tile(self, index, size):
        key = (index, size)
        if key not in self.cache:
            x, y = index % 16 * TILE, index // 16 * TILE
            self.cache[key] = self.sheet.crop((x, y, x + TILE, y + TILE)).resize((size, size), Image.NEAREST)
        return self.cache[key]

    def block(self, block, meta, side, size):
        if block == 35:
            index = WOOL[meta & 15]
        elif block in (43, 44):
            pair = SLABS[meta & 3]
            index = pair[side]
        elif block == 17:
            index = LOGS[meta & 3][side]
        elif block in TILES:
            index = TILES[block][side]
        else:
            index = 1
        img = self.tile(index, size)
        if block in TINT and side == 0:
            tint = Image.new("RGBA", img.size, TINT[block] + (255,))
            alpha = img.split()[3]
            img = Image.composite(Image.blend(img, tint, 0.55), img, alpha)
        return img


def shade(img, light):
    if light >= 0.999:
        return img
    dark = Image.new("RGBA", img.size, (0, 0, 0, 255))
    out = Image.blend(img, dark, 1 - light)
    out.putalpha(img.split()[3])
    return out


def font(size):
    for name in ("segoeui.ttf", "arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


TRANSPARENT = {0, 8, 9, 20, 30, 31, 32, 37, 38, 39, 40, 18, 50, 65, 66, 27, 28, 55, 69, 76, 70, 72, 77, 78, 85, 64, 71, 83, 81, 59, 6, 96, 63, 68, 51}


def plan_view(d, painter, floor, scale=10, route=None):
    """From just under a floor's ceiling looking down: walls in full, what's below shaded by how far it is."""
    y_top = min(floor["y"] + floor["h"] - 1, floor["stand"] + 2)
    y_low = max(0, floor["y"] - 3)
    margin = 8
    size = d.size * scale
    img = Image.new("RGBA", (size, size), (24, 24, 28, 255))
    for ix in range(d.size):
        for iz in range(d.size):
            x, z = d.ox + ix, d.oz + iz
            # Everything from the cut down to the first opaque cell, furthest first, so water and plants lie over the floor.
            layers = []
            for y in range(y_top, y_low - 1, -1):
                b = d.id(x, y, z)
                if b == 0:
                    continue
                layers.append((y, b, d.meta(x, y, z)))
                if b not in TRANSPARENT:
                    break
            at = (ix * scale, iz * scale)
            for y, b, m in reversed(layers):
                # Walls cut at the eye's height stay bright; what's below goes dark with depth, and what's outside the walls more so.
                light = 1.0 if y >= y_top - 1 else max(0.22, 0.62 - (y_top - y) * 0.05)
                if max(abs(x - d.cx), abs(z - d.cz)) > d.radius + 1:
                    light *= 0.4
                tile = shade(painter.block(b, m, 0, scale), light)
                if b in THIN:
                    tile = thin(tile, THIN[b], scale)
                img.alpha_composite(tile, at)
    return img


def thin(tile, shape, scale):
    out = Image.new("RGBA", tile.size, (0, 0, 0, 0))
    q = max(1, scale // 4)
    if shape == "torch":
        patch = Image.new("RGBA", (q * 2, q * 2), (255, 190, 60, 255))
        out.alpha_composite(patch, (scale // 2 - q, scale // 2 - q))
    elif shape == "lever":
        patch = Image.new("RGBA", (q * 2, q), (150, 150, 150, 255))
        out.alpha_composite(patch, (scale // 2 - q, scale // 2))
    elif shape == "post":
        out.alpha_composite(tile.crop((0, 0, q * 2, q * 2)), (scale // 2 - q, scale // 2 - q))
    elif shape == "door":
        patch = Image.new("RGBA", (scale, q), (190, 190, 200, 255))
        out.alpha_composite(patch, (0, scale // 2 - q // 2))
    else:
        out = tile.copy()
        out.putalpha(170)
    return out


def marks(img, d, floor, scale, route=None):
    draw = ImageDraw.Draw(img)
    y0, y1 = floor["y"] - 1, floor["y"] + floor["h"]
    for key, what in d.fixtures.items():
        x, y, z = [int(v) for v in key.split(",")]
        if not (y0 <= y <= y1):
            continue
        ix, iz = (x - d.ox) * scale, (z - d.oz) * scale
        color = (255, 214, 64) if what.startswith("chest") else (235, 64, 64)
        draw.rectangle([ix, iz, ix + scale - 1, iz + scale - 1], outline=color, width=2)
    if floor.get("mouth"):
        x, z = floor["mouth"]
        ix, iz = (x - d.ox) * scale, (z - d.oz) * scale
        draw.ellipse([ix - 3, iz - 3, ix + scale + 2, iz + scale + 2], outline=(80, 240, 120), width=3)
    sx, _, sz = floor["safe"]
    ix, iz = (sx - d.ox) * scale, (sz - d.oz) * scale
    draw.ellipse([ix - 3, iz - 3, ix + scale + 2, iz + scale + 2], outline=(90, 180, 255), width=3)
    if route:
        pts = [((x - d.ox) * scale + scale // 2, (z - d.oz) * scale + scale // 2) for x, y, z in route if y0 - 1 <= y <= y1 + 1]
        if len(pts) > 1:
            draw.line(pts, fill=(255, 255, 255, 180), width=1)


def section(d, painter, axis, scale=6):
    """A slice through the middle, with what's behind the slice dimmed to help read depth."""
    width = d.size
    top = min(127, max(f["y"] + f["h"] for f in d.info["floors"]) + 8)
    low = max(0, min(f["y"] for f in d.info["floors"]) - 4)
    img = Image.new("RGBA", (width * scale, (top - low + 1) * scale), (24, 24, 28, 255))
    for iw in range(width):
        for y in range(low, top + 1):
            layers = []
            for k in range(0, 12):
                x, z = (d.ox + iw, d.cz + k) if axis == "x" else (d.cx + k, d.oz + iw)
                b = d.id(x, y, z)
                if b == 0:
                    continue
                layers.append((k, b, d.meta(x, y, z)))
                if b not in TRANSPARENT:
                    break
            at = (iw * scale, (top - y) * scale)
            for k, b, m in reversed(layers):
                tile = shade(painter.block(b, m, 1, scale), max(0.3, 1 - k * 0.1))
                img.alpha_composite(tile, at)
    return img, low, top


def labelled_section(d, painter, axis, scale=6):
    img, low, top = section(d, painter, axis, scale)
    draw = ImageDraw.Draw(img)
    f = font(13)
    for floor in d.info["floors"]:
        y = (top - (floor["y"] + floor["h"] - 1)) * scale
        down = floor.get("down")
        text = f"{floor['index']} {floor['kind']}" + (f"  > {down}" if down else "")
        draw.text((4, y + 2), text, fill=(255, 255, 255, 255), font=f, stroke_width=2, stroke_fill=(0, 0, 0, 255))
    return img


def render(dump_path, out, route_path=None):
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    d = Dump(dump_path)
    painter = Painter()
    routes = json.loads(Path(route_path).read_text()) if route_path else {}
    route = routes.get("route")
    plans = []
    scale = 10
    for floor in d.info["floors"]:
        img = plan_view(d, painter, floor, scale)
        marks(img, d, floor, scale, route)
        name = f"floor{floor['index']:02d}-{floor['kind'].replace(' ', '_')}"
        title = f"{floor['index']}: {floor['kind']}  y={floor['y']} h={floor['h']} stand={floor['stand']} {floor['palette']}" + (f"  down: {floor['down']}" if floor.get("down") else "")
        framed = Image.new("RGBA", (img.width, img.height + 26), (16, 16, 20, 255))
        framed.alpha_composite(img, (0, 26))
        ImageDraw.Draw(framed).text((6, 4), title, fill=(255, 255, 255, 255), font=font(15))
        framed.convert("RGB").save(out / f"{name}.png")
        plans.append(framed)
    a = labelled_section(d, painter, "x")
    b = labelled_section(d, painter, "z")
    a.convert("RGB").save(out / "section-x.png")
    b.convert("RGB").save(out / "section-z.png")
    # All of it on a sheet.
    thumb = 460
    columns = 4
    rows = (len(plans) + columns - 1) // columns
    head = 34
    sheet = Image.new("RGB", (a.width + columns * (thumb + 6) + 12, max(a.height + head, rows * (thumb + 6 + 26) + head)), (16, 16, 20))
    title = f"{d.info['climate']} {d.info['purpose']}  at {d.cx},{d.cz}  land y={d.info['groundY']}  radius {d.radius}"
    ImageDraw.Draw(sheet).text((8, 6), title, fill=(255, 255, 255), font=font(18))
    sheet.paste(a.convert("RGB"), (6, head))
    for i, p in enumerate(plans):
        small = p.resize((thumb, int(p.height * thumb / p.width)), Image.LANCZOS)
        sheet.paste(small.convert("RGB"), (a.width + 12 + i % columns * (thumb + 6), head + i // columns * (thumb + 6 + 26)))
    sheet.save(out / "sheet.png")
    return out / "sheet.png"


def contact(out, towers):
    pics = []
    for t in towers:
        pics.append(Image.open(Path(t) / "sheet.png"))
    width = max(p.width for p in pics)
    sheet = Image.new("RGB", (width, sum(p.height + 8 for p in pics)), (16, 16, 20))
    y = 0
    for p in pics:
        sheet.paste(p, (0, y))
        y += p.height + 8
    sheet.save(out)


if __name__ == "__main__":
    args = sys.argv[1:]
    if args[0] == "--sheet":
        contact(args[1], args[2:])
    else:
        route = None
        if "--route" in args:
            at = args.index("--route")
            route = args[at + 1]
            del args[at:at + 2]
        print(render(args[0], args[1], route))
