---
id: T-016
type: task
priority: P1
milestone: D13
tags: [task, media, parity]
---

# T-016 — Clips, chapters and SponsorBlock

[Home](../Home.md) · [Kanban](../Kanban.md) · [Parity](../01-product/Feature-parity.md)

## Outcome

F-16/F-17 advanced media output plus chapter naming/delivery aspects of F-11/F-13.

## Dependencies

- [T-013](T-013-Media-formats.md).
- [T-014](T-014-Storage-and-delivery.md).
- [T-015](T-015-Captions-thumbnails-metadata.md). Phase: [Phase 13](../00-project/Phase-13-Postprocessors.md).

## Acceptance criteria

- [x] Validate clip start/end and URL timestamp precedence; document accuracy, keyframe/transcode costs and invalid-range errors.
- [x] Split available chapters using confined templates and expose every chapter artifact with size/open/download actions.
- [x] Implement opt-in SponsorBlock behavior with clear unavailable-marker/source/error handling.
- [x] Test interactions among clipping, splitting, presets and cancellation; prevent unsafe postprocessor or template injection.

## Evidence / notes

Not started. Scheduled 2026-09-29 as D13 in [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md). Extra external service use and format limitations must be visible to users. Web clip and chapter split fail typed. Done 2026-09-30.

**Iteration 1 — clip ranges and chapter split (2026-09-30).** `ClipRangeParser` validates separate start/end fields (`HH:MM:SS.mmm`, `MM:SS`, plain seconds, `1h2m3s`), rejects invalid, negative, or reversed ranges, and resolves the URL `t=`/`start=` fallback only when both fields are blank (an explicit field wins). `ChapterTemplate` renders the app's chapter template (`%(title)s`, `%(section_number)02d`, `%(section_number)s`, `%(section_title)s`, `%(ext)s`) and confines the result to the download root, returning null for unknown fields, traversal, or an absolute path. `ToolkitCapabilities.canClip` and `MediaToolkit.clip` were added (default typed failure); desktop FFmpeg cuts with `-ss`/`-t -c copy` and probes the result. The engine applies an explicit clip in `finishTemp` (typed failure when the host cannot cut or the range is invalid) and splits chapters into `ArtifactKind.CHAPTER` files with the confined template when chapters exist; a clip and a split cannot be combined (the clip wins and the split is skipped).

Commands:
- `./gradlew --no-parallel :shared:core:jvmTest :apps:desktop:test --rerun-tasks` — core 689, desktop 144 (15 live skipped), 0 failures.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs`, `:shared:ui:compileKotlinIosSimulatorArm64` — clean.

Next: SponsorBlock opt-in with visible source/unavailable-marker handling, the E-20 host table, cancellation and interaction tests, then the final T-016 verification.

**Iteration 2 — SponsorBlock and verification (2026-09-30).** `SponsorBlockClient` queries the reviewed `sponsor.ajay.app/api/skipSegments` endpoint with the MeTube default categories and parses only `actionType=skip` rows into millisecond segments; a blank id is NotApplicable, an empty list is NoSegments, and any transport or parse failure is Unavailable. `DownloadJob.sponsorBlock` (`SponsorBlockOutcome`: removed / no-segments / unavailable) and the job document round-trip record the outcome so the external call is visible. `ToolkitCapabilities.canRemoveSegments` and `MediaToolkit.removeSegments` were added; desktop FFmpeg re-encodes with `select`/`aselect` filters. The engine runs the opt-in lookup after download, removes segments and publishes the trimmed file when the host can, keeps the media with a typed outcome otherwise, and fails typed when the host cannot remove. A clip or split cannot be combined with SponsorBlock in this phase; a clip wins over a split. Cancellation during a clip discards the temp.

Commands (sequential; the fixture suites are timing-sensitive when Gradle runs projects in parallel):
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 700, ui 105, desktop 145 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs`, `:shared:ui:compileKotlinIosSimulatorArm64` — clean.

Limits recorded: a clip is a stream copy that lands on the nearest keyframe at or before the requested start; SponsorBlock removal re-encodes on desktop and is unavailable on Android, iOS, and web in this phase; the SponsorBlock call is the only extra external service and only runs when the user opts in; presets feed the immutable per-attempt options and cannot add shell/exec or free-form fields.
