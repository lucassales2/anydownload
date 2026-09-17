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
| R-02 | Standalone iOS/browser execution assumed from Kotlin wrapper branding. **High** | Remote-first proposal and real four-target spike; archived JVM wrapper not put in common code. | [T-004](../06-tasks/T-004-Validate-KMP-targets.md), [T-005](../06-tasks/T-005-Choose-backend-engine.md) |
| R-03 | Browser UI/dependency/accessibility limitations. **High** | Compose/Wasm trial across engines; Kotlin/JS/web UI fallback; measured browser and accessibility budget. | T-004 |
| R-04 | Server cost, operational burden and cookie trust unacceptable to users. **High** | Confirm remote server ownership/user model; explain bandwidth/storage/credentials; explicit quotas and cleanup. | T-003, [T-006](../06-tasks/T-006-Review-security-licensing.md), [T-014](../06-tasks/T-014-Storage-and-delivery.md) |
| R-05 | App Store/Play distribution rejected or binary licensing incompatible. **High** | Review policies and artifact-specific licenses before coding dependencies; choose viable channels without assuming approval. | T-006, [T-023](../06-tasks/T-023-Release-readiness.md) |
| R-06 | SSRF, command/option injection, path traversal or secret exposure. **Critical** | Restricted egress, safe argument/options contract, scoped storage/auth, redaction, isolation and adversarial tests. | T-006, [T-009](../06-tasks/T-009-Backend-vertical-slice.md), [T-017](../06-tasks/T-017-Options-and-presets.md), [T-018](../06-tasks/T-018-Cookie-lifecycle.md) |
| R-07 | Site breakage/engine runtime changes (including JS requirements). **High** | Pin/test runtime set, version diagnostics, opt-in updates with rollback, clear degraded-source errors. | T-005, [T-021](../06-tasks/T-021-Self-host-operations.md) |
| R-08 | New backend rebuild underestimates MeTube scope. **High** | Traceable parity inventory; compare adapter vs new API with fixtures; separate MVP from parity candidate. | T-005, T-022 |
| R-09 | API/Socket.IO mismatch or unstable MeTube coupling. **High** | Adapter contract tests and pinned server matrix; do not use a plain WebSocket client as if it were Socket.IO. | T-005 |
| R-10 | Lost/duplicated jobs, orphan FFmpeg, misleading completion. **High** | Durable attempts/leases, idempotency, restart/cancel race tests, artifact finalization. | [T-011](../06-tasks/T-011-Durable-queue.md) |
| R-11 | Large playlists/media exhaust disk, memory, CPU or network. **High** | Bounded extraction/queue, worker limits, streaming exports, quotas, disk checks, retention and long-running source policy. | [T-012](../06-tasks/T-012-Playlists-and-batches.md), T-014, T-021 |
| R-12 | Mobile suspension/browser save behavior breaks “downloaded” UX. **High** | Separate server jobs and device transfers; test real-device background/export limits; honest status. | T-004, [T-010](../06-tasks/T-010-Remote-vertical-slice.md), T-014 |
| R-13 | Subscription scans repeatedly fetch old items or overload sources. **Medium** | Explicit initial-scan/seen-ID policy, interval floors, bounded filters/scans, jitter/backoff, restart tests. | [T-019](../06-tasks/T-019-Subscriptions.md) |
| R-14 | Public vault accidentally contains cookies/private URLs or plugin code. **High** | Ignore local state, review staged diffs/history, synthetic examples, redact evidence; rotate any exposed secrets. | [T-001](../06-tasks/T-001-Planning-vault.md), all contributors |
| R-15 | Native packaging multiplies maintenance cost. **Medium** | Keep local engines optional; require per-platform update/signing/license evidence before adoption. | [T-024](../06-tasks/T-024-Desktop-local-spike.md), [T-025](../06-tasks/T-025-Android-local-spike.md) |
