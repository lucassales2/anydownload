---
type: phase
status: done
milestone: D2
tags: [project, engine, kmp, delivery]
---

# Phase 2 — Local HTTP engine

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-006](../03-decisions/ADR-006-Local-http-engine-phase.md) · [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md)

**Start here if you are implementing.** This note is the handoff for Phase D2. ADR-004 remains the end state (Kotlin port of yt-dlp on every target). This phase does **not** port extractors.

Owner direction, 2026-09-23: prove one local direct HTTP(S) file download on iOS, Android, web, and desktop. Web downloads through a browser extension. Android may also call pinned yt-dlp via Chaquopy. Desktop keeps the installed CLI for non-direct URLs.

## Done looks like

Each host can submit one public direct-file URL, show progress, write the file on the device, cancel, and show a clean failure. Desktop CLI and Android Chaquopy still handle yt-dlp site URLs. iOS and web fail those with a clear error. Settings / Add never pretend a download started when the required tool (extension, Chaquopy, or desktop CLI) is missing.

## Rules for every D2 task

- No backend, login, MeTube HTTP/Socket.IO client, or `shared/network` use.
- Do not copy MeTube, NewPipe, or YtDlp-kt. Do not translate yt-dlp extractors in this phase. T-006 may only write notices and inventory.
- Common code has no `ProcessBuilder`, no Python, no Chaquopy imports.
- Build process arguments as a list. Never concatenate a shell command.
- Cookie file contents, signed media URLs, and raw process/extension stderr do not go into logs, history, toasts, or this vault.
- Unknown progress stays unknown. Do not invent a percent, speed, or ETA.
- Free-form yt-dlp JSON stays disabled. Options are an allowlist (Q-09).
- Tests use fixture hosts (`https://example.com/files/tiny.bin` or a local mock server). No private URLs or cookies.
- When a task is finished, check its acceptance boxes, write what you ran under Evidence, and move its Kanban card. The board column is the status.

## Layout

```text
shared/core          HttpDownloadEngine behind DownloadEngine; URL classification
shared/ui            Same screens; honest unavailable states
apps/desktop         Kotlin HTTP for direct files; YtDlpCliEngine for the rest
apps/android         Kotlin HTTP for direct files; Chaquopy adapter for the rest
apps/ios             Kotlin HTTP only
apps/web             Compose/Wasm UI; talks to the extension; does not fetch origins
apps/web-extension   MV3; host permissions; fetch + browser.downloads
```

Suggested packages:

| Piece | Package |
| --- | --- |
| HTTP engine | `com.anydownlod.core.engine` |
| HTTP / file expect | `com.anydownlod.core.platform` |
| Desktop CLI (existing) | `com.anydownlod.desktop.engine` |
| Android Chaquopy | `com.anydownlod.android.engine` |
| iOS file adapter | host / Kotlin Native actuals |
| Extension bridge | `com.anydownlod.web` + `apps/web-extension` |

## Task order

Do them in this order. Later tasks assume the engine and adapters exist.

| Order | Task | Delivers |
| --- | --- | --- |
| 1 | [T-006](../06-tasks/T-006-Review-security-licensing.md) | License inventory and local threat notes. No source copy. |
| 2 | [T-003](../06-tasks/T-003-Approve-product-scope.md) | Q-09 allowlist recorded. Close the last open criterion. |
| 3 | [T-038](../06-tasks/T-038-Shared-http-engine.md) | Direct HTTP engine + classification + unit tests |
| 4 | [T-039](../06-tasks/T-039-Platform-http-and-files.md) | expect/actual HTTP and streaming file writes |
| 5 | [T-040](../06-tasks/T-040-Desktop-http-route.md) | Desktop uses Kotlin HTTP for direct files |
| 6 | [T-041](../06-tasks/T-041-Android-http-and-chaquopy.md) | Android HTTP + Chaquopy for other URLs |
| 7 | [T-042](../06-tasks/T-042-Ios-http-download.md) | iOS HTTP download in the sandbox |
| 8 | [T-043](../06-tasks/T-043-Web-extension-download.md) | Extension fetches and saves; page stays UI |
| 9 | [T-044](../06-tasks/T-044-Phase-2-verification.md) | Four-target run-through; closes [T-004](../06-tasks/T-004-Validate-KMP-targets.md) |

T-003 can finish as soon as the Q-09 text is in the vault. T-041 must not add Chaquopy until T-006 has recorded Chaquopy, CPython, and bundled yt-dlp licenses.

## What this phase covers

| Host | Direct HTTP(S) file | yt-dlp site URL |
| --- | --- | --- |
| Desktop | Shared Kotlin engine | Installed CLI (D1) |
| Android | Shared Kotlin engine | Chaquopy + pinned yt-dlp |
| iOS | Shared Kotlin engine | Error: extractor not implemented |
| Web | Extension fetch + save | Error: extractor not implemented |

Parity rows: initial F-01/F-07/F-26 evidence for a **direct file**, not for YouTube. [T-022](../06-tasks/T-022-Parity-audit.md) stays open.

## Explicitly later

- Translating yt-dlp extractors or yt-dlp-ejs.
- YouTube on iOS or web.
- Merge, audio extract, clips (per-target media toolkit).
- Spotify matching ([T-037](../06-tasks/T-037-Spotify-youtube-match.md)).
- Enabling free-form yt-dlp JSON.
- Store submission of the Android app or the extension.

## How to start a session

Paste the prompt in [Phase 2 loop prompt](Phase-2-Loop-prompt.md) as the first message.

1. Read this note and [ADR-006](../03-decisions/ADR-006-Local-http-engine-phase.md).
2. On [Kanban](../Kanban.md), take the first D2 card whose dependencies are Done.
3. Read that task note fully before editing code.
4. Do not pick up extractor-port work, T-009's "extraction" wording as a license to parse HTML, or M2/M3 cards.
