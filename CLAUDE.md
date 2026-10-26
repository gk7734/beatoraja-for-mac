# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Unofficial Apple Silicon–only macOS fork of [beatoraja](https://github.com/exch-bms2/beatoraja) (Java/libGDX BMS rhythm game, GPL v3). The fork adds a native SwiftUI launcher (Korean UI), ARM64 native libraries, and a bundled Java 27 + JavaFX runtime. User-facing docs (README.md, launcher strings, status messages) are written in Korean. Keep upstream author/copyright notices intact; fork credits live in CREDITS.md, the original README in README.upstream.md.

## Build

There is no Gradle/Maven/Ant build and no test suite. Everything goes through one script:

```sh
BEATORAJA_JDK=/path/to/jdk/Contents/Home bash macos/build-app.sh
```

Requires an ARM64 JDK 27 that includes JavaFX modules, and Xcode Command Line Tools. Outputs `beatoraja.app` and `beatoraja-AppleSilicon.zip` in the parent of the repo (override with `BEATORAJA_OUTPUT_DIR`; intermediate files go to `BEATORAJA_BUILD_DIR`, default `../.build/package-build`; `BEATORAJA_MACOS_SDK` overrides the SDK). The app is ad-hoc signed, not notarized.

What the script does, in order (useful when only one part needs rebuilding):
1. Compiles `macos/controller-adapter/**` and **overwrites the tracked `lib/gdx-controllers-desktop-2.2.3.jar`** with the result (a patched Jamepad-based controller backend). Expect that jar to show as modified in git after a build.
2. `javac` all of `src/**/*.java` against `lib/*` with `--add-modules javafx.controls,javafx.fxml,javafx.swing`; non-Java files under `src/` (FXML, CSS, GLSL shaders, `resources/UIResources*.properties`) are copied into the classpath.
3. Compiles `macos/audio/CoreAudio.c` (Audio Queue output) plus `macos/audio/AudioUnitOutput.c` (low-latency Audio Unit output, driver `CoreAudioUnit`: a real-time render callback reads a lock-free ring the Java mixer fills) and `macos/metal/MetalRenderer.m` into `natives/libbeatoraja_coreaudio.dylib` and `natives/libbeatoraja_metal.dylib`, builds `beatoraja.jar` (Main-Class `MacBootstrap`, Class-Path = every jar in `lib/`), jlinks a runtime (cached in the build dir — delete `runtime/` there to force a rebuild), and runs `jpackage`.
4. Runs `MacMaintenance --defaults` to generate `Contents/app/defaults/config_sys.json` and `config_player.json`.
5. Compiles `macos/Launcher/*.swift` with `swiftc` and **replaces the jpackage executable** `Contents/MacOS/beatoraja` with the Swift launcher. Prefers the MacOSX26.5 SDK if present because the macOS 27 beta CLT lacks SwiftUIMacros.
6. Signs the app with `BEATORAJA_SIGN_IDENTITY`, else the self-signed "beatoraja Local Signing" identity if it is in the login keychain, else ad-hoc. Ad-hoc signatures change every build, which silently drops the Input Monitoring grant that HID keyboard input needs.

Releases are automatic: pushing a `v*` tag runs `.github/workflows/release.yml` on a `macos-26` runner (Zulu 27 + FX, ad-hoc signed) and publishes the DMG and zip. The annotated tag's subject is the release title and its body the notes, with SHA-256 sums appended (falls back to GitHub's generated notes for a lightweight tag). A manual run (`gh workflow run Release`) only builds and keeps the files as an artifact.

Test launches must be silent and invisible: run from a scratch data folder with `systemvolume`/`keyvolume`/`bgvolume` set to 0 in its `config_sys.json`, and pass `-Dbeatoraja.hiddenWindow=true` (and `-Dapple.awt.UIElement=true`) so the game window stays hidden with no Dock icon or menu bar.

Quick Java-only compile check (no packaging):

```sh
find src -name '*.java' > /tmp/sources.txt
"$BEATORAJA_JDK/bin/javac" --add-modules javafx.controls,javafx.fxml,javafx.swing -cp 'lib/*' -d /tmp/bclasses @/tmp/sources.txt
```

Dependencies are vendored jars in `lib/` (libGDX 1.14 with the LWJGL 3 backend, LWJGL 3.3.3 arm64 natives, JavaCV/FFmpeg arm64, sqlite-jdbc, luaj, etc.). `natives/` holds prebuilt `libjportaudio.dylib` and `libbeatoraja-glass.dylib` (no source in this repo).

## Runtime architecture

Two separate processes:

- **Swift launcher** (`macos/Launcher/`) — the app's actual executable. `SettingsStore.swift` owns all state: it seeds the data dir (`~/Library/Application Support/beatoraja`, overridable via `BEATORAJA_DATA_DIR`) from the bundle's `skin`, `font`, `defaultsound`, `folder`, `random` and `defaults/*.json`, reads/writes `config_sys.json` and `player/<id>/config_player.json` directly as JSON, and spawns the bundled JVM (`Contents/runtime/.../bin/java`) with the data dir as working directory. Settings are addressed by dotted keys: `s.<field>` → system config, `p.<field>` → player config, mapping 1:1 to the Java `Config` / `PlayerConfig` JSON fields. `SettingsSearch.swift` indexes settings for the search UI; `LauncherApp.swift` is the SwiftUI view tree; `GlassStyle.swift` applies Liquid Glass on macOS 26+.
- **Java game process**, launched per action:
  - `bms.player.beatoraja.MacBootstrap -s` (with `-XstartOnFirstThread`, required for GLFW on macOS) — sets Korean locale, runs `MainLoader.main`, then force-`System.exit`s so stray audio/MIDI/AWT threads don't keep the JVM alive. Output → `game.log` in the data dir. When the game exits, the launcher reloads the config files.
  - `bms.player.beatoraja.MacMaintenance --scan | --rebuild` — headless song DB update (no JavaFX). Output → `scan.log`.

When adding a new config field in Java, the Swift launcher only sees it if a UI binding for the matching `s.`/`p.` key is added; unknown keys are preserved because the launcher round-trips the whole JSON dictionary.

Mac-specific pieces in the game process:
- `MainLoader` disables libGDX's own FPS limiter (its `Sync` yield-spins) and calls `FrameLimiter` + `RenderScaler` around `MainController.render()`. `RenderScaler` renders into a 1x FrameBuffer and upscales, because the Retina backbuffer is always 2x (GLFW 3.4 ignores `GLFW_SCALE_FRAMEBUFFER` here). Inside `render()`, use `RenderScaler.getTargetWidth/Height()` instead of the backbuffer size, and call `RenderScaler.restoreTarget()` after any nested `FrameBuffer.end()`.
- With `Config.metalRenderer` (default on; launcher: Metal로 그리기) the game draws with Metal (`metal/`). `MetalRenderer.m` puts a CAMetalLayer over GLFW's content view. libGDX keeps its GL context for bookkeeping only: `MetalGL20` turns texture uploads into MTLTextures (one copy each, unlike Apple's GL) and handles framebuffers, clears, scissor, viewport and `glReadPixels`; `MetalSpriteBatch.flush` sends quads to Metal; the four `glsl/` shaders have Metal ports in `MetalRenderer.m` (change both together). Anything drawn with GL directly (ShapeRenderer, Mesh, a plain `new SpriteBatch()`) is invisible in Metal mode, so create batches with `MetalRenderer.newSpriteBatch()`. `MetalTextureLoader` reads PNGs of 1M+ pixels on a background thread, so screens appear before their large effect sheets finish loading. `RenderScaler` is bypassed (Core Animation upscales the 1x drawable). If Metal fails to start, the game falls back to GL.
- GLFW key state only updates once per frame (`glfwPollEvents`), so keyboard timing is quantized to the frame rate. `input/MacHidKeyboard` reads the keyboard via IOHIDManager (Java FFM, needs Input Monitoring permission) and `KeyBoardInputProcesseor` switches a lane key to HID only after HID and GLFW agree on it once.

The legacy JavaFX launcher (`MainLoader.start` → `launcher/PlayConfigurationView.fxml`, restyled by `launcher/ModernLauncher` + CSS, with `MacGameProcess` for spawning the game) still exists in the source but is not the entry point of the packaged app.

### Game engine (upstream code, `src/bms/player/beatoraja/`)

`MainLoader` builds the `Lwjgl3Application`; an anonymous `ApplicationListener` there delegates to `MainController`, which owns shared resources and switches between `MainState` subclasses — `select/` (music select), `decide/`, `play/` (`BMSPlayer`, judge, gauge, key input), `result/`, plus `config/` screens. `skin/` loads skins from JSON (`skin/json`), Lua (`skin/lua`), and LR2 CSV (`skin/lr2`) formats into a common `Skin`/`SkinObject` model. `song/` holds the SQLite song DB (`SQLiteSongDatabaseAccessor`); score/replay data goes through `PlayDataAccessor` and the `*DatabaseAccessor` classes. `ir/` is the internet-ranking plugin interface; `pattern/` implements lane/note modifiers (random, mirror, etc.). BMS parsing itself comes from the vendored `jbms-parser.jar`.
