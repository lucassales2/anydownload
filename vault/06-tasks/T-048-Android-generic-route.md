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

- [x] `:apps:android-engine-tests:test` covers matching HTML success, cancel, unresolved HTML sent to the fake Chaquopy port, and direct-file still on HTTP.
- [x] Shared and Android engine sources still import no Chaquopy types outside `apps/android`.
- [x] No FFmpeg binary and no postprocessing API added.

## Evidence / notes

Done 2026-09-24.

- `AndroidRouteClassifier` mirrors the desktop route: HEAD probe stays; HTML/unknown now triggers a bounded GET (≤ `HttpDownloadEngine.MAX_HTML_BYTES`, same policy-checked redirect budget) through the shared `GenericExtractor`. Exactly one policy-passing media URL → `AndroidRoute.DIRECT_FILE` (a `HttpDownloadEngine` job); zero, several, or probe errors → `AndroidRoute.CHAQUOPY`, so unresolved HTML and site URLs still reach the Chaquopy port, and missing Chaquopy still fails honestly (`ENGINE_UNAVAILABLE`). Direct files stay on Kotlin HTTP and never touch Python. `AndroidAppGraph` wiring unchanged; the extractor call passes the classifier's `urlCheck` seam (UrlPolicy in production), exactly like desktop.
- Chaquopy, CPython, and yt-dlp remain under `apps/android` only at the T-041 pins; shared code imports no Chaquopy types (grep over `shared` shows none; the only hit is a doc-comment mention of the pin in the extractor notice). No MediaMuxer/FFmpeg/postprocessing API added.
- AGP/Compose APK blocker: not touched, per the task. The sanctioned `:apps:android-engine-tests` JVM module runs the pure engine sources; `:apps:android:compileDebugKotlin` still compiles. Recorded, not treated as a new D3 failure.
- Tests: `AndroidRouteClassifierTest` — `htmlFixtureWithOneMediaElementRoutesToHttpEngine`, `htmlWithoutMediaRoutesToChaquopy` (local `HttpServer` + fixture exception), existing direct-file and loopback tests kept. `AndroidEngineTest` — `htmlFixtureCompletesThroughHttpEngineWithoutPython` (COMPLETED, file on disk, port untouched), `unresolvedHtmlStillDispatchesIntoTheFakeChaquopyPort` (request reaches the fake port, http untouched), `cancelOfHtmlFixtureJobLeavesNothingBehind` (CANCELLED, root empty), plus the existing direct-file/cancel/missing-Chaquopy tests untouched.
- Verification: `./gradlew :apps:android-engine-tests:test` — 11 tests, 0 failures; `:apps:android:compileDebugKotlin`, `:apps:desktop:test`, `:shared:core:jvmTest`, `:shared:ui:jvmTest`, `:shared:core:compileKotlinIosSimulatorArm64`, `:shared:core:compileKotlinWasmJs` all BUILD SUCCESSFUL.
