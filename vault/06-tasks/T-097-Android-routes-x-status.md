---
id: T-097
type: task
priority: P0
milestone: D7
tags: [task, android, twitter]
---

# T-097 — Android routes a matched X status through Kotlin

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 7](../00-project/Phase-7-X-Twitter.md) · [T-096](T-096-Gate-selected-media-download.md)

## Outcome

Android registers `TwitterIE` so a matched status URL is `KOTLIN` before any probe and never reaches Chaquopy. A JVM-equivalent fixture test previews and downloads a two-video status through the shared engines; `assembleDebug` or an emulator run closes the host. No live X/Twitter call.

## Dependencies

- [T-096](T-096-Gate-selected-media-download.md).

## Work

- `AndroidAppGraph`: add `TwitterIE(ExtractorHttp(transfer))` to `extractorRegistry` beside `YoutubeIE`. `AndroidRouteClassifier` already routes any registry match to `KOTLIN`, so no classifier change is needed; confirm it with a test.
- JVM-equivalent test in the existing Android engine test sources: use a fixture transfer that serves the T-094 redacted status JSON and two synthetic media bodies, then preview, select both videos, and download through the shared `HttpDownloadEngine`. Assert two files inside the app-style root, the status URL as the job source, and no Chaquopy/Python call.
- Assemble `:apps:android:assembleDebug` and `:apps:android:compileDebugAndroidTestKotlin`. If an emulator/AVD exists, run the fixture click-through and say so; otherwise the JVM-equivalent suite plus the assembled APK is the evidence and the emulator gap is recorded.

## Acceptance criteria

- [x] Android's registry includes `TwitterIE`; a matched status routes to `KOTLIN` with no HEAD, page GET, or Chaquopy.
- [x] The JVM-equivalent test previews and downloads a two-video fixture status with one file per selected video.
- [x] `:apps:android:assembleDebug` and `:apps:android:compileDebugAndroidTestKotlin` pass, or an emulator click-through is recorded with the command.
- [x] No live X/Twitter call, cookie, or token; no media file committed.

## Evidence / notes

Done 2026-09-25. Assemble path used; no emulator/AVD exists on this machine.

What landed:

- `apps/android/src/main/kotlin/com/anydownlod/android/engine/AndroidExtractors.kt`: the pure-engine registry builder (YouTube then `TwitterIE`) so the JVM-equivalent suite proves the same registry the app graph builds. `AndroidAppGraph` now calls `AndroidExtractors.registry(transfer, jsRuntime)`.
- `apps/android/src/test/kotlin/com/anydownlod/android/engine/AndroidXStatusTest.kt`: `aMatchedStatusRoutesToKotlinWithoutAProbe` (the registry contains `TwitterIE` and `AndroidRouteClassifier` returns `KOTLIN`) and `theFixturePathPreviewsAndDownloadsTwoSelectedVideosWithoutPython` (the preview lists both stable media ids; `AndroidRoutingEngine` downloads two files with the fixture bytes; the status URL stays the job source; the recording Chaquopy port received nothing).
- The fixture is synthesized JSON with `*.example` media hosts; no live X/Twitter call, cookie, or token.

Verification run 2026-09-25:

- `./gradlew :apps:android-engine-tests:test` — 22 tests, 0 failures (`AndroidXStatusTest` 2 / 0).
- `./gradlew :apps:android:assembleDebug :apps:android:compileDebugAndroidTestKotlin` — BUILD SUCCESSFUL; APK at `apps/android/build/outputs/apk/debug/android-debug.apk` (15,300,049 bytes).
- No emulator binary and no AVD directory on this machine, so the JVM-equivalent suite plus the assembled APK is the evidence; the emulator click-through remains a recorded gap.
