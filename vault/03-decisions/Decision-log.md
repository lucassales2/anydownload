---
type: decision-index
tags: [architecture, decisions, index]
---

# Decision log

[Home](../Home.md) · [Architecture](../02-architecture/Architecture.md) · [Open questions](../00-project/Open-questions.md)

**ADR-004 is accepted.** Earlier remote-server proposals are superseded. ADR-002 is still a proposal: YtDlp-kt remains a JVM CLI wrapper, not the Kotlin port.

| ADR | Topic | Status | Decision gate |
| --- | --- | --- | --- |
| [ADR-001](ADR-001-Execution-model.md) | Remote-first execution, optional local engines | Superseded by ADR-004 | Owner rejected a required server on 2026-09-21 |
| [ADR-002](ADR-002-Kotlin-wrapper.md) | Do not treat archived YtDlp-kt as a shared KMP engine | Proposed | Still open for the license/maintenance note in T-006. The wrapper cannot satisfy ADR-004. |
| [ADR-003](ADR-003-Backend-engine.md) | Compare MeTube integration, Ktor + worker, and Python service | Superseded by ADR-004 | Owner chose no backend on 2026-09-21. The fixture spike was not run. |
| [ADR-004](ADR-004-Local-kotlin-engine.md) | Local Kotlin port of yt-dlp; no backend; no app login | Accepted | Owner, 2026-09-21. Target feasibility is still T-004. |

The project license is MIT as of 2026-09-21. Next records should capture the per-target media toolkit (FFmpeg or equivalent), the web/iOS feasibility result, and dependency licenses for any copied extractor code. Use the [ADR template](../99-templates/ADR-template.md).

## Lifecycle

**Proposed → Accepted / Rejected → Superseded when replaced.** Record approver, date, evidence and consequences when a decision is accepted. Do not edit old reasoning to make it appear a choice was always obvious.
