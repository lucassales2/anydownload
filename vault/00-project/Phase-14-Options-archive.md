---
type: phase
status: planned
milestone: D14
tags: [project, engine, options]
---

# Phase 14 — Options, names, archive, network

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Planned. 8 engineer-days.** Depends on D13.

## Done looks like

Typed presets overlay in order, and the options for one attempt are immutable. The output-template language (`prepare_filename`) is confined to the download root; the D8 safe-title name stays the default. A download-archive file sits beside app history (E-25). Proxy, rate limit, and sleep exist (E-22). A per-job free-form proxy string is rejected (Q-09). Settings show the pinned tag and the Kotlin build. There is no self-update (E-26).

## Task

[T-017](../06-tasks/T-017-Options-and-presets.md).
