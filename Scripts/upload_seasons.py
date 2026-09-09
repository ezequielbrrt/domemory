#!/usr/bin/env python3
"""Build the DoMemory season catalog from JSON and upload it to Firebase Realtime Database.

The canonical catalog lives in ``seasons.json``, an object keyed by season id —
exactly the shape of the ``/seasons`` node the app reads. The script validates
every season against the same rules the app applies when decoding one, writes
``seasons_data.json`` (the payload as uploaded), and replaces the ``/seasons``
node with the full catalog.

``/seasons`` is a *dictionary*, not an array like ``/data``: the key is the
season id, so ``id`` never appears in a season's body (``SeasonCatalogService``
injects the key before decoding). Any ``id`` you write in a body is dropped.

Because the upload *replaces* the node, ``seasons.json`` is canonical: a season
you delete from the file disappears from Firebase on the next upload. To retire
a season without losing it, flip its kill switch instead — see below.

PUBLISHING PREREQUISITE — DO THIS BEFORE UPLOADING A SEASON
-----------------------------------------------------------
``firebase-database.rules.json`` (repo root) grants ``/seasons`` a ``.read``
rule, but **committing that file does not publish it.** Publishing is a manual
Firebase CLI or console step that, as of this writing, has NOT been done:

    firebase deploy --only database        # from the repo root

Until the rules are live, every client's ``/seasons`` read is denied, the app
takes its no-season path, and the feature simply never appears. That path fails
closed safely — nothing breaks, no error is shown — which is exactly why this is
easy to miss: uploading a season before the rule is live produces a season that
nobody can read and no crash to tell you so. Check the published rules first.

Kill switch
-----------
``"enabled": false`` on a season removes it from the app on the next menu load,
with **no app release**. The menu reads ``/seasons`` once per launch
(``MenuView`` -> ``SeasonCatalogService.load()``), and a disabled season is
filtered out of the active-season selection, so the Daily Challenge card returns
to its full-width layout and no season UI appears anywhere. The season's per-user
progress is namespaced by season id and is left untouched, so re-enabling the
same id later resumes players where they left off.

``enabled`` is absent-means-false: a season with no ``enabled`` key never
appears. This script warns rather than guessing when the key is missing.

Examples
--------
    # Validate + preview only, no upload, no credentials needed (safe anytime):
    python3 upload_seasons.py --dry-run

    # Validate a season you are drafting elsewhere:
    python3 upload_seasons.py --seasons /tmp/draft.json --dry-run

    # Publish the canonical catalog:
    python3 upload_seasons.py --credentials /path/to/serviceAccount.json

Credentials
-----------
``/seasons`` is read-only for app clients (see firebase-database.rules.json), so
uploading needs admin access via a service-account key:

    1. Firebase console -> Project settings -> Service accounts -> Generate new
       private key. Save the JSON somewhere private (do NOT commit it).
    2. Pass it with ``--credentials path.json`` or set the env var
       ``GOOGLE_APPLICATION_CREDENTIALS=path.json``.

Requires ``firebase-admin`` for the actual upload (``--dry-run`` does not):

    pip install firebase-admin

Season fields
-------------
    enabled      optional bool, absent means false. The kill switch.
    startDate    required "YYYY-MM-DD", inclusive first day.
    endDate      required "YYYY-MM-DD", inclusive last day, >= startDate.
    priority     optional int, default 0. Highest wins when seasons overlap.
    levelCount   required int >= 1. A season is finite.
    icon         optional string, the menu card glyph.
    accentColor  optional "#RRGGBB".
    emojiPool    required array of strings, >= 12 *distinct* entries.
    strings      locale code -> {title, subtitle}; needs a usable "en" entry.

Every rule above is restated from ``Season.init(from:)`` in
``DoMemory/DoMemory/Services/Seasons/Season.swift``. When that file changes,
change this one.
"""

import argparse
import datetime
import json
import os
import re

DEFAULT_DATABASE_URL = "https://domemory-c9211.firebaseio.com"
SEASONS_PATH = "/seasons"

# CROSS-REFERENCE — keep in sync with Swift.
#
# The app derives this floor as `Season.minimumEmojiPoolSize`, which is
# `LevelCurve.pairs(for: .max)` — the largest board the curve will ever ask a
# season to deal. `LevelCurve.pairAnchors` currently tops out at 12 pairs
# (`DoMemory/DoMemory/Services/Seasons/Season.swift:75`,
#  `DoMemory/DoMemory/Services/Levels/LevelCurve.swift:15`), so a season with a
# smaller pool cannot fill its own late boards and the app rejects it outright.
#
# Retuning `pairAnchors` moves the Swift constant automatically. Python cannot
# import Swift, so this number has to be typed out and will NOT follow. If you
# change the curve's last anchor, change this too — `Season.swift` carries the
# matching pointer back here.
MINIMUM_EMOJI_POOL_SIZE = 12

# Recognized season fields. "id" is listed so it is reported as ignored rather
# than as an unknown key; the dictionary key is the id.
KNOWN_KEYS = {
    "id", "enabled", "startDate", "endDate", "priority",
    "levelCount", "icon", "accentColor", "emojiPool", "strings",
}

# Realtime Database forbids these in a key, so they cannot appear in a season id.
ILLEGAL_KEY_CHARS = ".$#[]/"

ACCENT_COLOR_PATTERN = re.compile(r"^#[0-9A-Fa-f]{6}$")

HERE = os.path.dirname(os.path.abspath(__file__))


def day_key(iso_day):
    """``"2026-10-01"`` -> ``"20261001"``; None for anything not a strict YYYY-MM-DD.

    Mirrors ``Season.dayKey(fromISODay:)``: exactly three "-"-separated parts of
    4, 2 and 2 ASCII digits. The digit test is deliberately a literal 0-9
    membership check and not ``str.isdigit()``, which also accepts superscripts
    and non-ASCII digits that Swift's ``isASCII && isNumber`` pair rejects.

    Like the Swift version, this checks the *shape* only. Whether the date
    exists on a calendar is checked separately by ``is_real_day``.
    """
    parts = iso_day.split("-")
    if [len(part) for part in parts] != [4, 2, 2]:
        return None
    if not all(character in "0123456789" for part in parts for character in part):
        return None
    return "".join(parts)


def is_real_day(key):
    """Whether a ``YYYYMMDD`` day key names a date that exists.

    Stricter than the app on purpose. ``Season.dayKey(fromISODay:)`` accepts
    "2026-13-01" and the season then simply never activates, because no day key
    the player's calendar produces can fall inside that window. The app fails
    closed and silently; a seeding script is the right place to catch it loudly.
    A date this rejects would never have worked, so nothing valid is refused.
    """
    try:
        datetime.date(int(key[0:4]), int(key[4:6]), int(key[6:8]))
    except ValueError:
        return False
    return True


def normalize_locale_key(key):
    """Mirrors ``Season.normalizedLocaleKey``: ``es_419`` and ``ES-419`` -> ``es-419``."""
    return key.replace("_", "-").lower()


def parse_season(season_id, body, source, warnings):
    """Validate one child of /seasons, returning its upload body.

    Raises ValueError on data the app would reject. Anything the app tolerates
    but that is probably a mistake is appended to ``warnings`` instead.
    """
    if not isinstance(body, dict):
        raise ValueError(
            f'{source}: season "{season_id}": expected an object, got {type(body).__name__}'
        )

    errors = []

    unknown = sorted(set(body) - KNOWN_KEYS)
    if unknown:
        warnings.append(
            f'season "{season_id}": unrecognized field(s) {unknown}; not uploaded. '
            'Check for a typo — a misspelled "enabled" leaves the season switched off.'
        )
    if "id" in body:
        warnings.append(
            f'season "{season_id}": "id" in the body is ignored and not uploaded; '
            "the dictionary key is the id."
        )

    # enabled — the kill switch. Absent means false in the app, so say so rather
    # than quietly defaulting and letting a season be published into invisibility.
    enabled = body.get("enabled")
    if "enabled" not in body:
        warnings.append(
            f'season "{season_id}": no "enabled" key. The app treats an absent "enabled" '
            'as false, so this season will never appear. Add "enabled": true to publish it.'
        )
    elif not isinstance(enabled, bool):
        errors.append('"enabled" must be true or false')
    elif not enabled:
        warnings.append(
            f'season "{season_id}": "enabled" is false — the kill switch is on and the '
            "season stays hidden."
        )

    # startDate / endDate — strict YYYY-MM-DD, inclusive, ordered.
    day_keys = {}
    for field in ("startDate", "endDate"):
        value = body.get(field)
        if not isinstance(value, str):
            errors.append(f'"{field}" is required and must be a "YYYY-MM-DD" string')
            continue
        key = day_key(value)
        if key is None:
            errors.append(f'"{field}" {value!r} is not a strict YYYY-MM-DD date')
        elif not is_real_day(key):
            errors.append(f'"{field}" {value!r} is not a real calendar date')
        else:
            day_keys[field] = key
    if len(day_keys) == 2 and day_keys["endDate"] < day_keys["startDate"]:
        errors.append(
            f'"endDate" {body["endDate"]} is before "startDate" {body["startDate"]}; '
            "the window would never open"
        )

    # priority — optional, default 0. bool is an int in Python but not in JSON.
    priority = body.get("priority")
    if "priority" in body and (isinstance(priority, bool) or not isinstance(priority, int)):
        errors.append('"priority" must be a whole number')

    # levelCount — required, >= 1. A season is finite.
    level_count = body.get("levelCount")
    if isinstance(level_count, bool) or not isinstance(level_count, int):
        errors.append('"levelCount" is required and must be a whole number')
    elif level_count < 1:
        errors.append(f'"levelCount" is {level_count}; a season needs at least one level')

    # icon / accentColor — optional presentation, with app-side fallbacks.
    for field in ("icon", "accentColor"):
        if field in body and not isinstance(body[field], str):
            errors.append(f'"{field}" must be a string when present')
    accent_color = body.get("accentColor")
    if isinstance(accent_color, str) and not ACCENT_COLOR_PATTERN.match(accent_color):
        warnings.append(
            f'season "{season_id}": accentColor {accent_color!r} is not "#RRGGBB"; '
            "the app falls back to its default accent."
        )

    # emojiPool — deduplicated first, and the DISTINCT count is what must clear
    # the floor. A repeated emoji would deal two identical pairs (four matching
    # cards), which the matching rules cannot resolve, so the app drops repeats
    # and empties before measuring. 11 distinct + 1 repeat is 11, not 12.
    raw_pool = body.get("emojiPool")
    pool = []
    if not isinstance(raw_pool, list) or not all(isinstance(item, str) for item in raw_pool):
        errors.append('"emojiPool" is required and must be an array of strings')
    else:
        seen = set()
        for emoji in raw_pool:
            if not emoji or emoji in seen:
                continue
            seen.add(emoji)
            pool.append(emoji)
        dropped = len(raw_pool) - len(pool)
        if dropped:
            warnings.append(
                f'season "{season_id}": emojiPool lists {len(raw_pool)} entries but only '
                f"{len(pool)} are distinct; {dropped} empty or duplicate entries dropped, "
                "matching the app. The deduplicated pool is what gets uploaded."
            )
        if len(pool) < MINIMUM_EMOJI_POOL_SIZE:
            errors.append(
                f"emojiPool has {len(pool)} distinct emoji; {MINIMUM_EMOJI_POOL_SIZE} are "
                "required to fill its late boards"
            )

    # strings — locale code -> {title, subtitle}. A single entry missing its
    # title fails the whole season in the app, because the strings map is
    # decoded as one value.
    raw_strings = body.get("strings", {})
    if not isinstance(raw_strings, dict):
        errors.append('"strings" must be an object of locale -> {title, subtitle}')
    else:
        by_normalized_locale = {}
        for locale, text in raw_strings.items():
            if not isinstance(text, dict) or not isinstance(text.get("title"), str):
                errors.append(
                    f'strings["{locale}"] needs a "title" string; without one the whole '
                    "season fails to decode, not just that locale"
                )
                continue
            if "subtitle" in text and not isinstance(text["subtitle"], str):
                errors.append(f'strings["{locale}"].subtitle must be a string')
            normalized = normalize_locale_key(locale)
            if normalized in by_normalized_locale:
                errors.append(
                    f'strings has two entries for locale "{normalized}" '
                    f"({by_normalized_locale[normalized]!r} and {locale!r}); the app matches "
                    "locale keys case-insensitively and would pick between them at random"
                )
                continue
            by_normalized_locale[normalized] = locale

        english_key = by_normalized_locale.get("en")
        english = raw_strings.get(english_key) if english_key else None
        if not english or not english.get("title", "").strip():
            errors.append(
                'strings has no "en" entry with a non-empty title; the app\'s locale chain '
                'always ends at "en" before the fallback constant, so most of the world '
                "would see the fallback title instead of this season's name"
            )

    if errors:
        raise ValueError(f'{source}: season "{season_id}": ' + "; ".join(errors))

    # Rebuild the body from validated fields so the uploaded node is exactly
    # what the app decodes: no unknown keys, no redundant "id", pool deduplicated.
    season = {}
    if "enabled" in body:
        season["enabled"] = enabled
    season["startDate"] = body["startDate"]
    season["endDate"] = body["endDate"]
    if "priority" in body:
        season["priority"] = priority
    season["levelCount"] = level_count
    if "icon" in body:
        season["icon"] = body["icon"]
    if "accentColor" in body:
        season["accentColor"] = accent_color
    season["emojiPool"] = pool
    if raw_strings:
        season["strings"] = raw_strings
    return season


def load_seasons(path, source):
    try:
        with open(path, encoding="utf-8") as f:
            payload = json.load(f)
    except FileNotFoundError:
        raise SystemExit(f"Seasons file not found: {path}")
    except json.JSONDecodeError as error:
        raise SystemExit(f"{source}: not valid JSON: {error}")

    if not isinstance(payload, dict):
        raise SystemExit(
            f"{source}: expected an object keyed by season id, got {type(payload).__name__}"
        )
    if not payload:
        raise SystemExit(
            f"{source}: no seasons found. Uploading this would empty {SEASONS_PATH}; "
            'use "enabled": false to retire a season instead.'
        )
    return payload


def build_catalog(seasons_json, warnings):
    source = os.path.basename(seasons_json)
    payload = load_seasons(seasons_json, source)

    # Sorted so the written file and the printed summary are stable; the node
    # itself is a dictionary and carries no order.
    catalog = {}
    for season_id in sorted(payload):
        if not season_id or any(c in season_id for c in ILLEGAL_KEY_CHARS):
            raise SystemExit(
                f'{source}: season id "{season_id}" is not a valid Realtime Database key '
                f"(must be non-empty and free of {ILLEGAL_KEY_CHARS!r})."
            )
        catalog[season_id] = parse_season(season_id, payload[season_id], source, warnings)
    return catalog


def upload(seasons, database_url, credentials):
    try:
        import firebase_admin
        from firebase_admin import credentials as fb_credentials, db
    except ImportError:
        raise SystemExit(
            "firebase-admin is required to upload. Install it with:\n"
            "    pip install firebase-admin\n"
            "(or run with --dry-run to validate without uploading)."
        )

    cred_path = credentials or os.environ.get("GOOGLE_APPLICATION_CREDENTIALS")
    if not cred_path:
        raise SystemExit(
            "No service-account credentials. Pass --credentials path.json or set "
            "GOOGLE_APPLICATION_CREDENTIALS. See the header of this script."
        )
    if not os.path.exists(cred_path):
        raise SystemExit(f"Credentials file not found: {cred_path}")

    firebase_admin.initialize_app(
        fb_credentials.Certificate(cred_path), {"databaseURL": database_url}
    )
    db.reference(SEASONS_PATH).set(seasons)


def main():
    parser = argparse.ArgumentParser(description="Upload DoMemory seasons to Firebase.")
    parser.add_argument("--seasons", default=os.path.join(HERE, "seasons.json"),
                        help="Canonical seasons JSON (default: Scripts/seasons.json)")
    parser.add_argument("--credentials", help="Path to a Firebase service-account JSON")
    parser.add_argument("--database-url", default=DEFAULT_DATABASE_URL)
    parser.add_argument("--out", default=os.path.join(HERE, "seasons_data.json"),
                        help="Where to write the generated seasons_data.json")
    parser.add_argument("--dry-run", action="store_true",
                        help="Validate and write seasons_data.json, but do not upload")
    args = parser.parse_args()

    warnings = []
    try:
        seasons = build_catalog(args.seasons, warnings)
    except ValueError as error:
        raise SystemExit(str(error))

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump({"seasons": seasons}, f, ensure_ascii=False, indent=2)

    print(f"Catalog: {len(seasons)} seasons (ids {', '.join(sorted(seasons))})")
    print(f"Wrote {args.out}")
    for warning in warnings:
        print(f"Warning: {warning}")

    if args.dry_run:
        print("Dry run — nothing uploaded.")
        return

    upload(seasons, args.database_url, args.credentials)
    print(f"Uploaded {len(seasons)} seasons to {args.database_url}{SEASONS_PATH}")


if __name__ == "__main__":
    main()
