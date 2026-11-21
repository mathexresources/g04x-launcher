#!/usr/bin/env bash
# Sestaví APK a vydá ho jako GitHub Release – hodiny si ho stáhnou přes ⚙ Nastavení → Aktualizovat.
# Verzi zvyš v AndroidManifest.xml (versionCode + versionName). Potřebuje gh a hodiny.keystore.
set -euo pipefail
cd "$(dirname "$0")"
VER=$(grep -o 'versionName="[^"]*"' AndroidManifest.xml | cut -d'"' -f2)
NOTES=${1:-""}
./build.sh
cp Hodiny.apk "Hodiny-$VER.apk"
gh release create "v$VER" "Hodiny-$VER.apk" --title "Hodiny $VER" --notes "$NOTES"
rm "Hodiny-$VER.apk"
