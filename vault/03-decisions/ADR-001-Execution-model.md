---
id: ADR-001
type: adr
status: superseded
created: 2026-09-16
superseded_by: ADR-004
tags: [architecture, decisions]
---

# ADR-001 — Remote-first execution

> [!warning] Superseded on 2026-09-21
> The owner rejected a required backend. The accepted direction is [ADR-004](ADR-004-Local-kotlin-engine.md): a local Kotlin port of yt-dlp on Android, iOS, desktop, and Compose/Wasm. The proposal below is kept as the original reasoning.

[Decision log](Decision-log.md) · [Architecture](../02-architecture/Architecture.md) · [Platform matrix](../02-architecture/Platform-matrix.md)

## Context

The requested clients are Android, iOS, desktop, and web. yt-dlp is a Python/CLI ecosystem with FFmpeg and, for current full YouTube support, JavaScript runtime components. Shared Kotlin does not make these executable dependencies run uniformly in iOS or browser sandboxes.

## Proposed decision

Use a **trusted remote engine on all four targets** for the first release. Share domain/networking code in Kotlin; use platform adapters for storage, sharing and lifecycle. Keep extraction behind a capability-based engine boundary. Investigate local desktop/Android engines later without promising a standalone iOS/browser equivalent.

This recommendation was **not accepted**. On 2026-09-21 the owner required a local-only app.

## Alternatives

- Local-only per platform: attractive offline story on desktop/Android, but does not satisfy the same practical iOS/web execution model.
- Existing MeTube server: may satisfy remote execution with less backend work; evaluate in ADR-003.
- Hosted public engine: lowers user setup but introduces ongoing cost, abuse, multi-tenant authorization and content-policy obligations; not an assumed requirement.
- Browser Python/FFmpeg-Wasm experiments: incomplete extraction/network/runtime parity, substantial resource and compatibility risks.

## Consequences

Server jobs outlive clients and enable one consistent queue/subscription model. Users must trust the server with URLs/media/cookies and pay server storage/bandwidth costs. Device export remains platform-specific. Server-side and device-side completion must be distinct in UI/domain/API. Store-policy questions remain even with a remote engine.

## Acceptance evidence required

[T-003](../06-tasks/T-003-Approve-product-scope.md) confirms the server requirement; [T-004](../06-tasks/T-004-Validate-KMP-targets.md) proves clients/export; [T-005](../06-tasks/T-005-Choose-backend-engine.md) proves the engine path; [T-006](../06-tasks/T-006-Review-security-licensing.md) reviews trust/distribution. Record approver/date here only after those decisions occur.
