"""Fit the selected Codex generations to Apple's exact creative-asset canvases.

Pipeline per asset: raw Codex PNG -> Real-ESRGAN x4 (realesrgan-x4plus-anime,
run separately) -> cover-fit to the exact canvas (Lanczos, centre crop of at
most a few pixels) -> flatten to RGB (Apple rejects alpha) -> validate.

Usage: python3 scripts/store-art/finalize.py <upscaled-dir> A-universal=A-universal-2 ...
"""
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
# Shipped finals live in assets/images/store/creative-assets/; candidates that were
# not uploaded stay in assets/proposals/2026-10-app-store-creative-assets/final/.
FINAL = REPO / "assets/images/store/creative-assets"

# name of the selected raw generation -> (output file, width, height)
ASSETS = {
    "A-universal": ("A-universal-header-search-5244x2950.png", 5244, 2950),
    "B-search": ("B-search-results-3840x2560.png", 3840, 2560),
    "C-header-spooky": ("C-header-spooky-3840x1646.png", 3840, 1646),
    "D1-event-card": ("D1-event-card-spooky-3840x2160.jpg", 3840, 2160),
    "D2-event-details": ("D2-event-details-spooky-2160x3840.jpg", 2160, 3840),
    # Dedicated header and search art (second round)
    "H2-header": ("H2-header-constellations-3840x1646.png", 3840, 1646),
    "H1-header": ("H1-header-stage-3840x1646.png", 3840, 1646),
    "S1-search": ("S1-search-match-3840x2560.png", 3840, 2560),
    "S2-search": ("S2-search-friends-3840x2560.png", 3840, 2560),
}
# Move the content down by this fraction of the height to land Flippo inside
# Apple's safe area. The vacated top strip is filled by mirroring the image's
# own top edge (plain starry indigo there), and the same strip leaves at the
# bottom (plain indigo below the lowest cards).
SHIFT_DOWN = {"A-universal": 0.03}


def shift_down(im, frac):
    strip = round(im.height * frac)
    top = im.crop((0, 0, im.width, strip)).transpose(Image.FLIP_TOP_BOTTOM)
    out = Image.new(im.mode, im.size)
    out.paste(top, (0, 0))
    out.paste(im.crop((0, 0, im.width, im.height - strip)), (0, strip))
    return out


def fit(im, w, h):
    scale = max(w / im.width, h / im.height)
    rw, rh = round(im.width * scale), round(im.height * scale)
    im = im.resize((rw, rh), Image.LANCZOS)
    left, top = (rw - w) // 2, (rh - h) // 2
    return im.crop((left, top, left + w, top + h))


def main():
    up = Path(sys.argv[1])
    picks = dict(a.split("=", 1) for a in sys.argv[2:])
    FINAL.mkdir(exist_ok=True)
    for name, stem in picks.items():
        out, w, h = ASSETS[name]
        im = Image.open(up / f"{stem}-x4.png").convert("RGB")
        if name in SHIFT_DOWN:
            im = shift_down(im, SHIFT_DOWN[name])
        im = fit(im, w, h)
        path = FINAL / out
        if out.endswith(".png"):
            im.save(path, optimize=True)
        else:
            im.save(path, quality=95, subsampling=0)
        check = Image.open(path)
        assert check.size == (w, h), (out, check.size)
        assert check.mode == "RGB", (out, check.mode)
        print(f"{out}: {check.size[0]}x{check.size[1]} {check.mode} {path.stat().st_size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
