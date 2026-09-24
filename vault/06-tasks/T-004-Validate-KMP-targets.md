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

Show that one local download can finish on iOS, Compose/Wasm, Android, and desktop, or record the target as blocked.

## Dependencies

- [T-001](T-001-Planning-vault.md).

## Acceptance criteria

- [ ] Record exact toolchain versions, OS/browser/CPU coverage, build/run evidence, and proposed minimum versions.
- [ ] Download one public URL to a device file on Android, iOS, desktop, and Compose/Wasm, or document the blocker. Name which desktop OS was actually run.
- [ ] On the web, try Safari, Firefox, and Chromium, including a cross-origin media fetch and a large file written without holding it all in memory.
- [ ] Document iOS suspension, browser tab close, and file-save limits.
- [ ] Accept Compose/Wasm for the engine or document a Kotlin/JS fallback in a new ADR.

## Evidence / notes

Not started as a standalone spike. [ADR-006](../03-decisions/ADR-006-Local-http-engine-phase.md) binds this gate to Phase D2: a **direct HTTP(S) file**, not a yt-dlp extractor. Web evidence must go through the extension (T-043), not an in-page YouTube fetch. A JVM process wrapper around Python yt-dlp still does not pass this task. Close the checkboxes from [T-044](T-044-Phase-2-verification.md) evidence. Do not pick this card up on its own during the D2 loop.
