import UIKit
import WebKit

/// Keep the hosted page in and everything else out. The page holds the project
/// key, so a wrong decision hands it on — `decideNavigation` is the tested
/// half.
@MainActor
final class OverlayNavigationDelegate: NSObject, WKNavigationDelegate {

    private let hosted: HostedOrigin
    private let onLoadEnded: () -> Void
    private let onProcessGone: () -> Void

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
            // A link that opens a new window has no target frame, and it is a page of its own.
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

    /// The process ending never takes the app with it, but the page is gone.
    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        logger.warning("the widget's web content process ended, so it is gone until it is next wanted")
        onProcessGone()
    }

    private func failed(_ error: any Error) {
        // Superseded by another load. It says nothing about the page.
        if (error as? URLError)?.code == .cancelled { return }
        logger.warning("the hosted page did not load: \(error.localizedDescription, privacy: .public)")
        onLoadEnded()
    }
}
