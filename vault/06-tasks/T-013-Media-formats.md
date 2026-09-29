---
id: T-013
type: task
priority: P1
milestone: M2
tags: [task, downloads, formats]
---

# T-013 — Video profiles and audio extraction

[Home](../Home.md) · [Kanban](../Kanban.md) · [Parity](../01-product/Feature-parity.md)

## Outcome

Tested video/codec/quality profiles and M4A/MP3/Opus/WAV/FLAC audio output for F-02/F-03.

## Dependencies

- [T-010](T-010-Remote-vertical-slice.md).

## Acceptance criteria

- [x] Match reviewed Auto/MP4/iOS-compatible, codec preference and resolution/best/worst controls with explicit fallback behavior.
- [x] Implement format-appropriate audio quality choices and FFmpeg extraction/conversion; distinguish source selection, muxing and transcoding.
- [x] Validate actual codec/container/bitrate using output probes; do not promise native playback of every output on every target.
- [x] Test missing formats/FFmpeg, unsupported combinations, unknown sizes, postprocessing failure and cancellation.

## Evidence / notes

D5 shipped merge and audio extract; this card stayed open. [T-111](T-111-Profile-honesty.md) in [Phase 8](../00-project/Phase-8-On-device-core.md) ties the profile controls to those capabilities and closes this card. Artwork/metadata/captions stay in T-015; library tagging is not implied.

### Closed by Phase 8 (2026-09-29)

- **Profiles and fallback:** the typed options (`VideoContainerProfile` Auto/MP4/iOS, `VideoCodec` Auto/H.264/HEVC/AV1/VP9, `QualityPreference` Best/Worst/resolution, `AudioContainer` M4A/MP3/Opus/WAV/FLAC, bitrate) compile through `OptionsToSpec`/`FormatSelector`; `resolveFormat` fails `UNSUPPORTED_FORMAT` when the extracted formats cannot satisfy the choice instead of switching container. Tests: `FormatSelectorTest`, `OptionsToSpecTest`, `EngineExtractionTest.mp3FailsTypedWhenTheHostCannotWriteIt`, `EngineMergeTest` unsupported-merge cases.
- **Selection vs mux vs transcode:** direct single-format selection copies bytes; the merge path muxes video+audio through the host toolkit; audio extraction copies M4A/Opus where the host has only those and transcodes MP3/WAV/FLAC on desktop. `DesktopFfmpegToolkitTest` (`mergeCopiesBothStreamsIntoOneFile`, `extractAudioCopiesM4aWithoutReencoding`, `extractAudioProducesMp3WavAndFlac`) and the mobile `AndroidMediaToolkitTest`/`IosMediaToolkitTest` pin the distinction.
- **Output probes:** `DesktopFfmpegToolkitTest` uses `ffprobe` to check the merged `mp4` container, both stream codecs, and each audio output's container/codec/bitrate. Mobile remux outputs are probed by their platform toolkit tests. Native playback of every output on every target is not promised; web merge/transcode stays disabled.
- **Failure coverage:** missing `ffmpeg`/`ffprobe` fails typed without starting a process; an incompatible copy fails typed and deletes the destination; postprocessing failure maps to `POSTPROCESSING_FAILURE`; cancellation kills the process and leaves no output; unknown sizes stay unknown (`JobProgress.percent` null).
- **Phase 8 evidence:** T-111 added capability gating to the shared `AddForm` and ran `AddFormUiTest` (11), `PreviewEditPanelTest` (8), and the desktop toolkit probes (9). Commands: `./gradlew :shared:ui:jvmTest`; `CHROME_BIN=... ./gradlew :shared:ui:iosSimulatorArm64Test :apps:web:wasmJsBrowserTest`; `./gradlew :apps:desktop:test`.
