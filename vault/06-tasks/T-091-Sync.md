---
id: T-091
type: task
priority: P1
milestone: D6
tags: [task, engine, spotify]
---

# T-091 — Sync

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 6](../00-project/Phase-6-SpotDL-Parity.md)

## Outcome

A playlist or album can be synced: new songs download, songs no longer on the list are deleted, and nothing else in the folder is touched. Covers F-33.

## Dependencies

- [T-090](T-090-Save-url-meta.md).

## Work

- The first sync writes a `.spotdl` file naming the query and the files it created.
- A later sync downloads additions and deletes only files that this sync file created and that are no longer in the query. `sync-without-deleting` keeps those files.
- If LRC siblings exist from T-088, deleting a song deletes its `.lrc` too.
- Cancel leaves files already written. A failed new song does not delete the old set.

## Acceptance criteria

- [x] A song added to the fixture list downloads. A song removed from it deletes only that file.
- [x] A file the sync file did not create is left in place.
- [x] `sync-without-deleting` downloads additions and deletes nothing.
- [x] The delete list is shown before the files are removed.

## Evidence / notes

Done 2026-09-25. spotDL v4.5.2 (`cd4a4203`) was read only for behavior (`console/sync.py`); no Python is vendored or copied.

What landed:

- `SpotifySaveFile.sync` and `SpotifySavedSong.createdFiles`: a sync file names the query, the records, and the audio files that sync created. Only those files can ever be removed.
- `SpotifyDownloadService.planSync(query, savePath, capabilities, listOptions, deleteRemoved)`: resolves the query, diffs it against the saved records by song id, and returns `additions`, `unchanged`, and the `removals` delete list (audio paths plus any existing `.lrc` sibling) without touching a file. `deleteRemoved = false` is `sync-without-deleting`.
- `SpotifyDownloadService.applySync(plan, ...)`: queues the additions through the existing matcher/engine, deletes the planned removals only when every addition queued cleanly (a failed new song keeps the old set), and rewrites the `.spotdl` file with the new records and their created files.
- `SpotifyListStore.delete(relativePath)` with the in-memory fake and the desktop root implementation.

Verification run 2026-09-25:

- `./gradlew :shared:core:jvmTest` — 456 tests, 0 failures; `:shared:core:iosSimulatorArm64Test` — 419, 0 failures; `CHROME_BIN=… ./gradlew :shared:core:wasmJsBrowserTest` — 408, 0 failures.
- `./gradlew :shared:ui:jvmTest` — 84, 0 failures; `:apps:desktop:test` — 128, 0 failures (14 opt-in skipped); `:apps:android-engine-tests:test` — 20, 0 failures.
- Full T-083 command list — BUILD SUCCESSFUL (one transient FFmpeg flake on the first run; the suite passed on the immediate rerun and the desktop tests pass in isolation); `:tools:port-manifest:check` ok.
- Acceptance cases: `SpotifyDownloadServiceTest.theFirstSyncDownloadsEverythingAndWritesTheFile` (a foreign file stays), `theSecondSyncAddsAndDeletesOnlyRemovedFiles` (one addition job, only the removed song's file deleted, the kept song and the foreign file stay, and the delete list exists before `applySync`), `syncWithoutDeletingDownloadsAdditionsAndDeletesNothing`, `aFailedAdditionKeepsTheOldFiles`, `aRemovalAlsoDeletesTheLrcSibling`.
