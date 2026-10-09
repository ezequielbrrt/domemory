#!/usr/bin/env python3
"""Build the single-file Google Ads playable from template.html.

Inlines Flippo's win pose as a WebP data URI so the output has no external
resources other than Google's exit API. Run from anywhere:

    python3 scripts/playable/build.py
"""
import base64, io, pathlib, re, sys
from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent.parent
FLIPPO = ROOT / "assets/images/flippo/flippo-win.png"
OUT = HERE / "domemory-playable.html"

im = Image.open(FLIPPO).convert("RGBA")
im = im.crop(im.getbbox())
im.thumbnail((360, 380))
buf = io.BytesIO()
im.save(buf, "WEBP", quality=82, method=6)
uri = "data:image/webp;base64," + base64.b64encode(buf.getvalue()).decode()

html = (HERE / "template.html").read_text(encoding="utf-8").replace("{{FLIPPO_WIN}}", uri)
OUT.write_text(html, encoding="utf-8")

# Sanity checks Google's HTML5 validator also enforces.
refs = [m for m in re.findall(r"https?://[^\s\"')]+", html) if "tpc.googlesyndication.com" not in m]
size = OUT.stat().st_size
print(f"wrote {OUT.relative_to(ROOT)} ({size/1024:.0f} KB)")
if refs:
    print("external references found:", refs); sys.exit(1)
if size > 5 * 1024 * 1024:
    print("over 5 MB"); sys.exit(1)
if "ExitApi.exit" not in html:
    print("missing ExitApi.exit"); sys.exit(1)
print("ok: no external references, exit API wired, under 5 MB")
