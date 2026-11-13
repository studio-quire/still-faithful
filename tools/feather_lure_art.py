"""The Lured Fishing Rod's sprite, beta's fishing rod with a white tuft round its hook, and its bobber's.

    python tools/feather_lure_art.py [--sheet PATH] [--dry] [--variant NAME] [--bobber NAME]

Writes the item's sprite into library/feather-lure's assets, beta's own rod from the game jar in local/cache with a
feather drawn on, in beta's feather's own greys and white. Cast, the rod is beta's cast rod (tile 85), as the feather
goes out with the line; the game draws that from the player's own items.png, so nothing here draws it. A lured line's
bobber is beta's (tile (1, 2) of particles.png, 8x8) with a small feather drawn over its hook, written beside the item as
textures/entity/lured_bobber.png. --sheet PATH also writes a sheet for looking at: each rod variant enlarged beside
beta's rod, at its own size and twice it, in the inventory's grey and on the hotbar by day and by night; --dry writes
nothing into the mod.
Claude Opus 5.5, 2026-11-11.
"""
import argparse
import io
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, "local", "cache", "minecraft", "b1.7.3-client.jar")
ASSETS = os.path.join(ROOT, "library", "feather-lure", "src", "main", "resources", "assets", "feather-lure", "textures")
INVENTORY_GREY = (0x8b, 0x8b, 0x8b, 255)

# Beta's feather's own inks: white, its light vane, its shade and its quill's dark end.
INKS = {"W": "ffffff", "l": "d3d3d3", "Q": "686868", "K": "353535"}
# Columns 9 to 15, rows 9 to 15 of beta's rod, where its line comes down to the white hook. "=" keeps beta's pixel,
# "." clears it.
AT = (9, 9)
BETA = [
    ".....=.",  # row 9: the line at column 14
    "....=..",
    "....=..",
    "...=...",
    ".=.=...",  # the hook
    ".==....",
    ".......",
]
# The one drawn is CHOSEN; the others were looked at beside it, kept for a sheet if the look is ever reconsidered.
# "along" shipped first; "tuft" replaced it after two rounds of variants (2026-11-11).
VARIANTS = {
    # A white tuft round beta's own hook, pale where it meets the rod's side: a fly tied on.
    "tuft": [
        ".....=.",
        "....=..",
        "....=..",
        "..W=...",
        "l=W=W..",
        "W==l...",
        ".......",
    ],
    # Lying along the line above the hook, white on its open side and pale against the line, its quill's end in the
    # hook: beta's feather in small, tied on.
    "along": [
        ".....=.",
        "...W=..",
        "..Wl=..",
        ".Wl=...",
        ".=Q=...",
        ".==....",
        ".......",
    ],
    # Tied on at the hook, on the line's outer side, slanting as beta's feather does: reads as a thicker line.
    "beside": [
        ".....=.",
        "....=.W",
        "....=Wl",
        "...=WlQ",
        ".=.=QK.",
        ".==....",
        ".......",
    ],
    # Hanging from the line into the space between it and the rod: reads as a hook of its own.
    "inside": [
        "....Q=.",
        "..Wl=..",
        ".Wl.=..",
        "lW.=...",
        ".=.=...",
        ".==....",
        ".......",
    ],
}
CHOSEN = "tuft"

# Beta's bobber, the whole 8x8 tile: its float (grey, red, grey) on the black line, which ends in the hook. Same marks
# as the rod's.
BOBBER_BETA = [
    "........",
    "...===..",
    "...===..",
    "...===..",
    "....=...",
    "....=...",
    "..=.=...",
    "...==...",
]
# Named as on the sheets of 2026-11-11. "four-soft" is the one drawn, the one that read most like a feather (only the
# four-pixel shapes did): a feather lying over the hook's barb and bend,
# those two a shade darker. T1 to T5 were the first round.
BOBBER_VARIANTS = {
    # Four pixels slanting down over the hook: the tip, the barb (light) beside white, the bend (light).
    "four-soft": BOBBER_BETA[:5] + ["..W.=...", "..lW=...", "...l=..."],
    # A white tuft round the hook, as on the rod.
    "T1": BOBBER_BETA[:6] + ["..=W=l..", "..W==W.."],
    # A fuller tuft round the hook.
    "T2": BOBBER_BETA[:5] + ["...l=l..", "..=W=W..", "..W==Wl."],
    # A plume standing up out of the float, which shows above the water.
    "T3": ["....Wl.."] + BOBBER_BETA[1:],
    # A feather beside the float, slanting up.
    "T4": ["......Wl", "...===W.", "...===l."] + BOBBER_BETA[3:],
    # The tuft at the hook and a small plume on top.
    "T5": ["....W..."] + BOBBER_BETA[1:6] + ["..=W=...", "..W==W.."],
}
BOBBER_CHOSEN = "four-soft"


def rgb(hex):
    return tuple(int(hex[i:i + 2], 16) for i in (0, 2, 4))


def drawn(rod, rows, at=AT):
    out = rod.copy()
    px = out.load()
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == ".":
                px[at[0] + i, at[1] + j] = (0, 0, 0, 0)
            elif ch != "=":
                px[at[0] + i, at[1] + j] = rgb(INKS[ch]) + (255,)
    return out


def beta():
    jar = zipfile.ZipFile(JAR)
    items = Image.open(io.BytesIO(jar.read("gui/items.png"))).convert("RGBA")
    hotbar = Image.open(io.BytesIO(jar.read("gui/gui.png"))).convert("RGBA").crop((0, 0, 182, 22))
    return items.crop((5 * 16, 4 * 16, 6 * 16, 5 * 16)), hotbar


def beta_bobber():
    jar = zipfile.ZipFile(JAR)
    return Image.open(io.BytesIO(jar.read("particles.png"))).convert("RGBA").crop((8, 16, 16, 24))


def check_template(sprite, rows, at, what):
    """The template's "=" cells are where beta's sprite has pixels in that window, and nowhere else: a guard against a
    misread of the sheet."""
    px = sprite.load()
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if (ch == "=") != (px[at[0] + i, at[1] + j][3] != 0):
                raise SystemExit("beta's %s isn't as its template says at %d,%d" % (what, at[0] + i, at[1] + j))


def big(sprite, k):
    return sprite.resize((sprite.size[0] * k, sprite.size[1] * k), Image.NEAREST)


def in_slots(sprites, hotbar=None, world=None):
    w = 2 + len(sprites) * 20
    out = Image.new("RGBA", (w, 22), INVENTORY_GREY if hotbar is None else world + (255,))
    if hotbar is not None:
        out.alpha_composite(hotbar.crop((0, 0, w, 22)))
    for i, s in enumerate(sprites):
        out.alpha_composite(s, (3 + i * 20, 3))
    return out


def sheet(path, rows):
    from PIL import ImageDraw, ImageFont
    rod, hotbar = beta()
    font = ImageFont.truetype("arial.ttf", 15)
    k = 10
    line = 16 * k + 14
    out = Image.new("RGBA", (560, 8 + len(rows) * line), INVENTORY_GREY)
    d = ImageDraw.Draw(out)
    for j, (label, item) in enumerate(rows):
        y = 8 + j * line
        d.text((8, y + 70), label, font=font, fill=(0x18, 0x18, 0x18, 255))
        out.alpha_composite(big(item, k), (90, y))
        x = 90 + 16 * k + 16
        pair = [rod, item]
        for kk, yy in ((2, y + 8), (1, y + 64)):
            xx = x
            for strip in (in_slots(pair), in_slots(pair, hotbar, (0x6a, 0x9a, 0x40)), in_slots(pair, hotbar, (0x2a, 0x2a, 0x30))):
                out.alpha_composite(big(strip, kk), (xx, yy))
                xx += strip.size[0] * kk + 8
    out.save(path)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--sheet", help="a file for the sheet")
    p.add_argument("--dry", action="store_true", help="write nothing into the mod")
    p.add_argument("--variant", default=CHOSEN, choices=sorted(VARIANTS))
    p.add_argument("--bobber", default=BOBBER_CHOSEN, choices=sorted(BOBBER_VARIANTS))
    a = p.parse_args()
    rod, _ = beta()
    check_template(rod, BETA, AT, "rod")
    bobber = beta_bobber()
    check_template(bobber, BOBBER_BETA, (0, 0), "bobber")
    if a.sheet:
        os.makedirs(os.path.dirname(os.path.abspath(a.sheet)), exist_ok=True)
        sheet(a.sheet, [("beta's rod", rod)] + [(name, drawn(rod, rows)) for name, rows in VARIANTS.items()])
    if not a.dry:
        os.makedirs(os.path.join(ASSETS, "item"), exist_ok=True)
        drawn(rod, VARIANTS[a.variant]).save(os.path.join(ASSETS, "item", "lured_fishing_rod.png"))
        os.makedirs(os.path.join(ASSETS, "entity"), exist_ok=True)
        drawn(bobber, BOBBER_VARIANTS[a.bobber], (0, 0)).save(os.path.join(ASSETS, "entity", "lured_bobber.png"))


if __name__ == "__main__":
    main()
