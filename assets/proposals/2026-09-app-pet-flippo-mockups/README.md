# Flippo in DoMemory: static placement mockups

These images are visual proposals, composed with Pillow from the approved Flippo sprites in `.proposals/app-pet-v2/`. They do not depict shipped screens or change app behavior. The win and notification mockups are 945 × 2048 phone views; the four-frame loading sheet is 1024 × 256.

## Win modal — `mockup-win-modal.png`

This shows Flippo's celebration pose inside a cream rounded result card, over a dimmed crop of the real gameplay screenshot at `assets/screenshots/6.9/en-US/01_hero_gameplay.jpg`. The title, match count, time, misses, share action, and coral Continue button make the art part of a plausible completion flow. The corresponding app view is `ios/DoMemory/DoMemory/Modules/Memorize/MemorizeView/Modals/WinModal/WinModal.swift`, whose current modal includes a celebration graphic, result stats, sharing, and a primary action. The text and figures here are placeholders; no new art prompt was needed because the composite uses `.proposals/app-pet-v2/pet2-a-celebrate.png`.

## Streak notification — `mockup-streak-notification.png`

This explores Flippo's idle pose as a small media thumbnail inside a generic iOS-style notification card. It includes the real app icon thumbnail from `assets/my-video/public/assets/domemory-app-icon.png`, a generic time stamp, and placeholder reminder copy over an abstract purple lock-screen background; no personal content or real device chrome is shown. The notification preview and permission rationale live in `ios/DoMemory/DoMemory/Services/Notifications/NotificationPrimerContent.swift`, while the streak-at-risk reminder is scheduled by `ios/DoMemory/DoMemory/Services/Notifications/NotificationService.swift`. The wording is exploratory and would require product/localization review before implementation. The thumbnail uses `.proposals/app-pet-v2/pet2-a-idle.png`; no new art prompt was needed.

## Card-flip loader — `mockup-loading-spinner.png`

This four-frame sheet tests whether Flippo's own card-body flip can become a loading motion: question-mark front, narrow edge, indigo back, narrow edge, then a loop to the front. The static source for the front and edge frames is `.proposals/app-pet-v2/pet2-a-idle.png`; Pillow crops and horizontally narrows the source for the edge views. The back-facing art was generated from the same idle sprite as the exact identity reference. This is a general loading-state concept that could replace or accompany `ios/DoMemory/DoMemory/Modules/SharedModules/Views/LoaderView.swift`; timing and animation implementation are outside this proposal.

**Back-view image-generation prompt:** “Use the provided Flippo mascot sprite as exact identity and illustration style reference. Render the SAME full-body living memory-card character viewed from directly behind for one frame of a card-flip loading animation. Visible body: indigo/navy rounded card back with a subtle lighter purple inset and a small centered coral rounded-card motif; lavender mitten arms attached at sides and amber feet below, matching the original scale and proportions. No face, no question mark, no apple because this is the reverse side of the same card; preserve friendly 2D sticker shading, rounded corners, and clean dark-navy details. Center full body with generous clear margin on a TRUE transparent alpha PNG, no floor, shadow, background, text, labels, logo, or confetti.”

The back view appears only in the loading sheet; the two original Flippo sprite files remain untouched.
