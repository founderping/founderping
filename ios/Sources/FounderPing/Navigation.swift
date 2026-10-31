enum Navigation {
    case allow, openInBrowser, block
}

/// Handing anything else to `UIApplication.open` would launch whichever app
/// claims the scheme.
private let browsableSchemes: Set<String> = ["http", "https"]

/// Where a navigation the page attempted should end up.
///
/// Split out from the `OverlayNavigationDelegate` that enforces it so it can be
/// tested without a WebView: the three outcomes are not interchangeable, and
/// picking the wrong one is either a dead link or a project key handed to a
/// stranger.
func decideNavigation(
    scheme: String?,
    host: String?,
    hosted: HostedOrigin,
    followsLink: Bool,
    inMainFrame: Bool
) -> Navigation {
    let scheme = scheme?.lowercased() ?? ""

    // The widget's own `srcdoc` iframe, and the `about:blank` any frame starts
    // out as. Never in the main frame, where it would replace the page.
    if scheme == "about" && !inMainFrame { return .allow }

    // Not something a browser should be handed.
    guard browsableSchemes.contains(scheme) else { return .block }

    // A link the user followed — in practice the privacy policy in the
    // conversation footnote. It has to leave this WebView wherever it points:
    // the overlay is transparent and has no chrome to navigate back with, and
    // the page it would replace is holding the project key. That includes the
    // hosted origin itself, which in production is where the privacy policy
    // lives: loaded in here, it paints an opaque page over the app and the
    // widget is gone.
    if followsLink { return .openInBrowser }

    // Our own load of the hosted page, and any redirect or reload on the way.
    // Only our page asks the bridge for its config, so one that lands
    // somewhere else here stays hidden.
    if hosted.matches(scheme: scheme, host: host) { return .allow }

    // Anything else off-site: a stale deployment answering /native with a
    // bounce to a marketing page, say, or a frame the page never needed.
    // Launching a browser for that would turn a misconfiguration into an app
    // that opens web pages by itself.
    return .block
}
