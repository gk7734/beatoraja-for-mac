#!/bin/bash
# Submits a signed beatoraja.app or DMG to Apple's notary service, waits, and staples the ticket.
# Usage: notarize.sh <beatoraja.app | .dmg> <notarytool keychain profile>
set -euo pipefail
TARGET="$1"
PROFILE="$2"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
SUBMIT="$TARGET"
if [ -d "$TARGET" ]; then
  SUBMIT="$WORK/$(basename "$TARGET").zip"
  ditto -c -k --norsrc --keepParent "$TARGET" "$SUBMIT"
fi
xcrun notarytool submit "$SUBMIT" --keychain-profile "$PROFILE" --wait --output-format json > "$WORK/result.json"
STATUS="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("status",""))' "$WORK/result.json")"
if [ "$STATUS" != Accepted ]; then
  ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("id",""))' "$WORK/result.json")"
  printf 'Notarization %s for %s\n' "${STATUS:-failed}" "$TARGET" >&2
  [ -n "$ID" ] && xcrun notarytool log "$ID" --keychain-profile "$PROFILE" >&2
  exit 1
fi
xcrun stapler staple "$TARGET"
printf 'Notarized %s\n' "$TARGET"
