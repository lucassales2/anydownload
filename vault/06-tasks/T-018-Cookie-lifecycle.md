---
id: T-018
type: task
priority: P0
milestone: M3
tags: [task, credentials, security]
---

# T-018 — Authorized cookie lifecycle

[Home](../Home.md) · [Kanban](../Kanban.md) · [Security](../04-delivery/Security-and-licensing.md)

## Outcome

F-18 cookie upload/replace/status/delete with scoped secrets and explicit user trust.

## Dependencies

- [T-009](T-009-Backend-vertical-slice.md), including approved T-006 security requirements.

## Acceptance criteria

- [ ] Show trusted destination and consent; validate cookie format/size and store only protected owner-scoped references in jobs/subscriptions.
- [ ] Implement agreed secret storage, permissions, transient worker copies, expiration/replacement/deletion and backup/retention behavior.
- [ ] Expose configured/error state without secret contents; redact logs/events/errors and prevent credential artifact downloads.
- [ ] Test expired/invalid credentials, cross-owner denial, deletion during active work and restart using synthetic fixtures.
- [ ] Document platform import limits and that cookies do not guarantee access or authorize unpermitted downloads.

## Evidence / notes

Not started. Never use personal cookie files as committed fixtures or public issue evidence.
