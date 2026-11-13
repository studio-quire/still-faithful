#!/usr/bin/env python3
"""Eye-level pictures of Buried Towers' rooms in the real game, chosen from the tower's dump.

  tower_shots.py views <plan.json> <dump.bin>[:kind,kind...]...    prints the -PsceneViews string for :harness:sceneShotTowers, and writes which view is what
  tower_shots.py montage <plan.json> <shots dir> <out dir>         a labelled sheet per tower

Places are outside the walls by bearing, as a dump doesn't reach far enough to know the land there: some come out in a hill or a tree.
"""
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from tower_view import Dump, font

from PIL import Image, ImageDraw

THROUGH = {0, 30, 31, 32, 37, 38, 39, 40, 50, 66, 70, 72, 69, 8, 9, 65, 44, 85}
SHEET_W, SHEET_H = 800, 500


def yaw_of(dx, dz):
    return math.degrees(math.atan2(-dx, dz)) % 360


def free(d, x, y, z):
    return d.id(x, y, z) in (0, 31, 37, 38, 39, 40, 50) and d.id(x, y + 1, z) in (0, 50) and d.id(x, y - 1, z) not in (0, 8, 9, 10, 11, 50)


def ray(d, x, y, z, angle):
    dx, dz = math.sin(angle), math.cos(angle)
    t = 0.0
    while t < 30:
        t += 0.5
        ax, az = math.floor(x + 0.5 + dx * t), math.floor(z + 0.5 + dz * t)
        if d.id(ax, y, az) not in THROUGH or d.id(ax, y + 1, az) not in THROUGH:
            return t
    return t


def scan(d, y, count, toward=None, near=3):
    """Places to stand that see a long way with nothing in the near part of the view, looking roughly at the middle. Tight rooms have none, so it settles for less."""
    found = look(d, y, toward, near)
    while not found and near > 1:
        near -= 1
        found = look(d, y, toward, near)
    chosen = []
    for score, x, z, a in found:
        at = math.atan2(z - d.cz, x - d.cx)
        if all(abs(math.remainder(at - math.atan2(c[2] - d.cz, c[1] - d.cx), math.pi * 2)) > 1.2 for c in chosen):
            chosen.append((score, x, z, a))
        if len(chosen) == count:
            break
    return chosen


def look(d, y, toward, near):
    r = d.radius
    tx, tz = toward or (d.cx, d.cz)
    found = []
    for x in range(d.cx - r, d.cx + r + 1):
        for z in range(d.cz - r, d.cz + r + 1):
            if not free(d, x, y, z):
                continue
            base = math.atan2(tx - x, tz - z)
            for off in (-0.5, -0.25, 0, 0.25, 0.5):
                a = base + off
                fan = [ray(d, x, y, z, a + o) for o in (-0.7, -0.45, -0.2, 0, 0.2, 0.45, 0.7)]
                if min(fan) < near:
                    continue
                score = sum(fan) / len(fan) + min(fan) - abs(off) * 2 + math.hypot(x - d.cx, z - d.cz) * 0.25
                found.append((score, x, z, a))
    found.sort(reverse=True)
    return found


def shot(parts, entry, x, y, z, a, pitch, label):
    parts.append(f"{x} {y + 1.62:.2f} {z} {math.degrees(-a) % 360:.0f} {pitch} 6000")
    entry["shots"].append(label)


def cart_views(d, parts, entry):
    """A cart run: its doorway at the top seen from the room, the track going down from the landing, the doorway at the foot, the shaft from the foot, and a chute from a floor it skips."""
    c = d.info["cart"]
    floors = d.info["floors"]
    sx, sy, sz = c["start"]
    ex, ey, ez = c["end"]
    ox, oz = c["lever"][0] - sx, c["lever"][2] - sz
    ux, uz = -oz, ox
    if d.id(sx + ux, sy, sz + uz) not in (66, 27):
        ux, uz = -ux, -uz
    wall = 1 if d.info.get("style") == "SIMPLE" else 2
    inward = wall + 1

    def into_room(x, z, back):
        return x - ox * (inward + back), z - oz * (inward + back)

    # The top: from the room, three back from the doorway, looking at it and the chest beside it.
    dx, dz = sx + ux, sz + uz
    rx, rz = into_room(dx, dz, 3)
    shot(parts, entry, rx, sy, rz, math.atan2(dx - rx, dz - rz), 8, "cart run, the doorway at the top")
    # On the landing beside the station, looking along the track and down it.
    lx, lz = sx + ox + ux, sz + oz + uz
    parts.append(f"{lx} {sy + 1.62:.2f} {lz} {yaw_of(ux, uz):.0f} 30 6000")
    entry["shots"].append("cart run, down the track from the top")
    parts.append(f"{sx - ux} {sy + 2.4:.2f} {sz - uz} {yaw_of(ux + ox * 0.6, uz + oz * 0.6):.0f} 55 6000")
    entry["shots"].append("cart run, the shaft from the top")
    # The foot: from the room, looking at the doorway, and from the station up the shaft.
    rx, rz = into_room(ex, ez, 3)
    shot(parts, entry, rx, ey, rz, math.atan2(ex - rx, ez - rz), 8, "cart run, the doorway at the foot")
    parts.append(f"{ex - ux} {ey + 1.62:.2f} {ez - uz} {yaw_of(-ux + ox * 0.5, -uz + oz * 0.5):.0f} -55 6000")
    entry["shots"].append("cart run, up the shaft from the foot")
    # A chute from the first floor skipped, seen from its room.
    for i in range(c["from"] + 1, c["to"]):
        f = floors[i]
        for k in (1, -1, 2, -2, 0):
            hx, hz = sx + ux * (k + 1) - ox, sz + uz * (k + 1) - oz
            if d.id(hx, f["stand"], hz) == 0 and d.id(hx, f["stand"] + 1, hz) == 0:
                rx, rz = into_room(hx + ox, hz + oz, 3)
                shot(parts, entry, rx, f["stand"], rz, math.atan2(hx - rx, hz - rz), 10, f"cart run, a chute from {f['kind']}")
                break
        break


def facing(d, parts, entry, level, ids, label, pitch=6, backs=(6, 5, 4, 3), seek=None):
    """Square on to the middle one of the cells holding any of ids at a level (or at seek, if given), standing on the level, from as far back toward the room's middle as there's a free place."""
    seek = level if seek is None else seek
    cells = [(x, z) for x in range(d.cx - d.radius - 4, d.cx + d.radius + 5) for z in range(d.cz - d.radius - 4, d.cz + d.radius + 5) if d.id(x, seek, z) in ids]
    if not cells:
        return
    sx = sum(c[0] for c in cells) / len(cells)
    sz = sum(c[1] for c in cells) / len(cells)
    sx, sz = min(cells, key=lambda c: (c[0] - sx) ** 2 + (c[1] - sz) ** 2)
    far = math.hypot(sx - d.cx, sz - d.cz) or 1
    for back in backs:
        cx, cz = round(sx + (d.cx - sx) / far * back), round(sz + (d.cz - sz) / far * back)
        if free(d, cx, level, cz):
            shot(parts, entry, cx, level, cz, math.atan2(sx - cx, sz - cz), pitch, label)
            return True


def views(out, args):
    plan, parts = [], []
    for arg in args:
        path, _, only = arg.partition(":")
        d = Dump(path)
        entry = {"name": Path(path).stem, "shots": []}
        gx, gz = d.info["gate"]
        ox, oz = gx - d.cx, gz - d.cz
        n = math.hypot(ox, oz) or 1
        ground = d.info["groundY"]
        for off, dist, up, label in ((0.5, d.radius + 22, 8, "outside, the gate"), (-1.9, d.radius + 24, 8, "outside, round the side"), (2.6, d.radius + 24, 8, "outside, the far side"), (1.0, d.radius + 30, 20, "outside, from above")) if not only else ():
            ca, sa = math.cos(off), math.sin(off)
            rx, rz = (ox * ca - oz * sa) / n, (ox * sa + oz * ca) / n
            ex, ez = int(d.cx + rx * dist), int(d.cz + rz * dist)
            ey = max(ground + up, 66)
            pitch = math.degrees(math.atan2(ey - (ground + 8), math.hypot(d.cx - ex, d.cz - ez)))
            parts.append(f"{ex} {ey:.2f} {ez} {yaw_of(d.cx - ex, d.cz - ez):.0f} {pitch:.0f} 6000")
            entry["shots"].append(label)
        for f in d.info["floors"]:
            kind = f["kind"]
            if only and not any(o in kind for o in only.split(",")):
                continue
            label = f"{f['index']} {kind}"
            y = f["stand"]
            if kind == "ruin":
                for _, x, z, a in scan(d, y, 1):
                    shot(parts, entry, x, y, z, a, 6, label)
            elif kind == "gate hall":
                for _, x, z, a in scan(d, y, 2):
                    shot(parts, entry, x, y, z, a, 4, label)
                shot(parts, entry, d.cx, y, d.cz, 0.8, -55, label + ", up through the roof")
            elif kind == "library":
                for _, x, z, a in scan(d, y, 2):
                    shot(parts, entry, x, y, z, a, 2, label)
                for _, x, z, a in scan(d, y + 5, 1):
                    shot(parts, entry, x, y + 5, z, a, 12, label + ", the gallery")
                # Square on to a case of books, on the floor and on the gallery.
                for level, name in ((y, "a case"), (y + 5, "a case on the gallery")):
                    shelves = [(x, z) for x in range(d.cx - d.radius, d.cx + d.radius + 1) for z in range(d.cz - d.radius, d.cz + d.radius + 1) if d.id(x, level, z) == 47]
                    if shelves:
                        sx, sz = shelves[len(shelves) // 2]
                        far = math.hypot(sx - d.cx, sz - d.cz) or 1
                        for back in (6, 5, 4, 3):
                            cx, cz = round(sx + (d.cx - sx) / far * back), round(sz + (d.cz - sz) / far * back)
                            if free(d, cx, level, cz):
                                shot(parts, entry, cx, level, cz, math.atan2(sx - cx, sz - cz), 6, label + ", " + name)
                                break
            elif kind == "bridges over a pit":
                for _, x, z, a in scan(d, y, 2):
                    shot(parts, entry, x, y, z, a, 4, label)
                    shot(parts, entry, x, y, z, a, 34, label + ", down into it")
                # Standing on a bridge, looking along the shaft and down.
                done = 0
                for k, (ax, az) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
                    bx, bz = d.cx + ax * 5, d.cz + az * 5
                    if done < 2 and d.id(bx, y - 1, bz) == 5 and free(d, bx, y, bz):
                        # Along the shaft, across the bridge.
                        shot(parts, entry, bx, y, bz, math.atan2(az, -ax) if ax else math.atan2(az, ax), 38, label + ", from a bridge")
                        done += 1
                # Partway down, in the air, as a look at the wall.
                ring = d.radius - 5
                shot(parts, entry, d.cx + ring, y - 5, d.cz, 0.0, 12, label + ", in the shaft")
                shot(parts, entry, d.cx - ring, y - 5, d.cz, 3.14, 12, label + ", in the shaft")
                low = f["y"] + 2
                for _, x, z, a in scan(d, low, 1):
                    shot(parts, entry, x, low, z, a, -32, label + ", up from the ledge")
            elif kind == "spiral gallery":
                # From the stage at the top, across and down the walkway; from the floor, up it.
                for _, x, z, a in scan(d, y, 2):
                    shot(parts, entry, x, y, z, a, 28, label + ", from the top")
                for _, x, z, a in scan(d, f["y"], 1):
                    shot(parts, entry, x, f["y"], z, a, -40, label + ", up from the floor")
            elif kind == "aquarium":
                for _, x, z, a in scan(d, y, 1):
                    shot(parts, entry, x, y, z, a, 4, label)
                facing(d, parts, entry, y, {20}, label + ", a window", pitch=4, backs=(4, 3, 2), seek=y + 1)
            elif kind == "jukebox hall":
                for _, x, z, a in scan(d, y, 1):
                    shot(parts, entry, x, y, z, a, 4, label)
                facing(d, parts, entry, y, {84}, label + ", the jukebox", pitch=12) or facing(d, parts, entry, y, {84}, label + ", the jukebox", pitch=12, seek=y + 1)
            elif kind == "portal frame":
                for _, x, z, a in scan(d, y, 1):
                    shot(parts, entry, x, y, z, a, 4, label)
                facing(d, parts, entry, y, {49}, label + ", the frame", pitch=-4, backs=(7, 6, 5, 4), seek=y + 2) or facing(d, parts, entry, y, {49}, label + ", the frame", pitch=-4, backs=(7, 6, 5, 4), seek=y + 3)
            elif kind == "lava lake":
                for _, x, z, a in scan(d, y, 2):
                    shot(parts, entry, x, y, z, a, 14, label)
            elif kind == "sand floor":
                for _, x, z, a in scan(d, y, 2):
                    shot(parts, entry, x, y, z, a, 10, label)
            elif kind == "vault":
                for _, x, z, a in scan(d, y, 3):
                    shot(parts, entry, x, y, z, a, 6, label)
            else:
                for _, x, z, a in scan(d, y, 2):
                    shot(parts, entry, x, y, z, a, 4, label)
        if d.info.get("cart") and (not only or "cart" in only):
            cart_views(d, parts, entry)
        plan.append(entry)
    Path(out).write_text(json.dumps(plan))
    print(";".join(parts))


def montage(out, shots, dest, suffix=""):
    plan = json.loads(Path(out).read_text())
    shots, dest = Path(shots), Path(dest)
    dest.mkdir(parents=True, exist_ok=True)
    at = 0
    for entry in plan:
        cells = []
        for label in entry["shots"]:
            p = shots / f"view-{at}.png"
            at += 1
            if label and p.exists():
                cells.append((label, Image.open(p).convert("RGB").resize((SHEET_W, SHEET_H), Image.LANCZOS)))
        cols = 3
        rows = (len(cells) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * SHEET_W, rows * SHEET_H), (16, 16, 20))
        draw = ImageDraw.Draw(sheet)
        for i, (label, img) in enumerate(cells):
            x, y = i % cols * SHEET_W, i // cols * SHEET_H
            sheet.paste(img, (x, y))
            draw.text((x + 9, y + 7), label, fill=(0, 0, 0), font=font(22))
            draw.text((x + 8, y + 6), label, fill=(255, 255, 255), font=font(22))
        sheet.save(dest / f"{entry['name']}{suffix}.png")


if sys.argv[1] == "views":
    views(sys.argv[2], sys.argv[3:])
else:
    montage(*sys.argv[2:])
