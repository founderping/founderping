import CoreGraphics
import Foundation

/// The part of the WebView the widget currently claims.
///
/// The widget paints into a transparent WebView that covers the app. Left
/// alone, that view would swallow every touch meant for the app underneath it,
/// so the page keeps reporting the rectangles it actually paints on and
/// everything outside them is passed through.
struct HitRegion {
    static let empty = HitRegion(rects: [])

    let rects: [CGRect]

    var isEmpty: Bool { rects.isEmpty }

    /// Half-open on the right and bottom edge, matching how the page measures.
    func contains(_ point: CGPoint) -> Bool {
        rects.contains { $0.contains(point) }
    }

    /// Parse a `setHitRegion` payload: `[{x, y, width, height}, …]` in **CSS
    /// pixels relative to the WebView**, the only unit the page can measure in.
    /// On iOS those are points already, the unit touches arrive in, so nothing
    /// needs scaling.
    ///
    /// Anything malformed yields `empty`: claiming nothing at least leaves the
    /// host app fully usable.
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
