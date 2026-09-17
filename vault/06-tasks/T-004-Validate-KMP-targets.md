---
id: T-004
type: task
priority: P0
milestone: M0
tags: [task, research, platforms]
---

# T-004 — Validate all four Kotlin targets

[Home](../Home.md) · [Kanban](../Kanban.md) · [Platform matrix](../02-architecture/Platform-matrix.md)

## Outcome

A time-bounded feasibility report for Kotlin/Compose, networking and file export on Android, iOS, desktop and web. Any prototype is explicitly experimental.

## Dependencies

- [T-001](T-001-Planning-vault.md).

## Acceptance criteria

- [ ] Record exact compatible toolchain versions, target OS/browser/CPU coverage, build/run evidence and proposed minimum versions.
- [ ] Demonstrate shared state/networking plus artifact export on each target, including physical iOS behavior and supported desktop OS coverage or explicit gaps.
- [ ] Test browser accessibility/keyboard, Safari/Firefox/Chromium, loading cost, authenticated downloads and bounded-memory large-file export.
- [ ] Document mobile suspension/background transfer, secure storage and sharing limitations.
- [ ] Accept Compose/Wasm or document a Kotlin/JS/web-specific fallback with trade-offs in a new ADR.

## Evidence / notes

Not started. Framework upstream stability labels do not prove application support. This task does not approve store distribution or local yt-dlp execution.
