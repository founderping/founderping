import UIKit
import WebKit

/// How often the overlays check on the windows they cover while a scene is in
/// front. UIKit announces neither a new view controller in front nor a view
/// stacked over ours, so both are looked for. Half a second is soon enough for
/// the Live page, and the overlay is drawn over whatever arrives in between;
/// only taps on it wait for the check.
private let checkInterval: TimeInterval = 0.5

/// Lets iOS fold the checks in with other work rather than wake up for each.
private let checkTolerance: TimeInterval = 0.1

/// Names the widget's website data store, so every launch finds the same one.
private let dataStoreIdentifier = UUID(uuidString: "4F0D6B1E-9C35-4A57-8E2B-7D1C3F5A9B60")!

/// The widget across the whole app: an `Overlay` over every window scene that
/// comes to the front, and which of them a call is for.
///
/// A call goes to the scene in front. While none is — before the first one
/// comes up, or with the app in the background — it waits for the next one.
///
/// iOS 17 and later, for the website data store of its own: the widget keeps
/// its whole session in cookies and local storage, and an app clearing its
/// own web views' data — a usual step in logging out — would otherwise turn
/// every returning user into a new one, silently.
@available(iOS 17, *)
@MainActor
final class Overlays: NSObject {

    /// Nil until `FounderPing.start()`, and after it too if the Info.plist
    /// names no project.
    private(set) static var running: Overlays?

    private var config: WidgetConfig
    private let dataStore = WKWebsiteDataStore(forIdentifier: dataStoreIdentifier)
    private var overlays: [UIWindowScene: Overlay] = [:]
    private var waiting = PendingCalls()
    private var checks: Timer?

    static func start() {
        guard running == nil, let config = WidgetConfig.read() else { return }
        running = Overlays(config: config)
    }

    private init(config: WidgetConfig) {
        self.config = config
        super.init()
        let center = NotificationCenter.default
        center.addObserver(self, selector: #selector(sceneActivated), name: UIScene.didActivateNotification, object: nil)
        center.addObserver(self, selector: #selector(sceneDisconnected), name: UIScene.didDisconnectNotification, object: nil)
        // Started with a scene already in front, which will not announce itself again.
        checkScenes()
    }

    func send(_ script: String) {
        if let front = frontOverlay() { front.send(script) } else { waiting.add(script) }
    }

    func useHostedPage(_ page: URL) {
        config.hostedPage = page
        overlays.values.forEach { $0.remove() }
        overlays.removeAll()
        checkScenes()
    }

    /// For the first time, or back from the background, the app switcher or a
    /// system alert.
    @objc private func sceneActivated(_ notification: Notification) {
        (notification.object as? UIWindowScene).flatMap { overlays[$0] }?.resume()
        checkScenes()
    }

    @objc private func sceneDisconnected(_ notification: Notification) {
        guard let scene = notification.object as? UIWindowScene else { return }
        overlays.removeValue(forKey: scene)?.remove()
    }

    /// Covers every scene in front and hands the calls waiting for one to the
    /// frontmost. Repeats for as long as any scene is in front.
    @objc private func checkScenes() {
        let inFront = scenesInFront()
        for scene in inFront { cover(scene) }
        if inFront.isEmpty {
            checks?.invalidate()
            checks = nil
            return
        }
        for script in waiting.drain() { send(script) }
        if checks == nil {
            let timer = Timer(timeInterval: checkInterval, target: self, selector: #selector(checkScenes), userInfo: nil, repeats: true)
            timer.tolerance = checkTolerance
            // In every mode, so the checks go on while the user is scrolling.
            RunLoop.main.add(timer, forMode: .common)
            checks = timer
        }
    }

    /// The overlay over the scene the user is in — of several side by side,
    /// the one holding the app's key window. Nil while no scene is in front.
    private func frontOverlay() -> Overlay? {
        let inFront = scenesInFront()
        return (inFront.first { $0.keyWindow?.isKeyWindow == true } ?? inFront.first).flatMap(cover)
    }

    /// The overlay over `scene`, kept over its main window, and made the first
    /// time there is one to go over.
    @discardableResult
    private func cover(_ scene: UIWindowScene) -> Overlay? {
        let window = mainWindow(of: scene)
        if let overlay = overlays[scene] {
            if let window { overlay.cover(window) }
            return overlay
        }
        guard let window else { return nil }
        let overlay = Overlay(config: config, dataStore: dataStore, window: window)
        overlays[scene] = overlay
        return overlay
    }
}

/// The scenes the user can see and touch right now.
@MainActor
private func scenesInFront() -> [UIWindowScene] {
    UIApplication.shared.connectedScenes
        .compactMap { $0 as? UIWindowScene }
        .filter { $0.activationState == .foregroundActive }
}

/// The window the overlay belongs in: the scene's key window, the one it is
/// showing, while that is an ordinary one. Above the normal level sit the app's
/// own alert-style windows — a passcode screen, say — which are not the
/// widget's to cover, so the overlay stays where it was underneath them.
@MainActor
private func mainWindow(of scene: UIWindowScene) -> UIWindow? {
    guard let window = scene.keyWindow, window.windowLevel == .normal else { return nil }
    return window
}
