#!/usr/bin/env python3
"""Export all 40 Paper artboards and save as JPEG files for App Store Connect."""

import base64
import json
import os
import subprocess
import urllib.request

MCP_URL = "http://127.0.0.1:29979/mcp"
OUT_DIR = os.path.join(os.path.dirname(__file__), "screenshots", "6.5")

ARTBOARDS = [
    # (nodeId, locale, filename)
    ("1SL-0", "en-US",   "01_menu.jpg"),
    ("1UI-0", "en-US",   "02_gameplay.jpg"),
    ("1VZ-0", "en-US",   "03_win.jpg"),
    ("1X2-0", "en-US",   "04_difficulty.jpg"),
    ("1YE-0", "es-MX",   "01_menu.jpg"),
    ("20B-0", "es-MX",   "02_gameplay.jpg"),
    ("21S-0", "es-MX",   "03_win.jpg"),
    ("22V-0", "es-MX",   "04_difficulty.jpg"),
    ("247-0", "de-DE",   "01_menu.jpg"),
    ("264-0", "de-DE",   "02_gameplay.jpg"),
    ("27L-0", "de-DE",   "03_win.jpg"),
    ("28O-0", "de-DE",   "04_difficulty.jpg"),
    ("2A0-0", "fr-FR",   "01_menu.jpg"),
    ("2BX-0", "fr-FR",   "02_gameplay.jpg"),
    ("2DE-0", "fr-FR",   "03_win.jpg"),
    ("2EH-0", "fr-FR",   "04_difficulty.jpg"),
    ("2FT-0", "hi",      "01_menu.jpg"),
    ("2HQ-0", "hi",      "02_gameplay.jpg"),
    ("2J7-0", "hi",      "03_win.jpg"),
    ("2KA-0", "hi",      "04_difficulty.jpg"),
    ("2LM-0", "it",      "01_menu.jpg"),
    ("2NJ-0", "it",      "02_gameplay.jpg"),
    ("2P0-0", "it",      "03_win.jpg"),
    ("2Q3-0", "it",      "04_difficulty.jpg"),
    ("2RF-0", "ja",      "01_menu.jpg"),
    ("2TC-0", "ja",      "02_gameplay.jpg"),
    ("2UT-0", "ja",      "03_win.jpg"),
    ("2VW-0", "ja",      "04_difficulty.jpg"),
    ("2X8-0", "ko",      "01_menu.jpg"),
    ("2Z5-0", "ko",      "02_gameplay.jpg"),
    ("30M-0", "ko",      "03_win.jpg"),
    ("31P-0", "ko",      "04_difficulty.jpg"),
    ("331-0", "pt-BR",   "01_menu.jpg"),
    ("34Y-0", "pt-BR",   "02_gameplay.jpg"),
    ("36F-0", "pt-BR",   "03_win.jpg"),
    ("37I-0", "pt-BR",   "04_difficulty.jpg"),
    ("38U-0", "zh-Hans", "01_menu.jpg"),
    ("3AR-0", "zh-Hans", "02_gameplay.jpg"),
    ("3C8-0", "zh-Hans", "03_win.jpg"),
    ("3DB-0", "zh-Hans", "04_difficulty.jpg"),
]


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

    files_written = []

    for i, (node_id, locale, filename) in enumerate(ARTBOARDS, start=1):
        dest_dir = os.path.join(OUT_DIR, locale)
        os.makedirs(dest_dir, exist_ok=True)
        dest = os.path.join(dest_dir, filename)

        print(f"[{i:2d}/40] {locale}/{filename} ← {node_id}", end="  ", flush=True)
        img_bytes = screenshot(node_id, i)
        with open(dest, "wb") as f:
            f.write(img_bytes)

        # Resize to ASC 6.5" dimensions: 1242 × 2688
        subprocess.run(
            ["/usr/bin/sips", "-z", "2688", "1242", dest],
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

    print(f"\n✓ {len(files_written)} screenshots saved and resized to 1242×2688")


if __name__ == "__main__":
    main()
