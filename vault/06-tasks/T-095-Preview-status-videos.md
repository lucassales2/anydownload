---
id: T-095
type: task
priority: P0
milestone: D7
tags: [task, ui, preview, twitter]
---

# T-095 — Preview lists the status videos and the user selects them

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 7](../00-project/Phase-7-X-Twitter.md) · [T-094](T-094-Twitter-status-extractor.md)

## Outcome

A status preview shows one selectable row per video, labelled from the extracted media and grouped by stable media id. The first row is preselected. Photos are never rows. The existing Edit panel still chooses quality. The preview performs no media request.

## Dependencies

- [T-094](T-094-Twitter-status-extractor.md) — the extractor and the grouped media model.

## Context the next session needs

`ExtractorMediaPreviewSource` maps `InfoDict` to `MediaPreview`. The Spotify preview already renders a read-only entry list; this task adds a selectable list for videos but changes no Spotify behaviour. The selection is request state that T-096 turns into `DownloadRequest.selectedMediaIds`; until then the callback payload is the observable result.

## Work

- `MediaPreview` gains `videos: List<MediaPreviewVideo> = emptyList()` (port-only) with `MediaPreviewVideo(mediaId, title, durationSeconds, thumbnailUrl)`. `ExtractorMediaPreviewSource` fills it in extraction order from `InfoDict.media`, and the preview thumbnail falls back to the first video's thumbnail when the status has none of its own.
- `PreviewScreen`: render a `preview-videos` section with one checkable row per video (`preview-video-<mediaId>`, toggle `select-video-<mediaId>`). The first video starts selected. The Download action carries the selected media ids; for a preview without videos it passes an empty list and behaves exactly as today. Keep Download unavailable while a status has videos and none is selected (the engine also refuses an empty selection in T-096).
- Rows show the media title (or `Video <n>` when the extractor gave no title), duration, and keep the list compact. A photo-only status never reaches this state: T-094 fails typed.
- `PreviewEditPanel` is unchanged in shape: it keeps reading `availableFormats`, which now unions the listed videos' formats. One quality and container choice applies to every selected video; the selector's existing fallback handles a video that lacks the exact height.
- Add the new strings to `shared/ui/src/commonMain/composeResources/values/strings.xml` and `values-pt-rBR/strings.xml`.

## Tests

- Core: `ExtractorMediaPreviewSourceTest` gains a case where `InfoDict.media` becomes `videos` in order with ids, durations, and thumbnails, and a preview with no media keeps `videos` empty.
- UI: a `PreviewScreen` test with two videos shows two rows, the first preselected, toggling the second changes the id list, Download passes exactly the selected ids, and no media URL is requested (fake transfer/source). A photo-only preview has no `preview-videos` section.
- UI: the Edit panel still shows the quality choices for a status preview and does not change with the selection.

## Acceptance criteria

- [x] A status preview lists only videos, one row per stable media id, first preselected.
- [x] Toggling selection changes the ids the Download action carries; a status with no selection cannot submit.
- [x] Photos never appear as rows.
- [x] The Edit panel still chooses quality; no new Edit controls and no per-row quality UI.
- [x] Preview issues no media request; `:shared:core:jvmTest` and `:shared:ui:jvmTest` pass.

## Evidence / notes

Done 2026-09-25.

What landed:

- `MediaPreview` gains the port-only `videos: List<MediaPreviewVideo>` and the `MediaPreviewVideo(mediaId, title, durationSeconds, thumbnailUrl)` model. `ExtractorMediaPreviewSource` maps `InfoDict.media` in extraction order and falls back to the first video's thumbnail when the status has none of its own.
- `PreviewScreen` renders a `preview-videos` section with one row per video (`preview-video-<mediaId>`, checkbox `select-video-<mediaId>`), preselects the first video, and disables Download while a status has videos and none is selected. The `onDownload` callback now carries the selected ids; `App.kt` passes them through (T-096 turns them into the request). A preview without videos renders no section and keeps the previous behaviour.
- The Edit panel is unchanged: it keeps reading `availableFormats` (which unions the listed media's formats since T-094), and one quality choice applies to every selected video.
- Strings `preview_videos` and `preview_video_fallback` added to `values` and `values-pt-rBR`. The fallback label is `Video <n>` when the extractor gave no title.

Verification run 2026-09-25:

- `./gradlew :shared:core:jvmTest --tests "com.anydownlod.core.ExtractorMediaPreviewSourceTest" :shared:ui:jvmTest --tests "com.anydownlod.ui.preview.StatusVideoSelectionUiTest"` — core 7, UI 3 (later 4) tests, 0 failures.
- `./gradlew :shared:core:jvmTest :shared:ui:jvmTest` — core 476 tests / 0 failures, UI 89 tests / 0 failures.
- `./gradlew :shared:core:iosSimulatorArm64Test :shared:ui:iosSimulatorArm64Test` — core 439 / 0, UI 4 / 0.
- Tests: `ExtractorMediaPreviewSourceTest.mediaBecomesSelectableVideosInExtractionOrder` (order, ids, durations, thumbnail fallback, `transfer.requests.isEmpty()`), `StatusVideoSelectionUiTest.theFirstVideoIsPreselectedAndDownloadCarriesTheSelection` (preselection, toggle, exact id lists, Download disabled with none selected), `theEditPanelKeepsChoosingQualityWhileVideosToggle`, `aPreviewWithoutVideosHasNoSelectionSection`, `aPhotoOnlyFailureHasNoVideoRows`.
- No media request during any preview test; no media URL, token, or cookie written anywhere. Spotify and YouTube preview tests are unchanged and green.
