import WebKit

/// Only the microphone, only for the hosted page. Granting here skips a second
/// prompt, from WebKit, naming the hosted page.
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
            // anything touches the microphone.
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
