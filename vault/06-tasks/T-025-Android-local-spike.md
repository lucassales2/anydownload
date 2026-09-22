---
id: T-025
type: task
priority: P2
milestone: M5
tags: [task, research, android]
---

# T-025 — Investigate an optional Android local engine

[Home](../Home.md) · [Kanban](../Kanban.md) · [Platform matrix](../02-architecture/Platform-matrix.md)

## Outcome

Folded into the product. Android uses the same in-app Kotlin engine. Feasibility is [T-004](T-004-Validate-KMP-targets.md).

## Dependencies

- [T-004](T-004-Validate-KMP-targets.md).
- [T-005](T-005-Choose-backend-engine.md).
- [T-006](T-006-Review-security-licensing.md).
- [T-010](T-010-Remote-vertical-slice.md).

## Acceptance criteria

- [x] Record that this spike is no longer a separate track.

## Evidence / notes

Owner, 2026-09-21, [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md). The engine is shared Kotlin, including on Android. Store policy is out of scope.
