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

- [ ] Resolve a public Spotify track to title, artists, album, duration, ISRC, and artwork URL. Use the unauthenticated metadata client by default. A client id and secret stored on the device select the official Web API. The app still has no account.
- [ ] Match YouTube Music first, then YouTube. Score artist, title, duration, album, and ISRC when the provider returns one. A miss fails that song with a redacted message.
- [ ] Hand the matched URL to `DownloadEngine`. Album, playlist, and artist URLs become child jobs. Cancel stops expansion and leaves children already created. One child's failure does not hide the others.
- [ ] Embed the Spotify title, artists, album, and artwork in the finished audio file. The file stays inside the download root.
- [ ] Keep cookie material, client secrets, and signed media URLs out of logs, history, and this vault. Tests use public fixture URLs only.
- [ ] Do not add the spotDL package, do not copy its Python, and do not spawn `spotdl`. Reimplement the workflow. Copying matcher source waits on [T-006](T-006-Review-security-licensing.md).

## Evidence / notes

Not started. Behavior baseline is [spotDL](https://github.com/spotDL/spotify-downloader), MIT, README inspected 2026-09-23, recorded in [Client yt-dlp options](../05-research/Client-yt-dlp-options.md). Later slices, not this task: lyrics, SoundCloud, Bandcamp, Piped, save, sync, meta, and Spotify saved-library queries. The web host still cannot fetch the matched media until [T-004](T-004-Validate-KMP-targets.md) shows otherwise. D1 does not include this task.
