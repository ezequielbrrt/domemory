# DoMemory playable ad (Google Ads App campaigns)

A single self-contained HTML5 playable: a 6-pair memory board with a 30-second
timer and a Flippo end card. Upload `domemory-playable.html` as an **HTML5
asset** on an App campaign. The store link is set on the campaign, not in the
file; the "Play" buttons call Google's `ExitApi.exit()`.

## Files

- `template.html` — the source. Edit copy, colors or the emoji pool here.
- `build.py` — inlines `assets/images/flippo/flippo-win.png` (as a ~30 KB WebP
  data URI) into `domemory-playable.html` and checks the result has no external
  references, wires the exit API and is under 5 MB.
- `domemory-playable.html` — the built asset. Do not edit by hand.

```
python3 scripts/playable/build.py
```

## Constraints it meets

- No network requests other than `tpc.googlesyndication.com/…/exitapi.js`.
- Responsive: portrait (3×4 board) and landscape (4×3 board), tested at
  320×480 and 480×320.
- Finishable in under 30 seconds; a persistent CTA plus an end card.
- No audio, no storage, no data collection, no `window.open`.
- The emoji list mirrors `EmojiPool.all` in the app (same order). Append there
  first, then here.

## Preview locally

Open `domemory-playable.html` in a browser. Without Google's exit API the
buttons do nothing, which is expected.
