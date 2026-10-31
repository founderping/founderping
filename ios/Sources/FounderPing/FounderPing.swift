import Foundation
import os

let logger = Logger(subsystem: "app.founderping", category: "FounderPing")

/// The FounderPing feedback widget, running inside your app.
///
/// The widget itself is the same web widget that runs on the web; this package
/// is the shell around it. It lays a transparent WebView over each of your
/// window scenes and passes every touch the widget is not using straight
/// through, so your own UI stays live while the panel is open.
///
/// Name your project in your Info.plist:
///
/// ```xml
/// <key>FounderPingProjectKey</key>
/// <string>pk_…</string>
/// ```
///
/// start the widget as your app launches:
///
/// ```swift
/// FounderPing.start()
/// ```
///
/// and then, wherever the moment happens:
///
/// ```swift
/// FounderPing.mount("post_checkout")
/// ```
///
/// Every call is safe from any thread, at any time: it goes to the scene in
/// front, or waits for the next one to come up.
///
/// Before any of this works, add your bundle ID to **Allowed iOS Apps** in the
/// FounderPing dashboard (Settings → Project). Projects reject iOS traffic
/// until they list at least one app.
public enum FounderPing {

    /// Start the widget for the project your Info.plist names. Once, as your app
    /// launches — in `application(_:didFinishLaunchingWithOptions:)`, or your
    /// `App`'s initializer; calls after the first change nothing.
    ///
    /// Below iOS 17 it stays off, with a line in the log saying so, and every
    /// other call does nothing.
    public static func start() {
        DispatchQueue.main.async {
            if #available(iOS 17, *) {
                Overlays.start()
            } else {
                logger.warning("FounderPing runs on iOS 17 and later, so it is off on this device")
            }
        }
    }

    /// Show the touchpoint registered under `touchpoint` in the dashboard.
    public static func mount(_ touchpoint: String) {
        send("founderping.mount(\(javaScriptLiteral(touchpoint)))")
    }

    /// Bind the session to one of your own user ids, or pass nil on logout to
    /// fall back to an anonymous session.
    public static func setUser(_ identifier: String?) {
        send("founderping.setUser(\(javaScriptLiteral(["identifier": identifier])))")
    }

    /// Put this user on your Live page now, on your say-so: the step automatic
    /// go-live takes after about ten seconds on screen, so it needs no wait and
    /// works with automatic go-live turned off. Sticks as they move through your
    /// app, for about an hour. Silent by itself — pair it with `notifyIfLive`
    /// to be told.
    public static func goLive() {
        send("founderping.goLive()")
    }

    /// Tell yourself something worth your attention happened to this user: a
    /// card on your dashboard, plus Slack or Discord if the project connected
    /// either.
    ///
    /// `event` is a slug such as `checkout_failed`, and the quiet window is kept
    /// per event: the same one stays silent for `quietMs` afterwards — an hour
    /// unless you say otherwise, `0` to hear every one. `note` is one short line
    /// of your own to trail it.
    ///
    /// Only sends once the user is live, waiting if that has not happened yet
    /// and dropping it if it never does, so the ordinary call follows `goLive`.
    public static func notifyIfLive(_ event: String, note: String? = nil, quietMs: Int? = nil) {
        let options = NotifyOptions(note: note, quietMs: quietMs)
        send("founderping.notifyIfLive(\(javaScriptLiteral(event)),\(javaScriptLiteral(options)))")
    }

    /// Put the widget away for now — while a modal or full-screen view of your
    /// own is up, say. Unlike the user closing it, this starts no quiet period.
    public static func hide() {
        send("founderping.hide()")
    }

    /// Load the widget from another deployment of the hosted page, one of
    /// FounderPing's own staging or dev servers, instead of production. For
    /// testing FounderPing itself: your app has no other page to point at.
    ///
    /// Every widget already up starts over from `page`, and every one after
    /// loads it too. Call it after `start()`.
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

    /// Nil while the widget is not running, with a line in the log saying the
    /// call went nowhere.
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
