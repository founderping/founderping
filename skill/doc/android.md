# FounderPing for Android — integration guide

## 1. Quick start

### 1.1 Install the library

The library is on Maven Central, which Android Studio projects already pull
from, so this step is one line:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("app.founderping:widget:0.1.0")
}
```

That fetches it prebuilt. Its source is open too, and your build can compile it
from a clone instead: §4.4 shows how.

It needs `minSdk` 24, and declares the `INTERNET` permission itself. The widget
shows on Android 8.0 (API 26) and later. On Android 7 a WebView renders inside
your app's own process, where its crash would be your app's, so there the
library stays off and your app runs as if it were not installed.

Then put your project key in `AndroidManifest.xml`, inside `<application>`:

```xml
<meta-data
    android:name="app.founderping.PROJECT_KEY"
    android:value="pk_your_project_key" />
```

That is the whole install, and it takes no code. The library starts with your
app, lays its overlay over every activity as it starts and takes it away when
the activity goes, so no screen has to know. Out of the box that gives users
the sticky feedback button your project starts with and puts them on Live; the
calls below are what put it to work at your own moments.

> **Add your app to Allowed Android Apps before you test.** FounderPing rejects
> every request from Android until the project lists your app under
> **Settings → Project**, and an empty list is the usual reason a fresh install
> never appears. Details in §4.

### 1.2 Live — be there in person

**Live** shows you who is in your product right now so you can talk to them
yourself. Visitors appear on their own after about ten seconds of real
attention; `goLive()` is that same step on your say-so, and `notifyIfLive()` is
what actually tells you.

```kotlin
// Reachable for the whole session — goLive() sticks as they move between screens.
FounderPing.goLive()
```

Going live is silent, and by design: it means you *can* reach someone, not that
you have been told about them. Pair the two calls at the moments that matter:

```kotlin
// A checkout that just broke is worth being around for.
if (payment.status == PaymentStatus.FAILED) {
    FounderPing.goLive()
    FounderPing.notifyIfLive("checkout_failed", note = "3rd attempt")
}
```

You get a card on the Dashboard, plus Slack or Discord if the project connected
either. The event name is the unit of quiet: the same name won't ring again for
an hour, so a visitor bouncing back and forth costs you one notification rather
than twenty. Anything your code can notice can reach you —
`pricing_viewed`, `trial_expiring`, `started_chatting` — so pick the two or
three where showing up in person changes the outcome.

On the Live page, an app user's location is the activity in front, shown by its
class name: `/CheckoutActivity`. Moves inside one activity — between fragments
or Compose destinations — are not reported, so a single-activity app shows the
same one throughout.

If you'd rather choose *everyone* who becomes reachable, turn **Automatic
go-live** off under **Live → Live settings**; then `goLive()` is the only way
anyone appears.

### 1.3 Stand-in — let the AI ask

**Stand-in** is the other half: your AI holds the conversation when you can't.
You mark the moments; the Dashboard decides what gets asked there.

```kotlin
class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        FounderPing.mount("home_landing")
    }
}
```

```kotlin
feedbackButton.setOnClickListener {
    FounderPing.mount("feedback_button")
}
```

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

```kotlin
FounderPing.setUser(currentUser.id)  // after login
FounderPing.setUser(null)            // on logout
```

---

## 2. How the widget works

The widget is the same one that runs on the web, in a transparent full-screen
WebView laid over each of your activities. Touches it isn't using pass straight
through, so your own UI stays live while the panel is open — the widget is an
overlay, not a modal. Links inside it, such as the privacy policy in the
conversation footnote, open in the user's browser. The overlay itself only ever
shows FounderPing's own page — anything else is refused, with a
`blocked navigation` line in logcat — and has no access to the device's files.

One gap to know about: the widget doesn't handle the back button yet. It is an
overlay rather than a screen, so `BACK` navigates your app while an open panel
stays open.

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

Every call is safe from any thread, at any time: it goes to the screen in
front, or waits for the next one to come up. From Java the same calls are
static — `FounderPing.mount("post_checkout")` — with an overload for each
optional argument.

| Method | What it does |
| --- | --- |
| [`mount(touchpoint)`](#mount) | Declares that a named Touchpoint moment just happened. |
| [`setUser(identifier)`](#setuser) | Binds the session to one of your users, or clears it. |
| [`goLive()`](#golive) | Makes this user reachable in person, right now. |
| [`notifyIfLive(event, note?, quietMs?)`](#notifyiflive) | Tells you something happened to a reachable user. |
| [`hide()`](#hide) | Puts the widget away without counting as a dismissal. |
| [`crashRendererForTesting()`](#crashrendererfortesting) | Kills the widget's renderer, for troubleshooting a crash (§6.2). |

### mount

```kotlin
FounderPing.mount(touchpoint: String)
```

The argument is your identifier for the moment (`post_checkout`,
`home_landing`), as registered in the Dashboard. Safe to call repeatedly. Calls
landing within about half a second of each other resolve together, so a burst
can't produce a stack of dialogs.

Made while the screen in front is finishing, it shows on the screen the user
lands on instead — so a screen can describe its own exit, mounting
`onboarding_abandoned` and then calling `finish()`.

### setUser

```kotlin
FounderPing.setUser(identifier: String?)
```

The widget keeps one session per install and mirrors whatever you pass, live.
Takes effect immediately and survives restarts.

`identifier` is your stable id for the end user. Trimmed; empty or `null`
resets to anonymous, which is what you want on logout.

### goLive

```kotlin
FounderPing.goLive()
```

Sticks as the user moves through your app, lapsing about an hour later.
Changes nothing on the visitor's screen — pair it with `notifyIfLive` to be
told.

### notifyIfLive

```kotlin
FounderPing.notifyIfLive(event: String, note: String? = null, quietMs: Long? = null)
```

**Only fires for a visitor who is live.** Called before `goLive()` has
connected, it waits; if the visitor never becomes reachable it is dropped, since
an alert you can't act on in person is worse than none.

The wait is held by the screen the call was made on, so make it on one the user
stays on: a screen that closes before the user is live takes the notification
with it.

| Parameter | Default | Description |
| --- | --- | --- |
| `event` | *required* | A slug for what happened, up to 32 characters matching `^[a-z0-9][a-z0-9_.-]*$`. Lowercased first, so `Pricing_Viewed` shares one quiet window with `pricing_viewed`. |
| `quietMs` | `3600000` | How long this name then stays quiet, in ms. `0` for something you always want to hear. |
| `note` | none | One short line of your own to trail the notification. Cut at 120 characters. Never becomes the subject — who the alert is about is resolved server-side, because this string is untrusted. |

### hide

```kotlin
FounderPing.hide()
```

For getting out of the way of your own UI — a modal opening, a full-screen view.
Unlike the user *closing* the widget this starts no quiet period, so automatic
pop-ups resume normally.

### crashRendererForTesting

```kotlin
FounderPing.crashRendererForTesting()
```

Kills the renderer of the widget in front, the way the system does whenever
memory runs short. The library is built to absorb that: your app carries on, and
the widget comes back with the next call or when its screen next comes to the
front. Call it to see that for yourself, or to rule the widget out when your app
goes down while it is up (§6.2).

---

## 4. Setup reference

### 4.1 Project key

The **project key** (`pk_…`) ties your app to your project. It is
*publishable* — meant to ship inside your app — so there is nothing secret to
leak: abuse is held off by the Allowed Android Apps list below and your credit
balance.
Every project starts with one; add or remove named keys (one per app or
environment, say) under **Settings → Project**.

### 4.2 Allowed Android Apps

FounderPing only answers requests from Android apps you list. Add your
`applicationId` under **Settings → Project → Allowed Android Apps** — the
library reports it for you. A build type with an
`applicationIdSuffix`, such as `.debug`, reports the suffixed id, so list that
one too.

**An empty list rejects every request from Android** — the most common reason a
fresh install never appears. A project that only embeds on the web can leave it
empty, which keeps Android access off.

The id is self-reported, so the list identifies your app about as strongly as an
`Origin` header identifies a website: it keeps other apps out, but it is no proof
against a client that claims your id.

### 4.3 Voice input

Voice input is opt-in, and **declaring `RECORD_AUDIO` in your manifest is how you
opt in.** The library reads your manifest rather than taking a flag, so it
cannot disagree with it: declare the permission and the widget offers a mic;
leave it out and there is no mic button at all.

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

There is no runtime request to write. The library asks the user the first time
they tap the mic, the moment the reason needs no explaining; if they decline,
the widget tells them where to allow it in Settings. If your app already asks
for the microphone itself, nothing changes: the library only asks when the app
doesn't hold it yet.

`RECORD_AUDIO` is the only one you declare. Recording in a WebView also needs
`MODIFY_AUDIO_SETTINGS`, which the library declares for you — a normal
permission, granted at install and never prompted. If your manifest strips
library permissions with `tools:node="remove"`, leave this one: without it the
mic fails even once the user allows it, and the only trace is a `cr_media` line
in logcat.

### 4.4 Building from source

The library's source is open, in
[github.com/founderping/founderping](https://github.com/founderping/founderping),
and your app can compile it from there instead of fetching the build on Maven
Central — to read or change it, or because you build what you ship yourself.
Clone it wherever suits, next to your project say:

```sh
git clone https://github.com/founderping/founderping.git
```

Its `android/` folder is a Gradle build of its own. Include that build in your
`settings.gradle.kts`, and Gradle compiles the library from the clone in place
of the download:

```kotlin
// settings.gradle.kts — the path is relative to this file
includeBuild("../founderping/android") {
    dependencySubstitution {
        substitute(module("app.founderping:widget")).using(project(":founderping"))
    }
}
```

Leave the `implementation("app.founderping:widget:0.1.0")` line from §1.1 as it
is: the substitution stands in for it, whatever version it names, and every
edit to the clone is in your next build. `main` is the newest source, so it can
be ahead of the release on Maven Central.

Being a build of its own, the clone needs two things from you:

- **Where your Android SDK is.** It does not read your project's
  `local.properties`: copy that file into its `android/` folder, where git
  ignores it, or set `ANDROID_HOME`. Otherwise Gradle stops with
  `SDK location not found`.
- **Your Android Gradle plugin version.** Gradle allows one per build, and the
  clone names its own: `agp` in `android/gradle/libs.versions.toml`. If yours is
  different, set it to yours; otherwise the build stops with
  `Using multiple versions of the Android Gradle plugin(…) in the same build is not allowed`.

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

Move to another screen, and the same conversation comes back as it was: open if
it was open, parked on the sticky button if minimised.

The hand-off lasts as long as `FOCUS_GUARD_MS` from the last time that chat was
shown or touched, and deliberately can't renew itself — restoring a chat isn't
engagement, so the window keeps draining while the user moves around. When it
runs out the widget returns to the sticky button, and whatever comes next is
free to raise its own pop-up. That is also why a chat from yesterday never
re-opens by itself.

One caveat: the widget's memory lives in your app's WebView storage, so whatever
clears that clears it — clearing the app's data, a reinstall, or your own app
wiping WebView storage, commonly `WebStorage.getInstance().deleteAllData()` on
logout. The user then looks like a new one.

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

- **Allowed Android Apps** doesn't list this app's id (§4.2) — nothing loads at
  all, which looks exactly like nothing firing. A debug build says so in
  logcat, naming the id to add (§6.3).
- **The manifest has no project key** (§1.1) — the library doesn't start, and
  logcat says so.
- **The widget's page didn't load** — no network on the device, say. The
  overlay stays hidden rather than show an error page; `chrome://inspect` shows
  what it got (§6.3).
- **The device runs Android 7** — the library stays off below Android 8.0
  (§1.1), and logcat says so.
- **The library turned itself off** — it does that after an error of its own
  rather than take your app down, logs the error under the `FounderPing` tag
  (§6.3), and starts afresh on the next launch.
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
and see that everything the panel is not painting over still reacts, and that a
drag begun on your own UI keeps scrolling even if the panel moves under the
finger.

A frozen widget page can't hold your UI for long either: from Android 10 the
overlay steps aside once a touch on it goes unanswered for a few seconds, and
comes back when the page recovers.

### 6.2 Your app goes down while the widget is up

The system kills a WebView's renderer whenever memory runs short, and a WebView
that mishandles it takes the whole app down. The library is built to absorb
that, and to turn itself off rather than crash should anything of its own fail,
leaving the error in logcat under the `FounderPing` tag (§6.3). To rule the
widget out, call `FounderPing.crashRendererForTesting()` (§3): your app should
stay up with its state intact, and the next `mount()` should still work.

### 6.3 Seeing into the widget

`adb logcat -s FounderPing` shows the library's own lines and, in a debug build,
the page's console alongside them — worth having, because a transparent page is
hard to read by eye. For more, `chrome://inspect` in desktop Chrome gives full
DevTools on the overlay: console, network, breakpoints.

The page's console and `chrome://inspect` are for debug builds of your app
only: a release build neither logs the one nor allows the other, so shipping the
library doesn't make your app inspectable by anyone holding a cable.
