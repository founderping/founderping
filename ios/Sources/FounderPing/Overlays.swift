import UIKit
import WebKit

/// UIKit announces neither a new front view controller nor a view stacked over
/// ours, so both are looked for. The overlay is already drawn above; only taps
/// wait for the check.
private let checkInterval: TimeInterval = 0.5

/// Lets iOS fold the checks in with other work rather than wake up for each.
private let checkTolerance: TimeInterval = 0.1

/// Names the widget's website data store, so every launch finds the same one.
private let dataStoreIdentifier = UUID(uuidString: "4F0D6B1E-9C35-4A57-8E2B-7D1C3F5A9B60")!

/// One `Overlay` per window scene in front. A call with none waits for the next.
///
/// iOS 17 for a website data store of its own: the session lives in web storage,
/// and an app clearing its own web views' data on logout would otherwise make
/// every returning user a new one.
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
        // A scene already in front will not announce itself again.
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

    /// Covers every scene in front and delivers waiting calls. Repeats while any scene is in front.
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
            // Common modes, or scrolling pauses the checks.
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

/// The scene's key window, while it is an ordinary one. An alert-level window
/// (a passcode screen) is not ours to cover.
@MainActor
private func mainWindow(of scene: UIWindowScene) -> UIWindow? {
    guard let window = scene.keyWindow, window.windowLevel == .normal else { return nil }
    return window
}
