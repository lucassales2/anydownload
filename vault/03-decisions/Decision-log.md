---
type: decision-index
tags: [architecture, decisions, index]
---

# Decision log

[Home](../Home.md) · [Architecture](../02-architecture/Architecture.md) · [Open questions](../00-project/Open-questions.md)

**ADR-004 is accepted** as the end state. **ADR-005 is accepted** as Phase D1 (done): desktop Compose UI via installed yt-dlp. **ADR-006 is accepted** as Phase D2: shared HTTP-only engine on all four hosts, web via extension, Android Chaquopy interim. Earlier remote-server proposals are superseded. ADR-002 is still a proposal: YtDlp-kt remains a JVM CLI wrapper, not the Kotlin port.

| ADR | Topic | Status | Decision gate |
| --- | --- | --- | --- |
| [ADR-001](ADR-001-Execution-model.md) | Remote-first execution, optional local engines | Superseded by ADR-004 | Owner rejected a required server on 2026-09-21 |
| [ADR-002](ADR-002-Kotlin-wrapper.md) | Do not treat archived YtDlp-kt as a shared KMP engine | Proposed | Still open for the license/maintenance note in T-006. The wrapper cannot satisfy ADR-004. |
| [ADR-003](ADR-003-Backend-engine.md) | Compare MeTube integration, Ktor + worker, and Python service | Superseded by ADR-004 | Owner chose no backend on 2026-09-21. The fixture spike was not run. |
| [ADR-004](ADR-004-Local-kotlin-engine.md) | Local Kotlin port of yt-dlp; no backend; no app login | Accepted | Owner, 2026-09-21. Target feasibility is still T-004. |
| [ADR-005](ADR-005-Desktop-metube-phase.md) | Desktop MeTube UI first, using installed yt-dlp; Kotlin port after | Accepted | Owner, 2026-09-21. Does not supersede ADR-004. Tasks T-026–T-036. Done. |
| [ADR-006](ADR-006-Local-http-engine-phase.md) | HTTP-only shared engine first; web extension; Android Chaquopy; allowlist options | Accepted | Owner, 2026-09-23. Does not supersede ADR-004 or ADR-005. Tasks T-006, T-003, T-038–T-044. |

The project license is MIT as of 2026-09-21. A 2026-09-23 comparison of Python packagers and the Kotlin port is in [Client yt-dlp options](../05-research/Client-yt-dlp-options.md). It does not replace ADR-004. Owner answers the same day: Unlicense yt-dlp may be translated with notices; MeTube/NewPipe/YtDlp-kt must not be copied; Q-09 is allowlist. Next records after D2 should capture the per-target media toolkit and the first extractor translation. Use the [ADR template](../99-templates/ADR-template.md).

## Lifecycle

**Proposed → Accepted / Rejected → Superseded when replaced.** Record approver, date, evidence and consequences when a decision is accepted. Do not edit old reasoning to make it appear a choice was always obvious.
