"""Blue Flowers' lupin and its dye, Lupin Blue, drawn pixel by pixel.

    python tools/lupin_art.py [--sheet FILE]

Writes library/blue-flowers' textures/block/blue_flower.png (the id keeps its first name) and textures/item/lupin_blue.png.
--sheet FILE also writes an enlarged sheet of both beside beta's rose, dandelion and a few of its dyes, read from
the game jar in local/cache for the sheet only. Alchemy's golden lupin is drawn over this outline in tools/alchemy_art.py.

Both are the user's picks (2026-11-12). The lupin is the first sketch made of one, kept through four rounds of other candidates
(not kept here: five shapes in six shadings; spires scattered with single light and dark pixels, which they found speckled;
spires shaded as beta shades its own plants), with three changes they asked for: its top right dark pixel less dark, its stem
and leaves lit from the left where the sketch alternated light and dark along them, and its lightest blues a quarter of the way
to its middle blue. The dye is a pile in the family of beta's, its inside scattered as beta's Cyan is.
What beta does, for whoever draws beside it next, read off its sprites: its tones sit in clusters, not scattered (the mushrooms
lit from the upper left in bands, the dandelion with light tips and one dark clump, the rose with a dark heart, sugar cane in
two tones); a flower's tones are one hue at about 100, 85, 75 and 59% of its lightest; its heads are small (the rose's 18
pixels, the dandelion's 15); and its dyes are about 12 by 11 with an outline one pixel wide in their darkest colour and a flat
foot, each dye that comes straight from a thing with an outline of its own, the inside four to six tones scattered pixel by
pixel and a little lighter up and left.
Claude Fable 5.2, 2026-11-12.
"""
import argparse
import io
import os
import zipfile

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, "local", "cache", "minecraft", "b1.7.3-client.jar")
TEXTURES = os.path.join(ROOT, "library", "blue-flowers", "src", "main", "resources", "assets", "blue-flowers", "textures")

# l, b and B the head's three blues, T its top right dark pixel; g and G beta's two stem greens.
INKS = {"l": "637aea", "b": "3f58d8", "B": "2638a8", "T": "3349c2", "g": "168700", "G": "106300"}
LUPIN_TOP = 3
LUPIN = [
    ".......l........",
    ".......b........",
    "......lbT.......",
    "......bBl.......",
    ".....lbbBb......",
    "......bBlB......",
    ".....blbBb......",
    "......bBB.......",
    ".......g........",
    "....g..g..G.....",
    ".....gggGG......",
    ".......G........",
    ".......G........",
]

# o the outline, x the inside.
DYE_TOP = 3
DYE = [
    "....ooooooo.....",
    "...oxxxxxxxo....",
    "..oxxxxxxxxxo...",
    "..oxxxxxxxxxxo..",
    ".oxxxxxxxxxxxo..",
    ".oxxxxxxxxxxxo..",
    ".oxxxxxxxxxxxo..",
    "..oxxxxxxxxxo...",
    "...ooxxxxxoo....",
    ".....ooooo......",
]
DYE_OUTLINE = "0d2166"
DYE_TONES = ["5687ea", "4474de", "3360cf", "274fbe", "1f44ad", "1a3a9a"]   # light to dark, as far apart as Cyan's six


def rgba(h):
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def lupin():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(LUPIN, start=LUPIN_TOP):
        for x, ch in enumerate(row):
            if ch != ".":
                im.putpixel((x, y), rgba(INKS[ch]))
    return im


def scatter(x, y, salt):
    """A fixed scatter, 0 to 1: the same picture every run."""
    n = (x * 374761393 + y * 668265263 + salt * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def dye():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(DYE, start=DYE_TOP):
        for x, ch in enumerate(row):
            if ch == "o":
                im.putpixel((x, y), rgba(DYE_OUTLINE))
            elif ch == "x":
                lean = 2.5 + 0.2 * (x - 7) + 0.24 * (y - 7)
                t = int(round(lean + (scatter(x, y, 1) - 0.5) * 4.0))
                im.putpixel((x, y), rgba(DYE_TONES[max(0, min(len(DYE_TONES) - 1, t))]))
    return im


def tile(img, i):
    x, y = (i % 16) * 16, (i // 16) * 16
    return img.crop((x, y, x + 16, y + 16))


def sheet(path, scale=10):
    jar = zipfile.ZipFile(JAR)
    terrain = Image.open(io.BytesIO(jar.read("terrain.png"))).convert("RGBA")
    items = Image.open(io.BytesIO(jar.read("gui/items.png"))).convert("RGBA")
    try:
        f = ImageFont.truetype("segoeui.ttf", 15)
    except OSError:
        f = ImageFont.load_default()
    grass, slot = (110, 168, 84), (0x8b, 0x8b, 0x8b)
    cells = [("beta's rose", tile(terrain, 12), grass), ("Lupin", lupin(), grass), ("beta's dandelion", tile(terrain, 13), grass),
             ("Rose Red", tile(items, 94), slot), ("Lupin Blue", dye(), slot), ("Dandelion Yellow", tile(items, 127), slot), ("Cyan", tile(items, 174), slot)]
    cell, pad = 16 * scale, 8
    out = Image.new("RGB", (pad + len(cells) * (cell + pad), cell + 44), (38, 40, 46))
    d = ImageDraw.Draw(out)
    for i, (name, im, back) in enumerate(cells):
        x = pad + i * (cell + pad)
        d.rectangle([x, 8, x + cell - 1, 8 + cell - 1], fill=back)
        big = im.resize((cell, cell), Image.NEAREST)
        out.paste(big, (x, 8), big)
        d.text((x, cell + 14), name, fill=(232, 232, 236), font=f)
    out.save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sheet")
    a = ap.parse_args()
    lupin().save(os.path.join(TEXTURES, "block", "blue_flower.png"))
    dye().save(os.path.join(TEXTURES, "item", "lupin_blue.png"))
    if a.sheet:
        sheet(a.sheet)


if __name__ == "__main__":
    main()
