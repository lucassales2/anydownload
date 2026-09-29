---
id: T-111
type: task
priority: P1
milestone: D8
tags: [task, formats]
---

# T-111 — Profile controls follow the host toolkit

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 8](../00-project/Phase-8-On-device-core.md) · [ADR-012](../03-decisions/ADR-012-On-device-core-phase.md) · [T-013](T-013-Media-formats.md)

## Outcome

The add-form video and audio choices do what D5 already implemented, and they say so when the host cannot. [T-013](T-013-Media-formats.md) closes on that evidence.

## Dependencies

- [T-106](T-106-Four-host-m1.md).

## Context the next session needs

D5 shipped merge and M4A/Opus copy on desktop, Android, and iOS, and MP3/WAV/FLAC transcode on desktop only. Web cannot merge or transcode. The add form already has Auto/MP4/iOS, codec, resolution, and audio container fields. T-013's note still says "not started", which is stale. Do not add an encoder and do not vendor FFmpeg.

## Work

- Map the existing profile, codec, and resolution fields through the typed options onto the format selector. A choice the extracted formats cannot satisfy fails typed with `UNSUPPORTED_FORMAT` and does not silently pick another container.
- Audio: M4A and Opus follow the host copy path. MP3, WAV, and FLAC stay enabled only where T-082 proved an encoder (desktop). On Android, iOS, and web those three stay disabled with the existing capability reason.
- Desktop: one probe test (ffprobe or the existing D5 probe) checks the container of a merged fixture and of an MP3 transcode. Cite it from T-013.
- Check T-013's acceptance boxes, replace the "not started" evidence with the D5 commands plus this task's tests, and move T-013 to Done.
- Do not implement captions, artwork embedding, or chapter split.

## Acceptance criteria

- [x] An impossible profile fails typed and does not download a different container.
- [x] MP3, WAV, and FLAC are enabled on desktop and disabled on Android, iOS, and web.
- [x] A desktop probe confirms one merged fixture and one MP3 fixture.
- [x] T-013 is Done, and its Evidence cites D5 and this task.

## Evidence / notes

2026-09-29.

- The user-facing profile controls were already capability-honest: `PreviewEditPanel` gates quality by the extracted formats (`mergeReady`/`availableFormats`) and gates audio containers by `capabilities.audioContainers`, with `preview_cannot_merge`, `preview_cannot_write`, and `preview_not_available_source` reasons. `PreviewEditPanelTest` (8 tests) covers a split-only height disabled with the host reason, M4A/Opus enabled with MP3/WAV/FLAC disabled plus the "This host cannot write MP3" reason, a capability-only container enabled without a source stream, and desktop-style caps with all containers enabled.
- The shared `AddForm` advanced panel was still offering every container. It now takes `ToolkitCapabilities` (from `AppShell` → `graph.toolkitCapabilities`) and gates `AudioContainer.entries` with `optionEnabled = { it in capabilities.audioContainers }` and the existing `preview_cannot_write` reason. New `AddFormUiTest` cases: desktop caps enable MP3/WAV/FLAC; Android/iOS caps enable M4A/Opus and disable MP3/WAV/FLAC with the reason; empty (web) caps disable all five.
- Typed options already compile through `OptionsToSpec`/`FormatSelector`; an impossible choice fails `UNSUPPORTED_FORMAT` instead of silently changing container: `EngineExtractionTest.mp3FailsTypedWhenTheHostCannotWriteIt` (message names MP3 and M4A), `EngineMergeTest` unsupported-merge cases, and the `FormatSelectorTest`/`OptionsToSpecTest` suites.
- Desktop probes: `DesktopFfmpegToolkitTest.mergeCopiesBothStreamsIntoOneFile` probes the merged `mp4` container and both stream codecs; `extractAudioProducesMp3WavAndFlac` probes each output container/codec/bitrate; `extractAudioCopiesM4aWithoutReencoding` proves the copy path. No new encoder was added and FFmpeg was not vendored.

Verification run:

- `./gradlew :shared:ui:jvmTest` — BUILD SUCCESSFUL; ui jvm 99 tests, 0 failures (`AddFormUiTest` 11, `PreviewEditPanelTest` 8).
- Cross-target: `CHROME_BIN=... ./gradlew :shared:ui:compileKotlinWasmJs :shared:ui:compileKotlinIosSimulatorArm64 :shared:ui:iosSimulatorArm64Test :apps:web:wasmJsBrowserTest :apps:android:compileDebugKotlin` — BUILD SUCCESSFUL; ui iOS 8, web 6, 0 failures.
- Desktop probes: `:apps:desktop:test` — 131 tests, 0 failures, including `DesktopFfmpegToolkitTest` 9/9.
