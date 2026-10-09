TARGET: Landscape and square versions of five approved Spanish Google Ads illustrations for DoMemory. Repository root: /Users/ezequielbrrt/Documents/personal/development/apps/domemory. The five portrait sources are approved (character, scene, colours, style, and their Spanish text); Google Ads also needs each one as a 16:9 landscape and as a 1:1 square.

CHANGE: For each of the five sources below, call the image generation tool TWICE (10 calls in total): once with aspect 16:9 (landscape) and once with aspect 1:1 (square), largest size allowed, passing the portrait source AND assets/images/flippo/flippo-idle.png as referenced images. Save the unmodified outputs to .proposals/google-ads-es/raw/<nn>-landscape.png and .proposals/google-ads-es/raw/<nn>-square.png.

THE INSTRUCTION FOR EVERY CALL: Recreate the source illustration in the new aspect ratio with the same character, scene, phone, cards, colours, lighting and rendering style, and the SAME Spanish text, spelled EXACTLY as given below, in the same bold rounded display lettering and colours as the source (headline in large white/yellow/green capitals as in the source; subline smaller in white). Put the text in the prompt verbatim and say it must be spelled exactly, with the accents and ¿ ? marks. Layout:
- LANDSCAPE (16:9): the headline and subline on the LEFT 40% of the frame, left-aligned, vertically centred; the scene (Flippo, phone, cards) on the RIGHT 60%. Keep all text and the character inside the central 90% of the frame (a 5% margin on every side).
- SQUARE (1:1): the headline at the top, centred, taking the top 30%; the scene below it filling the rest. Same 5% margin.
Deep indigo-violet background (#4B3FC8 family) with the soft darker shapes and yellow/colour sparkle accents of the source. No new characters, no new text, no app store badges, no logos, no watermark.

SOURCES (text to reproduce exactly):
1) nn: 01 — source assets/google-ads-es/01-voltea-empareja-gana.png — headline "VOLTEA. EMPAREJA. GANA." (white, yellow, green words) — subline "Un nuevo reto de memoria cada día." — scene: Flippo beside a phone showing a memory board with cat, dog, apple, penguin cards.
2) nn: 02 — source assets/google-ads-es/02-tu-baraja-tus-reglas.png — headline "TU BARAJA. TUS REGLAS." (white, then mint green) — subline "Crea un memorama con tus emojis favoritos." — scene: Flippo beside a phone showing an emoji picker grid, with floating emoji cards.
3) nn: 03 — source assets/google-ads-es/03-reta-a-un-amigo.png — headline "RETA A UN AMIGO." (white, then yellow) — subline "¿Quién encuentra todas las parejas primero?" — scene: Flippo between two phones showing a two-player match (scores 8 and 6), confetti.
4) nn: 04 — source assets/google-ads-es/04-sube-al-siguiente-nivel.png — headline "SUBE AL SIGUIENTE NIVEL." (white, then yellow) — subline "Un tablero nuevo cada vez que juegas." — scene: Flippo beside a phone, a winding path of numbered level tiles 1 to 5, stars and hearts.
5) nn: 05 — source assets/google-ads-es/05-un-tema-nuevo.png — headline "UN TEMA NUEVO PARA EMPAREJAR." (white, then yellow) — subline "Encuentra tu favorito entre decenas de barajas." — scene: Flippo pointing at a phone, surrounded by pairs of themed cards (lions, apples, rockets, leaves, footballs).

FLIPPO IDENTITY (keep exactly): a coral-orange rounded playing card with a cream question mark, a tiny red apple under the folded upper-right corner, a dark indigo back card behind him, a navy face with big eyes and pink blush, two lavender mitten hands, two amber feet.

CONSTRAINTS:
- Do NOT call view_image or open, inspect, crop, resize or edit any image file yourself; only the generation tool produces the images.
- Write only the 10 new files. Do not edit other files and do not run git.
- If a call fails, retry that call once; then continue with the others.

OWNERSHIP: only 01-landscape.png, 01-square.png, 02-landscape.png, 02-square.png, 03-landscape.png, 03-square.png, 04-landscape.png, 04-square.png, 05-landscape.png, 05-square.png in .proposals/google-ads-es/raw/.

OBSERVABLE ACCEPTANCE: all 10 files exist as unmodified tool output. Immediately after saving the tenth, send worker_done with --outcome succeeded (or failed, naming any missing file), and do nothing else.
