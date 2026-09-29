---
id: T-130
type: task
priority: P1
milestone: D21
tags: [task, engine, extractors]
---

# T-130 — Small extractors, second half

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 21](../00-project/Phase-21-Small-extractors-b.md) · [Schedule](../00-project/Full-engine-schedule.md)

## Outcome

The remaining 339 extractor files under 8 KB are accounted for. Estimate 85 engineer-days. After this task the catalog outside the D22 cutover is scheduled to be complete at the pin.

## Dependencies

- [T-129](T-129-Small-extractors-a.md).

## Acceptance criteria

- [ ] The file list is written into Evidence at the start and does not repeat D20.
- [ ] Each file has harness cases and a manifest row. A login wall or DRM wall is Partial with that reason.
- [ ] `:tools:port-manifest:check` passes and the coverage count accounts for every class named in `port/upstream-extractors.json`.

## Evidence / notes

Not started. Scheduled 2026-09-29 by [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md).
