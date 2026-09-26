---
id: T-096
type: task
priority: P0
milestone: D7
tags: [task, engine, gate, twitter]
---

# T-096 — Gate: selected status videos download as separate files

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 7](../00-project/Phase-7-X-Twitter.md) · [ADR-011](../03-decisions/ADR-011-X-twitter-phase.md) · [T-094](T-094-Twitter-status-extractor.md) · [T-095](T-095-Preview-status-videos.md)

## Outcome

A status preview's selection becomes a real download. `DownloadRequest.selectedMediaIds` carries the chosen stable media ids; the engine re-extracts the status at download time and writes one file per selected video inside the download root. An empty selection writes nothing. The job's source URL is the status URL. On desktop this works from a redacted fixture, with an opt-in live public status when a URL is configured; no `yt-dlp` process runs for a matched status URL. This is the D7 gate.

## Dependencies

- [T-094](T-094-Twitter-status-extractor.md) and [T-095](T-095-Preview-status-videos.md).

## Context the next session needs

`HttpDownloadEngine.extractAndDownload` resolves one format and publishes one artifact. `DownloadJob.artifacts` is already a list, so one status job can own several files. `WebExtensionEngine` has its own extraction path; leave it to T-099. The desktop registry in `apps/desktop/src/jvmMain/kotlin/com/anydownlod/desktop/Main.kt` and `DesktopPreviewSource.kt` currently lists only `YoutubeIE`; add `TwitterIE` here so the desktop route becomes KOTLIN before any probe or process.

## Work

- Domain: `DownloadRequest` gains `selectedMediaIds: List<String> = emptyList()`. It is never logged. `AddFormPresenter.submit` accepts the ids and puts them on every request it creates; `App.kt` passes the list from `PreviewScreen`. Non-status previews pass an empty list and behave unchanged.
- Engine: after extraction, when `info.media` is not empty, the request must use the media path:
  - Empty selection → fail typed (`INVALID_URL_OPTIONS`) with a redacted "select at least one video" message, before any media request, and write no file.
  - Resolve every selected id against the fresh extraction first. A selected id that is gone (deleted, protected, edited) → fail typed (`UNAVAILABLE_OR_PRIVATE`) before any media GET, so a stale selection never half-downloads.
  - For each selected media in extraction order: select one format from its formats with the compiled spec and the existing sorter, download it through the existing direct-file or manifest path, and publish an artifact on the same job. Titles come from the media title or the status title, with `#n` when the status has more than one selected video; the existing `ArtifactName` sanitization and collision policy stand. A mid-way failure keeps files already published as artifacts and fails the job typed; the failed temp is discarded.
  - Re-extraction is required: no media URL or format from the preview is reused. The job's `request.sourceUrl` stays the status URL.
- Desktop: register `TwitterIE` in both desktop registries. Add a fixture gate test and an opt-in live test.
- Do not change `WebExtensionEngine` here; T-099 mirrors the media path over the extension.

## Tests

- Engine (JVM, fake extractor and transfer): two selected videos produce two artifacts with byte-exact files inside the root; an unselected video's URL is never fetched; the extraction runs again at download time (request count); the job source URL is the status URL; the artifacts belong to that job.
- Engine: an empty selection with media present fails typed and performs no media GET; a selected id missing from the fresh extraction fails typed before any media GET.
- Engine: a three-video status with two selected downloads exactly two files; cancellation discards the current temp.
- Desktop gate: `DesktopXStatusGateTest` previews a fixture status, selects both videos, downloads into a temp root, and finds two files; the route is KOTLIN and zero CLI process starts occur.
- Desktop live (opt-in): `DesktopXStatusLiveTest` runs only with `-PxStatusUrl=<public status>`; it downloads one selected video to a temp folder, reports what it saw, and is skipped and reported otherwise. It never runs in default CI and never commits the URL or media.

## Acceptance criteria

- [x] `DownloadRequest.selectedMediaIds` exists and is wired from the preview through `AddFormPresenter` to the engine.
- [x] Two selected videos download as exactly two files inside the download root; an unselected video is never fetched.
- [x] An empty selection downloads nothing and fails typed with a redacted message.
- [x] The engine re-extracts at download time and never reuses preview media URLs.
- [x] The job source URL is the status URL and the artifacts hang off that job.
- [x] Desktop fixture gate passes; the opt-in live test exists, is skipped by default, and is out of default CI.
- [x] No `yt-dlp` process starts for a matched status URL; existing engine tests are unchanged and green.

## Evidence / notes

Done 2026-09-25.

What landed:

- `DownloadRequest.selectedMediaIds` (never logged) and `AddFormPresenter.submit(selectedMediaIds)` put the preview selection on the request; `App.kt` passes the list from `PreviewScreen`. Batch and non-status submissions pass an empty list and behave unchanged.
- `HttpDownloadEngine.downloadSelectedMedia`: when the fresh `InfoDict.media` is non-empty, an empty selection fails `INVALID_URL_OPTIONS` before any media request; every selected id is resolved against the fresh extraction first (a missing id fails `UNAVAILABLE_OR_PRIVATE` before any media GET); each selected media selects one format from its own formats with the compiled spec and sorter, downloads through the existing direct-file/manifest path, and is published as an artifact on the same job. Titles come from the media title or the status title with `#n` when several are selected. A mid-way failure keeps the files already published and fails typed; cancellation discards the current temp. The job's source URL stays the status URL. `WebExtensionEngine` is untouched (T-099).
- Desktop: `TwitterIE` registered in `apps/desktop/.../Main.kt` and `DesktopPreviewSource.kt`, so a matched status routes to KOTLIN before any probe or process.
- `apps/desktop/build.gradle.kts` passes `-PxStatusUrl` to the test JVM for the opt-in live test.

Verification run 2026-09-25 (all green):

- `./gradlew :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :shared:core:iosSimulatorArm64Test :apps:android-engine-tests:test :shared:core:compileTestKotlinWasmJs :tools:port-manifest:check` — BUILD SUCCESSFUL.
- Counts: core JVM 481 / 0, UI JVM 90 / 0, desktop 130 / 0 with 15 opt-in skips, core iOS 444 / 0, android-engine 20 / 0; wasm test compile and port-manifest check green.
- `EngineExtractionTest` (13): `twoSelectedOfThreeVideosDownloadTwoFilesFromAFreshExtraction` (two files, unselected URL never fetched, `extractor.calls == 1`, status URL preserved, artifacts on the job), `anEmptySelectionFailsTypedAndNeverFetchesMedia`, `aSelectedIdGoneFromTheFreshExtractionFailsBeforeAnyMediaGet`, `cancellingBetweenSelectedVideosDiscardsTheCurrentTemp`, `aMidWayFailureKeepsPublishedFilesAndFailsTyped`.
- `DesktopXStatusGateTest` (1/0): previews the fixture status, selects both videos, downloads two files with the fixture bytes into a temp root, asserts the source URL, preview + download each did one guest lookup, and the fake CLI factory was never invoked.
- `StatusVideoSelectionUiTest.thePreviewSelectionReachesTheEngineRequest` (1): typing the status URL, toggling the second video, and pressing Download yields an engine request with `selectedMediaIds = [first, second]`.
- `DesktopXStatusLiveTest` (1 test, 1 skipped by default): runs only with `-PxStatusUrl=<public status>`; no URL was configured here, so it was skipped and reported. It never runs in default CI and prints only counts, never a URL or media address.
- Hygiene: no media file, real status, media URL, guest token, or cookie added; the gate fixtures are synthesized on `*.example` and localhost only.
