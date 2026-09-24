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

Phase **D1** is done. Phase **D2** is the implementation work in progress. Spec: [Phase 2 — Local HTTP engine](Phase-2-Local-Kotlin-Engine.md). Decision: [ADR-006](../03-decisions/ADR-006-Local-http-engine-phase.md).

1. Finish the T-006 license inventory and the Q-09 allowlist close on T-003.
2. Shared HTTP-only `DownloadEngine` and platform file adapters (T-038, T-039).
3. Wire desktop (Kotlin HTTP + existing CLI), Android (HTTP + Chaquopy), iOS (HTTP), and web (extension) — T-040–T-043.
4. Verify all four hosts and close T-004 from that evidence (T-044).

After D2, extractor translation and YouTube (yt-dlp-ejs) resume under ADR-004. Spotify matching ([T-037](../06-tasks/T-037-Spotify-youtube-match.md)) still waits until a YouTube download works. D2 does not close T-009/T-010's extractor wording.

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
