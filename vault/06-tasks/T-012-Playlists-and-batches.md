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

- [ ] Define single/playlist interpretation, item limits, child-job IDs/dedupe and partial-failure reporting.
- [ ] Import newline-separated URLs with progress and cancellable metadata expansion; preserve acknowledged jobs and per-item errors.
- [ ] Support auto-start/manual/bulk controls plus filtered URL export/copy without silently revealing private URLs.
- [ ] Test duplicates, huge/unavailable/private entries, cancellation, restart during expansion and quota boundaries.

## Evidence / notes

Not started. Avoid unbounded metadata requests and back-catalog downloads.
