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

- [x] Instrumented or JVM-equivalent tests cover direct-file success and a NeedsExtractor dispatch into a fake Chaquopy port.
- [x] Debug APK compiles with Chaquopy configured or the task records why the Gradle plugin could not be applied in this environment.
- [x] No Python types leak into `shared/core` or `shared/ui`.

## Evidence / notes

Done on 2026-09-23.

Commands run:

- `./gradlew :apps:android-engine-tests:test` - 7 tests, 0 failures (JVM-equivalent Android engine tests; a new pure-JVM module that compiles the `com.anydownlod.android.engine` sources - no Android/Compose/Python imports there).
- `./gradlew :apps:android:compileDebugKotlin` - Android main sources compile (default config).
- `./gradlew :apps:android:assembleDebug -DchaquopyVersion=17.0.0` - Chaquopy Gradle plugin **17.0.0 applies and validates** on this module (ndk abiFilters arm64-v8a+x86_64, local homebrew Python 3.11, pip pin `yt-dlp==2026.8.19` verified on PyPI 2026-09-23; pip wheel is Unlicense source, release-executable GPL caveat does not apply). The build then fails **only** at the pre-existing `checkDebugAarMetadata`: androidx.compose `1.12.0` AARs require AGP `>= 9.1.0` (published only as alpha) while the catalog pins AGP `9.0.0`. The Android app was never assembled in this environment (it used the in-memory graph before), so this is a pre-existing toolchain blocker, not a Chaquopy failure.
- Default checkouts evaluate cleanly (`:apps:android:help`); Chaquopy is applied only when `-DchaquopyVersion` is set, so no CPython/pip resolution happens on normal builds.
- `grep -rn -i 'chaquopy\|com.chaquo\|import python' shared/core shared/ui` - **no matches**; the Python seam lives only under `apps/android`.

Next action to produce a Debug APK: once a stable AGP `9.1.x` is in the catalog (or Compose is pinned to a version whose AAR metadata accepts AGP 9.0.0), assemble with `-DchaquopyVersion=17.0.0` and a local Python 3.13, then attach the real `PythonChaquopyPort` (only the `ChaquopyPort` interface, `ChaquopyEngine`, and fakes exist today; `com.chaquo.python` classes cannot compile into default builds). YouTube's JS-challenge runtime is not bundled in this task; the engine maps any port failure to a redacted `EXTRACTION_FAILURE` so raw Python output never reaches a job.

Test names (`:apps:android-engine-tests`):

- `directFileCompletesThroughHttpEngineAndNeverTouchesPython`
- `needsExtractorDispatchesIntoTheFakeChaquopyPort`
- `missingChaquopyFailsSiteUrlsHonestlyWithoutPretending` (ENGINE_UNAVAILABLE, non-retryable, no HTTP engine use)
- `cancelOfDirectFileStopsTheStreamAndLeavesNothingBehind`
- `directFileHeadRoutesToHttpEngine`, `htmlPageRoutesToChaquopy`, `defaultPolicyNeverSendsLoopbackToPython`

Delivered under `apps/android`: `AndroidRoutingEngine`, `AndroidRouteClassifier`, `ChaquopyPort` (interface + `pinnedVersion` 2026.8.19), `ChaquopyEngine`, `AndroidAppGraph` (files under `Context.filesDir` = app-scoped; HTTP engine streams in chunks, never whole-file in memory), `MainActivity` now builds the real graph. `AndroidToolProbe` reports the pinned yt-dlp availability honestly: when Chaquopy is missing, Settings shows not-found and a site URL fails instead of pretending it started, while direct files keep working.

Chaquopy is MIT since 12.0.1 (17.0.0 verified at T-006). It is an Android-only adapter, not the ADR-004 engine.
