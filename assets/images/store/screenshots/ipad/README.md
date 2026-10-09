# DoMemory en-US iPad screenshot proposal

Seven portrait compositions for the 12.9/13-inch iPad App Store slot. Each selected generation references the corresponding approved iPhone master plus the specified Flippo pose. Selected and superseded raw generations are retained under `en-US/raw/`. Final JPEGs are RGB, 2048 × 2732, quality 94; the generated 1086 × 1448 images were scaled proportionally with LANCZOS and placed on matching indigo background to meet the exact canvas without distortion.

| Shot | Attempts | Selected raw | Remaining flaw |
|---|---:|---|---|
| 01 Hero flip and match | 3 | `raw/01-final.png` | Minor game UI lettering and card proportions are generated recreation. |
| 02 Win celebration | 2 | `raw/02-final.png` | Tiny modal copy may need app-source verification. |
| 03 Meet Flippo | 2 | `raw/03-final.png` | None apparent at full-size visual review. |
| 04 Levels | 2 | `raw/04-final.png` | Tiny level-map UI details are generated recreation. |
| 05 Multiplayer | 2 | `raw/05-final.png` | Tiny VS panel copy may need app-source verification. |
| 06 Create your own | 2 | `raw/06-final.png` | Tiny deck-builder emoji labels are generated recreation. |
| 07 Daily themes | 2 | `raw/07-final.png` | Theme-panel microcopy is generated; no stray outer label apparent. |

## Prompts

All prompts use the exact en-US localized copy in `../localized-copy.json`, use the approved iPhone master as reference 1 and Flippo pose art as reference 2, and request a richly illustrated 3:4 iPad recomposition with indigo glow/sparkles, heavy rounded headlines, frosted panels, balanced width, large Flippo where present, fully contained speech bubbles, bottom captions, and no extra labels. After the first pass showed inset artwork, every second-pass prompt explicitly required edge-to-edge background, headline within 5% of the top, no inset poster/frame, and no empty top or bottom bands.

1. **01 hero flip and match** — Master `01_hero_flip_match.jpg`; pose `flippo-win.png`. Generic thin-bezel portrait iPad with no logo and dark-mode DoMemory board; centered purple portrait cards and × / red mistakes / timer / pause top bar; two gold sun cards face-up, gold MATCH! stamp, large Flippo win pose lower-left and attached “Find the pairs!” bubble. Copy: “MEET FLIPPO”; “FLIP &” / “MATCH.”; “MATCH!”; “Clear every pair before the timer runs out”.
2. **02 win celebration** — Master `02_win_celebrate.jpg`; pose `flippo-win.png`. Generic portrait iPad showing the purple-card game board, dim only the screen behind a dark win modal; keep page background and headline bright. Modal with Flippo, two gold stars and one empty star, three stat tiles and lavender Next Level button. Copy: “LEVEL CLEARED”; “CELEBRATE” / “EVERY WIN.”; “Level Cleared!”; “Level 12”; “7 pairs”; “18s remaining”; “1 errors”; “Next Level”; “Back to levels”; “Flippo cheers every level you clear”.
3. **03 meet Flippo** — Master `03_meet_flippo.jpg`; pose reference `flippo-win.png`. Four large frosted mood tiles in a 2 × 2 grid, each with the respective shipped Flippo mood pose. Copy: “MEET YOUR CARD BUDDY”; “FLIPPO FEELS” / “EVERY FLIP.”; “Nailed it!”; “Out of time!”; “Oops, not a pair”; “Quick break”; “Your card buddy reacts to every win, slip and pause”.
4. **04 levels** — Master `04_levels.jpg`; pose `flippo-idle.png`. Wide readable level-map frosted panel with hearts, star count, levels 7, 8, 9 and a locked level; large idle Flippo below and attached “Level 10 next!” bubble. Copy: “LEVELS”; “CLIMB” / “FOREVER.”; “A fresh board every level, each one a little harder”.
5. **05 multiplayer** — Master `05_multiplayer.jpg`; pose `flippo-idle.png`. Friends scene card above a multiplayer VS panel reading “You 6 · VS · Mia 5” and “Your turn — you lead by 1!”, plus mint chips “You ready” and “Mia ready”; no deck builder. Copy: “NEW”; “MULTIPLAYER”; “BATTLE” / “A FRIEND.”; “Host a room, share the code, play in real time”.
6. **06 create your own** — Master `06_create_your_own.jpg`; pose `flippo-idle.png`. Preserve create scene card and wide deck-builder panel with colorful real emoji cards. Copy: “YOUR DECK, YOUR RULES”; “CREATE” / “YOUR OWN.”; “Pick any emoji and build a memorama all your own”.
7. **07 daily themes** — Master `07_daily_themes.jpg`; pose `flippo-idle.png`. Preserve daily scene card and large theme-grid panel fading out at the bottom; no extra labels. Copy: “DAILY CHALLENGE”; “A NEW BOARD” / “EVERY DAY.”; “Keep your streak, then explore dozens of themes”.

## Coordinator fix

The worker pasted each 1086 x 1448 generation unscaled into the centre of the 2048 x 2732 canvas, leaving a wide flat border. The coordinator re-derived all seven finals from `raw/0N-final.png`: a LANCZOS resize to 2048 x 2731 (3:4), plus the bottom row repeated once to reach 2732. The English captions carry a trailing full stop that localized-copy.json does not have; this was kept as a known minor difference.
