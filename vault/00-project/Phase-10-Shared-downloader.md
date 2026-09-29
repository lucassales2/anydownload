---
type: phase
status: planned
milestone: D10
tags: [project, engine, download]
---

# Phase 10 — Shared downloader

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Planned. 8 engineer-days.** Starts after [T-120](../06-tasks/T-120-Metro-verification.md) is Done. No new site. This phase closes the partial core rows later extractors call.

## Done looks like

Format selection accepts `,` lists, `all`, and `mergeall`, and can select more than one simultaneous stream. An HTTP download can resume inside one attempt. Fragment downloads can run concurrently and skip an unavailable fragment. `GenericIE` finds embeds, iframes, JSON-LD, meta refresh, and HLS/DASH media, not only `<video>`, `<audio>`, and `<source>`. The manifest names each newly translated helper from `common.py` and `_utils.py`.

## Task

[T-123](../06-tasks/T-123-Shared-downloader.md).

## Touch

- [FormatSelector.kt](../../shared/core/src/commonMain/kotlin/com/anydownlod/core/format/FormatSelector.kt) for E-05.
- The HTTP downloader for E-07 resume, left open by [T-056](../06-tasks/T-056-Http-request-port.md).
- [FragmentDownloader.kt](../../shared/core/src/commonMain/kotlin/com/anydownlod/core/download/FragmentDownloader.kt) for E-10.
- [GenericExtractor.kt](../../shared/core/src/commonMain/kotlin/com/anydownlod/core/extract/GenericExtractor.kt). Upstream `generic.py` is 58 KB at the pin. `common.py` is 199 KB; translate the helpers this phase’s extractors and the generic subset call, and name each one in the manifest.
