---
id: T-009
type: task
priority: P0
milestone: M1
tags: [task, backend, security]
---

# T-009 — Build the authenticated engine vertical slice

[Home](../Home.md) · [Kanban](../Kanban.md) · [Architecture](../02-architecture/Architecture.md) · [Security](../04-delivery/Security-and-licensing.md)

## Outcome

Chosen backend or MeTube adapter handles a single authorized job securely. Covers foundational F-01/F-07/F-26 behavior.

## Dependencies

- [T-007](T-007-Define-UX-and-contract.md), including the M0 exit gate.

## Acceptance criteria

- [ ] Implement selected auth/capabilities/job contract, minimal durable acceptance and progress/result/error delivery.
- [ ] Package the tested engine/runtime set and stream an authorized finalized artifact; no secret or raw storage-path disclosure.
- [ ] Enforce safe URL/options, worker egress, path isolation, bounded work and authorization on jobs/events/files from day one.
- [ ] Demonstrate extraction, FFmpeg output, cancellation/failure and denied unauthenticated/unauthorized requests with integration tests.

## Evidence / notes

Not started. Do not implement both a MeTube integration and a new backend without a revised decision.
