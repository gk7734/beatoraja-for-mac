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
ln -s /Applications "$STAGE/root/Applications"
cat > "$STAGE/root/설치 안내.txt" <<'HELP'
beatoraja.app을 Applications 폴더에 끌어다 놓으세요.
기존 앱을 완전히 종료하고 교체한 뒤 응용 프로그램에서 실행하세요.
Apple Silicon 전용이며 Java와 JavaFX가 포함되어 있습니다.
Apple 공증은 적용되어 있지 않습니다.
원작: exch (exch-bms2) 및 beatoraja 기여자
https://github.com/exch-bms2/beatoraja
macOS 포크: https://github.com/gk7734/beatoraja-for-mac
HELP
codesign --verify --deep --strict "$STAGE/root/beatoraja.app"
hdiutil create -volname 'beatoraja — Apple Silicon' -srcfolder "$STAGE/root" -format UDZO -o "$STAGE/beatoraja-AppleSilicon.dmg"
hdiutil verify "$STAGE/beatoraja-AppleSilicon.dmg"
mv -f "$STAGE/beatoraja-AppleSilicon.dmg" "$DEST/beatoraja-AppleSilicon.dmg"
printf 'Built %s\n' "$DEST/beatoraja-AppleSilicon.dmg"
