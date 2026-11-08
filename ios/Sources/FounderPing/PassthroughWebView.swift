import UIKit
import WebKit

/// Keeps the touches the page claims. `hitTest` is asked once, as a touch
/// begins, and the rest of that touch follows the answer.
final class PassthroughWebView: WKWebView {

    var hitRegion = HitRegion.empty {
        // VoiceOver skips hitTest, so an empty claim would still be read out over the whole app.
        didSet { accessibilityElementsHidden = hitRegion.isEmpty }
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        hitRegion.contains(point) ? super.hitTest(point, with: event) : nil
    }

    /// VoiceOver asks separately, and only from iOS 18. Left to the page, the
    /// app underneath can't be explored while the widget is up.
    @available(iOS 18, *)
    override func accessibilityHitTest(_ point: CGPoint, event: UIEvent?) -> Any? {
        hitRegion.contains(point) ? super.accessibilityHitTest(point, event: event) : nil
    }
}
