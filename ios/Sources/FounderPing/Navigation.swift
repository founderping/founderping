enum Navigation {
    case allow, openInBrowser, block
}

/// Handing anything else to `UIApplication.open` would launch whichever app
/// claims the scheme.
private let browsableSchemes: Set<String> = ["http", "https"]

/// Split out so it can be tested without a WebView. The wrong outcome is a dead
/// link or the project key handed on.
func decideNavigation(
    scheme: String?,
    host: String?,
    hosted: HostedOrigin,
    followsLink: Bool,
    inMainFrame: Bool
) -> Navigation {
    let scheme = scheme?.lowercased() ?? ""

    // The widget's srcdoc iframe, and the about:blank a frame starts as. Never
    // the main frame, where it would replace the page.
    if scheme == "about" && !inMainFrame { return .allow }

    guard browsableSchemes.contains(scheme) else { return .block }

    // A followed link, usually the privacy policy. It has to leave: this
    // WebView has no back chrome, and the page it would replace holds the
    // project key.
    if followsLink { return .openInBrowser }

    // Our own load, and a redirect on the way. A page that lands elsewhere here
    // stays hidden: only ours asks for a config.
    if hosted.matches(scheme: scheme, host: host) { return .allow }

    // An off-site redirect opening a browser would make a misconfigured app open pages by itself.
    return .block
}
