---
id: T-123
type: task
priority: P0
milestone: D10
tags: [task, engine, download]
---

# T-123 — Finish the shared downloader

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 10](../00-project/Phase-10-Shared-downloader.md) · [Schedule](../00-project/Full-engine-schedule.md)

## Outcome

E-05, E-07, E-10, and the generic extractor grow to the scope in Phase 10. Estimate 8 engineer-days. No new site.

## Dependencies

- [T-120](T-120-Metro-verification.md). D9 Metro finishes first.

## Acceptance criteria

- [ ] Format selection handles `,` lists, `all` / `mergeall`, and more than one simultaneous stream, with tests.
- [ ] HTTP download resumes inside one attempt. A failed attempt still does not auto-resume on the next launch.
- [ ] Fragment download can run a bounded number of fragments at once and skip an unavailable fragment.
- [ ] `GenericIE` covers embeds, iframes, JSON-LD, meta refresh, and HLS/DASH discovery. The manifest scope names what is still out.
- [ ] Each newly translated `common.py` / `_utils.py` helper is named in `port/manifest.json`, and `:tools:port-manifest:check` passes.
- [ ] Shared tests pass on JVM, wasm, and the iOS simulator.

## Evidence / notes

Not started. Scheduled 2026-09-29 by [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md).
