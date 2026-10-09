# App Store Connect upload: dedicated header H2 and search S1, version 4.5.0

- **App:** DoMemory (1533115091).
- **Version:** 4.5.0 (`4f3eba12-d9c6-4bfa-b095-6bd78efbb8f1`), Prepare for Submission.
- **Replaces:** the universal asset A on both slots in every locale. See `upload-A-4.5.0.md`.

## Files and library images

Uploaded 2026-10-06 with `asc asset-library images upload` (asc 5.12.1). Both were processed and are `PREPARE_FOR_SUBMISSION`.

| Final | MD5 | Library image | Apple spec |
|---|---|---|---|
| `final/H2-header-constellations-3840x1646.png` | `072eb4055eefe68023043d7c2016dac3` | `a6c00005-b617-8ad3-8008-62e41b4c4340` | `1eb43b80-…` 3840 × 1646, 21:9, product page header |
| `final/S1-search-match-3840x2560.png` | `7d3ee7e25803222c1578913c123ac429` | `ddc00005-b617-8ad3-8018-9cbfbdfc7abb` | `d2ffa3b7-…` 3:2, search results |

## Swap

- **How:** per locale and slot, A's placement was removed (`asc localizations placements delete --confirm`), then the new image was assigned (`asc localizations placements create`).
- **Locales:** de-DE, en-US, es-MX, fr-FR, hi, it, ja, ko, pt-BR, zh-Hans.
- **Errors:** none; there were no retries.
- **Before-and-after IDs:** A's removed placement IDs are in the session scratchpad's `A_placements_before_swap.json`.

## Verified afterwards

- **Per locale:** every one of the 10 has exactly one `PRODUCT_PAGE_HEADER_ASSET` → H2 and one `APP_STORE_SEARCH_RESULTS_ASSET` → S1, both `ACTIVE`.
- **Library counts:** H2 has 10 placements, S1 has 10, and A has 0.
- **A stays in the library:** it's unassigned and can be reassigned at any time.

## Not done (App Store Connect web UI)

- **Preview:** check both in Asset Library's product page preview on iPhone and iPad.
- **Submit for review:** either a standalone Asset Library submission, or with the 4.5.0 version.
- **A:** still `PREPARE_FOR_SUBMISSION` in the library. If it's never going to be used, don't include it in the submission.
