import Foundation
import Testing
import WebKit
@testable import FounderPing

private let widget = WidgetConfig(
    projectKey: "pk_test",
    appId: "com.example.app",
    hostedPage: URL(string: "https://founderping.app/native")!,
    voiceInput: true,
    debuggable: false
)

/// The bridge, live in a WebView, on a page standing in for the hosted one.
/// The page is loaded from a string, so the tests need no network: WebKit
/// treats it as coming from `origin`.
@MainActor
private final class BridgedPage: NSObject, WKNavigationDelegate {
    let webView: WKWebView
    private(set) var configReads = 0
    private(set) var hitRegion: HitRegion?
    private var loaded: CheckedContinuation<Void, Never>?

    init(screen: String) {
        let scripts = WKUserContentController()
        webView = WKWebView(frame: .zero, configuration: {
            let configuration = WKWebViewConfiguration()
            configuration.userContentController = scripts
            return configuration
        }())
        super.init()
        let bridge = NativeBridge(
            config: widget,
            onConfigRead: { [weak self] in self?.configReads += 1 },
            onHitRegion: { [weak self] in self?.hitRegion = $0 }
        )
        bridge.listen(on: scripts)
        scripts.addUserScript(bridge.script(screen: screen))
        webView.navigationDelegate = self
    }

    func load(from origin: String) async {
        await withCheckedContinuation { continuation in
            loaded = continuation
            webView.loadHTMLString("<html><body></body></html>", baseURL: URL(string: origin))
        }
    }

    func evaluate(_ script: String) async -> String? {
        await withCheckedContinuation { continuation in
            webView.evaluateJavaScript(script) { result, _ in continuation.resume(returning: result as? String) }
        }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        loaded?.resume()
        loaded = nil
    }
}

private struct ReadConfig: Decodable {
    let projectKey: String
    let platform: String
    let appId: String
    let voiceInput: Bool
    let screen: String
}

@MainActor
@Test("config tells the page whose app this is and which screen it is on")
func configContract() async throws {
    let page = BridgedPage(screen: "TasksViewController")
    await page.load(from: "https://founderping.app/native")

    let json = try #require(await page.evaluate("window.FounderPingNative.config()"))
    let config = try JSONDecoder().decode(ReadConfig.self, from: Data(json.utf8))

    #expect(config.projectKey == "pk_test")
    #expect(config.platform == "ios")
    #expect(config.appId == "com.example.app")
    #expect(config.voiceInput)
    #expect(config.screen == "TasksViewController")
}

@MainActor
@Test("reading the config is what marks the page as ours")
func configReadSignals() async {
    let page = BridgedPage(screen: "TasksViewController")
    await page.load(from: "https://founderping.app/native")
    #expect(page.configReads == 0)

    _ = await page.evaluate("window.FounderPingNative.config()")

    #expect(page.configReads == 1)
}

@MainActor
@Test("no other origin ever sees the bridge", arguments: [
    "https://example.com/native", "http://founderping.app/native",
])
func bridgeOnlyOnHostedOrigin(origin: String) async {
    let page = BridgedPage(screen: "TasksViewController")
    await page.load(from: origin)

    #expect(await page.evaluate("typeof window.FounderPingNative") == "undefined")
}

@MainActor
@Test("a screen update reaches the bridge the widget reads")
func screenUpdateLands() async {
    let page = BridgedPage(screen: "TasksViewController")
    await page.load(from: "https://founderping.app/native")
    #expect(await page.evaluate("window.FounderPingNative.screen") == "TasksViewController")

    _ = await page.evaluate(NativeBridge.screenUpdate("Settings \"Account\""))

    #expect(await page.evaluate("window.FounderPingNative.screen") == "Settings \"Account\"")
}

@MainActor
@Test("the page's hit region arrives as points")
func hitRegionArrives() async {
    let page = BridgedPage(screen: "TasksViewController")
    await page.load(from: "https://founderping.app/native")

    _ = await page.evaluate(#"window.FounderPingNative.setHitRegion('[{"x":1,"y":2,"width":3,"height":4}]')"#)

    #expect(page.hitRegion?.rects == [CGRect(x: 1, y: 2, width: 3, height: 4)])
}
