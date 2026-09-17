---
id: T-008
type: task
priority: P0
milestone: M1
tags: [task, clients, kmp]
---

# T-008 — Scaffold shared Kotlin clients

[Home](../Home.md) · [Kanban](../Kanban.md) · [Architecture](../02-architecture/Architecture.md)

## Outcome

The first production-oriented client skeleton, only after M0 approval.

## Dependencies

- [T-007](T-007-Define-UX-and-contract.md), including the M0 exit gate.

## Acceptance criteria

- [ ] Pin approved toolchain/dependency versions and create Android/iOS/desktop/web entry points plus shared domain/networking and approved UI modules.
- [ ] Add server configuration, platform credential/file adapters and capability-aware state flow; no JVM-only process APIs in common code.
- [ ] Provide repeatable build/run instructions and CI/build evidence for each supported target with explicit signing limitations.
- [ ] Add initial shared serialization/state tests and license/dependency inventory from the approved design.

## Evidence / notes

Not started. This task is deliberately not part of the initial planning setup.
