---
id: T-048
type: task
priority: P0
milestone: D3
tags: [task, android, engine]
---

# T-048 — Android routes a matching page through Kotlin

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

## Outcome

Android uses the shared extractor for a matching HTML page. Other site URLs still go to the Chaquopy port. No new Python and no MediaMuxer.

## Dependencies

- [T-046](T-046-Engine-uses-generic-extractor.md).
- [T-041](T-041-Android-http-and-chaquopy.md) — Chaquopy port stays the fallback.

## Work

- Extend `AndroidRoutingEngine` (or its successor) so a resolved generic page is an HTTP-engine job.
- Unresolved site URLs still dispatch to the Chaquopy port. Missing Chaquopy still fails honestly.
- Direct files stay on Kotlin HTTP.
- Do not try to fix the AGP/Compose APK mismatch recorded on T-041. JVM-equivalent tests are enough if the APK is still blocked. If you hit that blocker, record it; do not treat it as a new D3 failure.
- Chaquopy, CPython, and yt-dlp stay under `apps/android` only, at the versions T-041 pinned.

## Acceptance criteria

- [ ] `:apps:android-engine-tests:test` covers matching HTML success, cancel, unresolved HTML sent to the fake Chaquopy port, and direct-file still on HTTP.
- [ ] Shared and Android engine sources still import no Chaquopy types outside `apps/android`.
- [ ] No FFmpeg binary and no postprocessing API added.

## Evidence / notes

Not started.
