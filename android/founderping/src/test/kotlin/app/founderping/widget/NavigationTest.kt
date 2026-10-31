package app.founderping.widget

import org.junit.Assert.assertEquals
import org.junit.Test

private val HOSTED = HostedOrigin("https", "founderping.app")

class NavigationTest {

    /** Splits a URL the way `Uri` would, so cases read as the URLs they are. */
    private fun decide(
        url: String,
        hasGesture: Boolean = true,
        isRedirect: Boolean = false,
    ): Navigation {
        val scheme = url.substringBefore("://", missingDelimiterValue = "").ifEmpty { null }
        val host = if (scheme == null) null else url.substringAfter("://").substringBefore("/")
        return decideNavigation(scheme, host, HOSTED, hasGesture, isRedirect)
    }

    /** An automatic redirect, the only way the hosted page itself is ever navigated to. */
    private fun redirect(url: String) = decide(url, hasGesture = false, isRedirect = true)

    @Test
    fun `a redirect within the hosted origin stays in the WebView`() {
        assertEquals(Navigation.ALLOW, redirect("https://founderping.app/native/"))
        assertEquals(Navigation.ALLOW, redirect("https://founderping.app/?showSignIn=true"))
    }

    @Test
    fun `host comparison ignores case`() {
        assertEquals(Navigation.ALLOW, redirect("https://FounderPing.app/native"))
    }

    @Test
    fun `a tapped link to the hosted origin still goes to the browser`() {
        // In production the privacy policy is on the hosted origin. Loaded in
        // here it would replace the widget with an opaque page.
        assertEquals(Navigation.OPEN_IN_BROWSER, decide("https://founderping.app/legal/privacy"))
        assertEquals(Navigation.OPEN_IN_BROWSER, decide("https://founderping.app/legal/privacy", hasGesture = false))
    }

    @Test
    fun `the hosted host under another scheme is not the hosted page`() {
        // Plain http is a tap away from the browser, like any other web link,
        // but never somewhere to be redirected to; file: and content: read the
        // device, so they never load at all.
        assertEquals(Navigation.OPEN_IN_BROWSER, decide("http://founderping.app/native"))
        assertEquals(Navigation.BLOCK, redirect("http://founderping.app/native"))
        assertEquals(Navigation.BLOCK, decide("file://founderping.app/native"))
        assertEquals(Navigation.BLOCK, decide("content://founderping.app/native"))
    }

    @Test
    fun `a tapped off-site link goes to the browser`() {
        assertEquals(Navigation.OPEN_IN_BROWSER, decide("https://example.com/legal/privacy"))
    }

    @Test
    fun `an off-site link with no reported gesture still goes to the browser`() {
        // hasGesture() is documented to report false on a real tap in some
        // situations. A privacy link that does nothing is the worse outcome.
        assertEquals(
            Navigation.OPEN_IN_BROWSER,
            decide("https://example.com/legal/privacy", hasGesture = false),
        )
    }

    @Test
    fun `a tapped link that redirects off-site still goes to the browser`() {
        assertEquals(
            Navigation.OPEN_IN_BROWSER,
            decide("https://example.com/moved", hasGesture = true, isRedirect = true),
        )
    }

    @Test
    fun `an automatic off-site redirect is blocked`() {
        // The shape of a real incident: /native was undeployed and bounced to
        // the marketing site. That must not launch a browser by itself.
        assertEquals(Navigation.BLOCK, redirect("https://example.com/?showSignIn=true"))
    }

    @Test
    fun `non-web schemes are never handed out, even on a tap`() {
        // ACTION_VIEW will happily fire these at other apps.
        for (url in listOf("intent://evil/#Intent;scheme=x;end", "market://details?id=x", "file:///etc")) {
            assertEquals(Navigation.BLOCK, decide(url))
        }
    }

    @Test
    fun `the widget's own srcdoc iframe is not mistaken for an outbound link`() {
        assertEquals(Navigation.BLOCK, decideNavigation("about", null, HOSTED, false, false))
    }

    @Test
    fun `scheme comparison ignores case`() {
        assertEquals(Navigation.OPEN_IN_BROWSER, decide("HTTPS://example.com/x"))
    }

    @Test
    fun `a hostless url is not matched against a null hosted host`() {
        // Two nulls must not read as "same host" if the page URL was malformed.
        assertEquals(Navigation.BLOCK, decideNavigation("https", null, HostedOrigin("https", null), false, true))
    }

    @Test
    fun `a lookalike host is off-site`() {
        assertEquals(
            Navigation.OPEN_IN_BROWSER,
            decide("https://founderping.app.attacker.com/"),
        )
    }
}
