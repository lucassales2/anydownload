---
type: phase
status: planned
milestone: D11
tags: [project, engine, cookies]
---

# Phase 11 — Cookies

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Planned. 6 engineer-days.** Depends on D10. Pulls [T-018](../06-tasks/T-018-Cookie-lifecycle.md) forward: cookies are an engine prerequisite, not a screen that waits until the rest of M3.

## Done looks like

The user can import, replace, and delete a Netscape cookie file on this device. Jobs store a reference, not the cookie text. Extractor requests attach the jar. Logs, history, fixtures, and this vault contain no cookie contents. Browser-store reading stays out on mobile and web (E-16). Synthetic fixtures only.

## Task

[T-018](../06-tasks/T-018-Cookie-lifecycle.md). The jar attaches inside the shared HTTP helper, not in a desktop-only process argument list.
