---
id: T-005
type: task
priority: P0
milestone: M0
tags: [task, research, backend]
---

# T-005 — Choose the backend and engine boundary

[Home](../Home.md) · [Kanban](../Kanban.md) · [Architecture](../02-architecture/Architecture.md) · [ADR-003](../03-decisions/ADR-003-Backend-engine.md)

## Outcome

Record where extraction runs. Closed by owner decision: in the app, as a Kotlin port of yt-dlp.

## Dependencies

- [T-001](T-001-Planning-vault.md).

## Acceptance criteria

- [x] Record the chosen approach, rejected alternatives, and owner approval.
- [x] Update architecture, roadmap, and affected tasks.
- [x] Waive the MeTube / Ktor / Python fixture spike. The owner required a local Kotlin port without that comparison.

License inventory for copied extractor code remains [T-006](T-006-Review-security-licensing.md).

## Evidence / notes

Owner waived this spike on 2026-09-21. There is no backend. The engine is a Kotlin port of yt-dlp inside the app. See [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md). The fixture comparison in the acceptance criteria was not run. License inventory remains [T-006](T-006-Review-security-licensing.md).
