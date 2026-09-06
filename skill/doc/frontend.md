# FounderPing Web Widget — front-end guide

## 1. Quick start

### 1.1 Install the script

One inline `<script>`, once per page — ideally in the `<head>` of a global
layout:

```html
<script>!function(u,d){var w=window,e=document;if(w.founderping)return;var v,p=new Promise(r=>v=r);w.__sr=v;w.founderping=new Proxy({},{get:(_,m)=>function(){var a=w.__sa,g=[...arguments];return a?a[m].apply(a,g):p.then(()=>w.__sa[m].apply(w.__sa,g))}});var s=e.createElement("script");s.async=1;s.src=u;if(d)for(var k in d)s.setAttribute(k,d[k]);e.head.appendChild(s)}("https://founderping.app/founderping-widget.umd.js",{"data-project-key":"pk_your_project_key"});</script>
```

It registers `window.founderping` synchronously and queues anything you call
before the real bundle lands, so there is no ready event to wait for — calling a
method on the next line is fine. The only rule is that the snippet must have run
on a page before you call `founderping.*` there.

> **Allowlist your domain before you test.** FounderPing turns away widget
> requests from origins you have not listed under **Settings → Project**, and an
> empty list is the usual reason a fresh install never appears. Details in §4.

### 1.2 Live — be there in person

**Live** shows you who is in your product right now so you can talk to them
yourself. Visitors appear on their own after about ten seconds of real
attention; `goLive()` is that same step on your say-so, and `notifyIfLive()` is
what actually tells you.

```html
<script>
  // Reachable for the whole visit — goLive() sticks across navigations.
  window.founderping.goLive();
</script>
```

Going live is silent, and by design: it means you *can* reach someone, not that
you have been told about them. Pair the two calls at the moments that matter:

```html
<script>
  // A checkout that just broke is worth being around for.
  if (payment.status === "failed") {
    window.founderping.goLive();
    window.founderping.notifyIfLive("checkout_failed", { note: "3rd attempt" });
  }
</script>
```

You get a card on the Dashboard, plus Slack or Discord if the project connected
either. The event name is the unit of quiet: the same name won't ring again for
an hour, so a visitor bouncing between pages costs you one notification rather
than twenty. Anything your page can notice can reach you —
`pricing_viewed`, `trial_expiring`, `started_chatting` — so pick the two or
three where showing up in person changes the outcome.

If you'd rather choose *everyone* who becomes reachable, turn **Automatic
go-live** off in Settings; then `goLive()` is the only way anyone appears.

### 1.3 Stand-in — let the AI ask

**Stand-in** is the other half: your AI holds the conversation when you can't.
You mark the moments; the Dashboard decides what gets asked there.

```html
<script>
  window.addEventListener("load", () => {
    window.founderping.mount("home_landing");
  });
</script>
```

```html
<button id="feedbackButton" type="button">Share feedback</button>

<script>
  document.querySelector("#feedbackButton")
    ?.addEventListener("click", () => {
      window.founderping.mount("feedback_button");
    });
</script>
```

`mount()` says *"this moment happened"*, not *"open a dialog"* — see §2. The
first `mount` of a name the Dashboard hasn't seen registers it, so you can place
hooks before configuring anything.

Worth doing once, early: walk your product and `mount()` every moment you might
ever want feedback from — checkout complete, onboarding milestones, error
states, feature discovery. You don't have to bind a Topic to each one on day
one. What you get is a stable map of moments that anyone can wire up from the
Dashboard afterwards, with no redeploy.

### 1.4 Identify your users

Optional, and worth it. Without this every visit is a separate anonymous
session; with it, someone's visits and devices collapse into one **User**.

```html
<script>
  window.founderping.setUser({ identifier: currentUser.id });  // after login
  window.founderping.setUser({ identifier: null });            // on logout
</script>
```

---

## 2. How the widget works

The widget renders in an isolated iframe, so it can't collide with your CSS or
JavaScript. On load it fetches your project's Topics and Touchpoint bindings
once and caches them, so every decision afterwards is instant and needs no
server round-trip. The cache refreshes on its own, which is why Dashboard edits
roll out without a redeploy.

**Your code names moments; the Dashboard decides what happens at them.** That
split is the whole design. `mount("post_checkout")` raises a *candidate* — which
Topic fires there, under which conditions, and whether anything appears at all
is configuration, not code. So you place the calls once and retarget questions
forever after without touching the front-end.

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

Every method is safe to call before the bundle has loaded. All of them except
`destroy()` return `founderping`, so they chain.

| Method | What it does |
| --- | --- |
| [`mount(name)`](#mountname) | Declares that a named Touchpoint moment just happened. |
| [`setUser(user)`](#setuseruser) | Binds the session to one of your users, or clears it. |
| [`goLive()`](#golive) | Makes this visitor reachable in person, right now. |
| [`notifyIfLive(event, options?)`](#notifyifliveevent-options) | Tells you something happened to a reachable visitor. |
| [`hide()`](#hide) | Puts the widget away without counting as a dismissal. |
| [`destroy()`](#destroy) | Tears the widget instance down entirely. |

### mount(name)

```ts
founderping.mount(name: string): FounderPing
```

Safe to call repeatedly. Calls landing within about half a second of each other
resolve together, so a burst can't produce a stack of dialogs.

| Parameter | Type | Default | Description |
| --- | --- | --- | --- |
| `name` | `string` | *required* | Your identifier for the moment (`post_checkout`, `home_landing`), as registered in the Dashboard. |

### setUser(user)

```ts
founderping.setUser(user?: { identifier: string | null } | null): FounderPing
```

The widget keeps one session per browser and mirrors whatever you pass, live.
Takes effect immediately and survives reloads.

| Parameter | Type | Default | Description |
| --- | --- | --- | --- |
| `user` | `{ identifier: string \| null } \| null` | `undefined` | Omit it, or pass `null`, to go anonymous. An object is taken so custom user fields can be added later without breaking the signature. |
| `user.identifier` | `string \| null` | *required* | Your stable id for the end user. Trimmed; empty or `null` resets to anonymous, which is what you want on logout. |

### goLive()

```ts
founderping.goLive(): FounderPing
```

Sticks across navigations and reloads, lapsing about an hour later. Changes
nothing on the visitor's screen — pair it with `notifyIfLive` to be told.

### notifyIfLive(event, options?)

```ts
founderping.notifyIfLive(
  event: string,
  options?: { note?: string | null; quietMs?: number },
): FounderPing
```

**Only fires for a visitor who is live.** Called before `goLive()` has
connected, it waits; if the visitor never becomes reachable it is dropped, since
an alert you can't act on in person is worse than none.

| Parameter | Type | Default | Description |
| --- | --- | --- | --- |
| `event` | `string` | *required* | A slug for what happened, up to 32 characters matching `^[a-z0-9][a-z0-9_.-]*$`. Lowercased first, so `Pricing_Viewed` shares one quiet window with `pricing_viewed`. |
| `options.quietMs` | `number` | `3600000` | How long this name then stays quiet, in ms. `0` for something you always want to hear. |
| `options.note` | `string \| null` | `null` | One short line of your own to trail the notification. Cut at 120 characters. Never becomes the subject — who the alert is about is resolved server-side, because this string is untrusted. |

### hide()

```ts
founderping.hide(): FounderPing
```

For getting out of the way of your own UI — a modal opening, a full-screen view.
Unlike the user *closing* the widget this starts no quiet period, so automatic
pop-ups resume normally.

### destroy()

```ts
founderping.destroy(): void
```

Removes the widget and its iframe, for single-page apps unmounting a whole
authenticated shell. Returns nothing — the one call that doesn't chain. Use
`hide()` for anything temporary.

---

## 4. Setup reference

### 4.1 Project key

The **project key** (`pk_…`) ties an embed to your project. It is *publishable*
— meant to sit in client-side HTML — so there is nothing secret to leak: abuse
is held off by the allowlist below, a one-time human check, and your credit
balance. Every project starts with one; add or remove named keys (one per site
or environment, say) under **Settings → Project**.

### 4.2 Allowed domains

FounderPing only answers requests from origins you allowlist. List every site
that embeds the widget under **Settings → Project**; `*.example.com` covers
subdomains and `localhost` is allowed while you develop.

**An empty allowlist blocks the widget entirely** — the most common reason a
fresh install never appears. Direct Links are exempt: they are served from
FounderPing's own page.

### 4.3 Content-Security-Policy

If your site sends a CSP, allowlist both FounderPing origins:

```
script-src  https://founderping.app;
connect-src https://*.founderping.app;
```

The bundle sits on the apex while the backend runs on subdomains, and a
`*.founderping.app` wildcard does **not** match the apex — so the two directives
name different hosts on purpose. `script-src` (which also governs
`script-src-elem`) is what lets the loader download; `connect-src` is what lets
it bootstrap and stream replies. A page with no CSP needs none of this.

This is your site granting permission to load FounderPing — the mirror image of
§4.2, where you grant FounderPing permission to accept your site's calls. A
working widget needs both directions.

---

## 5. Runtime reference

The mechanics behind §2. You need this when something didn't appear and you want
to know why.

### 5.1 The arbitration pipeline

```
mount("…")  →  coalesce  →  guards  →  conditions  →  pick one Topic  →  at most one widget
```

**Coalesce.** `mount()` calls landing close together — including the sticky
button's own auto-mount and a page-load restore — are collected into one window
(`COALESCE_WINDOW_MS`) and resolved together. A **Proactive** Touchpoint can also
be set to fire only a fraction of the time (`probability`); that dice roll
happens here.

**Guards**, checked before any content is chosen:

- **Focus Guard** — while the user is actively engaged with a conversation
  (recently typed, recorded, scrolled history), a new auto-mount won't pull them
  out of it. Once idle past `FOCUS_GUARD_MS`, mounts can take over again.
- **Quiet Guard** — if the user explicitly *closes* the widget, nothing
  auto-opens for `QUIET_GUARD_MS`.

While a Guard holds, a page load just restores whatever the user already had.

**Conditions & pacing**, both evaluated in the end user's local context:

- **Per-binding Conditions.** A **Proactive** binding may carry time-of-day,
  weekday, and a per-user **cooldown**; *all* of them must pass. Reactive
  bindings carry none — the user opened that chat, so there is nothing to pace.
  (Exact semantics: `doc/guide.md` Appendix A.)
- **Proactive pacing.** One project-wide setting, **"Minimum time between
  proactive pop-ups"** (Dashboard → **Stand-in**), throttles every proactive
  Topic together, so a project with many Touchpoints can't gang up on one user.
  Off by default, capped at 7 days, measured from the last proactive pop-up — a
  page refresh doesn't restart the clock. It paces proactive pop-ups only; chats
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

### 5.3 A conversation follows the user, not the page

Click through to another page or refresh, and the same conversation comes back
as it was: open if it was open, parked on the sticky button if minimised. The
new page just has to load the widget.

The hand-off lasts as long as `FOCUS_GUARD_MS` from the last time that chat was
shown or touched, and deliberately can't renew itself — restoring a chat isn't
engagement, so the window keeps draining while the user browses. When it runs
out the screen returns to the sticky button, and a new page is free to raise its
own pop-up. That is also why a chat from yesterday never re-opens by itself.

One caveat: the widget's memory is per browser origin, so a conversation doesn't
carry across domains (`www.example.com` → `app.example.com` starts clean).

### 5.4 Timing constants

| Constant | Value | What it controls |
| --- | --- | --- |
| `COALESCE_WINDOW_MS` | **500 ms** | How long `mount` / restore calls are batched before the pipeline resolves them into one result. |
| `BOOTSTRAP_EXPIRES_MINUTES` | **60 minutes** | How long the cached project config is reused before the widget re-fetches it. Server-set; the client honors the bootstrap's `expire_at`. |
| `FOCUS_GUARD_MS` | **15 minutes** | How long an actively-used conversation is protected from a new `mount`. Resets on each qualifying interaction. |
| `QUIET_GUARD_MS` | **1 day** | How long the widget stays silent after the user closes it. |
| `LIVE_INITIAL_DWELL_MS` | **10 seconds** | How much *focused* time a visitor accumulates before automatic go-live lists them. Not counted with Automatic go-live off, nor when you call `goLive()`. |
| `NOTIFY_DEFAULT_QUIET_MS` | **1 hour** | Default `quietMs`: how long one event name stays silent after notifying you. Per browser, so four open tabs still cost one notification. |
| `CONVERSATION_REUSE_WINDOW_MS` | **5 minutes** | Re-firing a Topic within this window of its last showing reopens the same conversation instead of starting a fresh one. |

### 5.5 When nothing appears

In rough order of likelihood:

1. **Allowed domains** are empty or don't cover this origin (§4.2) — nothing
   loads at all, which looks exactly like nothing firing.
2. **A Guard is active** — the user recently closed the widget, or is still
   mid-conversation.
3. **A per-binding Condition or cooldown** hasn't elapsed.
4. **Proactive pacing** is still open from an earlier pop-up.
5. **The Touchpoint's `probability`** rolled it out this time.
6. **A CSP** on your page is blocking the bundle or its connections (§4.3).
