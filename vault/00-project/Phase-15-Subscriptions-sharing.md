---
type: phase
status: planned
milestone: D15
tags: [project, engine, product]
---

# Phase 15 — Subscriptions and sharing

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Planned. 6 engineer-days.** Depends on D14. Cumulative raw time through this phase: 53 days, about 11 weeks. YouTube, cookies, postprocessing, and the MeTube workflows run in Kotlin. Every other site still uses the desktop CLI or Android Chaquopy.

## Done looks like

Channel and playlist subscriptions scan while the app is open, with a seen-id cap and an explicit initial-backlog policy. iOS and the browser say that suspension stops a scan. Share entry points are Android share, the iOS share sheet, desktop paste, and the existing web extension. F-14 through F-23 have fixture evidence. F-20 stays the allowlist difference. F-24 and F-25 stay withdrawn.

## Tasks

[T-019](../06-tasks/T-019-Subscriptions.md) and [T-020](../06-tasks/T-020-Sharing-and-UX.md).
