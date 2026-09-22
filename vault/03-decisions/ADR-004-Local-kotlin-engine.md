---
id: ADR-004
type: adr
status: accepted
created: 2026-09-21
tags: [architecture, decisions, engine]
---

# ADR-004 — Local Kotlin engine, no backend

[Decision log](Decision-log.md) · [Architecture](../02-architecture/Architecture.md) · [Product brief](../01-product/Product-brief.md)

Supersedes [ADR-001](ADR-001-Execution-model.md) and [ADR-003](ADR-003-Backend-engine.md).

## Context

The owner set the product direction on 2026-09-21. The earlier plan assumed a trusted remote engine so the same yt-dlp/Python/FFmpeg stack could serve Android, iOS, desktop, and web. That assumption is withdrawn.

## Decision

- **Product name:** AnyDownload (`anydownload`). The GitHub repository slug remains `anydownlod` until a rename is requested.
- **Execution:** the app is local-only. Download, queue, history, and the rest of the product behavior run inside the app. There is no required backend, server, or self-host package.
- **Engine:** port yt-dlp's behavior to Kotlin and run it in-process on every target. [ADR-002](ADR-002-Kotlin-wrapper.md) still applies: the archived YtDlp-kt project is a JVM process wrapper around the Python CLI, so it is not this port.
- **Coverage goal:** the same media sites yt-dlp supports, plus the user-facing workflows of yt-dlp and MeTube (queue, formats, audio extraction, playlists, history, and the rest of the reviewed feature inventory). This is behavior parity, not MeTube HTTP or Socket.IO compatibility.
- **Targets:** iOS, web via Compose/Wasm, Android, and desktop.
- **Authentication:** the app does not require a user account or login. Site cookies and credentials, where a yt-dlp/MeTube workflow needs them, are a later local feature, not an app login.
- **Distribution:** store publication, signing for public release, and App Store / Play policy are out of scope. The app is a portfolio project. Local builds are enough.

## Alternatives rejected by the owner

- Remote-first clients talking to MeTube, a Ktor API, or a Python worker.
- Optional local engines only on desktop and Android, with iOS and web staying remote.
- An authenticated single-owner server.

No comparative engine spike was run. The choice is an owner decision, not the result of [T-005](../06-tasks/T-005-Choose-backend-engine.md)'s fixture comparison.

## Consequences

Shared Kotlin must contain the extractor, download, and postprocess behavior, with platform adapters only for HTTP, files, sharing, and lifecycle. Common code cannot call `ProcessBuilder` or assume a Python runtime.

yt-dlp's site support is large and changes with upstream. The port reaches "what yt-dlp supports" only as extractors and the JavaScript challenge runtime are implemented. The first milestone is one authorized public URL on each target, then breadth.

Compose/Wasm and iOS are in scope and unproven for this engine. The browser has no subprocess, no bundled FFmpeg, and cross-origin fetches are limited to what the page is allowed to request. iOS has no desktop subprocess model and does not keep arbitrary work running after the app is suspended. [T-004](../06-tasks/T-004-Validate-KMP-targets.md) has to show a local download on each target before the port is treated as feasible there.

FFmpeg-style postprocessing (merge, audio extract, clips) needs a per-target media toolkit decision. Copying extractor logic from yt-dlp must follow that project's license; [T-006](../06-tasks/T-006-Review-security-licensing.md) still records the license inventory. Store review does not.

The existing client scaffold (`shared/network`, job API DTOs) was drafted for a remote server. It is not the engine.

## Approval

Owner, 2026-09-21, recorded from the product-direction message in the planning session. Evidence is that message, not a toolchain spike.
