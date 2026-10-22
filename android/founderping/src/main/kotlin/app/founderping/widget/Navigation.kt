package app.founderping.widget

internal enum class Navigation { ALLOW, OPEN_IN_BROWSER, BLOCK }

/** Handing anything else to `ACTION_VIEW` would make this an intent redirector. */
private val BROWSABLE_SCHEMES = setOf("http", "https")

/**
 * Where a navigation the page attempted should end up.
 *
 * Split out from the [OverlayClient] that enforces it so it can be tested
 * without a device: the three outcomes are not interchangeable, and picking the
 * wrong one is either a dead link or a project key handed to a stranger.
 */
internal fun decideNavigation(
    scheme: String?,
    host: String?,
    hosted: HostedOrigin,
    hasGesture: Boolean,
    isRedirect: Boolean,
): Navigation = when {
    hosted.matches(scheme, host) -> Navigation.ALLOW

    // Not something a browser should be handed. Blocking here also covers the
    // widget's own `srcdoc` iframe, whose scheme is `about`.
    scheme?.lowercase() !in BROWSABLE_SCHEMES -> Navigation.BLOCK

    // An outbound link — in practice the privacy policy in the conversation
    // footnote. It has to leave this WebView: the overlay is transparent and
    // has no chrome to navigate back with, and the page it would replace is
    // holding the project key.
    //
    // Not `hasGesture` alone, because that is documented to report false on a
    // real tap "for security reasons in certain situations" — and a privacy
    // link that silently does nothing is exactly the bug this replaced. A
    // navigation nobody asked for is a *redirect*, so accepting either signal
    // still rejects the case that matters below.
    hasGesture || !isRedirect -> Navigation.OPEN_IN_BROWSER

    // An automatic redirect off-site: a stale deployment answering /native with
    // a bounce to a marketing page, say. Launching a browser for that would
    // turn a misconfiguration into an app that opens web pages by itself.
    else -> Navigation.BLOCK
}
