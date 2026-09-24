---
id: T-041
type: task
priority: P0
milestone: D2
tags: [task, android, engine]
---

# T-041 — Android HTTP engine and Chaquopy adapter

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 2](../00-project/Phase-2-Local-Kotlin-Engine.md) · [Client yt-dlp options](../05-research/Client-yt-dlp-options.md)

## Outcome

Android downloads direct files through shared Kotlin HTTP. Other URLs go through a Chaquopy + pinned yt-dlp adapter that lives only under `apps/android`.

## Dependencies

- [T-006](T-006-Review-security-licensing.md) has recorded Chaquopy, CPython, and bundled yt-dlp licenses.
- [T-039](T-039-Platform-http-and-files.md).

## Work

- Wire `HttpDownloadEngine` into the Android host. Replace the in-memory fake for real downloads.
- Add Chaquopy to the Android application or library module only. Pin the yt-dlp version at build time. There is no in-app `yt-dlp -U`.
- Shared modules must not import Chaquopy or Python.
- If Chaquopy or the pinned engine is missing, Settings says so and Add does not pretend a site URL started. Direct files still work.
- YouTube via Chaquopy may need a JS runtime; if QuickJS is not bundled in this task, fail YouTube with a redacted “challenge runtime missing” and still pass direct-file and one non-JS fixture if you have one.
- Write files under app-scoped storage. Do not load the whole media file into memory.

## Acceptance criteria

- [ ] Instrumented or JVM-equivalent tests cover direct-file success and a NeedsExtractor dispatch into a fake Chaquopy port.
- [ ] Debug APK compiles with Chaquopy configured or the task records why the Gradle plugin could not be applied in this environment.
- [ ] No Python types leak into `shared/core` or `shared/ui`.

## Evidence / notes

Not started. Chaquopy is MIT since 12.0.1. It is an Android-only adapter, not the ADR-004 engine.
