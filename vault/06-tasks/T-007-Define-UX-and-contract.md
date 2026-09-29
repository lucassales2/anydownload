---
id: T-007
type: task
priority: P1
milestone: M0
tags: [task, design, api]
---

# T-007 — Define UX and review the contract

[Home](../Home.md) · [Kanban](../Kanban.md) · [User flows](../01-product/User-flows.md) · [API outline](../02-architecture/API-outline.md)

## Outcome

Local Add, Queue, History, Subscriptions, and Settings flows for a no-login app.

## Dependencies

- [T-003](T-003-Approve-product-scope.md).
- [T-004](T-004-Validate-KMP-targets.md).
- [T-005](T-005-Choose-backend-engine.md).
- [T-006](T-006-Review-security-licensing.md) for the option and cookie rules that appear in Settings.

## Acceptance criteria

- [x] Wireframe Add, Queue, History, Subscriptions, Settings, and error/offline states at mobile and desktop sizes.
- [x] Separate cancel, remove history, and delete file; review accessibility.
- [x] Specify on-device job states, typed options, retries, and errors. No login and no HTTP API.
- [x] Confirm the flows match ADR-004.

## Evidence / notes

2026-09-29, closed by [T-101](T-101-On-device-contract.md).

- The screens ship in `shared/ui` from D1: `add/AddForm.kt`, `queue/QueueScreen.kt`, `history/HistoryScreen.kt`, `subscriptions/SubscriptionsScreen.kt`, `settings/SettingsScreen.kt`, plus `shell/AppShell.kt` and `shell/EmptyStatePanel.kt` for the shell and error/offline empty states. The same Compose code runs at mobile and desktop sizes; desktop sizes are exercised by the `shell/AppShell.kt` layout and the `ShellUiHarness` tests (`shared/ui/src/jvmTest/.../AppShellTest.kt`).
- `DownloadEngine` names the three operations apart: `cancel` (stops work, keeps a finished file), `removeHistory` (drops the row, keeps the file), and `deleteArtifacts` (deletes files, keeps the row). The contract is written in [Download lifecycle](../02-architecture/Download-lifecycle.md#destructive-actions-are-separate). A full keyboard/screen-reader/large-text accessibility pass stays [T-020](T-020-Sharing-and-UX.md).
- Job states, attempts, retry, typed options, and error codes are specified in [Download lifecycle](../02-architecture/Download-lifecycle.md). There is no login, no account, and no HTTP job API; the [API outline](../02-architecture/API-outline.md) stays withdrawn.
- The flows match accepted [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md): local-only execution on iOS, web via Compose/Wasm, Android, and desktop, with no required backend. The [user flows](../01-product/User-flows.md) remain the interaction reference.
- Repo check: the notes above are the same entities used by `shared/core/src/commonMain/kotlin/com/anydownlod/core/domain/DownloadJob.kt` (`JobState`, `JobAttempt`, `JobError`, `Artifact`) and by the D1 screens; no UI code was changed by T-101.

Desktop implementation of Add, Queue, History, Subscriptions, and Settings is Phase D1 ([phase note](../00-project/Phase-1-Desktop-MeTube.md), T-026–T-036). That phase does not close this task. [T-101](T-101-On-device-contract.md) in [Phase 8](../00-project/Phase-8-On-device-core.md) writes the contract and closes this card. The accessibility pass stays [T-020](T-020-Sharing-and-UX.md).
