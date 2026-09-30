---
id: T-015
type: task
priority: P1
milestone: D13
tags: [task, media, parity]
---

# T-015 — Captions, thumbnails and metadata

[Home](../Home.md) · [Kanban](../Kanban.md) · [Parity](../01-product/Feature-parity.md)

## Outcome

Standalone captions/artwork and format-appropriate embedding/sidecars for F-14/F-15.

## Dependencies

- [T-013](T-013-Media-formats.md).
- [T-014](T-014-Storage-and-delivery.md).
- [T-124](T-124-Youtube-done.md). Subtitle and chapter fields come from D12. Phase: [Phase 13](../00-project/Phase-13-Postprocessors.md). The 10 engineer-days are shared with T-016.

## Acceptance criteria

- [x] Support caption language and manual/auto/fallback preferences, SRT/TXT/VTT/TTML with truthful conversion/fallback reporting.
- [x] Support JPG thumbnails, appropriate audio artwork/metadata, optional subtitle embedding and media/feed info/thumbnail sidecars.
- [x] Register/deliver every output artifact and document WAV/container and missing-caption/artwork limitations.
- [x] Test Unicode languages/metadata, unavailable tracks, playlist/channel sidecars, postprocessing failure and playable/probe-verified outputs.

## Evidence / notes

Not started. Scheduled 2026-09-29 as D13 in [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md). This task does not look up Spotify or any other music catalog. Embedding title, artists, album, and artwork for a Spotify match is [T-037](T-037-Spotify-youtube-match.md), which is already done. Done 2026-09-30.

**Iteration 1 — caption conversion and selection (2026-09-30).** `CaptionConverter` (pure Kotlin, no FFmpeg) parses `json3`, `srv1/2/3`, `ttml`, `vtt`, and `srt` bodies into cues and renders SRT, VTT, TTML, and TXT; malformed input returns null instead of throwing, and Unicode text and line breaks are covered. `CaptionSelector` applies the language field (exact code or prefix, e.g. `en` matches `en-US`) and the manual/automatic/either preference, returning the chosen track and a fallback flag when the preferred pool or the requested language is missing.

Commands:
- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.postprocess.Caption*"` — 16 tests, 0 failures.
- `:shared:core` wasm/iOS main+test compiles — clean.

Next: engine caption sidecars (fetch, convert, publish as `ArtifactKind.CAPTIONS`), JPG thumbnail sidecars, metadata embedding through the toolkit, optional subtitle embedding, and the E-17–E-20 host table.

**Iteration 2 — engine sidecars and metadata (2026-09-30).** The engine tracks the last `InfoDict` per job and, after a completed media download, publishes a caption sidecar (`ArtifactKind.CAPTIONS`, `<name>.<lang>.<ext>`) chosen by `CaptionSelector` and converted by `CaptionConverter` from the best available track format; a thumbnail sidecar (`ArtifactKind.THUMBNAIL`, largest thumbnail, actual jpg/png/webp extension); and, for audio artifacts with `writeMetadata`, tags built from the info dict (title, uploader/channel, year) embedded through the toolkit when `canEmbedTags`, with artwork when `canEmbedArtwork`. A missing track or failed sidecar never fails the completed media. The selector now returns null for a requested language with no manual or automatic match instead of silently choosing another language; the fallback flag only covers manual/automatic kind preference.

Commands:
- `./gradlew --no-parallel :shared:core:jvmTest --rerun-tasks` — 669 tests, 0 failures.
- `:shared:core` wasm/iOS main+test compiles — clean.

Next: optional subtitle embedding (a toolkit capability plus desktop FFmpeg), the E-17–E-20 host table, WAV/container and missing-artwork limitation notes, and the final T-015 verification.

**Iteration 3 — subtitle embedding, info sidecar, verification (2026-09-30).** `ToolkitCapabilities.canEmbedSubtitles` and `MediaToolkit.embedSubtitles` were added (default typed failure); desktop FFmpeg muxes SRT with `mov_text` for MP4/MOV/M4A and `srt` for MKV/WebM, keeps the media streams, and replaces the file atomically. The engine embeds instead of writing a sidecar when the host can, and falls back to the sidecar otherwise. `writeMetadata` also writes a `<name>.info.json` sidecar (`ArtifactKind.METADATA`) from the info dict. Thumbnail sidecars prefer a JPG/JPEG URL when one exists and otherwise keep the largest image's real extension. E-17, E-18, and E-19 now record the host table.

Commands (sequential; the fixture suites are timing-sensitive when Gradle runs projects in parallel):
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 671, ui 105, desktop 143 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs`, `:shared:ui:compileKotlinIosSimulatorArm64` — build clean.

Limits recorded: WAV has no standard lyrics tag (desktop skips lyrics there); Android and iOS do not write audio tags, artwork, or subtitle tracks in this phase and use sidecars instead; web writes no files at all (its engine fails a required merge/embed/clip typed); a requested caption language with no manual or automatic match writes nothing; server-side JPG conversion is out (the sidecar keeps the source image extension); clip ranges, chapter split, and SponsorBlock are T-016.
