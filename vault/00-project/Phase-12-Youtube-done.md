---
type: phase
status: planned
milestone: D12
tags: [project, engine, youtube]
---

# Phase 12 — YouTube done at the pin

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Planned. 15 engineer-days.** Depends on D11. The YouTube package at the pin is 36 files and 541 KB. D4 and D8 ported single video (visionos and web) and a playlist first page capped at 50.

## Done looks like

The 21 `Youtube*` classes in `port/upstream-extractors.json` are accounted for in the manifest at pin `2026.08.19`. Remaining innertube clients the pin uses, playlist continuations, channels, mixes, and `--playlist-items` are translated. PO tokens have a provider interface (E-13); formats that need a token stay dropped until a provider is configured. Subtitle tracks and chapter metadata sit on the info dict. File download of those tracks is D13. Live YouTube and live HLS playlists are in (E-21). The playlist cap of 50 stays the default; a higher cap requires an explicit user limit.

Harness cases cover the URL forms upstream tests at this pin. The desktop oracle still diffs normalized fields only. iOS live network may stay blocked in the simulator; the fixture path is the evidence, as in [T-074](../06-tasks/T-074-Phase-4-verification.md).

## Task

[T-124](../06-tasks/T-124-Youtube-done.md).
