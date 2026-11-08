# FounderPing Devkit

One-click **FounderPing** integration for AI coding agents. FounderPing lets you talk
with your users right inside your product — yourself when you're online, through an
AI stand-in when you're not — and turns those conversations into structured product
insight. This repo builds the two release artifacts an agent uses to wire FounderPing
into *your* project:

- a **skill** tarball (`skill.tar.gz`) — the agent playbook plus the product guide
  and API reference, fully self-contained, and
- the **`founderping` CLI** — a single static binary that holds your access key and
  forwards authenticated calls to the FounderPing API.

Website: **https://founderping.app**

## Install (paste this to your coding agent)

You don't clone this repo. In your own project, paste the prompt below into Claude
Code / Codex / Cursor:

> Add FounderPing to this project. Download its skill bundle
> — `https://github.com/founderping/founderping/releases/latest/download/skill.tar.gz` —
> unpack it into a folder here (e.g. `founderping-skill/`), then open `SKILL.md` inside and
> follow it.

That's the whole bootstrap. `SKILL.md` drives the rest — CLI download, sign-in,
project setup, widget install, topic design, and reading feedback back.

## What's in this repo

- [`skill/`](skill/) — source of the published skill: `SKILL.md` (authored) plus
  `doc/`, the product guide, web, Android and iOS integration guides and API
  reference (synced from the main product docs). Shipped as `skill.tar.gz`.
- [`cli/`](cli/) — the `founderping` CLI (Go). Shipped as per-platform binaries. See
  [`cli/README.md`](cli/README.md).
- [`android/`](android/) — the Android library, published to Maven Central as
  `app.founderping:widget`. See [`android/README.md`](android/README.md).
- [`ios/`](ios/) — the iOS Swift package, `FounderPing`, which Swift Package Manager
  fetches straight from this repository; `Package.swift` sits at the root because
  that is the only place it looks. See [`ios/README.md`](ios/README.md).

## Releases

`make dist` (from this directory) builds the full, upload-ready set into `./dist`:

- `founderping-<os>-<arch>[.exe]` — raw CLI binary per platform (direct download).
- `founderping-<os>-<arch>.tar.gz` — CLI archive per platform.
- `skill.tar.gz` — the installable skill bundle (see the prompt above).

Run `fp sync_assets` in the main repo first so `skill/doc/` is current, then
`make dist` and upload everything in `./dist` to a GitHub
release. CI does this automatically on a `v*` tag (`.github/workflows/release.yml`).

The same tag is also the iOS release, with nothing to upload: Swift Package
Manager reads package versions off this repository's tags.

## License

[MIT](LICENSE)
