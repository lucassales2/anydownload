---
id: T-085
type: task
priority: P0
milestone: D6
tags: [task, engine, spotify]
---

# T-085 — Audio match

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 6](../00-project/Phase-6-SpotDL-Parity.md)

## Outcome

A song record becomes one matched URL, or a typed miss. YouTube Music is first. YouTube is second. A `YouTubeURL|SpotifyURL` pair uses the YouTube URL as given.

## Dependencies

- [T-084](T-084-Spotify-metadata.md).

## Context the next session needs

Score artist, title, duration, album, and ISRC when the provider returns one. `only-verified` and `dont-filter-results` are explicit options, defaulting to spotDL's defaults (filter on, verified not required). Fallback providers are T-089. This task does not download the file.

## Work

- Search YouTube Music, then YouTube, through the existing extractor search path. Do not add a new HTTP stack.
- Return the winning URL plus the score. A miss is a typed error that names the song and hides the raw provider body.
- A manual pair skips the search and keeps the Spotify record from T-084 for tags.
- Unit-test with fixture search results, including a wrong-duration reject and a manual pair.

## Acceptance criteria

- [x] YouTube Music is tried before YouTube.
- [x] A low score fails typed and does not invent a URL.
- [x] A manual pair keeps the supplied YouTube URL and the Spotify record.
- [x] No media file is written.

## Evidence / notes

Done 2026-09-25. The matcher and its search path only; no media file and no download. spotDL v4.5.2 (`cd4a4203`) was read only for behavior (`providers/audio/base.py`, `utils/matching.py`, `utils/search.py`, `types/result.py`, `providers/audio/ytmusic.py`, `providers/audio/youtube.py`); no Python is vendored or copied.

What landed:

- `com.anydownlod.core.extract.youtube.YoutubeSearch`: the existing `ExtractorHttp` + innertube seam. YouTube Music `WEB_REMIX` songs and videos (`music.youtube.com/youtubei/v1/search`) and regular YouTube videos (`www.youtube.com/youtubei/v1/search`, `EgIQAfABAQ==`). Parses video id, title, channel/artists, album, duration, views, and the `MUSIC_VIDEO_TYPE_ATV` verified flag. No new HTTP stack; no thumbnails, continuations, or other clients.
- `com.anydownlod.core.music.AudioMatcher`: YouTube Music first, then YouTube; an ISRC pass for providers that support it (a single verified hit, or a score above 80); title/artist/duration/album/forbidden-word scoring with spotDL's floors and the `exp(-0.1 * diff) * 100` time score; `onlyVerified` and `filterResults` options with spotDL's defaults; a typed `SpotifyMatchError.NoMatch` that names the song and the provider list and never a provider body; and `manual()` for a `YouTubeURL|SpotifyURL` pair (`ManualPairParser`).
- Provider wrappers `YoutubeMusicAudioProvider`/`YoutubeAudioProvider`, and `AudioMatcher.default(search)` for the standard order. T-089 adds the fallback providers.
- Port manifest: `YoutubeSearchIE` (`_search.py` subset) added and the coverage block regenerated in `vault/01-product/Ytdlp-equivalence.md`.
- Fixtures: `fixtures/youtube-search/{ytm_songs,ytm_videos,youtube_videos}.json` in the public innertube shape with no thumbnail, visitor data, or signed URL.

Verification run 2026-09-25:

- `./gradlew :shared:core:jvmTest` — 380 tests, 0 failures (27 new).
- `./gradlew :shared:core:iosSimulatorArm64Test` — 348 tests, 0 failures (21 new common tests).
- `CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew :shared:core:wasmJsBrowserTest` — 337 tests, 0 failures (21 new common tests).
- Full T-083 command list — BUILD SUCCESSFUL; `:tools:port-manifest:check` ok (1,751 upstream classes, 3 partial).
- New tests: `YoutubeSearchTest` (10), `AudioMatcherTest` (11), `YoutubeSearchFixturesTest` (4, saved fixtures), `AudioMatcherFixturesTest` (2, end-to-end default order). The wrong-duration reject, the typed miss, the manual pair, `only-verified`, and `dont-filter-results` all have explicit cases.

The matcher has no file or download-engine dependency: a test builds it with providers only and asserts the returned URL, so no media file can be written by this task.
