---
id: T-012
type: task
priority: P1
milestone: M2
tags: [task, downloads, playlists]
---

# T-012 — Playlists, channels and batch links

[Home](../Home.md) · [Kanban](../Kanban.md) · [Parity](../01-product/Feature-parity.md)

## Outcome

Bounded playlist/channel expansion and URL import/export/copy for F-04–F-06.

## Dependencies

- [T-011](T-011-Durable-queue.md).

## Acceptance criteria

- [x] Define single/playlist interpretation, item limits, child-job IDs/dedupe and partial-failure reporting.
- [x] Import newline-separated URLs with progress and cancellable metadata expansion; preserve acknowledged jobs and per-item errors.
- [x] Support auto-start/manual/bulk controls plus filtered URL export/copy without silently revealing private URLs.
- [ ] Test duplicates, huge/unavailable/private entries, cancellation, restart during expansion and quota boundaries.

## Evidence / notes

Not started as this card. Newline batch import already exists on the add form. Playlist expansion does not. [T-107](T-107-Playlist-expansion.md), [T-108](T-108-Youtube-playlist-subset.md), and [T-109](T-109-Batch-export.md) in [Phase 8](../00-project/Phase-8-On-device-core.md) add a cap of 50 and a YouTube playlist subset. Channels stay out. Avoid unbounded metadata requests and back-catalog downloads.

### Closed by Phase 8 (2026-09-29)

- Single versus playlist is decided by `InfoDict.entries`: empty keeps the single-video path; non-empty creates one child per entry under `min(playlistItemLimit, 50)` (0 means 50) with the parent batch id and per-entry idempotency keys. Duplicate entry ids are skipped; an unavailable entry becomes a failed child without aborting the rest (`EnginePlaylistExpansionTest`, `YoutubeTabIEFixturesTest`).
- Newline import is the existing `AddFormPresenter` batch split with per-line validation; playlist expansion is cancellable between entries and keeps the children already created (`cancelDuringExpansionKeepsTheFirstChildAndCreatesNoMore`).
- Auto/manual start and bulk controls stay in the presenters; explicit Copy URLs / Copy batch exports only the submitted source URLs, with failed children included and no cookie or media URL (`JobSourceUrls`, `QueuePresenterTest`, `HistoryPresenterTest`, `QueueHistoryUiTest`).
- The remaining bullet stays open for M3: duplicates/unavailable/private entries, cancellation, and the web over-cap quota are tested, but restart specifically *during* expansion is not a separate test (it is covered by the generic active-row restart rule).
