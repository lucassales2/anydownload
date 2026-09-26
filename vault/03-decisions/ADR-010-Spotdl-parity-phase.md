---
id: ADR-010
type: adr
status: accepted
created: 2026-09-25
tags: [architecture, decisions, engine, spotify]
---

# ADR-010 — spotDL parity after the media toolkit

[Home](../Home.md) · [Decision log](Decision-log.md) · [Phase 6](../00-project/Phase-6-SpotDL-Parity.md) · [ADR-009](ADR-009-Media-toolkit-phase.md) · [T-037](../06-tasks/T-037-Spotify-youtube-match.md)

Does not supersede [ADR-004](ADR-004-Local-kotlin-engine.md). ADR-004 remains the end state. Does not supersede [ADR-009](ADR-009-Media-toolkit-phase.md). D5 still builds the toolkit before this phase starts. This record schedules the spotDL workflow [T-037](../06-tasks/T-037-Spotify-youtube-match.md) named and left outside D5.

## Context

Phase D5 is the next implementation work (proposed 2026-09-25, [ADR-009](ADR-009-Media-toolkit-phase.md)): merge and audio extract on desktop, Android, and iOS. Embedding title, artists, album, and artwork needs that toolkit, so Spotify matching cannot finish before D5.

Owner, 2026-09-25: the phase after D5 is feature parity with [spotDL](https://github.com/spotDL/spotify-downloader). X/Twitter, previously the next named site after D5, follows this phase instead.

Baseline inspected the same day: spotDL v4.5.2, `master` commit `cd4a4203f5b12bd6dbbdf22d7674807858d35e05`. MIT. The README and `docs/usage.md` list `download`, `save`, `web`, `url`, `sync`, and `meta`; queries for track, album, playlist, artist, text search, a `YouTubeURL|SpotifyURL` pair, and user-library words that need Spotify OAuth; audio providers youtube-music, youtube, soundcloud, bandcamp, piped, and slider.kz; lyrics providers genius, azlyrics, musixmatch, and synced.

spotDL does not download Spotify audio. This project already matches that rule.

## Proposed decision

- **Phase D6** follows D5. Tasks are [T-084](../06-tasks/T-084-Spotify-metadata.md) through [T-093](../06-tasks/T-093-Phase-6-verification.md), sequenced in the [phase note](../00-project/Phase-6-SpotDL-Parity.md). Nothing in D6 starts until [T-083](../06-tasks/T-083-Phase-5-verification.md) is Done. [T-086](../06-tasks/T-086-Spotify-download.md) is the gate: one public track downloads and is tagged, and that completion also closes [T-037](../06-tasks/T-037-Spotify-youtube-match.md).
- **Metadata, then a match, then the existing engine.** The unauthenticated Spotify client is the default. A client id and secret stored on the device select the official Web API. The app still has no account. YouTube Music is the first audio provider, then YouTube. SoundCloud, Bandcamp, Piped, and slider.kz are fallbacks. The matched URL is an ordinary `DownloadEngine` job.
- **Tags use the D5 toolkit.** Title, artists, album, artwork, and lyrics are embedded only when `capabilities()` says the host can write them. A host that cannot embed still keeps the audio file and says why the tags were skipped.
- **Operations.** `save` writes a `.spotdl` file and downloads nothing. `url` returns the matched URL and downloads nothing. `meta` retags files already in the download root. `sync` downloads songs added to the list and deletes songs removed from it, unless the user turns deletion off.
- **User library.** `saved`, `all-user-playlists`, `all-user-saved-albums`, and `all-user-followed-artists` require Spotify OAuth stored on the device. Public queries never prompt for it.
- **Out of D6:** spotDL's `web` server and its TLS flags, Docker, installing FFmpeg or Deno, free-form yt-dlp or FFmpeg arguments, Spotify audio streams, and X/Twitter. SponsorBlock stays [T-016](../06-tasks/T-016-Clips-chapters-SponsorBlock.md). YouTube Music Premium cookies stay [T-018](../06-tasks/T-018-Cookie-lifecycle.md).

## Alternatives

- Fold the whole spotDL surface into T-037 during M3, before the toolkit exists. Rejected: embedding and audio extract are D5, and the owner placed parity after that phase.
- Shell out to `spotdl`. Rejected: ADR-004 is a Kotlin engine, and a subprocess is not available on iOS or web.
- Copy spotDL's Python into the tree. Rejected for this phase: T-006 already says reimplement the workflow. A later copy still needs the MIT notice and a written note.
- Treat spotDL's `web` command as a local server in the app. Rejected: the four hosts already have the UI, and a server was withdrawn in ADR-004.
- Keep X/Twitter as the phase immediately after D5. Rejected by the owner on 2026-09-25: spotDL parity is that phase.

## Consequences

D6 ends with spotDL's download, save, url, sync, and meta operations on the shared engine, plus the user-library queries behind on-device Spotify OAuth. F-27 through F-34 gain evidence or a written host gap. The web host still only reaches origins the extension allows; a provider it cannot fetch fails that song. X/Twitter moves to after D6.

Risks: Spotify's unofficial metadata client and YouTube's player both change without a release. A match can pick the wrong recording; the score and the manual `YouTubeURL|SpotifyURL` pair are the correction, not a silent second download. Sync deletion can remove a file the user also kept for another playlist; deletion is limited to files this sync file created, and the user can turn it off.

## Validation / approval

Accepted 2026-09-25: [T-093](../06-tasks/T-093-Phase-6-verification.md) recorded the four-host result. Desktop passes the public download, save, url, meta, sync, and fixture-token library rows on public fixtures; Android and iOS run the shared tests with the public path wired and record the list/token-store and tag-embedding gaps; web carries every request through the extension with no page fetch and records the Spotify-flow and tag gaps. No blocker was written. [T-037](../06-tasks/T-037-Spotify-youtube-match.md) is Done; X/Twitter is next.
