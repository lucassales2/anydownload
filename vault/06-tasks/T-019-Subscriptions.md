---
id: T-019
type: task
priority: P1
milestone: M3
tags: [task, subscriptions, scheduling]
---

# T-019 — Durable channel and playlist subscriptions

[Home](../Home.md) · [Kanban](../Kanban.md) · [Lifecycle](../02-architecture/Download-lifecycle.md)

## Outcome

F-21 on-device recurring scans and controls. Scans run while the app is open.

## Dependencies

- [T-011](T-011-Durable-queue.md).
- [T-012](T-012-Playlists-and-batches.md).
- [T-017](T-017-Options-and-presets.md).
- [T-018](T-018-Cookie-lifecycle.md).

## Acceptance criteria

- [ ] Persist source/name/options, interval, scan cap, seen-ID cap, initial backlog policy and failure/dedupe semantics.
- [ ] Support rename, title filter, skip-members-only, pause/resume, check now/all/selected, bulk delete and last/next-check/errors.
- [ ] Bound/time-limit filter evaluation and scan work; coalesce overlapping checks with backoff/jitter and downtime policy.
- [ ] Test restart, unavailable items, changed cookies/options, overlapping subscriptions and failed-item retry without surprise redownloads.

## Evidence / notes

Not started. iOS and the browser will not keep scans running after the app is suspended. Say that in the UI.
