---
id: T-024
type: task
priority: P2
milestone: M5
tags: [task, research, desktop]
---

# T-024 — Investigate an optional desktop local engine

[Home](../Home.md) · [Kanban](../Kanban.md) · [Platform matrix](../02-architecture/Platform-matrix.md)

## Outcome

A go/no-go ADR for standalone desktop execution, separate from remote-mode parity.

## Dependencies

- [T-005](T-005-Choose-backend-engine.md).
- [T-006](T-006-Review-security-licensing.md).
- [T-010](T-010-Remote-vertical-slice.md).

## Acceptance criteria

- [ ] Test owned typed CLI/worker adapter on approved Windows/macOS/Linux/CPU variants with complete runtime dependencies.
- [ ] Prove safe argument handling, Unicode/spaced paths, progress, process-tree cancellation and resource cleanup.
- [ ] Assess installation size, signing/notarization, engine updates/rollback, dependency licenses and secret storage.
- [ ] Map capability differences and local/remote state/storage UX; record costs, evidence and owner decision before implementation backlog expansion.

## Evidence / notes

Not started; optional and non-blocking for remote release. Archived YtDlp-kt is not automatically selected.
