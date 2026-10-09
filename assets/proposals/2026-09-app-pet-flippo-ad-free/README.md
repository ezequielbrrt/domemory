# Flippo ad-free day poses

Reference: `assets/images/flippo/flippo-idle.png` (exact character identity and style). Both poses were generated in one Codex session (Orca run `run_fb15208e5553`) with the image tool, one generation each, using the idle sprite as the referenced image. `flippo-sorry-raw.png` and `flippo-no-ads-raw.png` preserve the returned 1254 x 1254 RGBA images; `assets/images/flippo/flippo-sorry.png` and `flippo-no-ads.png` frame them at 834 x 880 like the rest of the family (character scaled to 800 px tall, top edge at y = 40, centred horizontally), with isolated alpha values below 16 dropped to remove generation speckles. `compare.png` shows pause, sorry and no-ads side by side.

Used by `AdFreeDayArt` in `ios/DoMemory/DoMemory/Modules/AdFreeDay/AdFreeDayOfferView.swift`: the sorry pose on the first (apologetic) open of the ad-free day sheet, the no-ads pose on return visits and once the day is granted.

## Prompts

Each prompt used the shared boilerplate from `.proposals/app-pet-flippo-pause/README.md` (stylized-concept, DoMemory mascot sprite, exact identity clause, square canvas with 10% padding, sticker style, true alpha, no text) around the pose text below.

Follow `.proposals/app-pet-flippo-pause/README.md`: use
`assets/images/flippo/flippo-idle.png` as the identity reference, keep the
same boilerplate (chibi sticker, cel shading, transparent square canvas, 10%
padding, no text), frame the result at 834 × 880.

**FlippoSorry** — "Give Flippo a sheepish, apologetic expression: eyebrows
raised in the middle, small closed-mouth smile, soft pink blush. Both lavender
mittens pressed together in front of the card body at chest height, feet
turned slightly inward. Add only a TINY cue: two small sweat-drop marks beside
the upper-left corner, each smaller than one eye. Flippo is warm and humble,
NOT sad, NOT crying."

**FlippoNoAds** — "Give Flippo a proud, relieved grin with bright open eyes.
Flippo holds up in one lavender mitten a small rounded sign, the sign no
taller than Flippo's face, showing a simple rectangle 'ad' glyph with a
diagonal coral strike-through — NO letters or words on it. The other mitten
rests on the hip. Add only a TINY sparkle above the sign, smaller than one
eye. Flippo is cheerful and confident."
