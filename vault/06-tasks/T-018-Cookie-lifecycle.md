---
id: T-018
type: task
priority: P0
milestone: D11
tags: [task, credentials, security]
---

# T-018 — Local cookie lifecycle

[Home](../Home.md) · [Kanban](../Kanban.md) · [Security](../04-delivery/Security-and-licensing.md)

## Outcome

F-18 local cookie import, replace, status, and delete. Cookies stay on this device.

## Dependencies

- [T-123](T-123-Shared-downloader.md). Phase: [Phase 11](../00-project/Phase-11-Cookies.md). Estimate 6 engineer-days. Pulled ahead of the other M3 cards because later extractors attach the jar.

## Acceptance criteria

- [ ] Ask for consent, validate cookie format/size, and store the file only on this device.
- [ ] Support replacement, deletion, and expiry. Jobs hold a reference, not the cookie text.
- [ ] Show configured/error state without secret contents. Redact logs.
- [ ] Test expired cookies, deletion during active work, and restart using synthetic fixtures.
- [ ] Document platform import limits and that cookies do not guarantee access or authorize unpermitted downloads.

## Evidence / notes

Not started. Scheduled 2026-09-29 as D11 in [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md). The jar attaches in the shared HTTP helper. Never use personal cookie files as committed fixtures or public issue evidence. Browser-store reading stays out on mobile and web.
