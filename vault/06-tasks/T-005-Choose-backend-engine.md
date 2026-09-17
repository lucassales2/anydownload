---
id: T-005
type: task
priority: P0
milestone: M0
tags: [task, research, backend]
---

# T-005 — Choose the backend and engine boundary

[Home](../Home.md) · [Kanban](../Kanban.md) · [Architecture](../02-architecture/Architecture.md) · [ADR-003](../03-decisions/ADR-003-Backend-engine.md)

## Outcome

Evidence-based choice between a MeTube adapter, Ktor + isolated Python worker, and a smaller Python service; assess direct CLI versus Python hooks where relevant.

## Dependencies

- [T-001](T-001-Planning-vault.md).

## Acceptance criteria

- [ ] Compare the same authorized single-video/audio/playlist/error fixtures, progress, child-process cancellation, retries and restart behavior.
- [ ] Evaluate Kotlin/native/browser transport and auth/artifact delivery; explicitly test Socket.IO compatibility if using MeTube.
- [ ] Inventory yt-dlp, Python, FFmpeg/ffprobe, EJS/JS runtime and optional dependencies, with update/packaging implications.
- [ ] Estimate relative parity implementation/maintenance cost, queue/persistence design and Linux amd64/arm64 feasibility without fabricated deadlines.
- [ ] Record chosen approach, rejected alternatives, evidence and owner approval in ADR-002/ADR-003; update architecture/API/tasks.

## Evidence / notes

Not started. No dependency has been adopted. Align license/security findings with T-006 before M0 sign-off.
