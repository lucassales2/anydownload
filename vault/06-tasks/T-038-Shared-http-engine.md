---
id: T-038
type: task
priority: P0
milestone: D2
tags: [task, engine, kmp]
---

# T-038 — Shared HTTP download engine

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 2](../00-project/Phase-2-Local-Kotlin-Engine.md) · [ADR-006](../03-decisions/ADR-006-Local-http-engine-phase.md)

## Outcome

A shared `DownloadEngine` implementation that streams one direct HTTP(S) file to disk. No HTML extraction. No process. No Python.

## Dependencies

- [T-006](T-006-Review-security-licensing.md) must have recorded that this slice copies no upstream extractor source.

## Context the next session needs

`DownloadEngine` already exists in `shared/core`. Desktop uses `YtDlpCliEngine`. Other hosts use `InMemoryDownloadEngine`. Keep that interface. Add a real implementation that hosts can construct with platform HTTP and file ports.

Do not create a new Gradle module. Do not reference `ProcessBuilder` or Chaquopy here.

## Work

- Classify a URL as `DirectFile` or `NeedsExtractor`. Direct means the response is a downloadable body (not `text/html`). Classification may use a HEAD or the GET Content-Type. Unknown HTML is `NeedsExtractor`, not a successful download.
- Reject non-HTTP(S), userinfo, and loopback/link-local/reserved destinations on every redirect. Reuse and extend `SourceUrlValidator` rather than inventing a second URL type.
- Stream the body to a temp file under the download root, then rename onto the artifact path. Do not hold the whole file in memory.
- Honor `StartPolicy`, idempotency keys, cancel, retry, `removeHistory`, and `deleteArtifacts` as the interface already specifies.
- Progress: bytes and optional Content-Length percent. Speed and ETA stay null unless measured. Unknown stays unknown.
- If the URL needs an extractor, fail with a typed, redacted error the UI can map. Do not call yt-dlp from this class.
- Custom yt-dlp JSON on the request is ignored.

## Acceptance criteria

- [ ] Unit tests cover success, cancel mid-stream, HTTP error, redirect to a blocked address, HTML Content-Type treated as NeedsExtractor, and idempotent double submit.
- [ ] Tests use a local mock server or in-memory dispatcher. No live third-party hosts in default CI.
- [ ] Common code still has no process or Python API.
- [ ] Evidence lists commands and test names.

## Evidence / notes

Not started.
