#!/bin/bash
# Upload iPhone 6.5" screenshots to App Store Connect for each locale

APP_ID="1533115091"
VERSION_STRING="2.0.1"

echo "🔍 Resolving VERSION_ID for version $VERSION_STRING..."
VERSION_ID=$(asc versions list \
    --app "$APP_ID" \
    --output json | jq -r \
    ".data[] | select(.attributes.versionString==\"$VERSION_STRING\") | .id" | head -1)

if [ -z "$VERSION_ID" ]; then
    echo "❌ Could not resolve VERSION_ID for version $VERSION_STRING. Check that the version exists in App Store Connect."
    exit 1
fi

echo "✅ VERSION_ID: $VERSION_ID"
echo ""

LOCALES=("en-US" "es-MX" "de-DE" "fr-FR" "hi" "it" "ja" "ko" "pt-BR" "zh-Hans")

echo "📱 Uploading iPhone screenshots to App Store Connect"
echo ""

for locale in "${LOCALES[@]}"; do
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Processing locale: $locale"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # Get version localization ID for this locale
    loc_id=$(asc localizations list \
        --version "$VERSION_ID" \
        --app "$APP_ID" \
        --locale "$locale" \
        --output json | jq -r '.data[0].id')

    if [ -z "$loc_id" ]; then
        echo "❌ No localization ID found for $locale, skipping..."
        echo ""
        continue
    fi

    echo "🔍 Version Localization ID: $loc_id"

    # Check if screenshots directory exists
    if [ ! -d "./screenshots/6.5/$locale" ]; then
        echo "⚠️  No screenshots found for $locale, skipping..."
        echo ""
        continue
    fi

    # Upload screenshots
    echo "📤 Uploading..."
    asc screenshots upload \
        --version-localization "$loc_id" \
        --path "./screenshots/6.5/$locale" \
        --device-type "IPHONE_65" \
        --replace

    upload_result=$?

    if [ $upload_result -eq 0 ]; then
        echo "✅ Successfully uploaded iPhone screenshots for $locale"
    else
        echo "❌ Failed to upload iPhone screenshots for $locale"
    fi
    echo ""
done

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎉 Upload process complete!"
