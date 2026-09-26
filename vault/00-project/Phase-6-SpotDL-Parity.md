---
type: phase
status: done
milestone: D6
tags: [project, engine, spotify, parity]
---

# Phase 6 — spotDL parity

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-010](../03-decisions/ADR-010-Spotdl-parity-phase.md) · [Phase 5](Phase-5-Media-Toolkit.md) · [Parity](../01-product/Feature-parity.md) · [T-037](../06-tasks/T-037-Spotify-youtube-match.md)

**Start here if you are implementing.** This note is the handoff for Phase D6. It starts only after D5 is verified. ADR-004 remains the end state. D5's media toolkit stays. This phase makes a Spotify query behave like [spotDL](https://github.com/spotDL/spotify-downloader) v4.5.2: metadata from Spotify, audio from a matched provider, tags and artwork embedded by the toolkit.

**Phase D6 is done (2026-09-25).** [T-093](../06-tasks/T-093-Phase-6-verification.md) recorded the four-host result: desktop passes the public download, save, url, meta, sync, and fixture-token library rows on public fixtures; Android and iOS run the shared tests with the public path wired and record the list/token-store and tag-embedding gaps; web carries every request through the extension with no page fetch and records the Spotify-flow and tag gaps. [T-037](../06-tasks/T-037-Spotify-youtube-match.md) is Done. X/Twitter is next.

Owner direction, 2026-09-25: spotDL feature parity is the phase after the media toolkit. X/Twitter is the next named site after this phase.

Baseline inspected 2026-09-25: spotDL `master` at `cd4a4203f5b12bd6dbbdf22d7674807858d35e05` (release v4.5.2, 2026-07-20). Sources: the [README](https://github.com/spotDL/spotify-downloader/blob/cd4a4203f5b12bd6dbbdf22d7674807858d35e05/README.md) and [`docs/usage.md`](https://github.com/spotDL/spotify-downloader/blob/cd4a4203f5b12bd6dbbdf22d7674807858d35e05/docs/usage.md). MIT. Do not vendor the package, do not copy its Python, and do not spawn `spotdl`.

## Done looks like

A public Spotify track, album, playlist, or artist URL, and a text search, becomes audio on the device. Metadata comes from Spotify. The file comes from YouTube Music, then YouTube, then SoundCloud, Bandcamp, Piped, or slider.kz when an earlier provider misses. The existing engine downloads the match. Title, artists, album, artwork, and lyrics are embedded where the host toolkit can write them. Save, url, meta, and sync exist. Liked songs and the user's playlists and albums work only after an on-device Spotify login. Spotify's own audio is never a source. Direct-file, generic-page, and YouTube downloads behave as they do after D5.

## Rules for every D6 task

- No backend, no AnyDownload account, and no MeTube HTTP/Socket.IO client. Spotify OAuth in T-092 is a Spotify login stored on the device, used only for that user's library queries.
- No `spotdl` subprocess, no vendored spotDL, and no copied matcher source. Reimplement the workflow. MIT notices stay in [T-006](../06-tasks/T-006-Review-security-licensing.md).
- Common code has no `ProcessBuilder`. Embedding goes through `MediaToolkit`. Do not add a second FFmpeg.
- Spotify audio streams, preview URLs, and DRM are not a source.
- Client secrets, OAuth tokens, cookie files, and signed media URLs stay out of logs, history, fixtures, and this vault. Tests use public fixture URLs only.
- A miss or a provider the host cannot fetch fails that song with a redacted message. One child's failure does not hide the others.
- Free-form `--yt-dlp-args` and `--ffmpeg-args` stay disabled. The allowlist in ADR-006 stands.
- Do not download FFmpeg or Deno. D5 owns the toolkit. D4 owns the JavaScript runtime.
- When a task is finished, check its acceptance boxes, write what you ran under Evidence, and move its Kanban card. The board column is the status.

## Layout

```text
shared/core
  music/                Spotify metadata, matcher, save/sync files
shared/ui               Spotify query on the existing link field; library login is a settings control
apps/*                  No spotdl binary. Web reaches Spotify and providers only through the extension
```

Package: `com.anydownlod.core.music`. Downloads stay on `DownloadEngine`.

## Task order

Do them in this order. Dependencies are the links in each task note. Do not start until [T-083](../06-tasks/T-083-Phase-5-verification.md) is Done.

| Order | Task | Delivers |
| --- | --- | --- |
| 1 | [T-084](../06-tasks/T-084-Spotify-metadata.md) | Public Spotify metadata and text search |
| 2 | [T-085](../06-tasks/T-085-Audio-match.md) | YouTube Music, then YouTube; scored match; manual pair |
| 3 | [T-086](../06-tasks/T-086-Spotify-download.md) | **Gate:** one public track downloads and is tagged |
| 4 | [T-087](../06-tasks/T-087-Names-m3u-archive.md) | Output template, overwrite, m3u, archive |
| 5 | [T-088](../06-tasks/T-088-Lyrics.md) | Lyrics providers and optional LRC |
| 6 | [T-089](../06-tasks/T-089-Fallback-providers.md) | SoundCloud, Bandcamp, Piped, slider.kz |
| 7 | [T-090](../06-tasks/T-090-Save-url-meta.md) | save, url, and meta |
| 8 | [T-091](../06-tasks/T-091-Sync.md) | sync, with and without deletion |
| 9 | [T-092](../06-tasks/T-092-Spotify-library.md) | Liked songs and the user's lists, on-device OAuth |
| 10 | [T-093](../06-tasks/T-093-Phase-6-verification.md) | Four-host evidence and the parity rows |

## What this phase covers

| spotDL operation or input | AnyDownload | Row |
| --- | --- | --- |
| `download` of a track, album, playlist, artist, or text search | Child jobs through the engine; audio extract from D5 | F-27, F-28 |
| `YouTubeURL\|SpotifyURL` | That YouTube URL, Spotify tags | F-28 |
| Output template, m3u, overwrite, archive, skip explicit | Inside the download root | F-29 |
| Lyrics: genius, azlyrics, musixmatch, synced; `--generate-lrc` | Embedded when the container allows; LRC beside the file | F-30 |
| Providers after YouTube Music and YouTube | SoundCloud, Bandcamp, Piped, slider.kz | F-31 |
| `save`, `url`, `meta` | A `.spotdl` file, the matched URL, retag | F-32 |
| `sync` | New songs download; removed songs delete unless opted out | F-33 |
| `saved`, `all-user-playlists`, `all-user-saved-albums`, `all-user-followed-artists` | On-device Spotify OAuth only | F-34 |

Formats stay the D5 set: MP3, WAV, and FLAC only where that host can encode. Bitrate conversion is a desktop transcode. Mobile copies the container D5 already proved. YouTube Music Premium 256 kbps waits on the local cookie file in [T-018](../06-tasks/T-018-Cookie-lifecycle.md). SponsorBlock waits on [T-016](../06-tasks/T-016-Clips-chapters-SponsorBlock.md).

## Explicitly later or out of scope

- X / Twitter, the next named site after D6.
- spotDL's `web` server, its host/port/TLS flags, Docker, pip, and `--download-ffmpeg` / `--download-deno`. The app is already the interface.
- Free-form yt-dlp or FFmpeg argument strings.
- A Spotify audio stream. An AnyDownload account.

## How to start a session

Paste the prompt in [Phase 6 loop prompt](Phase-6-Loop-prompt.md) as the first message, after D5 is verified.

1. Read this note and [ADR-010](../03-decisions/ADR-010-Spotdl-parity-phase.md).
2. On [Kanban](../Kanban.md), take the first D6 card whose dependencies are Done.
3. Read that task note fully before editing code.
4. Do not pick up X/Twitter or a spotDL web server.
