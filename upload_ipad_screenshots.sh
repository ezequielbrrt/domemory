#!/bin/bash
# Upload iPad 12.9" screenshots to App Store Connect for each locale.

set -u

APP_ID="1533115091"
VERSION_STRING="2.1.0"
SCREENSHOTS_DIR="./screenshots/ipad-12.9"
DEVICE_TYPE="IPAD_PRO_3GEN_129"

echo "Resolving VERSION_ID for version $VERSION_STRING..."
VERSION_ID=$(asc versions list \
    --app "$APP_ID" \
    --output json | jq -r \
    ".data[] | select(.attributes.versionString==\"$VERSION_STRING\") | .id" | head -1)

if [ -z "$VERSION_ID" ]; then
    echo "Could not resolve VERSION_ID for version $VERSION_STRING. Check that the version exists in App Store Connect."
    exit 1
fi

echo "VERSION_ID: $VERSION_ID"
echo ""

LOCALES=("en-US" "es-MX" "de-DE" "fr-FR" "hi" "it" "ja" "ko" "pt-BR" "zh-Hans")

echo "Uploading iPad screenshots to App Store Connect"
echo "Device type: $DEVICE_TYPE"
echo "Expected portrait size: 2048x2732"
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
    if [ ! -d "$locale_dir" ]; then
        echo "No screenshots found at $locale_dir, skipping..."
        echo ""
        continue
    fi

    screenshot_count=0
    bad_dims=0
    for screenshot in "$locale_dir"/*.{jpg,jpeg,png}; do
        [ -e "$screenshot" ] || continue
        screenshot_count=$((screenshot_count + 1))
        width=$(/usr/bin/sips -g pixelWidth "$screenshot" 2>/dev/null | awk '/pixelWidth/ {print $2}')
        height=$(/usr/bin/sips -g pixelHeight "$screenshot" 2>/dev/null | awk '/pixelHeight/ {print $2}')

        if [ "$width" != "2048" ] || [ "$height" != "2732" ]; then
            echo "Invalid iPad size for $screenshot: ${width}x${height}; expected 2048x2732"
            bad_dims=1
        fi
    done

    if [ "$screenshot_count" -eq 0 ]; then
        echo "No screenshot files found at $locale_dir, skipping..."
        echo ""
        continue
    fi

    if [ "$bad_dims" -ne 0 ]; then
        echo "Skipping $locale until screenshot dimensions are fixed."
        echo ""
        continue
    fi

    echo "Uploading..."
    asc screenshots upload \
        --version-localization "$loc_id" \
        --path "$locale_dir" \
        --device-type "$DEVICE_TYPE" \
        --replace

    upload_result=$?

    if [ $upload_result -eq 0 ]; then
        echo "Successfully uploaded iPad screenshots for $locale"
    else
        echo "Failed to upload iPad screenshots for $locale"
    fi
    echo ""
done

echo "----------------------------------------"
echo "Upload process complete."
