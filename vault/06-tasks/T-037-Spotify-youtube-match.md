---
id: T-037
type: task
priority: P1
milestone: M3
tags: [task, engine, spotify]
---

# T-037 — Spotify URLs via a YouTube match

[Home](../Home.md) · [Kanban](../Kanban.md) · [Client yt-dlp options](../05-research/Client-yt-dlp-options.md) · [Parity](../01-product/Feature-parity.md)

## Outcome

A Spotify track, album, playlist, or artist URL becomes audio on the device. Metadata comes from Spotify. The audio file comes from a YouTube Music match, or a YouTube match when Music has none. The existing engine downloads that match. Spotify's own audio streams are not a source. Covers F-27.

## Dependencies

- [T-009](T-009-Backend-vertical-slice.md). A direct media URL must already download.
- [T-013](T-013-Media-formats.md). The match is an audio extract.
- [T-015](T-015-Captions-thumbnails-metadata.md). Title, artists, album, and artwork are embedded with the media toolkit.
- A YouTube download that can run the JavaScript challenge runtime. Until that exists, this task cannot succeed on YouTube Music or YouTube.

## Acceptance criteria

- [x] Resolve a public Spotify track to title, artists, album, duration, ISRC, and artwork URL. Use the unauthenticated metadata client by default. A client id and secret stored on the device select the official Web API. The app still has no account.
- [x] Match YouTube Music first, then YouTube. Score artist, title, duration, album, and ISRC when the provider returns one. A miss fails that song with a redacted message.
- [x] Hand the matched URL to `DownloadEngine`. Album, playlist, and artist URLs become child jobs. Cancel stops expansion and leaves children already created. One child's failure does not hide the others.
- [x] Embed the Spotify title, artists, album, and artwork in the finished audio file. The file stays inside the download root.
- [x] Keep cookie material, client secrets, and signed media URLs out of logs, history, and this vault. Tests use public fixture URLs only.
- [x] Do not add the spotDL package, do not copy its Python, and do not spawn `spotdl`. Reimplement the workflow. Copying matcher source waits on [T-006](T-006-Review-security-licensing.md).

## Evidence / notes

Done 2026-09-25 with Phase D6. This card is the M3 umbrella; the work landed in [T-084](T-084-Spotify-metadata.md) (metadata), [T-085](T-085-Audio-match.md) (match), and [T-086](T-086-Spotify-download.md) (download + tags).

- Metadata: `com.anydownlod.core.music.SpotifyWebApiClient` over the public Web API object model, with the unauthenticated embed-page session as the default and the official client selected only for a complete stored client id and secret. Fixtures carry no token or secret.
- Match: `YoutubeSearch` (innertube through the existing `ExtractorHttp`) and `AudioMatcher` (YouTube Music then YouTube, ISRC pass, score floors, typed `NoMatch`, manual pair). A provider failure does not stop the next provider.
- Download: `SpotifyDownloadService` resolves, matches, and queues one `DownloadEngine` job per song with a shared `parentBatchId`; unavailable entries and per-song match failures stay in the report. The engine downloads the matched URL and embeds title/artists/album/artwork through the toolkit when `capabilities().canEmbedTags` is true.
- Desktop gate: `DesktopSpotifyDownloadGateTest` downloads a public fixture track as a tagged MP3 inside the root; `ffprobe` shows the title, artist, album, and the attached cover. Mobile and web keep the audio and record that tags were skipped where the toolkit cannot write them; the four-host table is [T-093](T-093-Phase-6-verification.md)'s job.
- No `spotdl` binary, no vendored Python, no Spotify audio stream, no cookie, and no secret enters the repository. Spotify audio previews/CDN URLs never appear in the music code.
