# fr-FR App Store screenshot localization

Six direct image-generation edits of the approved en-US masters and one image-generation clean-plate fallback were selected. Each edit used the matching master as reference 1 and the exact locale copy from `../../localized-copy.json`; shots 4–7 also used a crop of the shipped localized UI panel from `assets/archive/2026-10/screenshots/6.9/fr-FR/`. The raw generations and UI crops are retained in `raw/`.

| Shot | Image-generation attempts | Selected raw | Fallback | Remaining text flaw |
|---|---:|---|---|---|
| 01 `01_hero_flip_match` | 2 | `raw/01-2.png` | No | None seen |
| 02 `02_win_celebrate` | 1 | `raw/02-1.png` | No | None seen |
| 03 `03_meet_flippo` | 1 | `raw/03-1.png` | No | None seen |
| 04 `04_levels` | 1 | `raw/04-1.png` | No | None seen |
| 05 `05_multiplayer` | 3 | `raw/05-fallback.png` | Yes, image-generated clean plate and deterministic eyebrow type | None seen |
| 06 `06_create_your_own` | 1 | `raw/06-1.png` | No | None seen |
| 07 `07_daily_themes` | 1 | `raw/07-1.png` | No | None seen |

Full-size inspection found no remaining English headline ghosts, smudge patches, missing characters, or cropped headline lines. The English NEW pills on shots 4 and 5 and the win-card values 7, 18s and 1 were retained. Finals were fitted to 1290 × 2796 without distortion using LANCZOS on indigo `#4b3fc9` and exported as RGB JPEG at quality 95 with 4:4:4 chroma.

Shot 05 had three image-generation text attempts whose eyebrow rendered a lowercase-looking initial m. An image-generated clean plate of the en-US master was retained as `raw/05-clean-plate.png`; an image-generated localized clean plate removed the eyebrow without a patch, and the uppercase eyebrow was typeset on that plate. A later chip-removal generation introduced transparency artifacts and was rejected (`raw/05-chip-clean-rejected.png`).
