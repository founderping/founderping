import UIKit
import WebKit

/// A transparent WebView that keeps only the touches the page says it painted
/// on and lets the rest reach the app underneath.
///
/// The decision is made in `hitTest`, which UIKit asks once, as a touch begins,
/// and then delivers the rest of that touch to whichever view it picked. That
/// is exactly what keeps a drag that starts on the panel working after it
/// leaves the panel — and what stops a scroll that started on the app from
/// being stolen when the panel happens to move under the finger.
final class PassthroughWebView: WKWebView {

    var hitRegion = HitRegion.empty {
        // VoiceOver does not go through hitTest, so a page claiming nothing
        // would still be read out: a layer of nothing over the whole app.
        didSet { accessibilityElementsHidden = hitRegion.isEmpty }
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        hitRegion.contains(point) ? super.hitTest(point, with: event) : nil
    }

    /// The same for what VoiceOver finds under a finger, which it asks for
    /// separately: left to the page, everywhere the widget is not drawing would
    /// answer with the page itself, and the app underneath could not be
    /// explored by touch at all while the widget is up. Public only from iOS 18.
    @available(iOS 18, *)
    override func accessibilityHitTest(_ point: CGPoint, event: UIEvent?) -> Any? {
        hitRegion.contains(point) ? super.accessibilityHitTest(point, event: event) : nil
    }
}
