---
type: phase
status: done
milestone: D13
tags: [project, engine, media]
---

# Phase 13 — Postprocessors

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Done 2026-09-30. 10 engineer-days planned.** Depends on D12. Upstream `postprocessor/` is 101 KB at the pin, mostly `ffmpeg.py` (48 KB) and `modify_chapters.py` (17 KB).

## Done looks like

Captions can be written as SRT, VTT, TTML, and TXT. JPG thumbnails can be saved as sidecars. Thumbnail, metadata, and subtitle embedding run where the host toolkit can do them. Desktop keeps FFmpeg on `PATH`. Android MediaMuxer and iOS AVFoundation embed only what those APIs can do. MP3, WAV, and FLAC stay disabled on mobile. Clip ranges, chapter split, and opt-in SponsorBlock work on hosts that can cut. Web fails typed when a merge, embed, or clip is required. E-17, E-18, E-19, and E-20 record that host table.

## Tasks

[T-015](../06-tasks/T-015-Captions-thumbnails-metadata.md) and [T-016](../06-tasks/T-016-Clips-chapters-SponsorBlock.md).

## Verification

Landed 2026-09-30 with T-015 and T-016: caption selection and SRT/VTT/TTML/TXT conversion with sidecars on every host; thumbnail (JPG-preferred) and info.json sidecars; desktop audio tag/artwork embedding and SRT subtitle muxing; validated clip ranges with URL timestamp precedence; chapter split with confined templates and one `CHAPTER` artifact per chapter; opt-in SponsorBlock with typed outcomes and desktop segment removal. E-17, E-18, E-19, and E-20 record the host table.

Commands (sequential; the fixture suites are timing-sensitive when Gradle runs projects in parallel):

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — BUILD SUCCESSFUL; core 700, ui 105, desktop 145 (15 live-only skipped), android-engine 41, port-manifest 25; 0 failures.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs`, `:shared:ui:compileKotlinIosSimulatorArm64` — BUILD SUCCESSFUL.

Limits: Android and iOS do not embed tags, artwork, subtitles, or remove SponsorBlock segments in this phase and use sidecars or fail typed; web writes no files and fails a required merge/embed/clip typed; WAV carries no lyrics tag; a clip lands on the nearest keyframe; SponsorBlock cannot combine with a clip or chapter split yet.
