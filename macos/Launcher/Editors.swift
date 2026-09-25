import SwiftUI
import AppKit

struct KeyBindings: View {
    @EnvironmentObject var store: SettingsStore
    let mode: String
    @State private var target: Int?
    @State private var monitor: Any?
    private var key: String { "p.\(mode).keyboard.keys" }
    private var codes: [Int] { store.value(key) as? [Int] ?? [] }
    static let special: [(Int, String)] = [(-1,"미지정"),(19,"↑"),(20,"↓"),(21,"←"),(22,"→"),(59,"왼쪽 Shift"),(60,"오른쪽 Shift"),(129,"왼쪽 Control"),(130,"오른쪽 Control"),(57,"왼쪽 Option"),(58,"오른쪽 Option"),(61,"Tab"),(62,"Space"),(66,"Return"),(67,"Delete"),(68,"`"),(69,"-"),(70,"="),(71,"["),(72,"]"),(73,"\\"),(74,";"),(75,"'"),(55,","),(56,"."),(76,"/"),(111,"Esc")]
    static func name(_ code: Int) -> String {
        if (29...54).contains(code) { return String(UnicodeScalar(code - 29 + 65)!) }
        if (7...16).contains(code) { return String(code - 7) }
        return special.first { $0.0 == code }?.1 ?? "키 \(code)"
    }
    var body: some View {
        VStack(spacing: 12) {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 100))], spacing: 12) {
                ForEach(Array(codes.enumerated()), id: \.offset) { i, code in
                    VStack(alignment: .leading, spacing: 6) {
                        Text("입력 \(i + 1)").font(.caption).foregroundStyle(.secondary)
                        Menu(Self.name(code)) {
                            Button("키를 눌러 지정…") { begin(i) }
                            ForEach(Self.special, id: \.0) { code, name in Button(name) { assign(i, code) } }
                        }.frame(maxWidth: .infinity)
                    }
                }
            }
            HStack {
                keyPicker("시작", "p.\(mode).keyboard.start")
                keyPicker("선택", "p.\(mode).keyboard.select")
            }
        }
        .sheet(isPresented: Binding(get: { target != nil }, set: { if !$0 { finish() } })) {
            VStack(spacing: 20) {
                Image(systemName: "keyboard").font(.system(size: 40)).foregroundStyle(.blue)
                Text("지정할 키를 눌러 주세요").font(.title3.bold())
                Text("Shift·Control·Option 키는 키 목록에서 선택할 수 있습니다.").foregroundStyle(.secondary)
                Button("취소") { finish() }.keyboardShortcut(.cancelAction)
            }.padding(36)
        }.onDisappear { finish() }
    }
    func keyPicker(_ title: String, _ path: String) -> some View {
        Picker(title, selection: store.choice(path, numeric: true)) {
            ForEach((29...54).map { ($0, Self.name($0)) } + Self.special, id: \.0) { Text($0.1).tag(String($0.0)) }
        }
    }
    func assign(_ i: Int, _ code: Int) { var copy = codes; guard copy.indices.contains(i) else { return }; copy[i] = code; store.set(key, copy) }
    func finish() { if let monitor { NSEvent.removeMonitor(monitor) }; monitor = nil; target = nil }
    func begin(_ i: Int) {
        target = i
        monitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
            let physical: [UInt16: Int] = [53:111,123:21,124:22,125:20,126:19,36:66,48:61,49:62,51:67,50:68,27:69,24:70,33:71,30:72,42:73,41:74,39:75,43:55,47:56,44:76]
            var code = physical[event.keyCode]
            if code == nil, let scalar = event.charactersIgnoringModifiers?.uppercased().unicodeScalars.first {
                if (65...90).contains(scalar.value) { code = Int(scalar.value) - 65 + 29 }
                else if (48...57).contains(scalar.value) { code = Int(scalar.value) - 48 + 7 }
            }
            if let code { assign(i, code); finish(); return nil }
            return event
        }
    }
}

struct SkinPath: View {
    @EnvironmentObject var store: SettingsStore
    let index: Int
    var skins: [Any] { store.player["skin"] as? [Any] ?? [] }
    var path: String { skins.indices.contains(index) ? (skins[index] as? [String: Any])?["path"] as? String ?? "" : "" }
    var body: some View {
        HStack { Text(path.isEmpty ? "기본 스킨" : path).lineLimit(2).textSelection(.enabled); Spacer(); Button("선택…") {
            let panel = NSOpenPanel(); panel.canChooseDirectories = false; panel.canChooseFiles = true; panel.prompt = "선택"
            panel.directoryURL = store.resolved(store.system["skinpath"] as? String ?? "skin")
            if panel.runModal() == .OK, let url = panel.url {
                var copy = skins
                while copy.count <= index { copy.append(NSNull()) }
                copy[index] = ["path":url.path, "properties":["option":[],"file":[],"offset":[]]] as [String:Any]
                store.player["skin"] = copy
            }
        } }
    }
}

struct IRSettings: View {
    @EnvironmentObject var store: SettingsStore
    var configs: [[String: Any]] { store.player["irconfig"] as? [[String: Any]] ?? [] }
    func binding(_ i: Int, _ key: String) -> Binding<String> {
        Binding(get: { configs.indices.contains(i) ? configs[i][key] as? String ?? "" : "" }, set: { var copy = configs; guard copy.indices.contains(i) else { return }; copy[i][key] = $0; copy[i]["c" + key] = ""; store.player["irconfig"] = copy })
    }
    var body: some View {
        if configs.isEmpty {
            Label("설치된 순위 서비스가 없습니다", systemImage: "globe").foregroundStyle(.secondary)
            Text("기존 플레이어에 등록된 서비스가 있으면 해당 계정 설정이 여기에 표시됩니다.").font(.caption).foregroundStyle(.secondary)
        } else {
            ForEach(configs.indices, id: \.self) { i in
                Text(configs[i]["irname"] as? String ?? "순위 서비스").font(.headline)
                TextField("사용자 이름", text: binding(i,"userid"))
                SecureField("비밀번호", text: binding(i,"password"))
            }
        }
    }
}

struct ControllerSettings: View {
    @EnvironmentObject var store: SettingsStore
    let mode: String
    var controllers: [[String: Any]] { store.value("p.\(mode).controller") as? [[String: Any]] ?? [] }
    func binding<T>(_ index: Int, _ key: String, _ fallback: T) -> Binding<T> {
        Binding(get: { controllers[index][key] as? T ?? fallback }, set: { value in var copy = controllers; copy[index][key] = value; store.set("p.\(mode).controller", copy) })
    }
    var body: some View {
        ForEach(controllers.indices, id: \.self) { i in
            Text("\(i + 1)P 컨트롤러").font(.headline)
            TextField("장치 이름", text: binding(i,"name",""))
            Toggle("아날로그 스크래치", isOn: binding(i,"analogScratch",false))
            Stepper("스크래치 인식 기준: \(controllers[i]["analogScratchThreshold"] as? Int ?? 100)", value: binding(i,"analogScratchThreshold",100), in: 1...1000)
            Picker("스크래치 방식", selection: binding(i,"analogScratchMode",0)) { Text("기본").tag(0); Text("방식 2").tag(1) }
            Stepper("입력 간격(ms): \(controllers[i]["duration"] as? Int ?? 16)", value: binding(i,"duration",16), in: 0...100)
        }
    }
}
