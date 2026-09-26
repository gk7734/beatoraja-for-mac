import SwiftUI
import AppKit

@main struct BeatorajaLauncher: App {
    @StateObject private var store = SettingsStore()
    var body: some Scene {
        Window("beatoraja", id: "launcher") {
            LauncherView().environmentObject(store)
                .environment(\.locale, Locale(identifier: "ko_KR"))
                .font(.system(size: 13, weight: .regular))
                .modifier(LauncherWindowMaterial())
                .frame(minWidth: 780, minHeight: 600)
                .onAppear { NSApp.setActivationPolicy(.regular); NSApp.activate(ignoringOtherApps: true) }
        }
        .defaultSize(width: 920, height: 730)
        .windowResizability(.contentMinSize)
        .commands {
            CommandGroup(replacing: .newItem) {}
            CommandGroup(after: .saveItem) { Button("설정 저장") { store.save() }.keyboardShortcut("s").disabled(store.busy) }
        }
    }
}

enum Page: String, CaseIterable, Identifiable {
    case display = "디스플레이", audio = "오디오", input = "입력", library = "라이브러리", select = "곡 선택", play = "플레이 옵션", skin = "스킨 및 사운드", general = "일반", online = "온라인 순위", tables = "난이도표", stream = "방송"
    var id: Self { self }
    var symbol: String {
        switch self {
        case .display: "display"; case .audio: "speaker.wave.2.fill"; case .input: "keyboard"; case .library: "folder.fill"; case .select: "music.note.list"; case .play: "gamecontroller.fill"; case .skin: "paintpalette.fill"; case .general: "gearshape.fill"; case .online: "globe"; case .tables: "tablecells.fill"; case .stream: "dot.radiowaves.left.and.right"
        }
    }
    var color: Color {
        switch self { case .display, .library: .blue; case .audio: .pink; case .input, .general: .gray; case .select: .orange; case .play: .indigo; case .skin: .purple; case .online: .cyan; case .tables: .green; case .stream: .red }
    }
    var subtitle: String {
        switch self {
        case .display: "화면 모드, 해상도와 배경 영상을 설정합니다."
        case .audio: "음량과 출력 장치, 재생 방식을 조절합니다."
        case .input: "키 모드별 입력과 스크래치를 설정합니다."
        case .library: "곡 폴더를 추가하고 라이브러리를 관리합니다."
        case .select: "곡 목록과 미리 듣기 동작을 설정합니다."
        case .play: "노트 속도와 판정, 게이지를 조절합니다."
        case .skin: "게임 화면과 사운드를 내 취향에 맞춥니다."
        case .general: "플레이어와 앱의 기본 동작을 설정합니다."
        case .online: "온라인 순위 서비스 설정을 관리합니다."
        case .tables: "난이도표 주소를 관리합니다."
        case .stream: "방송용 신청곡과 알림을 설정합니다."
        }
    }
}

struct LauncherView: View {
    @EnvironmentObject var store: SettingsStore
    @State private var page: Page = .display
    @State private var search = ""
    @State private var history: [Page] = []
    @State private var forward: [Page] = []
    @State private var confirmingDelete = false
    private let groups: [[Page]] = [[.display, .audio, .input], [.library, .select, .play, .skin], [.general, .online, .tables, .stream]]
    func navigate(_ next: Page) {
        guard next != page else { return }
        history.append(page); forward = []; page = next
    }
    var body: some View {
        NavigationSplitView {
            VStack(spacing: 0) {
                SearchField(text: $search).padding(.horizontal, 12).padding(.top, 12).padding(.bottom, 10)
                HStack(spacing: 10) {
                    Image(nsImage: NSApplication.shared.applicationIconImage).resizable().frame(width: 40, height: 40)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("beatoraja").font(.system(size: 14, weight: .semibold))
                        Text(store.player["name"] as? String ?? "플레이어").font(.system(size: 12)).foregroundStyle(.secondary)
                    }
                    Spacer()
                }.padding(.horizontal, 18).padding(.top, 18).padding(.bottom, 18)
                ScrollView {
                    VStack(spacing: 18) {
                        ForEach(groups.indices, id: \.self) { group in
                            VStack(spacing: 2) {
                                ForEach(groups[group]) { item in
                                    Button { navigate(item) } label: {
                                        HStack(spacing: 9) {
                                            Image(systemName: item.symbol).font(.system(size: 12, weight: .medium)).foregroundStyle(.white)
                                                .frame(width: 23, height: 23).background(item.color.gradient, in: RoundedRectangle(cornerRadius: 5))
                                            Text(item.rawValue).font(.system(size: 13, weight: page == item ? .medium : .regular))
                                            Spacer(minLength: 0)
                                        }.padding(.horizontal, 9).padding(.vertical, 5)
                                            .foregroundStyle(page == item ? Color.white : Color.primary)
                                            .background(page == item ? Color.accentColor : Color.clear, in: RoundedRectangle(cornerRadius: 8))
                                            .contentShape(Rectangle())
                                    }.buttonStyle(.plain).accessibilityAddTraits(page == item ? .isSelected : [])
                                }
                            }
                        }
                    }.padding(.horizontal, 10)
                }
            }.navigationSplitViewColumnWidth(min: 210, ideal: 232, max: 260)
        } detail: {
            VStack(spacing: 0) {
                if search.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    SettingsPage(page: page).disabled(store.busy)
                } else {
                    SettingsSearchResults(query: search) { result in
                        navigate(result.page)
                        search = ""
                    }
                }
                HStack(spacing: 10) {
                    if store.busy { ProgressView().controlSize(.small) }
                    Text(store.status).font(.system(size: 11)).foregroundStyle(.secondary)
                    Spacer()
                    Button("저장") { store.save() }
                        .buttonStyle(FooterButtonStyle(primary: false)).disabled(store.busy)
                    PlayButton().disabled(store.busy)
                }.padding(.horizontal, 22).padding(.top, 8).padding(.bottom, 18)
            }
            .navigationTitle(search.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? page.rawValue : "설정 검색")
            .toolbar {
                ToolbarItemGroup(placement: .navigation) {
                    Button {
                        if let previous = history.popLast() { forward.append(page); page = previous }
                    } label: { Image(systemName: "chevron.left") }.disabled(history.isEmpty).help("뒤로")
                    Button {
                        if let next = forward.popLast() { history.append(page); page = next }
                    } label: { Image(systemName: "chevron.right") }.disabled(forward.isEmpty).help("앞으로")
                }
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        ForEach(store.profiles, id: \.self) { id in Button(id) { store.selectProfile(id) } }
                        Divider()
                        Button("플레이어 추가") { store.addProfile() }
                        Button("현재 플레이어 삭제…", role: .destructive) { confirmingDelete = true }
                            .disabled(store.profiles.count < 2)
                    } label: { Image(systemName: "person.crop.circle") }.help("플레이어").disabled(store.busy)
                }
            }
        }
        .confirmationDialog("\(store.profileID)을 삭제할까요?", isPresented: $confirmingDelete, titleVisibility: .visible) {
            Button("휴지통으로 이동", role: .destructive) { store.deleteCurrentProfile() }
            Button("취소", role: .cancel) {}
        } message: {
            Text("이 플레이어의 설정·점수·리플레이를 휴지통으로 이동합니다. 다른 플레이어로 자동 전환되며, 삭제한 데이터는 휴지통에서 복구할 수 있습니다.")
        }
        .alert("확인이 필요합니다", isPresented: Binding(get: { store.error != nil }, set: { if !$0 { store.error = nil } })) {
            Button("확인", role: .cancel) { store.error = nil }
        } message: { Text(store.error ?? "") }
    }
}

struct PlayButton: View {
    @EnvironmentObject var store: SettingsStore
    var body: some View {
        Button("게임 시작") { store.run() }
            .buttonStyle(FooterButtonStyle(primary: true))
    }
}

/// Solid control surfaces keep text legible over any desktop sampled by the window.
struct FooterButtonStyle: ButtonStyle {
    let primary: Bool
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 13, weight: primary ? .semibold : .medium))
            .foregroundStyle(primary ? Color.white : Color(white: 0.12))
            .padding(.horizontal, 16).frame(minWidth: primary ? 100 : 68, minHeight: 32)
            .background(primary ? Color(white: configuration.isPressed ? 0.12 : 0.22)
                                : Color(white: configuration.isPressed ? 0.76 : 0.9),
                        in: RoundedRectangle(cornerRadius: 7))
            .overlay {
                RoundedRectangle(cornerRadius: 7)
                    .strokeBorder(primary ? Color.white.opacity(0.18) : Color.black.opacity(0.12), lineWidth: 0.5)
            }
            .opacity(enabled ? 1 : 0.45)
    }
}

struct SettingsPage: View {
    @EnvironmentObject var store: SettingsStore
    let page: Page
    @State var mode = "mode7"
    @State var tableURL = ""
    @State var skinIndex = 0
    var body: some View {
        Form {
            switch page {
            case .display: display
            case .audio: audio
            case .input: input
            case .library: library
            case .select: selection
            case .play: play
            case .skin: skins
            case .general: general
            case .online: online
            case .tables: tables
            case .stream: stream
            }
        }.formStyle(.grouped).scrollContentBackground(.hidden)
        .controlSize(.regular)
    }
    func toggle(_ title: String, _ key: String) -> some View { Toggle(title, isOn: store.flag(key)) }
    func number(_ title: String, _ key: String, _ range: ClosedRange<Double> = 0...10000, step: Double = 1) -> some View {
        LabeledContent(title) { TextField("", value: store.number(key), format: .number).multilineTextAlignment(.trailing).frame(width: 72)
            Stepper("", value: store.number(key), in: range, step: step).labelsHidden().fixedSize() }
    }
    func choice(_ title: String, _ key: String, _ options: [(String, String)], numeric: Bool = false) -> some View {
        Picker(title, selection: store.choice(key, numeric: numeric)) { ForEach(options, id: \.0) { Text($0.1).tag($0.0) } }
    }
    func slider(_ title: String, _ key: String) -> some View {
        LabeledContent(title) { Slider(value: store.number(key), in: 0...1).frame(maxWidth: 230); Text("\(Int((store.number(key).wrappedValue * 100).rounded()))%").monospacedDigit().frame(width: 38) }
    }
    var modes: some View {
        Picker("키 모드", selection: $mode) { ForEach([("mode5","5키"),("mode7","7키"),("mode9","9키"),("mode10","10키"),("mode14","14키"),("mode24","24키"),("mode24double","48키")], id: \.0) { Text($0.1).tag($0.0) } }
    }
    var display: some View {
        Group {
            Section("화면") {
                choice("화면 모드", "s.displaymode", [("WINDOW","창 모드"),("FULLSCREEN","전체 화면"),("BORDERLESS","테두리 없는 창")])
                choice("해상도", "s.resolution", [("HD","1280 × 720"),("FWXGA","1366 × 768"),("HDPLUS","1600 × 900"),("FULLHD","1920 × 1080"),("WQHD","2560 × 1440"),("ULTRAHD","3840 × 2160"),("SD","640 × 480"),("SVGA","800 × 600"),("XGA","1024 × 768"),("QUADVGA","1280 × 960"),("SXGAPLUS","1400 × 1050"),("UXGA","1600 × 1200"),("WSXGAPLUS","1680 × 1050"),("WUXGA","1920 × 1200"),("QXGA","2048 × 1536")])
                toggle("수직 동기화", "s.vsync")
                number("최대 프레임 수 (0은 제한 없음)", "s.maxFramePerSecond", 0...2000)
            }
            Section("배경 영상") {
                choice("재생", "s.bga", [("0","항상"),("1","자동 플레이만"),("2","사용 안 함")], numeric: true)
                choice("확대 방식", "s.bgaExpand", [("0","화면에 맞춤"),("1","비율 유지"),("2","원본 크기")], numeric: true)
                number("미스 화면 표시 시간(ms)", "p.misslayerDuration", 0...5000)
            }
        }
    }
    var audio: some View {
        Group {
            Section("출력") {
                choice("오디오 출력", "s.audio.driver", [("CoreAudio","Apple Core Audio"),("OpenAL","OpenAL"),("PortAudio","Core Audio (PortAudio)")])
                if store.value("s.audio.driver") as? String == "CoreAudio" { Text("macOS 기본 출력 장치를 사용합니다. 출력 장치는 시스템 설정에서 변경할 수 있습니다.").font(.caption).foregroundStyle(.secondary) }
                if store.value("s.audio.driver") as? String == "PortAudio" { TextField("출력 장치 이름 (비워 두면 기본 장치)", text: store.text("s.audio.driverName")) }
                choice("샘플 레이트", "s.audio.sampleRate", [("0","장치 기본값"),("44100","44,100 Hz"),("48000","48,000 Hz"),("96000","96,000 Hz"),("192000","192,000 Hz")], numeric: true)
                number("오디오 버퍼 크기", "s.audio.deviceBufferSize", 32...16384, step: 32)
                number("동시 재생 음원 수", "s.audio.deviceSimultaneousSources", 16...1024, step: 16)
            }
            Section("음량") { slider("시스템", "s.audio.systemvolume"); slider("키음", "s.audio.keyvolume"); slider("배경음", "s.audio.bgvolume") }
            Section("재생") {
                choice("주파수 조절", "s.audio.freqOption", [("FREQUENCY","주파수 변경"),("UNPROCESSED","변경 안 함")])
                choice("배속 재생", "s.audio.fastForward", [("FREQUENCY","주파수 변경"),("UNPROCESSED","변경 안 함")])
                toggle("결과 화면 효과음 반복", "s.audio.isLoopResultSound"); toggle("코스 결과 화면 효과음 반복", "s.audio.isLoopCourseResultSound")
            }
        }
    }
    var library: some View {
        Group {
            Section("곡 폴더") {
                let paths = store.value("s.bmsroot") as? [String] ?? []
                if paths.isEmpty { Text("곡 폴더를 추가해 주세요.").foregroundStyle(.secondary) }
                ForEach(Array(paths.enumerated()), id: \.offset) { i, path in
                    HStack { Image(systemName: "folder").foregroundStyle(.blue); Text(path).lineLimit(2).textSelection(.enabled); Spacer(); Button { var copy = paths; copy.remove(at: i); store.set("s.bmsroot", copy) } label: { Image(systemName: "minus.circle") }.buttonStyle(.borderless).help("목록에서 제거") }
                }
                HStack { Button("폴더 추가…") { store.addFolder() }; Spacer(); Button("곡 검색") { store.run(scan: true) } }
            }
            Section("검색") {
                toggle("게임 시작 시 곡 목록 갱신", "s.updatesong"); toggle("압축 파일 검색", "s.scanSongArchives"); toggle("곡 분석 정보 사용", "s.useSongInfo")
                Button("곡 목록 다시 만들기") { store.run(scan: true, rebuild: true) }
            }
        }
    }
    var selection: some View {
        Group {
            Section("곡 목록") {
                toggle("폴더 클리어 표시", "s.folderlamp"); toggle("없는 곡도 표시", "s.showNoSongExistingBar"); toggle("랜덤 선택 사용", "p.isRandomSelect")
                number("검색 결과 표시 개수", "s.maxSearchBarCount", 1...100)
                choice("정렬", "p.sortid", [("TITLE","제목"),("ARTIST","아티스트"),("BPM","BPM"),("LENGTH","길이"),("LEVEL","레벨"),("CLEAR","클리어"),("SCORE","점수"),("MISSCOUNT","미스 수")])
            }
            Section("미리 듣기와 스크롤") {
                choice("미리 듣기", "s.songPreview", [("NONE","사용 안 함"),("ONCE","한 번"),("LOOP","반복")])
                toggle("패턴 미리 보기", "p.chartPreview"); toggle("아날로그 스크롤", "s.analogScroll")
                number("스크롤 대기 시간(ms)", "s.scrolldurationlow", 1...2000); number("빠른 스크롤 간격(ms)", "s.scrolldurationhigh", 1...1000)
            }
        }
    }
    var input: some View {
        Group {
            Section { modes }
            Section("키보드") {
                KeyBindings(mode: mode)
                number("입력 간격(ms)", "p.\(mode).keyboard.duration", 0...100)
            }
            Section("컨트롤러") { ControllerSettings(mode: mode) }
            Section("마우스 스크래치") {
                toggle("마우스 스크래치 사용", "p.\(mode).keyboard.mouseScratchConfig.mouseScratchEnabled")
                number("이동 거리", "p.\(mode).keyboard.mouseScratchConfig.mouseScratchDistance", 1...1000)
                number("인식 시간(ms)", "p.\(mode).keyboard.mouseScratchConfig.mouseScratchTimeThreshold", 1...1000)
            }
        }
    }
    var play: some View {
        Group {
            Section("판정") { number("판정 타이밍(ms)", "p.judgetiming", -500...500); toggle("판정 타이밍 자동 조절", "p.notesDisplayTimingAutoAdjust"); toggle("판정 영역 표시", "p.showjudgearea") }
            Section("노트 속도") {
                modes
                number("하이스피드", "p.\(mode).playconfig.hispeed", 0.1...20, step: 0.1)
                number("노트 표시 시간(ms)", "p.\(mode).playconfig.duration", 1...10000)
                choice("속도 기준", "p.\(mode).playconfig.fixhispeed", [("0","사용 안 함"),("1","시작 BPM"),("2","최대 BPM"),("3","주요 BPM"),("4","최소 BPM")], numeric: true)
                toggle("레인 커버", "p.\(mode).playconfig.enablelanecover"); slider("레인 커버 높이", "p.\(mode).playconfig.lanecover")
                toggle("리프트", "p.\(mode).playconfig.enablelift"); slider("리프트 높이", "p.\(mode).playconfig.lift")
                toggle("히든", "p.\(mode).playconfig.enablehidden"); slider("히든 높이", "p.\(mode).playconfig.hidden")
            }
            Section("게이지와 노트") {
                choice("게이지", "p.gauge", [("0","어시스트 이지"),("1","이지"),("2","노멀"),("3","하드"),("4","EX 하드"),("5","해저드")], numeric: true)
                choice("롱노트", "p.lnmode", [("0","롱노트"),("1","차지 노트"),("2","헬 차지 노트")], numeric: true)
                toggle("BPM 안내", "p.bpmguide"); toggle("처리한 노트 표시", "p.markprocessednote")
            }
        }
    }
    var skins: some View {
        Group {
            Section("게임 스킨") {
                Picker("화면", selection: $skinIndex) { ForEach([(0,"7키"),(1,"5키"),(2,"14키"),(3,"10키"),(4,"9키"),(5,"곡 선택"),(6,"곡 결정"),(7,"결과"),(8,"키 설정"),(9,"스킨 선택"),(15,"코스 결과"),(16,"24키"),(17,"48키")], id: \.0) { Text($0.1).tag($0.0) } }
                SkinPath(index: skinIndex)
                Text("곡 선택 화면에서 F12(필요하면 Fn+F12)를 누르면 세부 스킨 설정을 열 수 있습니다.").font(.caption).foregroundStyle(.secondary)
            }
            Section("사운드") { TextField("배경음 폴더", text: store.text("s.bgmpath")); TextField("효과음 폴더", text: store.text("s.soundpath")) }
        }
    }
    var general: some View {
        Group {
            Section {
                VStack(spacing: 10) {
                    Image(systemName: "gearshape.fill").font(.system(size: 32)).foregroundStyle(.white)
                        .frame(width: 52, height: 52).background(Color.gray.gradient, in: RoundedRectangle(cornerRadius: 12))
                    Text("일반").font(.system(size: 21, weight: .semibold))
                    Text("플레이어 이름, 게임 동작과 저장 공간을 관리합니다.")
                        .foregroundStyle(.secondary).multilineTextAlignment(.center)
                }.frame(maxWidth: .infinity).padding(.vertical, 16)
            }
            Section("플레이어") { TextField("표시 이름", text: store.text("p.name")); toggle("이벤트 모드", "p.eventMode"); number("종료 키 누름 시간(ms)", "p.exitPressDuration", 0...10000) }
            Section("동작") { toggle("창 위치와 크기 유지", "p.isWindowHold"); toggle("안내 효과음", "p.isGuideSE"); toggle("스크린샷을 클립보드에도 복사", "s.setClipboardScreenshot"); toggle("스킨 이미지 캐시", "s.cacheSkinImage") }
            Section("크레딧") {
                LabeledContent("beatoraja 원작자", value: "exch 및 beatoraja 기여자")
                Link("원본 프로젝트", destination: URL(string: "https://github.com/exch-bms2/beatoraja")!)
                LabeledContent("Mac 포크", value: "gk7734")
                Link("Mac 버전 소스", destination: URL(string: "https://github.com/gk7734/beatoraja-for-mac")!)
                Text("원본 beatoraja를 기반으로 한 비공식 macOS 포크입니다. GNU GPL v3.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Section("저장 공간") { Button("설정 및 기록 폴더 열기") { NSWorkspace.shared.open(store.dataURL) }; Text(store.dataURL.path).font(.caption).foregroundStyle(.secondary).textSelection(.enabled) }
        }
    }
    var online: some View {
        Section("온라인 순위") {
            IRSettings()
            number("전송 재시도 횟수", "s.irSendCount", 1...100)
        }
    }
    var tables: some View {
        Section("난이도표 주소") {
            let urls = store.value("s.tableURL") as? [String] ?? []
            ForEach(Array(urls.enumerated()), id: \.offset) { i, url in
                HStack { Text(url).font(.callout).lineLimit(2).textSelection(.enabled); Spacer(); Button { var copy = urls; copy.remove(at: i); store.set("s.tableURL", copy) } label: { Image(systemName: "minus.circle") }.buttonStyle(.borderless).help("주소 제거") }
            }
            HStack { TextField("https://…", text: $tableURL); Button("추가") {
                if let url = URL(string: tableURL), ["https","http"].contains(url.scheme), !urls.contains(tableURL) { store.set("s.tableURL", urls + [tableURL]); tableURL = "" }
                else { store.error = "올바른 http 또는 https 주소를 입력해 주세요." }
            }.disabled(tableURL.isEmpty) }
        }
    }
    var stream: some View {
        Section("신청곡") { toggle("신청곡 기능 사용 (!!req)", "p.enableRequest"); toggle("신청곡 알림", "p.notifyRequest"); number("최대 신청곡 기록 수", "p.maxRequestCount", 1...1000) }
    }
}
