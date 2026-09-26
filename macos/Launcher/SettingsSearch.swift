import SwiftUI

struct SettingSearchItem: Identifiable {
    let page: Page
    let title: String
    var id: String { page.rawValue + ":" + title }
    var searchableText: String {
        var text = page.rawValue + " " + title
        if page == .audio { text += " 소리 사운드 audio" }
        if ["시스템", "키음", "배경음"].contains(title) { text += " 음량 볼륨 volume" }
        if title.contains("프레임") { text += " fps 프레임레이트" }
        if title.contains("수직") { text += " vsync" }
        if title.contains("배경 영상") { text += " bga 동영상" }
        if title.contains("타이밍") { text += " 지연 latency" }
        if title.contains("폴더") { text += " 경로 디렉터리" }
        return text
    }
    static func results(for query: String) -> [SettingSearchItem] {
        let words = query.split(whereSeparator: { $0.isWhitespace }).map(String.init)
        guard !words.isEmpty else { return [] }
        return all.filter { item in words.allSatisfy { item.searchableText.localizedCaseInsensitiveContains($0) } }
    }
    // Actual settings, rather than just sidebar category names.
    static let all: [SettingSearchItem] = [
        .init(page: .display, title: "화면 모드"),
        .init(page: .display, title: "해상도"),
        .init(page: .display, title: "수직 동기화"),
        .init(page: .display, title: "최대 프레임 수 (0은 제한 없음)"),
        .init(page: .display, title: "재생"),
        .init(page: .display, title: "확대 방식"),
        .init(page: .display, title: "미스 화면 표시 시간(ms)"),
        .init(page: .audio, title: "오디오 출력"),
        .init(page: .audio, title: "출력 장치 이름 (비워 두면 기본 장치)"),
        .init(page: .audio, title: "샘플 레이트"),
        .init(page: .audio, title: "오디오 버퍼 크기"),
        .init(page: .audio, title: "동시 재생 음원 수"),
        .init(page: .audio, title: "시스템"),
        .init(page: .audio, title: "키음"),
        .init(page: .audio, title: "배경음"),
        .init(page: .audio, title: "주파수 조절"),
        .init(page: .audio, title: "배속 재생"),
        .init(page: .audio, title: "결과 화면 효과음 반복"),
        .init(page: .audio, title: "코스 결과 화면 효과음 반복"),
        .init(page: .library, title: "폴더 추가…"),
        .init(page: .library, title: "곡 검색"),
        .init(page: .library, title: "게임 시작 시 곡 목록 갱신"),
        .init(page: .library, title: "압축 파일 검색"),
        .init(page: .library, title: "곡 분석 정보 사용"),
        .init(page: .library, title: "곡 목록 다시 만들기"),
        .init(page: .select, title: "폴더 클리어 표시"),
        .init(page: .select, title: "없는 곡도 표시"),
        .init(page: .select, title: "랜덤 선택 사용"),
        .init(page: .select, title: "검색 결과 표시 개수"),
        .init(page: .select, title: "정렬"),
        .init(page: .select, title: "미리 듣기"),
        .init(page: .select, title: "패턴 미리 보기"),
        .init(page: .select, title: "아날로그 스크롤"),
        .init(page: .select, title: "스크롤 대기 시간(ms)"),
        .init(page: .select, title: "빠른 스크롤 간격(ms)"),
        .init(page: .input, title: "입력 간격(ms)"),
        .init(page: .input, title: "숫자 기능 키 · 서브키"),
        .init(page: .input, title: "마우스 스크래치 사용"),
        .init(page: .input, title: "이동 거리"),
        .init(page: .input, title: "인식 시간(ms)"),
        .init(page: .play, title: "판정 타이밍(ms)"),
        .init(page: .play, title: "판정 타이밍 자동 조절"),
        .init(page: .play, title: "판정 영역 표시"),
        .init(page: .play, title: "하이스피드"),
        .init(page: .play, title: "노트 표시 시간(ms)"),
        .init(page: .play, title: "속도 기준"),
        .init(page: .play, title: "레인 커버"),
        .init(page: .play, title: "레인 커버 높이"),
        .init(page: .play, title: "리프트"),
        .init(page: .play, title: "리프트 높이"),
        .init(page: .play, title: "히든"),
        .init(page: .play, title: "히든 높이"),
        .init(page: .play, title: "게이지"),
        .init(page: .play, title: "롱노트"),
        .init(page: .play, title: "BPM 안내"),
        .init(page: .play, title: "처리한 노트 표시"),
        .init(page: .skin, title: "배경음 폴더"),
        .init(page: .skin, title: "효과음 폴더"),
        .init(page: .general, title: "표시 이름"),
        .init(page: .general, title: "이벤트 모드"),
        .init(page: .general, title: "종료 키 누름 시간(ms)"),
        .init(page: .general, title: "창 위치와 크기 유지"),
        .init(page: .general, title: "안내 효과음"),
        .init(page: .general, title: "스크린샷을 클립보드에도 복사"),
        .init(page: .general, title: "스킨 이미지 캐시"),
        .init(page: .general, title: "설정 및 기록 폴더 열기"),
        .init(page: .online, title: "전송 재시도 횟수"),
        .init(page: .tables, title: "https://…"),
        .init(page: .stream, title: "신청곡 기능 사용 (!!req)"),
        .init(page: .stream, title: "신청곡 알림"),
        .init(page: .stream, title: "최대 신청곡 기록 수"),
        .init(page: .input, title: "키보드 키 배치 · 시작 키 · 선택 키"),
        .init(page: .input, title: "컨트롤러 · 아날로그 스크래치"),
        .init(page: .skin, title: "게임 스킨 선택"),
        .init(page: .online, title: "순위 서비스 계정 · 사용자 이름 · 비밀번호"),
        .init(page: .tables, title: "난이도표 주소 추가 · 삭제"),
    ]
}

struct SettingsSearchResults: View {
    let query: String
    let open: (SettingSearchItem) -> Void
    private var results: [SettingSearchItem] { SettingSearchItem.results(for: query) }
    var body: some View {
        if results.isEmpty {
            ContentUnavailableView.search(text: query)
        } else {
            Form {
                Section {
                    Text("설정 \(results.count)개를 찾았습니다. 항목을 선택하면 해당 설정 화면으로 이동합니다.")
                        .font(.callout).foregroundStyle(.secondary)
                }
                ForEach(Page.allCases) { page in
                    let matches = results.filter { $0.page == page }
                    if !matches.isEmpty {
                        Section(page.rawValue) {
                            ForEach(matches) { result in
                                Button { open(result) } label: {
                                    HStack {
                                        Image(systemName: page.symbol).foregroundStyle(page.color).frame(width: 22)
                                        Text(result.title).foregroundStyle(.primary)
                                        Spacer()
                                        Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
                                    }.padding(.vertical, 5).contentShape(Rectangle())
                                }.buttonStyle(.plain)
                            }
                        }
                    }
                }
            }.formStyle(.grouped).scrollContentBackground(.hidden)
        }
    }
}

struct SearchField: View {
    @Binding var text: String
    var field: some View {
        HStack(spacing: 6) {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
            TextField("설정 검색", text: $text).textFieldStyle(.plain).font(.system(size: 13))
            if !text.isEmpty {
                Button { text = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) }
                    .buttonStyle(.plain).help("검색 지우기")
            }
        }.padding(.horizontal, 10).padding(.vertical, 7)
    }
    var body: some View {
        if #available(macOS 26.0, *) { field.glassEffect(.regular, in: Capsule()) }
        else { field.background(.regularMaterial, in: Capsule()) }
    }
}
