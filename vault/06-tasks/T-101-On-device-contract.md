---
id: T-101
type: task
priority: P0
milestone: D8
tags: [task, design, queue]
---

# T-101 — Write the on-device contract

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 8](../00-project/Phase-8-On-device-core.md) · [ADR-012](../03-decisions/ADR-012-On-device-core-phase.md)

## Outcome

The M0 contract card matches the app that already exists. No new screens. [T-007](T-007-Define-UX-and-contract.md) moves to Done because its remaining criteria are this write-up.

## Dependencies

- [T-100](T-100-Phase-7-verification.md).

## Context the next session needs

Add, Queue, History, Subscriptions, and Settings already ship in `shared/ui` from D1. Job states, attempts, and the three destructive actions already exist on `DownloadEngine`. The API outline is a withdrawn server sketch. User flows are the interaction note. Accessibility beyond what those screens already expose stays in T-020.

## Work

- Update [Download lifecycle](../02-architecture/Download-lifecycle.md) so the transition list matches `JobState` and the D8 restart rule: active states become `FAILED` / `ENGINE_UNAVAILABLE` on load, retryable, with no auto-resume. `PENDING` and `SCHEDULED` survive. Name cancel, remove history, and delete artifacts as different operations.
- In T-007, check the acceptance boxes and point Evidence at the existing screens plus this lifecycle note. State that a full accessibility pass remains T-020.
- Do not draw new wireframes and do not change UI code.

## Acceptance criteria

- [x] The lifecycle note states the restart rule, the three destructive actions, and that there is no login and no HTTP job API.
- [x] T-007's acceptance boxes are checked and its Evidence names the screens and the date.
- [x] T-007's Kanban card is in Done.

## Evidence / notes

2026-09-29.

- [Download lifecycle](../02-architecture/Download-lifecycle.md) now carries the contract from code: a `JobState` table with wire names and `isActive`/`isTerminal`, the full transition table (`submit`/`start`/`cancel`/`retry` plus worker and failure edges), the D8 restart-on-load table (`RESOLVING`/`QUEUED`/`DOWNLOADING`/`POSTPROCESSING` → `FAILED` with `ENGINE_UNAVAILABLE`, retryable, no auto-resume; `PENDING`/`SCHEDULED` survive; `COMPLETED`/`FAILED`/`CANCELLED` unchanged; an unknown wire name becomes `UNKNOWN` and stays visible), a destructive-actions table that keeps `cancel`, `removeHistory`, and `deleteArtifacts` apart, and a “No login and no HTTP job API” section.
- [T-007](T-007-Define-UX-and-contract.md) has all four acceptance boxes checked and Evidence dated 2026-09-29 that names the `shared/ui` Add, Queue, History, Subscriptions, and Settings screens, the `AppShell`/`EmptyStatePanel` states, and the `DownloadEngine` operations. A full accessibility pass is left to [T-020](T-020-Sharing-and-UX.md). The Kanban card was moved to Done.
- Read to keep the note true, no code changes: `anydownload`, `domain/DownloadJob.kt`, `fake/InMemoryDownloadEngine.kt`, `engine/HttpDownloadEngine.kt`, and `anydownload` (`isInterruptedOnStartup`, `interrupt`, `dropExpired`). No test or source file changed in this task.
