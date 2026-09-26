---
type: roadmap
tags: [project, delivery]
---

# Roadmap

[Home](../Home.md) · [Kanban](../Kanban.md) · [Feature parity](../01-product/Feature-parity.md)

Dates and effort estimates are intentionally unset until feasibility work is complete. All milestones describe future work; the initial deliverable is documentation only.

| Milestone | Deliverable | Exit gate | Tasks |
| --- | --- | --- | --- |
| **M0 — Plan and de-risk** | Vault, public repository, accepted local-only scope, four-target local-engine feasibility, license notes for a Kotlin port, local UX flows. | Owner scope is recorded in ADR-004. A local download is shown on iOS, Compose/Wasm, Android, and desktop, or a target is documented as blocked with the reason. | T-001–T-007 |
| **M1 — Local vertical slice** | Shared Kotlin engine and UI shell. One public URL downloads on each target and the file stays on the device. | Same workflow on all four families, including a failed URL. Record desktop OS and browser coverage. | T-008–T-010 |
| **M2 — On-device downloader core** | Durable local queue, retries, playlists/channels, batch links, video/audio profiles, history, on-device files. | Queue survives app restart; retries do not duplicate finished files; files are not loaded wholly into memory. This is the usable MVP, not full parity. | T-011–T-014 |
| **M3 — yt-dlp and MeTube workflow parity** | Captions, thumbnails, clips, chapters, SponsorBlock, presets/options, local cookies, subscriptions, sharing, and Spotify URLs matched onto YouTube. | Every parity row has tested evidence or a documented platform gap. Self-host server operations are out of scope. | T-015–T-022, except T-021, plus T-037 |
| **M4 — Portfolio builds** | Repeatable build/run instructions for all four targets and license notices. | A person can build each target from the README. Store submission is not required. | T-023 |

Local execution is the product, not a later optional track. T-024 and T-025 fold into M0/M1 feasibility.

## Current priorities

Phases **D1** through **D7** are done. Phase **D4** was verified on 2026-09-24. Phase **D5** was verified on 2026-09-25: the media toolkit, merge and audio extract, on desktop, Android, and iOS; web stays a documented gap. Spec: [Phase 5 — Media toolkit](Phase-5-Media-Toolkit.md). Decision: [ADR-009](../03-decisions/ADR-009-Media-toolkit-phase.md). Phase **D6** was verified on 2026-09-25: [spotDL](https://github.com/spotDL/spotify-downloader) v4.5.2 parity on the shared engine, with desktop passing the fixture rows and the mobile/web gaps recorded. Spec: [Phase 6 — spotDL parity](Phase-6-SpotDL-Parity.md). Decision: [ADR-010](../03-decisions/ADR-010-Spotdl-parity-phase.md). Phase **D7** was verified on 2026-09-25: the public X/Twitter status video on all four hosts, with the opt-in live status and the web end-to-end gaps recorded. Spec: [Phase 7 — X/Twitter status video](Phase-7-X-Twitter.md). Decision: [ADR-011](../03-decisions/ADR-011-X-twitter-phase.md). D4 spec: [Phase 4 — Extractor core and YouTube](Phase-4-Extractor-Core-and-YouTube.md).

D7 shipped (2026-09-25):

1. `TwitterIE` for a public `x.com`/`twitter.com`/`mobile.x.com`/`mobile.twitter.com` `/user/status/<id>` post: the public guest lookup, videos grouped by stable media id, and typed photo-only/protected/deleted failures (T-094).
2. The preview lists the videos, the first is preselected, photos are not rows, and the Edit panel still chooses quality (T-095).
3. **Gate:** the selection rides `DownloadRequest.selectedMediaIds`, the engine re-extracts at download time, and one file per selected video lands inside the download root on the status job (T-096).
4. Android JVM-equivalent and iOS simulator fixtures, and the web extension carries the lookup and the media GET while the page fetches nothing (T-097–T-099).
5. Four-host verification with the opt-in live desktop status and the web end-to-end gap: T-100.

D6 shipped (2026-09-25):

1. Spotify metadata from fixtures, with the unauthenticated client as the default and the official Web API for stored credentials (T-084).
2. YouTube Music then YouTube matching, with a manual pair and opt-in SoundCloud, Bandcamp, Piped, and slider.kz fallbacks (T-085, T-089).
3. The gate: a public fixture track downloads and is tagged through the D5 toolkit, closing T-037 (T-086).
4. Output templates, m3u, archive, lyrics, `.spotdl` save/url/meta, sync, and the user library behind an on-device Spotify login (T-087–T-092).
5. Four-host verification with the honest mobile/web gaps: T-093.

D5 shipped (2026-09-25):

1. `MediaToolkit` contract in common code: merge, extract audio, and a capability query. A missing toolkit fails typed (T-075).
2. Desktop FFmpeg on `PATH` merges a local split pair and probes the output (T-076). The engine downloads both sides and publishes one file when the host can merge (T-077). Edit follows those capabilities (T-078).
3. **Gate:** desktop merge, including an opt-in public YouTube video; web still refuses (T-079).
4. Android MediaMuxer and iOS AVFoundation remux and stream-copy (T-080, T-081). No bundled FFmpeg.
5. Desktop transcodes MP3, WAV, and FLAC. Mobile keeps those disabled; no platform encoder was proven (T-082). Four-host verification: T-083.

X/Twitter is done in D7. The upstream pin stays `2026.08.19`. D5 does not close T-013: web and some mobile codecs remain gaps. D6 implemented [T-037](../06-tasks/T-037-Spotify-youtube-match.md) and the rest of spotDL's operations (T-084–T-093): save, url, sync, meta, lyrics, fallback providers, and the user's library behind on-device Spotify login. D7 implemented the public X/Twitter status video (T-094–T-100): `TwitterIE`, the selectable preview, the selection download, the three host fixtures, and the web extension path, with photos, threads, cards, Spaces, broadcasts, profiles, `t.co`, cookies, and live mobile/web X runs out.

## Sequencing rules

- Android, iOS, Compose/Wasm, and desktop stay in the same milestone. A target that cannot run the engine is a recorded gap, not a silent cut.
- The remote-client scaffold is not the engine. Do not add a server to unblock a target.
- M3 work can proceed once its dependencies are met. Cookie storage is local and opt-in.
- Store review, hosting, and multi-user auth are out of scope.

## Previous plan

Until 2026-09-21, M1 was an authenticated remote vertical slice, M5 was an optional desktop/Android local engine, and a server was required. That sequence is withdrawn. See [ADR-001](../03-decisions/ADR-001-Execution-model.md).

## Release definitions

**Vertical slice:** one local URL-to-file flow on every target family.

**MVP:** on-device queue, history, and useful video/audio workflows.

**Parity candidate:** reviewed yt-dlp and MeTube workflows are represented and ready for an evidence-based audit.

**Portfolio build:** each target builds and runs locally, with license notices. If a feature differs on a platform, document the difference.
