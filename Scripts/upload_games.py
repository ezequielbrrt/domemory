#!/usr/bin/env python3
"""Build the DoMemory game catalog from CSV and upload it to Firebase Realtime Database.

The canonical catalog lives in ``games.csv``. To add cards you can either append
rows to ``games.csv`` directly, or put new rows in a separate file (e.g.
``new_games.csv``) and pass it with ``--add`` — new rows get fresh, auto-assigned
ids so you never have to renumber.

The script validates every row, writes ``data.json`` (same shape the app reads),
and replaces the ``/data`` node in Realtime Database with the full catalog.

Examples
--------
    # Validate + preview only, no upload (safe to run anytime):
    python3 upload_games.py --dry-run

    # Upload the canonical catalog:
    python3 upload_games.py --credentials /path/to/serviceAccount.json

    # Append extra cards from another CSV, then upload everything:
    python3 upload_games.py --add new_games.csv --credentials /path/to/serviceAccount.json

Credentials
-----------
``/data`` is read-only for app clients (see firebase-database.rules.json), so
uploading needs admin access via a service-account key:

    1. Firebase console -> Project settings -> Service accounts -> Generate new
       private key. Save the JSON somewhere private (do NOT commit it).
    2. Pass it with ``--credentials path.json`` or set the env var
       ``GOOGLE_APPLICATION_CREDENTIALS=path.json``.

Requires ``firebase-admin`` for the actual upload (``--dry-run`` does not):

    pip install firebase-admin

CSV columns (header required)
-----------------------------
    category,description,difficulty,id,isDoubleItem,itemType,items,name,publishedDate

    - id           optional in --add files (auto-assigned); must be unique otherwise.
    - isDoubleItem TRUE/FALSE.
    - items        comma-separated; wrap the cell in double quotes in the CSV.
    - difficulty   one of: easy, medium, hard, veryHard.
"""

import argparse
import csv
import json
import os
import sys

DEFAULT_DATABASE_URL = "https://domemory-c9211.firebaseio.com"
DATA_PATH = "/data"
VALID_DIFFICULTIES = {"easy", "medium", "hard", "veryHard"}
REQUIRED_COLUMNS = {
    "category", "description", "difficulty", "isDoubleItem",
    "itemType", "items", "name", "publishedDate",
}
HERE = os.path.dirname(os.path.abspath(__file__))


def parse_row(row, source, line_no):
    """Convert one CSV row dict into a game object, raising ValueError on bad data."""
    items = [i.strip() for i in (row.get("items") or "").split(",") if i.strip()]
    difficulty = (row.get("difficulty") or "").strip()
    name = (row.get("name") or "").strip()

    errors = []
    if len(items) < 2:
        errors.append("needs at least 2 items")
    if difficulty not in VALID_DIFFICULTIES:
        errors.append(f"difficulty '{difficulty}' not in {sorted(VALID_DIFFICULTIES)}")
    if not name:
        errors.append("missing name")
    if errors:
        raise ValueError(f"{source}:{line_no}: " + "; ".join(errors))

    # Note: the CSV "id" column is intentionally ignored. Ids are assigned by
    # position (see build_catalog), matching the original gamesToJson.py so the
    # existing /data ids (and per-game stats keyed on them) stay stable.
    return {
        "category": (row.get("category") or "emoji").strip(),
        "description": (row.get("description") or "").strip(),
        "difficulty": difficulty,
        "id": "",  # assigned by position in build_catalog
        "isDoubleItem": str(row.get("isDoubleItem", "")).strip().upper() in ("TRUE", "1", "YES"),
        "itemType": (row.get("itemType") or "String").strip(),
        "items": items,
        "name": name,
        "publishedDate": (row.get("publishedDate") or "").strip(),
    }


def load_csv(path, source):
    with open(path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        missing = REQUIRED_COLUMNS - set(reader.fieldnames or [])
        if missing:
            raise SystemExit(f"{source}: missing required columns: {sorted(missing)}")
        return [parse_row(row, source, i + 2) for i, row in enumerate(reader)]


def build_catalog(base_csv, add_csvs):
    # Canonical rows first (order preserved -> stable ids), then any additions.
    games = load_csv(base_csv, os.path.basename(base_csv))
    for add_path in add_csvs:
        games.extend(load_csv(add_path, os.path.basename(add_path)))

    # Assign sequential ids by position, exactly like the original gamesToJson.py.
    for position, game in enumerate(games, start=1):
        game["id"] = str(position)
    return games


def upload(games, database_url, credentials):
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
    db.reference(DATA_PATH).set(games)


def main():
    parser = argparse.ArgumentParser(description="Upload DoMemory game cards to Firebase.")
    parser.add_argument("--csv", default=os.path.join(HERE, "games.csv"),
                        help="Canonical games CSV (default: Scripts/games.csv)")
    parser.add_argument("--add", action="append", default=[],
                        help="Extra CSV of new cards (repeatable); ids auto-assigned")
    parser.add_argument("--credentials", help="Path to a Firebase service-account JSON")
    parser.add_argument("--database-url", default=DEFAULT_DATABASE_URL)
    parser.add_argument("--out", default=os.path.join(HERE, "data.json"),
                        help="Where to write the generated data.json")
    parser.add_argument("--dry-run", action="store_true",
                        help="Validate and write data.json, but do not upload")
    args = parser.parse_args()

    games = build_catalog(args.csv, args.add)

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump({"data": games}, f, ensure_ascii=False, indent=2)

    print(f"Catalog: {len(games)} games (ids {games[0]['id']}..{games[-1]['id']})")
    print(f"Wrote {args.out}")
    if args.add:
        print(f"Added from: {', '.join(args.add)}")

    if args.dry_run:
        print("Dry run — nothing uploaded.")
        return

    upload(games, args.database_url, args.credentials)
    print(f"Uploaded {len(games)} games to {args.database_url}{DATA_PATH}")


if __name__ == "__main__":
    main()
