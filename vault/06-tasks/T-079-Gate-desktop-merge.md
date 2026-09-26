---
id: T-079
type: task
priority: P0
milestone: D5
tags: [task, verification, toolkit]
---

# T-079 — Gate: desktop merge, web still refuses

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 5](../00-project/Phase-5-Media-Toolkit.md)

## Outcome

Desktop turns a local split-stream fixture into one playable file through the Kotlin engine, and an opt-in public YouTube video whose picture needs a merge does the same. Web still disables that choice and does not run a toolkit. Single-file downloads are unchanged. Android and iOS adapters do not start before this gate.

## Dependencies

- [T-075](T-075-Media-toolkit-contract.md) through [T-078](T-078-Edit-follows-capabilities.md).

## Context the next session needs

Use the public video already named in the desktop oracle test. Do not paste the URL, a `googlevideo` host, or media bytes into the vault. The opt-in run is `-PliveExtractorTests=true` and requires `ffmpeg` on `PATH`. If `ffmpeg` is missing, record that and do not vendor one. Web evidence is the existing preview test plus a check that the page still has no FFmpeg and that Add refuses a merge-only quality.

## Work

- Desktop: local fixture merge through `HttpDownloadEngine` and `DesktopFfmpegToolkit`; ffprobe shows one video stream and one audio stream. Opt-in: one public YouTube URL completes with one artifact and no CLI process.
- Web: capability stays empty; the merge-only quality stays disabled; the page fetches nothing new.
- Re-run D2/D3/D4 single-file and generic suites named in T-074's command list, minus the live YouTube tests.
- Do not start T-080 until the acceptance boxes below are checked.

## Acceptance criteria

- [x] Desktop local fixture merge is one probed file. Temps are gone after success.
- [x] Opt-in desktop YouTube merge completes with one artifact and no yt-dlp process, or the missing-tool result is written here.
- [x] Web still disables the merge-only choice and ships no toolkit.
- [x] Single-file and generic tests stay green.

## Evidence / notes

Done on 2026-09-25. No URL, media bytes, or signed link is recorded here.

Desktop local fixture (from T-077): `DesktopEngineMergeTest` runs the real `DesktopFfmpegToolkit` through `HttpDownloadEngine` on a generated local split pair; `ffprobe` reports one H.264 video and one AAC audio stream and no `.anydownload-` temp remains.

Opt-in live merge: `./gradlew :apps:desktop:test -PliveExtractorTests=true --tests "com.anydownlod.desktop.engine.DesktopLiveKotlinMergeTest"` — passed. ffmpeg and ffprobe 9.0.1 were on PATH. The run printed `live desktop merge: state=COMPLETED artifacts=1 video=1 audio=1`; the observed job phases included `merging`, so the engine took the two-temp merge path, and the fake CLI runner recorded that no yt-dlp process started.

Web: `PreviewFromExtractorUiTest` (empty-capability graph) shows the 1080 split-only quality disabled with `This host cannot merge video and audio`. The new `the web page ships no process or platform muxer` check in `apps/web-extension/test/bridge.test.mjs` scans `apps/web/src/wasmJsMain`, `shared/ui/src`, and `shared/core/src/commonMain` for `ProcessBuilder|MediaMuxer|MediaExtractor|AVFoundation` and finds none. `WebAppGraph` does not override `toolkitCapabilities`, so it keeps the empty default.

Suites re-run (T-074's list minus the live YouTube runs):

- `./gradlew :shared:core:jvmTest` — 311 tests, 0 failures.
- `./gradlew :shared:core:iosSimulatorArm64Test` — 293 tests, 0 failures.
- `CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew :shared:core:wasmJsBrowserTest` — 282 tests, 0 failures.
- `./gradlew :shared:ui:jvmTest` — 81 tests, 0 failures.
- `./gradlew :apps:desktop:test` — 121 tests, 14 skipped (opt-in live/oracle), 0 failures.
- `./gradlew :apps:android-engine-tests:test :apps:android:assembleDebug` — 14 tests, 0 failures; APK assembles.
- `node --test apps/web-extension/test/bridge.test.mjs` — 23 pass, 0 fail.

Android and iOS toolkit adapters have not started.
