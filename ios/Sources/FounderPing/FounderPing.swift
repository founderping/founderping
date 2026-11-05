import Foundation
import os

let logger = Logger(subsystem: "app.founderping", category: "FounderPing")

/// Shell around the web widget: a transparent WebView over each window scene.
/// `start()` once at launch, from the Info.plist project key; calls are safe
/// from any thread and wait for a scene when none is in front. The bundle ID
/// has to be listed under Allowed iOS Apps or every request is rejected.
public enum FounderPing {

    /// Once, at launch. Later calls change nothing. Below iOS 17 it stays off.
    public static func start() {
        DispatchQueue.main.async {
            if #available(iOS 17, *) {
                Overlays.start()
            } else {
                logger.warning("FounderPing runs on iOS 17 and later, so it is off on this device")
            }
        }
    }

    /// Show the touchpoint `touchpoint`.
    public static func mount(_ touchpoint: String) {
        send("founderping.mount(\(javaScriptLiteral(touchpoint)))")
    }

    /// Bind the session to one of your user ids. Nil goes back to anonymous.
    public static func setUser(_ identifier: String?) {
        send("founderping.setUser(\(javaScriptLiteral(["identifier": identifier])))")
    }

    /// Erase this visitor's conversations, server and device. A failure leaves
    /// both, so call it again.
    public static func deleteData() {
        send("founderping.deleteData()")
    }

    /// Make this user reachable now. Silent; pair with `notifyIfLive` to be told.
    public static func goLive() {
        send("founderping.goLive()")
    }

    /// Tell yourself `event` happened, once this user is live. The same event
    /// stays quiet for `quietMs` (an hour if omitted).
    public static func notifyIfLive(_ event: String, note: String? = nil, quietMs: Int? = nil) {
        let options = NotifyOptions(note: note, quietMs: quietMs)
        send("founderping.notifyIfLive(\(javaScriptLiteral(event)),\(javaScriptLiteral(options)))")
    }

    /// Put the widget away. Unlike the user closing it, this starts no quiet period.
    public static func hide() {
        send("founderping.hide()")
    }

    /// Load `page` instead of production, including widgets already up. Call after `start()`.
    public static func useHostedPageForTesting(_ page: URL) {
        DispatchQueue.main.async {
            if #available(iOS 17, *) { running()?.useHostedPage(page) }
        }
    }

    private static func send(_ script: String) {
        DispatchQueue.main.async {
            if #available(iOS 17, *) { running()?.send(script) }
        }
    }

    /// Nil while not running; the log line says why the call went nowhere.
    @available(iOS 17, *)
    @MainActor
    private static func running() -> Overlays? {
        if Overlays.running == nil {
            logger.warning("FounderPing is not running, so the call is ignored: call FounderPing.start() first, or see the FounderPing line before this one for why it is off")
        }
        return Overlays.running
    }
}

/// Leaves out what was not given, so the widget's own defaults apply.
private struct NotifyOptions: Encodable {
    let note: String?
    let quietMs: Int?
}
