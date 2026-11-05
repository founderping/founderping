import WebKit

/// `window.FounderPingNative`. WKWebView can't hand over a native object, so
/// this is a user script that defines one before the page's own scripts. The
/// page refuses to start without it.
@MainActor
final class NativeBridge: NSObject, WKScriptMessageHandler {

    static let global = "FounderPingNative"

    private enum Message: String, CaseIterable {
        case configRead, hitRegion
    }

    private let config: WidgetConfig
    private let onConfigRead: () -> Void
    private let onHitRegion: (HitRegion) -> Void

    init(config: WidgetConfig, onConfigRead: @escaping () -> Void, onHitRegion: @escaping (HitRegion) -> Void) {
        self.config = config
        self.onConfigRead = onConfigRead
        self.onHitRegion = onHitRegion
    }

    /// Once per WebView, since `scripts` holds on to its handlers.
    func listen(on scripts: WKUserContentController) {
        for message in Message.allCases { scripts.add(self, name: message.rawValue) }
    }

    /// The bridge for the page about to load on `screen`.
    ///
    /// `config()` returns JSON, the only shape Android's bridge can carry, and
    /// asking is the signal that the page is ours. The object only appears on
    /// the hosted origin, so a page the navigation rules let through still
    /// doesn't get the project key.
    func script(screen: String) -> WKUserScript {
        let hosted = config.hostedOrigin
        let source = """
            (function () {
              if (location.protocol !== \(javaScriptLiteral(hosted.scheme + ":")) || location.hostname !== \(javaScriptLiteral(hosted.host))) return;
              var native = window.webkit.messageHandlers;
              window.\(Self.global) = {
                screen: \(javaScriptLiteral(screen)),
                config: function () {
                  native.\(Message.configRead.rawValue).postMessage(null);
                  return \(javaScriptLiteral(configJSON(screen: screen)));
                },
                setHitRegion: function (json) { native.\(Message.hitRegion.rawValue).postMessage(json); }
              };
            })();
            """
        return WKUserScript(source: source, injectionTime: .atDocumentStart, forMainFrameOnly: true)
    }

    /// The page outlives screens, so the screen it loaded on goes stale. The
    /// widget reads this property over `config()`.
    static func screenUpdate(_ screen: String) -> String {
        "window.\(global).screen = \(javaScriptLiteral(screen))"
    }

    func configJSON(screen: String) -> String {
        javaScriptLiteral(BridgeConfig(
            projectKey: config.projectKey,
            platform: "ios",
            appId: config.appId,
            voiceInput: config.voiceInput,
            screen: screen
        ))
    }

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        // Every frame can post, the widget's iframe included. Only the hosted
        // page speaks for the widget.
        guard message.frameInfo.isMainFrame, config.hostedOrigin.matches(message.frameInfo.securityOrigin) else {
            return
        }
        switch Message(rawValue: message.name) {
        case .configRead: onConfigRead()
        case .hitRegion: onHitRegion(HitRegion.parse(message.body as? String ?? ""))
        case nil: break
        }
    }
}

/// Field names are the bootstrap's contract. Without the first three it refuses
/// to start; without a screen the Live page shows `/unknown`.
private struct BridgeConfig: Encodable {
    let projectKey: String
    let platform: String
    let appId: String
    let voiceInput: Bool
    let screen: String
}
