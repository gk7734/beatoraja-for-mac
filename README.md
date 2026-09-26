# beatoraja for Mac

[beatoraja](https://github.com/exch-bms2/beatoraja)를 기반으로 만든 비공식 Apple Silicon 전용 macOS 포크입니다.

## Credits

**원작자: exch (exch-bms2) 및 beatoraja 기여자 여러분.**
게임 엔진, BMS 기능과 기본 리소스의 원본 프로젝트는 [exch-bms2/beatoraja](https://github.com/exch-bms2/beatoraja)입니다.
이 저장소의 macOS 포팅 및 Swift 런처 변경은 [gk7734](https://github.com/gk7734)가 관리합니다.
자세한 표기는 [CREDITS.md](CREDITS.md)를 참고하세요. 원본 설명서는 [README.upstream.md](README.upstream.md)에 보존했습니다.

## 이 포크의 변경 사항

- 한국어 SwiftUI 런처와 macOS 시스템 설정 형태의 UI
- macOS 26 이상에서 네이티브 Liquid Glass 사용
- Apple Silicon 전용 런타임 및 네이티브 라이브러리
- 앱 내부에 Java 27과 JavaFX 포함
- LWJGL 3 기반 게임 실행, 게임 종료 후 런처 복귀
- 설정 검색, 플레이어 추가 및 휴지통으로 삭제
- 이전 설치의 기본 스킨 글꼴 자동 복구

게임 렌더러는 OpenGL입니다. 런처와 게임은 별도 프로세스로 실행됩니다.

## 빌드

Apple Silicon Mac, Xcode Command Line Tools, JavaFX가 포함된 ARM64 JDK 27이 필요합니다.

```sh
BEATORAJA_JDK=/path/to/jdk/Contents/Home bash macos/build-app.sh
```

완성된 앱, ZIP, 설치 DMG는 기본적으로 프로젝트 상위 폴더에 생성됩니다.
`BEATORAJA_BUILD_DIR`, `BEATORAJA_OUTPUT_DIR`, `BEATORAJA_MACOS_SDK`로 경로를 지정할 수 있습니다.
앱은 로컬 임시 서명을 사용하며 Apple 공증은 포함하지 않습니다. 실제 테스트 환경은 macOS 27 / Apple Silicon입니다.

## 실행

앱을 열고 라이브러리에서 BMS 곡 폴더를 추가한 뒤 곡 검색을 실행하세요. 곡 파일은 포함하지 않습니다.
곡 선택 화면에서 **F12**(필요하면 **Fn+F12**)로 스킨 설정을 엽니다. 숫자 9는 곡 설명 문서, 8은 같은 폴더의 곡, 7은 라이벌 전환입니다.
설정과 플레이 기록은 `~/Library/Application Support/beatoraja`에 저장됩니다.

## Apple Core Audio 출력

런처의 **오디오 → 오디오 출력 → Apple Core Audio**에서 선택합니다.
Apple Audio Queue Services에 JNI로 직접 연결하며, macOS 기본 출력 장치를 사용합니다.
`장치 기본값`은 시작 시 기본 출력 장치의 샘플 레이트를 조회합니다.
기존 OpenAL/PortAudio 설정은 그대로 유지되므로 새 출력 방식을 직접 선택해야 합니다.

PCM 믹싱은 기존 Java 믹서를 사용하고, 네이티브 콜백은 JVM에 진입하지 않고 버퍼를 반환합니다.
버퍼는 3개이며 설정값은 버퍼당 프레임 수입니다. 실제 왕복 지연은 측정하지 않았습니다.
출력 장치 변경 후에는 게임을 다시 시작하세요. 이 구현은 Audio Unit/HAL 저지연 전용 백엔드는 아닙니다.

빌드 후 실제 기본 출력 장치에 무음만 보내는 테스트:

```sh
JDK=/path/to/jdk/Contents/Home
"$JDK/bin/javac" -cp '../.build/package-build/classes:lib/*' -d ../.build/package-build/test-classes macos/tests/CoreAudioSmokeTest.java
"$JDK/bin/java" --enable-native-access=ALL-UNNAMED -Djava.library.path=../.build/package-build/input/natives -cp '../.build/package-build/test-classes:../.build/package-build/classes:lib/*' CoreAudioSmokeTest
```

## 라이선스

원본의 GNU GPL v3 라이선스와 저작자 표기를 유지합니다. [LICENSE](LICENSE)를 참고하세요.
서드파티 라이브러리와 리소스의 권리는 각 저작자에게 있습니다.
