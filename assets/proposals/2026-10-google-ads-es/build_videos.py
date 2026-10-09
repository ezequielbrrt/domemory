#!/usr/bin/env python3
"""Builds the three Spanish Google Ads videos (9:16, 16:9, 1:1) from the es-MX
App Store screenshots with ffmpeg: each shot sits sharp over a blurred,
darkened copy of itself, drifts with a slow Ken Burns zoom, cuts on a fade,
and the video ends on an App Store card. Silent by design (no licensed music)."""
import subprocess, sys, os, pathlib

ROOT = pathlib.Path('/Users/ezequielbrrt/Documents/personal/development/apps/domemory')
SHOTS = ROOT / 'assets/screenshots/6.9/es-MX'
ICON = ROOT / 'ios/DoMemory/DoMemory/SupportingFiles/Assets.xcassets/AppIcon.appiconset/appstore.png'
FONT = '/System/Library/Fonts/Supplemental/Arial Rounded Bold.ttf'
OUT = ROOT / '.proposals/google-ads-es/video'
TMP = OUT / 'tmp'
TMP.mkdir(parents=True, exist_ok=True)

# (file, caption for the wide formats)
SEQUENCE = [
    ('01_hero_flip_match.jpg', 'Voltea y empareja.'),
    ('07_daily_themes.jpg', 'Un tablero nuevo cada día.'),
    ('04_levels.jpg', 'Sube sin fin.'),
    ('05_multiplayer.jpg', 'Reta a un amigo.'),
    ('06_create_your_own.jpg', 'Crea tu propia baraja.'),
    ('02_win_celebrate.jpg', 'Celebra cada victoria.'),
]
SHOT_SECONDS = 2.3
END_SECONDS = 2.8
FADE = 0.35
FPS = 30
PURPLE = '0x4B3FC8'

FORMATS = {
    '9x16': (1080, 1920),
    '16x9': (1920, 1080),
    '1x1': (1080, 1080),
}

def esc(text):
    return text.replace('\\', '\\\\').replace(':', '\\:').replace("'", "\\\\'")

def run(cmd):
    print(' '.join(cmd)[:300], file=sys.stderr)
    subprocess.run(cmd, check=True, capture_output=True)

def shot_segment(src, caption, w, h, out, zoom_in=True):
    frames = int(SHOT_SECONDS * FPS)
    wide = w > h * 0.8
    # Foreground fit: tall formats fit the height; wide formats keep the phone
    # on the left and leave the right side for the caption.
    fg_h = h if not wide else int(h * 0.92)
    fg_w = int(fg_h * 1290 / 2796)
    if wide:
        fg_x = f'(W*0.42-w)/2+W*0.04' if w > h else f'W*0.06'
    else:
        fg_x = '(W-w)/2'
    fg_y = '(H-h)/2'
    z0, z1 = (1.0, 1.06) if zoom_in else (1.06, 1.0)
    zoom = f"'if(eq(on,1),{z0},zoom{'+' if zoom_in else '-'}{(z1-z0)/frames:.6f})'"
    text = ''
    if wide:
        size = int(h * 0.075)
        text = (f",drawtext=fontfile='{FONT}':text='{esc(caption)}':fontsize={size}:fontcolor=white:"
                f"x=W*0.44:y=(H-text_h)/2-{int(h*0.05)}:shadowcolor=0x1C1830@0.5:shadowx=0:shadowy=4"
                f":line_spacing=8:box=0")
        sub = 'DoMemory · Gratis en el App Store'
        text += (f",drawtext=fontfile='{FONT}':text='{esc(sub)}':fontsize={int(h*0.036)}:fontcolor=0xFFD54A:"
                 f"x=W*0.44:y=(H-text_h)/2+{int(h*0.06)}")
    filt = (
        f"[0:v]scale={w*2}:{h*2}:force_original_aspect_ratio=increase,crop={w*2}:{h*2},"
        f"scale={w}:{h},boxblur=28:3,colorlevels=rimax=0.55:gimax=0.5:bimax=0.75,"
        f"colorbalance=bs=0.15:ms=0.1[bg];"
        f"[0:v]scale={fg_w}:{fg_h}[fg];"
        f"[bg][fg]overlay=x={fg_x}:y={fg_y}{text},"
        f"zoompan=z={zoom}:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d={frames}:s={w}x{h}:fps={FPS},"
        f"format=yuv420p"
    )
    run(['ffmpeg', '-y', '-loop', '1', '-i', str(src), '-filter_complex', filt,
         '-t', str(SHOT_SECONDS), '-r', str(FPS), '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', str(out)])

def end_segment(w, h, out):
    wide = w > h * 0.8
    icon = int(min(w, h) * (0.28 if not wide else 0.34))
    title = int(min(w, h) * 0.11)
    sub = int(min(w, h) * 0.05)
    line = int(min(w, h) * 0.042)
    filt = (
        f"color=c={PURPLE}:s={w}x{h}:d={END_SECONDS}:r={FPS}[bg];"
        f"[1:v]scale={icon}:{icon}[ic];"
        f"[bg][ic]overlay=x=(W-w)/2:y=(H-h)/2-{int(min(w,h)*0.22)},"
        f"drawtext=fontfile='{FONT}':text='DoMemory':fontsize={title}:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+{int(min(w,h)*0.04)}:shadowcolor=0x1C1830@0.5:shadowy=4,"
        f"drawtext=fontfile='{FONT}':text='{esc('Un juego de memoria, cinco formas de jugar.')}':fontsize={line}:fontcolor=white@0.9:x=(w-text_w)/2:y=(h-text_h)/2+{int(min(w,h)*0.17)},"
        f"drawtext=fontfile='{FONT}':text='{esc('Gratis en el App Store')}':fontsize={sub}:fontcolor=0xFFD54A:x=(w-text_w)/2:y=(h-text_h)/2+{int(min(w,h)*0.26)},"
        f"format=yuv420p"
    )
    run(['ffmpeg', '-y', '-f', 'lavfi', '-i', f'color=c={PURPLE}:s={w}x{h}:d={END_SECONDS}:r={FPS}',
         '-loop', '1', '-i', str(ICON), '-filter_complex', filt.replace(f"color=c={PURPLE}:s={w}x{h}:d={END_SECONDS}:r={FPS}[bg];", "[0:v]null[bg];"),
         '-t', str(END_SECONDS), '-r', str(FPS), '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', str(out)])

def join(segments, durations, out):
    inputs = []
    for s in segments:
        inputs += ['-i', str(s)]
    filt = ''
    prev = '[0:v]'
    offset = 0.0
    for i in range(1, len(segments)):
        offset += durations[i - 1] - FADE
        cur = f'[v{i}]'
        filt += f"{prev}[{i}:v]xfade=transition=fade:duration={FADE}:offset={offset:.3f}{cur if i < len(segments)-1 else '[out]'};"
        prev = cur
    filt = filt.rstrip(';')
    run(['ffmpeg', '-y', *inputs, '-filter_complex', filt, '-map', '[out]', '-r', str(FPS),
         '-c:v', 'libx264', '-preset', 'slow', '-crf', '19', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(out)])

for name, (w, h) in FORMATS.items():
    segs, durs = [], []
    for i, (f, caption) in enumerate(SEQUENCE):
        seg = TMP / f'{name}-{i}.mp4'
        shot_segment(SHOTS / f, caption, w, h, seg, zoom_in=(i % 2 == 0))
        segs.append(seg); durs.append(SHOT_SECONDS)
    end = TMP / f'{name}-end.mp4'
    end_segment(w, h, end)
    segs.append(end); durs.append(END_SECONDS)
    join(segs, durs, OUT / f'domemory-es-{name}.mp4')
    print('built', name)
