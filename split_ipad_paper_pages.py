#!/usr/bin/env python3
"""Clone the large Paper iPad screenshot page into one lightweight page per locale."""

import json

import export_ipad_screenshots as exporter


SOURCE_PAGE_ID = "8-0"
EXISTING_PAGES = {
    "en": "9-0",
    "de": "A-0",
    "es-419": "B-0",
    "fr": "C-0",
    "hi": "D-0",
    "it": "E-0",
    "ja": "F-0",
    "ko": "G-0",
    "pt-BR": "H-0",
    "zh-Hans": "I-0",
}
PAGE_PREFIX = "ScreenshotsV4 — iPad"


def call_tool(name, arguments, req_id):
    response = exporter.mcp_call(
        "tools/call",
        {"name": name, "arguments": arguments},
        req_id,
    )
    if "error" in response:
        raise RuntimeError(response["error"])
    return response["result"]["content"]


def text_payload(content):
    for item in content:
        if item.get("type") == "text":
            return json.loads(item["text"])
    raise RuntimeError("Paper returned no text payload")


def basic_info(req_id):
    return text_payload(call_tool("get_basic_info", {}, req_id))


def source_artboards():
    call_tool("open_page", {"pageId": SOURCE_PAGE_ID}, 1)
    info = basic_info(2)
    by_locale = {}

    for artboard in info["artboards"]:
        name = artboard["name"]
        matched_base = next(
            (base for base, _ in exporter.SCREEN_SPECS
             if name == base or name.startswith(f"{base} — ")),
            None,
        )
        if not matched_base:
            continue

        locale = "en" if name == matched_base else name[len(f"{matched_base} — "):]
        by_locale.setdefault(locale, {})[matched_base] = artboard["id"]

    return by_locale


def ensure_target_artboard(source_id, base_name, existing, req_id):
    if base_name in existing:
        return existing[base_name]

    created = text_payload(call_tool(
        "create_artboard",
        {
            "name": base_name,
            "styles": {
                "width": f"{exporter.ASC_IPAD_WIDTH}px",
                "height": f"{exporter.ASC_IPAD_HEIGHT}px",
                "backgroundColor": "#ffffff",
            },
        },
        req_id,
    ))
    target_id = created["id"]

    call_tool(
        "write_html",
        {
            "targetNodeId": target_id,
            "mode": "insert-children",
            "html": (
                f'<x-paper-clone node-id="{source_id}" '
                f'style="width:{exporter.ASC_IPAD_WIDTH}px;'
                f'height:{exporter.ASC_IPAD_HEIGHT}px" />'
            ),
        },
        req_id + 1,
    )
    return target_id


def main():
    exporter.mcp_call(
        "initialize",
        {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "ipad-page-split", "version": "1.0"},
        },
        0,
    )

    sources = source_artboards()
    page_ids = dict(EXISTING_PAGES)
    req_id = 10

    for locale in sorted(sources, key=lambda value: (value != "en", value)):
        page_id = page_ids.get(locale)
        if not page_id:
            created = text_payload(call_tool(
                "create_page",
                {"name": f"{PAGE_PREFIX} — {exporter.LOCALE_MAP.get(locale, locale)}"},
                req_id,
            ))
            page_id = created["pageId"]
            page_ids[locale] = page_id
            req_id += 1

        call_tool("open_page", {"pageId": page_id}, req_id)
        req_id += 1
        existing = {a["name"]: a["id"] for a in basic_info(req_id)["artboards"]}
        req_id += 1

        for base_name, _ in exporter.SCREEN_SPECS:
            source_id = sources[locale].get(base_name)
            if not source_id:
                raise RuntimeError(f"Missing source artboard: {base_name} — {locale}")
            ensure_target_artboard(source_id, base_name, existing, req_id)
            req_id += 2

        print(f"{locale}: {page_id}")

    print("\nPAGE_IDS = " + json.dumps(page_ids, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
