---
id: T-075
type: task
priority: P0
milestone: D5
tags: [task, engine, kmp, toolkit]
---

# T-075 — MediaToolkit contract

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 5](../00-project/Phase-5-Media-Toolkit.md) · [ADR-009](../03-decisions/ADR-009-Media-toolkit-phase.md)

## Outcome

Common code can ask a host to merge one video file with one audio file, or to extract audio into a named container, and can read what that host is able to do. A missing toolkit is a typed error. No process API enters `shared/core`.

## Dependencies

- [T-074](T-074-Phase-4-verification.md) — D4's single-file engine, which this contract sits beside.

## Context the next session needs

`FormatSelector` already returns `Selection.Merge`. `HttpDownloadEngine` rejects that selection with `UNSUPPORTED_FORMAT`. `OptionsToSpec` never emits a merge. Package for this task: `com.anydownlod.core.postprocess`. Do not call FFmpeg, MediaMuxer, or AVFoundation here. Host adapters are T-076, T-080, and T-081.

## Work

- Add `MediaToolkit` with `merge(video: Path, audio: Path, destination: Path)`, `extractAudio(source: Path, container: AudioContainer, destination: Path)`, and `capabilities(): ToolkitCapabilities`.
- `ToolkitCapabilities` carries `canMerge` and the set of `AudioContainer` values the host can produce. The web and test default is `canMerge = false` and an empty container set.
- Failures are a typed `ToolkitError` the engine can map to `JobErrorCode.UNSUPPORTED_FORMAT` (incompatible codecs, missing tool) or a retryable I/O failure. Messages name the reason and never include tool stderr.
- A `UnavailableToolkit` implementation returns the empty capabilities and fails both operations typed. Unit-test it. Common source must not reference `ProcessBuilder`, `ffmpeg`, `MediaMuxer`, or `AVFoundation`.

## Acceptance criteria

- [x] `MediaToolkit`, `ToolkitCapabilities`, `ToolkitError`, and `UnavailableToolkit` exist in common code and are unit-tested.
- [x] The empty capability set is the default used by tests that do not supply a toolkit.
- [x] `shared/core` common source has no `ProcessBuilder` and no platform muxer import.
- [x] D4 single-file tests still pass. The engine does not call the toolkit yet.

## Evidence / notes

Done on 2026-09-25.

Added under `com.anydownlod.core.postprocess`:

- `shared/core/src/commonMain/kotlin/com/anydownlod/core/postprocess/MediaToolkit.kt` — `MediaFilePath` (opaque host token; common code has no platform `Path`), `ToolkitCapabilities` (default `canMerge = false`, empty container set, `Unavailable`), sealed `ToolkitError` (`ToolUnavailable`, `IncompatibleStreams`, retryable `Io`), `MediaToolkit` (`capabilities()`, suspend `merge`, suspend `extractAudio`), and `UnavailableToolkit`.
- `shared/core/src/commonTest/kotlin/com/anydownlod/core/postprocess/MediaToolkitContractTest.kt` — 6 tests.

Commands run:

- `./gradlew :shared:core:jvmTest --tests "com.anydownlod.core.postprocess.MediaToolkitContractTest"` — 6 tests, 0 failures.
- `./gradlew :shared:core:jvmTest :shared:core:compileKotlinWasmJs :shared:core:compileKotlinIosSimulatorArm64 :shared:core:compileAndroidMain` — BUILD SUCCESSFUL; jvmTest 303 tests, 0 failures, 0 errors.
- `grep -rn "ProcessBuilder" shared/core/src/commonMain/` — no matches.
- `grep -rn "MediaMuxer\|MediaExtractor\|AVFoundation" shared/core/src/commonMain/` — no matches.

Test names: `emptyCapabilitiesAreTheDefault`, `unavailableToolkitReportsEmptyCapabilities`, `unavailableToolkitMergeFailsTyped`, `unavailableToolkitExtractAudioFailsTyped`, `missingToolAndIncompatibleStreamsAreNotRetryableButIoIs`, `hostAdapterCanImplementTheContract`.

Design note: `merge`/`extractAudio` are `suspend` so host adapters can stop work on coroutine cancellation (T-076 kills the process and deletes temps). The caller creates the destination temp, passes its `MediaFilePath`, and publishes it; implementations delete a partial destination before throwing. The engine does not call the toolkit yet (T-077).
