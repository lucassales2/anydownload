---
id: T-042
type: task
priority: P0
milestone: D2
tags: [task, ios, engine]
---

# T-042 — iOS HTTP download

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 2](../00-project/Phase-2-Local-Kotlin-Engine.md) · [Platform matrix](../02-architecture/Platform-matrix.md)

## Outcome

The iOS host downloads a direct HTTP(S) file into the app sandbox through the shared engine. Non-direct URLs fail clearly. Work is foreground-only.

## Dependencies

- [T-039](T-039-Platform-http-and-files.md).

## Work

- Replace the in-memory fake engine on the iOS host with `HttpDownloadEngine` and Native actuals.
- Write under the sandbox download root. Export/share of the finished file can be the existing share sheet or Files; do not invent a second file tree.
- If the app is suspended, the job is interrupted and must not be marked completed on next launch (reuse D1 restart semantics if the iOS store is still the fake; if you add persistence, match the desktop JSON rules).
- NeedsExtractor → typed error. Do not embed Python or a CLI.

## Acceptance criteria

- [ ] Simulator or device run, or a Native test, downloads a fixture file and shows it in Completed.
- [ ] Cancel leaves no completed file.
- [ ] A site/HTML URL shows the extractor-not-implemented error.
- [ ] Unsigned simulator build still compiles.

## Evidence / notes

Not started. Background URLSession is out of this phase.
