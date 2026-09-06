# FounderPing — Product, Configuration & Implementation Guide

## 1. What FounderPing is

FounderPing is an **AI-powered user-feedback platform for product teams**. It helps you hear from the people who use your product—not through long surveys or static forms, but through **short, natural conversations** (voice or text) that an AI leads on your behalf. Each chat is turned into **structured, searchable insight**—what was said, how the user felt, what matters—so you can prioritize and act without manually reading every submission.

FounderPing chats with users **inside your app**—as a **Founder** or as a **Team Feedback Assistant**—and **summarizes the feedback** you collect.

### Problems it addresses

Most teams don’t have one feedback problem—they have **a patchwork of channels that each fail in a different way**. FounderPing is aimed at replacing that patchwork, not adding another inbox.

**Reach doesn’t scale**

- **The same small circle.** “Begging the same 15 friends” on Reddit, X, or your network—kind people, tiny sample, no growth with your real users.
- **Email into the void.** Often the only way to reach signups, yet reply rates are brutal and answers are a line or two with no follow-up.
- **Audiences you never hear from.** Churned users, onboarding drop-offs, and the silent majority rarely show up in surveys or prompts.

**What you collect stays thin**

- **Forms and star ratings nobody fills out.** Typing is a chore, so users skip it—you get a lone ⭐⭐⭐ and no story behind it.
- **Shallow one-liners when they do reply.** “It’s broken” / “I love it” without clarification still leaves you guessing.
- **Numbers, never the why.** Analytics tell you *what* moved—churn up, a step dropped—but never *why*.

**Signal is fragmented and hard to turn into decisions**

- **Feedback scattered everywhere.** Discord, support mail, DMs, GitHub issues—truth in pieces, and no one has time to merge it.
- **Synthesis doesn’t scale.** Tagging, prioritizing, and tying feedback to a user stays manual, so backlogs outgrow insight.
- **Rolling your own feedback UX is costly.** Every new question, audience, or trigger still means more frontend work and redeploys.

**Timing and trust work against you**

- **After the fact, the moment is gone.** Most feedback channels ask days or weeks later—users have already forgotten the scene, so response rates stay low and answers stay thin. FounderPing captures feedback **in the moment**, and **proactive Topics** raise the right question at the right trigger while the experience is still fresh—so more people reply, with more detail.
- **Cold outreach keeps users at arm's length.** Star ratings and anonymous surveys feel institutional; people hold back. FounderPing lets you show up inside your app as a **Founder** (your AI stand-in) or a **Team Feedback Assistant**—a warmer, more personal voice that closes the distance and earns more candid feedback.

FounderPing is built to replace that loop: **reach users in the product (and beyond), let AI dig deeper in the moment, and put summarized, tagged signal in one Dashboard**—so you’re not guessing from stars, silence, or scattered threads.

---

## 2. End-to-end workflow (developer view)

This section walks the full lifecycle from a developer’s perspective: from signing up, through configuring what to ask, to reading and acting on what comes back. Each step links to its deep-dive section.

> **Two complementary ways to operate FounderPing.** Project setup, the product doc, Topics, Touchpoints, widget keys, allowed domains, user/conversation reads, and status are available through the **Dashboard** and the **`founderping` CLI / REST API** (`/api/v1`). Live founder-to-user messaging stays in the Dashboard this release—you type it straight into the conversation viewer (see **Direct Chat**, §5.6).

### Fastest setup — one prompt with your coding agent

If you use **Cursor, Claude Code, Codex, Gemini CLI**, or a similar assistant, you can stand up FounderPing in **one line** without clicking through the Dashboard first:

1. Open your product's repo in the agent.
2. Paste a setup prompt—for example: *"Download the FounderPing skill from https://github.com/founderping/founderping, follow it, and help me set up FounderPing in this project."*
3. The agent pulls the **FounderPing skill**—this guide, the `founderping` CLI, and the complete API documentation—walks you through login, creates your project (picking a widget voice with you), co-designs Topics and Touchpoints with you, and drops the widget install script plus `mount()` calls into your codebase.

Prefer the UI? **Sign up free** and set it up in the Dashboard instead—same end state, one page at a time. A short walkthrough meets you on your first visit and shows what each page is for.

### Step 0 — Set up the workspace

1. **Create your Project — and pick its voice.** The **Organization** (your billing entity) is created for you when you sign up; a **Project** is one product/app you want feedback on, and you create it from the Dashboard. Creating one carries a choice worth making *with* the user: the widget's **identity mode**, i.e. who the AI speaks *as*.
  - **Founder** (default) — the widget talks as the founder's AI stand-in (“I'm Alice's AI”). More personal, so users feel they're speaking almost directly with the founder, which tends to draw more candid feedback; it needs the founder's name.
  - **Brand** — the widget talks as a **Team Feedback Assistant** for the product team. Neutral, fits any product, needs only the app name.
  Ask which fits before creating, and default to Founder if they're unsure — it's not permanent, so they can switch the voice (or rename the founder) later from the Dashboard's **Settings → “Meet users as”** card. Either way the widget starts with a **default avatar**; uploading a custom logo / bot avatar (and accent color) is only available in the Dashboard (**Settings → “Meet users as”**, see §5.4)—for **both** Brand and Founder mode, the CLI doesn't support avatar upload yet.
2. **Write your product context.** Drop in a free-form description of your product—what it does, who uses it, key terms. FounderPing injects this into every conversation so the AI sounds informed instead of generic. You write it in **Settings → Product Description**.

### Step 1 — Decide what to ask (Topics)

1. **Create Topics.** A Topic is something you want FounderPing to talk to users about—a churn reason, a reaction to a new feature, an onboarding check. Which flavor it is comes down to one question: **who starts the conversation?**
  - **Reactive Topics — the user comes to you.** Every project starts with one, the **Default Topic**, sitting behind a small sticky side button so users can open it whenever they want to say something: a general “tell us anything” helper. Add more for the entry points you place yourself—a “Talk to the founder” button, a link in your help menu—and they open the moment they’re tapped.
  - **Proactive Topics — you go to the user.** Each one focuses on a single question or a specific class of issue, and the AI raises it on its own at the right moment (e.g. after checkout, on a power-user milestone).

Both flavors are configured through the same two core concepts, edited in the Dashboard:

- **Hint** — context for the AI, *not* shown to the user. It’s injected into the AI’s context during the chat so the AI understands the background and what you’re trying to learn, and drives the conversation accordingly.
- **Opening** — the first line(s) the user sees when a chat starts. FounderPing assembles it at runtime from **two pieces**, each picked at random so the same person doesn't always get the same hello:
  - **Introduction** — project-level. Configured on **Settings → “Meet users as”** (see §5.4): a few greeting / self-intro variants for the active identity mode (Founder or Brand). One variant is chosen each time a Topic fires. It covers exactly one beat — hello, and who's speaking — and deliberately asks nothing, so the Hook can do the asking without the widget asking twice.
  - **Hook** — topic-level. The **Openings** you write per Topic: a few substantive candidates; one is chosen when that Topic fires. This is the piece that has to earn a reply — see **§3.5** for the principles worth following. **Don't** put hi/hey/hello or a self-introduction in a hook; the Introduction already covers that beat.

The Hint is split into short, **orthogonal** fields — each one captures a *different* dimension of the conversation, so any given detail belongs in exactly one field (don’t restate the same thing across them). A **Proactive Topic** uses four:

- **Context** — the *situation*: what the dialogue is about and where/when it happens (which screen, after which action or event, what kind of user), plus any helpful background. It sets the scene; the goal and the questions do **not** go here.
- **Goal** — the *objective*: what you want to learn from the user — one or two sharp things the developer cares about, not a grab-bag of five.
- **Requirements** — the *guard-rails*: constraints and must-dos for how the AI behaves — tone, what it must cover, what to avoid, whether to ask for contact details, and so on.
- **Plan** — the *flow*: what the conversation should *do* — the order of beats and how to move toward the Goal — not exact question wording.

Keep every field tight — a sentence or two each (rough budget: ~30 words for Context, Goal, and Requirements, up to ~50 for Plan). The Hint is a quick brief the AI skims before talking, not an essay; padding it only buries the signal. **§3.6** walks through how to write each field so the AI can act on it.

**Reactive Topics** are open-ended — the user brings the subject — so they use just **one** of these: **Requirements**. With no fixed situation, single goal, or scripted flow to pin down, Context, Goal, and Plan are all dropped; only the guard-rails for how the AI should behave still apply.

You don’t have to write any of this from scratch. You can **co-design the Topic with FounderPing’s AI assistant** — describe your goal in a short chat and it drafts the Hint fields and Openings with you — or hand it to **your own AI** with the *“Co-design with your AI”* button, which points your assistant at this guide and has it walk you through the same fields. Everything stays editable by hand.

### Step 2 — Decide when and where to ask (reach users)

There are two ways to put a Topic in front of users. Most projects use both.

**Path A — The Widget (in your product)**

This is the main path: an embedded widget that brings your Topics to users right inside your app.

1. **Touchpoints — naming the moments.** A **Touchpoint** is a named hook you place in your code at a meaningful moment, e.g. `founderping.mount("post_checkout")`. It’s just a label for “something interesting happened here”; *what* should be asked there is decided later in the Dashboard, not in your code.
2. **Bind Topics to Touchpoints (with Conditions).** In the Dashboard you bind one or more Topics to each Touchpoint, so firing that moment can start the right conversation. Bindings can carry **Conditions** that gate when they’re allowed to fire—time windows, weekdays, and per-user cooldowns (see *Appendix A — Touchpoint Condition types* for the full list).
  The **Default Topic** arrives already bound to a reserved Touchpoint of its own, so it needs no `mount(...)` from you—switch **Default Touchpoint** on and it shows as the sticky “tell us anything” button. Everything else is bound here: **Proactive Topics** so they pop up at the right moment, and any further **Reactive Topic** so the button *you* placed opens the chat you meant. A Touchpoint has no flavor of its own—it takes it from the Topics bound to it, so they must all be the same flavor, and the first one you bind settles whether this Touchpoint pops up on its own or waits for a tap.
3. **Integrate the widget once.** Add the one-line widget script to your product and place your `mount(...)` calls at the moments you named. After this, which Topic fires where, and under what Conditions, is all controlled from the Dashboard—no further frontend changes per question. (Concrete snippets, the widget JavaScript API, and the runtime mechanics—coalesce, Conditions, and Guards—are in `doc/frontend.md`.)

**Path B — Direct Links (outside your product)**

A **Direct Link** is a shareable URL (and QR code) that opens a single Topic in a full-screen chat, with **no SDK integration required**. It’s useful for reaching people outside your app—emails, social posts, support replies, printed QR codes, etc. You just pick a Topic, optionally set a branded subdomain, and share the link; anyone who opens it lands straight in that conversation (see §5.8).

### Step 3 — Conversations happen

1. **Users talk, not type.** The AI runs a short voice-or-text chat, asking follow-ups in the moment to turn “it’s broken” into something specific. Anonymous sessions stick to a real user once your app supplies a User Identifier (`doc/frontend.md`).
2. **Everything is captured.** Each chat is stored as a Conversation tied to its Topic, user, and moment—ready for processing.

### Step 4 — Feedback is processed automatically

1. **Summarized into Points.** After a conversation ends, FounderPing writes a conversation summary and extracts atomic **Points**: each has a one-line gist, sentiment, and tags from your Tag Tree; the conversation also gets an attention flag (see §5.9).
2. **Organized for you.** Points are labeled and flagged so urgent or on-watch items surface instead of getting buried.
3. **Tune your taxonomy.** A sensible default **Tag Tree** and **Attention List** ship out of the box, so labeling and flagging work from the first conversation. Once feedback is flowing, reshape them in **Insights → Tags & attention** to match your product—how feedback should be labeled, and which signals should auto-flag a conversation (e.g. churn, billing, security). See §5.2–5.5.

### Step 5 — Read, prioritize, and act

1. **Review and follow up in the Dashboard.** Browse the Conversations and Users views, filter by tag/sentiment/attention, expand any row for the full chat, and save curated sets as **Bookmarks**. That same conversation viewer carries a composer, so you can reply to a Web Widget user in person right where you read their chat (**Direct Chat**, §5.6). The sidebar's **Live** page lists whoever is online at this moment; clicking one of them opens the same viewer on that person's latest conversation.
2. **See the big picture.** The **Overview** page shows trends, sentiment and quality breakdowns, top tags, and a daily digest email (see §5.5).
3. **Close the loop and iterate.** Acting on feedback.

---

## 3. Help designing the feedback program for your developer (For AI assistant reading this)

This chapter is written mainly for you, the AI assistant a developer has brought in to help them get more out of FounderPing — though it’s really about *their* product, so a developer reading along will get just as much from it. (“you” here means the assistant; elsewhere in this guide it means the developer.) Treat it as background for a role you can play, not a procedure you have to run: most of the time the developer arrives with a concrete question, and helping with that comes first. What follows is how, around that, you can also help them shape a feedback program worth having.

### 3.1 Why proactive, in-product feedback is worth it

In-product feedback is one of the highest-leverage things a team can build, and also one of the easiest to undervalue. Most teams learn too little, too late — through the patchwork of failing channels in §1: a tiny circle of friends, emails into the void, star ratings with no story, and silence from the users who matter most. Asking **in the moment, inside the product** flips that — the barrier to speak is low, the experience is fresh, and the AI can dig past “it’s broken” to the actual why. For the developer that means a steady stream of specific, attributable signal: roadmap calls grounded in what real users said instead of guesswork. It’s worth carrying that perspective into the conversation — a developer unsure whether users will even talk is often just a few well-placed asks away from finding out they will.

### 3.2 Lead with their question, design alongside

A developer usually arrives with something concrete — often a prompt pasted straight from an “Ask your AI” / “Co-design with your AI” button in the Dashboard, sometimes a specific question about the conversation they’re building. **Help with that first.** It’s what they came for, and it’s what earns you the room to suggest more.

Keep this in mind as you do: because you understand their project and you’ve read this guide, you’re unusually well-placed to notice feedback opportunities they might miss. Developers are close to their product and often don’t realize which moments would yield the richest signal — or that a question they’re hand-wiring could be a Topic they retarget from the Dashboard without touching code again. The better you understand the product — from the repo, the product context you’ve written (§5.1), or just the conversation — the sharper that instinct gets.

So when you spot a good idea or a well-timed moment, **offer it**: say what it would capture and why it’s worth it, and if they’re interested, help them design and wire it. The point isn’t to push a process — it’s to use what you know to leave their product a little better instrumented than you found it. Suggest, don’t insist, and follow their lead on how far to take it.

### 3.3 What makes a good Moment

Not every screen or click is worth a feedback ask — but some moments consistently yield rich signal. The right Moment is usually one where the user has *just* formed an opinion: the experience is still fresh, they know enough to answer, and a short question would feel natural rather than intrusive. Keep these principles in mind as background.

**Principles that help you spot good Moments**

- **Fresh feeling, still in context** — the user has just done something meaningful and can describe what happened while it's vivid.
- **A decision or emotion in play** — they chose, succeeded, failed, paid, canceled, discovered something new, or hit a wall. Those are when people have something to say.
- **Answerable in a short chat** — the question fits the moment; you're not interrupting something that demands their full attention.
- **Tied to a product decision the developer cares about** — the signal would actually change how the team builds, prices, or prioritizes.

These are heuristics, not a checklist. Every product has its own journey; the developer's domain knowledge and gut feel matter more than any list we could publish.

### 3.4 Help end-to-end, not just answer

Your job isn't to fire back one-liners when the developer asks a question — it's to help them **design a feedback touchpoint all the way through**, from the first idea to something they can ship. When they mention a goal, a feature, or a hunch about what to learn, think about the whole path and offer to walk it with them.

**Start with their question, then widen the lens.** They might ask how to word an Opening or whether to use reactive or proactive. Answer that — and if you see the adjacent pieces they haven't thought about yet, bring them up: a Moment worth instrumenting (§3.3), the Hint fields that would shape the AI's conversation, the Touchpoint name that keeps the code stable, the conditions so the ask doesn't nag, the exact `mount()` / `setUser()` call and where it belongs. Don't wait to be asked for each step.

**Hand back concrete artifacts.** Where you can, deliver the drafted Hint fields and Openings, the touchpoint name, the Dashboard conditions to set, and the code snippets — not a vague plan they still have to interpret. The developer should leave with something they can paste, configure, and ship.

Follow their lead on how far to go on any given thread; Your default stance is **design partner**, not **FAQ bot**: connect the dots across product, copy, configuration, and code so a feedback idea becomes a working integration.

### 3.5 Writing Openings that earn a reply

The **Opening** is what the user reads — often the only thing they weigh before deciding whether to answer. Apply this whenever you draft an Opening, for the developer or alongside them. (The **Hint** — what the AI reads — has its own rules in **§3.6**.)

You're asking a stranger for a favor: their honest opinion, on their time. What earns it is sincerity — respect for the intrusion, real care about the product, and the sense that what they say lands somewhere. Flattery ("your feedback is incredibly valuable to us") and survey wording earn nothing; users have learned to skip both.

Openings follow a fixed **shape**, written in **bubbles** — the separate messages the chat delivers, one per line break. The beats are what matter; how you phrase each one is yours. Write the way you'd actually message someone — warm, direct, a little different in each variant. Keep the beats and bubble split; vary the wording beat by beat.

**Proactive Topics** (the AI interrupts after something the user did) — **two bubbles**:

**Bubble 1 — earn it:** own the interruption politely (*"hope you don't mind"*, *"hope I'm not interrupting"* — not an apology), say you take the app seriously, then say why their answer matters **to you and to them** (*"…really matters to me and helps me keep improving it for you"*).

**Bubble 2 — ask:** name what they just did from **Context**, then one question from **Plan** — phrased as a request, answerable in a sentence. Open the second bubble however reads naturally — *"Looks like you just finished…"*, *"I noticed you wrapped up…"*, or fold the moment into the question. You don't need to start with *"So,"* every time.

Filled in, with the Introduction the widget prepends automatically:

> Hi, I'm Ava, founder of SpeakEasy.
>
> Hope you don't mind me reaching out. I genuinely want my app to be the best it can be, so what you say really matters to me and helps me keep improving it for you.
>
> Looks like you just finished the mock interview — how much did it help your speaking confidence? 💬

If the Introduction speaks for a team rather than one person, the possessives follow it — *our app*, *our users*.

**Reactive Topics** (the user opened the chat) — **one bubble**, two beats: thank them and say you want to hear it, then ask what's on their mind. Nobody interrupted them and you don't know what they were doing, so there's no moment to name and no reason to ask for their time. Phrase each beat in your own words — the examples below are one voice, not a script.

> Hi, I'm Ava, founder of SpeakEasy.
>
> Thanks for reaching out! I'd love to hear from my users. What's on your mind? 💬

Keep proactive hooks under ~50 words, reactive under ~25. **At most one emoji per opening** — each example below shows one, placed where it feels natural. **Every line break starts a new chat bubble** — one Enter in the Dashboard, one `\n` over the API — and several line breaks in a row count as one break, so a blank line between paragraphs gives you two bubbles, not three. No markdown: the chat renders text literally.

### 3.6 Writing Hints the AI can act on

The **Hint** is re-read before every turn of a live conversation to decide what to ask next and when to stop — write it so the AI can act on it, not just describe the topic. §2 Step 1 defines the four fields; below is how to write each one. Keep every field tight (~30 words, ~50 for Plan), orthogonal, and cut anything that doesn't help the AI decide.

**Context — set the scene, nothing else.** This field is *where and when* the conversation happens: which screen, which user action or event triggered it, and any background that helps the AI recognize the moment. Name the screen and the action concretely — "after the user finishes the mock interview on the practice screen" beats "after a session." Good Context gives the Opening something real to acknowledge — if it doesn't pin down what the user just did, the hook can't either. Don't put the learning goal here, don't script questions, and don't list behavioral guard-rails — those belong in Goal, Plan, and Requirements.

**Goal — what you want to learn.** State what the developer wants to find out and cares about — the outcome of a good conversation, not the steps to run it. One or two sharp goals is fine; more than that usually means the Topic is doing too much and should be split. "Understand why users churn" is a Goal; "understand churn and whether they'd pay for premium" is two goals, still workable. A common mistake is writing Plan here instead — "ask about pricing, then dig into alternatives" belongs in Plan, not Goal.

**Requirements — constraints, not wishes.** Put something here only if the AI behaving otherwise would be a bug: a subject it must never touch, a point it must cover before closing, whether to ask for contact details, a tone boundary that matters for this specific topic. "Be friendly" or "keep it conversational" aren't Requirements — the AI already knows how to talk. If removing a line wouldn't change how you'd judge a transcript, it doesn't belong here. In many cases this field can stay empty.

**Plan — what to do.** Sketch how the conversation should move: the order of beats, what to explore first, when to go deeper or close — actions and directions, not exact question wording. The Opening's question should draw from the Plan — a concrete starting move toward the Goal. Goal says *what you want to know*; Plan says *how to get there*. If both fields say the same thing in different words ("learn why they churn" / "find out why they left"), the AI gets no guidance on what to actually do next.

**Reactive Topics use only Requirements.** The user brought the subject, so there's no fixed moment, single goal, or scripted flow to pin down — Context, Goal, and Plan are all dropped. Requirements still apply: how the AI should behave when the user could say anything.

---

## 4. Integration

One principle runs through all of it: **your code only names moments—the Dashboard decides what to ask and when.** You integrate once; from then on you add, retarget, or retire questions entirely from the Dashboard, with no frontend changes or redeploys.

**Everything front-end is in `doc/frontend.md`**: worked examples to copy, the install snippet, every method on `window.founderping`, the project key and allowed-domains setup, and the runtime mechanics behind it. Point your coding agent at that file and it can do the integration for you.

## 5. Key concepts & settings

A reference for the concepts and settings that come up most once you're past the first integration. Each is managed—or surfaced—in the Dashboard, with no code change or redeploy.

### 5.1 Documentation (your product context)

This is the plain-language description of your product that FounderPing feeds into **every** conversation, so the AI sounds like it already knows your app instead of asking from scratch. You write it in **Settings → Product Description**: what the product does, who uses it, the core flow, the words your users use, and the known rough edges. It's the single highest-leverage thing you can set—thin context yields thin, generic interviews—so the Dashboard nudges you whenever it's too short. You don't have to draft it by hand: the **“Ask your AI”** button hands you a ready-made prompt to paste into your coding agent (Claude Code, Codex, Cursor…), which reads your repo and writes a first version for you to refine.

### 5.2 Tag Tree

The **Tag Tree** is your feedback taxonomy. After each chat FounderPing attaches short tags from this tree to every **Point** (one atomic thing a user said), so you can later filter and count—“show me every *pricing* complaint.” The tree is hierarchical (up to three levels, broad at the top, finer below); FounderPing walks each branch top-down and attaches every tag that still fits, and a single Point can carry several. A sensible default tree ships out of the box so tagging works immediately—reshape it around your own product for sharper signal. Edit it in **Insights → Tags & attention**.

### 5.3 Attention List

The **Attention List** is a short set of high-value signals you want caught the instant they appear—churn risk, security worries, pricing objections, or a launch-specific theme. When a conversation matches one, FounderPing stamps it with an **attention flag** so it rises to the top instead of getting buried. Keep it to a handful (up to five) of genuinely important items: it's a spotlight, not a second Tag Tree. Set it in **Insights → Tags & attention**.

### 5.4 Widget branding — match the Widget to your brand

**Widget branding** lets the Widget wear your brand instead of the defaults: pick an **accent color** (it drives the voice and send buttons, the input focus ring, and the “go away” pill), pick a **light or dark theme** (match it to the site you embed the Widget in — it's a per-project choice, not the visitor's system setting), and upload a **bot avatar / logo**, with a live preview as you edit and a one-click reset. It's available on every plan and purely cosmetic, so it never changes which questions fire or when. Set it on the **Settings** page, in the **“Meet users as”** card (the same card where you choose Founder vs Brand, name the AI, and edit the **introduction** variants that prepend every Opening — see Step 1) — branding stays Dashboard-only on purpose: the live preview is the whole point, so it's a step to do yourself rather than script. A new project always starts on the default look until someone brands it here.

### 5.5 Daily digest

A short morning email recapping yesterday's replies, conversation quality, and notable Points. It runs on **two independent switches**, and the email only arrives when both are on:

- **Per project** (Project Settings) — whether that project feeds the digest.
- **Per person** (your Account) — whether *you* receive digest emails at all, across every org you belong to.

Both default to on, so if the emails stop completely, check your Account toggle first; if just one project is missing from them, check that project's toggle.

### 5.6 Direct Chat

**Direct Chat** lets you talk to a Web Widget user in person, from the same conversation viewer you already read chats in. Expand a row in **Conversations**, open a user from **Users** or **Bookmarks**, or follow an Overview notification, and that modal carries a text composer (up to 2,000 characters), an online dot in its header, and a **Read** marker under your last message. There is no separate Direct Chat workspace—the conversation you opened is the one you reply to. Only Web Widget sessions get the composer; Direct Link and dev-helper conversations stay read-only, since no widget is running behind them.

The sidebar's **Live** page is the companion entry point: a plain list of the users online right now, where clicking a row opens the same viewer on that person's latest conversation. Offline users are reached from Conversations or Users in exactly the same way—your message waits in that session's history until they open the widget in that browser again.

**Who shows up on Live.** By default a visitor appears there on their own once they've spent about ten seconds actually looking at a page carrying the Widget—long enough to tell a visit from a bounce, short enough that you can still catch them. Switch **Automatic go-live** off under **Live → Live settings** and nobody appears by themselves; your own page calls `founderping.goLive()` (`doc/frontend.md`) at the moments you care about, and only those visitors become reachable in real time. Both routes are the same step—one taken by the Widget, one by you—and either way a visitor who is live stays live as they browse on. This decides only who is *listed as online*: you can write to anyone at any time from Conversations or Users, and the message waits in their history.

**How many at once.** About 20 visitors can be live at a time across your Organization—counted over all its Projects, not per Project—so not everyone who opens your product is listed. Past that, later arrivals aren't listed at all: they carry on with your Stand-in as usual and take the next seat that frees up. Paying for more capacity will raise the ceiling.

Your first message takes the conversation over for good: the AI never speaks in that thread again, and the conversation reads as a Direct Chat from then on. On the user's side your message appears in the chat they already had open, or brings the Widget out on its own; either way they can reply in place, and their replies are summarized like any other feedback. There is no prompt to accept or decline, and nothing hands the thread back to the AI.

Direct Chat writes are Dashboard-only in this release; the public CLI/REST API has user and conversation reads but no messaging endpoint.

### 5.7 Being told someone is live

Someone going live is only worth anything if you find out while they're still there, and staring at the **Live** page all day isn't a plan. Two things watch it for you, and they split along one line: whether your Dashboard is open.

**Dashboard notifications** cover the case where it is. A card slides into the bottom-right corner of any page in the project the moment a visitor goes live, hits a moment your own code named with `notifyIfLive` (`doc/frontend.md`), or writes back in a chat you've taken over; clicking it opens that conversation, so you can answer without losing the page you were on. Cards are deliberately of-the-moment—they live in that browser, expire on their own, and nothing that happened while the Dashboard was shut is waiting in them. That backlog is what **Conversations** is for. Switch them off per project under **Live → Notification settings → Dashboard notifications**.

**Slack and Discord** cover the case where it isn't. The same **Live → Notification settings** section has a block for each, nested under the channel switch, and from then on the same notifications post a line in the channel you pick.

- **Slack** is one button: **Add to Slack** hands you to Slack's own screen, you choose a channel, and you're back—nothing to create, nothing to copy.
- **Discord** takes three clicks in Discord itself: *Channel Settings → Integrations → Create Webhook*, then **Copy Webhook URL** and paste it here.

Each project posts to one channel per app, so a connected section shows only **Disconnect**—pointing somewhere else means disconnecting and connecting again. The two apps are connected and dropped independently, and if you run both, both get told. Replies are never posted, so a talkative visitor can't flood a channel—and once you're in the conversation you're reading the Dashboard anyway. The **Channel notifications** switch pauses both without forgetting either channel. FounderPing never shows a saved webhook URL back to you, not even to the person who pasted it: it's a key to your channel, so it can be dropped but not read.

Both are quiet by default when nothing is happening. Volume is your call rather than ours: each event name goes quiet for an hour after it rings (`doc/frontend.md`), and switching **Automatic go-live** off drops the automatic `live` notification too—leaving only the moments your own code names.

### 5.8 Direct Links

A **Direct Link** is a shareable URL—plus a matching QR code—that opens a single Topic in a full-screen hosted chat, with **no widget and no code** on your side. It's how you reach people *outside* your product: drop it in an email, a social post, a support reply, a Discord/community message, or a printed QR. You pick the Topic, optionally put it on a branded subdomain, and share it; whoever opens it lands straight in that conversation, and their replies flow into the same Dashboard as widget feedback. Once a Topic's direct link is enabled, it's reachable at `https://founderping.app/t/<topic-id>`. Because FounderPing hosts the page, Direct Links ignore the allowed-domains list (`doc/frontend.md` §4.2) and need no project key on any page of yours—handy for beta cohorts, power users, or one-off campaigns.

### 5.9 Points, sentiment & attention flags

When a conversation ends, FounderPing doesn't just file the transcript—it writes an overall summary and breaks the chat into **Points**. A Point is one atomic thing the user expressed (a single complaint, request, or bit of praise), distilled to a one-line gist. Each Point carries:

- **Sentiment** — positive, neutral, or negative, so you can read the mood at a glance and filter by it.
- **Tags** — pulled from your Tag Tree (§5.2).

The conversation also gets an **attention flag** when its feedback matches your Attention List (§5.3). Splitting chats into Points—rather than one blob per conversation—is what makes feedback *countable*: a single five-minute chat can yield several Points, each tagged and filterable on its own. You browse and slice them in the **Conversations** and **Users** views.

### 5.10 Billing & credits

*Placeholder — pricing and credit mechanics are still being finalized.* This section will cover how usage is metered, how credits are spent per conversation (less for thin “hi-and-gone” chats, more for rich ones), and what happens when a project runs low. We'll fill it in once the model is locked.

---

## Appendix A — Touchpoint Condition types

A binding ties a Topic to a Touchpoint. A **Proactive** binding can carry zero or more **Conditions**, and all of them must pass (AND logic) for that Topic to be eligible to fire. A **Reactive** binding carries none: Conditions exist to pace an interruption, and a chat the user opened themselves isn’t one. Supported types:

- **time_range** — a wall-clock window in the end user’s timezone, with `start` / `end` as `HH:mm`. Omit `start` for “no lower bound” or `end` for “no upper bound.” Multiple `time_range`s can be combined for split windows.
- **weekdays** — restricts firing to specific days of the week (`0 = Sunday … 6 = Saturday`).
- **cooldown** — a per-user “don’t ask again so soon” gate, measured in minutes against the user’s last **reply** to that Topic:
  - `-1` — ask only once; any prior reply blocks it forever (per browser slot).
  - `0` — no cooldown; re-fires after every reply.
  - `>0` — minimum minutes between the user’s last reply and the next allowed fire.
  Notes: the “last reply” timestamp is tracked **per Topic, per user** (not per binding), so a chat opened through a Reactive entry point still counts against the cooldown of any Proactive binding to the same Topic; openings the user merely saw but never replied to don’t count.

---

