import UIKit
import WebKit

/// Drawn above whatever the window stacks on later. Taps follow at the next
/// check: UIKit hit-tests in subview order, ignoring zPosition.
private let overlayZPosition: CGFloat = 1000

/// The widget over one scene, for as long as the scene is connected.
///
/// Inside the host window. A window of its own would take the status bar, the
/// allowed orientations, and the key window the keyboard follows.
@available(iOS 17, *)
@MainActor
final class Overlay {

    /// Where the page in the WebView stands.
    private enum Page {
        /// Asked for, and not yet heard from.
        case loading
        /// Ours, and read its config: calls can run.
        case ready
        /// Never arrived, its widget never started, or its process ended.
        /// Loaded again when next wanted.
        case lost
    }

    private let config: WidgetConfig
    private let scripts: WKUserContentController
    private let webView: PassthroughWebView
    private let container: PassthroughContainer
    private let uiDelegate: OverlayUIDelegate
    private lazy var bridge = NativeBridge(
        config: config,
        onConfigRead: { [weak self] in self?.pageReady() },
        onHitRegion: { [weak self] in self?.webView.hitRegion = $0 },
        onStartFailed: { [weak self] in self?.lose() }
    )
    private lazy var navigationDelegate = OverlayNavigationDelegate(
        hosted: config.hostedOrigin,
        onLoadEnded: { [weak self] in self?.loadEnded() },
        onProcessGone: { [weak self] in self?.lose() }
    )
    private var page = Page.loading
    /// When the page now loading was asked for. A slow load shows up nowhere else.
    private var loadStarted = Date.now
    private var queued = PendingCalls()
    private var screen: String

    init(config: WidgetConfig, dataStore: WKWebsiteDataStore, window: UIWindow) {
        self.config = config
        let scripts = WKUserContentController()
        self.scripts = scripts
        webView = Self.newWebView(dataStore: dataStore, scripts: scripts, inspectable: config.debuggable)
        container = PassthroughContainer(holding: webView)
        container.layer.zPosition = overlayZPosition
        uiDelegate = OverlayUIDelegate(hosted: config.hostedOrigin, voiceInput: config.voiceInput)
        screen = screenName(of: window)

        bridge.listen(on: scripts)
        if config.debuggable { ConsoleRelay(hosted: config.hostedOrigin).listen(on: scripts) }
        webView.navigationDelegate = navigationDelegate
        webView.uiDelegate = uiDelegate
        attach(to: window)
        load()
    }

    func send(_ script: String) {
        if page == .lost { load() }
        if page == .ready { run(script) } else { queued.add(script) }
    }

    /// Above everything in `window`, and the page told which screen is showing.
    func cover(_ window: UIWindow) {
        if container.superview !== window {
            attach(to: window)
        } else if window.subviews.last !== container {
            window.bringSubviewToFront(container)
        }
        let shown = screenName(of: window)
        if shown != screen {
            screen = shown
            if page == .ready { run(NativeBridge.screenUpdate(shown)) }
        }
    }

    /// In front again. Reload a page lost out of sight here, not when it died:
    /// reloading into that memory pressure loops.
    func resume() {
        if page == .lost { load() }
    }

    /// Out of its window for good: the scene is gone, or the widget is starting
    /// over elsewhere.
    func remove() {
        container.removeFromSuperview()
    }

    private static func newWebView(
        dataStore: WKWebsiteDataStore,
        scripts: WKUserContentController,
        inspectable: Bool
    ) -> PassthroughWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = dataStore
        configuration.userContentController = scripts
        configuration.mediaTypesRequiringUserActionForPlayback = []
        // An iPad would browse as a Mac, which lets a pinch zoom the page away
        // from the hit region it reports.
        configuration.defaultWebpagePreferences.preferredContentMode = .mobile

        let webView = PassthroughWebView(frame: .zero, configuration: configuration)
        webView.isHidden = true
        webView.accessibilityElementsHidden = true
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        // Of two scroll views that scroll to the top, a status bar tap scrolls
        // neither, and the app's own stops answering it.
        webView.scrollView.scrollsToTop = false
        webView.isInspectable = inspectable
        return webView
    }

    /// Into `window`, over everything already there.
    private func attach(to window: UIWindow) {
        container.frame = window.bounds
        window.addSubview(container)
    }

    /// The bridge is rebuilt per load, so the page starts on the screen showing now.
    private func load() {
        page = .loading
        loadStarted = .now
        scripts.removeAllUserScripts()
        if config.debuggable { scripts.addUserScript(ConsoleRelay.script) }
        scripts.addUserScript(bridge.script(screen: screen))
        webView.load(URLRequest(url: config.hostedPage))
    }

    private func pageReady() {
        page = .ready
        logger.info("the hosted page is up, \(Date.now.timeIntervalSince(self.loadStarted), format: .fixed(precision: 1), privacy: .public) s after it was asked for")
        // A foreign page never asks for a config, and revealing it would paint
        // an opaque sheet over the app.
        webView.isHidden = false
        // The screen may have changed while the page was loading.
        run(NativeBridge.screenUpdate(screen))
        for script in queued.drain() { run(script) }
    }

    private func loadEnded() {
        guard page == .loading else { return }
        logger.warning("no widget at \(self.config.hostedPage, privacy: .public), so it is tried again when next wanted")
        lose()
    }

    /// Hidden and claiming nothing until the page is next wanted — see `resume`.
    private func lose() {
        page = .lost
        webView.isHidden = true
        webView.hitRegion = .empty
    }

    private func run(_ script: String) {
        webView.evaluateJavaScript(script, completionHandler: nil)
    }
}
