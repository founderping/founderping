import CoreGraphics
import Foundation

/// The rectangles the widget claims. Everything else passes through.
struct HitRegion {
    static let empty = HitRegion(rects: [])

    let rects: [CGRect]

    var isEmpty: Bool { rects.isEmpty }

    /// Half-open on the right and bottom edge, matching how the page measures.
    func contains(_ point: CGPoint) -> Bool {
        rects.contains { $0.contains(point) }
    }

    /// `[{x, y, width, height}, …]` in CSS pixels, which are points here, so
    /// nothing scales. Malformed yields `empty`.
    static func parse(_ json: String) -> HitRegion {
        guard let items = (try? JSONSerialization.jsonObject(with: Data(json.utf8))) as? [Any] else {
            return .empty
        }
        return HitRegion(rects: items.compactMap { item in
            guard let rect = item as? [String: Any] else { return nil }
            func number(_ key: String) -> Double { (rect[key] as? NSNumber)?.doubleValue ?? 0 }
            let width = number("width"), height = number("height")
            guard width > 0, height > 0 else { return nil }
            return CGRect(x: number("x"), y: number("y"), width: width, height: height)
        })
    }
}
