---
id: T-063
type: task
priority: P0
milestone: D4
tags: [task, ui, engine]
---

# T-063 — Preview and Edit driven by the Kotlin extractor

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [T-053](T-053-Validate-then-preview.md) · [T-054](T-054-Download-and-edit.md)

## Outcome

On every host, a matched link opens the metadata preview from the shared extractor: real title, channel, duration, thumbnail, upload date. The Edit panel lists only qualities and containers the extracted formats can satisfy as a single file, and says why the rest are unavailable.

## Dependencies

- [T-061](T-061-Engine-extracts-selects-downloads.md).

## Context the next session needs

`MediaPreviewSource` exists; desktop uses `YtDlpMediaPreviewSource` (CLI) and others use `UnavailableMediaPreviewSource`. The preview must not start a download. The Edit panel today shows static choices from `PreviewFormat.kt`.

## Work

- `ExtractorMediaPreviewSource(registry, http)` in `shared/core`: bounded, cancellable, returns `MediaPreviewResult.Ready` with `MediaPreview` plus a new `availableFormats: FormatChoices` (resolutions present as single-file video, audio containers present natively, count hidden for lack of a JS runtime). Unmatched URL → `Failed(Unavailable)` so hosts can fall through to their existing source.
- `CompositeMediaPreviewSource(first = extractor, then = host source)`: desktop keeps CLI preview for unmatched URLs.
- Edit panel: quality list from `FormatChoices.videoHeights`; audio containers enabled only when present natively; MP3/WAV/FLAC and heights above the best single-file format shown disabled with the text "Needs the media toolkit (not built yet)". Defaults: best single-file video; best native audio.
- Preview shows "N more formats need the JavaScript runtime" when the count is non-zero (stage 1 hosts).
- Tests in `shared/ui`: preview renders extractor fields; disabled entries carry the reason; choosing a disabled entry is impossible; composite falls through.

## Acceptance criteria

- [x] `:shared:ui:jvmTest` covers the cases above and passes.
- [x] Desktop click-through: a YouTube link previews via Kotlin without spawning `yt-dlp`; an unmatched site URL still previews via the CLI.
- [x] The preview never issues the media download request (asserted with a fake transfer).
- [x] Thumbnail URLs are not logged.

## Evidence / notes

Done 2026-09-24.

- Core: `FormatChoices` (single-file video heights, native audio containers, formats needing JS), `MediaPreview.availableFormats` (null for CLI/legacy sources so the panel keeps its static choices), `ExtractorMediaPreviewSource` (bounded by `withTimeout`, cancellable, unmatched → `Unavailable`, matched-but-failed → `Failed`), and `CompositeMediaPreviewSource` (falls through only on `Unavailable`). `ExtractorRegistry` now permits a registry without a generic extractor, so a host can route unmatched URLs itself; the generic-must-be-last rule still holds when one is present.
- UI: `SegmentedChoice`/`ChoiceRow` gained `optionEnabled`/`optionReason` and render the distinct disabled reasons under the row; `PreviewEditPanel` enables only heights/containers present as single files, disables MP3/WAV/FLAC and heights above the best single-file format with “Needs the media toolkit (not built yet)”, uses “Not available from this source” for other missing heights, and defaults to native audio when only split streams exist; `PreviewScreen` passes the choices through and shows “N more formats need the JavaScript runtime”. New strings in `values` and `values-pt-rBR`.
- Desktop: `DesktopPreviewSource.create` builds `CompositeMediaPreviewSource(ExtractorMediaPreviewSource(kotlin registry), YtDlpMediaPreviewSource)` and `Main.kt` uses it, so a matched YouTube URL previews from Kotlin and an unmatched URL uses the installed CLI as before.
- Tests: core `ExtractorMediaPreviewSourceTest` 6 (ready fields/choices, unmatched vs matched-failure, composite fall-through, choices derivation, registry without generic); UI `PreviewEditPanelTest` 4 (enabled/disabled heights with the reasons and no state move on a disabled click, toolkit containers disabled, audio-only default, static choices when no formats); UI click-through `PreviewFromExtractorUiTest` 1 (type a YouTube URL → preview shows the Kotlin title/channel, no job, no media request, Edit shows 360 enabled / 1080 disabled with the reason and the JS count); desktop `DesktopPreviewSourceTest` 3 (YouTube via Kotlin with zero process starts, unmatched falls through to the CLI, matched failure never reaches the CLI).
- Thumbnail URLs are not logged: no preview path prints or logs metadata; the only thumbnail handling fetches bytes for display.
- Verification: `./gradlew :shared:core:jvmTest` → 257 tests, 0 failures; `:shared:ui:jvmTest` → 76 tests, 0 failures; `:apps:desktop:test` and `:apps:android-engine-tests:test` green; `:shared:core:iosSimulatorArm64Test` green; iOS/wasm-test/Android/web compiles green; `:tools:port-manifest:check` green. No manifest change.
