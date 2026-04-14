#!/bin/bash
# Upload iPhone screenshots to App Store Connect for each locale.

set -euo pipefail

APP_ID="${APP_ID:-1533115091}"
VERSION_STRING="${VERSION_STRING:-2.1.0}"
SCREENSHOTS_DIR="${SCREENSHOTS_DIR:-./screenshots/6.5}"
# ASC still treats the phone screenshot set as IPHONE_65.
DEVICE_TYPE="${DEVICE_TYPE:-IPHONE_65}"

resolve_version_id() {
    asc versions list \
        --app "$APP_ID" \
        --output json | jq -r \
        ".data[] | select(.attributes.versionString==\"$VERSION_STRING\") | .id" | head -1
}

resolve_locales() {
    find "$SCREENSHOTS_DIR" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; | sort
}

read_lines_into_array() {
    local target_array_name="$1"
    local line
    local -a values=()

    while IFS= read -r line; do
        values+=("$line")
    done

    eval "$target_array_name=(\"\${values[@]}\")"
}

is_valid_size() {
    case "$1" in
        1242x2688|1284x2778|2688x1242|2778x1284)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

echo "Resolving VERSION_ID for version $VERSION_STRING..."
VERSION_ID="$(resolve_version_id)"

if [ -z "$VERSION_ID" ]; then
    echo "Could not resolve VERSION_ID for version $VERSION_STRING. Check that the version exists in App Store Connect."
    exit 1
fi

if [ ! -d "$SCREENSHOTS_DIR" ]; then
    echo "Screenshot directory not found: $SCREENSHOTS_DIR"
    exit 1
fi

read_lines_into_array LOCALES < <(resolve_locales)

if [ "${#LOCALES[@]}" -eq 0 ]; then
    echo "No locale directories found under $SCREENSHOTS_DIR"
    exit 1
fi

echo "VERSION_ID: $VERSION_ID"
echo "Uploading iPhone screenshots to App Store Connect"
echo "Device type: $DEVICE_TYPE"
echo "Expected sizes: 1242x2688 or 1284x2778"
echo ""

for locale in "${LOCALES[@]}"; do
    echo "----------------------------------------"
    echo "Processing locale: $locale"
    echo "----------------------------------------"

    loc_id=$(asc localizations list \
        --version "$VERSION_ID" \
        --app "$APP_ID" \
        --locale "$locale" \
        --output json | jq -r '.data[0].id')

    if [ -z "$loc_id" ] || [ "$loc_id" = "null" ]; then
        echo "No localization ID found for $locale, skipping..."
        echo ""
        continue
    fi

    echo "Version Localization ID: $loc_id"

    locale_dir="$SCREENSHOTS_DIR/$locale"
    read_lines_into_array screenshot_files < <(find "$locale_dir" -maxdepth 1 -type f \( -name '*.jpg' -o -name '*.jpeg' -o -name '*.png' \) | sort)

    if [ "${#screenshot_files[@]}" -eq 0 ]; then
        echo "No screenshot files found at $locale_dir, skipping..."
        echo ""
        continue
    fi

    bad_dims=0
    for screenshot in "${screenshot_files[@]}"; do
        width=$(/usr/bin/sips -g pixelWidth "$screenshot" 2>/dev/null | awk '/pixelWidth/ {print $2}')
        height=$(/usr/bin/sips -g pixelHeight "$screenshot" 2>/dev/null | awk '/pixelHeight/ {print $2}')
        size="${width}x${height}"

        if ! is_valid_size "$size"; then
            echo "Invalid iPhone size for $screenshot: $size"
            bad_dims=1
        fi
    done

    if [ "$bad_dims" -ne 0 ]; then
        echo "Skipping $locale until screenshot dimensions are fixed."
        echo ""
        continue
    fi

    echo "Uploading with --replace..."
    asc screenshots upload \
        --version-localization "$loc_id" \
        --path "$locale_dir" \
        --device-type "$DEVICE_TYPE" \
        --replace

    echo "Successfully uploaded iPhone screenshots for $locale"
    echo ""
done

echo "----------------------------------------"
echo "Upload process complete."
