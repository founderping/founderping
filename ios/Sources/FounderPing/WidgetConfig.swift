import Foundation

/// The Info.plist key an app names its project under.
let projectKeyInfoKey = "FounderPingProjectKey"

/// The hosted page. Pinned into the binary deliberately: it takes no parameters
/// — every piece of configuration arrives over the bridge instead — so there is
/// nothing here to get wrong, and nothing in the URL worth reading off a log.
/// Only `FounderPing.useHostedPageForTesting` moves off it.
private let productionHostedPage = URL(string: "https://founderping.app/native")!

#if DEBUG
private let isDebugBuild = true
#else
private let isDebugBuild = false
#endif

/// Everything every overlay shares, read from the app's Info.plist as it starts.
struct WidgetConfig {
    let projectKey: String
    /// The app's bundle ID, matched against the project's Allowed iOS Apps.
    let appId: String
    var hostedPage: URL
    /// Whether the app declares `NSMicrophoneUsageDescription`, which decides
    /// whether the widget offers voice input.
    ///
    /// Read off the Info.plist rather than taken as a parameter, so there is no
    /// second opinion to keep in sync: without that key iOS ends the app the
    /// moment anything in it touches the microphone, so this is a fact about
    /// the app, and any flag saying otherwise would simply be wrong. Not the
    /// same question as whether the user has allowed it, which iOS asks them on
    /// their first tap of the mic.
    let voiceInput: Bool
    /// Whether the app we are built into is a debug build. Swift packages are
    /// built in the configuration of the app that depends on them, so this is
    /// the app's `DEBUG`.
    ///
    /// Two debugging affordances hang off this, and in a shipped app both would
    /// be liabilities rather than aids: a release build must not become
    /// inspectable from Safari merely because it embedded us, and it must not
    /// narrate the page's console — which carries whatever the user is telling
    /// us — into a log that any connected cable can read.
    let debuggable: Bool

    var hostedOrigin: HostedOrigin { HostedOrigin(hostedPage) }

    /// Nil, with the fix in the log, when the Info.plist names no project.
    static func read(from bundle: Bundle = .main) -> WidgetConfig? {
        let projectKey = (bundle.object(forInfoDictionaryKey: projectKeyInfoKey) as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !projectKey.isEmpty else {
            logger.error("no project key, so FounderPing is off: add \(projectKeyInfoKey, privacy: .public) (a String, pk_…) to your app's Info.plist")
            return nil
        }
        return WidgetConfig(
            projectKey: projectKey,
            appId: bundle.bundleIdentifier ?? "",
            hostedPage: productionHostedPage,
            voiceInput: bundle.object(forInfoDictionaryKey: "NSMicrophoneUsageDescription") != nil,
            debuggable: isDebugBuild
        )
    }
}
