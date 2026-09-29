---
id: T-023
type: task
priority: P0
milestone: D22
tags: [task, release, testing]
---

# T-023 — Portfolio builds

[Home](../Home.md) · [Kanban](../Kanban.md) · [Roadmap](../00-project/Roadmap.md) · [Testing](../04-delivery/Testing-strategy.md)

## Outcome

Repeatable portfolio builds for iOS, Compose/Wasm, Android, and desktop, with license notices and honest support notes. Store submission is out of scope.

## Dependencies

- [T-022](T-022-Parity-audit.md), plus the approved T-004/T-006 platform/distribution decisions. Phase: [Phase 22](../00-project/Phase-22-Remove-ytdlp-fallback.md). M4 closes here.

## Acceptance criteria

- [ ] Document how to build and run Android, iOS, desktop, and Compose/Wasm from a clean checkout.
- [ ] Publish tested OS/browser coverage and known limitations, including suspension and browser save limits.
- [ ] Include the project license and notices for ported or bundled code.
- [ ] Publish user notes for download, save, cookies, and retention.

## Evidence / notes

Not started. Scheduled 2026-09-29 as the last D22 card in [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md). A successful local build is the exit gate. App Store and Play submission are not.
