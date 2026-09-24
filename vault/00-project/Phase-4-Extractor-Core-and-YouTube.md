---
type: phase
status: active
milestone: D4
tags: [project, engine, kmp, youtube, delivery]
---

# Phase 4 — Extractor core and YouTube

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-008](../03-decisions/ADR-008-Extractor-core-and-youtube-phase.md) · [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md) · [Phase 3](Phase-3-Generic-Extractor.md)

**Start here if you are implementing.** This note is the handoff for Phase D4. ADR-004 remains the end state: Kotlin yt-dlp on every target. D2's direct-file path and D3's generic subset stay. This phase builds the extractor core every later translation plugs into, then translates YouTube for a single video, first without JavaScript and then with yt-dlp-ejs on an embedded runtime.

Owner direction, 2026-09-24: the end goal is yt-dlp feature equivalence, meaning the core engine plus the extractor catalog over time. D4 is the core and YouTube single video on iOS, Android, web, and desktop. No merge or transcode yet. Stage YouTube: JS-less `visionos` first as a gate, then EJS with the `web` client. Zipline QuickJS is the embedded runtime to try first. yt-dlp-ejs is bundled and pinned. The desktop CLI and Chaquopy stay for unmatched URLs and become an opt-in oracle. Preview and Edit come from the Kotlin extractor.

## Done looks like

A pasted YouTube watch, `youtu.be`, shorts, or embed link opens a preview with the real title, channel, duration, and thumbnail on every host. Edit offers only the qualities and containers the extracted formats can satisfy as a single file, and explains that higher video quality and MP3/WAV/FLAC wait for the media toolkit. Download saves a progressive video or an audio-only stream to the device with progress and cancel. Without a JavaScript runtime the `visionos` client still works; with the bundled solver on the embedded runtime the `web` client's signed formats appear too. A private, removed, age-gated, or login-only video fails typed. Desktop and Android never send a matched YouTube URL to the CLI or Chaquopy. Direct files and the generic subset behave as in D3. A manifest URL downloads its fragments as one file instead of saving the playlist text.

The [equivalence note](../01-product/Ytdlp-equivalence.md) shows a generated coverage table from `port/manifest.json`, the opt-in oracle run is recorded, and the honest limits are in the README.

## Rules for every D4 task

- No backend, login, MeTube HTTP/Socket.IO client, or `shared/network` use.
- Translate from yt-dlp at tag `2026.08.19` (commit `3a08beaf031ab68f966401ead017ac81fe8486cf`), file by file, with the Unlicense notice, the upstream path, and that revision in the header and in `port/manifest.json`. Re-read the upstream file the day you translate it. Do not vendor `common.py`, `utils`, `YoutubeDL.py`, or any extractor file. Do not copy MeTube, NewPipe, or YtDlp-kt.
- Common code has no `ProcessBuilder`, no Python, no Chaquopy imports. The only process use stays in `apps/desktop` (CLI adapter, oracle tests, optional Deno probe).
- The bundled yt-dlp-ejs release is the one yt-dlp vendors at the pin (`0.8.0`). Its hashes must match upstream `_info.py`. No runtime script download.
- Cookie material, signed media URLs (including `googlevideo` URLs from a player response), PO tokens, visitor data, and raw process/extension/runtime output never enter logs, history, toasts, fixtures, or this vault. Fixtures synthesize or redact them.
- Unknown progress stays unknown. Fragment downloads report fragment counts, not an invented percent, until the size is known.
- Free-form yt-dlp JSON stays disabled. Options stay an allowlist. The typed-options compiler never emits a merge in D4.
- Nothing is labeled as a quality or container it is not. When the toolkit is needed, the job fails typed or the UI disables the choice with the reason.
- Tests use fixtures by default. Live extractor tests and the oracle run only with an explicit Gradle property and only against public URLs listed in the task note. No cookies, no private media, no live hosts in default CI.
- Do not add FFmpeg, MediaMuxer, or AVFoundation postprocessing.
- When a task is finished, check its acceptance boxes, write what you ran under Evidence, update `port/manifest.json` if a module was ported, and move its Kanban card. The board column is the status.

## Layout

```text
port/manifest.json          Ported modules: upstream path, tag, commit, Kotlin file, status
third_party/yt-dlp-ejs/     Pinned EJS release files + UNLICENSE + hashes (T-069)
tools/port-manifest         Gradle task: validate manifest, generate the coverage table
shared/core
  extract/                  InfoExtractor base, InfoDict, MediaFormat, registry, helpers
  extract/youtube/          YouTube translation (JS-less, then EJS)
  format/                   Format-spec parser, sorter, typed-options compiler
  download/                 HTTP (range/chunk), HLS, DASH fragment downloaders
  jsc/                      JS challenge provider protocol, JsRuntime port
  platform/                 HttpTransfer with method/headers/body/range; JsRuntime adapters
shared/ui                   Preview and Edit driven by extracted formats
apps/desktop                Registry route before CLI; oracle tests; Deno probe (optional)
apps/android                Registry route before Chaquopy; QuickJS runtime
apps/ios                    Same shared engine; QuickJS or JavaScriptCore runtime
apps/web                    Page runs the solver in its own JS; extension carries requests
apps/web-extension          fetch with method, headers, body, range; policy on every hop
```

Packages: `com.anydownlod.core.extract`, `com.anydownlod.core.extract.youtube`, `com.anydownlod.core.format`, `com.anydownlod.core.download`, `com.anydownlod.core.jsc`. Engines stay in `com.anydownlod.core.engine`.

## Task order

Do them in this order. Dependencies are the links in each task note.

| Order | Task | Delivers |
| --- | --- | --- |
| 1 | [T-055](../06-tasks/T-055-Port-manifest-and-equivalence-matrix.md) | `port/manifest.json`, generator, coverage table in the equivalence note |
| 2 | [T-056](../06-tasks/T-056-Http-request-port.md) | `HttpTransfer` with method, headers, body, range on all hosts and the extension |
| 3 | [T-057](../06-tasks/T-057-Extractor-core.md) | `InfoExtractor` base, `InfoDict`, `MediaFormat`, registry, helpers; generic subset on the base |
| 4 | [T-058](../06-tasks/T-058-Format-selector.md) | Format-spec parser and sorter; typed options compile to a spec; no merge in D4 |
| 5 | [T-059](../06-tasks/T-059-Extractor-test-harness.md) | Upstream-style `_TESTS` harness: fixtures by default, live opt-in |
| 6 | [T-060](../06-tasks/T-060-Youtube-jsless-extractor.md) | YouTube single video via the `visionos` client, no JavaScript |
| 7 | [T-061](../06-tasks/T-061-Engine-extracts-selects-downloads.md) | Engine: registry → extract → select → download one format; chunked ranges |
| 8 | [T-062](../06-tasks/T-062-Desktop-ytdlp-oracle.md) | Opt-in `yt-dlp -J` differential tests on desktop |
| 9 | [T-063](../06-tasks/T-063-Preview-from-extractor.md) | Preview and Edit from extracted metadata and formats |
| 10 | [T-064](../06-tasks/T-064-Desktop-routes-youtube.md) | Desktop routes matched URLs to Kotlin; CLI for the rest |
| 11 | [T-065](../06-tasks/T-065-Android-routes-youtube.md) | Android routes matched URLs to Kotlin; Chaquopy for the rest |
| 12 | [T-066](../06-tasks/T-066-Ios-youtube-download.md) | iOS downloads a YouTube video into the sandbox |
| 13 | [T-067](../06-tasks/T-067-Web-extension-carries-requests.md) | Extension carries POST/headers/range; page saves the chosen format |
| 14 | [T-068](../06-tasks/T-068-Gate-jsless-youtube-four-hosts.md) | **Gate:** JS-less YouTube on four hosts |
| 15 | [T-069](../06-tasks/T-069-Ejs-bundle-and-jsruntime-port.md) | Bundled EJS `0.8.0`, hash check, `JsRuntime` port, Zipline QuickJS spike |
| 16 | [T-070](../06-tasks/T-070-Ejs-challenge-solving-web-client.md) | Challenge provider protocol, player JS, `sig`/`n` solving, `web` client |
| 17 | [T-071](../06-tasks/T-071-Js-runtime-desktop-android-ios.md) | QuickJS adapters on desktop, Android, iOS; optional Deno probe on desktop |
| 18 | [T-072](../06-tasks/T-072-Js-runtime-web-page.md) | Web page runs the solver in its own JavaScript |
| 19 | [T-073](../06-tasks/T-073-Native-hls-dash-downloaders.md) | Native HLS and DASH fragment downloaders; manifests no longer "direct files" |
| 20 | [T-074](../06-tasks/T-074-Phase-4-verification.md) | Four-host run-through, oracle, coverage table, README |

## What this phase covers

| Host | Direct file (D2) | Simple HTML page (D3) | YouTube single video (D4) | Manifest URL (D4) | Other site URL |
| --- | --- | --- | --- | --- | --- |
| Desktop | Shared Kotlin HTTP | Shared generic subset | Shared registry + selector + HTTP; QuickJS embedded, Deno optional | Shared HLS/DASH | Installed CLI |
| Android | Shared Kotlin HTTP | Shared generic subset | Same; QuickJS embedded | Shared HLS/DASH | Chaquopy + pinned yt-dlp |
| iOS | Shared Kotlin HTTP | Shared generic subset | Same; QuickJS or JavaScriptCore | Shared HLS/DASH | Error: extractor not implemented |
| Web | Extension fetch + save | Extension fetches; Kotlin extracts | Extension carries requests; page solves in JS; extension saves | Extension fetches fragments; page concatenates | Error: extractor not implemented |

Parity rows: F-01 gains YouTube evidence for a single video at single-file quality. F-02/F-03 stay open: no merge, no conversion. [T-022](../06-tasks/T-022-Parity-audit.md), [T-009](../06-tasks/T-009-Backend-vertical-slice.md), and [T-010](../06-tasks/T-010-Remote-vertical-slice.md) stay open. Equivalence rows are tracked in the [equivalence note](../01-product/Ytdlp-equivalence.md), not here.

## Explicitly later

- Media toolkit: merge, audio extract, transcode, clips, chapter split, embed thumbnail/metadata (recorded in ADR-007).
- YouTube playlists, channels and tabs, live and from-start, comments, subtitles, PO token providers, cookies, premium/authed clients.
- X / Twitter (next named site), the rest of the generic extractor (JSON-LD, og:video, embeds), and any other extractor.
- Runtime EJS updates, `jsinterp` port, curl_cffi-style impersonation.
- Spotify matching ([T-037](../06-tasks/T-037-Spotify-youtube-match.md)) — unblocks once a YouTube audio download works, but stays outside D4.
- Retiring Chaquopy or the desktop CLI. Store submission.

## How to start a session

Paste the prompt in [Phase 4 loop prompt](Phase-4-Loop-prompt.md) as the first message.

1. Read this note, [ADR-008](../03-decisions/ADR-008-Extractor-core-and-youtube-phase.md), and the [equivalence note](../01-product/Ytdlp-equivalence.md).
2. On [Kanban](../Kanban.md), take the first D4 card whose dependencies are Done.
3. Read that task note fully before editing code.
4. Do not pick up the media toolkit, playlists, cookies, X/Twitter, or M2/M3 cards.
