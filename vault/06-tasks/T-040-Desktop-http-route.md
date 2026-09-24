---
id: T-040
type: task
priority: P0
milestone: D2
tags: [task, desktop, engine]
---

# T-040 — Desktop routes direct files through Kotlin HTTP

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 2](../00-project/Phase-2-Local-Kotlin-Engine.md)

## Outcome

The desktop host uses the shared HTTP engine for direct files and keeps `YtDlpCliEngine` for every other URL.

## Dependencies

- [T-039](T-039-Platform-http-and-files.md).
- D1 desktop CLI (`YtDlpCliEngine`) stays.

## Work

- Compose a routing engine in `apps/desktop` only: classify, then dispatch.
- Direct-file jobs never spawn yt-dlp.
- NeedsExtractor jobs keep the D1 CLI path, including missing-tool behavior.
- Settings tool probe still reports yt-dlp and ffmpeg. Add an honest line that direct files do not need them.
- Persist jobs through the existing JSON store.

## Acceptance criteria

- [ ] Desktop tests: a mock direct file completes without `ProcessBuilder`; an HTML/site URL still goes to the CLI fake/fixture path.
- [ ] Cancel of a direct-file job stops the HTTP stream and leaves no completed file.
- [ ] `./gradlew :apps:desktop:test` passes.

## Evidence / notes

Not started.
