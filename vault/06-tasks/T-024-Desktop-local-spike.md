---
id: T-024
type: task
priority: P2
milestone: M5
tags: [task, research, desktop]
---

# T-024 — Investigate an optional desktop local engine

[Home](../Home.md) · [Kanban](../Kanban.md) · [Platform matrix](../02-architecture/Platform-matrix.md)

## Outcome

Folded into the product. Desktop local execution is required, not optional. Feasibility is [T-004](T-004-Validate-KMP-targets.md) and the engine work is [T-009](T-009-Backend-vertical-slice.md).

## Dependencies

- [T-005](T-005-Choose-backend-engine.md).
- [T-006](T-006-Review-security-licensing.md).
- [T-010](T-010-Remote-vertical-slice.md).

## Acceptance criteria

- [x] Record that this spike is no longer a separate track.

## Evidence / notes

Owner, 2026-09-21, [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md). Do not shell out to Python yt-dlp. YtDlp-kt remains rejected as the shared engine.
