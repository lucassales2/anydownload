---
id: T-018
type: task
priority: P0
milestone: M3
tags: [task, credentials, security]
---

# T-018 — Local cookie lifecycle

[Home](../Home.md) · [Kanban](../Kanban.md) · [Security](../04-delivery/Security-and-licensing.md)

## Outcome

F-18 local cookie import, replace, status, and delete. Cookies stay on this device.

## Dependencies

- [T-009](T-009-Backend-vertical-slice.md), including approved T-006 security requirements.

## Acceptance criteria

- [ ] Ask for consent, validate cookie format/size, and store the file only on this device.
- [ ] Support replacement, deletion, and expiry. Jobs hold a reference, not the cookie text.
- [ ] Show configured/error state without secret contents. Redact logs.
- [ ] Test expired cookies, deletion during active work, and restart using synthetic fixtures.
- [ ] Document platform import limits and that cookies do not guarantee access or authorize unpermitted downloads.

## Evidence / notes

Not started. Never use personal cookie files as committed fixtures or public issue evidence.
