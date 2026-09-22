---
type: risk-register
tags: [delivery, risks]
---

# Risk register

[Home](../Home.md) · [Security and licensing](Security-and-licensing.md) · [Testing](Testing-strategy.md) · [Open questions](../00-project/Open-questions.md)

Risks are open until evidence or an accepted decision closes them. Severity is qualitative; no probabilities or schedules have been invented.

| ID | Risk / severity | Mitigation and validation | Owning task |
| --- | --- | --- | --- |
| R-01 | “Any media” expectation cannot be guaranteed. **High** | State installed-engine/site/auth limits; capability/error UX; opt-in live smoke suite and supported-scenario documentation. | [T-003](../06-tasks/T-003-Approve-product-scope.md), [T-022](../06-tasks/T-022-Parity-audit.md) |
| R-02 | iOS and Compose/Wasm cannot complete a local download. **High** | Four-target spike with an in-app engine. Archived JVM CLI wrapper stays out of common code. | [T-004](../06-tasks/T-004-Validate-KMP-targets.md) |
| R-03 | Browser UI/dependency/accessibility limitations. **High** | Compose/Wasm trial; Kotlin/JS fallback only if the spike fails; measured browser and accessibility budget. | T-004 |
| R-04 | Local cookies and saved files are easier to expose than a server secret store. **High** | On-device consent, redaction, deletion, and no secrets in logs. | [T-006](../06-tasks/T-006-Review-security-licensing.md), [T-018](../06-tasks/T-018-Cookie-lifecycle.md) |
| R-05 | A later store submission is rejected. **Low** | Publication is out of scope. Revisit only if distribution returns to the plan. | [T-003](../06-tasks/T-003-Approve-product-scope.md) |
| R-06 | URL/option injection, path traversal, or secret exposure inside the local engine. **Critical** | Safe option contract, scoped storage, redaction, and adversarial tests. | T-006, [T-009](../06-tasks/T-009-Backend-vertical-slice.md), [T-017](../06-tasks/T-017-Options-and-presets.md), T-018 |
| R-07 | Site breakage and JavaScript challenge changes. **High** | Version the port, show the engine revision, and surface degraded-source errors. | T-009, [T-022](../06-tasks/T-022-Parity-audit.md) |
| R-08 | A full yt-dlp/MeTube port is larger than the first milestones imply. **High** | Parity inventory; M1 is one URL; MVP is separate from the parity candidate. | T-022 |
| R-09 | MeTube protocol compatibility is assumed. **Closed** | ADR-004 targets workflow parity, not MeTube HTTP or Socket.IO. | [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md) |
| R-10 | Lost/duplicated jobs or a file marked complete before it is finalized. **High** | Durable attempts, idempotency, restart/cancel tests, artifact finalization. | [T-011](../06-tasks/T-011-Durable-queue.md) |
| R-11 | Large playlists/media exhaust disk, memory, CPU, or network. **High** | Bounded extraction/queue, streaming writes, disk checks, and retention. | [T-012](../06-tasks/T-012-Playlists-and-batches.md), [T-014](../06-tasks/T-014-Storage-and-delivery.md) |
| R-12 | Mobile suspension or browser save behavior breaks the “downloaded” UX. **High** | Honest in-progress versus saved status. Test suspension and tab close. | T-004, [T-010](../06-tasks/T-010-Remote-vertical-slice.md), T-014 |
| R-13 | Subscription scans repeatedly fetch old items or overload sources. **Medium** | Explicit initial-scan/seen-ID policy, interval floors, bounded filters/scans, jitter/backoff, restart tests. | [T-019](../06-tasks/T-019-Subscriptions.md) |
| R-14 | Public vault accidentally contains cookies/private URLs or plugin code. **High** | Ignore local state, review staged diffs/history, synthetic examples, redact evidence; rotate any exposed secrets. | [T-001](../06-tasks/T-001-Planning-vault.md), all contributors |
| R-15 | Four local targets multiply engine and media-toolkit maintenance. **High** | Prove one download on each target before widening extractor coverage. | T-004, T-010 |
