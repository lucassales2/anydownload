---
id: T-011
type: task
priority: P0
milestone: M2
tags: [task, queue, reliability]
---

# T-011 — Durable queue, recovery and live events

[Home](../Home.md) · [Kanban](../Kanban.md) · [Lifecycle](../02-architecture/Download-lifecycle.md)

## Outcome

Reliable F-06–F-10 queue, attempts, scheduling/waiting and progress semantics.

## Dependencies

- [T-010](T-010-Remote-vertical-slice.md).

## Acceptance criteria

- [x] Persist pending/queued/active/terminal states, attempts/options and concurrency limits; implement individual/bulk start, cancel and failed retry.
- [x] Define complete transitions, idempotency, process-tree cancellation and deterministic cancel/complete races.
- [x] Recover interrupted jobs after the app restarts without duplicating finished files.
- [x] A source that is not available yet becomes `SCHEDULED` and downloads nothing until the user starts it. No countdown, no timer, and no live stream. Do not promise arbitrary calendar scheduling or universal pause/resume.
- [ ] Test unknown progress, disk/worker failure, retry limits and safely resumable partial files where supported.

## Evidence / notes

Not started as this card. Desktop already restores jobs and marks active ones retryable. Android, iOS, and web do not. [T-102](T-102-Shared-job-document.md) through [T-104](T-104-Worker-cancel-retry.md) and [T-112](T-112-Phase-8-verification.md) in [Phase 8](../00-project/Phase-8-On-device-core.md) implement the shared queue. The upcoming-source countdown is replaced by `SCHEDULED` with no timer. Byte-level resume is engine/source-dependent, not synonymous with retry.

### Closed by Phase 8 (2026-09-29)

- States/attempts/options/concurrency persist through `JobDocumentStore` (T-102) on every host (T-103); `maxConcurrentDownloads` is read when a worker starts (T-104); individual and bulk start/cancel/retry are in `QueuePresenter`/`HistoryPresenter` with unknown-id skipping.
- Transitions and idempotency are written in [Download lifecycle](../02-architecture/Download-lifecycle.md); cancel versus complete has one persisted winner (T-104). Desktop CLI cancellation keeps its process-tree kill from D1; shared work cancels the coroutine and discards the temp.
- Restart: active rows reload `FAILED`/`ENGINE_UNAVAILABLE`, retryable, with no auto-resume and no second copy of a completed file (`JobDocumentStoreTest`, `DesktopHttpRestartGateTest`, `AndroidM1GateTest`, `IosM1GateTest`, `WebM1GateTest`).
- Not-yet-available becomes `SCHEDULED` with no media GET until `start` (`EngineExtractionTest.notYetAvailableSchedulesAndWaitsForStartWithoutAMediaGet`, `WebExtensionEngineTest`); the countdown is cut.
- The remaining bullet stays open for M3: unknown progress (`progressPercentIsNullWhenContentLengthIsUnknown`), disk failure (`aHostWriteFailureIsDiskExhaustedAndDiscardsTheTemp`), and worker failure/cancel are tested, but D8 has no automatic retry-limit policy (retry is manual) and partial byte resume stays best-effort rather than a universal feature.
