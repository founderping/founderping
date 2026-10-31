import WebKit

/// In a debug build, the page's console — the widget's own frame included — in
/// your Xcode console.
///
/// Worth having because the page is hard to see into: it is transparent, it
/// passes touches through, and the integration mistake it explains most often,
/// a bundle ID missing from Allowed iOS Apps, it explains only there. Safari's
/// Web Inspector shows the same and more, once someone thinks to open it.
///
/// Never in a release build: the console carries whatever the user is telling
/// us.
@MainActor
final class ConsoleRelay: NSObject, WKScriptMessageHandler {

    private static let handlerName = "console"

    /// Into every frame, since the widget runs in an iframe of its own.
    static let script = WKUserScript(
        source: relaySource(handler: handlerName),
        injectionTime: .atDocumentStart,
        forMainFrameOnly: false
    )

    private let hosted: HostedOrigin

    init(hosted: HostedOrigin) {
        self.hosted = hosted
    }

    /// Once per WebView, since `scripts` holds on to its handlers.
    func listen(on scripts: WKUserContentController) {
        scripts.add(self, name: Self.handlerName)
    }

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        guard hosted.matches(message.frameInfo.securityOrigin),
              let line = message.body as? [String: Any],
              let text = line["text"] as? String
        else { return }
        switch line["level"] as? String {
        case "error": logger.error("console: \(text, privacy: .public)")
        case "warn": logger.warning("console: \(text, privacy: .public)")
        default: logger.info("console: \(text, privacy: .public)")
        }
    }
}

private func relaySource(handler: String) -> String {
    """
    (function () {
      var native = window.webkit.messageHandlers.\(handler);
      function post(level, values) {
        var text = Array.prototype.map.call(values, function (value) {
          if (value instanceof Error) {
            // WebKit's stack leaves out the message that V8's starts with.
            var stack = value.stack || "", message = String(value);
            return stack.indexOf(message) === 0 ? stack : stack ? message + "\\n" + stack : message;
          }
          if (value && typeof value === "object") {
            try { return JSON.stringify(value); } catch (e) { return String(value); }
          }
          return String(value);
        }).join(" ");
        try { native.postMessage({ level: level, text: text }); } catch (e) {}
      }
      ["log", "info", "warn", "error", "debug"].forEach(function (level) {
        var original = console[level];
        console[level] = function () {
          post(level, arguments);
          return original.apply(console, arguments);
        };
      });
      addEventListener("error", function (event) { post("error", [event.error || event.message]); });
      addEventListener("unhandledrejection", function (event) { post("error", ["unhandled rejection:", event.reason]); });
    })();
    """
}
