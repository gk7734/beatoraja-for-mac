#!/bin/bash
set -euo pipefail
PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="${BEATORAJA_BUILD_DIR:-$PROJECT/../.build/package-build}"
DEST="${BEATORAJA_OUTPUT_DIR:-$PROJECT/..}"
APP="$DEST/beatoraja.app"
mkdir -p "$BUILD" "$DEST"
codesign --verify --deep --strict "$APP"
STAGE="$(mktemp -d "$BUILD/dmg.XXXXXX")"
trap 'rm -rf "$STAGE"' EXIT
mkdir "$STAGE/root"
ditto --norsrc --noextattr "$APP" "$STAGE/root/beatoraja.app"
codesign --verify --deep --strict "$STAGE/root/beatoraja.app"
VOLUME='beatoraja — Apple Silicon'
# Installer window like JetBrains': background with an arrow from the app to Applications, fixed icon layout
# (dmgbuild writes the Finder layout itself; no Finder scripting). Falls back to a plain DMG without uv.
if command -v uvx >/dev/null 2>&1; then
  swift "$PROJECT/macos/DmgBackground.swift" "$STAGE/background.png" 1 "$PROJECT/macos/beatoraja.icns"
  swift "$PROJECT/macos/DmgBackground.swift" "$STAGE/background@2x.png" 2 "$PROJECT/macos/beatoraja.icns"
  uvx dmgbuild -s "$PROJECT/macos/dmg-settings.py" -D app="$STAGE/root/beatoraja.app" \
    -D background="$STAGE/background.png" -D icon="$PROJECT/macos/beatoraja.icns" \
    "$VOLUME" "$STAGE/beatoraja-AppleSilicon.dmg"
else
  ln -s /Applications "$STAGE/root/Applications"
  hdiutil create -volname "$VOLUME" -srcfolder "$STAGE/root" -format UDZO -o "$STAGE/beatoraja-AppleSilicon.dmg"
fi
hdiutil verify "$STAGE/beatoraja-AppleSilicon.dmg"
mv -f "$STAGE/beatoraja-AppleSilicon.dmg" "$DEST/beatoraja-AppleSilicon.dmg"
printf 'Built %s\n' "$DEST/beatoraja-AppleSilicon.dmg"
