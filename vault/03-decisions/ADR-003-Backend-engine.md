---
id: ADR-003
type: adr
status: superseded
created: 2026-09-16
superseded_by: ADR-004
tags: [architecture, decisions, backend]
---

# ADR-003 — Backend and engine selection

> [!warning] Superseded on 2026-09-21
> The owner chose no backend. Engine work is a Kotlin port inside the app. See [ADR-004](ADR-004-Local-kotlin-engine.md). The comparison below was not executed.

[Decision log](Decision-log.md) · [Architecture](../02-architecture/Architecture.md) · [API outline](../02-architecture/API-outline.md)

## Context

Feature parity with MeTube is substantial: durable queues, subscriptions, advanced media outputs, option layering, cookies, storage and operations. A new Kotlin backend provides contract ownership but requires rebuilding those behaviors. The user asked for a Kotlin Multiplatform **app**, not necessarily an exclusively Kotlin server.

## Proposal to evaluate

Prefer a **Ktor/JVM API plus isolated Python yt-dlp worker** if the benefits of a typed owned contract justify the implementation and operations cost. This is a candidate, not a finalized stack.

Compare it against:

1. **MeTube integration/adapter:** reuse a running MeTube service as the engine; verify version compatibility, authentication and Socket.IO support on every target. No assumption that existing routes equal our proposed API.
2. **Python API + yt-dlp:** a smaller direct service with Kotlin clients; assess whether reduced runtime boundaries outweigh shared backend Kotlin.
3. **JVM CLI wrapper:** typed process arguments and structured output, compared against Python hooks for progress, cancellation and metadata.

## Spike and decision criteria

Run the same authorized fixtures: single video, extracted audio, playlist with unavailable entry, authenticated fixture, FFmpeg postprocessing, cancellation, failed retry, restart and reconnect.

Record:

- Time/complexity to demonstrate all four clients and preserve the parity inventory.
- Durable job/attempt and subscription model fit.
- Progress/cancellation behavior, including child processes and resource limits.
- Auth, artifact delivery, SSRF/egress, secret handling and safe options.
- Kotlin/browser transport compatibility; **Socket.IO is not raw WebSocket**.
- Packaging on Linux amd64/arm64, versions, updates/rollback and maintenance cost.
- License obligations for each artifact and any copied/modified upstream code.

## Consequences

A new service buys contract control but cannot claim MeTube parity just because it invokes yt-dlp. A MeTube adapter buys existing behavior but creates version/protocol coupling and does not automatically solve auth or app-specific export. Whichever wins, do not implement both prematurely.

## Decision record

**Closed without the spike.** Owner, 2026-09-21: no MeTube server, no Ktor API, and no Python worker. The accepted engine is the local Kotlin port in [ADR-004](ADR-004-Local-kotlin-engine.md).
