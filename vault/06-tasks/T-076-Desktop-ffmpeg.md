---
id: T-076
type: task
priority: P0
milestone: D5
tags: [task, engine, desktop, toolkit]
---

# T-076 — Desktop FFmpeg adapter

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 5](../00-project/Phase-5-Media-Toolkit.md)

## Outcome

Desktop can merge two local media files into one, and can copy an audio stream out, by running `ffmpeg` and `ffprobe` from `PATH`. A missing binary fails typed. Cancel kills the process and deletes temps. The binary is not vendored.

## Dependencies

- [T-075](T-075-Media-toolkit-contract.md) — the contract this adapter implements.

## Context the next session needs

`apps/desktop` already probes `ffmpeg` on `PATH` (`ToolProbe`) and is the only module allowed to use `ProcessBuilder`. Build arguments as a list. Never concatenate a shell command. Do not log the raw process stderr; keep a short sanitized reason. Stream copy only in this task (`-c copy`). Transcode to MP3/WAV/FLAC is T-082.

## Work

- `DesktopFfmpegToolkit` implements `MediaToolkit`. `capabilities()` reports `canMerge = true` when `ffmpeg` and `ffprobe` resolve, and audio containers limited to M4A and Opus for this task.
- Merge and extract use an argument list: stream copy, no re-encode. `ffprobe` checks the output has the expected container and that both streams survived a merge (one video, one audio).
- Cancel and failure delete the destination and any temp. A missing binary returns `ToolkitError` mapped to `UNSUPPORTED_FORMAT`.
- Tests use a local synthetic fixture (two elementary streams or a tiny generated pair). Skip the test with a recorded reason when `ffmpeg` is absent; do not download a binary. No YouTube URL.

## Acceptance criteria

- [x] A local split pair becomes one file whose probed streams match the inputs.
- [x] A missing `ffmpeg` fails typed and starts no process.
- [x] Cancel kills the process and leaves no output file.
- [x] No FFmpeg binary is added to the repo. Arguments are a list.

## Evidence / notes

Done on 2026-09-25.

Added:

- `apps/desktop/src/jvmMain/kotlin/com/anydownlod/desktop/engine/DesktopFfmpegToolkit.kt` — `MediaToolkit` over `ffmpeg`/`ffprobe` resolved from `PATH` (nothing bundled). `capabilities()` is `canMerge = true` with `{M4A, OPUS}` only when both binaries resolve; a missing tool fails `ToolUnavailable` before `runner.start`. Merge and extract use argument lists and `-c copy`; `ffprobe -print_format json` checks one video + one audio stream after a merge and the expected container after an extract. A nonzero exit or a bad probe is `IncompatibleStreams` and deletes the destination; a timeout or start failure is retryable `Io`. Cancel destroys the process tree and deletes the destination; tool output is drained into daemon threads and discarded, never logged or put in a message.
- `apps/desktop/src/jvmTest/kotlin/com/anydownlod/desktop/engine/DesktopFfmpegToolkitTest.kt` — 6 tests, local lavfi fixtures, no network.

Commands run (ffmpeg 9.0.1 and ffprobe 9.0.1 on PATH):

- `./gradlew :apps:desktop:test --tests "com.anydownlod.desktop.engine.DesktopFfmpegToolkitTest"` — 6 tests, 0 skipped, 0 failures.
- `./gradlew :apps:desktop:test` — 119 tests, 13 skipped (existing opt-in live/oracle tests), 0 failures, 0 errors.
- `find apps/desktop -iname "*ffmpeg*" -o -iname "*ffprobe*"` — only the two new `.kt` files; no binary added.

Test names: `mergeCopiesBothStreamsIntoOneFile`, `extractAudioCopiesM4aWithoutReencoding`, `incompatibleCopyFailsTypedAndDeletesTheDestination`, `missingFfmpegFailsTypedAndStartsNoProcess`, `capabilitiesNeedBothTools`, `cancelKillsTheProcessAndLeavesNoOutput`.

The merge fixture is a generated H.264 video-only MP4 plus an AAC audio-only M4A; the test asserts the merged file carries the same codec pair. The incompatible case copies Opus into the M4A muxer, which fails typed and leaves no file. Tests skip with `assumeTrue` when a binary is absent instead of downloading one.
