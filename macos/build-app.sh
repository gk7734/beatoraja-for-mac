#!/bin/bash
set -euo pipefail
PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="${BEATORAJA_BUILD_DIR:-$PROJECT/../.build/package-build}"
DEST="${BEATORAJA_OUTPUT_DIR:-$PROJECT/..}"
JDK="${BEATORAJA_JDK:-$(/usr/libexec/java_home)}"
mkdir -p "$BUILD"
python3 - "$BUILD" <<'CLEAN'
import pathlib,sys,shutil
b=pathlib.Path(sys.argv[1])
for name in ['classes','input','controller-classes']:
 p=b/name
 if p.exists(): shutil.rmtree(p)
 p.mkdir()
CLEAN
"$JDK/bin/javac" -cp "$PROJECT/lib/*" -d "$BUILD/controller-classes" $(find "$PROJECT/macos/controller-adapter" -name '*.java')
"$JDK/bin/jar" --create --file "$PROJECT/lib/gdx-controllers-desktop-2.2.3.jar" -C "$BUILD/controller-classes" . -C "$PROJECT/macos/controller-adapter" gamecontrollerdb.txt
STAGE="$(mktemp -d /private/tmp/beatoraja-arm64.XXXXXX)"
find "$PROJECT/src" -name '*.java' > "$BUILD/sources.txt"
"$JDK/bin/javac" --add-modules javafx.controls,javafx.fxml,javafx.swing -cp "$PROJECT/lib/*" -d "$BUILD/classes" @"$BUILD/sources.txt"
python3 - "$PROJECT" "$BUILD" <<'PY'
import pathlib,sys,shutil
p,b=map(pathlib.Path,sys.argv[1:])
for f in (p/'src').rglob('*'):
 if f.is_file() and f.suffix!='.java':
  dst=b/'classes'/f.relative_to(p/'src');dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,dst)
manifest='Manifest-Version: 1.0\nMain-Class: bms.player.beatoraja.MacBootstrap\nClass-Path: '+' '.join('lib/'+f.name for f in sorted((p/'lib').glob('*.jar')))+'\n\n'
import textwrap
manifest='\n'.join('\n '.join(textwrap.wrap(line, width=68, break_long_words=True, break_on_hyphens=False, replace_whitespace=False, drop_whitespace=False)) for line in manifest.split('\n'))
(b/'MANIFEST.MF').write_text(manifest)
for name in ['lib','natives','skin','font','defaultsound','folder','random']:
 shutil.copytree(p/name,b/'input'/name,dirs_exist_ok=True)
shutil.copy2(p/'LICENSE',b/'input/LICENSE')
shutil.copy2(p/'CREDITS.md',b/'input/CREDITS.md')
PY
xcrun clang -arch arm64 -mmacosx-version-min=14.0 -O2 -Wall -Wextra -Wno-unused-parameter -dynamiclib \
 -I"$JDK/include" -I"$JDK/include/darwin" "$PROJECT/macos/audio/CoreAudio.c" \
 -framework AudioToolbox -framework CoreAudio -framework CoreFoundation -o "$BUILD/input/natives/libbeatoraja_coreaudio.dylib"
"$JDK/bin/jar" --create --file "$BUILD/input/beatoraja.jar" --manifest "$BUILD/MANIFEST.MF" -C "$BUILD/classes" .
if [ ! -d "$BUILD/runtime" ]; then
 "$JDK/bin/jlink" --add-modules java.se,jdk.unsupported,jdk.jfr,jdk.charsets,javafx.controls,javafx.fxml,javafx.swing --strip-debug --no-header-files --no-man-pages --output "$BUILD/runtime"
fi
"$JDK/bin/jpackage" --type app-image --name beatoraja --app-version 0.5.4 --dest "$STAGE" --icon "$PROJECT/macos/beatoraja.icns" --input "$BUILD/input" --main-jar beatoraja.jar --main-class bms.player.beatoraja.MacBootstrap --runtime-image "$BUILD/runtime" --mac-package-identifier local.beatoraja.applesilicon --java-options '-Xms256m' --java-options '-Xmx4g' --java-options '--add-modules=javafx.controls,javafx.fxml,javafx.swing' --java-options '--enable-native-access=ALL-UNNAMED,javafx.graphics' --java-options '--add-exports=javafx.graphics/com.sun.javafx.stage=ALL-UNNAMED' --java-options '--add-exports=javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED' --java-options '-Dbeatoraja.appdir=$APPDIR' --java-options '-Djava.library.path=$APPDIR/natives' --description 'Apple Silicon rhythm game with JavaFX and native Liquid Glass'
APP="$STAGE/beatoraja.app"
"$JDK/bin/java" -cp "$BUILD/classes:$PROJECT/lib/*" bms.player.beatoraja.MacMaintenance --defaults "$APP/Contents/app/defaults"
SDK="${BEATORAJA_MACOS_SDK:-$(xcrun --sdk macosx --show-sdk-path)}"
# The installed 27 beta CLT lacks SwiftUIMacros; prefer the stable SDK when present.
if [ -z "${BEATORAJA_MACOS_SDK:-}" ] && [ -d /Library/Developer/CommandLineTools/SDKs/MacOSX26.5.sdk ]; then
 SDK=/Library/Developer/CommandLineTools/SDKs/MacOSX26.5.sdk
fi
xcrun swiftc -sdk "$SDK" -parse-as-library -O -target arm64-apple-macos14.0 -module-cache-path "$BUILD/swift-module-cache" "$PROJECT"/macos/Launcher/*.swift -o "$APP/Contents/MacOS/beatoraja"
/usr/libexec/PlistBuddy -c 'Set :CFBundleDevelopmentRegion ko' "$APP/Contents/Info.plist"
/usr/libexec/PlistBuddy -c 'Add :CFBundleLocalizations array' "$APP/Contents/Info.plist"
/usr/libexec/PlistBuddy -c 'Add :CFBundleLocalizations:0 string ko' "$APP/Contents/Info.plist"
/usr/libexec/PlistBuddy -c 'Set :LSMinimumSystemVersion 14.0' "$APP/Contents/Info.plist"
codesign --force --deep --sign - "$APP"
codesign --verify --deep --strict "$APP"
mkdir -p "$DEST"
python3 - "$APP" "$DEST" "$BUILD" <<'COPY'
import pathlib,sys,shutil,os,subprocess
app,dest,build=map(pathlib.Path,sys.argv[1:])
target=dest/'beatoraja.app'
if target.exists():
 old=build/'previous-app'
 if old.exists():shutil.rmtree(old)
 shutil.move(target,old)
shutil.copytree(app,target,symlinks=True,copy_function=shutil.copyfile)
# Restore executable modes without inheriting Finder metadata from Documents.
for p in app.rglob('*'):
 if not p.is_symlink(): (target/p.relative_to(app)).chmod(p.stat().st_mode)
subprocess.run(['xattr','-d','com.apple.FinderInfo',str(target)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
COPY
ditto -c -k --norsrc --keepParent "$APP" "$DEST/beatoraja-AppleSilicon.zip"
printf '%s\n' "$APP" > "$BUILD/staged-app.txt"
printf 'Built %s\n' "$DEST/beatoraja-AppleSilicon.zip"

BEATORAJA_BUILD_DIR="$BUILD" BEATORAJA_OUTPUT_DIR="$DEST" bash "$PROJECT/macos/build-dmg.sh"
