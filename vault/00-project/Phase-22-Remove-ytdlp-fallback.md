---
type: phase
status: planned
milestone: D22
tags: [project, engine, release]
---

# Phase 22 — Remove the yt-dlp fallbacks

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Planned. 8 engineer-days**, plus the 80-day rework buffer already spent in D17–D21. Depends on D21. This phase is the end of the 614-day estimate.

## Done looks like

Desktop no longer has a `YTDLP_CLI` route. Android no longer has a `CHAQUOPY` route. An unmatched URL fails typed on every host. The desktop `yt-dlp -J` oracle stays behind its opt-in test flag. [T-022](../06-tasks/T-022-Parity-audit.md) records every equivalence row as Done, Partial with a host note, or Out with a reason, and the catalog count accounts for all 1,751 classes. [T-023](../06-tasks/T-023-Release-readiness.md) documents how to build the four targets and the license notices.

## Tasks

[T-131](../06-tasks/T-131-Remove-ytdlp-fallback.md), then [T-022](../06-tasks/T-022-Parity-audit.md), then [T-023](../06-tasks/T-023-Release-readiness.md).
