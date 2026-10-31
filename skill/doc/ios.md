# FounderPing for iOS — integration guide

## 1. Quick start

### 1.1 Install the package

Swift Package Manager fetches the package straight from its repository. In
Xcode, choose **File → Add Package Dependencies…**, enter
`https://github.com/founderping/founderping`, and add the **FounderPing**
library to your app's target. In a `Package.swift` of your own, that is:

```swift
dependencies: [
    .package(url: "https://github.com/founderping/founderping.git", from: "0.4.0"),
],
// …and in your target's dependencies:
.product(name: "FounderPing", package: "founderping"),
```

It compiles from source along with your app and depends on nothing but UIKit
and WebKit. Your app can support iOS 15 and later; the widget shows on iOS 17
and later, and below that the package stays off and your app runs as if it
were not installed.

Then put your project key in your app's Info.plist:

```xml
<key>FounderPingProjectKey</key>
<string>pk_your_project_key</string>
```

and start the widget as your app launches:

```swift
import FounderPing

func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
) -> Bool {
    FounderPing.start()
    return true
}
```

In a SwiftUI app, call it from your `App`'s initializer instead. That is the
whole install. The package lays its overlay over each of your window scenes as
it comes to the front, so no screen has to know. Out of the box that gives
users the sticky feedback button your project starts with and puts them on
Live; the calls below are what put it to work at your own moments.

> **Add your app to Allowed iOS Apps before you test.** FounderPing rejects
> every request from iOS until the project lists your app under
> **Settings → Project**, and an empty list is the usual reason a fresh install
> never appears. Details in §4.

### 1.2 Live — be there in person

**Live** shows you who is in your product right now so you can talk to them
yourself. Visitors appear on their own after about ten seconds of real
attention; `goLive()` is that same step on your say-so, and `notifyIfLive()` is
what actually tells you.

```swift
// Reachable for the whole session — goLive() sticks as they move between screens.
FounderPing.goLive()
```

Going live is silent, and by design: it means you *can* reach someone, not that
you have been told about them. Pair the two calls at the moments that matter:

```swift
// A checkout that just broke is worth being around for.
if payment.status == .failed {
    FounderPing.goLive()
    FounderPing.notifyIfLive("checkout_failed", note: "3rd attempt")
}
```

You get a card on the Dashboard, plus Slack or Discord if the project connected
either. The event name is the unit of quiet: the same name won't ring again for
an hour, so a visitor bouncing back and forth costs you one notification rather
than twenty. Anything your code can notice can reach you —
`pricing_viewed`, `trial_expiring`, `started_chatting` — so pick the two or
three where showing up in person changes the outcome.

On the Live page, an app user's location is the view controller in front, shown
by its class name: `/CheckoutViewController`. It is followed into navigation,
tab and split view controllers and whatever is presented over them, and kept
current as the user moves. A SwiftUI screen shows as `/UIHostingController`, so
an app built in SwiftUI shows the same one throughout.

If you'd rather choose *everyone* who becomes reachable, turn **Automatic
go-live** off under **Live → Live settings**; then `goLive()` is the only way
anyone appears.

### 1.3 Stand-in — let the AI ask

**Stand-in** is the other half: your AI holds the conversation when you can't.
You mark the moments; the Dashboard decides what gets asked there.

```swift
final class HomeViewController: UIViewController {
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        FounderPing.mount("home_landing")
    }
}
```

```swift
feedbackButton.addAction(UIAction { _ in
    FounderPing.mount("feedback_button")
}, for: .primaryActionTriggered)
```

In SwiftUI, the same calls go in `.onAppear` and in a `Button`'s action.

`mount()` says *"this moment happened"*, not *"open a dialog"* — see §2. The
first `mount` of a name the Dashboard hasn't seen registers it, so you can place
hooks before configuring anything.

Worth doing once, early: walk your product and `mount()` every moment you might
ever want feedback from — checkout complete, onboarding milestones, error
states, feature discovery. You don't have to bind a Topic to each one on day
one. What you get is a stable map of moments that anyone can wire up from the
Dashboard afterwards, without a release.

### 1.4 Identify your users

Optional, and worth it. Without this every visit is a separate anonymous
session; with it, someone's visits and devices collapse into one **User**.

```swift
FounderPing.setUser(currentUser.id)  // after login
FounderPing.setUser(nil)             // on logout
```

---

## 2. How the widget works

The widget is the same one that runs on the web, in a transparent WebView laid
over each of your window scenes, above everything else in the scene's window.
Touches it isn't using pass straight through, so your own UI stays live while
the panel is open — the widget is an overlay, not a modal. It is part of no
screen, so it stays put while the user moves between them. Links inside it,
such as the privacy policy in the conversation footnote, open in the user's
browser. The overlay itself only ever shows FounderPing's own page — anything
else is refused, with a `blocked navigation` line in the log.

That includes your own sheets and alerts, which come up under the widget. Call
`hide()` (§3) first where the widget shouldn't cover one.

One gap to know about: on iOS 17, VoiceOver's explore by touch finds only the
widget while it shows. From iOS 18 it finds your UI around the widget too.

On load it fetches your project's Topics and Touchpoint bindings once and caches
them, so every decision afterwards is instant and needs no server round-trip.
The cache refreshes on its own, which is why Dashboard edits roll out without a
release.

**Your code names moments; the Dashboard decides what happens at them.** That
split is the whole design. `mount("post_checkout")` raises a *candidate* — which
Topic fires there, under which conditions, and whether anything appears at all
is configuration, not code. So you place the calls once and retarget questions
forever after without touching your code.

Which means a `mount()` may open its Topic, open something else that fired at
the same instant, or show nothing. Between the call and the screen sit a
coalescing window, two attention guards, per-binding conditions, and a
project-wide pace — all of it in §5. The one guarantee: the user ends up with
**at most one** widget, at a sensible time.

Two things outrank all of that. A Touchpoint **you** mount onto a **Reactive**
Topic — your own "talk to founder" button — skips every gate, because the user
asked for it. And an unseen **Direct Chat** message from you takes the slot
ahead of any automatic Topic.

---

## 3. API reference

Every call is safe from any thread, at any time after `start()`: it goes to the
scene in front, or waits for the next one to come up. One made before `start()`
goes nowhere, with a line in the log saying so.

| Method | What it does |
| --- | --- |
| [`start()`](#start) | Starts the widget, once, as your app launches. |
| [`mount(_:)`](#mount) | Declares that a named Touchpoint moment just happened. |
| [`setUser(_:)`](#setuser) | Binds the session to one of your users, or clears it. |
| [`goLive()`](#golive) | Makes this user reachable in person, right now. |
| [`notifyIfLive(_:note:quietMs:)`](#notifyiflive) | Tells you something happened to a reachable user. |
| [`hide()`](#hide) | Puts the widget away without counting as a dismissal. |

### start

```swift
FounderPing.start()
```

Starts the widget for the project your Info.plist names under
`FounderPingProjectKey`. Call it once, as your app launches — in
`application(_:didFinishLaunchingWithOptions:)`, or your SwiftUI `App`'s
initializer; calls after the first change nothing. Without a project key, or
below iOS 17, the widget stays off with a line in the log saying why, and every
other call does nothing.

### mount

```swift
FounderPing.mount(_ touchpoint: String)
```

The argument is your identifier for the moment (`post_checkout`,
`home_landing`), as registered in the Dashboard. Safe to call repeatedly. Calls
landing within about half a second of each other resolve together, so a burst
can't produce a stack of dialogs.

The widget outlives your screens, so a screen can describe its own exit:
`onboarding_abandoned`, mounted as the screen is dismissed, shows over the one
the user lands on.

### setUser

```swift
FounderPing.setUser(_ identifier: String?)
```

The widget keeps one session per install and mirrors whatever you pass, live.
Takes effect immediately and survives restarts.

`identifier` is your stable id for the end user. Trimmed; empty or `nil`
resets to anonymous, which is what you want on logout.

### goLive

```swift
FounderPing.goLive()
```

Sticks as the user moves through your app, lapsing about an hour later.
Changes nothing on the visitor's screen — pair it with `notifyIfLive` to be
told.

### notifyIfLive

```swift
FounderPing.notifyIfLive(_ event: String, note: String? = nil, quietMs: Int? = nil)
```

**Only fires for a visitor who is live.** Called before `goLive()` has
connected, it waits; if the visitor never becomes reachable it is dropped, since
an alert you can't act on in person is worse than none.

| Parameter | Default | Description |
| --- | --- | --- |
| `event` | *required* | A slug for what happened, up to 32 characters matching `^[a-z0-9][a-z0-9_.-]*$`. Lowercased first, so `Pricing_Viewed` shares one quiet window with `pricing_viewed`. |
| `quietMs` | `3600000` | How long this name then stays quiet, in ms. `0` for something you always want to hear. |
| `note` | none | One short line of your own to trail the notification. Cut at 120 characters. Never becomes the subject — who the alert is about is resolved server-side, because this string is untrusted. |

### hide

```swift
FounderPing.hide()
```

For getting out of the way of your own UI — a modal opening, a full-screen view.
Unlike the user *closing* the widget this starts no quiet period, so automatic
pop-ups resume normally.

---

## 4. Setup reference

### 4.1 Project key

The **project key** (`pk_…`) ties your app to your project. It is
*publishable* — meant to ship inside your app — so there is nothing secret to
leak: abuse is held off by the Allowed iOS Apps list below and your credit
balance.
Every project starts with one; add or remove named keys (one per app or
environment, say) under **Settings → Project**.

### 4.2 Allowed iOS Apps

FounderPing only answers requests from iOS apps you list. Add your bundle ID
under **Settings → Project → Allowed iOS Apps** — the package reports it for
you. A build configuration with a bundle ID of its own, such as one ending in
`.debug`, reports that one, so list it too.

**An empty list rejects every request from iOS** — the most common reason a
fresh install never appears. A project that doesn't ship an iOS app can leave
it empty, which keeps iOS access off.

The id is self-reported, so the list identifies your app about as strongly as an
`Origin` header identifies a website: it keeps other apps out, but it is no proof
against a client that claims your id.

### 4.3 Voice input

Voice input is opt-in, and **declaring `NSMicrophoneUsageDescription` in your
Info.plist is how you opt in.** The package reads your Info.plist rather than
taking a flag, so it cannot disagree with it: declare the key and the widget
offers a mic; leave it out and there is no mic button at all. It could not
safely do otherwise, since without that key iOS ends an app the moment anything
in it touches the microphone.

```xml
<key>NSMicrophoneUsageDescription</key>
<string>Lets you talk to us instead of typing.</string>
```

There is no permission request to write. iOS asks the user, in your words, the
first time they tap the mic; if they decline, the widget tells them where to
allow it in Settings. If your app already holds the microphone, nothing
changes: the permission is your app's, so the widget has it too.

### 4.4 App-Bound Domains

If your app limits its web views to App-Bound Domains — a `WKAppBoundDomains`
array in its Info.plist — WebKit withholds the scripts and messages the package
relies on from every domain outside that array, and the widget never starts.
Add `founderping.app` to it, where it counts toward the limit of ten. An app
without that key has nothing to do.

---

## 5. Runtime reference

The mechanics behind §2. You need this when something didn't appear and you want
to know why.

### 5.1 The arbitration pipeline

```
mount("…")  →  coalesce  →  guards  →  conditions  →  pick one Topic  →  at most one widget
```

**Coalesce.** `mount()` calls landing close together — including the sticky
button's own auto-mount and the restore when the widget loads — are collected
into one window (`COALESCE_WINDOW_MS`) and resolved together. A **Proactive**
Touchpoint can also be set to fire only a fraction of the time (`probability`);
that dice roll happens here.

**Guards**, checked before any content is chosen:

- **Focus Guard** — while the user is actively engaged with a conversation
  (recently typed, recorded, scrolled history), a new auto-mount won't pull them
  out of it. Once idle past `FOCUS_GUARD_MS`, mounts can take over again.
- **Quiet Guard** — if the user explicitly *closes* the widget, nothing
  auto-opens for `QUIET_GUARD_MS`.

While a Guard holds, the widget only restores whatever the user already had.

**Conditions & pacing**, both evaluated in the end user's local context:

- **Per-binding Conditions.** A **Proactive** binding may carry time-of-day,
  weekday, and a per-user **cooldown**; *all* of them must pass. Reactive
  bindings carry none — the user opened that chat, so there is nothing to pace.
  (Exact semantics: `doc/guide.md` Appendix A.)
- **Proactive pacing.** One project-wide setting, **"Minimum time between
  proactive pop-ups"** (Dashboard → **Stand-in**), throttles every proactive
  Topic together, so a project with many Touchpoints can't gang up on one user.
  Off by default, capped at 7 days, measured from the last proactive pop-up
  wherever it appeared. It paces proactive pop-ups only; chats
  the user opens themselves and Direct Chat have their own rules.

**Picking one**, when several candidates survive:

- A moment you mounted beats the sticky button, which is reachable anyway.
- If the user is already in one of the surviving Topics, they stay there.
- Otherwise weighted-random, biased by the **weight** you set per binding.

### 5.2 Why the user-initiated paths are exempt

Every gate above exists to pace *interruptions*, so the two entry points the
user reaches for themselves are treated differently:

- **A Touchpoint you mount onto a Reactive Topic** clears every gate and has no
  Conditions to clear. It isn't sampled either — the Dashboard pins its
  `probability` at `1`, because "the button sometimes does nothing" is not a
  behaviour worth offering. It even swaps content in place if the widget is
  already open on something else.
- **The always-on sticky button** has no Conditions and is never sampled, and
  proactive pacing never applies. The Guards still do: the widget mounts that
  one by itself rather than at a moment the user chose, so a user who *closed*
  the widget stays undisturbed — otherwise closing it would do nothing.

An unseen **Direct Chat** message respects the Quiet Guard too, and only
auto-opens the panel once per conversation; after that, further messages light up
the sticky button instead of taking the screen.

### 5.3 A conversation follows the user, not the screen

Move to another screen, and the conversation stays as it was: the widget is
part of no screen, so moving between them changes nothing about it. It also
comes back after your app has been closed and opened again, the way it does
across pages on the web: open if it was open, parked on the sticky button if
minimised.

The hand-off lasts as long as `FOCUS_GUARD_MS` from the last time that chat was
shown or touched, and deliberately can't renew itself — restoring a chat isn't
engagement, so the window keeps draining while the user moves around. When it
runs out the widget returns to the sticky button, and whatever comes next is
free to raise its own pop-up. That is also why a chat from yesterday never
re-opens by itself.

One caveat: the widget's memory lives in WebView storage of its own, apart from
your app's web views, so clearing theirs — a usual step on logout — leaves it
alone. Deleting the app clears it, and the user then looks like a new one.

### 5.4 Timing constants

| Constant | Value | What it controls |
| --- | --- | --- |
| `COALESCE_WINDOW_MS` | **500 ms** | How long `mount` / restore calls are batched before the pipeline resolves them into one result. |
| `BOOTSTRAP_EXPIRES_MINUTES` | **60 minutes** | How long the cached project config is reused before the widget re-fetches it. Server-set; the client honors the bootstrap's `expire_at`. |
| `FOCUS_GUARD_MS` | **15 minutes** | How long an actively-used conversation is protected from a new `mount`. Resets on each qualifying interaction. |
| `QUIET_GUARD_MS` | **1 day** | How long the widget stays silent after the user closes it. |
| `LIVE_INITIAL_DWELL_MS` | **10 seconds** | How much *focused* time a visitor accumulates before automatic go-live lists them. Not counted with Automatic go-live off, nor when you call `goLive()`. |
| `NOTIFY_DEFAULT_QUIET_MS` | **1 hour** | Default `quietMs`: how long one event name stays silent after notifying you. Kept for the whole browser or app rather than per page, so four open tabs still cost one notification. |
| `CONVERSATION_REUSE_WINDOW_MS` | **5 minutes** | Re-firing a Topic within this window of its last showing reopens the same conversation instead of starting a fresh one. |

### 5.5 When nothing appears

In rough order of likelihood:

- **Allowed iOS Apps** doesn't list this app's bundle ID (§4.2) — nothing loads
  at all, which looks exactly like nothing firing. A debug build says so in
  Xcode's console, naming the id to add (§6.3).
- **`FounderPing.start()` is never called, or the Info.plist has no project
  key** (§1.1) — the widget doesn't start, and the log says so.
- **The widget's page didn't load** — no network on the device, say. The
  overlay stays hidden rather than show an error page, and tries again with the
  next call; Safari's Web Inspector shows what it got (§6.3).
- **The device runs iOS 16 or earlier** — the widget stays off below iOS 17
  (§1.1), and the log says so.
- **Your app uses App-Bound Domains** without `founderping.app` among them
  (§4.4).
- **A Guard is active** — the user recently closed the widget, or is still
  mid-conversation.
- **A per-binding Condition or cooldown** hasn't elapsed.
- **Proactive pacing** is still open from an earlier pop-up.
- **The Touchpoint's `probability`** rolled it out this time.

---

## 6. Troubleshooting

### 6.1 Your own UI stops responding

Touches the widget isn't using pass straight through to your UI, and if that
ever broke it would break silently: the widget looks fine while some of your
own UI has stopped responding. To check, open the panel on a screen full of
controls that visibly react — buttons that change on tap, a list that scrolls —
and see that everything the panel is not painting over still reacts. Then
present one of your own sheets: the widget should come up above it and still
take taps.

### 6.2 The widget disappears

The widget's page runs in a process of its own, which iOS ends now and then to
reclaim memory, mostly while your app is in the background. That never takes
your app with it: the widget is gone until your app next comes to the front, or
until the next call, and then loads again.

### 6.3 Seeing into the widget

The package logs under the subsystem `app.founderping`, which shows in Xcode's
console and in Console.app, and in a debug build the page's console goes there
too — worth having, because a transparent page is hard to read by eye. For
more, Safari's **Develop** menu lists the widget's page under your device or
simulator, with the full Web Inspector on it: console, network, breakpoints.
The menu is turned on in Safari's settings on your Mac, and Web Inspector in
Safari's settings on the device, both under Advanced.

The page's console and the Web Inspector are for debug builds of your app only:
a release build neither relays the one nor allows the other, so shipping the
package doesn't make your app inspectable by anyone holding a cable.
