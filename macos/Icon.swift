import AppKit
let image = NSImage(size: NSSize(width: 1024, height: 1024))
image.lockFocus()
let rect = NSRect(x: 64, y: 64, width: 896, height: 896)
let shape = NSBezierPath(roundedRect: rect, xRadius: 200, yRadius: 200)
NSGradient(starting: NSColor(calibratedRed: 0.27, green: 0.64, blue: 1, alpha: 1), ending: NSColor(calibratedRed: 0.01, green: 0.29, blue: 0.85, alpha: 1))!.draw(in: shape, angle: -90)
let string = "b." as NSString
let attrs: [NSAttributedString.Key: Any] = [.font: NSFont.systemFont(ofSize: 650, weight: .semibold), .foregroundColor: NSColor.white]
let size = string.size(withAttributes: attrs)
string.draw(at: NSPoint(x: (1024-size.width)/2, y: 170), withAttributes: attrs)
image.unlockFocus()
let png = NSBitmapImageRep(data: image.tiffRepresentation!)!.representation(using: .png, properties: [:])!
try png.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
