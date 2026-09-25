import AppKit
import SwiftUI

@MainActor final class SettingsStore: ObservableObject {
    @Published var system: [String: Any] = [:]
    @Published var player: [String: Any] = [:]
    @Published var profiles: [String] = []
    @Published var busy = false
    @Published var status = "플레이할 준비가 되었습니다"
    @Published var error: String?
    let dataURL: URL
    let appURL: URL
    private var child: Process?
    private var logHandle: FileHandle?
    private let fm = FileManager.default
    private var ready = false
    var profileID: String { system["playername"] as? String ?? "player1" }
    var playerRoot: URL { resolved(system["playerpath"] as? String ?? "player") }
    var playerURL: URL { playerRoot.appendingPathComponent(profileID).appendingPathComponent("config_player.json") }

    init(bundleURL: URL = Bundle.main.bundleURL, dataURL: URL? = nil) {
        appURL = bundleURL.appendingPathComponent("Contents/app")
        self.dataURL = dataURL ?? ProcessInfo.processInfo.environment["BEATORAJA_DATA_DIR"].map { URL(fileURLWithPath: $0) }
            ?? fm.homeDirectoryForCurrentUser.appendingPathComponent("Library/Application Support/beatoraja")
        do {
            try fm.createDirectory(at: self.dataURL, withIntermediateDirectories: true)
            for name in ["skin", "font", "defaultsound", "folder", "random"] {
                let destination = self.dataURL.appendingPathComponent(name)
                if !fm.fileExists(atPath: destination.path) { try fm.copyItem(at: appURL.appendingPathComponent(name), to: destination) }
            }
            // Older installed default skins reference a font beside the skin files.
            // Repair missing assets without replacing customized skins or existing fonts.
            let bundledFont = appURL.appendingPathComponent("font/VL-Gothic-Regular.ttf")
            for relative in ["font/VL-Gothic-Regular.ttf", "skin/default/VL-Gothic-Regular.ttf"] {
                let destination = self.dataURL.appendingPathComponent(relative)
                if !fm.fileExists(atPath: destination.path) {
                    try fm.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
                    try fm.copyItem(at: bundledFont, to: destination)
                }
            }
            for name in ["table", "screenshot"] { try fm.createDirectory(at: self.dataURL.appendingPathComponent(name), withIntermediateDirectories: true) }
            let config = self.dataURL.appendingPathComponent("config_sys.json")
            if !fm.fileExists(atPath: config.path) { try fm.copyItem(at: appURL.appendingPathComponent("defaults/config_sys.json"), to: config) }
            system = try read(config)
            guard validID(profileID) else { throw message("플레이어 이름이 올바르지 않습니다.") }
            if !fm.fileExists(atPath: playerURL.path) {
                try fm.createDirectory(at: playerURL.deletingLastPathComponent(), withIntermediateDirectories: true)
                var defaults = try read(appURL.appendingPathComponent("defaults/config_player.json"))
                defaults["id"] = profileID
                try write(defaults, to: playerURL)
            }
            try reload()
            ready = true
        } catch { self.error = "설정을 불러오지 못했습니다.\n\(error.localizedDescription)" }
    }
    func message(_ text: String) -> NSError { NSError(domain: "beatoraja", code: 1, userInfo: [NSLocalizedDescriptionKey: text]) }
    func validID(_ id: String) -> Bool { !id.isEmpty && id != "." && id != ".." && !id.contains("/") && !id.contains("\\") }
    func resolved(_ path: String) -> URL { path.hasPrefix("/") ? URL(fileURLWithPath: path) : dataURL.appendingPathComponent(path) }
    func read(_ url: URL) throws -> [String: Any] {
        guard let value = try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any] else { throw message("설정 파일 형식을 읽을 수 없습니다.") }
        return value
    }
    func write(_ value: [String: Any], to url: URL) throws {
        try JSONSerialization.data(withJSONObject: value, options: [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]).write(to: url, options: .atomic)
    }
    func reload() throws {
        system = try read(dataURL.appendingPathComponent("config_sys.json"))
        player = try read(playerURL)
        profiles = try fm.contentsOfDirectory(atPath: playerRoot.path).filter { validID($0) && fm.fileExists(atPath: playerRoot.appendingPathComponent($0).appendingPathComponent("config_player.json").path) }.sorted()
    }
    @discardableResult func save() -> Bool {
        guard ready else { return false }
        do {
            try write(player, to: playerURL)
            try write(system, to: dataURL.appendingPathComponent("config_sys.json"))
            status = "설정을 저장했습니다"
            return true
        } catch { self.error = "설정을 저장하지 못했습니다.\n\(error.localizedDescription)"; return false }
    }
    func selectProfile(_ id: String) {
        guard !busy, id != profileID, validID(id), save() else { return }
        do {
            let next = try read(playerRoot.appendingPathComponent(id).appendingPathComponent("config_player.json"))
            system["playername"] = id
            player = next
            _ = save()
        } catch { self.error = error.localizedDescription }
    }
    func addProfile() {
        guard !busy, save() else { return }
        do {
            var n = 1
            while fm.fileExists(atPath: playerRoot.appendingPathComponent("player\(n)").path) { n += 1 }
            let id = "player\(n)"
            var value = try read(appURL.appendingPathComponent("defaults/config_player.json"))
            value["id"] = id; value["name"] = "플레이어 \(n)"
            let directory = playerRoot.appendingPathComponent(id)
            try fm.createDirectory(at: directory, withIntermediateDirectories: true)
            try write(value, to: directory.appendingPathComponent("config_player.json"))
            profiles.append(id); selectProfile(id)
        } catch { self.error = error.localizedDescription }
    }
    func deleteCurrentProfile() {
        guard !busy, profiles.count > 1, validID(profileID), save() else { return }
        let removedID = profileID
        let previousSystem = system
        let configURL = dataURL.appendingPathComponent("config_sys.json")
        guard let replacement = profiles.first(where: { $0 != removedID }) else { return }
        do {
            let replacementPlayer = try read(playerRoot.appendingPathComponent(replacement).appendingPathComponent("config_player.json"))
            let directory = playerRoot.appendingPathComponent(removedID)
            var nextSystem = system
            nextSystem["playername"] = replacement
            // Persist the replacement first, so the config never points to a trashed profile.
            try write(nextSystem, to: configURL)
            do {
                try fm.trashItem(at: directory, resultingItemURL: nil)
            } catch {
                try write(previousSystem, to: configURL)
                throw error
            }
            system = nextSystem; player = replacementPlayer
            profiles.removeAll { $0 == removedID }
            status = "\(removedID)을 휴지통으로 이동했습니다"
        } catch {
            self.error = "플레이어를 삭제하지 못했습니다.\n\(error.localizedDescription)"
        }
    }
    func value(_ key: String) -> Any? {
        let pieces = key.split(separator: ".").map(String.init)
        var result: Any = pieces[0] == "p" ? player : system
        for part in pieces.dropFirst() { result = (result as? [String: Any])?[part] ?? NSNull() }
        return result is NSNull ? nil : result
    }
    func set(_ key: String, _ value: Any) {
        var parts = key.split(separator: ".").map(String.init)
        let isPlayer = parts.removeFirst() == "p"
        func change(_ object: [String: Any], _ path: ArraySlice<String>) -> [String: Any] {
            var copy = object; let head = path.first!
            copy[head] = path.count == 1 ? value : change(copy[head] as? [String: Any] ?? [:], path.dropFirst())
            return copy
        }
        if isPlayer { player = change(player, parts[...]) } else { system = change(system, parts[...]) }
    }
    func text(_ key: String, default fallback: String = "") -> Binding<String> {
        Binding(get: { self.value(key) as? String ?? fallback }, set: { self.set(key, $0) })
    }
    func flag(_ key: String) -> Binding<Bool> {
        Binding(get: { self.value(key) as? Bool ?? false }, set: { self.set(key, $0) })
    }
    func number(_ key: String, default fallback: Double = 0) -> Binding<Double> {
        Binding(get: { (self.value(key) as? NSNumber)?.doubleValue ?? fallback }, set: { self.set(key, $0) })
    }
    func choice(_ key: String, numeric: Bool = false) -> Binding<String> {
        Binding(get: { if let s = self.value(key) as? String { return s }; return (self.value(key) as? NSNumber)?.stringValue ?? "0" }, set: { self.set(key, numeric ? (Int($0) ?? 0) as Any : $0) })
    }
    func addFolder() {
        let panel = NSOpenPanel(); panel.canChooseDirectories = true; panel.canChooseFiles = false; panel.allowsMultipleSelection = true
        panel.prompt = "추가"; panel.message = "BMS 곡이 들어 있는 폴더를 선택하세요."
        if panel.runModal() == .OK {
            var paths = value("s.bmsroot") as? [String] ?? []
            for url in panel.urls where !paths.contains(url.path) { paths.append(url.path) }
            set("s.bmsroot", paths)
        }
    }
    func run(scan: Bool = false, rebuild: Bool = false) {
        guard !busy, save() else { return }
        let process = Process()
        process.executableURL = appURL.deletingLastPathComponent().appendingPathComponent("runtime/Contents/Home/bin/java")
        var args = ["-Xms256m", "-Xmx4g", "--enable-native-access=ALL-UNNAMED,javafx.graphics", "--add-modules=javafx.controls,javafx.fxml,javafx.swing", "-Djava.library.path=\(appURL.appendingPathComponent("natives").path)", "-cp", appURL.appendingPathComponent("beatoraja.jar").path]
        if scan { args += ["bms.player.beatoraja.MacMaintenance", rebuild ? "--rebuild" : "--scan"] }
        else { args.insert("-XstartOnFirstThread", at: 0); args += ["bms.player.beatoraja.MacBootstrap", "-s"] }
        process.arguments = args; process.currentDirectoryURL = dataURL
        let log = dataURL.appendingPathComponent(scan ? "scan.log" : "game.log")
        do {
            if !fm.fileExists(atPath: log.path) { fm.createFile(atPath: log.path, contents: nil) }
            let handle = try FileHandle(forWritingTo: log); try handle.seekToEnd(); logHandle = handle
            process.standardOutput = handle; process.standardError = handle
            process.terminationHandler = { [weak self] process in
                let code = process.terminationStatus
                Task { @MainActor in
                    guard let self, self.child === process else { return }
                    self.busy = false; self.child = nil; try? self.logHandle?.close(); self.logHandle = nil
                    self.status = code == 0 ? (scan ? "곡 검색을 마쳤습니다" : "게임을 종료했습니다") : "실행 중 문제가 발생했습니다"
                    if code != 0 { self.error = "실행을 완료하지 못했습니다(\(code)).\n\(log.path)에서 실행 기록을 확인할 수 있습니다." }
                    do { try self.reload() } catch { self.error = error.localizedDescription }
                }
            }
            // Publish ownership before launch: even an immediately exiting child must
            // be able to clear the matching operation without a later busy=true write.
            child = process; busy = true
            status = scan ? "곡을 검색하고 있습니다…" : "게임 실행 중"
            try process.run()
        } catch {
            child = nil; busy = false; status = "실행하지 못했습니다"
            self.error = "실행하지 못했습니다.\n\(error.localizedDescription)"
            try? logHandle?.close(); logHandle = nil
        }
    }
}
