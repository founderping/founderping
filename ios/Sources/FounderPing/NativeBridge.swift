import WebKit

/// The object the hosted page sees as `window.FounderPingNative`, and the
/// receiving end of the calls it makes on it.
///
/// A WKWebView cannot hand the page a native object, so the bridge is a user
/// script that defines one before any of the page's own scripts run, whose
/// methods post their calls here. A page that cannot read the bridge refuses to
/// start the widget at all rather than falling back to some default, so a
/// missing bridge shows up as a blank overlay — safe, but baffling without the
/// console line the page prints.
@MainActor
final class NativeBridge: NSObject, WKScriptMessageHandler {

    /// The name the page looks the bridge up under.
    static let global = "FounderPingNative"

    /// What the bridge's methods post, under the name of each.
    private enum Message: String, CaseIterable {
        case configRead, hitRegion
    }

    private let config: WidgetConfig
    private let onConfigRead: () -> Void
    private let onHitRegion: (HitRegion) -> Void

    /// `onConfigRead` hears the page read its config — see `script(screen:)`.
    init(config: WidgetConfig, onConfigRead: @escaping () -> Void, onHitRegion: @escaping (HitRegion) -> Void) {
        self.config = config
        self.onConfigRead = onConfigRead
        self.onHitRegion = onHitRegion
    }

    /// Once per WebView, since `scripts` holds on to its handlers.
    func listen(on scripts: WKUserContentController) {
        for message in Message.allCases { scripts.add(self, name: message.rawValue) }
    }

    /// The bridge, for the page about to load on `screen`.
    ///
    /// `config()` returns the tenant configuration and the screen as a JSON
    /// string, the shape Android's bridge is limited to and the page parses on
    /// both. Reading it is also the signal that the page we loaded is *ours*:
    /// nothing else knows to ask, and the overlay stays hidden until then.
    ///
    /// The bridge only appears on the hosted origin. The navigation rules keep
    /// any other page out of the main frame already; this keeps the project key
    /// out of one even if they ever let it through.
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

    /// Tells a page that is already up that the user is now on `screen`. The
    /// page stays up across every screen in the scene, so the one it loaded on
    /// soon stops being true, and the widget reads this property over it.
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
        // Every frame in the page can post here, the widget's own iframe
        // included, and only the hosted page itself speaks for the widget.
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

/// The field names are a contract with the hosted page's bootstrap: without the
/// first three it refuses to start, and without a screen the Live page shows
/// `/unknown`.
private struct BridgeConfig: Encodable {
    let projectKey: String
    let platform: String
    let appId: String
    let voiceInput: Bool
    let screen: String
}
