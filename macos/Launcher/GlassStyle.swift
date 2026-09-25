import SwiftUI

struct LauncherWindowMaterial: ViewModifier {
    func body(content: Content) -> some View {
        if #available(macOS 15.0, *) {
            content.containerBackground(.ultraThinMaterial, for: .window)
        } else { content }
    }
}

