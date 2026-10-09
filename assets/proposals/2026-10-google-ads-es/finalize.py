#!/usr/bin/env python3
"""Sizes the Spanish Google Ads images to Google's specs and builds the logos.

- portrait/  1200 x 1500 (4:5), from the five approved 1122 x 1402 portraits
- square/    1200 x 1200 (1:1), from Codex's 1:1 outputs
- landscape/ 1200 x 628 (1.91:1), from Codex's 16:9 outputs, centre-cropped
- logo/      1200 x 1200 (icon on brand purple) and 1200 x 300 (icon + wordmark)

Every output is an RGB PNG without alpha, under Google's 5 MB limit.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path("/Users/ezequielbrrt/Documents/personal/development/apps/domemory")
SRC_PORTRAIT = ROOT / "assets/google-ads-es"
RAW = ROOT / ".proposals/google-ads-es/raw"
OUT = ROOT / "assets/google-ads-es"
ICON = ROOT / "assets/my-video/public/assets/domemory-app-icon.png"
FONT = ROOT / "assets/my-video/public/assets/fonts/Righteous-Regular.ttf"
PURPLE = (75, 63, 201)

NAMES = {
    "01": "01-voltea-empareja-gana",
    "02": "02-tu-baraja-tus-reglas",
    "03": "03-reta-a-un-amigo",
    "04": "04-sube-al-siguiente-nivel",
    "05": "05-un-tema-nuevo",
}


def fit_cover(img: Image.Image, w: int, h: int) -> Image.Image:
    """Scale to cover w x h, then centre-crop."""
    img = img.convert("RGB")
    scale = max(w / img.width, h / img.height)
    resized = img.resize((round(img.width * scale), round(img.height * scale)), Image.LANCZOS)
    left = (resized.width - w) // 2
    top = (resized.height - h) // 2
    return resized.crop((left, top, left + w, top + h))


def save(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(path, "PNG", optimize=True)
    size = path.stat().st_size
    assert size < 5 * 1024 * 1024, f"{path} is {size} bytes"
    print(f"{path.relative_to(ROOT)}  {img.width}x{img.height}  {size // 1024} KB")


def main() -> None:
    for nn, name in NAMES.items():
        save(fit_cover(Image.open(SRC_PORTRAIT / f"{name}.png"), 1200, 1500), OUT / "portrait" / f"{name}.png")
        square = RAW / f"{nn}-square.png"
        landscape = RAW / f"{nn}-landscape.png"
        if square.exists():
            save(fit_cover(Image.open(square), 1200, 1200), OUT / "square" / f"{name}.png")
        else:
            print(f"MISSING {square}")
        if landscape.exists():
            save(fit_cover(Image.open(landscape), 1200, 628), OUT / "landscape" / f"{name}.png")
        else:
            print(f"MISSING {landscape}")

    icon = Image.open(ICON).convert("RGBA")
    # The icon file is a full square; round it like a home-screen icon so it
    # doesn't sit as a hard-edged block on the purple.
    mask = Image.new("L", icon.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, icon.width - 1, icon.height - 1), radius=round(icon.width * 0.225), fill=255)
    icon.putalpha(mask)

    # Square logo: the icon on the brand purple, with a margin so Google's
    # round crops never touch it.
    logo = Image.new("RGB", (1200, 1200), PURPLE)
    big = icon.resize((960, 960), Image.LANCZOS)
    logo.paste(big, (120, 120), big)
    save(logo, OUT / "logo" / "logo-1200x1200.png")

    # Landscape logo: icon and the DoMemory wordmark in Righteous, centred.
    wide = Image.new("RGB", (1200, 300), PURPLE)
    small = icon.resize((220, 220), Image.LANCZOS)
    font = ImageFont.truetype(str(FONT), 150)
    draw = ImageDraw.Draw(wide)
    text = "DoMemory"
    bbox = draw.textbbox((0, 0), text, font=font)
    text_w, text_h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    gap = 40
    total = 220 + gap + text_w
    x0 = (1200 - total) // 2
    wide.paste(small, (x0, 40), small)
    draw.text((x0 + 220 + gap - bbox[0], (300 - text_h) // 2 - bbox[1]), text, font=font, fill=(255, 255, 255))
    save(wide, OUT / "logo" / "logo-1200x300.png")


if __name__ == "__main__":
    main()
