---
id: T-125
type: task
priority: P0
milestone: D16
tags: [task, engine, extractors]
---

# T-125 — Priority sites

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 16](../00-project/Phase-16-Priority-sites.md) · [Schedule](../00-project/Full-engine-schedule.md)

## Outcome

Twitter’s remaining classes, and the 13 high-traffic files named in Phase 16, are in the registry. Estimate 37 engineer-days.

## Dependencies

- [T-019](T-019-Subscriptions.md) and [T-020](T-020-Sharing-and-UX.md).

## Acceptance criteria

- [ ] Twitter cards, Amplify, broadcasts, Spaces, and the shortener are translated or marked Partial. No bearer or guest token is committed.
- [ ] Bilibili, Vimeo, PeerTube, and BBC each have harness cases and a manifest row.
- [ ] TikTok, SoundCloud, Facebook, Twitch, Instagram, Dailymotion, VK, Patreon, and archive.org each have harness cases and a manifest row.
- [ ] A matched URL on desktop and Android routes to Kotlin, not the CLI or Chaquopy.
- [ ] `:tools:port-manifest:check` passes.

## Evidence / notes

Not started. Scheduled 2026-09-29 by [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md).
