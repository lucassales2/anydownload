---
id: T-078
type: task
priority: P0
milestone: D5
tags: [task, ui, toolkit]
---

# T-078 — Edit follows host capabilities

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 5](../00-project/Phase-5-Media-Toolkit.md)

## Outcome

Edit enables resolutions a merge can satisfy, and enables an audio container only when the host capability includes it. A disabled choice names the host gap. The user cannot submit a choice the host cannot perform.

## Dependencies

- [T-075](T-075-Media-toolkit-contract.md) — `ToolkitCapabilities`.
- [T-077](T-077-Engine-executes-a-merge.md) — the compiler and engine honor the same capabilities.

## Context the next session needs

`PreviewEditPanel` disables heights above the best single-file video and disables MP3, WAV, and FLAC with the string "Needs the media toolkit (not built yet)". That string becomes wrong on a host that can merge. The panel reads extracted formats today; it needs the host `ToolkitCapabilities` as well. Do not enable a container just because FFmpeg exists on the developer's machine if the running host's capability set omits it.

## Work

- Thread `ToolkitCapabilities` into the preview/edit state. Default for tests and for web is the empty set.
- A resolution is enabled when a single-file format satisfies it, or when `canMerge` is true and a video-only format of that height exists with an audio-only format beside it.
- M4A and Opus are enabled when that container is in `capabilities` or a single-file format already has that container. MP3, WAV, and FLAC stay disabled until T-082 adds them to a host's set.
- Disabled reason: "This host cannot merge video and audio" or "This host cannot write MP3", not "not built yet", once the capability API exists. Web uses the host-gap wording.
- UI tests: a merge-capable capability enables a height that no progressive file has; an empty capability keeps it disabled; picking a disabled entry does not change state.

## Acceptance criteria

- [x] Edit enables a split-stream resolution only when `canMerge` is true.
- [x] MP3, WAV, and FLAC stay disabled in this task.
- [x] The disabled reason names the host gap.
- [x] Existing preview tests still pass for a single-file format list.

## Evidence / notes

Done on 2026-09-25.

Changed:

- `shared/core/.../FormatChoices.kt` — `mergeableVideoHeights` (video-only heights) and `hasAudioOnly`, plus `hasSplitStreams`; `from(info)` fills them. `ExtractorMediaPreviewSourceTest` asserts the split pair.
- `shared/core/.../AppGraph.kt` — `toolkitCapabilities` property, default `ToolkitCapabilities.Unavailable` (web and tests).
- `shared/ui/.../preview/PreviewEditPanel.kt` — takes `capabilities`; a resolution is enabled by a single-file height or, when `canMerge` and the source has a split pair, by a video-only height. M4A/Opus are enabled when the source carries them or the host capability set includes them; MP3/WAV/FLAC only when the capability set includes them (still empty until T-082). Disabled reasons are `This host cannot merge video and audio` and `This host cannot write MP3`.
- `shared/ui/.../preview/PreviewScreen.kt`, `shared/ui/.../App.kt` — thread the graph capabilities into the panel.
- `values/strings.xml` and `values-pt-rBR/strings.xml` — `preview_cannot_merge` and `preview_cannot_write` replace `preview_needs_toolkit`.
- `apps/desktop/.../Main.kt` — builds one `DesktopFfmpegToolkit` with the host process runner and PATH resolver, passes it to `HttpDownloadEngine`, and exposes its `capabilities()` through the graph.

Tests:

- `PreviewEditPanelTest` (7 tests) — a merge-capable host enables the 1080 split-only height and a disabled entry does not move state; an empty capability keeps it disabled with the host reason; MP3/WAV/FLAC are disabled with `This host cannot write MP3`; a capability-only M4A is enabled without a source stream; the single-file and no-format cases still pass.
- `PreviewFromExtractorUiTest` — the fixture now has a 1080 video-only stream plus audio; the empty test host shows the host-merge reason.

Commands run:

- `./gradlew :shared:ui:jvmTest :shared:core:jvmTest` — 81 UI tests and 311 core tests, 0 failures.
- `./gradlew :apps:desktop:test` — 120 tests, 13 skipped (opt-in live/oracle), 0 failures.
- `./gradlew :shared:core:iosSimulatorArm64Test` — 293 tests, 0 failures.
- `./gradlew :apps:android-engine-tests:test :apps:android:assembleDebug :shared:ui:compileKotlinIosSimulatorArm64 :shared:ui:compileKotlinWasmJs :shared:ui:compileAndroidMain` — BUILD SUCCESSFUL.
- `node --test apps/web-extension/test/bridge.mjs` — 22 pass.
