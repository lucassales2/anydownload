---
id: T-064
type: task
priority: P0
milestone: D4
tags: [task, desktop, engine]
---

# T-064 — Desktop routes matched URLs through Kotlin; CLI for the rest

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [T-047](T-047-Desktop-generic-route.md)

## Outcome

On desktop, a URL the Kotlin registry matches never reaches the `yt-dlp` process. Everything else keeps the D1 CLI path. Direct files and generic pages keep the D3 routing.

## Dependencies

- [T-063](T-063-Preview-from-extractor.md).

## Work

- `DesktopRouteClassifier`: check `registry.suitableFor(url)` **before** the bounded page probe; matched → `DesktopRoute.Kotlin`. No probe request is sent for matched URLs.
- `DesktopRoutingEngine`: Kotlin route uses the shared engine with the real `JavaNetHttpTransfer` (request port) and `DesktopFileStore`. Queue, history, cancel, retry, delete unchanged.
- Settings: the tool status row still reports `yt-dlp`/`ffmpeg` presence; add "Kotlin extractors: YouTube (single video)" from the manifest so the user sees what runs locally.
- Tests in `apps/desktop`: a YouTube URL never spawns a process (fake process factory asserts zero invocations); a non-matching site URL still reaches the CLI path; the D3 fixture and direct-file routing tests stay green.

## Acceptance criteria

- [ ] Routing tests above pass; `:apps:desktop:test` green.
- [ ] Click-through: paste a public YouTube URL, preview, download audio (M4A) and a progressive video; files land under the download root; no `yt-dlp` process in the process list during the job.
- [ ] Choosing MP3 fails typed with the toolkit message; no process is spawned to work around it.

## Evidence / notes

Not started.
