# beatoraja for Mac

An unofficial Apple Silicon macOS port of [beatoraja](https://github.com/exch-bms2/beatoraja), with a native launcher.

[한국어](#한국어) · [Download](https://github.com/gk7734/beatoraja-for-mac/releases/latest)

## Install

1. Download `beatoraja-AppleSilicon.dmg` from [Releases](https://github.com/gk7734/beatoraja-for-mac/releases/latest).
2. Open it and drag **beatoraja** into **Applications**.
3. The app is not notarized by Apple: the first time, Control-click it and choose **Open**.

Add your BMS folders in the launcher's library and run a song scan. No songs are included.
Settings and scores are stored in `~/Library/Application Support/beatoraja`.

## Features

- **Native launcher**: SwiftUI settings in the style of System Settings (Liquid Glass on macOS 26+), settings search, players, difficulty tables with automatic download
- **Apple Silicon only**: Java 27 and JavaFX bundled, native libraries for arm64; the game runs as its own process and returns to the launcher
- **Display**: resolution picked from your display, skins kept at their aspect ratio (no stretching on MacBook screens), Retina 1x rendering for less heat
- **Input & audio**: HID keyboard input independent of frame rate, a primary and secondary key per lane, Apple Core Audio output
- **Performance**: decoded skin images cached on disk (faster screen changes), bounded skin image memory
- **Skins**: notes radar and IIDX-style player notes radar, skin settings preview, LEGGENDARIA title slots, Lua API extensions

The game renders with OpenGL (LWJGL 3).

## Keys

In music select: **F12** (Fn+F12) opens skin settings, **9** the song's document, **8** songs in the same folder, **7** switches rivals.

## Build

Requires an Apple Silicon Mac, the Xcode Command Line Tools and an arm64 JDK 27 with JavaFX.

```sh
BEATORAJA_JDK=/path/to/jdk/Contents/Home bash macos/build-app.sh
```

The app, a zip and the installer DMG are written to the parent folder of the project. Set `BEATORAJA_BUILD_DIR`,
`BEATORAJA_OUTPUT_DIR` or `BEATORAJA_MACOS_SDK` to change the paths. The installer layout needs
[uv](https://docs.astral.sh/uv/) (`dmgbuild`); without it a plain DMG is built. With a Developer ID Application certificate in the keychain the app is signed for notarization, and `BEATORAJA_NOTARY_PROFILE=<notarytool profile>` also notarizes the app and DMG; otherwise it is ad-hoc signed.

<details>
<summary>Core Audio smoke tests (play silence on the default output)</summary>

```sh
JDK=/path/to/jdk/Contents/Home
"$JDK/bin/javac" -cp '../.build/package-build/classes:lib/*' -d ../.build/package-build/test-classes macos/tests/CoreAudioSmokeTest.java
"$JDK/bin/java" --enable-native-access=ALL-UNNAMED -Djava.library.path=../.build/package-build/input/natives -cp '../.build/package-build/test-classes:../.build/package-build/classes:lib/*' CoreAudioSmokeTest
```

`macos/tests/AudioUnitSmokeTest.java` checks the low-latency Audio Unit output (`CoreAudioUnit`) the same way and prints its buffer sizes, latency and underruns.

</details>

## Credits & license

beatoraja is created by **exch (exch-bms2) and the beatoraja contributors**: [exch-bms2/beatoraja](https://github.com/exch-bms2/beatoraja).
The macOS port and launcher are maintained by [gk7734](https://github.com/gk7734). See [CREDITS.md](CREDITS.md);
the original readme is kept in [README.upstream.md](README.upstream.md).

Licensed under the GNU GPL v3 like the original ([LICENSE](LICENSE)). Third-party libraries and resources belong to
their respective authors.

---

## 한국어

[beatoraja](https://github.com/exch-bms2/beatoraja)를 기반으로 만든 비공식 Apple Silicon 전용 macOS 포크입니다.

### 설치

1. [Releases](https://github.com/gk7734/beatoraja-for-mac/releases/latest)에서 `beatoraja-AppleSilicon.dmg`를 받습니다.
2. 열어서 **beatoraja**를 **Applications** 폴더로 끌어다 놓습니다.
3. Apple 공증이 없어서 처음 실행할 때는 앱을 Control-클릭한 뒤 **열기**를 선택합니다.

런처의 라이브러리에서 BMS 곡 폴더를 추가하고 곡 검색을 실행하세요. 곡은 포함되어 있지 않습니다.
설정과 플레이 기록은 `~/Library/Application Support/beatoraja`에 저장됩니다.

### 주요 기능

- **네이티브 런처**: 시스템 설정 형태의 SwiftUI 설정(macOS 26 이상 Liquid Glass), 설정 검색, 플레이어 관리, 난이도표 자동 다운로드
- **Apple Silicon 전용**: Java 27과 JavaFX 내장, arm64 네이티브 라이브러리, 게임은 별도 프로세스로 실행되고 끝나면 런처로 복귀
- **화면**: 디스플레이에 맞춘 해상도 자동 선택, 스킨 비율 유지(맥북 화면에서 늘어나지 않음), 발열을 줄이는 Retina 1x 렌더링
- **입력·오디오**: 프레임과 무관한 HID 키보드 입력, 레인마다 기본 키와 서브 키, Apple Core Audio 출력
- **성능**: 스킨 이미지 디스크 캐시(빠른 화면 전환), 스킨 이미지 메모리 상한
- **스킨**: 노트레이더와 IIDX 방식 플레이어 노트레이더, 스킨 설정 미리보기, 레전다리아 제목 슬롯, Lua API 확장

### 단축키

곡 선택 화면에서 **F12**(Fn+F12)는 스킨 설정, **9**는 곡 설명 문서, **8**은 같은 폴더의 곡, **7**은 라이벌 전환입니다.

### 빌드

Apple Silicon Mac, Xcode Command Line Tools, JavaFX가 포함된 arm64 JDK 27이 필요합니다. 명령은 위 [Build](#build)와 같습니다.

### 크레딧·라이선스

원작자는 **exch (exch-bms2) 및 beatoraja 기여자 여러분**입니다. macOS 포팅과 런처는 [gk7734](https://github.com/gk7734)가 관리합니다.
원본과 같은 GNU GPL v3 라이선스입니다.
