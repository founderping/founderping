# FounderPing for Android

A reference shell that runs the FounderPing feedback widget inside your app.

It is the same widget that runs on the web, hosted in a fullscreen transparent
WebView over your activity. Touches the widget is not using pass straight
through, so **your own UI stays live while the panel is open** — the widget is
an overlay, not a modal.

Read it, copy it, or depend on it. It is deliberately small and has no
dependencies beyond the Android framework.

## Use it

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var founderping: FounderPingWidget

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        founderping = FounderPingWidget.install(this, projectKey = "pk_…")
    }

    private fun onCheckoutFinished() = founderping.mount("post_checkout")

    override fun onDestroy() {
        founderping.destroy()
        super.onDestroy()
    }
}
```

`mount` is safe to call immediately — the hosted page queues calls made before
the widget bundle finishes loading.

Two things have to be true before any of this works:

1. **Add your `applicationId` to Allowed Apps** in the dashboard, under
   Settings → Project. A project rejects all native traffic until it lists at
   least one app; native access is opt-in per project.
2. Your app needs `INTERNET`, which this library already declares.

Voice input is opt-in, and **declaring `RECORD_AUDIO` in your manifest is how you
opt in.** There is no flag for it: the library reads your manifest, so it cannot
hold an opinion that contradicts the OS. Declare it and the widget offers a mic;
leave it out and the widget has no mic button at all.

There is no flag for the reverse either, because a permission you never declared
cannot be granted — `requestPermissions` for one is refused outright. Voice being
impossible is a fact about your app, not a preference, and asking you to state it
twice only creates a way for the two answers to disagree.

Declaring it is not the same as holding it. You still have to request it at
runtime, when the user reaches for voice: the library forwards the microphone but
never asks for it, because the permission is yours and so is the moment and the
reason. Declared-but-not-granted keeps the mic button — the user can still say
yes — and the widget tells them to allow it in Settings, which is a place they
will actually find a switch.

`RECORD_AUDIO` is the only one you declare. Recording in a WebView also needs
`MODIFY_AUDIO_SETTINGS`, which this library declares for you: Chromium checks for
both together and reports no recording device if either is missing, so without it
a user who has granted the microphone still gets a refused `getUserMedia` and the
only evidence is a `cr_media` line in logcat. That one is a requirement of the
WebView we put in your app rather than a decision your user makes — it is granted
at install, never prompted, and absent from your store listing.

Links inside the widget — the privacy policy in the conversation footnote — open
in the user's browser, and need nothing from you. They cannot open in the overlay
itself: it is a transparent WebView with no back button, and the page it would
replace is holding your project key.

## This cannot take your app down

Since API 26 a WebView's renderer is a separate process, and the system kills it
when memory runs short. If the `WebViewClient` embedding it does not return true
from `onRenderProcessGone`, Android's response is to **kill the host app** — so a
WebView-based SDK that skips that one method is an SDK that closes your app on
low-memory devices, for reasons that will look nothing like its fault in a crash
report.

This shell absorbs it, for a system kill and a genuine renderer crash alike.
Google's own sample returns false on `didCrash()`; that is advice for an app that
owns its process and exactly backwards for a guest in someone else's.

Nothing is rebuilt at that moment, deliberately: the usual cause is memory
pressure on a backgrounded app, and reloading the page right then invites the
same kill again, in a loop. The overlay comes back on your next `mount()` — by
definition the foreground, and the moment it is wanted. Session history lives in
`localStorage` and survives, so a recovered widget is not an amnesiac one.

Don't take this on faith:

```kotlin
founderping.crashRendererForTesting()
```

Your app stays up, its state intact, and the next `mount()` still works.

## Verifying it on a device

Pass-through is the one part that only a real device can prove, and it fails
silently: the widget looks fine while some of your own UI has quietly stopped
responding. An emulator will not show it either, because the bug is in which
view receives the touch, not in what gets drawn.

The cheapest check is a screen full of controls that visibly react — buttons
that change on tap, a list that scrolls. Open the panel and confirm two things:
everything the panel is not painting over still reacts, and a drag begun on your
own UI still scrolls even if the panel moves under the finger.

`install()` shows nothing by itself. `mount()` is what asks for a touchpoint, so
wire it to a real moment in your app and trigger it deliberately while testing.

When something does go wrong, the overlay is inspectable the ordinary way:
`chrome://inspect` in desktop Chrome gives you full DevTools on it — console,
network, breakpoints. Web contents debugging is enabled for you, but **only when
your app is a debug build**, so shipping us does not make your release build
inspectable by anyone holding a cable.

`adb logcat -s FounderPing` is the lighter option, and in a debug build that tag
carries the page's console alongside our own lines. Worth having because the page
is hard to see into by eye: it is transparent, and anything it draws outside the
widget's own rectangles cannot be tapped, since those touches belong to your app.
A page that renders an error indicator in a corner sits there being unreadable.

In a release build we log none of it — and that is a reduction, not a gap. WebView
prints every console line to logcat by itself, under the `chromium` tag, release
builds included; handling the callback is what stops it. Your shipped app says
*less* for having integrated us.

## How the pass-through works

The overlay is a fullscreen `WebView`, which by default swallows every touch on
the app underneath. Instead, the page continuously reports the rectangles it
actually paints on, and [`PassthroughWebView`](founderping/src/main/kotlin/app/founderping/widget/PassthroughWebView.kt)
declines any `ACTION_DOWN` that lands outside them. Android then offers the
gesture to the view below.

Three things about this are easy to get wrong if you adapt it:

- **The WebView must be a sibling view in the activity's own window.** Not a
  `Dialog`, not a `PopupWindow`. Android's "try the next view down" walk stops
  at a window boundary; from a separate window your only option is the
  all-or-nothing `FLAG_NOT_TOUCHABLE`, which cannot express "claim this
  rectangle only".
- **Only `ACTION_DOWN` decides.** Android delivers the rest of a gesture to
  whoever accepted the down event, which is what keeps a drag working after it
  leaves the panel, and what stops the panel stealing a scroll that began on
  your UI.
- **The region arrives in CSS pixels**, because that is the only unit the page
  can measure in. [`HitRegion`](founderping/src/main/kotlin/app/founderping/widget/HitRegion.kt)
  scales by `displayMetrics.density` before comparing against `MotionEvent`.

## The bridge

The page takes no URL parameters. Everything it needs arrives through
`window.FounderPingNative`, registered *before* `loadUrl`:

| Direction | Member | Purpose |
|---|---|---|
| native → JS | `config()` | project key, platform, app id, as a JSON string |
| JS → native | `setHitRegion(json)` | the rects above |

A page that cannot read the bridge refuses to start the widget rather than
falling back to a default, so registering it too late shows up as a blank
overlay — safe, but confusing until you check logcat.

Reading `config()` doubles as proof that the page in this WebView is ours:
nothing else knows to ask for it. The overlay therefore starts `INVISIBLE` and
is revealed on the first call. Without that, a failed load or a redirect puts
some other page in a fullscreen view over the host app — an error page, or our
own marketing site — and it paints an opaque sheet that touches still pass
straight through, because a foreign page never reports a hit region. Any port of
this shell needs the same guard.

Every `@JavascriptInterface` method runs on the WebView's JavaScript thread,
never the UI thread. Touching a view from inside one crashes.

## Not done yet

- **The back button is unowned.** Because the widget is non-modal, it is not
  "the current screen", so today `BACK` navigates your app while the panel stays
  open. The intended rule is that an open panel closes first, which needs an
  `onAppearanceChange` callback on the bridge — the hit region alone cannot tell
  an expanded panel from a collapsed bubble.
- **Session state lives in WebView `localStorage`.** DOM storage is enabled, but
  Android reclaims WebView data under pressure, and when it does the user looks
  like a brand new one. Moving the session behind the bridge is planned.
- **No device attestation.** The app id is self-reported, so it identifies an
  app about as strongly as an `Origin` header identifies a website. Play
  Integrity is what would make it trustworthy.

## Building

```
./gradlew :founderping:assembleRelease :founderping:test
./gradlew :founderping:publishToMavenLocal   # to consume it from another build
```

Needs JDK 17 and an Android SDK with API 34.

On an arm64 Linux host, add `-Pandroid.aapt2FromMavenOverride=/usr/bin/aapt2`
(from Debian/Ubuntu's `aapt` package). Google publishes `aapt2` for x86-64 only,
so without this every resource task fails with an exec format error.
