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

Not started. Framework upstream stability labels do not prove application support. After ADR-004 this task must show a **local** download on iOS, Compose/Wasm, Android, and desktop. Store distribution is out of scope. A JVM process wrapper around Python yt-dlp does not pass this task. [Client yt-dlp options](../05-research/Client-yt-dlp-options.md) records the expected web cross-origin limit and the rejected Python packagers; a run on this task confirms or revises that, and the note alone does not pass the task.
