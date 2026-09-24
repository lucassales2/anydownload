---
id: T-039
type: task
priority: P0
milestone: D2
tags: [task, kmp, platforms]
---

# T-039 — Platform HTTP and file adapters

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 2](../00-project/Phase-2-Local-Kotlin-Engine.md) · [Platform matrix](../02-architecture/Platform-matrix.md)

## Outcome

expect/actual (or injected ports) for HTTP GET/HEAD with redirect policy and for streaming file create/rename/delete, so `HttpDownloadEngine` can run on JVM, Android, iOS, and the web host without calling browser-forbidden origins itself.

## Dependencies

- [T-038](T-038-Shared-http-engine.md).

## Work

- Add small ports in `com.anydownlod.core.platform`: HTTP request/response stream, and a `FileStore` scoped to the download root.
- JVM/Android/iOS actuals perform the fetch in-process. The web actual must **not** fetch arbitrary origins; it either no-ops with a clear `ExtensionRequired` error or talks to the extension bridge stub that T-043 will fill.
- Normalize paths. Reject traversal and paths outside the download root.
- Stream writes. Prove with a generated payload large enough that a whole-file buffer would be obvious in a test (for example 8–16 MiB on JVM).
- Keep Ktor (or the existing HTTP stack) as an implementation detail of the actuals, not of the UI.

## Acceptance criteria

- [ ] JVM tests write, cancel, and delete under a temp root.
- [ ] Android and iOS actuals compile. A missing SDK is recorded, not treated as a D2 design failure.
- [ ] Web actual cannot silently fetch a third-party URL.
- [ ] Common code still has no `ProcessBuilder`.

## Evidence / notes

Not started.
