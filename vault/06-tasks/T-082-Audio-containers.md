---
id: T-082
type: task
priority: P1
milestone: D5
tags: [task, engine, toolkit, formats]
---

# T-082 — Audio containers

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 5](../00-project/Phase-5-Media-Toolkit.md)

## Outcome

Desktop produces MP3, WAV, and FLAC through FFmpeg, and ffprobe checks the container. Android and iOS keep copying M4A and Opus. They enable WAV only if this task proves a platform PCM export; otherwise WAV stays disabled. MP3 and FLAC stay disabled on Android, iOS, and web. Edit shows the host-gap reason.

## Dependencies

- [T-076](T-076-Desktop-ffmpeg.md), [T-078](T-078-Edit-follows-capabilities.md), [T-080](T-080-Android-mediamuxer.md), [T-081](T-081-Ios-avfoundation.md).

## Context the next session needs

Transcode is allowed on desktop only, and only for these three containers. The argument list must name the codec explicitly. Do not add a bundled encoder on mobile. A platform API that cannot write the container is an honest disabled choice, not a silent M4A rename.

## Work

- Extend `DesktopFfmpegToolkit.capabilities()` with MP3, WAV, and FLAC. `extractAudio` transcodes when the source codec is not already that container, then ffprobe asserts the format name.
- On Android and iOS, try a platform PCM WAV export only if the API can do it without a third-party encoder. If it cannot, leave WAV out of the set and record why.
- Edit enables a container only when it is in the host capability set. Web's set stays empty for these three.
- Tests: desktop fixture audio becomes each container when `ffmpeg` is present; mobile tests assert the capability set they actually ship.

## Acceptance criteria

- [x] Desktop ffprobe confirms MP3, WAV, and FLAC outputs from a local fixture.
- [x] Android and iOS capability sets match what their tests produce. MP3 and FLAC are absent.
- [x] Web still disables MP3, WAV, and FLAC with the host-gap reason.
- [x] No encoder binary is added to the repo.

## Evidence / notes

Done on 2026-09-25. FFmpeg stays on `PATH`; no binary was added.

Changed:

- `shared/core/.../format/OptionsToSpec.kt` — `compile(options, canMerge, audioContainers)`. MP3/WAV/FLAC compile to `CompiledSpec.ExtractAudio("ba", container)` when the host lists the container, else to `NeedsToolkit` with `This host cannot write MP3 audio.`
- `shared/core/.../engine/HttpDownloadEngine.kt` — `FormatResolution.ExtractAudio` and `downloadAndExtractAudio`: download the best audio-only stream to a temp, set the `extracting audio` phase, call `MediaToolkit.extractAudio`, and publish one artifact with the container extension. Temps are deleted on success, failure, and cancel.
- `shared/core/.../engine/WebExtensionEngine.kt` — the `ExtractAudio` branch fails typed with the web-gap message.
- `apps/desktop/.../DesktopFfmpegToolkit.kt` — capabilities add MP3/WAV/FLAC; `extractAudio` probes the source codec and copies when it already matches, otherwise names the encoder explicitly (`libmp3lame`, `pcm_s16le`, `flac`); ffprobe asserts the written format.
- Mobile: Android `MediaMuxer` writes only MP4/WebM and has no PCM/WAV muxer, and `AVAssetExportSession` has no WAV/PCM preset. Both hosts keep `{M4A, OPUS}` and leave MP3, WAV, and FLAC out rather than silently writing a different codec. No third-party encoder was added.

Tests:

- `DesktopFfmpegToolkitTest.extractAudioProducesMp3WavAndFlac` — an AAC fixture becomes MP3 (`mp3`), WAV (`pcm_s16le`), and FLAC (`flac`), each ffprobe-checked.
- `DesktopEngineMergeTest.engineExtractsAudioToMp3ThroughTheDesktopToolkit` — the shared engine produces `Fixture Clip.mp3` from a local fixture end to end.
- `EngineMergeTest` — audio extraction publishes the requested container, and a toolkit failure is typed with no artifact.
- `OptionsToSpecTest` — `ExtractAudio` when the capability is present, `NeedsToolkit` otherwise.
- `PreviewEditPanelTest.capabilityContainersEnableMp3WavAndFlac` — Edit enables them only from the host set; the empty-capability panel test still shows the host-gap reason.
- Android `AndroidMediaToolkitTest` and iOS `IosMediaToolkitTest` assert MP3, WAV, and FLAC are absent from their sets.

Commands run:

- `./gradlew :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test` — 313 + 82 + 123 tests (14 opt-in skipped), 0 failures.
- `./gradlew :shared:core:iosSimulatorArm64Test :shared:ui:iosSimulatorArm64Test` — 295 + 4 tests, 0 failures.
- `CHROME_BIN="…Brave Browser" ./gradlew :shared:core:wasmJsBrowserTest` — 284 tests, 0 failures.
- `./gradlew :apps:android-engine-tests:test :apps:android:assembleDebug :apps:android:compileDebugAndroidTestKotlin` — 20 tests, 0 failures; APK and instrumented test compile.
- `node --test apps/web-extension/test/bridge.test.mjs` — 23 pass.
- Binary scan: no `ffmpeg`/`lame`/`flac` file added; only the desktop argument list names the encoders.
