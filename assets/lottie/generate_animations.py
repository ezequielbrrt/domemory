#!/usr/bin/env python3
"""Hand-authors the small Lottie clips DoMemory ships, as plain Python.

There is no After Effects pipeline for this project, so every clip here is
described as shapes and keyframes and written out as Lottie 5.9 JSON. Run it
from anywhere; it (re)writes the files next to itself:

    python3 assets/lottie/generate_animations.py

Conventions the two apps rely on:

* Every clip is 100x100 at 60 fps, drawn around (0, 0) with the layer anchored
  at the canvas centre, so a call site only needs to size it.
* A fill or stroke named ``tint`` is recoloured at runtime to the current palette
  (``LottieView(tint:)`` on iOS, ``BundledLottie(tint = ...)`` on Android). Shapes
  with any other name keep their authored colour. The authored colours below are
  the light-mode palette, so a clip still looks right if a call site passes no
  tint.
* "Overlay" clips (``heart-refill``, ``freeze-thaw``, ``star-sparkle``,
  ``heart-break``) end fully transparent: the UI draws the real final state
  underneath and the clip is only the transient effect. "Hero" clips
  (``clock-crack``, ``x-shake``) end on a static picture that stays on screen.

``confetti-burst`` and ``star-pop`` predate this script and are not regenerated.
"""

import json
import math
import os

FPS = 60
SIZE = 100

# Light-mode palette, from ios/.../Configuration/AppConfiguration.swift.
SECONDARY = (255 / 255, 99 / 255, 64 / 255)     # secundaryColor
AMBER = (245 / 255, 166 / 255, 35 / 255)        # hardAmber
FREEZE = (0 / 255, 145 / 255, 199 / 255)        # freezeBlue
WHITE = (1.0, 1.0, 1.0)

EASE = {"i": {"x": 0.667, "y": 1}, "o": {"x": 0.333, "y": 0}}
EASE_OUT = {"i": {"x": 0.2, "y": 1}, "o": {"x": 0.4, "y": 0}}
EASE_IN = {"i": {"x": 0.6, "y": 1}, "o": {"x": 0.8, "y": 0}}


def const(value):
    return {"a": 0, "k": value}


def animated(keys, ease=EASE):
    """keys: [(frame, value), ...]; values are lists (or scalars)."""
    out = []
    for index, (frame, value) in enumerate(keys):
        value = value if isinstance(value, list) else [value]
        if index == len(keys) - 1:
            out.append({"t": frame, "s": value})
        else:
            nxt = keys[index + 1][1]
            nxt = nxt if isinstance(nxt, list) else [nxt]
            out.append({"t": frame, "s": value, "e": nxt, **ease})
    return {"a": 1, "k": out}


def rgba(color):
    return [color[0], color[1], color[2], 1]


def fill(color, name="tint", opacity=100):
    return {"ty": "fl", "c": const(rgba(color)), "o": const(opacity), "r": 1, "nm": name}


def stroke(color, width, name="tint", opacity=100):
    return {"ty": "st", "c": const(rgba(color)), "o": const(opacity), "w": const(width), "lc": 2, "lj": 2, "nm": name}


def transform(p=(0, 0), s=(100, 100), r=0, o=100, name="transform"):
    return {"ty": "tr", "p": const(list(p)), "a": const([0, 0]), "s": const(list(s)), "r": const(r), "o": const(o), "nm": name}


def ellipse(size, p=(0, 0), name="ellipse"):
    return {"ty": "el", "d": 1, "p": const(list(p)), "s": const(list(size)), "nm": name}


def rect(size, p=(0, 0), radius=0, name="rect"):
    return {"ty": "rc", "d": 1, "p": const(list(p)), "s": const(list(size)), "r": const(radius), "nm": name}


def star(points, inner, outer, name="star"):
    return {
        "ty": "sr", "sy": 1, "d": 1, "pt": const(points), "p": const([0, 0]), "r": const(0),
        "ir": const(inner), "is": const(0), "or": const(outer), "os": const(0), "nm": name,
    }


def path(vertices, in_tangents=None, out_tangents=None, closed=True, name="path"):
    count = len(vertices)
    in_tangents = in_tangents or [[0, 0]] * count
    out_tangents = out_tangents or [[0, 0]] * count
    return {
        "ty": "sh", "d": 1,
        "ks": const({"i": in_tangents, "o": out_tangents, "v": [list(v) for v in vertices], "c": closed}),
        "nm": name,
    }


def trim(end_keys, name="trim"):
    return {"ty": "tm", "s": const(0), "e": animated(end_keys), "o": const(0), "m": 1, "nm": name}


def group(items, name, tr=None):
    return {"ty": "gr", "it": items + [tr or transform(name=f"{name}-transform")], "nm": name}


def layer(name, ind, shapes, op, o=None, r=None, p=None, s=None, ip=0):
    return {
        "ddd": 0, "ind": ind, "ty": 4, "nm": name, "sr": 1,
        "ks": {
            "o": o or const(100),
            "r": r or const(0),
            "p": p or const([SIZE / 2, SIZE / 2, 0]),
            "a": const([0, 0, 0]),
            "s": s or const([100, 100, 100]),
        },
        "ao": 0, "shapes": shapes, "ip": ip, "op": op, "st": 0, "bm": 0,
    }


def clip(name, op, layers):
    return {
        "v": "5.9.6", "fr": FPS, "ip": 0, "op": op, "w": SIZE, "h": SIZE, "nm": name,
        "ddd": 0, "assets": [], "layers": layers, "markers": [],
    }


def polar(radius, degrees):
    a = math.radians(degrees)
    return [round(radius * math.cos(a), 2), round(radius * math.sin(a), 2)]


# ---------------------------------------------------------------------------
# clock-crack — the "out of time" hero. The face wobbles like a rung bell,
# then a crack draws itself across the glass. Ends on the cracked clock.
# ---------------------------------------------------------------------------
def clock_crack():
    op = 60
    face = [
        ellipse((64, 64), name="face"),
        fill(SECONDARY, opacity=14),
        stroke(SECONDARY, 6),
        transform(name="face-transform"),
    ]
    hands = [
        path([(0, 0), (0, -18)], closed=False, name="hour"),
        path([(0, 0), (13, 0)], closed=False, name="minute"),
        stroke(SECONDARY, 5),
        transform(name="hands-transform"),
    ]
    crack = [
        path([(-3, -33), (3, -14), (-8, 1), (6, 13), (-2, 33)], closed=False, name="crack-path"),
        stroke(SECONDARY, 4),
        trim([(20, 0), (40, 100)]),
        transform(name="crack-transform"),
    ]
    return clip("clock-crack", op, [
        layer(
            "clock", 1,
            [group(hands, "hands"), group(face, "face")],  # first shape draws on top
            op,
            r=animated([(0, 0), (8, -12), (16, 11), (24, -8), (32, 5), (40, -2), (48, 0)]),
            s=animated([(0, [100, 100, 100]), (6, [110, 110, 100]), (14, [100, 100, 100])]),
        ),
        layer("crack", 2, [group(crack, "crack")], op),
    ])


# ---------------------------------------------------------------------------
# x-shake — the "too many mistakes" hero. A red badge stamps in oversized,
# settles, then shakes its head. Ends on the static badge.
# ---------------------------------------------------------------------------
def x_shake():
    op = 48
    badge = [ellipse((64, 64), name="badge"), fill(SECONDARY), transform(name="badge-transform")]
    cross = [
        rect((9, 38), radius=4.5, name="bar-a"),
        fill(WHITE, name="cross-fill"),
        transform(r=45, name="bar-a-transform"),
    ]
    cross_b = [
        rect((9, 38), radius=4.5, name="bar-b"),
        fill(WHITE, name="cross-fill"),
        transform(r=-45, name="bar-b-transform"),
    ]
    return clip("x-shake", op, [
        layer(
            "x", 1,
            [group(cross, "bar-a"), group(cross_b, "bar-b"), group(badge, "badge")],  # first shape draws on top
            op,
            o=animated([(0, 0), (4, 100)]),
            s=animated([(0, [0, 0, 100]), (8, [124, 124, 100]), (14, [100, 100, 100])], EASE_OUT),
            p=animated([
                (14, [50, 50, 0]), (18, [44, 50, 0]), (22, [56, 50, 0]),
                (26, [46, 50, 0]), (30, [53, 50, 0]), (34, [50, 50, 0]),
            ]),
        ),
    ])


# ---------------------------------------------------------------------------
# heart-break — one life lost. The filled heart squeezes, splits along a
# jagged line, and the two halves fall apart and fade. Ends transparent, so
# the outline heart the UI draws underneath is what remains.
# ---------------------------------------------------------------------------
HEART_ZIG = [(0, -20), (-4, -10), (4, 0), (-3, 8), (0, 18)]


def heart_half(side):
    """One half of a heart, closed along the jagged split so the two halves tile."""
    sign = -1 if side == "left" else 1
    # Top dip, the zig-zag down to the bottom tip, then the lobe's top.
    v = HEART_ZIG + [(sign * 10, -30)]
    i = [[0, 0]] * 6
    o = [[0, 0]] * 6
    # Bottom tip -> lobe: the heart's outer curve. Lobe -> top dip: the inner curve.
    o[4] = [sign * 30, -22]
    i[5] = [sign * 20, 0]
    o[5] = [sign * -7, 0]
    i[0] = [0, -4]
    return path(v, i, o, closed=True, name=f"{side}-path")


def heart_break():
    op = 54
    split = 12

    def half(side, ind):
        sign = -1 if side == "left" else 1
        return layer(
            f"{side}-half", ind,
            [group([heart_half(side), fill(SECONDARY), transform(name=f"{side}-transform")], side)],
            op,
            s=animated([(0, [100, 100, 100]), (6, [110, 110, 100]), (split, [100, 100, 100])]),
            p=animated([(split, [50, 50, 0]), (split + 18, [50 + sign * 7, 56, 0]), (op - 6, [50 + sign * 9, 62, 0])], EASE_OUT),
            r=animated([(split, 0), (split + 18, sign * 16), (op - 6, sign * 20)], EASE_OUT),
            o=animated([(split + 12, 100), (op - 4, 0)], EASE_IN),
        )

    return clip("heart-break", op, [half("left", 1), half("right", 2)])


# ---------------------------------------------------------------------------
# Overlay bursts. Each is a ring plus a handful of particles flying outward
# and fading; they end transparent over whatever the UI already shows.
# ---------------------------------------------------------------------------
def burst_ring(ind, op, color, width=4, start=40, end=130, name="ring"):
    return layer(
        name, ind,
        [group([ellipse((40, 40), name="ring-path"), stroke(color, width), transform(name="ring-transform")], "ring")],
        op,
        s=animated([(0, [start, start, 100]), (op * 2 // 3, [end, end, 100])], EASE_OUT),
        o=animated([(0, 100), (op * 2 // 3, 0)], EASE_OUT),
    )


def particle_layer(ind, op, shape_items, angle, inner, outer, spin=0, start=0, life=None, name="particle"):
    """A particle that flies from radius `inner` to `outer` along `angle`, shrinking and fading."""
    life = life or op
    end = min(op, start + life)
    fly_end = start + int(life * 0.75)
    origin = [50 + polar(inner, angle)[0], 50 + polar(inner, angle)[1], 0]
    target = [50 + polar(outer, angle)[0], 50 + polar(outer, angle)[1], 0]
    return layer(
        f"{name}-{ind}", ind, [group(shape_items, "particle")], op,
        p=animated([(start, origin), (fly_end, target)], EASE_OUT),
        s=animated([(start, [100, 100, 100]), (end, [45, 45, 100])], EASE_OUT),
        o=animated([(start, 0), (start + 3, 100), (start + int(life * 0.4), 100), (end, 0)], EASE_OUT),
        r=animated([(start, 0), (end, spin)]) if spin else None,
        ip=start,
    )


def heart_refill():
    op = 42
    layers = [burst_ring(1, op, SECONDARY)]
    for index in range(6):
        layers.append(particle_layer(
            index + 2, op,
            [ellipse((7, 7), name="dot"), fill(SECONDARY), transform(name="dot-transform")],
            angle=index * 60 - 90, inner=10, outer=36,
        ))
    return clip("heart-refill", op, layers)


def freeze_thaw():
    op = 36
    shard = [path([(0, -7), (5, 2), (0, 7), (-5, 2)], closed=True, name="shard-path"), fill(FREEZE), transform(name="shard-transform")]
    layers = [burst_ring(1, op, FREEZE, width=3, start=30, end=150)]
    for index in range(8):
        angle = index * 45 + (0 if index % 2 == 0 else 12)
        layers.append(particle_layer(
            index + 2, op, shard, angle=angle, inner=6, outer=42 if index % 2 == 0 else 34,
            spin=140 if index % 2 == 0 else -110,
        ))
    return clip("freeze-thaw", op, layers)


def star_sparkle():
    op = 48
    spots = [(-18, -10), (14, -16), (20, 8), (-14, 12), (2, -22), (0, 16)]
    layers = []
    for index, (x, y) in enumerate(spots):
        start = index * 5
        life = 20
        end = start + life
        layers.append(layer(
            f"spark-{index}", index + 1,
            [group([star(4, 2.5, 8), fill(AMBER), transform(name="spark-transform")], "spark")],
            op,
            p=const([50 + x, 50 + y, 0]),
            s=animated([(start, [0, 0, 100]), (start + life // 2, [110, 110, 100]), (end, [0, 0, 100])], EASE_OUT),
            r=animated([(start, 0), (end, 90)]),
            o=animated([(start, 100), (end, 100)]),
            ip=start,
        ))
    return clip("star-sparkle", op, layers)


CLIPS = [clock_crack, x_shake, heart_break, heart_refill, freeze_thaw, star_sparkle]


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    for build in CLIPS:
        data = build()
        target = os.path.join(here, f"{data['nm']}.json")
        with open(target, "w") as handle:
            json.dump(data, handle, separators=(", ", ": "))
        print(f"wrote {os.path.relpath(target)} ({os.path.getsize(target)} bytes)")


if __name__ == "__main__":
    main()
