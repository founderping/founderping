package app.founderping.widget

internal enum class Navigation { ALLOW, OPEN_IN_BROWSER, BLOCK }

/** Handing anything else to `ACTION_VIEW` would make this an intent redirector. */
private val BROWSABLE_SCHEMES = setOf("http", "https")

/**
 * Split out so it can be tested without a device. The wrong outcome is a dead
 * link or the project key handed on.
 */
internal fun decideNavigation(
    scheme: String?,
    host: String?,
    hosted: HostedOrigin,
    hasGesture: Boolean,
    isRedirect: Boolean,
): Navigation = when {
    // Also the widget's own srcdoc iframe, whose scheme is `about`.
    scheme?.lowercase() !in BROWSABLE_SCHEMES -> Navigation.BLOCK

    // A followed link, usually the privacy policy. It has to leave: this
    // WebView has no back chrome, and the page it would replace holds the
    // project key. `hasGesture` alone is not enough — it reports false on a
    // real tap in some cases, which is the dead privacy link this replaced.
    // A navigation nobody asked for is a redirect.
    hasGesture || !isRedirect -> Navigation.OPEN_IN_BROWSER

    // A redirect inside the hosted origin is still on the way to our page.
    hosted.matches(scheme, host) -> Navigation.ALLOW

    // An off-site redirect opening a browser would make a misconfigured app open pages by itself.
    else -> Navigation.BLOCK
}
