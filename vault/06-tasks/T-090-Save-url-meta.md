---
id: T-090
type: task
priority: P1
milestone: D6
tags: [task, engine, spotify]
---

# T-090 — Save, url, and meta

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 6](../00-project/Phase-6-SpotDL-Parity.md)

## Outcome

The user can save song records without downloading, copy the matched URL without downloading, and retag audio files already on disk. Covers F-32.

## Dependencies

- [T-086](T-086-Spotify-download.md).

## Work

- `save` writes a `.spotdl` JSON file of the song records inside the download root. `--preload` stores the matched URL in that file. The file is not a media file.
- `url` shows the matched URL for each song and writes nothing else.
- `meta` reads existing audio in the download root, matches it back to a song record, and re-embeds tags. It does not redownload unless the user asks to redownload in another format the host can encode. `skip-album-art` leaves artwork untouched.
- A `.spotdl` file from `save` can be opened later as a download query.

## Acceptance criteria

- [x] `save` writes no audio. The file ends in `.spotdl` and reloads into song records.
- [x] `url` downloads nothing and shows one URL per song.
- [x] `meta` changes tags on a fixture file and does not fetch media unless redownload was asked.
- [x] Paths outside the download root are rejected.

## Evidence / notes

Done 2026-09-25. spotDL v4.5.2 (`cd4a4203`) was read only for behavior (`console/save.py`, `console/url.py`, `console/meta.py`, and the downloader's preload/redownload flow); no Python is vendored or copied.

What landed:

- `SpotifySaveFile`/`SpotifySavedSong` and `SpotifySaveFiles`: the `.spotdl` JSON document, its encode/decode, and the `isSaveFile` extension rule. `SpotifyListPaths.validate` refuses absolute paths and `..` segments for every sidecar.
- `SpotifyDownloadService.save(preview, path, preload, lyricsOptions)`: writes records plus the matched URL and lyrics; no engine job and no audio.
- `SpotifyDownloadService.urls(preview)`: one matched URL per song, per-song failures, no job and no write.
- `SpotifyDownloadService.previewSaveFile(path)`: reloads a `.spotdl` file as the same preview/query shape.
- `SpotifyDownloadService.meta(preview, options, capabilities, listOptions, skipAlbumArt, redownload)`: matches each song to the templated file in the download root, then queues the engine's `metadata` overwrite mode (retag in place, no media fetch) or `force` when redownload is asked; `skipAlbumArt` clears the artwork URL so existing artwork stays.
- `SpotifyListStore.list()` (in-memory fake and desktop root walk) provides the root's file list; the engine's `metadata` path resolves a published file through `FileStore.mediaFilePath(relativePath)`, and an unexpected failure there now fails the job instead of leaving it non-terminal.

Verification run 2026-09-25:

- `./gradlew :shared:core:jvmTest` — 451 tests, 0 failures; `:shared:core:iosSimulatorArm64Test` — 414, 0 failures; `CHROME_BIN=… ./gradlew :shared:core:wasmJsBrowserTest` — 403, 0 failures.
- `./gradlew :shared:ui:jvmTest` — 84, 0 failures; `:apps:desktop:test` — 128, 0 failures (14 opt-in skipped); `:apps:android-engine-tests:test` — 20, 0 failures.
- Full T-083 command list — BUILD SUCCESSFUL; `:tools:port-manifest:check` ok.
- Acceptance cases: `SpotifyDownloadServiceTest.saveWritesASpotdlFileAndNoAudio`, `aSavedFileReloadsAsAPreview`, `saveRejectsAPathOutsideTheDownloadRoot`, `urlsReturnOneUrlPerSongAndStartNoJobs`, `urlsReportAMissPerSong`, `metaQueuesAMetadataJobForAnExistingFile`, `metaRedownloadUsesForceAndSkipAlbumArtClearsTheArtwork`, `metaSkipsAFileThatIsNotInTheRoot`; the desktop end-to-end `DesktopSpotifyDownloadGateTest.metaRetagsAnExistingFixtureFileWithoutDownloading` (real FFmpeg; ffprobe reads the new title/artist/album, the audio URL is never requested, and the job completes as a metadata skip).
