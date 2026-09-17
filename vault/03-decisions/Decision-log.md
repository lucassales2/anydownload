---
type: decision-index
tags: [architecture, decisions, index]
---

# Decision log

[Home](../Home.md) · [Architecture](../02-architecture/Architecture.md) · [Open questions](../00-project/Open-questions.md)

**No architecture proposal has been approved yet.** Creating these records is planning work, not owner sign-off.

| ADR | Topic | Status | Decision gate |
| --- | --- | --- | --- |
| [ADR-001](ADR-001-Execution-model.md) | Remote-first execution, optional local engines | Proposed | Scope approval and target/backend spikes: T-003–T-005 |
| [ADR-002](ADR-002-Kotlin-wrapper.md) | Do not treat archived YtDlp-kt as a shared KMP engine | Proposed | Backend/dependency/license review: T-005–T-006 |
| [ADR-003](ADR-003-Backend-engine.md) | Compare MeTube integration, Ktor + worker, and Python service | Proposed | Evidence-based backend selection: T-005 |

Next records should capture UI/web fallback and toolchain, auth/storage policy, project/dependency license, deployment packaging, and any approved local engine. Use the [ADR template](../99-templates/ADR-template.md).

## Lifecycle

**Proposed → Accepted / Rejected → Superseded when replaced.** Record approver, date, evidence and consequences when a decision is accepted. Do not edit old reasoning to make it appear a choice was always obvious.
