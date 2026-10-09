TARGET: Generate five App Store creative-asset illustrations for DoMemory with your built-in image generation tool. Repository root: /Users/ezequielbrrt/Documents/personal/development/apps/domemory. Proposal: .proposals/app-store-creative-assets/README.md. Layout blueprints (follow their composition): .proposals/app-store-creative-assets/blueprints/.

CHANGE: For each of the five assets below, call the image generation tool exactly once, passing the listed reference images as referenced images, then save the returned image (unmodified, full resolution) to .proposals/app-store-creative-assets/raw/<name>-1.png. Create the raw/ folder if missing.

CONSTRAINTS:
- Do NOT call view_image or open, inspect, crop, resize or edit any image. The coordinator reviews and fits them.
- Do not edit, move or delete any other file. Do not run git. Write only into .proposals/app-store-creative-assets/raw/.
- Request the exact aspect ratio given for each asset, at the largest size the tool allows.
- If a generation fails, retry that one asset at most once, then move on and report it.

CHARACTER IDENTITY (all five): assets/images/flippo/flippo-idle.png and assets/images/flippo/flippo-win.png define Flippo, DoMemory's mascot, exactly: a living coral-orange rounded memory card with a large cream question mark, a tiny red apple under the folded upper-right corner, an indigo card behind it, a navy face with blush, exactly two lavender mitten arms and two amber feet. Keep that identity; do not add limbs, change colours or redesign.

SHARED STYLE: premium polished editorial game illustration matching DoMemory's App Store screenshots: soft cel shading, crisp clean edges, gentle depth and glow, playful and friendly, 4+ audience. Memory cards are rounded rectangles: face-down cards are indigo with a lavender question mark; face-up cards are cream with one large emoji-style icon. ABSOLUTELY NO TEXT, letters, numbers, logos, watermarks, badges, prices, UI chrome or device frames anywhere unless explicitly listed. Opaque background, no transparency.

SAFE AREA: each asset lists a central box as percentages of width (x) and height (y). Flippo and every element that carries the idea must sit fully inside that box, feet included. Everything outside the box is decorative bleed that may be cropped; keep it continuous with no hard edges.

ASSETS

1) name: A-universal — aspect 16:9 landscape. References: flippo-idle.png, flippo-win.png, blueprints/A-universal-16x9.jpg.
Scene: Flippo in the joyful win pose (arms up, one foot kicked) centred in the safe area (x 37%–63%, y 22%–55%), softly lit by a lavender glow, on a flat deep indigo #4b3fc9 field that darkens toward the edges, with sparse small four-point sparkles. On both sides, in the bleed, matched memory cards fan out in gentle arcs toward the edges: face-up pairs showing a red apple, a puppy face, a gold star and a sunflower, with face-down indigo cards between them; a smaller second arc lower left and lower right. Cards tilt slightly, cast soft shadows, and nothing overlaps Flippo. Calm, uncluttered, a single clear idea: a friendly card-matching game.

2) name: B-search — aspect 3:2 landscape. References: flippo-win.png, blueprints/B-search-3x2.jpg, assets/screenshots/6.9/en-US/01_hero_flip_match.jpg (style reference only; do not copy its text).
Scene: on the same indigo #4b3fc9 field with sparse sparkles, a dark navy rounded game board panel holding 8 cards in 4 columns by 2 rows: exactly two cards face up and matching, each showing a yellow sun, all others face down. A gold rounded stamp sits on the top edge of the board with only the exact text "MATCH!" in heavy rounded dark brown capitals, character-perfect, and no other text anywhere. Flippo in the win pose cheers beside the board on the right, overlapping its right edge. Board, stamp and Flippo all inside the safe area (x 22%–78%, y 30%–70%). Readable at a small thumbnail size.

3) name: C-header-spooky — aspect 21:9 ultra-wide landscape. References: flippo-idle.png, flippo-win.png, blueprints/C-header-spooky-21x9.jpg, firebase/hosting/seasons/spooky-2026/card.png (season palette).
Scene: Halloween night, friendly not scary. A deep night-purple field (#221240 at the edges) with a warm pumpkin-orange #FF6B1A glow behind Flippo. Flippo in the win pose wearing a small cute purple witch hat, centred in the safe area (x 29%–71%, y 30%–70%), with a small jack-o'-lantern by its feet. In the bleed on both sides, memory cards fan out: face-up cards showing a jack-o'-lantern, a cute ghost and a bat, face-down indigo cards between them. A few small bats in the sky, a row of glowing jack-o'-lanterns along the bottom edge, warm sparkles. Cosy and playful.

4) name: D1-event-card — aspect 16:9 landscape. References: flippo-idle.png, flippo-win.png, blueprints/D1-event-card-16x9.jpg, firebase/hosting/seasons/spooky-2026/card.png.
Scene: the same Halloween world as asset 3, wider and taller: Flippo with the small purple witch hat, win pose, large and centred (keep Flippo within x 30%–70%, y 15%–85%) in a pumpkin-orange glow on night purple. Memory cards fan out to the left and right with face-up ghost, spider, bat and jack-o'-lantern cards between face-down indigo cards; glowing jack-o'-lanterns in the lower corners; small bats and warm sparkles. Leave the top-left corner calm (an App Store badge may sit there).

5) name: D2-event-details — aspect 9:16 portrait. References: flippo-idle.png, flippo-win.png, blueprints/D2-event-details-9x16.jpg, firebase/hosting/seasons/spooky-2026/card.png.
Scene: the same Halloween world in portrait: Flippo with the small purple witch hat, win pose, large in the middle (x 15%–85%, y 28%–65%) in a pumpkin-orange glow on night purple. Five memory cards float around Flippo at gentle angles (face-up ghost, jack-o'-lantern and bat; two face-down indigo), a small bat near the top, two glowing jack-o'-lanterns at the bottom, warm sparkles. Keep the lowest 20% calm and dark (the App Store places the event title there).

OWNERSHIP: you own only .proposals/app-store-creative-assets/raw/. Everything else is read-only.

OBSERVABLE ACCEPTANCE: five files exist — raw/A-universal-1.png, raw/B-search-1.png, raw/C-header-spooky-1.png, raw/D1-event-card-1.png, raw/D2-event-details-1.png — each the unmodified tool output. Immediately after saving the fifth file, send worker_done with --outcome succeeded (or failed, naming any missing asset), listing each saved file with its pixel dimensions as reported by the tool. Do not do anything else after worker_done.
