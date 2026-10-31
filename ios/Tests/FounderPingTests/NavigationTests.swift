import Foundation
import Testing
@testable import FounderPing

private let hosted = HostedOrigin(scheme: "https", host: "founderping.app")

/// A link the user followed to `url`, split the way the delegate splits it.
private func follow(_ url: String, inMainFrame: Bool = true) -> Navigation {
    decide(url, followsLink: true, inMainFrame: inMainFrame)
}

/// A load nobody tapped: our own, or a redirect or reload on the way to it.
private func load(_ url: String, inMainFrame: Bool = true) -> Navigation {
    decide(url, followsLink: false, inMainFrame: inMainFrame)
}

private func decide(_ url: String, followsLink: Bool, inMainFrame: Bool) -> Navigation {
    let parsed = URL(string: url)
    return decideNavigation(
        scheme: parsed?.scheme,
        host: parsed?.host,
        hosted: hosted,
        followsLink: followsLink,
        inMainFrame: inMainFrame
    )
}

@Test("a load within the hosted origin stays in the WebView")
func hostedLoadStays() {
    #expect(load("https://founderping.app/native") == .allow)
    #expect(load("https://founderping.app/native/") == .allow)
}

@Test("host comparison ignores case")
func hostIgnoresCase() {
    #expect(load("https://FounderPing.app/native") == .allow)
}

@Test("a tapped link to the hosted origin still goes to the browser")
func hostedLinkLeaves() {
    // In production the privacy policy is on the hosted origin. Loaded in
    // here it would replace the widget with an opaque page.
    #expect(follow("https://founderping.app/legal/privacy") == .openInBrowser)
}

@Test("a link tapped inside the widget's own iframe goes to the browser")
func linkFromWidgetFrameLeaves() {
    #expect(follow("https://example.com/legal/privacy", inMainFrame: false) == .openInBrowser)
}

@Test("the hosted host under another scheme is not the hosted page")
func otherSchemeIsElsewhere() {
    // Plain http is a tap away from the browser, like any other web link, but
    // never somewhere to be redirected to; file: reads the device instead.
    #expect(follow("http://founderping.app/native") == .openInBrowser)
    #expect(load("http://founderping.app/native") == .block)
    #expect(follow("file://founderping.app/native") == .block)
}

@Test("a tapped off-site link goes to the browser")
func offSiteLinkLeaves() {
    #expect(follow("https://example.com/legal/privacy") == .openInBrowser)
}

@Test("an off-site load is blocked")
func offSiteLoadBlocked() {
    // The shape of a real incident: /native was undeployed and bounced to the
    // marketing site. That must not launch a browser by itself.
    #expect(load("https://example.com/?showSignIn=true") == .block)
}

@Test("a frame from anywhere else never loads")
func foreignFrameBlocked() {
    #expect(load("https://tracker.example/frame", inMainFrame: false) == .block)
}

@Test("non-web schemes are never handed out, even on a tap", arguments: [
    "tel:+15555550123", "sms:+15555550123", "itms-apps://apps.apple.com/app/id1", "file:///etc/hosts", "data:text/html,hi",
])
func nonWebSchemesBlocked(url: String) {
    #expect(follow(url) == .block)
}

@Test("the widget's own srcdoc iframe loads")
func srcdocFrameLoads() {
    #expect(load("about:srcdoc", inMainFrame: false) == .allow)
    #expect(load("about:blank", inMainFrame: false) == .allow)
}

@Test("an about: page never replaces the hosted page")
func aboutNeverInMainFrame() {
    #expect(load("about:blank") == .block)
    #expect(follow("about:blank") == .block)
}

@Test("scheme comparison ignores case")
func schemeIgnoresCase() {
    #expect(follow("HTTPS://example.com/x") == .openInBrowser)
    #expect(load("HTTPS://founderping.app/native") == .allow)
}

@Test("a hostless url is not matched against an unknown hosted host")
func hostlessNeverMatches() {
    // Two unknowns must not read as "same host" if the page URL was malformed.
    let decision = decideNavigation(
        scheme: "https",
        host: nil,
        hosted: HostedOrigin(scheme: "https", host: nil),
        followsLink: false,
        inMainFrame: true
    )
    #expect(decision == .block)
}

@Test("a lookalike host is off-site")
func lookalikeIsOffSite() {
    #expect(load("https://founderping.app.attacker.com/") == .block)
    #expect(follow("https://founderping.app.attacker.com/") == .openInBrowser)
}
