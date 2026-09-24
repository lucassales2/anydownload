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

- [x] `./gradlew :apps:android-engine-tests:test` covers the routing cases and passes.
- [x] `./gradlew :apps:android:assembleDebug` succeeds.
- [x] Emulator click-through recorded, or the blocker and the JVM-equivalent evidence recorded.
- [x] No Chaquopy import outside `apps/android`; no new Python package.

## Evidence / notes

Done 2026-09-24.

- `AndroidRoute.KOTLIN` added; `AndroidRouteClassifier` takes the `ExtractorRegistry` and returns `KOTLIN` before any probe, so a matched URL sends no HEAD, no page GET, and never reaches Python. `AndroidRoutingEngine` sends `KOTLIN` to the shared engine.
- `AndroidAppGraph` builds one `JavaNetHttpTransfer` and one `ExtractorRegistry(YoutubeIE)` shared by the download engine and the classifier, and wires `ExtractorMediaPreviewSource` as the preview source (falling back to the existing behavior only where the registry has no match). The request port already lived in `jvmAndroidMain` from T-056, so no Android-specific transfer change was needed.
- Tests: `AndroidEngineTest` gains two cases: the Kotlin route completes through the shared HTTP engine with the extracted title naming the artifact and never touches the fake Chaquopy port, and a registry-matched URL routes `KOTLIN` with zero probe requests at a local server while an unmatched URL still routes `CHAQUOPY`. The existing direct-file, needs-extractor, and cancel cases stay green.
- Emulator click-through: not available in this environment (the SDK emulator has no AVD, `adb devices` is empty, and no device is attached), so the JVM-equivalent suite plus the successful debug APK are the recorded evidence, as the task note allows. `:apps:android:assembleDebug` succeeds (AGP 9.1.1) along with `:apps:android:compileDebugKotlin`.
- Verification: `:apps:android-engine-tests:test` green; `:apps:android:assembleDebug` BUILD SUCCESSFUL; `:shared:core:jvmTest` 257 tests, `:shared:ui:jvmTest` 76 tests, `:apps:desktop:test` green; iOS/wasm-test/Android/web compiles green; `:tools:port-manifest:check` green. Grep confirms no Chaquopy import outside `apps/android` (only KDoc mentions) and no new Python package. No manifest change.
