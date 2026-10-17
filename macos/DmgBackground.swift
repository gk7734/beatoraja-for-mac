// usage: swift DmgBackground.swift <out.png> <scale> <icon.icns>
// Background of the installer window (660 x 400 points) in the style of JetBrains' installers: white, a small
// brand mark top left, and ".dragTo(" ... ")" in monospace around the Applications folder. Icon positions are in
// dmg-settings.py (app at x 140, Applications at x 475, both centred at y 170 from the top, so the
// whole "app .dragTo( folder )" row sits in the middle of the window).
import AppKit

let args = CommandLine.arguments
let scale = CGFloat(Double(args[2]) ?? 1)
let W: CGFloat = 660, H: CGFloat = 400
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(W * scale), pixelsHigh: Int(H * scale),
                           bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                           colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
rep.size = NSSize(width: W, height: H)
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)

NSColor.white.setFill()
NSRect(x: 0, y: 0, width: W, height: H).fill()

// brand mark: app icon + name, top left
let markY = H - 44
if args.count > 3, let icon = NSImage(contentsOfFile: args[3]) {
    icon.draw(in: NSRect(x: 30, y: markY - 1, width: 20, height: 20))
}
let mark: [NSAttributedString.Key: Any] = [.font: NSFont.systemFont(ofSize: 15, weight: .bold), .foregroundColor: NSColor.black]
("beatoraja for Mac" as NSString).draw(at: NSPoint(x: 56, y: markY), withAttributes: mark)

// ".dragTo(" between the app and the folder, ")" after the folder, on the icons' centre line
let mono: [NSAttributedString.Key: Any] = [.font: NSFont.monospacedSystemFont(ofSize: 29, weight: .regular),
                                           .foregroundColor: NSColor.black]
let lineY = H - 170
let call = ".dragTo(" as NSString
let size = call.size(withAttributes: mono)
call.draw(at: NSPoint(x: 392 - size.width, y: lineY - size.height / 2), withAttributes: mono)
let close = ")" as NSString
close.draw(at: NSPoint(x: 534, y: lineY - size.height / 2), withAttributes: mono)

NSGraphicsContext.restoreGraphicsState()
try rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: args[1]))
