TARGET: Generate dedicated, higher-quality App Store PRODUCT PAGE HEADER and SEARCH RESULTS illustrations for DoMemory with your built-in image generation tool. Repository root: /Users/ezequielbrrt/Documents/personal/development/apps/domemory.

CHANGE: Four concepts (H1, H2 for the header; S1, S2 for search). Call the image tool TWICE per concept (two independent variants, 8 calls in total), passing the listed reference images, and save each unmodified full-resolution output to .proposals/app-store-creative-assets/raw/<name>-1.png and <name>-2.png.

CONSTRAINTS:
- Do NOT call view_image or open, inspect, crop, resize or edit any image.
- Write only into .proposals/app-store-creative-assets/raw/. Do not edit other files and do not run git.
- Exact aspect ratio as listed, at the largest size the tool allows. Opaque, no transparency.
- ABSOLUTELY NO TEXT anywhere: no letters, words, numbers, logos, watermarks, badges, prices, UI chrome, status bars or device frames. These images serve all ten store languages.
- 4+ audience: friendly, joyful, nothing scary.

CHARACTERS:
- Flippo (references assets/images/flippo/flippo-idle.png and flippo-win.png), exact identity: a living coral-orange rounded memory card with a large cream question mark, a tiny red apple under the folded upper-right corner, an indigo card behind it, a navy face with blush, exactly two lavender mitten arms and two amber feet.
- Teal friend (only where listed; reference assets/screenshots/6.9/en-US/05_multiplayer.jpg, the scene in the white panel): the same kind of living card but mint-teal, with a cream question mark, a small gold star under its folded corner, an indigo card behind it, lavender mittens and amber feet.
- Keep both identities exactly. No extra limbs or characters.

QUALITY BAR: premium, polished, editorial game key art, a clear step up from a flat background. Real depth: a soft, out-of-focus foreground layer (blurred cards or bokeh) near the frame edges, a crisp mid-ground subject and an atmospheric background. Directional rim light and glow, gentle volumetric light, rich but controlled colour. Main palette: deep indigo #4b3fc9 to #2a1f8f, with accents of gold #fbc54a, mint #5fe3b3 and coral #fb6b4b. Clean, uncluttered and readable at small size: one clear idea per image.

MEMORY CARDS: rounded rectangles. Face-down cards are indigo with a lavender question mark. Face-up cards are cream with one large emoji-style icon (apple, puppy, gold star, sunflower, sun, octopus, pizza, rocket and similar friendly icons).

SCALE RULE (critical): the App Store shows only a central box of these images on some devices and covers the lower part with the app name and Get button. State the size numbers below explicitly in your prompt, and say the open space around the subject is intentional. Keep the main subject SMALLER than you think: the listed numbers are maximums.

THE CONCEPTS

H1 — name: H1-header-stage — aspect 21:9 ultra-wide. References: flippo-win.png, flippo-idle.png.
"The big reveal." Flippo in the win pose on a low, glowing round stage made of stacked face-down cards, centred. A spotlight and soft volumetric light come from above. A sweeping wave of memory cards arcs left and right from behind Flippo, flipping mid-air in sequence: the cards nearest Flippo are turning face-up (puppy, star, apple, sunflower), each with a small sparkle burst, while the outer ones are still face-down, showing the flip in motion. Large, blurred, out-of-focus cards sit in the extreme left and right foreground. Flippo including its feet: at most 26% of the image height, top at about 36%, feet at about 62%, horizontally centred. All face-up cards stay between 25% and 75% of the width. The bottom 30% is calm: the stage's soft glow fading into dark indigo, no detail.

H2 — name: H2-header-constellations — aspect 21:9 ultra-wide. References: flippo-win.png, flippo-idle.png.
"Memory constellations." A dreamy deep-indigo night sky. Memory cards float like glowing lanterns at different depths (near ones larger and softly blurred, far ones small and sharp). Matching face-up pairs are joined by thin, glowing golden constellation lines with tiny stars along them: puppy to puppy, star to star, apple to apple. Flippo floats happily in the centre in the win pose, gently lit, as if conducting the sky. Flippo: at most 26% of the image height, top at about 36%, feet at about 62%, centred. The matched pairs and their lines sit mostly between 25% and 75% of the width. The bottom 30% is a calm gradient of soft clouds fading dark.

S1 — name: S1-search-match — aspect 3:2 landscape. References: flippo-win.png, assets/screenshots/6.9/en-US/01_hero_flip_match.jpg (game board look; ignore its text).
"The match moment, up close." A 3/4-perspective view of a dark navy rounded game board with a 4 by 3 grid of cards. Exactly two cards are flipping up at the same moment to reveal matching yellow suns, with a burst of golden sparkles and a soft glow between them, while the other cards are face-down. Flippo leans in from the right edge of the board, cheering, one mitten pointing at the pair. The board and Flippo together span about 26% to 74% of the image width and 33% to 67% of the image height, centred. Indigo background with soft depth: blurred cards in the far corners. Instantly readable as a card-matching game, with no text.

S2 — name: S2-search-friends — aspect 3:2 landscape. References: flippo-win.png, flippo-idle.png, assets/screenshots/6.9/en-US/05_multiplayer.jpg (teal friend and scene style; ignore its text and QR code).
"Play together." Flippo and the teal friend stand on either side of a small floating game board of 3 by 2 cards. Two puppy cards are face-up as a found pair, the rest face-down. The two characters high-five above the board with a burst of sparkles. Above the board, a few face-up cards hover, showing different themes (pizza, rocket, octopus, sunflower) to hint at many themes. The whole group spans about 26% to 74% of the image width and 33% to 67% of the image height, centred. Indigo background with soft depth and gentle rim light, sparse sparkles.

OWNERSHIP: only these 8 new files in .proposals/app-store-creative-assets/raw/: H1-header-stage-1.png, H1-header-stage-2.png, H2-header-constellations-1.png, H2-header-constellations-2.png, S1-search-match-1.png, S1-search-match-2.png, S2-search-friends-1.png, S2-search-friends-2.png.

OBSERVABLE ACCEPTANCE: all 8 files exist as unmodified tool output. Immediately after saving the eighth, send worker_done with --outcome succeeded (or failed, naming any missing file), and do nothing else.
