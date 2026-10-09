TARGET: Regenerate only the spooky product-page header once more, smaller still. Repository root: /Users/ezequielbrrt/Documents/personal/development/apps/domemory. Approved style and world: .proposals/app-store-creative-assets/raw/C-header-spooky-3.png (keep everything about it: night-purple sky, pumpkin-orange glow, haunted houses and trees at the far sides, fanned ghost, bat and jack-o'-lantern cards left and right, glowing jack-o'-lanterns along the bottom, Flippo with the small purple witch hat in the win pose). The only problem: Flippo is still too big. The App Store shows only a short central band of this ultra-wide header on some devices.

CHANGE: Call the image generation tool TWICE (two variants), aspect 21:9 ultra-wide at the largest size allowed, references assets/images/flippo/flippo-win.png and .proposals/app-store-creative-assets/raw/C-header-spooky-3.png. Save the unmodified outputs as .proposals/app-store-creative-assets/raw/C-header-spooky-4.png and C-header-spooky-5.png.

SCALE (state these numbers explicitly in the prompt): Flippo including the witch hat is TINY relative to the frame, only about 28% of the image height: hat tip at about 36% of the image height, feet at about 64%, horizontally centred, floating above the ground in the glow. The ground line and the pumpkin row stay in the bottom quarter, well below Flippo's feet. The fanned card groups are smaller too, at mid height near the left and right edges. Wide open glowing sky around Flippo is intentional.

CONSTRAINTS: Keep Flippo's exact identity (coral-orange rounded card, cream question mark, tiny red apple under the folded upper-right corner, indigo back card, navy face with blush, exactly two lavender mittens, two amber feet). No text, no transparency. Do NOT call view_image or open, inspect or edit any image. Write only the two new files. Do not run git.

OWNERSHIP: only raw/C-header-spooky-4.png and raw/C-header-spooky-5.png.

OBSERVABLE ACCEPTANCE: both files exist as unmodified tool output. Immediately after saving the second, send worker_done with --outcome succeeded (or failed, naming any missing file) and do nothing else.
