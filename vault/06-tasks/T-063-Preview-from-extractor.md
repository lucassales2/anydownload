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

- [ ] `:shared:ui:jvmTest` covers the cases above and passes.
- [ ] Desktop click-through: a YouTube link previews via Kotlin without spawning `yt-dlp`; an unmatched site URL still previews via the CLI.
- [ ] The preview never issues the media download request (asserted with a fake transfer).
- [ ] Thumbnail URLs are not logged.

## Evidence / notes

Not started.
