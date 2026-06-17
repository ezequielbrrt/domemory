#!/usr/bin/env python3
"""Export iPad 12.9" App Store screenshots (2048×2732) directly from Paper artboards.

Artboard naming convention (same as the phone script):
  "{base_name} — {app_locale}"
  e.g. "01 Flip & Match — en", "02 Play Dozens — de"

The script connects to the Paper MCP server, discovers all matching artboards,
captures them, and saves them to screenshots/ipad-12.9/{asc-locale}/{slug}.jpg.

Output: screenshots/ipad-12.9/{asc-locale}/{slug}.jpg
"""

import base64
import json
import os
import subprocess
import urllib.request

MCP_URL = "http://127.0.0.1:29979/mcp"
OUT_DIR = os.path.join(os.path.dirname(__file__), "screenshots", "ipad-12.9")

ASC_IPAD_WIDTH  = 2048
ASC_IPAD_HEIGHT = 2732

# Base artboard name → output filename.
# Localized artboards must follow the pattern: "{base_name} — {app_locale}"
SCREEN_SPECS = [
    ("01 Flip & Match",     "01_flip_and_match.jpg"),
    ("02 Play Dozens",      "02_play_dozens.jpg"),
    ("03 Choose Challenge", "03_choose_challenge.jpg"),
    ("05 Features",         "05_features.jpg"),
]

# Maps Paper/app locale codes → App Store Connect locale codes.
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

    for line in raw.splitlines():
        if line.startswith("data: "):
            return json.loads(line[6:])

    raise RuntimeError(f"No data line in SSE response: {raw[:200]}")


def screenshot(node_id, req_id):
    """Fetch screenshot for a Paper node and return the raw image bytes."""
    result = mcp_call(
        "tools/call",
        {"name": "get_screenshot", "arguments": {"nodeId": node_id, "scale": 1}},
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
    """Return [(node_id, asc_locale, filename)] discovered from the Paper canvas."""
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

    artboards_by_name = {a["name"]: a["id"] for a in payload["artboards"]}

    app_locales = set()
    for name in artboards_by_name:
        for base_name, _ in SCREEN_SPECS:
            prefix = f"{base_name} — "
            if name.startswith(prefix):
                app_locales.add(name[len(prefix):])

    discovered = []
    for app_locale in sorted(app_locales):
        asc_locale = LOCALE_MAP.get(app_locale, app_locale)
        for base_name, filename in SCREEN_SPECS:
            localized_name = f"{base_name} — {app_locale}"
            node_id = artboards_by_name.get(localized_name)
            if node_id:
                discovered.append((node_id, asc_locale, filename))

    if not discovered:
        raise RuntimeError("No iPad screenshot artboards found on the Paper canvas")

    return discovered


def main():
    mcp_call(
        "initialize",
        {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "asc-ipad-export", "version": "1.0"},
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

        # Enforce exact App Store iPad dimensions.
        subprocess.run(
            ["/usr/bin/sips", "-z", str(ASC_IPAD_HEIGHT), str(ASC_IPAD_WIDTH), dest],
            check=True, capture_output=True,
        )

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
        f"\n✓ {len(files_written)} iPad screenshots saved to {OUT_DIR}  "
        f"({ASC_IPAD_WIDTH}×{ASC_IPAD_HEIGHT})"
    )


if __name__ == "__main__":
    main()
