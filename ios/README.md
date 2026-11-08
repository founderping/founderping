# FounderPing for iOS

The FounderPing feedback widget inside your iOS app: the same widget that runs
on the web, in a transparent WebView over each of your window scenes. A Swift
package that depends on nothing but UIKit and WebKit.

Adding it to your app, every call, and what to check when something goes wrong
are in the [iOS integration guide](../skill/doc/ios.md), also published at
<https://founderping.app/doc/ios.md>. The short version: add this repository
as a package dependency (Xcode: File → Add Package Dependencies…), then

```xml
<!-- Info.plist -->
<key>FounderPingProjectKey</key>
<string>pk_…</string>
```

```swift
// As your app launches:
FounderPing.start()

// Then, wherever the moment happens:
FounderPing.mount("post_checkout")
```

How each part works, and why, is written up in the source, file by file.

## Building

From the repository root, where `Package.swift` has to be:

```
xcodebuild test -scheme FounderPing -destination "platform=iOS Simulator,name=iPhone 18 Pro"
```

Any iPhone simulator will do; `xcrun simctl list devices available` lists what
you have. Needs Xcode 16 or later, for Swift 6. `swift test` cannot run these:
the package is UIKit and WebKit throughout, and some of the tests drive a real
WKWebView, so they need a simulator.

## Releasing

There is nothing to build or upload. Swift Package Manager fetches packages
straight from git and reads their versions off the tags, so a `v*` tag on this
repository is the iOS release — the same tag that releases the CLI and the
skill.

That makes a tag a release the moment it is pushed, so tag a commit whose
`ios/` has been tested. And never move one: every app that resolved it has the
commit pinned in its `Package.resolved`, and a tag that now points elsewhere
fails their next resolve.
