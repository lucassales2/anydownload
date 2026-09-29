---
id: ADR-012
type: adr
status: accepted
created: 2026-09-29
tags: [architecture, decisions, queue, platforms]
---

# ADR-012 — On-device queue, playlists, and history (M1 and M2)

[Home](../Home.md) · [Decision log](Decision-log.md) · [Phase 8](../00-project/Phase-8-On-device-core.md) · [ADR-004](ADR-004-Local-kotlin-engine.md) · [Download lifecycle](../02-architecture/Download-lifecycle.md)

Does not supersede [ADR-004](ADR-004-Local-kotlin-engine.md). ADR-004 remains the end state. Does not supersede [ADR-008](ADR-008-Extractor-core-and-youtube-phase.md) through [ADR-011](ADR-011-X-twitter-phase.md). Direct files, the generic subset, YouTube single video, the media toolkit, spotDL, and the public X status stay as they are.

## Context

Phases D1 through D7 are done. The original milestones M1 and M2 are still open on the Kanban ([T-007](../06-tasks/T-007-Define-UX-and-contract.md) through [T-014](../06-tasks/T-014-Storage-and-delivery.md)). Much of their surface already exists, and the cards do not say so:

- The domain already has job states, attempts, artifacts, idempotency keys, start policy, and error codes. [Download lifecycle](../02-architecture/Download-lifecycle.md) is the contract.
- Desktop persists `jobs.json` and, on load, marks an active job failed and retryable. It does not auto-resume. Android, iOS, and web still hold jobs in memory, so a restart drops the queue.
- `HttpDownloadEngine` already downloads, cancels, and retries. Concurrency is a semaphore captured when the engine is built.
- The Add form already splits a newline batch into child requests. Nothing in the shared engine expands a playlist or channel into entries. `playlistItemLimit` of 0 means "no extra cap".
- D5 already merges and extracts audio on desktop, Android, and iOS. [T-013](../06-tasks/T-013-Media-formats.md) is still open because the profile controls are not tied to that evidence.
- Queue, history, add, and settings screens exist from D1. Subscriptions, cookies, captions, clips, and SponsorBlock are M3.

The owner asked on 2026-09-29 for a loop-sized plan of M1 and M2. The next site in the extractor catalog stays unscheduled.

## Proposed decision

- **Phase D8** is the next implementation work. Tasks are [T-101](../06-tasks/T-101-On-device-contract.md) through [T-112](../06-tasks/T-112-Phase-8-verification.md), sequenced in the [phase note](../00-project/Phase-8-On-device-core.md). Nothing starts until T-100 is Done. [T-105](../06-tasks/T-105-Gate-desktop-restart.md) is the M1 desktop gate. [T-106](../06-tasks/T-106-Four-host-m1.md) is the four-host M1 gate and the point after which playlist work may start.
- **Do not rebuild D1–D7.** The phase adds a shared job document, host persistence, playlist expansion, a YouTube playlist subset, batch export, naming rules, and an honest profile check. It does not replace `HttpDownloadEngine`, the extractors, or the Compose screens.
- **Restart behavior matches desktop.** Active work (`RESOLVING`, `QUEUED`, `DOWNLOADING`, `POSTPROCESSING`) becomes `FAILED` with `ENGINE_UNAVAILABLE`, retryable, and a redacted message. The row is restored. The download does not resume by itself. A completed artifact is not downloaded again on retry of a job that already finished. `PENDING` and `SCHEDULED` stay as they were.
- **One job document in common code.** Serialization, stale-revision rejection, destination sanitizing, interrupt-on-load, and clear-completed live in `shared/core`. Each host only stores and loads the bytes: desktop keeps `jobs.json` in the state directory; Android and iOS write a file in the app sandbox, separate from the download root; web writes the document to `localStorage`. Media bytes never go into that document.
- **Playlists are bounded.** An extractor may return entries. The engine creates one child job per entry, up to `min(playlistItemLimit, 50)` when the limit is positive, and up to 50 when the limit is 0. Zero does not mean the whole channel. Cancellation during expansion keeps the children already created. An unavailable entry is a failed child with a redacted error, not a silent skip. Children of one expansion share `parentBatchId`. A repeated idempotency key does not create a second child.
- **One playlist extractor.** `YoutubeTabIE` is partial: `/playlist?list=` only, from a redacted fixture, flat video id and title, no channel tab, no mix, no cookies, no continuations beyond what the fixture needs to reach the cap. The child download reuses the existing YouTube single-video path.
- **Not-yet-available is `SCHEDULED`.** If extraction says the source is not available yet, the job stays `SCHEDULED` and downloads nothing. The user starts it again later. There is no countdown, no timer, and no live stream.
- **History actions stay separate.** Remove history drops the row and does not delete the file. Delete artifacts deletes or marks files and keeps the row. Cancel stops work and does not delete a finished file. Web may be unable to delete a file the browser already saved; that limitation is written down, not papered over.
- **Profiles follow D5.** T-111 proves the existing Auto/MP4/iOS, codec, resolution, and audio-container choices against host capabilities. It does not add an encoder. Web merge and mobile MP3/WAV/FLAC stay disabled.
- **Milestone cards.** T-101 closes T-007 by recording the contract the screens already implement. T-106 closes T-008, T-009, and T-010. T-112 closes T-011, T-012, and T-014, and closes T-013 when T-111's evidence is in. The upcoming-source countdown criterion on T-011 is replaced by the `SCHEDULED` rule above.
- **Out of D8:** cookies, subscriptions on the shared engine, captions, thumbnails as artifacts, clips, chapters, SponsorBlock, PO tokens, live HLS, another site, retiring the desktop CLI or Chaquopy, and store submission. M3 and M4 stay on their existing cards.

## Alternatives

- **Implement the old T-007–T-014 cards as written.** Rejected: several say "not started" over work D1–D7 already shipped, and each card is larger than one loop step.
- **Auto-resume a download after relaunch.** Rejected: desktop already fails the attempt and asks for retry, and a half-written temp is not a completed file. Byte resume stays best-effort inside one attempt.
- **Unbounded playlists when the limit is 0.** Rejected: a channel URL would expand without a ceiling. The hard cap is 50 until a later phase raises it with a measurement.
- **Port `youtube/_tab.py` whole.** Rejected: channels, mixes, and continuations are a different phase. D8 needs flat playlist entries so the expander has one real source.
- **Leave Android, iOS, and web on the in-memory engine.** Rejected: M1's exit gate is the same workflow on all four families, including restore after close.

## Consequences

D8 ends with a queue that survives restart on every host, a failed URL that stays a failed row, playlist and batch children under a cap, and history actions that do not confuse cancel, remove, and delete. The extractor count moves by one partial module. M3 remains the next product milestone. The catalog of other sites is unchanged.

Risks: web `localStorage` is small, so the job document must stay metadata-only; a YouTube playlist fixture must be redacted; expanding a playlist must not hold entry media in memory; iOS still cannot continue work after suspension, which this phase records rather than solving.

## Validation / approval

Accepted 2026-09-29 by [T-112](../06-tasks/T-112-Phase-8-verification.md). The four-host M2 table is recorded in the [phase note](../00-project/Phase-8-On-device-core.md#verification-2026-09-29): the queue restores after restart on desktop, Android, iOS, and web, retry does not duplicate a finished file, and files stream as they arrive. Host limits are recorded (Android JVM-equivalent with no emulator, iOS simulator-only, web wasm/Brave with no real browser click-through). No host is blocked. M3 stays later.
