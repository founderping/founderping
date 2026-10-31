import UIKit
import WebKit

/// Everything the overlay needs from a `WKNavigationDelegate`: keep the hosted
/// page in the WebView, keep everything else out, and notice when the page is
/// gone.
///
/// The page holds your project key in reachable JavaScript, so letting this
/// WebView navigate off-site would hand that key to whatever loaded next. Of
/// everything this shell sets up, that is the one that fails *unsafely* if it
/// is wrong — hence `decideNavigation`, which is unit-tested.
@MainActor
final class OverlayNavigationDelegate: NSObject, WKNavigationDelegate {

    private let hosted: HostedOrigin
    private let onLoadEnded: () -> Void
    private let onProcessGone: () -> Void

    /// `onLoadEnded` hears every load finish or fail, ours or not, and
    /// `onProcessGone` the page's web content process end.
    init(hosted: HostedOrigin, onLoadEnded: @escaping () -> Void, onProcessGone: @escaping () -> Void) {
        self.hosted = hosted
        self.onLoadEnded = onLoadEnded
        self.onProcessGone = onProcessGone
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor action: WKNavigationAction,
        decisionHandler: @escaping @MainActor (WKNavigationActionPolicy) -> Void
    ) {
        let url = action.request.url
        let decision = decideNavigation(
            scheme: url?.scheme,
            host: url?.host,
            hosted: hosted,
            followsLink: action.navigationType == .linkActivated,
            // None for a link that opens a new window, which is a page of its own.
            inMainFrame: action.targetFrame?.isMainFrame ?? true
        )
        switch decision {
        case .allow:
            decisionHandler(.allow)
        case .openInBrowser:
            decisionHandler(.cancel)
            if let url { UIApplication.shared.open(url) }
        case .block:
            logger.warning("blocked navigation to \(url?.absoluteString ?? "nothing", privacy: .public)")
            decisionHandler(.cancel)
        }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        onLoadEnded()
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: any Error) {
        failed(error)
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: any Error) {
        failed(error)
    }

    /// Absorbed either way — a WebView's process ending never takes the app
    /// with it on iOS — but the page is gone, and with it the widget.
    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        logger.warning("the widget's web content process ended, so it is gone until it is next wanted")
        onProcessGone()
    }

    private func failed(_ error: any Error) {
        // Another load took this one's place, so it says nothing about the page.
        if (error as? URLError)?.code == .cancelled { return }
        logger.warning("the hosted page did not load: \(error.localizedDescription, privacy: .public)")
        onLoadEnded()
    }
}
