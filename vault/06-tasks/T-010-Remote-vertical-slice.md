---
id: T-010
type: task
priority: P0
milestone: M1
tags: [task, integration, platforms]
---

# T-010 — Demonstrate the remote flow on all targets

[Home](../Home.md) · [Kanban](../Kanban.md) · [User flows](../01-product/User-flows.md) · [Parity](../01-product/Feature-parity.md)

## Outcome

Android, iOS, desktop and web can authenticate, submit a URL, monitor it and export an artifact. Initial F-01/F-07 evidence.

## Dependencies

- [T-008](T-008-Scaffold-KMP-clients.md).
- [T-009](T-009-Backend-vertical-slice.md).

## Acceptance criteria

- [ ] Record end-to-end runs on every target family with exact OS/browser/build/engine versions and explicit desktop coverage.
- [ ] Demonstrate server completion versus local export, authenticated streaming without whole-file buffering, and export cancellation/denial.
- [ ] Demonstrate failed/unsupported URL, invalid credentials and client disconnect/reconnect while server work continues.
- [ ] Link safe logs/screenshots/tests and document target-specific limitations; obtain M1 gate review.

## Evidence / notes

Not started. This is a vertical slice, not an MVP or full MeTube parity claim.
