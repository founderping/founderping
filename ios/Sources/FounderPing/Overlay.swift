import UIKit
import WebKit

/// Above anything the window stacks over the overlay later — a presented
/// sheet, an alert, a new root view controller — so the widget is drawn over it
/// from the moment it arrives. Taps follow at the next check (see `Overlays`),
/// since UIKit hit-tests in subview order whatever the layer order.
private let overlayZPosition: CGFloat = 1000

/// The widget over one window scene: a transparent `PassthroughWebView`
/// holding the hosted page, in a `PassthroughContainer` over everything in the
/// scene's window.
///
/// Inside your window rather than in one of its own. A window of its own would
/// take over the status bar and the orientations the screen may turn to while
/// it is up, both of which are your view controllers' to decide; inside yours,
/// they stay theirs, and the page's text field takes the keyboard without the
/// key window changing hands.
///
/// One per scene, kept for as long as the scene is connected, so the widget —
/// and anything the user has open in it — stays put across every screen,
/// every presentation and every trip to the background.
@available(iOS 17, *)
@MainActor
final class Overlay {

    /// Where the page in the WebView stands.
    private enum Page {
        /// Asked for, and not yet heard from.
        case loading
        /// Ours, and read its config: calls can run.
        case ready
        /// Never arrived, or its process ended. Loaded again when next wanted.
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
        onHitRegion: { [weak self] in self?.webView.hitRegion = $0 }
    )
    private lazy var navigationDelegate = OverlayNavigationDelegate(
        hosted: config.hostedOrigin,
        onLoadEnded: { [weak self] in self?.loadEnded() },
        onProcessGone: { [weak self] in self?.lose() }
    )
    private var page = Page.loading
    /// Calls made before the page could run them, replayed once it can.
    private var queued = PendingCalls()
    /// The screen the user is on, as the page knows it.
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

    /// Keeps the overlay over everything in `window`, and the page told which
    /// screen the user is on.
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

    /// The scene is in front again, and the page comes back if it was lost
    /// while the scene was out of sight. Not at the moment it went: the usual
    /// cause is iOS reclaiming memory from an app in the background, and loading
    /// the page into the pressure that just ended it could repeat on a loop. A
    /// scene coming to the front is the user wanting it, with the memory to
    /// show it.
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
        // Voice replies play without a preceding tap.
        configuration.mediaTypesRequiringUserActionForPlayback = []

        let webView = PassthroughWebView(frame: .zero, configuration: configuration)
        // Hidden until the page identifies itself over the bridge.
        webView.isHidden = true
        // The overlay must never paint a background: your app has to show
        // through everywhere the widget is not drawing.
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        // The proper way to debug this page: Safari's Develop menu, for the
        // full Web Inspector on the overlay.
        webView.isInspectable = inspectable
        return webView
    }

    /// Into `window`, over everything already there.
    private func attach(to window: UIWindow) {
        container.frame = window.bounds
        window.addSubview(container)
    }

    /// The bridge is rebuilt for every load, so the page starts out on the
    /// screen the user is on now rather than the one the scene first showed.
    private func load() {
        page = .loading
        scripts.removeAllUserScripts()
        if config.debuggable { scripts.addUserScript(ConsoleRelay.script) }
        scripts.addUserScript(bridge.script(screen: screen))
        webView.load(URLRequest(url: config.hostedPage))
    }

    private func pageReady() {
        page = .ready
        // Revealed only now. A network failure or a redirect lands some *other*
        // page in this WebView, and whatever that page paints — an error page,
        // our own marketing site — would be an opaque sheet over the host app
        // while touches still pass through it, since a foreign page never
        // reports a hit region. Only our page knows to ask for a config.
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
