"""Layout blueprints for the App Store creative-asset proposal.

Not final art: flat shapes, real Flippo sprites and real emoji, placed on the
exact canvases Apple specifies, with the art safe areas measured from Apple's
own Photoshop templates drawn on the annotated copies.
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

REPO = Path("/Users/ezequielbrrt/Documents/personal/development/apps/domemory")
OUT = REPO / ".proposals/app-store-creative-assets/blueprints"
OUT.mkdir(parents=True, exist_ok=True)
FLIPPO = REPO / "assets/images/flippo"
NUNITO = str(REPO / "android/app/src/main/res/font/nunito.ttf")
EMOJI = "/System/Library/Fonts/Apple Color Emoji.ttc"

INDIGO = (75, 63, 201)
INDIGO_DARK = (40, 31, 128)
INDIGO_LIGHT = (112, 98, 240)
CARD_BACK = (58, 46, 176)
CREAM = (255, 248, 236)
GOLD = (251, 197, 74)
SPOOKY = (255, 107, 26)
NIGHT = (34, 18, 64)

# Art safe areas, read from the 'Art Safe Area' layer of Apple's templates
# (creative_assets-*_template-static.psd, downloaded 2026-10-06).
SAFE = {
    "universal": (1921, 660, 3323, 1622),  # 5244 x 2950
    "header": (1097, 493, 2743, 1154),     # 3840 x 1646
    "search": (836, 765, 3004, 1795),      # 3840 x 2560
}

_emoji_cache = {}


def emoji(ch, size):
    key = (ch, size)
    if key not in _emoji_cache:
        font = ImageFont.truetype(EMOJI, 160)
        im = Image.new("RGBA", (220, 220), (0, 0, 0, 0))
        ImageDraw.Draw(im).text((110, 110), ch, font=font, embedded_color=True, anchor="mm")
        im = im.crop(im.getbbox())
        _emoji_cache[key] = im.resize((size, int(size * im.height / im.width)), Image.LANCZOS)
    return _emoji_cache[key]


def font(size, weight=b"Black"):
    f = ImageFont.truetype(NUNITO, size)
    f.set_variation_by_name(weight)
    return f


def background(w, h, center, edge, glow_at=(0.5, 0.45)):
    """Radial gradient, computed small and upscaled (smooth and fast)."""
    sw, sh = 320, max(1, int(320 * h / w))
    small = Image.new("RGB", (sw, sh))
    px = small.load()
    cx, cy = glow_at[0] * sw, glow_at[1] * sh
    rmax = math.hypot(max(cx, sw - cx), max(cy, sh - cy))
    for y in range(sh):
        for x in range(sw):
            t = min(1.0, math.hypot(x - cx, y - cy) / rmax) ** 1.3
            px[x, y] = tuple(int(center[i] + (edge[i] - center[i]) * t) for i in range(3))
    return small.resize((w, h), Image.BICUBIC).convert("RGBA")


def sparkles(im, n, seed, color=(255, 255, 255), scale=1.0):
    rnd = random.Random(seed)
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    w, h = im.size
    for _ in range(n):
        x, y = rnd.uniform(0, w), rnd.uniform(0, h)
        r = rnd.uniform(10, 34) * scale
        a = rnd.randint(90, 220)
        d.polygon([(x, y - r), (x + r * 0.22, y - r * 0.22), (x + r, y), (x + r * 0.22, y + r * 0.22),
                   (x, y + r), (x - r * 0.22, y + r * 0.22), (x - r, y), (x - r * 0.22, y - r * 0.22)],
                  fill=color + (a,))
    im.alpha_composite(layer)


def card(w, face=None, back=CARD_BACK, front=CREAM, mark=(150, 140, 255)):
    """A memory card: face-down shows a '?', face-up shows an emoji."""
    h = int(w * 1.32)
    pad = int(w * 0.12)
    im = Image.new("RGBA", (w + pad * 2, h + pad * 2), (0, 0, 0, 0))
    shadow = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((pad, pad + w * 0.05, pad + w, pad + h + w * 0.05),
                                             radius=w * 0.16, fill=(10, 5, 50, 110))
    im.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(w * 0.05)))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((pad, pad, pad + w, pad + h), radius=w * 0.16, fill=front if face else back)
    if face:
        e = emoji(face, int(w * 0.62))
        im.alpha_composite(e, (pad + (w - e.width) // 2, pad + (h - e.height) // 2))
    else:
        d.text((pad + w / 2, pad + h / 2), "?", font=font(int(w * 0.7)), fill=mark, anchor="mm")
    return im


def place(base, sprite, cx, cy, angle=0):
    if angle:
        sprite = sprite.rotate(angle, resample=Image.BICUBIC, expand=True)
    base.alpha_composite(sprite, (int(cx - sprite.width / 2), int(cy - sprite.height / 2)))


def flippo(pose, height):
    im = Image.open(FLIPPO / f"flippo-{pose}.png").convert("RGBA")
    im = im.crop(im.getbbox())
    return im.resize((int(im.width * height / im.height), height), Image.LANCZOS)


def glow(base, cx, cy, r, color, alpha=150):
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse((cx - r, cy - r, cx + r, cy + r), fill=color + (alpha,))
    base.alpha_composite(layer.filter(ImageFilter.GaussianBlur(r * 0.45)))


def annotate(im, box, label):
    a = im.copy()
    d = ImageDraw.Draw(a)
    lw = max(6, im.width // 500)
    d.rectangle(box, outline=(0, 255, 90, 255), width=lw)
    f = font(max(36, im.width // 70), b"Bold")
    tb = d.textbbox((box[0], box[3]), label, font=f, anchor="lb")
    d.rectangle((tb[0] - 10, tb[1] - 10, tb[2] + 10, tb[3] + 10), fill=(0, 255, 90, 255))
    d.text((box[0], box[3]), label, font=f, fill=(0, 0, 0), anchor="lb")
    return a


def save(im, name, box=None):
    rgb = im.convert("RGB")  # Apple rejects alpha; blueprints follow suit
    preview_w = 1600
    rgb.resize((preview_w, int(preview_w * im.height / im.width)), Image.LANCZOS).save(
        OUT / f"{name}.jpg", quality=88)
    if box:
        annotate(im, box, "APPLE ART SAFE AREA").convert("RGB").resize(
            (preview_w, int(preview_w * im.height / im.width)), Image.LANCZOS).save(
            OUT / f"{name}-safe-area.jpg", quality=88)


def card_fan(base, cx, cy, spread, cw, faces, seed, arc=0.18, tilt=14):
    """Cards along a gentle arc either side of (cx, cy); faces[i]=None is face-down."""
    rnd = random.Random(seed)
    n = len(faces)
    for i, face in enumerate(faces):
        t = (i / (n - 1)) * 2 - 1  # -1 .. 1
        x = cx + t * spread
        y = cy + (t * t) * spread * arc - spread * arc * 0.5
        place(base, card(cw, face), x, y, angle=-t * tilt + rnd.uniform(-5, 5))


# ---------------------------------------------------------------- A: universal
def universal():
    W, H = 5244, 2950
    im = background(W, H, INDIGO_LIGHT, INDIGO_DARK, glow_at=(0.5, 0.38))
    sparkles(im, 70, 1, scale=2.0)
    # Matched pairs fanning out into the bleed on both sides.
    left = ["🍎", None, "🐶", None, "⭐️"]
    right = ["⭐️", None, "🐶", None, "🍎"]
    card_fan(im, 900, 1500, 620, 430, left, 2, tilt=18)
    card_fan(im, W - 900, 1500, 620, 430, right, 3, tilt=18)
    card_fan(im, 1650, 2350, 420, 380, [None, "🌻", None], 4, tilt=10)
    card_fan(im, W - 1650, 2350, 420, 380, [None, "🌻", None], 5, tilt=10)
    x0, y0, x1, y1 = SAFE["universal"]
    glow(im, (x0 + x1) / 2, (y0 + y1) / 2 + 40, 720, (190, 180, 255), 120)
    place(im, flippo("win", 900), (x0 + x1) / 2, (y0 + y1) / 2 + 10)
    save(im, "A-universal-16x9", SAFE["universal"])


# ------------------------------------------------------------- B: search 3:2
def search():
    W, H = 3840, 2560
    im = background(W, H, INDIGO_LIGHT, INDIGO_DARK, glow_at=(0.5, 0.45))
    sparkles(im, 110, 11, scale=1.7)
    x0, y0, x1, y1 = SAFE["search"]
    # A face-on board: two suns turned up and matched, the rest face-down. The safe
    # area is only 1030 px tall, so the board is two rows and everything that
    # carries the idea (board, stamp, Flippo) sits inside it.
    cols, rows, cw = 4, 2, 270
    gap = 46
    bw = cols * cw + (cols - 1) * gap
    bh = rows * int(cw * 1.32) + (rows - 1) * gap
    bx = x0 + 130
    by = y0 + 180
    panel = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(panel).rounded_rectangle((bx - 70, by - 70, bx + bw + 70, by + bh + 70),
                                            radius=90, fill=(26, 20, 88, 235))
    im.alpha_composite(panel)
    faces = {(1, 0): "☀️", (2, 1): "☀️"}
    for r in range(rows):
        for c in range(cols):
            cx = bx + c * (cw + gap) + cw / 2
            cy = by + r * (int(cw * 1.32) + gap) + cw * 0.66
            place(im, card(cw, faces.get((c, r))), cx, cy)
    # Flippo cheering beside the board, inside the safe area.
    place(im, flippo("win", 940), x1 - 430, (y0 + y1) / 2 + 10)
    d = ImageDraw.Draw(im)
    sx, sy = bx + bw / 2, by - 70
    d.rounded_rectangle((sx - 300, sy - 95, sx + 300, sy + 95), radius=40, fill=GOLD)
    d.text((sx, sy), "MATCH!", font=font(120), fill=(60, 30, 0), anchor="mm")
    save(im, "B-search-3x2", SAFE["search"])


# ------------------------------------------------------ C: spooky header 21:9
def spooky_header():
    W, H = 3840, 1646
    im = background(W, H, (120, 52, 150), NIGHT, glow_at=(0.5, 0.42))
    sparkles(im, 90, 21, color=(255, 214, 160), scale=1.4)
    x0, y0, x1, y1 = SAFE["header"]
    glow(im, (x0 + x1) / 2, (y0 + y1) / 2, 520, SPOOKY, 150)
    card_fan(im, 620, 900, 420, 300, ["🎃", None, "👻", None], 31, tilt=16)
    card_fan(im, W - 620, 900, 420, 300, [None, "🦇", None, "🎃"], 32, tilt=16)
    for x, y, s, ch in [(260, 260, 170, "🦇"), (W - 300, 220, 150, "🦇"), (1500, 160, 110, "🦇")]:
        place(im, emoji(ch, s), x, y)
    for x in (240, 1000, W - 1000, W - 240):
        place(im, emoji("🎃", 260), x, H - 170)
    place(im, flippo("win", 640), (x0 + x1) / 2, (y0 + y1) / 2 + 10)
    place(im, emoji("🎃", 190), (x0 + x1) / 2 + 360, y1 - 110)
    save(im, "C-header-spooky-21x9", SAFE["header"])


# --------------------------------------------- D: Spooky Season in-app event
def event_card():
    W, H = 3840, 2160
    im = background(W, H, (130, 56, 160), NIGHT, glow_at=(0.5, 0.45))
    sparkles(im, 110, 41, color=(255, 214, 160), scale=1.6)
    glow(im, W / 2, H / 2, 820, SPOOKY, 150)
    card_fan(im, 700, 1150, 440, 380, ["👻", None, "🕷️", None], 42, tilt=16)
    card_fan(im, W - 700, 1150, 440, 380, [None, "🦇", None, "👻"], 43, tilt=16)
    place(im, flippo("win", 1200), W / 2, H / 2 + 20)
    for x in (300, W - 300):
        place(im, emoji("🎃", 340), x, H - 220)
    save(im, "D1-event-card-16x9")


def event_details():
    W, H = 2160, 3840
    im = background(W, H, (130, 56, 160), NIGHT, glow_at=(0.5, 0.42))
    sparkles(im, 120, 51, color=(255, 214, 160), scale=1.6)
    glow(im, W / 2, H * 0.44, 860, SPOOKY, 150)
    for i, (x, y, f, a) in enumerate([(360, 700, "👻", 12), (1800, 640, None, -10), (300, 2700, None, 8),
                                      (1860, 2760, "🎃", -12), (1080, 3240, "🦇", 4)]):
        place(im, card(380, f), x, y, angle=a)
    place(im, flippo("win", 1300), W / 2, H * 0.45)
    for x in (420, W - 420):
        place(im, emoji("🎃", 300), x, H - 260)
    place(im, emoji("🦇", 220), W / 2, 330)
    save(im, "D2-event-details-9x16")


def overview():
    names = ["A-universal-16x9-safe-area", "B-search-3x2-safe-area", "C-header-spooky-21x9-safe-area",
             "D1-event-card-16x9", "D2-event-details-9x16"]
    ims = [Image.open(OUT / f"{n}.jpg") for n in names]
    # Left column: the four landscape pieces stacked at 1200 wide; right: the portrait one.
    lw = 1200
    land = [i.resize((lw, int(lw * i.height / i.width)), Image.LANCZOS) for i in ims[:4]]
    gap = 40
    left_h = sum(i.height for i in land) + gap * 3
    port = ims[4].resize((int(left_h * 9 / 16), left_h), Image.LANCZOS)
    sheet = Image.new("RGB", (lw + port.width + gap * 3, left_h + gap * 2), (24, 24, 28))
    y = gap
    for i in land:
        sheet.paste(i, (gap, y))
        y += i.height + gap
    sheet.paste(port, (lw + gap * 2, gap))
    sheet.save(OUT / "overview.jpg", quality=88)


if __name__ == "__main__":
    universal()
    search()
    spooky_header()
    event_card()
    event_details()
    overview()
    print("\n".join(sorted(p.name for p in OUT.iterdir())))
