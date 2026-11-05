import Foundation

/// The Info.plist key an app names its project under.
let projectKeyInfoKey = "FounderPingProjectKey"

/// No parameters: config arrives over the bridge. Only
/// `FounderPing.useHostedPageForTesting` moves off it.
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
    /// Whether Info.plist has `NSMicrophoneUsageDescription`. Without it iOS
    /// kills the app on the first touch of the microphone, so a flag could only
    /// be wrong.
    let voiceInput: Bool
    /// The host's `DEBUG`: a package builds in the app's configuration. Release
    /// stays uninspectable and doesn't log the page console.
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
