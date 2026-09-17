---
id: T-021
type: task
priority: P1
milestone: M3
tags: [task, operations, self-hosting]
---

# T-021 — Self-host configuration and engine operations

[Home](../Home.md) · [Kanban](../Kanban.md) · [Security](../04-delivery/Security-and-licensing.md)

## Outcome

F-24–F-26 hosting/update controls and operational aspects of storage/configuration parity.

## Dependencies

- [T-009](T-009-Backend-vertical-slice.md).
- [T-011](T-011-Durable-queue.md).
- [T-017](T-017-Options-and-presets.md).

## Acceptance criteria

- [ ] Package tested Linux amd64/arm64 runtime with persistent media/state/secrets separation, permissions/umask, quotas, temp paths and health/readiness.
- [ ] Document/test host/port/IPv6, reverse proxy/TLS/base path, credential-aware CORS, event transport, artifact URL configuration, safe optional indexing and robots file.
- [ ] Expose redacted logs/version/config diagnostics and validated config reload; operator-only unsafe exceptions with explicit warnings.
- [ ] Pin/verify dependencies and support approved stable/nightly update policy, graceful jobs, rollback and backup/restore.
- [ ] Test secure default egress, secret/state non-exposure, upgrades and recovery; write admin documentation.

## Evidence / notes

Not started. Hosting location is undecided; this task is not permission to deploy anything during planning.
