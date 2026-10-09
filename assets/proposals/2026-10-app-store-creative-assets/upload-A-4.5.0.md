# App Store Connect upload: universal creative asset A, version 4.5.0

- **App:** DoMemory (1533115091).
- **Version:** 4.5.0 (`4f3eba12-d9c6-4bfa-b095-6bd78efbb8f1`), Prepare for Submission.
- **File:** `final/A-universal-header-search-5244x2950.png`
  - 5244 × 2950 PNG, RGB, 4,035,402 bytes.
  - MD5 `d5d827ca67981379d1e757809141874e`.

## Upload

- **Uploaded:** 2026-10-06 19:20 UTC with `asc asset-library images upload --library-id 1533115091` (asc 5.12.1).
- **Timeout, not a failure:** the CLI's wait for processing timed out, but the upload itself had completed. It wasn't re-uploaded.
- **Processing:** Apple finished at about 19:37 UTC.
- **Library image:** `71c00005-b617-8ad3-8007-72a19cef32a4`.
  - Category: `CREATIVE_ASSETS`.
  - Spec: `c8f4e2b1-7a3d-5c9e-8b6f-2d1a0e9c8b7a` (5244 × 2950, valid for the search results and product page header placements).
  - State: `PREPARE_FOR_SUBMISSION`.

## Placements

- **Command:** `asc localizations placements create`.
- **What was assigned:** `PRODUCT_PAGE_HEADER_ASSET` and `APP_STORE_SEARCH_RESULTS_ASSET` (group `DEFAULT_PROFILE`) on every 4.5.0 localization: de-DE, en-US, es-MX, fr-FR, hi, it, ja, ko, pt-BR, zh-Hans.
- **One server error:** pt-BR search returned HTTP 500. A list call showed nothing had been created, so it was retried once and succeeded.
- **Verified after assignment:**
  - Each of the 10 localizations has exactly one header and one search placement.
  - Both are `ACTIVE` and point to image `71c00005-…32a4`.
  - The library image reports 20 placements.

## Not done (App Store Connect web UI)

- **Submit for review:**
  - Either submit the image on its own from Asset Library (a standalone submission, reviewed against the latest live version).
  - Or it goes in with the 4.5.0 version submission.
- **Visible only after approval:** nothing shows on the store until the image is approved.
- **Preview first:** check the crop in Asset Library's product page preview on iPhone and iPad.
