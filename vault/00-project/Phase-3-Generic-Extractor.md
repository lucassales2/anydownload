---
type: phase
status: done
milestone: D3
tags: [project, engine, kmp, delivery]
---

# Phase 3 — Generic extractor subset

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-007](../03-decisions/ADR-007-Generic-extractor-phase.md) · [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md) · [Phase 2](Phase-2-Local-Kotlin-Engine.md)

**Done 2026-09-24** ([T-051](../06-tasks/T-051-Phase-3-verification.md)). The current handoff is [Phase 4](Phase-4-Extractor-Core-and-YouTube.md). This note is kept as the record of Phase D3. ADR-004 remains the end state. D2's direct-file path stays. This phase first simplifies the home screen, then translates one small slice of yt-dlp's generic extractor and runs it on all four hosts.

Owner direction, 2026-09-23: the idle screen is only a link field. The app does not check the clipboard or show that dialog. A compatible link opens a metadata preview with Download and a collapsible Edit (video or audio, quality, format). After that UI, an HTML page with a single `<video>`, `<audio>`, or inner `<source>` downloads on iOS, Android, web, and desktop. The existing HTTP engine saves that media URL. No YouTube. No merge.

## Done looks like

The idle screen is the link field alone. No clipboard permission dialog and no automatic clipboard read. An invalid link stays on the field. A compatible link opens the metadata preview. Download starts the job. Edit download expands and collapses video or audio, quality, and format.

Then each host can submit one local HTML fixture, show progress, write the referenced media file on the device, cancel, and show a clean failure when the page has no media element or more than one. Direct files still use the D2 path. Desktop CLI and Android Chaquopy still handle every URL this extractor does not resolve. iOS and web still fail those with a clear error. Settings / Add never pretend a download started when the extension, Chaquopy, or desktop CLI is required and missing.

## Rules for every D3 task

- No backend, login, MeTube HTTP/Socket.IO client, or `shared/network` use.
- Do not copy MeTube, NewPipe, or YtDlp-kt. Translate only the generic subset in [ADR-007](../03-decisions/ADR-007-Generic-extractor-phase.md). Do not vendor `generic.py`.
- Keep an Unlicense notice that names the upstream revision inspected that day. Start from the tag already pinned for Android (`yt-dlp==2026.8.19`) and re-read that file before translating.
- Common code has no `ProcessBuilder`, no Python, no Chaquopy imports.
- Build process arguments as a list. Never concatenate a shell command.
- Cookie file contents, signed media URLs, and raw process/extension stderr do not go into logs, history, toasts, or this vault.
- Unknown progress stays unknown. Do not invent a percent, speed, or ETA.
- Free-form yt-dlp JSON stays disabled. Options stay an allowlist.
- Tests use a local HTML fixture and a local media file. No private URLs, cookies, or live third-party hosts in default CI.
- Do not add FFmpeg, MediaMuxer, or AVFoundation postprocessing. The toolkit choice is already recorded in ADR-007.
- When a task is finished, check its acceptance boxes, write what you ran under Evidence, and move its Kanban card. The board column is the status.

## Layout

```text
shared/core          GenericExtractor used by HttpDownloadEngine
shared/ui            Link-only home, then metadata preview; honest unresolved-page errors
apps/desktop         Kotlin extractor for matching HTML; CLI for the rest
apps/android         Kotlin extractor for matching HTML; Chaquopy for the rest
apps/ios             Kotlin extractor only; unresolved HTML still errors
apps/web             Compose/Wasm UI; extractor on HTML bytes; no origin fetch
apps/web-extension   MV3 fetches the page and the media file
```

Suggested package: `com.anydownlod.core.extract` for the extractor. Engines stay in `com.anydownlod.core.engine`.

## Task order

Do them in this order. The extractor tasks assume the first-screen cards are Done, then that the extractor and the shared engine hook exist.

| Order | Task | Delivers |
| --- | --- | --- |
| 1 | [T-052](../06-tasks/T-052-Link-only-home.md) | Idle screen is the link field; no clipboard dialog or auto-check |
| 2 | [T-053](../06-tasks/T-053-Validate-then-preview.md) | Compatible link opens metadata preview; invalid stays on the field |
| 3 | [T-054](../06-tasks/T-054-Download-and-edit.md) | Download, plus collapsible video/audio, quality, and format |
| 4 | [T-045](../06-tasks/T-045-Generic-extractor-subset.md) | Generic subset, notice, unit tests on fixture HTML |
| 5 | [T-046](../06-tasks/T-046-Engine-uses-generic-extractor.md) | HTML that matches downloads; other HTML still fails typed |
| 6 | [T-047](../06-tasks/T-047-Desktop-generic-route.md) | Desktop uses Kotlin for a matching page; CLI for the rest |
| 7 | [T-048](../06-tasks/T-048-Android-generic-route.md) | Android uses Kotlin for a matching page; Chaquopy for the rest |
| 8 | [T-049](../06-tasks/T-049-Ios-generic-download.md) | iOS downloads the fixture media into the sandbox |
| 9 | [T-050](../06-tasks/T-050-Web-extension-generic.md) | Extension fetches page and media; page stays UI |
| 10 | [T-051](../06-tasks/T-051-Phase-3-verification.md) | Four-host run-through |

## What this phase covers

| Host | Direct file (D2) | Simple HTML media page | Other site URL |
| --- | --- | --- | --- |
| Desktop | Shared Kotlin HTTP | Shared generic extractor, then HTTP | Installed CLI |
| Android | Shared Kotlin HTTP | Shared generic extractor, then HTTP | Chaquopy + pinned yt-dlp |
| iOS | Shared Kotlin HTTP | Shared generic extractor, then HTTP | Error: extractor not implemented |
| Web | Extension fetch + save | Extension fetches HTML; Kotlin extractor; extension saves the media | Error: extractor not implemented |

Parity rows: F-01 evidence for **this HTML fixture only**, not for YouTube. [T-022](../06-tasks/T-022-Parity-audit.md) stays open. [T-009](../06-tasks/T-009-Backend-vertical-slice.md) and [T-010](../06-tasks/T-010-Remote-vertical-slice.md) stay open; D3 does not close their broader wording.

## Explicitly later

- The rest of yt-dlp's generic extractor, and any other site extractor.
- YouTube and yt-dlp-ejs.
- Merge, audio extract, and clips. The toolkit is chosen in ADR-007 and not built here.
- Spotify matching ([T-037](../06-tasks/T-037-Spotify-youtube-match.md)).
- Enabling free-form yt-dlp JSON.
- Retiring Chaquopy or the desktop CLI.
- Store submission.

## How to start a session

Paste the prompt in [Phase 3 loop prompt](Phase-3-Loop-prompt.md) as the first message.

1. Read this note and [ADR-007](../03-decisions/ADR-007-Generic-extractor-phase.md).
2. On [Kanban](../Kanban.md), take the first D3 card whose dependencies are Done.
3. Read that task note fully before editing code.
4. Do not pick up YouTube, postprocessing, T-009's full extraction wording, or M2/M3 cards.
