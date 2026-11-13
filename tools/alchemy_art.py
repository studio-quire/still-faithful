"""Alchemy's sprites, made from beta's own and the library's: the gilded things, the potions and the timed potions'
bottles draining in the inventory.

    python tools/alchemy_art.py [--sheet DIR]

Writes the PNGs into library/alchemy's assets. The four gilded things are held here pixel by pixel, over the outlines
of beta's rose, dandelion and feather and Blue Flowers' lupin (GILDED); the bottle is library/water-bottles'. Beta's
own sprites are read from the game jar in local/cache only for the sheet. --sheet DIR writes an enlarged contact sheet
there, with the HUD's pieces, the draining bottles and the gilded things in beta's hotbar, for looking at.
Claude Opus 5.5, 2026-11-09; the gilded things and haste's colours by Claude Fable 5.2 the same day.
"""
import argparse
import io
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, "local", "cache", "minecraft", "b1.7.3-client.jar")
LIBRARY = os.path.join(ROOT, "library")
ASSETS = os.path.join(LIBRARY, "alchemy", "src", "main", "resources", "assets", "alchemy", "textures")
BLUEBELL = os.path.join(LIBRARY, "blue-flowers", "src", "main", "resources", "assets", "blue-flowers", "textures", "block", "blue_flower.png")
BOTTLE = os.path.join(LIBRARY, "water-bottles", "src", "main", "resources", "assets", "water-bottles", "textures", "item", "water_bottle.png")
GLASS = os.path.join(LIBRARY, "water-bottles", "src", "main", "resources", "assets", "water-bottles", "textures", "item", "glass_bottle.png")
INVENTORY_GREY = (0x8b, 0x8b, 0x8b, 255)

# The gilded things, pixel by pixel, each over its own outline (beta's rose, dandelion and feather, Blue Flowers'
# lupin), all gold and one metal (Claude Fable 5.2, 2026-11-09): pale and bright gold in the light, the stems' gold
# in the middle, and for the dark the orange of beta's gold ingot, with the ingot's white glint on the feather only.
# The user's "let's try" after two rounds that kept a trace of each flower's colour: nothing here is made to tell the
# four apart or from their plain flowers, since their outlines and the gold do that (their word: we overrate how much
# help people need to tell things apart). The feather was a pale cream and is now the flowers' gold, with their darks.
# Of those two rounds the user liked the first pass's rose and dandelion (Claude Opus 5.5), and preferred none of the
# second's six bluebells and redrawn rose and dandelion (Fable 5.2).
INKS = {
    "W": "ffffff", "P": "fff697", "h": "ffef6d", "y": "fad42c",  # the glint, then pale to bright gold
    "s": "e3ad15", "u": "b77d0b",  # the stems' gold, and the rose's thorns
    "d": "b76110", "t": "885607",  # the ingot's dark orange, and the stems' shade
}


def from_row(top, *rows):
    return [""] * top + list(rows)


GILDED = {
    "golden_rose": from_row(5, ".......Ph", "......Pdysy", ".....yyyhd", "......hsddy", "......yst",
                            ".......u", "......us", ".......ts", "........su", ".......ut", "........t"),
    "golden_dandelion": from_row(8, ".....yh.Psh", "......yyhd", ".....hysdds", ".......ss",
                                 ".......stst", ".....tsstt", "......tst", ".......st"),
    # Blue Flowers' flower is a lupin since 2026-11-12 (tools/lupin_art.py); the file and id keep the bluebell's name.
    "golden_bluebell": from_row(3, ".......P", ".......y", "......hys", "......ydh", ".....hyydy", "......ydhd", ".....yhydy", "......yds",
                                ".......s", "....s..s..t", ".....sssst", ".......t", ".......t"),
    "golden_feather": from_row(2, "............y", "...........hs", "..........hsy", ".........Wsys", ".......sshsys", "......hWysyyd",
                               ".....ssysysd", "....yhysyys", "......dysyd", ".....dtysd", "...ddt.yd", "...tt..d"),
}

# Each family's liquid, weak to strong, as the health potions have it: the surface's highlight, then light to dark.
# Health's are the first round's (Claude Opus 5.5, 2026-11-06), kept as they are.
# Haste's were butter, yellow and amber; amber and brownish yellow in a bottle look like something unfortunate (the
# user, 2026-11-09, who asked for the dandelion's own colours). So (Fable 5.2): a pale lemon, the dandelion's lemon
# with its own shades, and for the strong one a sharper lemon leaning to lime, since yellow only gets darker by going
# olive or brown, and the dandelion's olive as a whole bottle looked murky.
LIQUIDS = {
    "health": [("ffc2c8", "f47c8c", "e05468", "b43650"), ("ff8074", "e82026", "be1020", "860a1c"), ("b81c38", "7c0a24", "5c061c", "380412")],
    "haste": [("ffffd8", "f9fb98", "eaee6c", "c8cc48"), ("ffff90", "f1f902", "d6dc02", "b0b601"), ("f4ff30", "dcf000", "b4cc00", "849800")],
    # Cerulean, the bluebell's side of blue: water's own is the periwinkle the bottle is filled with.
    "ward": [("e4faff", "b4eeff", "84d8fc", "54b4ec"), ("88e4ff", "20b4f8", "0890e0", "0866b0"), ("2098d8", "0a68ac", "064a86", "032a50")],
    "feather": [("f8f4ff", "e8e0fc", "d4c8f0", "b4a4dc"), ("ece4ff", "c4b0f8", "a088e8", "7860c4"), ("b49ce8", "8464d4", "6244b0", "3e2878")],
}
# The timed potions in Timed.all()'s order, a row each of gui/timers.png, and the steps their bottle drains by: its
# liquid's six rows, two minutes each of the longest potion's twelve.
TIMED = ("haste", "feather")
LEVELS = 6
WATER = ("96bef4", "5c8aec", "3a68de", "2a4cb4")
SIZES = ("weak_", "", "strong_")


def rgb(hex):
    return tuple(int(hex[i:i + 2], 16) for i in (0, 2, 4))


def drawn(rows):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = rgb(INKS[ch]) + (255,)
    return img


def potion(bottle, shades):
    """The water bottle with its water's four shades swapped for another liquid's."""
    swap = {rgb(w): rgb(s) for w, s in zip(WATER, shades)}
    out = bottle.copy()
    px = out.load()
    for y in range(16):
        for x in range(16):
            c = px[x, y]
            if c[3] and c[:3] in swap:
                px[x, y] = swap[c[:3]] + (255,)
    return out


def drained(full, empty, left):
    """A timed potion's bottle in the inventory (Claude Fable 5.2's prototype D, the user's pick, 2026-11-09): the plain
    potion's own sprite with its liquid down to {@code left} of its rows, the top one left keeping the surface's pale line."""
    rows = sorted({y for y in range(16) for x in range(16) if full.getpixel((x, y)) != empty.getpixel((x, y))})
    surface = full.getpixel((6, rows[0]))
    out = full.copy()
    top = rows[len(rows) - left] if left > 0 else 99
    for y in rows:
        for x in range(16):
            if full.getpixel((x, y)) == empty.getpixel((x, y)):
                continue
            if y < top:
                out.putpixel((x, y), empty.getpixel((x, y)))
            elif y == top and y != rows[-1]:
                out.putpixel((x, y), surface)
    return out


def beta_sprites():
    jar = zipfile.ZipFile(JAR)
    terrain = Image.open(io.BytesIO(jar.read("terrain.png"))).convert("RGBA")
    items = Image.open(io.BytesIO(jar.read("gui/items.png"))).convert("RGBA")
    icons = Image.open(io.BytesIO(jar.read("gui/icons.png"))).convert("RGBA")
    hotbar = Image.open(io.BytesIO(jar.read("gui/gui.png"))).convert("RGBA").crop((0, 0, 182, 22))

    def tile(img, i):
        x, y = i % 16, i // 16
        return img.crop((x * 16, y * 16, x * 16 + 16, y * 16 + 16))

    return tile(terrain, 12), tile(terrain, 13), tile(items, 1 * 16 + 8), icons, hotbar


def make():
    rose, dandelion, feather, icons, hotbar = beta_sprites()
    bluebell = Image.open(BLUEBELL).convert("RGBA")
    bottle = Image.open(BOTTLE).convert("RGBA")
    made = {name: drawn(rows) for name, rows in GILDED.items()}
    for family, sizes in LIQUIDS.items():
        if family == "health":
            continue
        for size, shades in zip(SIZES, sizes):
            made[size + family + "_potion"] = potion(bottle, shades)
    # A row a timed potion at the top left of a 256 square, as beta's GUI sheets are drawn: full, then a row of
    # liquid less each, to one.
    empty = Image.open(GLASS).convert("RGBA")
    gui = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    for row, family in enumerate(TIMED):
        for step in range(LEVELS):
            gui.paste(drained(made[family + "_potion"], empty, LEVELS - step), (step * 16, row * 16))
    return made, gui, (rose, dandelion, bluebell, feather, bottle, icons, hotbar)


def write(made, gui):
    for name, img in made.items():
        img.save(os.path.join(ASSETS, "item", name + ".png"))
    os.makedirs(os.path.join(ASSETS, "gui"), exist_ok=True)
    gui.save(os.path.join(ASSETS, "gui", "timers.png"))


# EffectsHud's tint over beta's grey armour sprite, for the sheet.
WARD_TINT = (0.45, 0.88, 1.0)


def tinted(img, tint):
    out = img.copy()
    px = out.load()
    for y in range(out.size[1]):
        for x in range(out.size[0]):
            c = px[x, y]
            if c[3]:
                px[x, y] = tuple(round(c[i] * tint[i]) for i in range(3)) + (255,)
    return out


def hud_cells(gui, icons):
    """Each timed potion's bottle draining, then the HUD's armour pips, padded to 16 so they sit in the sheet's grid:
    beta's, the blue one, and the half of each."""
    bottles = [gui.crop((step * 16, row * 16, step * 16 + 16, row * 16 + 16)) for row in range(len(TIMED)) for step in range(LEVELS)]
    cells = []
    full_pip, half_pip, empty_pip = icons.crop((34, 9, 43, 18)), icons.crop((25, 9, 34, 18)), icons.crop((16, 9, 25, 18))
    blue_half = half_pip.copy()
    blue_half.alpha_composite(tinted(full_pip.crop((5, 0, 9, 9)), WARD_TINT), (5, 0))
    mixed = half_pip.copy()
    mixed.alpha_composite(tinted(full_pip.crop((0, 0, 4, 9)), WARD_TINT), (0, 0))
    cells += [full_pip, tinted(full_pip, WARD_TINT), half_pip, blue_half, mixed, empty_pip]
    padded = []
    for c in cells:
        p = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        p.alpha_composite(c, (3, 3))
        padded.append(p)
    return bottles + padded


def in_hotbar(hotbar, sprites, world=(0x6a, 0x9a, 0x40)):
    """Up to nine sprites in beta's hotbar over a patch of world: its slots are much darker than the inventory's, and
    it is where a player tells one item from the next at a glance."""
    out = Image.new("RGBA", hotbar.size, world + (255,))
    out.alpha_composite(hotbar)
    for i, s in enumerate(sprites[:9]):
        out.alpha_composite(s, (3 + i * 20, 3))
    return out


def contact_sheet(made, gui, sources, path, k=8):
    """Beta's rose, dandelion, bluebell and feather beside their golden ones; the water bottle and every potion; the HUD's
    pieces. Enlarged, then each at its own size and twice it, as an inventory shows them, on beta's inventory grey; then
    the first row in the hotbar, at twice its size and its own."""
    rose, dandelion, bluebell, feather, bottle, icons, hotbar = sources

    def item(name):
        return Image.open(os.path.join(ASSETS, "item", name + ".png")).convert("RGBA") if name not in made else made[name]

    hud = hud_cells(gui, icons)
    rows = [
        [rose, made["golden_rose"], dandelion, made["golden_dandelion"], bluebell, made["golden_bluebell"], feather, made["golden_feather"]],
        [bottle] + [item(s + "health_potion") for s in SIZES] + [item(s + "haste_potion") for s in SIZES],
        [None] + [item(s + "ward_potion") for s in SIZES] + [item(s + "feather_potion") for s in SIZES],
        hud[:LEVELS],
        hud[LEVELS:2 * LEVELS],
        hud[2 * LEVELS:],
    ]
    cell = 16 * k + 8
    width = max(len(r) for r in rows)
    out = Image.new("RGBA", (width * cell + 8, len(rows) * cell + 8 + 60 + 52), INVENTORY_GREY)
    for j, row in enumerate(rows):
        for i, s in enumerate(row):
            if s is not None:
                out.alpha_composite(s.resize((16 * k, 16 * k), Image.NEAREST), (8 + i * cell, 8 + j * cell))
    y = 8 + len(rows) * cell
    x = 8
    for row in rows[:3]:
        for s in row:
            if s is not None:
                out.alpha_composite(s, (x, y))
                out.alpha_composite(s.resize((32, 32), Image.NEAREST), (x, y + 20))
                x += 36
        x += 12
    bar = in_hotbar(hotbar, rows[0])
    out.alpha_composite(bar.resize((364, 44), Image.NEAREST), (8, y + 60))
    out.alpha_composite(bar, (8 + 364 + 12, y + 60 + 11))
    out.save(path)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--sheet", help="a folder for the contact sheet")
    p.add_argument("--dry", action="store_true", help="write nothing into the mod")
    a = p.parse_args()
    made, gui, sources = make()
    if not a.dry:
        write(made, gui)
    if a.sheet:
        os.makedirs(a.sheet, exist_ok=True)
        contact_sheet(made, gui, sources, os.path.join(a.sheet, "sprites.png"))


if __name__ == "__main__":
    main()
