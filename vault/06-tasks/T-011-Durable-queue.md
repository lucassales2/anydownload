---
id: T-011
type: task
priority: P0
milestone: M2
tags: [task, backend, reliability]
---

# T-011 — Durable queue, recovery and live events

[Home](../Home.md) · [Kanban](../Kanban.md) · [Lifecycle](../02-architecture/Download-lifecycle.md)

## Outcome

Reliable F-06–F-10 queue, attempts, scheduling/waiting and progress semantics.

## Dependencies

- [T-010](T-010-Remote-vertical-slice.md).

## Acceptance criteria

- [ ] Persist pending/queued/active/terminal states, attempts/options and concurrency limits; implement individual/bulk start, cancel and failed retry.
- [ ] Define complete transitions, idempotency, process-tree cancellation and deterministic cancel/complete races.
- [ ] Recover interrupted jobs after server restart; reconcile missed/duplicate/out-of-order events from snapshot/revision without duplicate work.
- [ ] Reproduce supported upcoming-source waiting/countdown behavior and bounded rechecks; do not promise arbitrary calendar scheduling or universal pause/resume.
- [ ] Test unknown progress, disk/worker failure, retry limits and safely resumable partial files where supported.

## Evidence / notes

Not started. Byte-level resume is engine/source-dependent, not synonymous with retry.
