TARGET: Regenerate three of the five App Store creative assets from the previous task, with a much SMALLER central composition. Repository root: /Users/ezequielbrrt/Documents/personal/development/apps/domemory. The previous outputs are .proposals/app-store-creative-assets/raw/A-universal-1.png, B-search-1.png and C-header-spooky-1.png; their art style, colours and character are approved. Only the scale and placement of the main subject are wrong: the App Store crops these images hard and covers their lower part with the app name and Get button, so only a small central box is guaranteed visible.

CHANGE: For each asset below, call the image generation tool TWICE (two independent variants), passing the listed reference images, and save the unmodified full-resolution outputs to .proposals/app-store-creative-assets/raw/<name>-2.png and <name>-3.png.

CONSTRAINTS (same as before):
- Do NOT call view_image or open, inspect, crop, resize or edit any image.
- Write only into .proposals/app-store-creative-assets/raw/. Do not edit any other file and do not run git.
- Exact aspect ratio as listed, largest size the tool allows. No text anywhere except the single word in asset B. No transparency.
- Keep Flippo's exact identity from assets/images/flippo/flippo-idle.png and flippo-win.png (coral-orange rounded card, cream question mark, tiny red apple under the folded upper-right corner, indigo back card, navy face with blush, exactly two lavender mittens, two amber feet).

THE SCALE RULE (most important): describe the main subject as SMALL in a WIDE, open scene. State the size explicitly in your prompt using the numbers below, and say that the large open space around it is intentional.

1) name: A-universal — aspect 16:9. References: flippo-win.png, raw/A-universal-1.png (approved style and layout; only shrink the subject), blueprints/A-universal-16x9.jpg (correct scale).
Same scene as A-universal-1.png, but Flippo is SMALL: Flippo's full height, from the top of the indigo back card to the bottom of the feet, is about 28% of the image height. Flippo sits in the UPPER-MIDDLE of the image: top of Flippo at about 25% of the height, feet at about 53% of the height, horizontally centred. The fanned memory-card arcs are also smaller and sit at mid height on the far left and far right, roughly level with Flippo, and the lower sunflower arcs sit lower toward the bottom corners. The lower third of the image below Flippo is calm indigo with sparse sparkles only.

2) name: B-search — aspect 3:2. References: flippo-win.png, raw/B-search-1.png (approved style; only shrink and recentre), blueprints/B-search-3x2.jpg (correct scale).
Same content as B-search-1.png (dark navy board of 8 cards in 4 columns by 2 rows with exactly two matching yellow-sun cards face up, a gold stamp reading exactly "MATCH!" on the board's top edge, Flippo cheering at the board's right edge), but the WHOLE GROUP is SMALL and centred: the group spans only from about 25% to 75% of the image width and from about 32% (top of the stamp) to 68% (bottom of Flippo's feet) of the image height. Wide, open indigo #4b3fc9 space with sparse sparkles surrounds the group on all sides. "MATCH!" must be spelled character-perfect, and there is no other text.

3) name: C-header-spooky — aspect 21:9 ultra-wide. References: flippo-win.png, raw/C-header-spooky-1.png (approved style and world; only shrink the subject), blueprints/C-header-spooky-21x9.jpg.
Same Halloween scene as C-header-spooky-1.png (night purple, pumpkin-orange glow, Flippo with the small purple witch hat in the win pose, fanned ghost, bat and jack-o'-lantern cards left and right, glowing jack-o'-lanterns along the bottom, small bats), but Flippo is SMALL: Flippo's full height, from the tip of the witch hat to the feet, is about 36% of the image height, centred horizontally, with the hat tip at about 32% and the feet at about 68% of the image height. The fanned cards are smaller and sit level with Flippo near the left and right edges. Keep the area directly around Flippo open, with only glow and sparkles.

OWNERSHIP: you own only the six new files in .proposals/app-store-creative-assets/raw/.

OBSERVABLE ACCEPTANCE: raw/A-universal-2.png, raw/A-universal-3.png, raw/B-search-2.png, raw/B-search-3.png, raw/C-header-spooky-2.png, raw/C-header-spooky-3.png exist as unmodified tool output. Immediately after saving the sixth file send worker_done with --outcome succeeded (or failed, naming any missing file), and do nothing else afterwards.
