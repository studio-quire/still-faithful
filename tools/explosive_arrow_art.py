"""The Explosive Arrow's two sprites: beta's arrow with a small charge for a head.

    python tools/explosive_arrow_art.py [--sheet PATH] [--dry]

Writes the item's sprite and the flying arrow's texture into library/explosive-arrows' assets, each beta's own from the
game jar in local/cache with only the head redrawn.
The head is a short stick of TNT lying along the arrow, red with TNT's pale band across its middle, lit from the upper
left as beta's arrowhead is. --sheet PATH also writes a sheet for looking at: beta's arrow beside it, enlarged, at its
own size, in the hotbar, and the flying texture; --dry writes nothing into the mod.
Claude Fable 5.2, 2026-11-09.
"""
import argparse
import io
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, "local", "cache", "minecraft", "b1.7.3-client.jar")
ASSETS = os.path.join(ROOT, "library", "explosive-arrows", "src", "main", "resources", "assets", "explosive-arrows", "textures")
INVENTORY_GREY = (0x8b, 0x8b, 0x8b, 255)

# TNT's own colours: its red lit and shaded, its band lit and shaded.
INKS = {"R": "db441a", "r": "a83414", "W": "dbdbdb", "w": "afafaf"}
# The item: rows 2 to 6, columns 9 to 14 of beta's arrow, whose head they hold. "=" keeps beta's pixel (the shaft's
# end), "." clears it.
ITEM_AT = (9, 2)
ITEM_HEAD = [
    "...RR.",
    "..WWrr",
    ".RRww.",
    ".=rr..",
    "==....",
]
# In flight: rows 1 to 3, columns 12 to 15 of beta's arrows.png, the side view's head and the shaft's last pixel.
FLYING_AT = (12, 1)
FLYING_HEAD = [
    "RWR.",
    "RWRr",
    "rwr.",
]


def rgb(hex):
    return tuple(int(hex[i:i + 2], 16) for i in (0, 2, 4))


def with_head(sprite, at, rows):
    out = sprite.copy()
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
    flying = Image.open(io.BytesIO(jar.read("item/arrows.png"))).convert("RGBA")
    hotbar = Image.open(io.BytesIO(jar.read("gui/gui.png"))).convert("RGBA").crop((0, 0, 182, 22))
    return items.crop((5 * 16, 2 * 16, 6 * 16, 3 * 16)), flying, hotbar


def make():
    arrow, flying, hotbar = beta()
    return with_head(arrow, ITEM_AT, ITEM_HEAD), with_head(flying, FLYING_AT, FLYING_HEAD)


def write(item, flying):
    item.save(os.path.join(ASSETS, "item", "explosive_arrow.png"))
    flying.save(os.path.join(ASSETS, "entity", "explosive_arrow.png"))


def big(sprite, k):
    return sprite.resize((sprite.size[0] * k, sprite.size[1] * k), Image.NEAREST)


def in_slots(sprites, hotbar=None, world=None):
    """The sprites in a row of slots: the inventory's grey, or beta's hotbar over a patch of world."""
    w = 2 + len(sprites) * 20
    out = Image.new("RGBA", (w, 22), INVENTORY_GREY if hotbar is None else world + (255,))
    if hotbar is not None:
        out.alpha_composite(hotbar.crop((0, 0, w, 22)))
    for i, s in enumerate(sprites):
        out.alpha_composite(s, (3 + i * 20, 3))
    return out


def sheet(path, rows):
    """Each row a label, an item sprite and a flying texture: enlarged, the item in slots (inventory grey, the hotbar by
    day and at night, twice its size and its own) beside beta's arrow, and the flying texture's used part."""
    from PIL import ImageDraw, ImageFont
    arrow, beta_flying, hotbar = beta()
    font = ImageFont.truetype("arial.ttf", 15)
    k = 10
    line = 16 * k + 14
    out = Image.new("RGBA", (630, 8 + len(rows) * line), INVENTORY_GREY)
    d = ImageDraw.Draw(out)
    for j, (label, item, flying) in enumerate(rows):
        y = 8 + j * line
        d.text((8, y + 70), label, font=font, fill=(0x18, 0x18, 0x18, 255))
        out.alpha_composite(big(item, k), (110, y))
        x = 110 + 16 * k + 16
        pair = [arrow, item] if item is not arrow else [arrow]
        for kk, yy in ((2, y + 8), (1, y + 64)):
            xx = x
            for strip in (in_slots(pair), in_slots(pair, hotbar, (0x6a, 0x9a, 0x40)), in_slots(pair, hotbar, (0x2a, 0x2a, 0x30))):
                out.alpha_composite(big(strip, kk), (xx, yy))
                xx += strip.size[0] * kk + 8
        # the side view (16 by 5) and the end-on cross (5 by 5), as the game maps them onto the arrow's quads
        out.alpha_composite(big(flying.crop((0, 0, 16, 5)), k), (x, y + 100))
        out.alpha_composite(big(flying.crop((0, 5, 5, 10)), k), (x + 16 * k + 12, y + 100))
        out.alpha_composite(big(flying.crop((0, 0, 16, 5)), 3), (x + 21 * k + 30, y + 100))
        out.alpha_composite(flying.crop((0, 0, 16, 5)), (x + 21 * k + 90, y + 105))
    out.save(path)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--sheet", help="a file for the sheet")
    p.add_argument("--dry", action="store_true", help="write nothing into the mod")
    a = p.parse_args()
    item, flying = make()
    if a.sheet:
        arrow, beta_flying, hotbar = beta()
        was = (Image.open(os.path.join(ASSETS, "item", "explosive_arrow.png")).convert("RGBA"),
               Image.open(os.path.join(ASSETS, "entity", "explosive_arrow.png")).convert("RGBA"))
        os.makedirs(os.path.dirname(os.path.abspath(a.sheet)), exist_ok=True)
        sheet(a.sheet, [("beta's arrow", arrow, beta_flying), ("in the mod", was[0], was[1]), ("drawn here", item, flying)])
    if not a.dry:
        write(item, flying)


if __name__ == "__main__":
    main()
