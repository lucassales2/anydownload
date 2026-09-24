---
id: T-047
type: task
priority: P0
milestone: D3
tags: [task, desktop, engine]
---

# T-047 — Desktop routes a matching page through Kotlin

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

## Outcome

A desktop HTML fixture with one media element downloads without `ProcessBuilder`. Every URL the generic subset does not resolve still uses `YtDlpCliEngine`.

## Dependencies

- [T-046](T-046-Engine-uses-generic-extractor.md).
- D1 CLI and D2 `DesktopRoutingEngine` stay.

## Work

- Extend the desktop route so a page the shared extractor resolves is owned by `HttpDownloadEngine`.
- Unresolved HTML and other site URLs keep the CLI path, including missing-tool behavior.
- Direct files still never spawn a process.
- Settings still report yt-dlp and ffmpeg. One honest line: a simple media page does not need them; other sites do.
- Jobs still persist through the existing JSON store.

## Acceptance criteria

- [ ] Desktop tests: fixture HTML completes with no process; a non-matching site URL still reaches the CLI fake; direct file still skips the CLI.
- [ ] Cancel of the HTML-backed job leaves no completed file.
- [ ] `./gradlew :apps:desktop:test` passes.

## Evidence / notes

Not started.
