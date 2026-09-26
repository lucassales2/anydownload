---
id: T-080
type: task
priority: P0
milestone: D5
tags: [task, engine, android, toolkit]
---

# T-080 — Android MediaMuxer remux

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 5](../00-project/Phase-5-Media-Toolkit.md)

## Outcome

Android merges one video file and one audio file with `MediaMuxer` and `MediaExtractor`, and copies an M4A or Opus audio stream out. Incompatible codecs fail typed. No FFmpeg binary is added.

## Dependencies

- [T-079](T-079-Gate-desktop-merge.md) — desktop merge is proven before a second host adapter.

## Context the next session needs

The adapter lives under `apps/android`, not in `shared/core`. `MediaMuxer` remuxes; it does not transcode. MP3 and FLAC stay out of `capabilities()` in this task. If the only available Android evidence is the JVM-equivalent module plus `assembleDebug`, record that, as D4 did, and keep the muxer test on an instrumented or Robolectric path that can actually construct `MediaMuxer`. Do not claim an emulator run that did not happen.

## Work

- `AndroidMediaToolkit` implements `MediaToolkit`. `canMerge = true`. Audio containers: M4A and Opus only, and only for stream copy.
- Merge writes one container. A codec the muxer rejects becomes `ToolkitError` / `UNSUPPORTED_FORMAT` and deletes the partial file.
- Extract audio copies the audio track. It does not re-encode.
- Test with a local fixture of a compatible pair and one incompatible pair. No network.

## Acceptance criteria

- [x] A compatible local pair becomes one file with a video track and an audio track.
- [x] An incompatible pair fails typed and leaves no file.
- [x] Capabilities omit MP3, WAV, and FLAC.
- [x] No FFmpeg binary or `ProcessBuilder` is added under `apps/android`.

## Evidence / notes

Done on 2026-09-25. No emulator or device exists in this environment, so the real `MediaMuxer` path is compiled and the instrumented test is written but not run; the JVM-equivalent module is the executed evidence, as D4 recorded.

Added:

- `apps/android/.../engine/media/MediaMuxerPort.kt` — pure-Kotlin port over the platform muxer (`merge`, `copyAudio`) plus `MuxerFailure.Incompatible`/`Io`.
- `apps/android/.../engine/media/AndroidMediaToolkit.kt` — implements `MediaToolkit`; `canMerge = true`, containers `{M4A, OPUS}`; copy-only; maps failures to `ToolkitError.IncompatibleStreams`/`Io` and deletes the destination on every failure; unsupported containers fail typed before the port is called.
- `apps/android/.../media/AndroidPlatformMuxer.kt` — real `MediaExtractor` + `MediaMuxer` track copy (no encoder, no subprocess). Merge writes MP4; M4A copies to MP4 and Opus to WebM. A rejected codec becomes `MuxerFailure.Incompatible`.
- `apps/android/src/test/.../engine/media/AndroidMediaToolkitTest.kt` — 6 JVM-equivalent tests with a fake port: capabilities, merge success (one file, two tracks), incompatible/IO cleanup, M4A copy, and MP3 rejection.
- `apps/android/src/androidTest/.../media/MediaFixtures.kt` and `AndroidPlatformMuxerInstrumentedTest.kt` — on-device fixtures encoded with `MediaCodec` and the real muxer test (compatible pair, incompatible pair, capabilities). Compiles; not run without a device.
- `AndroidAppGraph` wires the toolkit into `HttpDownloadEngine` and exposes `capabilities()`.
- `gradle/libs.versions.toml` / `apps/android/build.gradle.kts` — `androidx.test.ext:junit` and `androidx.test:runner` for the instrumented source set.

Commands run:

- `./gradlew :apps:android-engine-tests:test` — 20 tests, 0 failures (was 14).
- `./gradlew :apps:android:assembleDebug` — BUILD SUCCESSFUL.
- `./gradlew :apps:android:compileDebugAndroidTestKotlin` — BUILD SUCCESSFUL (instrumented test compiles).
- `./gradlew :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test` — BUILD SUCCESSFUL (311 + 81 + 121/14 skipped + 20 tests).
- `grep -rn "ProcessBuilder" apps/android` — no matches. `find apps/android -iname "*ffmpeg*"` — nothing; no binary or `.so` added.
