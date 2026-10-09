"""Build the Spooky Season Play Console event animation (Lottie JSON, <= 200 KB).

Comp: 1920x1080, 60 fps, 300 frames (5 s), seamless loop.
Layers: the event art (embedded JPEG) with a slow push-in/out, plus vector
overlays parented to it: moon glow, flickering pumpkin glow, twinkling stars,
flapping bats and falling leaves. Every periodic motion has a period that
divides 300, and everything that travels is off screen at frames 0 and 300.
"""
import base64, io, json, math, sys
from PIL import Image

SRC = sys.argv[1]
OUT = sys.argv[2]
BUDGET = 200 * 1024
W, H, FR, OP = 1920, 1080, 60, 300

EASE = {"i": {"x": [0.42], "y": [1]}, "o": {"x": [0.58], "y": [0]}}
LIN = {"i": {"x": [1], "y": [1]}, "o": {"x": [0], "y": [0]}}


def r(v):
    return round(v, 1) if isinstance(v, float) else v


def static(v):
    return {"a": 0, "k": v}


def anim(keys, ease=EASE):
    """keys: list of (frame, value). Values may be scalars or lists."""
    ks = []
    for n, (t, v) in enumerate(keys):
        v = v if isinstance(v, list) else [v]
        k = {"t": t, "s": [r(x) for x in v]}
        if n < len(keys) - 1:
            k["i"] = {"x": ease["i"]["x"][0], "y": ease["i"]["y"][0]}
            k["o"] = {"x": ease["o"]["x"][0], "y": ease["o"]["y"][0]}
        ks.append(k)
    return {"a": 1, "k": ks}


def transform(p=(0, 0), a=(0, 0), s=(100, 100), rot=0, o=100):
    def wrap(v):
        return v if isinstance(v, dict) else static(list(v) if isinstance(v, tuple) else v)
    return {"p": wrap(p), "a": wrap(a), "s": wrap(s), "r": wrap(rot), "o": wrap(o)}


def group_tr(**kw):
    t = transform(**kw)
    t["ty"] = "tr"
    return t


def path(verts, ins=None, outs=None, closed=True):
    n = len(verts)
    return {"ty": "sh", "ks": static({
        "c": closed,
        "v": [[r(x), r(y)] for x, y in verts],
        "i": [[r(x), r(y)] for x, y in (ins or [(0, 0)] * n)],
        "o": [[r(x), r(y)] for x, y in (outs or [(0, 0)] * n)],
    })}


def fill(hex_, o=100):
    c = [int(hex_[i:i + 2], 16) / 255 for i in (1, 3, 5)] + [1]
    return {"ty": "fl", "c": static([round(x, 3) for x in c]), "o": static(o), "r": 1}


def radial(hex_, radius, inner_alpha=1.0):
    """Radial gradient fill fading from hex_ (centre) to transparent (edge)."""
    c = [round(int(hex_[i:i + 2], 16) / 255, 3) for i in (1, 3, 5)]
    stops = [0] + c + [0.45] + c + [1] + c
    alphas = [0, inner_alpha, 0.45, round(inner_alpha * 0.35, 3), 1, 0]
    return {"ty": "gf", "t": 2, "o": static(100), "r": 1,
            "s": static([0, 0]), "e": static([radius, 0]),
            "h": static(0), "a": static(0),
            "g": {"p": 3, "k": static(stops + alphas)}}


def ellipse(rx, ry, p=(0, 0)):
    return {"ty": "el", "p": static(list(p)), "s": static([rx * 2, ry * 2])}


def group(items, **tr):
    return {"ty": "gr", "it": items + [group_tr(**tr)]}


ind = [0]


def shape_layer(name, shapes, ks, parent=None, ip=0, op=OP, st=0):
    ind[0] += 1
    L = {"ddd": 0, "ind": ind[0], "ty": 4, "nm": name, "sr": 1, "ks": ks,
         "ao": 0, "shapes": shapes, "ip": ip, "op": op, "st": st, "bm": 0}
    if parent:
        L["parent"] = parent
    return L


def periodic(period, lo, hi, total=OP * 2):
    """Trough-peak-trough keys covering [0, total]; with layer st=-phase it loops."""
    keys, t = [], 0
    while t < total:
        keys.append((t, lo))
        keys.append((t + period // 2, hi))
        t += period
    keys.append((t, lo))
    return keys


# --- background ------------------------------------------------------------
def background(quality, width):
    im = Image.open(SRC).convert("RGB")
    h = round(width * 9 / 16)
    b = io.BytesIO()
    im.resize((width, h), Image.LANCZOS).save(b, "JPEG", quality=quality, optimize=True, progressive=True)
    return width, h, "data:image/jpeg;base64," + base64.b64encode(b.getvalue()).decode()


def build(quality, width):
    ind[0] = 0
    iw, ih, data = background(quality, width)
    layers = []

    # Null that carries the slow push-in; everything on the art is parented to it.
    ind[0] += 1
    cam = ind[0]
    layers.append({"ddd": 0, "ind": cam, "ty": 3, "nm": "camera", "sr": 1,
                   "ks": transform(p=(W / 2, H / 2 + 10), a=(W / 2, H / 2 + 10),
                                   s=anim([(0, [100, 100]), (150, [104, 104]), (300, [100, 100])])),
                   "ao": 0, "ip": 0, "op": OP, "st": 0, "bm": 0})

    over = []  # overlay layers, front-most first

    # Twinkling stars on top of the art's own sparkles.
    stars = [(940, 110, 26, 100, 0), (1170, 168, 22, 150, 40), (1330, 320, 24, 100, 55),
             (660, 380, 22, 150, 90), (370, 365, 20, 100, 20), (1220, 432, 22, 150, 120),
             (460, 740, 20, 100, 70), (675, 760, 22, 150, 10), (1270, 702, 22, 100, 35),
             (1470, 722, 20, 150, 100), (560, 70, 16, 100, 80), (1430, 150, 14, 150, 60),
             (260, 230, 14, 100, 45), (1890, 435, 18, 150, 25)]
    for n, (x, y, R, P, ph) in enumerate(stars):
        k = R * 0.12
        star = path([(0, -R), (R, 0), (0, R), (-R, 0)],
                    ins=[(-k, R * 0.75), (R * 0.75, -k), (k, -R * 0.75), (-R * 0.75, k)],
                    outs=[(k, R * 0.75), (-R * 0.75, -k), (-k, -R * 0.75), (R * 0.75, k)])
        shapes = [group([ellipse(R * 1.4, R * 1.4), radial("#FFD76A", R * 1.4, 0.55)]),
                  group([star, fill("#FFF3C4")])]
        over.append(shape_layer(f"star{n}", shapes,
                                transform(p=(x, y), s=anim([(t, [v, v]) for t, v in periodic(P, 35, 115, OP + P)]),
                                          o=anim(periodic(P, 0, 100, OP + P)),
                                          rot=anim([(0, 0), (OP * 2, 180)], LIN)),
                                parent=cam, st=-ph))

    # Bats: body + two wings that flap around the shoulders every 20 frames.
    def bat(name, keys, scale, ip=0, op=OP, phase=0):
        ink = "#1A0E2E"
        body = group([ellipse(10, 17, (0, 6)), ellipse(9, 9, (0, -10)),
                      path([(-8, -13), (-6, -26), (-1, -16)]), path([(8, -13), (6, -26), (1, -16)]),
                      fill(ink)])
        wing_shape = path([(-6, -8), (-76, -46), (-64, -8), (-49, -2), (-33, 8), (-17, 11), (-6, 14)],
                          ins=[(0, 0), (-30, 4), (3, -9), (3, -8), (3, -8), (3, -7), (0, 0)],
                          outs=[(-24, -20), (0, 0), (5, -3), (5, -3), (5, -3), (5, -3), (0, 0)])
        mirror = path([(-x, y) for x, y in wing_shape["ks"]["k"]["v"]],
                      ins=[(-x, y) for x, y in wing_shape["ks"]["k"]["i"]],
                      outs=[(-x, y) for x, y in wing_shape["ks"]["k"]["o"]])
        flap = anim([(t, [100, v]) for t, v in periodic(20, 100, -70, OP + 20)])
        wings = group([wing_shape, mirror, fill(ink)], s=flap)
        return shape_layer(name, [body, wings],
                           transform(p=anim(keys, LIN), s=(scale, scale)), parent=cam,
                           ip=ip, op=op, st=-phase)

    over.append(bat("bat-big", [(0, [2060, 300]), (75, [1560, 250]), (150, [1060, 300]),
                                 (225, [560, 250]), (300, [-140, 300])], 95))
    over.append(bat("bat-moon", [(0, [-100, 110]), (100, [600, 70]), (200, [1300, 110]),
                                  (300, [2040, 70])], 55, phase=7))
    over.append(bat("bat-small", [(40, [2000, 520]), (140, [1720, 300]), (240, [1440, -60])], 40,
                    ip=40, op=240, phase=13))

    # Falling leaves drifting across the lower half.
    def leaf(name, x0, y0, x1, y1, ip, op, color, spin, r0=0):
        shape = path([(0, -22), (0, 22)], ins=[(-14, 14), (14, -14)], outs=[(14, 14), (-14, -14)])
        rib = path([(0, -18), (0, 18)], closed=False)
        stroke = {"ty": "st", "c": static([0.55, 0.22, 0.08, 1]), "o": static(70), "w": static(2), "lc": 2, "lj": 2}
        mid = ((x0 + x1) / 2 + 60, (y0 + y1) / 2)
        return shape_layer(name, [group([shape, fill(color)]), group([rib, stroke])],
                           transform(p=anim([(ip, [x0, y0]), ((ip + op) // 2, list(mid)), (op, [x1, y1])], LIN),
                                     rot=anim([(ip, r0), (op, r0 + spin)], LIN), s=(110, 110)),
                           parent=cam, ip=ip, op=op)

    over.append(leaf("leaf1", 300, -40, 520, 1130, 0, 300, "#F28C28", 300))
    over.append(leaf("leaf2", 1500, -40, 1320, 1130, 90, 300, "#E8642A", -260))
    over.append(leaf("leaf3", 1000, -40, 860, 600, 200, 300, "#F6A93B", 200))
    over.append(leaf("leaf3b", 860, 600, 760, 1130, 0, 60, "#F6A93B", 120, r0=200))

    # Flickering glow over the jack-o'-lanterns.
    flick = [(0, 60), (23, 90), (41, 55), (70, 95), (96, 65), (131, 100), (160, 60),
             (188, 85), (214, 55), (247, 95), (275, 70), (300, 60)]
    for n, (x, y, R, ph) in enumerate([(185, 965, 170, 0), (1735, 955, 170, 37), (395, 880, 85, 71),
                                         (1530, 865, 95, 19), (625, 885, 45, 53), (1290, 888, 45, 89)]):
        shifted = [((t + ph) % 300, v) for t, v in flick[:-1]]
        shifted.sort()
        first = shifted[0][1]
        # close the loop: value at 0 and 300 must match
        edge = shifted[-1][1] + (first - shifted[-1][1]) * 0.5
        keys = [(0, edge)] + [kv for kv in shifted if kv[0] != 0] + [(300, edge)]
        over.append(shape_layer(f"pumpkin-glow{n}", [group([ellipse(R, R * 0.8), radial("#FFB43C", R, 0.5)])],
                                transform(p=(x, y), o=anim(keys, LIN)), parent=cam))

    # Moon halo pulse.
    over.append(shape_layer("moon-glow", [group([ellipse(300, 300), radial("#FFE3A3", 300, 0.45)])],
                            transform(p=(1612, 140), o=anim([(0, 35), (150, 90), (300, 35)]),
                                      s=anim([(0, [92, 92]), (150, [108, 108]), (300, [92, 92])])),
                            parent=cam))

    # Art layer.
    ind[0] += 1
    art = {"ddd": 0, "ind": ind[0], "ty": 2, "nm": "event-art", "refId": "art", "sr": 1,
           "ks": transform(p=(W / 2, H / 2), a=(iw / 2, ih / 2), s=(W / iw * 100, W / iw * 100)),
           "ao": 0, "ip": 0, "op": OP, "st": 0, "bm": 0, "parent": cam}

    doc = {"v": "5.7.4", "fr": FR, "ip": 0, "op": OP, "w": W, "h": H, "nm": "Spooky Season", "ddd": 0,
           "assets": [{"id": "art", "w": iw, "h": ih, "u": "", "p": data, "e": 1}],
           "layers": [layers[0]] + over + [art], "markers": []}
    return json.dumps(doc, separators=(",", ":"))


for width, q in [(1440, 70), (1440, 64), (1280, 74), (1280, 70), (1280, 66), (1280, 60)]:
    s = build(q, width)
    if len(s.encode()) <= BUDGET - 4 * 1024:
        break
open(OUT, "w").write(s)
print(f"{OUT}: {len(s.encode()) / 1024:.1f} KB, art {width}px q{q}")
