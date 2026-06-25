#!/usr/bin/env python3
"""Export Paper artboards and save App Store Connect phone screenshots."""

import base64
import json
import os
import subprocess
import urllib.request

MCP_URL = "http://127.0.0.1:29979/mcp"
OUT_DIR = os.path.join(os.path.dirname(__file__), "screenshots", "6.9")
# 6.9-inch display (iPhone 16 Pro Max). Artboards on the
# "ScreenshotsV4 — Proposal" page are authored natively at this size.
ASC_IPHONE_WIDTH = 1290
ASC_IPHONE_HEIGHT = 2796

# Base artboard name → output filename.
# An artboard may be either the bare base name (treated as English) or
# localized as "{base_name} — {app_locale}" e.g. "Proposal 1 — Hero Gameplay — de".
SCREEN_SPECS = [
    ("Proposal 1 — Hero Gameplay",         "01_hero_gameplay.jpg"),
    ("Proposal 2 — Play.Create.Challenge", "02_play_create_challenge.jpg"),
    ("Proposal 3 — Multiplayer (NEW)",     "03_multiplayer.jpg"),
    ("Proposal 4 — Play Dozens",           "04_play_dozens.jpg"),
    ("Proposal 5 — Create Your Own (NEW)", "05_create_your_own.jpg"),
    ("Proposal 6 — Choose Challenge",      "06_choose_challenge.jpg"),
]

# Maps Paper/app locale codes → App Store Connect locale codes.
# Locales not listed here are passed through unchanged.
LOCALE_MAP = {
    "en":     "en-US",
    "de":     "de-DE",
    "es-419": "es-MX",
    "fr":     "fr-FR",
}


def mcp_call(method, params, req_id):
    """Make a single JSON-RPC call to the Paper MCP server, return parsed result."""
    payload = json.dumps({
        "jsonrpc": "2.0",
        "id": req_id,
        "method": method,
        "params": params,
    }).encode()

    req = urllib.request.Request(
        MCP_URL,
        data=payload,
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
        },
        method="POST",
    )

    with urllib.request.urlopen(req, timeout=30) as resp:
        raw = resp.read().decode()

    # SSE format: lines starting with "data: "
    for line in raw.splitlines():
        if line.startswith("data: "):
            return json.loads(line[6:])

    raise RuntimeError(f"No data line in SSE response: {raw[:200]}")


def screenshot(node_id, req_id):
    """Fetch screenshot for a Paper node and return the raw image bytes."""
    result = mcp_call(
        "tools/call",
        {"name": "get_screenshot", "arguments": {"nodeId": node_id, "scale": 2}},
        req_id,
    )
    if "error" in result:
        raise RuntimeError(f"MCP error: {result['error']}")

    content = result["result"]["content"]
    for item in content:
        if item.get("type") == "image":
            return base64.b64decode(item["data"])

    raise RuntimeError(f"No image content in response for {node_id}")


def discover_artboards():
    """Return [(node_id, locale, filename)] discovered from the Paper canvas."""
    result = mcp_call("tools/call", {"name": "get_basic_info", "arguments": {}}, 1)
    if "error" in result:
        raise RuntimeError(f"MCP error: {result['error']}")

    content = result["result"]["content"]
    payload = None
    for item in content:
        if item.get("type") == "text":
            payload = json.loads(item["text"])
            break

    if payload is None:
        raise RuntimeError("Paper get_basic_info returned no text payload")

    artboards_by_name = {artboard["name"]: artboard["id"] for artboard in payload["artboards"]}

    discovered = []

    # Collect all app locale codes that have at least one matching artboard.
    # An artboard is matched as either the bare base name (English) or
    # "{base_name} — {app_locale}" e.g. "Proposal 1 — Hero Gameplay — de".
    app_locales = set()
    for name in artboards_by_name:
        for base_name, _ in SCREEN_SPECS:
            if name == base_name:
                app_locales.add("en")
            elif name.startswith(f"{base_name} — "):
                app_locales.add(name[len(f"{base_name} — "):])

    for app_locale in sorted(app_locales):
        asc_locale = LOCALE_MAP.get(app_locale, app_locale)
        for base_name, filename in SCREEN_SPECS:
            if app_locale == "en":
                node_id = (artboards_by_name.get(base_name)
                           or artboards_by_name.get(f"{base_name} — en"))
            else:
                node_id = artboards_by_name.get(f"{base_name} — {app_locale}")
            if node_id:
                discovered.append((node_id, asc_locale, filename))

    if not discovered:
        raise RuntimeError("No screenshot artboards found on the Paper canvas")

    return discovered


def main():
    # Initialize session
    mcp_call(
        "initialize",
        {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "asc-export", "version": "1.0"},
        },
        0,
    )

    artboards = discover_artboards()
    files_written = []

    for i, (node_id, locale, filename) in enumerate(artboards, start=1):
        dest_dir = os.path.join(OUT_DIR, locale)
        os.makedirs(dest_dir, exist_ok=True)
        dest = os.path.join(dest_dir, filename)

        print(f"[{i:2d}/{len(artboards)}] {locale}/{filename} ← {node_id}", end="  ", flush=True)
        img_bytes = screenshot(node_id, i)
        with open(dest, "wb") as f:
            f.write(img_bytes)

        # Export the App Store phone screenshots in the 6.5-inch size.
        subprocess.run(
            ["/usr/bin/sips", "-z", str(ASC_IPHONE_HEIGHT), str(ASC_IPHONE_WIDTH), dest],
            check=True, capture_output=True,
        )

        # Verify
        result = subprocess.run(
            ["/usr/bin/sips", "-g", "pixelWidth", "-g", "pixelHeight", dest],
            capture_output=True, text=True, check=True,
        )
        dims = {
            line.split(":")[0].strip(): line.split(":")[1].strip()
            for line in result.stdout.splitlines()
            if ":" in line
        }
        w, h = dims.get("pixelWidth", "?"), dims.get("pixelHeight", "?")
        print(f"{w}×{h}")
        files_written.append(dest)

    print(
        f"\n✓ {len(files_written)} screenshots saved and resized to "
        f"{ASC_IPHONE_WIDTH}×{ASC_IPHONE_HEIGHT} in {OUT_DIR}"
    )


if __name__ == "__main__":
    main()
