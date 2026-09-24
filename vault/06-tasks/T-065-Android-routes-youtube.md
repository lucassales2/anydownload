---
id: T-065
type: task
priority: P0
milestone: D4
tags: [task, android, engine]
---

# T-065 — Android routes matched URLs through Kotlin; Chaquopy for the rest

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [T-048](T-048-Android-generic-route.md)

## Outcome

On Android, a registry-matched URL runs the shared engine. Unmatched site URLs still go to the Chaquopy port. No new Python.

## Dependencies

- [T-063](T-063-Preview-from-extractor.md).

## Work

- `AndroidRouteClassifier`: registry check before the probe, like desktop. `AndroidRoutingEngine` unchanged otherwise.
- `AndroidAppGraph`: `ExtractorMediaPreviewSource` first, then the existing source.
- `JavaNetHttpTransfer.android.kt` gains the request port from T-056 if the Android source set differs from JVM.
- `apps/android-engine-tests`: a YouTube URL completes through the HTTP engine with a fixture extractor and never dispatches into the fake Chaquopy port; an unmatched URL still dispatches to Chaquopy; cancel and direct-file cases stay green.
- Debug APK assembles (AGP 9.1.1). If an emulator is available, run the click-through; otherwise the JVM-equivalent suite is the evidence, as in D3, and the note says so.

## Acceptance criteria

- [ ] `./gradlew :apps:android-engine-tests:test` covers the routing cases and passes.
- [ ] `./gradlew :apps:android:assembleDebug` succeeds.
- [ ] Emulator click-through recorded, or the blocker and the JVM-equivalent evidence recorded.
- [ ] No Chaquopy import outside `apps/android`; no new Python package.

## Evidence / notes

Not started.
