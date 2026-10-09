TARGET: "Zoom out" four approved App Store illustrations for DoMemory. Repository root: /Users/ezequielbrrt/Documents/personal/development/apps/domemory. The images are approved as they are (characters, poses, cards, lighting, colours, style), but their subjects are too big for the App Store, which shows only a central box on some devices.

CHANGE: For each of the four sources below, call the image generation tool TWICE (8 calls in total), passing the source image (plus assets/images/flippo/flippo-win.png for identity) as referenced images, and save the unmodified outputs to .proposals/app-store-creative-assets/raw/<name>-1.png and <name>-2.png.

THE INSTRUCTION FOR EVERY CALL: Recreate the source image faithfully, with the same scene, characters, poses, cards, icons, lighting, colours and rendering, but with the CAMERA PULLED BACK. Everything that is in the source frame now appears SMALLER, at about 65% of its current size, in the centre of the new frame. The newly visible area around it continues the same world naturally (more sky, clouds, soft out-of-focus cards, glow and sparkles), with no hard edges, no borders and no picture-in-picture. Same aspect ratio as the source, largest size allowed. Do not add new characters or any text. State the subject-size numbers below explicitly in the prompt, and say the large surrounding space is intentional.

SOURCES:
1) name: H2z-header-constellations — source .proposals/app-store-creative-assets/raw/H2-header-constellations-1.png — aspect 21:9. After zooming out, Flippo (top of the indigo back card to the feet) spans only about 37% to 63% of the image height and stays horizontally centred. The constellation-linked card pairs stay within about 27% to 73% of the width. The bottom 28% is soft clouds.
2) name: H1z-header-stage — source .proposals/app-store-creative-assets/raw/H1-header-stage-2.png — aspect 21:9. After zooming out, Flippo spans only about 37% to 63% of the image height, centred, standing on the stage; the face-up cards stay within about 27% to 73% of the width. The bottom 28% is the stage glow fading into dark indigo.
3) name: S2z-search-friends — source .proposals/app-store-creative-assets/raw/S2-search-friends-1.png — aspect 3:2. After zooming out, the whole group (both characters, the board, the high-five burst and the four floating theme cards) spans only about 27% to 73% of the image width and 34% to 66% of the image height, centred.
4) name: S1z-search-match — source .proposals/app-store-creative-assets/raw/S1-search-match-1.png — aspect 3:2. After zooming out, the board plus Flippo spans only about 27% to 73% of the image width and 34% to 66% of the image height, centred.

CONSTRAINTS:
- Do NOT call view_image or open, inspect, crop, resize or edit any image file yourself; only the generation tool produces the images.
- Write only the 8 new files. Do not edit other files and do not run git.
- Keep Flippo's exact identity (coral-orange rounded card, cream question mark, tiny red apple under the folded upper-right corner, indigo back card, navy face with blush, two lavender mittens, two amber feet) and the teal friend's (mint-teal card, cream question mark, small gold star under its folded corner).
- No text, no transparency.

OWNERSHIP: only H2z-header-constellations-1.png, H2z-header-constellations-2.png, H1z-header-stage-1.png, H1z-header-stage-2.png, S2z-search-friends-1.png, S2z-search-friends-2.png, S1z-search-match-1.png, S1z-search-match-2.png in .proposals/app-store-creative-assets/raw/.

OBSERVABLE ACCEPTANCE: all 8 files exist as unmodified tool output. Immediately after saving the eighth, send worker_done with --outcome succeeded (or failed, naming any missing file), and do nothing else.
