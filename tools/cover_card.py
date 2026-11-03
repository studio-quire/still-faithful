"""A cover card for the site: a frame from `:harness:sceneShot*`, scaled to 1120x700, with an edition's seal over it
as the site's Faithful cards have it (300 px, pixel-sharp, a soft shadow). Writes the card and the frame
as webp for the covers workshop (prism/release/icons/covers/).

    python tools/cover_card.py <frame.png> <seal.png> <name> [--at centre|left|right]

Claude Opus 5.5, 2026-11-03.
"""
import argparse
from pathlib import Path

from PIL import Image, ImageFilter

COVERS = Path(__file__).resolve().parent.parent / "prism/release/icons/covers"
W, H = 1120, 700
SEAL = 300  # the 512 seal drawn 300 across, as on Old Faithful's card (fitted to it: off by under 3 levels a channel)
CENTRES = {"centre": (560, 350), "left": (270, 352), "right": (860, 350)}


def card(frame: Image.Image, seal: Image.Image, at: str) -> Image.Image:
    img = frame.convert("RGB").resize((W, H), Image.LANCZOS)
    seal = seal.convert("RGBA").resize((SEAL, SEAL), Image.NEAREST)
    cx, cy = CENTRES[at]
    left, top = cx - SEAL // 2, cy - SEAL // 2
    # Shadow: the seal's shape blurred, darkening what's under it by up to about a quarter.
    alpha = Image.new("L", (W, H), 0)
    alpha.paste(seal.getchannel("A"), (left, top))
    shadow = alpha.filter(ImageFilter.GaussianBlur(24)).point(lambda v: int(v * 0.24))
    img = Image.composite(Image.new("RGB", (W, H), (0, 0, 0)), img, shadow)
    img.paste(seal, (left, top), seal)
    return img


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("frame")
    p.add_argument("seal")
    p.add_argument("name")
    p.add_argument("--at", choices=CENTRES, default="centre")
    a = p.parse_args()
    frame = Image.open(a.frame)
    card(frame, Image.open(a.seal), a.at).save(COVERS / "card" / f"{a.name}.webp", quality=88)
    frame.convert("RGB").save(COVERS / "frames" / f"{a.name}.webp", quality=90)


if __name__ == "__main__":
    main()
