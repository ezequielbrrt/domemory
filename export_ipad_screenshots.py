#!/usr/bin/env python3
"""Export Paper artboards as iPad App Store screenshots.

The current Paper canvas contains the same 40 phone-shaped artboards used by
export_screenshots.py. This script mirrors that mapping and normalizes the
exported files to the App Store Connect iPad 12.9" target size:
APP_IPAD_PRO_3GEN_129, 2048 x 2732 portrait.
"""

import os
import subprocess

from export_screenshots import ARTBOARDS, mcp_call, screenshot

IPAD_WIDTH = 2048
IPAD_HEIGHT = 2732
OUT_DIR = os.path.join(os.path.dirname(__file__), "screenshots", "ipad-12.9")


def resize_for_app_store_connect(path):
    """Resize a screenshot to ASC's accepted iPad 12.9" portrait dimensions."""
    subprocess.run(
        ["/usr/bin/sips", "-z", str(IPAD_HEIGHT), str(IPAD_WIDTH), path],
        check=True,
        capture_output=True,
    )


def read_dimensions(path):
    """Return the pixel width and height reported by sips."""
    result = subprocess.run(
        ["/usr/bin/sips", "-g", "pixelWidth", "-g", "pixelHeight", path],
        capture_output=True,
        text=True,
        check=True,
    )
    dims = {
        line.split(":")[0].strip(): line.split(":")[1].strip()
        for line in result.stdout.splitlines()
        if ":" in line
    }
    return dims.get("pixelWidth", "?"), dims.get("pixelHeight", "?")


def main():
    mcp_call(
        "initialize",
        {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "asc-export-ipad", "version": "1.0"},
        },
        0,
    )

    files_written = []

    for i, (node_id, locale, filename) in enumerate(ARTBOARDS, start=1):
        dest_dir = os.path.join(OUT_DIR, locale)
        os.makedirs(dest_dir, exist_ok=True)
        dest = os.path.join(dest_dir, filename)

        print(f"[{i:2d}/{len(ARTBOARDS)}] {locale}/{filename} <- {node_id}", end="  ", flush=True)
        img_bytes = screenshot(node_id, i)
        with open(dest, "wb") as f:
            f.write(img_bytes)

        resize_for_app_store_connect(dest)
        width, height = read_dimensions(dest)
        print(f"{width}x{height}")
        files_written.append(dest)

    print(
        f"\n{len(files_written)} screenshots saved and resized to "
        f"{IPAD_WIDTH}x{IPAD_HEIGHT} for APP_IPAD_PRO_3GEN_129"
    )


if __name__ == "__main__":
    main()
