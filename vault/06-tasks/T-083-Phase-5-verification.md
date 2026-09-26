---
id: T-083
type: task
priority: P0
milestone: D5
tags: [task, verification]
---

# T-083 — Phase 5 verification

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 5](../00-project/Phase-5-Media-Toolkit.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

Phase D5 is shown on all four families: merge on desktop, Android, and iOS, and a recorded gap on web. This is one video stream plus one audio stream, plus the audio containers each host can write. It is not captions, clips, or X/Twitter.

## Dependencies

- [T-079](T-079-Gate-desktop-merge.md) through [T-082](T-082-Audio-containers.md).

## Work

1. Desktop: local split-stream fixture merges to one probed file; audio fixture becomes MP3, WAV, and FLAC when `ffmpeg` is present; a missing binary fails typed. Opt-in live YouTube merge only if T-079 did not already record it.
2. Android: the same fixture through MediaMuxer, or the recorded JVM-equivalent plus APK assemble if no emulator exists. State which.
3. iOS: simulator fixture through AVFoundation. No live YouTube. Foreground-only stays documented.
4. Web: merge and transcode choices stay disabled; the page still does not fetch origins and does not run a toolkit.
5. Shared tests from T-074's command list stay green, plus the new toolkit tests.
6. README status moves to D5 with the four-host capability table and the honest limits. Update Roadmap, Home, the decision log, and the Phase 5 note status.
7. Equivalence: E-05 and E-17 become Partial with the host split. [T-013](T-013-Media-formats.md) stays open.

## Acceptance criteria

- [x] Four-host table with pass/fail/gap, the tool used, and whether merge and each audio container work.
- [x] No FFmpeg binary, cookie, private URL, or media file added to the repository.
- [x] Web gap is explicit in the README. Mobile MP3/FLAC stay disabled unless a task proved an encoder.
- [x] E-05 and E-17 updated. T-013 still open.

## Evidence / notes

Done 2026-09-25. Public fixtures and the public oracle video only; no URL, cookie, media file, or binary is recorded or added.

Four-host capability matrix:

| Host | Tool | Merge | M4A | Opus | MP3 | WAV | FLAC | Result |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Desktop | `ffmpeg`/`ffprobe` on `PATH` | Pass (stream copy to MP4) | Pass (copy) | Pass (copy) | Pass (transcode, ffprobe) | Pass (transcode, ffprobe) | Pass (transcode, ffprobe) | **Pass** |
| Android | `MediaMuxer`/`MediaExtractor` | Pass (JVM-equivalent) | Pass (copy) | Pass (WebM copy) | Disabled | Disabled | Disabled | **Pass (JVM-equivalent)** |
| iOS | `AVFoundation` | Pass (simulator) | Pass (copy) | Copy to CAF; no Ogg writer | Disabled | Disabled | Disabled | **Pass (simulator)** |
| Web | none | Disabled, host gap | Native single-file stream only | Native single-file stream only | Disabled, host gap | Disabled, host gap | Disabled, host gap | **Pass (gap recorded)** |

Evidence per host:

- Desktop: `DesktopEngineMergeTest` merges a local split pair through the engine + `DesktopFfmpegToolkit` (one H.264 video, one AAC audio, no temp left); `DesktopFfmpegToolkitTest.extractAudioProducesMp3WavAndFlac` and `DesktopEngineMergeTest.engineExtractsAudioToMp3ThroughTheDesktopToolkit` produce the containers; `missingFfmpegFailsTypedAndStartsNoProcess` proves the typed missing-tool path. Opt-in live merge (T-079): `state=COMPLETED artifacts=1 video=1 audio=1`, the `merging` phase was observed, and the fake CLI runner saw no process.
- Android: `AndroidMediaToolkitTest` (fake port: merge, M4A copy, typed rejection/cleanup, capabilities omit MP3/WAV/FLAC), `:apps:android:assembleDebug`, and a compiled instrumented `MediaMuxer`/`MediaExtractor` test. No emulator/AVD exists here, so the real muxer path is compiled but not executed.
- iOS: `IosMediaToolkitTest` on the simulator (merge with one video + one audio track, M4A copy, typed rejection with no file, capabilities omit MP3/WAV/FLAC). No live YouTube, no subprocess.
- Web: `PreviewFromExtractorUiTest` keeps the split-only 1080 quality disabled with the host-gap reason; the node check `the web page ships no process or platform muxer` scans the web page sources; the extension tests pass.

Commands run (all green):

- `./gradlew :shared:core:jvmTest :shared:core:iosSimulatorArm64Test :shared:ui:jvmTest :shared:ui:iosSimulatorArm64Test :apps:desktop:test :apps:android-engine-tests:test :apps:android:assembleDebug :apps:android:compileDebugAndroidTestKotlin :tools:port-manifest:check :apps:web:wasmJsBrowserDistribution` — BUILD SUCCESSFUL.
- Test counts: core jvm 313; core iOS 295; core wasm (Brave `CHROME_BIN`) 284; ui jvm 82; ui iOS 4; desktop 123 (14 opt-in skipped); android-engine-tests 20; extension node 23.
- Opt-in live merge: `./gradlew :apps:desktop:test -PliveExtractorTests=true --tests "com.anydownlod.desktop.engine.DesktopLiveKotlinMergeTest"` — passed.
- Hygiene: no media/binary file added (`find` scan), no `googlevideo` outside the redaction harness, no cookie file added.

Docs updated: README status to D5 with the capability table and limits; Roadmap and Home point to D6; Decision log records ADR-009 accepted; [ADR-009](../03-decisions/ADR-009-Media-toolkit-phase.md) is `accepted`; [Phase 5](../00-project/Phase-5-Media-Toolkit.md) is `done` with the verified matrix; [E-05 and E-17](../01-product/Ytdlp-equivalence.md) are Partial with the host split; the [Feature-parity](../01-product/Feature-parity.md) intro records the D5 gain. [T-013](T-013-Media-formats.md) and [T-022](T-022-Parity-audit.md) stay open.
