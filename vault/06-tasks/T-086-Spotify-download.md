---
id: T-086
type: task
priority: P0
milestone: D6
tags: [task, engine, spotify]
---

# T-086 — Spotify download

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 6](../00-project/Phase-6-SpotDL-Parity.md) · [T-037](T-037-Spotify-youtube-match.md)

## Outcome

A public Spotify track becomes one audio file on the device. Album, playlist, and artist URLs become child jobs. Tags use the D5 toolkit. This task closes T-037 and F-27.

## Dependencies

- [T-085](T-085-Audio-match.md).
- [T-013](T-013-Media-formats.md) — the match is an audio extract. D5's toolkit is enough to embed on desktop; mobile embeds only what `capabilities()` allows.

## Context the next session needs

Hand the matched URL to `DownloadEngine`. Do not spawn `spotdl`. Default format is MP3 where the host can encode it, otherwise the best container the host can copy (M4A or Opus). Embed title, artists, album, and artwork. Lyrics are T-088.

## Work

- The link field accepts a Spotify URL and a text search. Preview lists the songs, then Download queues them.
- Cancel stops expansion and leaves children already created. One child's failure does not hide the others.
- Embed through `MediaToolkit`. If the host cannot embed, keep the audio and record that the tags were skipped.
- Desktop gate: one public track fixture or opt-in live URL downloads, and ffprobe shows the title. No cookies.

## Acceptance criteria

- [x] A public track downloads as audio inside the download root.
- [x] Album, playlist, and artist URLs become child jobs with per-item errors.
- [x] Title, artists, album, and artwork are embedded when the host can write them.
- [x] T-037's acceptance boxes are checked and that card moves to Done with this task.
- [x] No `spotdl` binary, no Spotify audio URL, and no secret in logs.

## Evidence / notes

Done 2026-09-25. The gate task: a Spotify record now becomes one tagged audio file through the existing engine. The metadata is T-084's, the match is T-085's, and this task adds the download/tag/queue path.

What landed:

- `com.anydownlod.core.music.SpotifyDownloadService`: resolves a query through `SpotifyMetadataClient`, matches each song through `AudioMatcher`, and submits one `DownloadEngine` job per hit with a shared `parentBatchId`. Unavailable entries and per-song match failures stay in `SpotifyQueueReport.failures`; cancellation stops the expansion and leaves the child jobs already created. The container is MP3 when the host can encode it, otherwise M4A, then Opus, then null (native container). No media is downloaded here.
- Engine: `DownloadRequest.metadata`/`artworkUrl`/`parentBatchId`; `DownloadJob.tagsEmbedded`; `MediaToolkit.embedTags` with `ToolkitCapabilities.canEmbedTags`/`canEmbedArtwork`. The engine fetches the artwork bounded (5 MiB) and `UrlPolicy`-checked, then embeds before publishing on both the native-copy and audio-extract routes. A host without the capability keeps the audio and records `tagsEmbedded=false`; a failed rewrite fails the job and discards the temp.
- Desktop `DesktopFfmpegToolkit.embedTags`: FFmpeg stream copy plus `-metadata` and an attached picture, an `ffprobe` title check, and a tags-only retry when a container rejects the artwork. Android/iOS/web keep `canEmbedTags=false` and keep the audio.
- UI: `AddFormPresenter` accepts a Spotify URL, `spotify:` URI, `album:`/`playlist:`/`artist:` prefix, or plain text; `SpotifyMediaPreviewSource` lists the songs and unavailable entries; `PreviewScreen` renders the list; Download queues through the service. The desktop `AppGraph` wires the service; other hosts default to null and show the unavailable preview. `StoreDto` persists the new fields.

Verification run 2026-09-25:

- `./gradlew :shared:core:jvmTest` — 390 tests, 0 failures; `:shared:core:iosSimulatorArm64Test` — 358, 0 failures; `CHROME_BIN=… ./gradlew :shared:core:wasmJsBrowserTest` — 347, 0 failures.
- `./gradlew :shared:ui:jvmTest` — 84, 0 failures; `:apps:desktop:test` — 125, 0 failures (14 opt-in skipped); `:apps:android-engine-tests:test` — 20, 0 failures; `node --test apps/web-extension/test/bridge.test.mjs` — 23, 0 failures.
- Full T-083 command list — BUILD SUCCESSFUL; `:tools:port-manifest:check` ok.
- Desktop gate: `DesktopSpotifyDownloadGateTest.aFixtureTrackDownloadsAsATaggedMp3InsideTheRoot` (1 test, 0 skipped) downloads a public fixture track as `Fixture Song.mp3` inside the download root, with `tagsEmbedded=true`; `ffprobe` reports `title=Fixture Song`, `artist=Fixture Artist`, `album=Fixture Album`, and an attached cover stream. `DesktopFfmpegToolkitTest.embedTagsWritesTitleArtistAlbumAndArtwork` checks the toolkit directly. The gate uses the fixture path (public fixture URLs); live Spotify/YouTube is quota-limited on this network, so no live call is recorded. T-093 states the opt-in live result per host.
- Hygiene: no `spotdl` binary or vendored Python, no `ProcessBuilder` in common code, and no Spotify preview/CDN URL in the music code.
