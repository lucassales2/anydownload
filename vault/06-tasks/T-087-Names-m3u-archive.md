---
id: T-087
type: task
priority: P1
milestone: D6
tags: [task, engine, spotify]
---

# T-087 — Names, m3u, and archive

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 6](../00-project/Phase-6-SpotDL-Parity.md)

## Outcome

Finished songs use spotDL's output template, can be listed in an m3u, and are skipped when the archive or an existing file says so. Covers F-29.

## Dependencies

- [T-086](T-086-Spotify-download.md).

## Work

- Default template `{artists} - {title}.{output-ext}` with the variables in spotDL's `docs/usage.md` output table. Paths stay inside the download root. Restrict filenames when asked (`strict`, `ascii`, `none`).
- Overwrite modes: `skip` (default), `metadata` (retag, do not redownload), `force`.
- Optional m3u for the list. Optional archive file of song ids. `skip-explicit` drops explicit songs and says so. Playlist numbering is off unless asked.
- Scan-for-songs looks only in the output directory.

## Acceptance criteria

- [x] A template with a `../` segment is rejected.
- [x] `skip` does not write a second file. `force` replaces it.
- [x] An m3u lists the files this job wrote, in list order.
- [x] An archived song is not downloaded again.

## Evidence / notes

Done 2026-09-25. spotDL v4.5.2 (`cd4a4203`) was read only for behavior (`utils/formatter.py` `format_query`/`restrict_filename`, `utils/m3u.py`, `utils/archive.py`, `download/downloader.py` overwrite/scan flow); no Python is vendored or copied.

What landed:

- `SpotifyOutputTemplate`: every variable in spotDL's `docs/usage.md` output table, the default `{artists} - {title}.{output-ext}`, `../`/absolute-path rejection, and `none`/`strict`/`ascii` restriction.
- `SpotifyListOptions` + `SpotifyListStore`: template, restrict, overwrite, m3u, archive, skip-explicit, playlist numbering, scan-for-songs; the store is root-scoped with an in-memory fake and a desktop adapter.
- `SpotifyDownloadService.queue`: computes the templated relative path, drops explicit songs with a reason, skips archived songs, skips a matching file when scan-for-songs is on, writes the m3u in list order and the archive of song ids.
- Engine: `DownloadRequest.relativePath` (validated by the file store) and `DownloadOptions.overwrite` (`skip`/`metadata`/`force`). `skip` completes without downloading, `metadata` retags an existing file through the toolkit, `force` replaces. `FileStore.mediaFilePath(relativePath)` opens a published file for the toolkit.
- The desktop store DTO persists the new fields; the desktop `AppGraph` wires `DesktopSpotifyListStore` over the download root.

Verification run 2026-09-25:

- `./gradlew :shared:core:jvmTest` — 407 tests, 0 failures; `:shared:core:iosSimulatorArm64Test` — 375, 0 failures; `CHROME_BIN=… ./gradlew :shared:core:wasmJsBrowserTest` — 364, 0 failures.
- `./gradlew :shared:ui:jvmTest` — 84, 0 failures; `:apps:desktop:test` — 126, 0 failures (14 opt-in skipped); `:apps:android-engine-tests:test` — 20, 0 failures.
- Full T-083 command list — BUILD SUCCESSFUL; `:tools:port-manifest:check` ok.
- Acceptance cases: `SpotifyOutputTemplateTest.aTemplateWithAParentSegmentIsRejected`; `EngineMergeTest.skipModeDoesNotDownloadASecondFile`, `forceModeReplacesTheExistingFile`, `metadataModeRetagsWithoutDownloading`; `DesktopSpotifyDownloadGateTest.aTwoSongListWritesAnM3uInOrderAndArchivesTheSongs` (real engine + ffmpeg: the m3u lists both written files in order, and a later queue skips the archived ids); `SpotifyDownloadServiceTest.anArchivedSongIsNotQueuedAgain`, `theTemplateNamesTheFilesAndTheM3uListsThemInOrder`, `skipExplicitDropsTheSongAndSaysSo`, `scanForSongsSkipsAnotherKnownExtensionInTheRoot`, `playlistNumberingRewritesTheTrackNumberBeforeTemplating`.
- Note: the archive is written when the songs are queued, not after every download completes; a later run skips them. OPUS/native-container jobs keep the engine's own name because the extension is only known after extraction; the templated path applies to MP3 and M4A.
