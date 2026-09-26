---
id: T-088
type: task
priority: P1
milestone: D6
tags: [task, engine, spotify]
---

# T-088 — Lyrics

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 6](../00-project/Phase-6-SpotDL-Parity.md)

## Outcome

A finished song can carry lyrics from genius, azlyrics, musixmatch, or the synced provider, in that default order. An optional LRC file sits next to the audio. Covers F-30.

## Dependencies

- [T-086](T-086-Spotify-download.md).

## Work

- Try the configured providers in order. The first hit is embedded when the container allows lyrics. A miss leaves the audio and records that lyrics were skipped.
- `--generate-lrc` writes an `.lrc` sibling only when the synced provider returned timed lines. A Genius token, if the user stores one, stays on the device and out of logs.
- Tests use fixture lyric pages, not live scrapes.

## Acceptance criteria

- [x] Provider order is genius, azlyrics, musixmatch, then synced, unless the user sets another order.
- [x] Lyrics embed only when the toolkit says the container supports them.
- [x] LRC is written only for synced lines, and only when requested.
- [x] No token in fixtures or logs.

## Evidence / notes

Done 2026-09-25. spotDL v4.5.2 (`cd4a4203`) was read only for behavior (`providers/lyrics/base.py`, `genius.py`, `azlyrics.py`, `musixmatch.py`, `synced.py`, and `utils/metadata.py` `embed_lyrics`); no Python is vendored or copied.

What landed:

- `LyricsFetcher` + `LyricsProviders`: the default order genius, azlyrics, musixmatch, synced; a user order; first non-blank hit wins; a failing provider does not stop the rest; a miss returns null.
- `GeniusLyricsProvider` (official search/song API with an optional on-device token sent only in the explicit authorization field; no token means a miss), `AzlyricsLyricsProvider`, `MusixmatchLyricsProvider`, and `SyncedLyricsProvider` (LRCLIB public JSON; timed lines win, plain lines fall back). Parsers are fixture-tested against public page shapes.
- `SpotifyLyricsOptions` (enabled, provider order, generateLrc) and service integration: lyrics ride in `MediaTags.lyrics`; a miss records the song in `SpotifyQueueReport.lyricsMisses` and keeps the audio; `lrcContent` is set only when the synced provider returned timed lines and `generate-lrc` is on.
- Toolkit/engine: `ToolkitCapabilities.lyricsContainers` (desktop: MP3, M4A, Opus, FLAC; not WAV) and `DownloadJob.lyricsEmbedded`. The engine drops lyrics for a container the host does not list and records `lyricsEmbedded=false`; the desktop toolkit writes `-metadata lyrics=…`. The engine publishes a `.lrc` sibling as a METADATA artifact next to the audio when `lrcContent` is set.
- The desktop store DTO persists `lyricsEmbedded` and `lrcContent`; the desktop graph wires `LyricsFetcher.default` over the shared extractor HTTP.

Verification run 2026-09-25:

- `./gradlew :shared:core:jvmTest` — 431 tests, 0 failures; `:shared:core:iosSimulatorArm64Test` — 394, 0 failures; `CHROME_BIN=… ./gradlew :shared:core:wasmJsBrowserTest` — 383, 0 failures.
- `./gradlew :shared:ui:jvmTest` — 84, 0 failures; `:apps:desktop:test` — 127, 0 failures (14 opt-in skipped); `:apps:android-engine-tests:test` — 20, 0 failures.
- Full T-083 command list — BUILD SUCCESSFUL; `:tools:port-manifest:check` ok.
- Acceptance cases: `LyricsFetcherTest` (default and user order); `EngineMergeTest.lyricsAreEmbeddedOnlyForAContainerTheHostLists` and `lyricsAreDroppedWhenTheContainerIsNotListed`; `SpotifyDownloadServiceTest.generateLrcWritesOnlySyncedLines`, `lyricsRideOnTheRequestAndAMissIsRecorded`, `lyricsCanBeDisabled`; `EngineMergeTest.aRequestedLrcIsPublishedNextToTheAudio`; `DesktopFfmpegToolkitTest.embedTagsWritesLyricsForM4a` (ffprobe reads the lyrics back); `LyricsFixturesTest.noSavedLyricsFixtureCarriesATokenOrCookie`.
- Hygiene: no `ProcessBuilder` in common code; the saved lyric fixtures contain no token, cookie, or signed URL; the Genius token only ever rides in the explicit `authorization` field.
