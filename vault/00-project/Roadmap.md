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

Phases **D1** through **D4** are done. Phase **D4** was verified on 2026-09-24 (extractor core + YouTube single video with and without the embedded runtime; the web extension carries requests and the page runs the solver itself). Spec: [Phase 4 — Extractor core and YouTube](Phase-4-Extractor-Core-and-YouTube.md). Decision: [ADR-008](../03-decisions/ADR-008-Extractor-core-and-youtube-phase.md). Target: [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md).

1. Port manifest and generated coverage table; the HTTP request port (T-055, T-056).
2. Extractor core: `InfoExtractor` base, `InfoDict`/`MediaFormat`, registry, helpers, format-spec selector, `_TESTS` harness (T-057–T-059).
3. YouTube single video without JavaScript (`visionos` client); engine extracts, selects one single-file format, downloads it; desktop oracle; preview and Edit from real formats (T-060–T-063).
4. Route matched URLs through Kotlin on desktop, Android, iOS, and web (T-064–T-067), then the **gate**: JS-less YouTube on four hosts (T-068).
5. Bundle yt-dlp-ejs 0.8.0, `JsRuntime` port, Zipline QuickJS spike, challenge solving with the `web` client, runtime adapters on every host (T-069–T-072).
6. Native HLS and DASH downloaders (T-073). Verify all four hosts (T-074).

The media toolkit stays recorded in ADR-007 and is not built in D4: single-file formats only. With D4 verified, the next phase is either the toolkit build (merge, audio extract) or the first named site, X/Twitter. Spotify matching ([T-037](../06-tasks/T-037-Spotify-youtube-match.md)) becomes possible once a YouTube audio download works but stays outside D4. D4 does not close T-009/T-010.

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
