---
id: T-007
type: task
priority: P1
milestone: M0
tags: [task, design, api]
---

# T-007 — Define UX and review the contract

[Home](../Home.md) · [Kanban](../Kanban.md) · [User flows](../01-product/User-flows.md) · [API outline](../02-architecture/API-outline.md)

## Outcome

Local Add, Queue, History, Subscriptions, and Settings flows for a no-login app.

## Dependencies

- [T-003](T-003-Approve-product-scope.md).
- [T-004](T-004-Validate-KMP-targets.md).
- [T-005](T-005-Choose-backend-engine.md).
- [T-006](T-006-Review-security-licensing.md) for the option and cookie rules that appear in Settings.

## Acceptance criteria

- [ ] Wireframe Add, Queue, History, Subscriptions, Settings, and error/offline states at mobile and desktop sizes.
- [ ] Separate cancel, remove history, and delete file; review accessibility.
- [ ] Specify on-device job states, typed options, retries, and errors. No login and no HTTP API.
- [ ] Confirm the flows match ADR-004.

## Evidence / notes

Not started. The API outline is a withdrawn server sketch. Use [user flows](../01-product/User-flows.md).

Desktop implementation of Add, Queue, History, Subscriptions, and Settings is Phase D1 ([phase note](../00-project/Phase-1-Desktop-MeTube.md), T-026–T-036). That phase does not close this task: mobile wireframes, the full accessibility pass, and the written on-device contract review are still open here.
