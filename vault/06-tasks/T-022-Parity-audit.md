---
id: T-022
type: task
priority: P0
milestone: M3
tags: [task, testing, parity]
---

# T-022 — Audit MeTube parity with evidence

[Home](../Home.md) · [Kanban](../Kanban.md) · [Feature matrix](../01-product/Feature-parity.md) · [Testing](../04-delivery/Testing-strategy.md)

## Outcome

A reproducible result for every F-ID, with versions/targets and approved differences; no blanket parity claim from source inspection alone.

## Dependencies

- [T-012](T-012-Playlists-and-batches.md), [T-013](T-013-Media-formats.md), [T-014](T-014-Storage-and-delivery.md).
- [T-015](T-015-Captions-thumbnails-metadata.md), [T-016](T-016-Clips-chapters-SponsorBlock.md).
- [T-017](T-017-Options-and-presets.md), [T-018](T-018-Cookie-lifecycle.md), [T-019](T-019-Subscriptions.md).
- [T-020](T-020-Sharing-and-UX.md), [T-021](T-021-Self-host-operations.md).

## Acceptance criteria

- [ ] Exercise pinned MeTube and AnyDownload with authorized deterministic fixtures; record expected versus actual behavior for F-01–F-26.
- [ ] Include cross-platform core flows and server restart, concurrency, cancellation, subscription, artifact/export and option/security scenarios.
- [ ] Document each gap/difference, evidence, impacted targets and explicit owner approval; open follow-up tasks for unresolved work.
- [ ] Recheck upstream scope drift without silently expanding the baseline; obtain M3 gate review with accurate release wording.

## Evidence / notes

Not started. Unsafe arbitrary options and external-client compatibility need explicit difference decisions, not hidden exceptions.
