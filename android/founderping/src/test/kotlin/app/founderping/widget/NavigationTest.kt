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

    @Test
    fun `the hosted page stays in the WebView`() {
        assertEquals(Navigation.ALLOW, decide("https://founderping.app/native"))
        assertEquals(Navigation.ALLOW, decide("https://founderping.app/native/anything"))
    }

    @Test
    fun `host comparison ignores case`() {
        assertEquals(Navigation.ALLOW, decide("https://FounderPing.app/native"))
    }

    @Test
    fun `the hosted host under another scheme is not the hosted page`() {
        // Plain http is a tap away from the browser, like any other web link;
        // file: and content: read the device, so they never load at all.
        assertEquals(Navigation.OPEN_IN_BROWSER, decide("http://founderping.app/native"))
        assertEquals(Navigation.BLOCK, decide("file://founderping.app/native"))
        assertEquals(Navigation.BLOCK, decide("content://founderping.app/native"))
    }

    @Test
    fun `a same-host redirect is still the hosted page`() {
        assertEquals(
            Navigation.ALLOW,
            decide("https://founderping.app/?showSignIn=true", hasGesture = false, isRedirect = true),
        )
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
        assertEquals(
            Navigation.BLOCK,
            decide("https://example.com/?showSignIn=true", hasGesture = false, isRedirect = true),
        )
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
        assertEquals(Navigation.BLOCK, decideNavigation("about", null, HostedOrigin(null, null), true, false))
    }

    @Test
    fun `a lookalike host is off-site`() {
        assertEquals(
            Navigation.OPEN_IN_BROWSER,
            decide("https://founderping.app.attacker.com/"),
        )
    }
}
