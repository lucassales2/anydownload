---
id: T-107
type: task
priority: P0
milestone: D8
tags: [task, engine, playlists]
---

# T-107 — Playlist entries become child jobs

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 8](../00-project/Phase-8-On-device-core.md) · [ADR-012](../03-decisions/ADR-012-On-device-core-phase.md)

## Outcome

When an extractor returns entries, the engine creates bounded child jobs and does not download the parent's media. Single videos stay a single job.

## Dependencies

- [T-106](T-106-Four-host-m1.md).

## Context the next session needs

`InfoDict` has `formats` and `media`, and no entries list. `DownloadRequest.parentBatchId` already groups Spotify children. `playlistItemLimit` of 0 currently means "no extra cap" on `DownloadOptions`. ADR-012 changes 0 to the hard cap of 50 for playlist expansion. Spotify's own expansion is unchanged.

## Work

- Add an entries list on `InfoDict`: id, title, URL, and an optional typed failure for that entry. Empty entries means "not a playlist".
- A playlist result creates a parent job that finishes `COMPLETED` once expansion finishes, with no media artifact, plus one child per entry. Children share the parent's `parentBatchId` and copy the parent's download options. Each child has its own idempotency key derived from the parent and the entry id.
- Cap: if `playlistItemLimit` is positive, take `min(limit, 50)`. If it is 0, take 50. Do not fetch further pages once the cap is reached.
- An entry with a typed failure becomes a `FAILED` child and does not abort the rest. Duplicate entry ids inside one parent are skipped after the first.
- Cancel during expansion stops further entries. Children already created stay in their current state.
- A non-playlist `InfoDict` does not create children. X status selection and Spotify expansion stay on their existing paths.

## Tests

- Three entries and a limit of 2 produce two children and no third request.
- Limit 0 produces at most 50.
- One unavailable entry and one good entry produce one failed child and one queued child.
- Two entries with the same id produce one child.
- Cancel after the first entry leaves that child and creates no more.
- A single-video info dict still downloads one file and creates no child.

## Acceptance criteria

- [x] Entries expand into child jobs under the cap, and the parent does not download media.
- [x] Limit 0 means 50, covered by a test.
- [x] Unavailable and duplicate entries follow the rules above.
- [x] Cancel mid-expansion keeps the children already created.
- [x] Existing single-video, X, and Spotify tests stay green.

## Evidence / notes

2026-09-29.

- `InfoDict` gained `InfoEntry(id, title, url, failure)` and `entries: List<InfoEntry>`. `failure` is `@Transient` (an `ExtractionError` is an in-process signal, not a wire field). Empty `entries` keeps the existing non-playlist behavior.
- `HttpDownloadEngine.expandEntries` (called from `extractAndDownload` before the media/X paths): the parent job downloads no media and finishes `COMPLETED` with no artifact after expansion; each entry becomes a child sharing the parent's batch id and options with idempotency key `parentKey:entry:entryId`; the cap is `min(playlistItemLimit, 50)` when positive and 50 when 0 (constant `HttpDownloadEngine.PLAYLIST_ITEM_CAP`); a typed entry failure becomes a `FAILED` child and the rest continue; duplicate entry ids are skipped after the first; a cancel between entries stops the loop and leaves created children alone; a `yield()` between entries lets real cancellation land. Spotify and X-status expansion are untouched (their own paths run before/after this check and `entries` stays empty for them).
- New tests in `shared/core/src/commonTest/kotlin/com/anydownlod/core/engine/EnginePlaylistExpansionTest.kt`: limit 2 of 3 entries creates two children and makes only two media requests; limit 0 creates exactly 50; one unavailable entry plus one good entry yields one `FAILED` child and one `COMPLETED` child; duplicate ids make one child; cancel during expansion keeps the first child and creates no more; a single-video `InfoDict` downloads one file and creates no child.

Verification run:

- `./gradlew :shared:core:jvmTest --tests "com.anydownlod.core.engine.EnginePlaylistExpansionTest"` — BUILD SUCCESSFUL; 6 tests, 0 failures.
- Cross-target regression: `CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew :shared:core:jvmTest :shared:core:wasmJsBrowserTest :shared:core:iosSimulatorArm64Test :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :apps:web:wasmJsBrowserTest :apps:android:compileDebugKotlin` — core jvm 508, core wasm 460, core iOS 471, ui jvm 91, android-engine-tests 26, web wasm 6, all 0 failures (the single-video, X, and Spotify suites included). The desktop suite showed two FFmpeg `ToolkitError.Io: unreadable output` failures only in the heavy parallel run; `:apps:desktop:cleanTest :apps:desktop:test` alone passed 131/131 with 0 failures, matching the load-related flake seen in T-104/T-105, unrelated to this change.
