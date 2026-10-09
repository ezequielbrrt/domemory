# ko iPad App Store screenshots

Seven localized portrait screenshots for the 12.9/13-inch iPad slot. The approved en-US iPad screenshots set the composition; approved localized iPhone screenshots set the wording and headline treatment. Each final is RGB JPEG, 2048 × 2732, quality 94. The whole 1086 × 1448 generation was resized with LANCZOS to 2048 × 2731, and its bottom row was repeated once to reach the target height.

| Shot | Generation attempts | Fallback | Remaining flaw |
|---|---:|---|---|
| 01 hero flip and match | 2 | None | None observed |
| 02 win celebration | 1 | None | None observed |
| 03 meet Flippo | 1 | None | None observed |
| 04 levels | 1 | None | None observed |
| 05 multiplayer | 3 | Image-generated text clean plate and Apple SD Gothic Neo Bold typesetting of the right ready chip | None observed |
| 06 create your own | 1 | None | None observed |
| 07 daily themes | 1 | None | None observed |

Raw generations and localized UI crops are retained in `raw/`. The hero uses the approved dark iPad game board with two gold face-up cards. The `NEW` pill remains English on shots 4 and 5. Text and visual placement were checked against `localized-copy.json`, the localized iPhone art, and the supplied UI crops at full generated size.

Shot 05 needed the fallback after three generation passes: the first added unwanted UI labels, and the next two rendered `Mia` as `mia` in the right ready chip. An image-generation edit erased only that chip lettering to a clean plate; `Mia · 준비 완료` was then typeset in Apple SD Gothic Neo Bold.
