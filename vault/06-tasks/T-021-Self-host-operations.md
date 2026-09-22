---
id: T-021
type: task
priority: P1
milestone: M3
tags: [task, operations, self-hosting]
---

# T-021 — Self-host configuration and engine operations

[Home](../Home.md) · [Kanban](../Kanban.md) · [Security](../04-delivery/Security-and-licensing.md)

## Outcome

Withdrawn. ADR-004 removed the self-host server. F-24 and F-25 are out of scope. Local path and log safety moved to T-009 and T-014.

## Dependencies

- [T-009](T-009-Backend-vertical-slice.md).
- [T-011](T-011-Durable-queue.md).
- [T-017](T-017-Options-and-presets.md).

## Acceptance criteria

- [x] Record the withdrawal. Do not package a server.

## Evidence / notes

Owner, 2026-09-21: the app does not require a backend. See [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md).
