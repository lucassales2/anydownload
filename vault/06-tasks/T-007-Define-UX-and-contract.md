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

Reviewed wireframes, domain/API contract or MeTube adapter mapping, and explicit M0 approval before application scaffolding.

## Dependencies

- [T-003](T-003-Approve-product-scope.md).
- [T-004](T-004-Validate-KMP-targets.md).
- [T-005](T-005-Choose-backend-engine.md).
- [T-006](T-006-Review-security-licensing.md).

## Acceptance criteria

- [ ] Wireframe Add, Queue, History/artifact export, Subscriptions, Settings and error/offline/permission states at mobile and desktop sizes.
- [ ] Separate server job completion from device transfer and destructive actions; review accessibility.
- [ ] Specify auth, capabilities, typed options, attempts/states, idempotency, events/reconnect, file delivery and errors in the chosen contract.
- [ ] Record approved ADRs, scope, target/toolchain and security/license/distribution decisions; owner explicitly accepts the M0 exit gate.

## Evidence / notes

Not started. The current API outline is illustrative, not an implemented or approved API.
