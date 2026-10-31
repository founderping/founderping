import WebKit

/// Passes the microphone to voice input: only ever the microphone, and only
/// ever to the hosted page.
///
/// Declaring `NSMicrophoneUsageDescription` is your app's decision, and the
/// widget offers no mic without it. Granting here spares the user a prompt from
/// WebKit naming the hosted page, on top of the one iOS shows in your app's
/// words on their first tap of the mic. If they decline that one, the widget
/// tells them where to allow it.
@MainActor
final class OverlayUIDelegate: NSObject, WKUIDelegate {

    private let hosted: HostedOrigin
    private let voiceInput: Bool

    init(hosted: HostedOrigin, voiceInput: Bool) {
        self.hosted = hosted
        self.voiceInput = voiceInput
    }

    func webView(
        _ webView: WKWebView,
        requestMediaCapturePermissionFor origin: WKSecurityOrigin,
        initiatedByFrame frame: WKFrameInfo,
        type: WKMediaCaptureType,
        decisionHandler: @escaping @MainActor (WKPermissionDecision) -> Void
    ) {
        let refusal: String? =
            // Without the usage description, iOS ends the app the moment
            // anything in it touches the microphone.
            if !voiceInput { "the app declares no NSMicrophoneUsageDescription" }
            else if type != .microphone { "only the microphone is ever passed on" }
            else if !hosted.matches(origin) { "\(origin.protocol)://\(origin.host) is not the hosted page" }
            else { nil }
        if let refusal {
            logger.warning("denied media capture: \(refusal, privacy: .public)")
        }
        decisionHandler(refusal == nil ? .grant : .deny)
    }
}
