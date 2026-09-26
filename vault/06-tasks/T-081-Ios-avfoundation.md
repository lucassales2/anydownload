---
id: T-081
type: task
priority: P0
milestone: D5
tags: [task, engine, ios, toolkit]
---

# T-081 — iOS AVFoundation remux

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 5](../00-project/Phase-5-Media-Toolkit.md)

## Outcome

iOS merges one video file and one audio file with AVFoundation, and copies an M4A or Opus audio stream out. Incompatible codecs fail typed. No subprocess. Foreground-only behavior is unchanged. Evidence is a local simulator fixture, not a live YouTube fetch.

## Dependencies

- [T-079](T-079-Gate-desktop-merge.md) — desktop merge is proven before this adapter.

## Context the next session needs

The adapter lives in the iOS host, not in `shared/core`. AVFoundation export can passthrough compatible tracks. Do not shell out. The simulator still cannot reach YouTube; do not use a network URL in this task. MP3 and FLAC stay out of `capabilities()` unless a later task proves an encoder.

## Work

- `IosMediaToolkit` implements `MediaToolkit`. `canMerge = true`. Audio containers: M4A and Opus, stream copy only.
- Merge and extract write into the sandbox. Failure and cancel delete the partial file.
- Simulator test uses the same kind of local fixture as T-080. Record the export preset and the probed track counts.

## Acceptance criteria

- [x] A compatible local pair becomes one sandbox file with a video track and an audio track.
- [x] An incompatible pair fails typed and leaves no file.
- [x] Capabilities omit MP3 and FLAC.
- [x] No subprocess is started. The test does not contact YouTube.

## Evidence / notes

Done on 2026-09-25. Simulator fixtures only; no network, no committed media, no subprocess.

Added:

- `shared/ui/src/iosMain/kotlin/com/anydownlod/ui/media/IosMediaToolkit.kt` — `AVMutableComposition` from one video track and one audio track exported with `AVAssetExportPresetPassthrough`; audio copy the same way. Capabilities are `canMerge = true` with `{M4A, OPUS}`. Failures are `ToolkitError.IncompatibleStreams`/`Io` and delete the partial destination; cancellation cancels the export and deletes the file. Opus has no Ogg writer in AVFoundation, so it copies into a Core Audio Format file; the `.opus` container is not produced (recorded limit).
- `shared/ui/src/iosTest/kotlin/com/anydownlod/ui/media/IosMediaFixtures.kt` — on-simulator fixtures: a video-only H.264 MP4 written with `AVAssetWriter` and an audio-only AAC M4A with `AVAudioFile`.
- `shared/ui/src/iosTest/kotlin/com/anydownlod/ui/media/IosMediaToolkitTest.kt` — 4 tests.
- `IosAppGraph` wires the toolkit into `HttpDownloadEngine` and exposes `capabilities()`.
- `shared/ui/build.gradle.kts` — `iosTest` gets `kotlin("test")` and coroutines.

Commands run:

- `./gradlew :shared:ui:iosSimulatorArm64Test` — 4 tests, 0 failures. Export preset: `AVAssetExportPresetPassthrough`; the merge test probes one video track and one audio track in the sandbox file.
- `./gradlew :shared:core:jvmTest :shared:core:iosSimulatorArm64Test :shared:ui:jvmTest :shared:ui:iosSimulatorArm64Test :apps:desktop:test :apps:android-engine-tests:test :apps:android:assembleDebug` — BUILD SUCCESSFUL.
- `CHROME_BIN="…Brave Browser" ./gradlew :shared:core:wasmJsBrowserTest :shared:ui:compileKotlinWasmJs :shared:ui:compileKotlinIosSimulatorArm64 :shared:ui:compileAndroidMain` — BUILD SUCCESSFUL.
- `node --test apps/web-extension/test/bridge.test.mjs` — 23 pass.
- `grep -rn "ProcessBuilder\|posix_spawn\|system(" shared/ui/src/iosMain` — no matches; the fixtures and tests use only local temp files.
